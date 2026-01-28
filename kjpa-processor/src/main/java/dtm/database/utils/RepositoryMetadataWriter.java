package dtm.database.utils;


import dtm.database.annotations.AutoFlush;
import dtm.database.annotations.Query;
import dtm.database.annotations.QueryParam;
import dtm.database.internal.JavaCode;
import dtm.database.internal.ParsedQueryMethod;
import dtm.database.repository.prototype.OperationType;
import dtm.database.repository.prototype.RepositoryMetaInfoManager;
import dtm.database.repository.prototype.RepositoryMetainfo;
import dtm.database.repository.prototype.ReturnStrategy;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static dtm.database.utils.DeclarationTypeProcessorUtils.*;
import static dtm.database.utils.RepositoryMetadataExtractor.parseQueryMethodName;

public final class RepositoryMetadataWriter {
    private static final String GENERATED_PACKAGE = "dtm.database.repository.generated";
    private final ProcessingEnvironment processingEnv;
    private final List<ExecutableElement> methods;
    private final String entityName;
    private final String entitySimpleName;
    private final TypeMirror idEntityType;
    private final TypeMirror entityType;
    private final TypeElement repositoryElement;
    private final List<Object> templateArgs;
    private final Types types;
    private final Types typeUtils;

    public RepositoryMetadataWriter(
            ProcessingEnvironment processingEnv,
            List<ExecutableElement> methods,
            TypeMirror entityType,
            TypeMirror idEntityType,
            TypeElement repositoryElement
    ) {
        this.processingEnv = processingEnv;
        this.types = processingEnv.getTypeUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        this.methods = methods;
        this.idEntityType = idEntityType;
        this.entityType = entityType;
        this.repositoryElement = repositoryElement;
        this.entityName = entityType.toString();
        this.entitySimpleName = ((DeclaredType) entityType).asElement().getSimpleName().toString();
        this.templateArgs = new ArrayList<>();
    }

    public JavaCode getJavaCode(){

        String className = repositoryElement.getSimpleName() + "MetaData";

        templateArgs.clear();
        templateArgs.add(GENERATED_PACKAGE);
        templateArgs.add(generateImports());
        templateArgs.add(className);
        templateArgs.add(generateMetadataEntries());
        templateArgs.add(entityName+".class");
        templateArgs.add(idEntityType+".class");


        String baseTemplateClass = getTemplateClass();
        String content = String.format(
                baseTemplateClass,
                templateArgs.toArray(Object[]::new)
        );



        return new JavaCode(className, GENERATED_PACKAGE, content);
    }

    private String getTemplateClass(){
        return
        """
        package %1$s;
        
        %2$s
        
        public final class %3$s implements RepositoryMetaInfoManager {
             private static final %3$s INSTANCE = new %3$s();
        
             private final Map<String, RepositoryMetainfo> repositoryMetainfoMap;
        
             private %3$s() {
                this.repositoryMetainfoMap = new ConcurrentHashMap<>(){{
                    %4$s
                }};
             }
        
             public static %3$s getInstance() {
                return INSTANCE;
             }
        
            @Override
            public RepositoryMetainfo getByMethod(String methodName){
                return repositoryMetainfoMap.get(methodName);
            }

            @Override
            public Class<?> getEntityClass(){
                return %5$s;
            }

            @Override
            public Class<?> getIdClass(){
                return %6$s;
            }
        
        }
        """;
    }

    private String generateImports(){
        StringBuilder stringBuilder = new StringBuilder();
        List<Class<?>> classesToImport = List.of(
                RepositoryMetaInfoManager.class,
                RepositoryMetainfo.class,
                ConcurrentHashMap.class,
                ReturnStrategy.class,
                OperationType.class,
                Map.class
        );

        for(Class<?> importClass : classesToImport){
            stringBuilder.append("import ")
                    .append(importClass.getName())
                    .append(";\n");
        }

        if (entityType != null) {
            stringBuilder.append("import ")
                    .append(entityName)
                    .append(";\n");
        }

        return stringBuilder.toString();
    }

    private String generateMetadataEntries() {
        StringBuilder sb = new StringBuilder();
        for (ExecutableElement method : methods) {

            if(isCrudRepositoryMethod(method)){
                generateMetadataEntriesOfCrudRepositoryMethod(method, sb);
            }else{
                generateMetadataEntriesOfCustomMethod(method, sb);
            }

        }

        return sb.toString();
    }

