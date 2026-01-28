package dtm.database.repository.prototype.datasource;

public class SimpleDatabaseConfiguration implements DatabaseConfiguration{
    private final String driverClassName;
    private final String url;
    private final String username;
    private final String password;
    private final String dialect;

    private Boolean showSql;
    private Boolean formatSql;
    private String hbm2ddlAuto;

    public SimpleDatabaseConfiguration(String driverClassName, String url, String username, String password, String dialect) {
        this.driverClassName = driverClassName;
        this.url = url;
        this.username = username;
        this.password = password;
        this.dialect = dialect;
    }

    public SimpleDatabaseConfiguration withShowSql(boolean showSql) {
        this.showSql = showSql;
        return this;
    }

    public SimpleDatabaseConfiguration withFormatSql(boolean formatSql) {
        this.formatSql = formatSql;
        return this;
    }

    public SimpleDatabaseConfiguration withHbm2ddlAuto(String hbm2ddlAuto) {
        this.hbm2ddlAuto = hbm2ddlAuto;
        return this;
    }

    @Override
    public String getDriverClassName() { return driverClassName; }

    @Override
    public String getUrl() { return url; }

    @Override
    public String getUsername() { return username; }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getDialect() { return dialect; }

    @Override
    public boolean showSql() {
        return showSql != null ? showSql : DatabaseConfiguration.super.showSql();
    }

    @Override
    public boolean formatSql() {
        return formatSql != null ? formatSql : DatabaseConfiguration.super.formatSql();
    }

    @Override
    public String getHbm2ddlAuto() {
        return hbm2ddlAuto != null ? hbm2ddlAuto : DatabaseConfiguration.super.getHbm2ddlAuto();
    }

}
