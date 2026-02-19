package dtm.database.repository.exceptions;

public class DatabaseDriverNotFoundException extends DatabaseInitializationException {

    public DatabaseDriverNotFoundException(String driverName, Throwable cause) {
        super("Driver JDBC '" + driverName + "' não encontrado no classpath.", cause);
    }
}
