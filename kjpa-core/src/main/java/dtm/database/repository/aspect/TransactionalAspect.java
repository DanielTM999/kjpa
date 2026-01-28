package dtm.database.repository.aspect;

import dtm.database.repository.exceptions.DatabaseSessionOutOfContextException;
import dtm.database.repository.prototype.datasource.EntityManagerFactoryContext;
import dtm.database.repository.sessions.DatabaseSession;
import dtm.database.repository.sessions.DatabaseSessionSynchronizationContext;
import dtm.di.annotations.DisableInjectionWarn;
import dtm.di.annotations.aop.*;
import dtm.di.exceptions.DependencyInjectionException;
import dtm.di.prototypes.LazyDependency;
import dtm.di.prototypes.async.AsyncComponent;
import jakarta.transaction.Transactional;
import dtm.database.repository.proxy.RepositoryInvocationHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Aspect
public class TransactionalAspect {

    private final DatabaseSessionSynchronizationContext databaseSessionSynchronizationContext;
    private final LazyDependency<AsyncComponent<EntityManagerFactoryContext>> entityManagerFactoryContextAsyncLazy;
    private final AtomicReference<EntityManagerFactoryContext> entityManagerFactoryContextRef;

    public TransactionalAspect(
            @DisableInjectionWarn DatabaseSessionSynchronizationContext databaseSessionSynchronizationContext,
            @DisableInjectionWarn LazyDependency<AsyncComponent<EntityManagerFactoryContext>> entityManagerFactoryContextAsyncLazy
    ) {
        this.databaseSessionSynchronizationContext = databaseSessionSynchronizationContext;
        this.entityManagerFactoryContextAsyncLazy = entityManagerFactoryContextAsyncLazy;
        this.entityManagerFactoryContextRef = new AtomicReference<>();
    }

    @Pointcut
    public boolean pointcut(Method method){
        if(!entityManagerFactoryContextAsyncLazy.isPresent()) return false;

        return method.isAnnotationPresent(Transactional.class);
    }

    @BeforeExecution
    public void aspectBefore(Method method){
        try{
            if(!this.databaseSessionSynchronizationContext.hasSession()){
                EntityManagerFactoryContext entityManagerFactoryContext = getEntityManagerFactoryContext();
                DatabaseSession databaseSession = entityManagerFactoryContext.createDatabaseSession();
                databaseSession.beginTransaction();
                log.debug("Nova transação iniciada e vinculada à thread: {}.", Thread.currentThread().getName());
                databaseSessionSynchronizationContext.addSession(databaseSession, RepositoryInvocationHandler.class);
            }else{
                log.debug("Reutilizando sessão existente para transação aninhada no método: {} vinculada à thread: {}.", method.getName(), Thread.currentThread().getName());
                DatabaseSession databaseSession = databaseSessionSynchronizationContext.getSession();
                databaseSession.beginTransaction();
            }
        } catch (Exception e) {
            log.error("""
            
            [ ERRO AO INICIAR TRANSAÇÃO ]
            Não foi possível abrir a transação para o método: {}
            > Causa Provável: Falha na conexão com o banco ou inicialização da Factory.
            > Detalhe técnico: {}
            """, method.getName(), e.getMessage());

            throw e;
        }
    }

    @AfterExecution
    public void aspectAfter(Method method){
        DatabaseSession session = databaseSessionSynchronizationContext.getSession();
        if (session == null) {
            log.error("""
            
            [ SESSÃO FORA DE CONTEXTO ]
            Falha crítica ao realizar o COMMIT do método: {}
            > Thread: {}
            > Solução: Verifique se houve desvio de thread ou se a sessão foi fechada prematuramente.
            """, method.getName(), Thread.currentThread().getName());

            throw new DatabaseSessionOutOfContextException(
                    String.format("Falha ao realizar commit: Nenhuma sessão ativa encontrada para a Thread [%s]. " +
                                    "Isso pode ocorrer se o método @Transactional '%s' foi chamado fora do contexto de execução gerenciado.",
                            Thread.currentThread().getName(), method.getName())
            );
        }

        log.debug("""
        
        [ TRANSAÇÃO FINALIZADA ]
        Commit realizado com sucesso.
        > Método: {}
        > Thread: {}
        """, method.getName(), Thread.currentThread().getName());

        session.commitIfActive();
    }

    @AfterException
    public void aspectException(Method method){
        DatabaseSession session = databaseSessionSynchronizationContext.getSession();
        if (session == null) {

            log.error("""
            
            [ SESSÃO FORA DE CONTEXTO - FALHA NO ROLLBACK ]
            O método '{}' lançou uma exceção, mas não há sessão ativa para realizar o ROLLBACK.
            > Thread: {}
            > Risco: Pode haver transações pendentes ou inconsistência de dados.
            """, method.getName(), Thread.currentThread().getName());

            throw new DatabaseSessionOutOfContextException(
                    String.format("Falha ao realizar roolback: Nenhuma sessão ativa encontrada para a Thread [%s]. " +
                                    "Isso pode ocorrer se o método @Transactional '%s' foi chamado fora do contexto de execução gerenciado.",
                            Thread.currentThread().getName(), method.getName())
            );
        }

        log.debug("""
        
        [ OPERAÇÃO DE ROLLBACK ]
        A transação foi revertida devido a uma exceção no método: {}
        > Thread: {}
        > Status: A integridade dos dados foi preservada.
        """, method.getName(), Thread.currentThread().getName());
        session.rollbackIfActive();
    }

    private EntityManagerFactoryContext getEntityManagerFactoryContext(){
        if(entityManagerFactoryContextRef.get() == null){
            AsyncComponent<EntityManagerFactoryContext> entityManagerFactoryContextAsync = entityManagerFactoryContextAsyncLazy.get();
            EntityManagerFactoryContext entityManagerFactoryContext = entityManagerFactoryContextAsync.getAsync().await();
            validEntityManagerFactoryContext(entityManagerFactoryContext);
            entityManagerFactoryContextRef.set(entityManagerFactoryContext);
        }

        return entityManagerFactoryContextRef.get();
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
