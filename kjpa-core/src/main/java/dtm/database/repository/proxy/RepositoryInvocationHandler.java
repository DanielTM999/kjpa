package dtm.database.repository.proxy;

import dtm.database.repository.exceptions.InvalidQueryOperationException;
import dtm.database.repository.exceptions.RepositoryMetaInfoResolutionException;
import dtm.database.repository.prototype.RepositoryMetaInfoManager;
import dtm.database.repository.prototype.RepositoryMetainfo;
import dtm.database.repository.prototype.datasource.EntityManagerFactoryContext;
import dtm.database.repository.sessions.DatabaseSession;
import dtm.database.repository.sessions.DatabaseSessionSynchronizationContext;
import dtm.di.annotations.aop.DisableAop;
import dtm.di.core.DependencyContainer;
import dtm.di.exceptions.DependencyInjectionException;
import dtm.di.prototypes.async.AsyncComponent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@DisableAop
public class RepositoryInvocationHandler implements InvocationHandler {

    private final AtomicReference<RepositoryMetaInfoManager> repositoryMetaInfoManagerRef = new AtomicReference<>();
    private final AtomicReference<EntityManagerFactoryContext> entityManagerFactoryContextRef = new AtomicReference<>();
    private final Class<?> repositoryInterface;
    private final DependencyContainer dependencyContainer;
    private final DatabaseSessionSynchronizationContext databaseSessionSynchronizationContext;
    private final AsyncComponent<EntityManagerFactoryContext> entityManagerFactoryContextAsync;


