package dtm.database.repository.exceptions;

/**
 * Exceção lançada quando o sistema não consegue resolver os metadados
 * gerados para um repositório via reflexão.
 */
public class RepositoryMetaInfoResolutionException extends RuntimeException {
    public RepositoryMetaInfoResolutionException(String message) {
        super(message);
    }
}
