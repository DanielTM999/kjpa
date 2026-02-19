package dtm.database.repository.exceptions;

public class OrmConfigurationException extends DatabaseInitializationException {
    public OrmConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
