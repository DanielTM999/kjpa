package dtm.database.repository.exceptions;

public class DatabaseSessionOutOfContextException extends RuntimeException {
    public DatabaseSessionOutOfContextException(String message) {
        super(message);
    }
}
