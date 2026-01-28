package dtm.database.repository.proxy;

import dtm.database.repository.prototype.datasource.EntityManagerFactoryContext;
import dtm.database.repository.sessions.DatabaseSessionSynchronizationContext;
import dtm.di.core.DependencyContainer;
import dtm.di.prototypes.async.AsyncComponent;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Proxy;

@Slf4j
public class ProxyDbUtils {

    private ProxyDbUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Cria uma instância de proxy para a interface de repositório fornecida.
     *
     * @param interfaceType A interface do repositório (ex: UserRepository.class).
     * @param dependencyContainer conteiner de dependecias
     * @return O objeto proxy que implementa a interface.
     */
    @SuppressWarnings("unchecked")
    public static <T> T createRepositoryProxy(
            Class<T> interfaceType,
            DependencyContainer dependencyContainer,
            DatabaseSessionSynchronizationContext databaseSessionSynchronizationContext,
            AsyncComponent<EntityManagerFactoryContext> entityManagerFactoryContextAsync
    ) {
        log.debug("Gerando JDK Proxy para: {}", interfaceType.getName());

        return (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class<?>[]{interfaceType},
                new RepositoryInvocationHandler(interfaceType, dependencyContainer, databaseSessionSynchronizationContext, entityManagerFactoryContextAsync)
        );
    }

}
