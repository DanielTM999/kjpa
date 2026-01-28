package dtm.database.repository.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dtm.database.repository.prototype.datasource.DatabaseConfiguration;
import dtm.database.repository.prototype.datasource.EntityManagerFactoryContext;
import dtm.di.annotations.Component;
import dtm.di.annotations.Configuration;
import dtm.di.annotations.DisableInjectionWarn;
import dtm.di.annotations.aop.DisableAop;
import dtm.di.application.startup.ManagedApplication;
import dtm.di.common.ComponentRegistor;
import dtm.di.core.DependencyContainer;
import dtm.di.exceptions.DependencyInjectionException;
import dtm.di.prototypes.async.AsyncRegistrationFunction;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.registry.classloading.spi.ClassLoadingException;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.service.spi.ServiceException;

@Slf4j
@DisableAop
@Configuration
public class HibernateConfiguration {

    @Component
    @DisableAop
    @DisableInjectionWarn
    public AsyncRegistrationFunction<EntityManagerFactoryContext> createSessionFactory(DependencyContainer dependencyContainer, DatabaseConfiguration databaseConfiguration){
        validDatabaseConfiguration(databaseConfiguration);

        return ComponentRegistor.ofAsync(EntityManagerFactoryContext.class, () -> {
            HikariDataSource dataSource = null;
            try {

                final HikariConfig hikariConfig = getHikariConfig(databaseConfiguration);

                dataSource = new HikariDataSource(hikariConfig);

                org.hibernate.cfg.Configuration configuration = new org.hibernate.cfg.Configuration();

                configuration.setProperty("hibernate.dialect", databaseConfiguration.getDialect());
                configuration.setProperty("hibernate.hbm2ddl.auto", databaseConfiguration.getHbm2ddlAuto());
                configuration.setProperty("hibernate.show_sql", String.valueOf(databaseConfiguration.showSql()));
                configuration.setProperty("hibernate.format_sql", String.valueOf(databaseConfiguration.formatSql()));


                configuration.addAnnotatedClasses(
                        dependencyContainer
                                .getLoadedSystemClasses()
                                .stream()
                                .filter(c -> c.isAnnotationPresent(Entity.class))
                                .toArray(Class[]::new)
                );

                StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder()
                        .applySettings(configuration.getProperties())
                        .applySetting(AvailableSettings.JAKARTA_JTA_DATASOURCE, dataSource)
                        .applySetting("hibernate.connection.datasource", dataSource);

                SessionFactory sessionFactory = configuration.buildSessionFactory(builder.build());
                registerGracefulShutdown(sessionFactory, dataSource);
                return new EntityManagerFactoryContext() {
                    @Override
                    public DatabaseConfiguration getDatabaseConfiguration() {
                        return databaseConfiguration;
                    }

                    @Override
                    public EntityManagerFactory getEntityManagerFactory() {
                        return sessionFactory;
                    }
                };
            } catch (ServiceException e) {
                Throwable rootCause = e.getCause();
                String detailedMessage;
                if (rootCause instanceof ClassNotFoundException ||
                        e.getMessage().contains("Unable to load class")) {

                    log.error("""
                
                [ ERRO DE DEPENDÊNCIA ]
                O Hibernate tentou configurar o banco, mas não encontrou o Driver JDBC.
                > Driver ausente : {}
                > Solução       : Adicione o driver do PostgreSQL ao seu projeto (Maven/Gradle).
                """, databaseConfiguration.getDriverClassName());

                    detailedMessage = String.format(
                            "Erro de Dependência: Driver JDBC '%s' não encontrado. Certifique-se de que a dependência do PostgreSQL está no classpath.",
                            databaseConfiguration.getDriverClassName()
                    );
                } else {
                    log.error("""
                
                [ ERRO DE CONEXÃO ]
                O Driver foi encontrado, mas a comunicação com o banco falhou.
                > URL tentada    : {}
                > Detalhe técnico: {}
                """, databaseConfiguration.getUrl(), e.getMessage());

                    detailedMessage = String.format(
                            "Erro de Conexão: Falha ao comunicar com o banco em '%s'. Detalhe: %s",
                            databaseConfiguration.getUrl(),
                            e.getMessage()
                    );
                }

                DependencyInjectionException error = new DependencyInjectionException(detailedMessage, e);
                ManagedApplication.reportError(error);
                throw error;
            } catch (ClassLoadingException e) {
                log.error("""
                
                [ ERRO DE DRIVER ]
                O driver JDBC necessário para a conexão não foi encontrado no sistema.
                > Driver esperado: {}
                > Solução: Verifique se a dependência do driver (Ex: PostgreSQL ou H2) está presente no seu pom.xml ou build.gradle.
                """, databaseConfiguration.getDriverClassName());

                DependencyInjectionException error = new DependencyInjectionException("Falha de biblioteca: Driver JDBC não encontrado.", e);
                ManagedApplication.reportError(error);
                throw error;
            } catch (HibernateException e) {
                log.error("""
                
                [ ERRO DE CONFIGURAÇÃO HIBERNATE ]
                Ocorreu um erro interno ao inicializar os serviços do Hibernate.
                > Verifique se o Dialeto ({}) é compatível com a versão do seu banco.
                > Detalhe técnico: {}
                """, databaseConfiguration.getDialect(), e.getMessage());

                DependencyInjectionException error = new DependencyInjectionException("Falha interna: Erro de configuração do ORM.", e);
                ManagedApplication.reportError(error);
                throw error;
            } catch (Exception e) {
                log.error("""
                
                [ ERRO INESPERADO ]
                Ocorreu uma falha não mapeada durante a criação da SessionFactory.
                > Tipo da Exceção: {}
                > Mensagem: {}
                """, e.getClass().getSimpleName(), e.getMessage());

                DependencyInjectionException error = new DependencyInjectionException("Erro crítico desconhecido ao configurar base de dados.", e);
                ManagedApplication.reportError(error);
                throw error;
            }
        });
    }