    public RepositoryInvocationHandler(Class<?> repositoryInterface, DependencyContainer dependencyContainer, DatabaseSessionSynchronizationContext databaseSessionSynchronizationContext, AsyncComponent<EntityManagerFactoryContext> entityManagerFactoryContextAsync) {
        this.repositoryInterface = repositoryInterface;
        this.dependencyContainer = dependencyContainer;
        this.databaseSessionSynchronizationContext = databaseSessionSynchronizationContext;
        this.entityManagerFactoryContextAsync = entityManagerFactoryContextAsync;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass().equals(Object.class)) return method.invoke(this, args);
        if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);

        resolveRepositoryMetaInfoManager();
        RepositoryMetainfo metadata = resolveMetadata(method);

        return executeWithSession(metadata, args);
    }

    @Override
    public String toString() {
        return "ProxyRepository<" + repositoryInterface.getSimpleName() + ">";
    }

    private void resolveRepositoryMetaInfoManager() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if(repositoryMetaInfoManagerRef.get() == null){

            String className = "dtm.database.repository.generated." + repositoryInterface.getSimpleName() + "MetaData";

            try {
                Class<?> metaDataManagerInfo = Class.forName(className);
                Object instance = metaDataManagerInfo.getMethod("getInstance").invoke(null);

                if(instance instanceof RepositoryMetaInfoManager metaInfoManager){
                    repositoryMetaInfoManagerRef.set(metaInfoManager);
                }
            } catch (ClassNotFoundException e) {
                throwIfInvalidrepositoryMetaInfoManagerRef(className);
            }
        }
    }

    private void throwIfInvalidrepositoryMetaInfoManagerRef(String attemptedClassName) {
        if(repositoryMetaInfoManagerRef.get() == null){
            log.error("""
                
                [ ERRO DE RESOLUÇÃO DE REPOSITÓRIO ]
                Não foi possível encontrar os metadados gerados para o repositório.
                > Interface  : {}
                > Classe Alvo : {}
                > Possível Causa: O processador de anotações não gerou a classe de metadados ou está desabilitado
                """, repositoryInterface.getName(), attemptedClassName);

            throw new RepositoryMetaInfoResolutionException(
                    "Falha ao localizar metadados para " + repositoryInterface.getSimpleName() +
                            ". Certifique-se de que o Proceessor esta habilitado e a classe " + attemptedClassName + " foi gerada corretamente"
            );
        }
    }

    private RepositoryMetainfo resolveMetadata(Method method) {
        String signature = buildMethodSignature(method);
        RepositoryMetainfo metadata = repositoryMetaInfoManagerRef.get().getByMethod(signature);

        if (metadata == null) {
            handleMetadataNotFound(signature);
        }
        return metadata;
    }

    private String buildMethodSignature(Method method) {
        String name = method.getName();

        return switch (name) {
            case "save", "saveAll", "findById", "delete", "deleteById", "findAll", "count" -> name;
            default -> {
                String params = Arrays.stream(method.getParameterTypes())
                        .map(Class::getName)
                        .collect(Collectors.joining(","));
                yield String.format("%s[%s]", name, params);
            }
        };
    }

    private Object executeWithSession(RepositoryMetainfo metadata, Object[] args) {
        if(databaseSessionSynchronizationContext.hasSession()){
            DatabaseSession databaseSession = databaseSessionSynchronizationContext.getSession();
            return executeEfetiveSqlWithSession(metadata, args, databaseSession);
        }else{
            EntityManagerFactoryContext entityManagerFactoryContext = getEntityManagerFactoryContext();

            DatabaseSession databaseSession = entityManagerFactoryContext.createDatabaseSession();

            return databaseSession.runInTransaction(() -> {
                return executeEfetiveSqlWithSession(metadata, args, databaseSession);
            });
        }
    }

    private Object executeEfetiveSqlWithSession(RepositoryMetainfo metadata, Object[] args, DatabaseSession databaseSession){
        return switch (metadata.operationType()){
            case SAVE -> {
                Object entity = (args.length == 1) ? args[0] : null;
                yield executeSave(entity, databaseSession, metadata.autoFlush());
            }
            case DELETE -> {
                Object entity = (args.length == 1) ? args[0] : null;
                yield executeDelete(entity, databaseSession, metadata.autoFlush());
            }
            case DELETE_BY_ID -> {
                Object entity = (args.length == 1) ? args[0] : null;
                yield executeDeleteById(args, metadata, databaseSession);
            }
            case FIND_ALL -> {
                yield executeFindAll(args, metadata, databaseSession);
            }
            case FIND_BY_ID -> {
                yield executeFindById(args, metadata, databaseSession);
            }
            case QUERY -> {
                yield executeQuery(args, metadata, databaseSession);
            }
            default -> throw new InvalidQueryOperationException(
                    metadata.methodName(),
                    repositoryInterface.getSimpleName(),
                    "Tipo de operacao '" + metadata.operationType() + "' nao suportada ou nao implementada."
            );
        };
    }

    private void handleMetadataNotFound(String signature) {
        String repoName = repositoryInterface.getName();

        String solution = String.format(
                "Certifique-se de que o Annotation Processor do Kernon foi executado. " +
                "Tente realizar um 'Clean and Build' no projeto. " +
                "Se o erro persistir, verifique se a dependência do processor está configurada no seu pom.xml/build.gradle " +
                "e se as anotações no repositório '%s' estão corretas.", repoName);


        log.error("""
        
        [ ERRO DE EXECUÇÃO: METADADO AUSENTE ]
        O método chamado não possui instruções de execução mapeadas.
        > Assinatura Gerada: {}
        > Repositório: {}.
        > Possível Solução : {}
        """, signature, repoName, solution);

        throw new RepositoryMetaInfoResolutionException(
                String.format("Metadados ausentes para '%s' em '%s'. %s", signature, repoName, solution)
        );
    }

    private Object executeSave(Object entity, DatabaseSession databaseSession, boolean flush){
        if (entity == null) {
            throw new InvalidQueryOperationException("Tentativa de salvar uma entidade nula.");
        }

        try {
            EntityManager em = databaseSession.getEntityManager();

            if (em.contains(entity)) {
                em.flush();
                return entity;
            }

            Object managedEntity = em.merge(entity);
            if(flush)em.flush();

            return managedEntity;
        }catch (Exception e) {
            log.error("Falha ao persistir entidade do tipo: {}", entity.getClass().getName(), e);
            throw e;
        }
    }

    private Object executeDelete(Object entity, DatabaseSession databaseSession, boolean flush){
        if (entity == null) {
            throw new InvalidQueryOperationException("Tentativa de remover uma entidade nula.");
        }

        try {
            EntityManager em = databaseSession.getEntityManager();

            Object entityToRemove = entity;

            if (!em.contains(entity)) {
                entityToRemove = em.merge(entity);
            }

            em.remove(entityToRemove);
            if(flush)em.flush();
        }catch (Exception e) {
            log.error("Falha ao remover entidade do tipo: {}", entity.getClass().getName(), e);
            throw e;
        }

        return null;
    }

    private Object executeDeleteById(Object[] args, RepositoryMetainfo metadata, DatabaseSession databaseSession){
        if (args == null || args.length == 0 || args[0] == null) {
            throw new IllegalArgumentException("O ID da entidade deve ser fornecido.");
        }

        try {
          return executeQuery(args, metadata, databaseSession);
        }catch (Exception e) {
            log.error("Erro ao remover entidade por ID no repositório: {}", repositoryInterface.getName(), e);
            throw e;
        }
    }

    private Object executeFindById(Object[] args, RepositoryMetainfo metadata, DatabaseSession databaseSession){
        if (args == null || args.length == 0 || args[0] == null) {
            throw new IllegalArgumentException("O ID da entidade deve ser fornecido.");
        }

        try {
            return executeQuery(args, metadata, databaseSession);
        }catch (Exception e) {
            log.error("Erro ao buscar entidade por ID no repositório: {}", repositoryInterface.getName(), e);
            throw e;
        }
    }

    private Object executeFindAll(Object[] args, RepositoryMetainfo metadata, DatabaseSession databaseSession){
        try {
            return executeQuery(args, metadata, databaseSession);
        }catch (Exception e) {
            log.error("Erro ao buscar entidades no repositório: {}", repositoryInterface.getName(), e);
            throw e;
        }
    }

    private EntityManagerFactoryContext getEntityManagerFactoryContext(){
        if(entityManagerFactoryContextRef.get() == null){
            EntityManagerFactoryContext entityManagerFactoryContext = entityManagerFactoryContextAsync.getAsync().await();
            validEntityManagerFactoryContext(entityManagerFactoryContext);
            entityManagerFactoryContextRef.set(entityManagerFactoryContext);
        }

        return entityManagerFactoryContextRef.get();
    }



    private Object executeQuery(Object[] args, RepositoryMetainfo metadata, DatabaseSession databaseSession){
        boolean isNative = metadata.isNative();
        boolean isAutoFlush = metadata.autoFlush();
        String queryString = metadata.queryTemplate();

        EntityManager em = databaseSession.getEntityManager();
        Map<Integer, String> paramMap = metadata.paramMap();
        try {

            if (isAutoFlush) {
                em.flush();
            }

            Class<?> entityType = metadata.resultType();
            Query query = isNative
                    ? em.createNativeQuery(queryString, entityType)
                    : em.createQuery(queryString, entityType);

            if (args != null && paramMap != null) {
                paramMap.forEach((index, paramName) -> {
                    query.setParameter(paramName, args[index]);
                });
            }


            Object result = switch (metadata.returnStrategy()){
                case SINGLE_ENTITY -> {
                    yield query.getSingleResultOrNull();
                }
                case COLLECTION -> {
                    yield query.getResultList();
                }
                case OPTIONAL -> {
                    yield Optional.ofNullable(query.getSingleResultOrNull());
                }
                case PRIMITIVE -> {
                    yield query.getSingleResult();
                }
                case VOID -> {
                    if (isDml(queryString)) {
                        query.executeUpdate();
                    } else {
                        query.setMaxResults(1).getResultList();
                    }
                    yield null;
                }
            };

            if (isAutoFlush) {
                em.flush();
            }
            return result;
        } catch (Exception e) {
            log.error("Erro ao executar query no repositório {}: {}",
                   repositoryInterface.getSimpleName(), queryString, e);
            throw e;
        }

    }

    private boolean isDml(String query) {
        String trimmedQuery = query.trim().toUpperCase();
        return trimmedQuery.startsWith("UPDATE") ||
                trimmedQuery.startsWith("DELETE") ||
                trimmedQuery.startsWith("INSERT");
    }

    private void validEntityManagerFactoryContext(EntityManagerFactoryContext entityManagerFactoryContext){
        if(entityManagerFactoryContext == null){
            throw new DependencyInjectionException("Erro ao criar o EntityManagerFactory");
        }


        if(entityManagerFactoryContext.getEntityManagerFactory() == null){
            throw new DependencyInjectionException("Erro ao criar o EntityManagerFactory");
        }
    }

}
