package dtm.database.annotations;


import dtm.database.repository.aspect.TransactionalAspect;
import dtm.database.repository.config.DatabaseSessionSynchronizationContextConfig;
import dtm.database.repository.config.HibernateConfiguration;
import dtm.database.repository.config.RepositoryCreatorConfiguration;
import dtm.di.annotations.Import;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Import({
        RepositoryCreatorConfiguration.class,
        HibernateConfiguration.class,
        DatabaseSessionSynchronizationContextConfig.class,
        TransactionalAspect.class
})
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnablePersistence {
}
