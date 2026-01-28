package dtm.database.repository.exceptions;

public class InvalidQueryOperationException extends RuntimeException {
    public InvalidQueryOperationException(String message) {
        super(message);
    }

    public InvalidQueryOperationException(String methodName, String repositoryName, String details) {
        super(String.format(
                "Operacao de query invalida no metodo '%s' do repositorio '%s'. Detalhes: %s",
                methodName, repositoryName, details
        ));
    }
}