    private void generateMetadataEntriesOfCustomMethod(ExecutableElement method, StringBuilder sb) {
        AnnotationMirror annotationMirror = getAnnotationPresent(method, Query.class);

        boolean isNativeQuery = false;
        String query;
        if(annotationMirror != null){
            isNativeQuery = isNativeQuery(annotationMirror);
            query = extractQueryByAnnotation(method, annotationMirror);
        }else{
            query = generateQueryByMethodSignature(method);
        }
        String paramMapCode = generateParamMapCodeFromMethod(method);
        TypeMirror returnType = method.getReturnType();
        String returnStrategyEnum;
        String resultTypeClass;
        String operationTypeStr = OperationType.QUERY.name();
        boolean isAutoFlush = isAnnotationPresent(method, AutoFlush.class);

        if (returnType.getKind() == TypeKind.VOID) {
            returnStrategyEnum = ReturnStrategy.VOID.name();
            resultTypeClass = "Void.class";
        }
        else if (isCollection(returnType, processingEnv)) {
            returnStrategyEnum = ReturnStrategy.COLLECTION.name();
            resultTypeClass = getGenericTypes(returnType).getFirst().toString();
        }
        else if (isOptional(returnType, processingEnv)) {
            returnStrategyEnum = ReturnStrategy.OPTIONAL.name();
            resultTypeClass = getGenericTypes(returnType).getFirst().toString();
        }
        else if (returnType.getKind().isPrimitive()) {
            returnStrategyEnum = ReturnStrategy.PRIMITIVE.name();
            resultTypeClass = normalizePrimitive(returnType, types).toString();
        }
        else {
            returnStrategyEnum = ReturnStrategy.SINGLE_ENTITY.name();
            resultTypeClass = returnType.toString();
        }
        String methodSignature = getMethodSignatureKey(method);
        appendEntry(sb, methodSignature, operationTypeStr, query, isNativeQuery, isAutoFlush, returnStrategyEnum, resultTypeClass, paramMapCode);
    }

    private String getMethodSignatureKey(ExecutableElement method) {
        String name = method.getSimpleName().toString();

        ExecutableType executableType = (ExecutableType) processingEnv.getTypeUtils().asMemberOf((DeclaredType) repositoryElement.asType(), method);

        String params = executableType.getParameterTypes().stream()
                .map(type -> processingEnv.getTypeUtils().erasure(type).toString())
                .collect(Collectors.joining(","));

        return String.format("%s[%s]", name, params);
    }

    private String generateParamMapCodeFromMethod(ExecutableElement method) {
        List<? extends VariableElement> methodParams = method.getParameters();

        if (methodParams.isEmpty()) {
            return "java.util.Map.of()";
        }

        StringBuilder sb = new StringBuilder("java.util.Map.of(");
        for (int i = 0; i < methodParams.size(); i++) {
            VariableElement param = methodParams.get(i);
            AnnotationMirror queryParam = getAnnotationPresent(param, QueryParam.class);

            String paramName = null;
            if (queryParam != null) {
                paramName = getAnnotationsValue(queryParam, "value");
            }

            if(paramName == null || paramName.isEmpty()){
                paramName = param.getSimpleName().toString();
            }

            sb.append(i)
                    .append(", \"")
                    .append(paramName)
                    .append("\"");

            if (i < methodParams.size() - 1) {
                sb.append(", ");
            }

        }

        sb.append(")");
        return sb.toString();
    }

    private String generateQueryByMethodSignature(ExecutableElement method){
        String methodName = method.getSimpleName().toString();
        ParsedQueryMethod parsed = parseQueryMethodName(methodName);
        if (parsed == null) return "";

        StringBuilder query = new StringBuilder();
        String alias = "e";

        switch (parsed.prefix()) {
            case "findBy":
                query.append("SELECT ").append(alias).append(" FROM ").append(entitySimpleName).append(" ").append(alias);
                break;
            case "countBy", "existsBy":
                query.append("SELECT COUNT(").append(alias).append(") FROM ").append(entitySimpleName).append(" ").append(alias);
                break;
            case "deleteBy":
                query.append("DELETE FROM ").append(entitySimpleName).append(" ").append(alias);
                break;
        }

        List<String> properties = parsed.properties();
        List<String> operators = parsed.operators();

        if (!properties.isEmpty()) {
            query.append(" WHERE ");

            for (int i = 0; i < properties.size(); i++) {
                String rawProp = properties.get(i);

                String fieldName = Character.toLowerCase(rawProp.charAt(0)) + rawProp.substring(1);

                query.append(alias).append(".").append(fieldName)
                        .append(" = :").append(fieldName);

                if (i < properties.size() - 1) {
                    String op = (i < operators.size()) ? operators.get(i).toUpperCase() : "AND";
                    query.append(" ").append(op).append(" ");
                }
            }
        }

        return query.toString();
    }

    private String extractQueryByAnnotation(ExecutableElement method, AnnotationMirror annotationMirror){
        return getAnnotationsValue(annotationMirror, "value");
    }

    private boolean isNativeQuery(AnnotationMirror annotationMirror){
        Boolean isNativeQuery = getAnnotationsValue(annotationMirror, "nativeQuery");
        return Boolean.TRUE.equals(isNativeQuery);
    }

