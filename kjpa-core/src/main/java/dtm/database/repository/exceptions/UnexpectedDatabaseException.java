package dtm.database.repository.exceptions;

public class UnexpectedDatabaseException extends DatabaseInitializationException {
    public UnexpectedDatabaseException(Throwable cause) {
        super("Erro crítico desconhecido ao configurar base de dados.", cause);
    }
}
