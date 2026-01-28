package dtm.database.repository.config;

import dtm.database.repository.sessions.DatabaseSessionSynchronizationContext;
import dtm.database.repository.sessions.imple.DatabaseSessionSynchronizationContextContextImple;
import dtm.di.annotations.BeanDefinition;
import dtm.di.annotations.Component;
import dtm.di.annotations.Configuration;
import dtm.di.annotations.aop.DisableAop;

@DisableAop
@Configuration
public class DatabaseSessionSynchronizationContextConfig {


    @Component
    @DisableAop
    public DatabaseSessionSynchronizationContext databaseSessionSynchronizationContextBean(){
        return new DatabaseSessionSynchronizationContextContextImple();
    }

}