    private void generateMetadataEntriesOfCrudRepositoryMethod(ExecutableElement method, StringBuilder sb){
        String name = method.getSimpleName().toString();
        List<? extends VariableElement> params = method.getParameters();

        boolean isAutoFlush = isAnnotationPresent(method, AutoFlush.class);

        String selectAll = "SELECT e FROM " + entitySimpleName + " e";
        String selectById = "SELECT e FROM " + entitySimpleName + " e WHERE e.id = :id";
        String deleteByIdQuery = "DELETE FROM " + entitySimpleName + " e WHERE e.id = :id";
        String countQuery = "SELECT COUNT(e) FROM " + entitySimpleName + " e";
        String emptyQuery = "";

        switch (name) {
            case "save", "saveAndFlush": {
                String paramName = params.getFirst().getSimpleName().toString();
                appendEntry(sb, name, OperationType.SAVE.name(), emptyQuery, false, isAutoFlush, ReturnStrategy.SINGLE_ENTITY.name(), entityName + ".class", "java.util.Map.of(0, \"" + paramName + "\")");
                break;
            }
            case "saveAll": {
                String paramName = params.getFirst().getSimpleName().toString();
                appendEntry(sb, name, OperationType.SAVE_ALL.name(), emptyQuery, false, isAutoFlush, ReturnStrategy.COLLECTION.name(), entityName + ".class", "java.util.Map.of(0, \"" + paramName + "\")");
                break;
            }
            case "findById": {
                String paramName = "id";
                appendEntry(sb, name, OperationType.FIND_BY_ID.name(), selectById, false, isAutoFlush, ReturnStrategy.OPTIONAL.name(), entityName + ".class", "java.util.Map.of(0, \"" + paramName + "\")");
                break;
            }
            case "deleteAndFlush", "delete" : {
                String paramName = params.getFirst().getSimpleName().toString();
                appendEntry(sb, name, OperationType.DELETE.name(), emptyQuery, false, isAutoFlush, ReturnStrategy.VOID.name(), Void.class.getName(), "java.util.Map.of(0, \"" + paramName + "\")");
                break;
            }
            case "deleteById": {
                String paramName = "id";
                appendEntry(sb, name, OperationType.DELETE_BY_ID.name(), deleteByIdQuery, false, isAutoFlush, ReturnStrategy.VOID.name(), Void.class.getName(), "java.util.Map.of(0, \"" + paramName + "\")");
                break;
            }
            case "findAll": {
                appendEntry(sb, name, OperationType.FIND_ALL.name(), selectAll, false, isAutoFlush, ReturnStrategy.COLLECTION.name(), entityName + ".class", "java.util.Map.of()");
                break;
            }
            case "count": {
                appendEntry(sb, name, OperationType.COUNT.name(), countQuery, false, isAutoFlush, ReturnStrategy.PRIMITIVE.name(), Long.class.getName(), "java.util.Map.of()");
                break;
            }
        }
    }

    private boolean isCrudRepositoryMethod(ExecutableElement method){
        String name = method.getSimpleName().toString();
        ExecutableType executavelType = (ExecutableType) typeUtils.asMemberOf((DeclaredType) repositoryElement.asType(), method);
        List<? extends TypeMirror> resolvedParams = executavelType.getParameterTypes();
        if (resolvedParams.isEmpty()) {
            return switch (name) {
                case "findAll", "count", "deleteAll" -> true;
                default -> false;
            };
        }

        if (resolvedParams.size() == 1) {
            TypeMirror paramType = resolvedParams.getFirst();

            return switch (name) {
                case "save", "delete", "saveAndFlush", "deleteAndFlush" -> typeUtils.isSameType(paramType, entityType);
                case "saveAll" -> isCollectionOf(paramType, entityType);
                case "deleteById", "findById", "existsById" -> typeUtils.isSameType(paramType, idEntityType);
                default -> false;
            };
        }

        return false;
    }

    private boolean isCollectionOf(TypeMirror typeToCheck, TypeMirror genericType) {

        if (typeToCheck.getKind() != TypeKind.DECLARED) return false;

        DeclaredType declaredType = (DeclaredType) typeToCheck;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

        if (typeArguments.size() != 1) return false;

        if (!typeUtils.isSameType(typeArguments.getFirst(), genericType)) return false;

        String rawType = ((TypeElement) declaredType.asElement()).getQualifiedName().toString();
        return rawType.equals("java.util.Collection") || rawType.equals("java.util.List");
    }

    private void appendEntry(StringBuilder sb, String methodName, String operationTypeStr, String query, boolean isNative, String returnStrategyEnum, String resultTypeClass, String paramMapCode) {
        appendEntry(sb, methodName, operationTypeStr, query, isNative, false, returnStrategyEnum, resultTypeClass, paramMapCode);
    }

    private void appendEntry(StringBuilder sb, String methodName, String operationTypeStr, String query, boolean isNative, boolean isAutoFlush ,String returnStrategyEnum, String resultTypeClass, String paramMapCode) {

        if(!resultTypeClass.endsWith(".class")){
            resultTypeClass += ".class";
        }

        sb.append(String.format("""
                this.put("%s", new RepositoryMetainfo(
                    "%s",
                    OperationType.%s,
                    "%s",
                    %b,
                    ReturnStrategy.%s,
                    %s,
                    %s,
                    %b
                ));
                """,
                methodName,
                methodName,
                operationTypeStr,
                escape(query),
                isNative,
                returnStrategyEnum,
                resultTypeClass,
                paramMapCode,
                isAutoFlush
        ));
    }

    private String escape(String s) {
        if (s == null) return "";

        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");
    }

}
