package dtm.database.repository.sessions;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.function.Supplier;

public interface DatabaseSession {
    EntityManager getEntityManager();

    default boolean isValidSession(){
        EntityManager em = getEntityManager();
        return (em != null) && em.isOpen();
    }

    default void rollbackIfActive() {
        EntityManager em = getEntityManager();
        if (em != null) {
            EntityTransaction tx = em.getTransaction();
            if (tx.isActive()) {
                tx.rollback();
            }
        }
    }

    default void commitIfActive() {
        EntityManager em = getEntityManager();
        if (em != null) {
            EntityTransaction tx = em.getTransaction();
            if (tx.isActive()) {
                tx.commit();
            }
        }
    }

    default void beginTransaction() {
        EntityManager em = getEntityManager();
        if (em != null) {
            EntityTransaction tx = em.getTransaction();
            if (!tx.isActive()) {
                tx.begin();
            }
        }
    }

    default void close(){
        EntityManager em = getEntityManager();
        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    default void runInTransaction(Runnable action) {
        beginTransaction();
        try {
            action.run();
            commitIfActive();
        } catch (Exception e) {
            rollbackIfActive();
            throw e;
        } finally {
            close();
        }
    }

    default <T> T runInTransaction(Supplier<T> action) {
        beginTransaction();
        try {
            T result = action.get();
            commitIfActive();
            return result;
        } catch (Exception e) {
            rollbackIfActive();
            throw e;
        } finally {
            close();
        }
    }
}
