package dtm.database.repository.prototype.datasource;

public interface DatabaseConfiguration {

    String getDriverClassName();
    String getUrl();
    String getUsername();
    String getPassword();

    String getDialect();

    default String getHbm2ddlAuto() {
        return "update";
    }

    default boolean showSql() {
        return true;
    }

    default boolean formatSql() {
        return false;
    }
}
