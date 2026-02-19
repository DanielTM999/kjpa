package dtm.database.repository.exceptions;

public class DatabaseAuthenticationException extends DatabaseInitializationException {
    public DatabaseAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}