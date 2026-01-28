package dtm.database.repository.config;

import dtm.database.annotations.Repository;
import dtm.database.repository.CrudRepository;
import dtm.database.repository.prototype.datasource.EntityManagerFactoryContext;
import dtm.database.repository.proxy.ProxyDbUtils;
import dtm.database.repository.sessions.DatabaseSessionSynchronizationContext;
import dtm.di.annotations.Component;
import dtm.di.annotations.Configuration;
import dtm.di.annotations.DisableInjectionWarn;
import dtm.di.annotations.aop.DisableAop;
import dtm.di.core.DependencyContainer;
import dtm.di.exceptions.InvalidClassRegistrationException;
import dtm.di.prototypes.async.AsyncComponent;
import lombok.extern.slf4j.Slf4j;
import dtm.di.exceptions.DependencyInjectionException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@DisableAop
@Configuration
public class RepositoryCreatorConfiguration {

    @Component
    @DisableInjectionWarn
    protected void injectRepositoryInContainer(
            DependencyContainer dependencyContainer,
            AsyncComponent<EntityManagerFactoryContext> entityManagerFactoryContextAsync,
            DatabaseSessionSynchronizationContext databaseSessionSynchronizationContext
    ){
        //validSessionFactory(entityManagerFactoryContext);
        List<CompletableFuture<?>> tasks = new ArrayList<>();
        try(ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())){
            for (Class<?> clazz : dependencyContainer.getLoadedSystemClasses()){
                tasks.add(CompletableFuture.runAsync(() -> {
                    if(isReporitoryInterface(clazz)){
                        Object repositoryProxy = createInterfaceProxy(clazz, dependencyContainer, databaseSessionSynchronizationContext, entityManagerFactoryContextAsync);
                        registerProxy(clazz, repositoryProxy, dependencyContainer);
                    }
                }, executorService));
            }
        }

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
    }





    private boolean isReporitoryInterface(Class<?> clazz){
        Class<?> baseInterface = CrudRepository.class;
        if(baseInterface.equals(clazz)) return false;
        if (!clazz.isInterface()) return false;
        boolean extendsBase = baseInterface.isAssignableFrom(clazz);
        return (extendsBase) && clazz.isAnnotationPresent(Repository.class);
    }

    private Object createInterfaceProxy(
            Class<?> clazz,
            DependencyContainer dependencyContainer,
            DatabaseSessionSynchronizationContext databaseSessionSynchronizationContext,
            AsyncComponent<EntityManagerFactoryContext> entityManagerFactoryContextAsync
    ){
        return ProxyDbUtils.createRepositoryProxy(clazz, dependencyContainer, databaseSessionSynchronizationContext, entityManagerFactoryContextAsync);
    }

    private void registerProxy(Class<?> proxyClass, Object repositoryProxy, DependencyContainer dependencyContainer){
        if(repositoryProxy == null){
            log.warn("Tentativa de registrar um proxy NULO para a interface [{}]. O registro será ignorado.", proxyClass.getCanonicalName());
            return;
        }

        try{
            dependencyContainer.registerDependency(repositoryProxy, false);
        }catch (InvalidClassRegistrationException e){
            log.error("Falha crítica ao registrar o proxy do repositório [{}]. O container recusou o registro.", proxyClass.getCanonicalName(), e);
        }
    }


}