    private HikariConfig getHikariConfig(DatabaseConfiguration databaseConfiguration) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(databaseConfiguration.getUrl());
        hikariConfig.setUsername(databaseConfiguration.getUsername());
        hikariConfig.setPassword(databaseConfiguration.getPassword());
        hikariConfig.setDriverClassName(databaseConfiguration.getDriverClassName());

        hikariConfig.setAutoCommit(false);

        hikariConfig.setMaximumPoolSize(20);
        hikariConfig.setMinimumIdle(5);
        hikariConfig.setIdleTimeout(300000);
        hikariConfig.setConnectionTimeout(20000);
        hikariConfig.setPoolName("Kernon-HikariPool-" + databaseConfiguration.getDialect());
        return hikariConfig;
    }

    private void registerGracefulShutdown(SessionFactory sessionFactory, HikariDataSource dataSource) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("""
        
        [ ENCERRANDO PERSISTÊNCIA ]
        Iniciando o fechamento gracioso dos recursos de banco de dados...
        """);

            try {
                if (sessionFactory != null && sessionFactory.isOpen()) {
                    log.debug("Fechando Hibernate SessionFactory...");
                    sessionFactory.close();
                }

                if (dataSource != null && !dataSource.isClosed()) {
                    log.debug("Fechando Pool de Conexões Hikari ({})...", dataSource.getPoolName());
                    dataSource.close();
                }

                log.info("Infraestrutura de persistência encerrada com sucesso.");
            } catch (Exception e) {
                log.error("""
            
            [ ERRO NO SHUTDOWN ]
            Falha ao encerrar recursos de banco de dados.
            > Detalhe: {}
            """, e.getMessage());
            }
        }, "Database-Shutdown-Hook"));
    }

    private void validDatabaseConfiguration(DatabaseConfiguration databaseConfiguration){

        if (databaseConfiguration == null) {
            log.error("""
            
            ╔════════════════════════════════════════════════════════════════════════════╗
            ║             ERRO CRÍTICO: CONFIGURAÇÃO DE BANCO AUSENTE                    ║
            ╠════════════════════════════════════════════════════════════════════════════╣
            ║                                                                            ║
            ║  O sistema não detectou uma implementação de 'DatabaseConfiguration'.      ║
            ║                                                                            ║
            ║  AÇÃO NECESSÁRIA:                                                          ║
            ║  Certifique-se de que existe uma classe de configuração ativa              ║
            ║  que injete/retorne uma instância válida de DatabaseConfiguration.         ║
            ║                                                                            ║
            ║  Verifique suas anotações de injeção de dependência ou seus Beans.         ║
            ║                                                                            ║
            ╚════════════════════════════════════════════════════════════════════════════╝
            """);

            throw new DependencyInjectionException("A implementação de DatabaseConfiguration não foi fornecida (é nula). Verifique a injeção de dependência.");
        }

        validateField(databaseConfiguration.getDriverClassName(), "Driver Class Name");
        validateField(databaseConfiguration.getUrl(), "Database URL");
        validateField(databaseConfiguration.getUsername(), "Database Username");
        validateField(databaseConfiguration.getPassword(), "Database Password");
        validateField(databaseConfiguration.getDialect(), "Hibernate Dialect");

        int size = 60;
        log.info("""
                
                ╔════════════════════════════════════════════════════════════════════════════╗
                ║              DATABASE CONFIGURATION LOADED                                 ║
                ╠════════════════════════════════════════════════════════════════════════════╣
                ║  -> Driver   : {}║
                ║  -> Dialect  : {}║
                ║  -> URL      : {}║
                ║  -> User     : {}║
                ║  -> Password : {}║
                ║  -> DDL Auto : {}║
                ║  -> Show SQL : {}║
                ╚════════════════════════════════════════════════════════════════════════════╝
                """,
                padRight(databaseConfiguration.getDriverClassName(), size),
                padRight(databaseConfiguration.getDialect(), size),
                padRight(databaseConfiguration.getUrl(), size),
                padRight(databaseConfiguration.getUsername(), size),
                padRight("[PROTECTED]", size),
                padRight(databaseConfiguration.getHbm2ddlAuto(), size),
                padRight(databaseConfiguration.showSql() ? "ENABLED" : "DISABLED", size)
        );
    }

    private void validateField(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            log.error("Falha na validação do banco de dados: O campo '{}' está vazio ou nulo.", fieldName);
            throw new DependencyInjectionException("Configuração de banco inválida: O campo obrigatório '" + fieldName + "' não foi informado.");
        }
    }

    private String padRight(Object value, int length) {
        String str = (value == null) ? "null" : value.toString();
        if (str.length() > length) {
            return str.substring(0, length);
        }
        return String.format("%-" + length + "s", str);
    }

}
