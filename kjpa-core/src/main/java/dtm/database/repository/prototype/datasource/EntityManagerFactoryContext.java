package dtm.database.repository.prototype.datasource;

import dtm.database.repository.sessions.DatabaseSession;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

public interface EntityManagerFactoryContext {

    DatabaseConfiguration getDatabaseConfiguration();
    EntityManagerFactory getEntityManagerFactory();


    default DatabaseSession createDatabaseSession(){
        return new DatabaseSession() {
            private final EntityManagerFactory emf = getEntityManagerFactory();
            private final EntityManager em = (emf != null) ? emf.createEntityManager() : null;

            @Override
            public EntityManager getEntityManager() {
                return em;
            }
        };
    }
}
