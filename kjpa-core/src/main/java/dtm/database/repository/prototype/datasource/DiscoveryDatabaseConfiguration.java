package dtm.database.repository.prototype.datasource;

public class DiscoveryDatabaseConfiguration implements DatabaseConfiguration {

    private final String driverClassName;
    private final String url;
    private final String username;
    private final String password;

    private Boolean showSql;
    private Boolean formatSql;
    private String hbm2ddlAuto;

    public DiscoveryDatabaseConfiguration(String driverClassName, String url, String username, String password) {
        this.driverClassName = driverClassName;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public DiscoveryDatabaseConfiguration withShowSql(boolean showSql) {
        this.showSql = showSql;
        return this;
    }

    public DiscoveryDatabaseConfiguration withFormatSql(boolean formatSql) {
        this.formatSql = formatSql;
        return this;
    }

    public DiscoveryDatabaseConfiguration withHbm2ddlAuto(String hbm2ddlAuto) {
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
    public String getDialect() {
        if (url == null) return null;

        String cleanUrl = url.toLowerCase().trim();

        if (cleanUrl.startsWith("jdbc:postgresql:")) {
            return "org.hibernate.dialect.PostgreSQLDialect";
        }
        else if (cleanUrl.startsWith("jdbc:mysql:") || cleanUrl.startsWith("jdbc:mariadb:")) {
            return "org.hibernate.dialect.MySQLDialect";
        }
        else if (cleanUrl.startsWith("jdbc:oracle:")) {
            return "org.hibernate.dialect.Oracle12cDialect";
        }
        else if (cleanUrl.startsWith("jdbc:sqlserver:")) {
            return "org.hibernate.dialect.SQLServerDialect";
        }
        else if (cleanUrl.startsWith("jdbc:h2:")) {
            return "org.hibernate.dialect.H2Dialect";
        }

        throw new IllegalArgumentException("Não foi possível detectar o dialeto automaticamente para a URL: " + url);
    }

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
