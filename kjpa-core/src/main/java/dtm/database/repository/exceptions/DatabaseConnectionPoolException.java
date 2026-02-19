package dtm.database.repository.exceptions;

public class DatabaseConnectionPoolException extends DatabaseInitializationException {
    public DatabaseConnectionPoolException(String message, Throwable cause) {
        super(message, cause);
    }
}