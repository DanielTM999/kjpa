package dtm.database.repository.prototype;

import java.util.Map;


public record RepositoryMetainfo (
        String methodName,
        OperationType operationType,
        String queryTemplate,
        boolean isNative,
        ReturnStrategy returnStrategy,
        Class<?> resultType,
        Map<Integer, String> paramMap,
        boolean autoFlush
) {

    @Override
    public String toString() {
        return "RepositoryMetainfo{" +
                "methodName='" + methodName + '\'' +
                ", operationType=" + operationType +
                ", queryTemplate='" + queryTemplate + '\'' +
                ", isNative=" + isNative +
                ", returnStrategy=" + returnStrategy +
                ", resultType=" + resultType +
                ", paramMap=" + paramMap +
                '}';
    }
}
