package dtm.database.repository.prototype;

public interface RepositoryMetaInfoManager {
    RepositoryMetainfo getByMethod(String methodName);
    Class<?> getEntityClass();
    Class<?> getIdClass();
}
