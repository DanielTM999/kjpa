package dtm.database.repository.exceptions;

public class DatabaseConnectionException extends DatabaseInitializationException {
    public DatabaseConnectionException(String url, String technicalDetail, Throwable cause) {
        super("Falha ao comunicar com o banco em '" + url + "'. Detalhe: " + technicalDetail, cause);
    }
}