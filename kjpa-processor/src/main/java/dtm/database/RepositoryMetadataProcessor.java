package dtm.database;

import dtm.database.annotations.NonQueryable;
import dtm.database.annotations.Query;
import dtm.database.annotations.QueryParam;
import dtm.database.annotations.Repository;
import dtm.database.internal.JavaCode;
import dtm.database.internal.ParsedQueryMethod;
import dtm.database.repository.CrudRepository;
import dtm.database.utils.DeclarationTypeProcessorUtils;
import dtm.database.utils.RepositoryMetadataWriter;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static dtm.database.utils.RepositoryMetadataExtractor.*;
import static dtm.database.utils.DeclarationTypeProcessorUtils.*;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class RepositoryMetadataProcessor extends AbstractProcessor {

    private static final Class<?> baseRepoClass = CrudRepository.class;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        if(annotations.isEmpty()){
            note("RepositoryMetadataProcessor iniciado (nenhuma annotation ainda)");
            return false;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(Repository.class)) {

            if (!validElementIsInterface(element)) continue;
            TypeElement typeElement = (TypeElement) element;

            note("KJPA: Processando repositorio: " + typeElement.getQualifiedName());

            if(!implementsInterfaceOrSuperclass(typeElement, baseRepoClass, processingEnv)){
                error(
                        typeElement,
                        "Invalid @Repository declaration. %s must extend %s",
                        typeElement.getQualifiedName(),
                        CrudRepository.class.getName()
                );
                return true;
            }

            DeclaredType declaredType = findFirstInterfaceOrSuperClassWithGenericExtending(typeElement, baseRepoClass, processingEnv);
            if(!validDeclaredTypeSuperclass(typeElement, declaredType)) return true;

            List<TypeMirror> genericTypesList = getGenericTypes(declaredType);
            if(!validDeclaredGenericTypesListSuperclass(typeElement, genericTypesList)) return true;

            List<ExecutableElement> methodsToValid = getMethods(typeElement, true, List.of(baseRepoClass));
            if(!validRepositoryMethods(typeElement, methodsToValid, genericTypesList.getFirst())) return true;

            List<ExecutableElement> methodsToWriteMetaInfo = getMethods(typeElement, true)
                    .stream()
                    .filter(e -> !isAnnotationPresent(e, NonQueryable.class))
                    .toList();

            RepositoryMetadataWriter repositoryMetadataWriter = new RepositoryMetadataWriter(
                    processingEnv,
                    methodsToWriteMetaInfo,
                    genericTypesList.getFirst(),
                    genericTypesList.getLast(),
                    typeElement
            );

            JavaCode javaCode = repositoryMetadataWriter.getJavaCode();
            String fullClassName = javaCode.classFullName();

            if (processingEnv.getElementUtils().getTypeElement(fullClassName) != null) {
                warningMadatory(typeElement, "A classe de metadados '%s' ja existe no classpath. Ignorando geracao.", fullClassName);
                continue;
            }

            try {
                JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(fullClassName);
                note("KJPA: Escrevendo arquivo fonte: " + fullClassName);
                try (java.io.Writer writer = builderFile.openWriter()) {
                    writer.write(javaCode.code());
                }
                note("KJPA: [Sucesso] Metadados gerados para " + typeElement.getQualifiedName());
            }catch (IOException e) {
                error(typeElement, "Falha fatal ao escrever o arquivo %s: %s", fullClassName, e.getMessage());
                return true;
            }
        }

        return true;
    }



    //implements
    private boolean validElementIsInterface(Element element){
        if (element.getKind() != ElementKind.INTERFACE) {
            warningMadatory(
                    element,
                    "@Repository so pode ser usado em interfaces, encontrado: %s",
                    element.getKind()
            );

            return false;
        }

        return true;
    }

    private boolean validDeclaredTypeSuperclass(TypeElement typeElement, DeclaredType declaredType){
        if(declaredType == null){
            error(
                    typeElement,
                    "Declaracao invalida de @Repository. %s deve declarar os tipos genéricos ao estender %s. " +
                            "Exemplo: interface %s extends %s<Entidade, ID>",
                    typeElement.getQualifiedName(),
                    CrudRepository.class.getSimpleName(),
                    typeElement.getSimpleName(),
                    CrudRepository.class.getSimpleName()
            );
            return false;
        }
        return true;
    }


    //signatire
    private boolean validDeclaredGenericTypesListSuperclass(TypeElement typeElement, List<TypeMirror> genericTypesList){
        if(genericTypesList.isEmpty()){
            error(
                    typeElement,
                    "Declaracao invalida de @Repository. A interface %s deve declarar tipos genéricos concretos ao estender o repositório.",
                    typeElement.getQualifiedName()
            );
            return false;
        }

        if(genericTypesList.size() != 2){
            error(
                    typeElement,
                    "Declaracao invalida de @Repository em %s. " +
                            "O repositorio deve declarar exatamente 2 tipos genericos: " +
                            "<Entidade, ID>. Encontrado: %d.",
                    typeElement.getQualifiedName(),
                    genericTypesList.size()
            );
            return false;
        }

        for(TypeMirror arg : genericTypesList){
            if (arg.getKind() == TypeKind.TYPEVAR) {
                error(
                        typeElement,
                        "Declaracao invalida de @Repository em %s. " +
                                "Tipos genericos como '%s' nao sao permitidos. " +
                                "Use tipos concretos (ex: <Usuario, Long>, <Order, String>).",
                        typeElement.getQualifiedName(),
                        arg.toString()
                );
                return false;
            }else if(arg.getKind() == TypeKind.WILDCARD){
                error(
                        typeElement,
                        "Declaracao invalida de @Repository em %s. " +
                                "Wildcards genericos ('? extends', '? super') nao sao permitidos. " +
                                "Declare tipos concretos.",
                        typeElement.getQualifiedName()
                );
                return false;
            }
        }

        TypeMirror entityType = genericTypesList.get(0);
        if(!validEntityType(typeElement, entityType)) return false;
        TypeMirror entityIdType = genericTypesList.get(1);
        return validEntityIdType(typeElement, entityIdType, entityType);
    }

    private boolean validEntityType(TypeElement repositoryElement, TypeMirror entityType){
        if (!(entityType instanceof DeclaredType declaredType)) {
            error(
                    repositoryElement,
                    "Declaracao invalida de @Repository em %s. O tipo da entidade deve ser uma classe concreta.",
                    repositoryElement.getQualifiedName()
            );
            return false;
        }

        Element element = declaredType.asElement();

        if (!(element instanceof TypeElement entityElement)) {
            error(
                    repositoryElement,
                    "Declaracao invalida de @Repository em %s. O tipo da entidade nao e uma classe valida.",
                    repositoryElement.getQualifiedName()
            );
            return false;
        }

        if (!isAnnotationPresent(entityElement, Entity.class)){
            error(
                    repositoryElement,
                    "Declaracao invalida de @Repository em %s. " +
                            "O tipo %s nao esta anotado com @Entity.",
                    repositoryElement.getQualifiedName(),
                    entityElement.getQualifiedName()
            );
            return false;
        }

        return true;
    }

    private boolean validEntityIdType(TypeElement repositoryElement, TypeMirror entityIdType, TypeMirror entityType){
        if (!(entityType instanceof DeclaredType declaredEntityType)) {
            error(
                    repositoryElement,
                    "Declaracao invalida de @Repository em %s. O tipo da entidade deve ser uma classe concreta.",
                    repositoryElement.getQualifiedName()
            );
            return false;
        }

        TypeElement entityElement = (TypeElement) declaredEntityType.asElement();

        VariableElement idField = findFristFieldWithAnnotation(entityElement, Id.class);

        if(idField == null){
            error(
                    repositoryElement,
                    "Declaracao invalida de @Repository em %s. " +
                            "A entidade %s nao possui um identificador. " +
                            "Para usar um repositorio, a entidade deve declarar um campo anotado com @Id(%s).",
                    repositoryElement.getQualifiedName(),
                    entityElement.getQualifiedName(),
                    Id.class.getName()
            );
            return false;
        }

        TypeMirror fieldIdType = idField.asType();
        Types types = processingEnv.getTypeUtils();

        TypeMirror normalizedFieldIdType = normalizePrimitive(fieldIdType, types);
        TypeMirror normalizedRepoIdType  = normalizePrimitive(entityIdType, types);

        if (!types.isSameType(
                types.erasure(normalizedFieldIdType),
                types.erasure(normalizedRepoIdType)
        )) {
            error(
                    repositoryElement,
                    "Declaracao invalida de @Repository em %s. " +
                            "O tipo do identificador da entidade (%s.%s : %s) " +
                            "nao e compativel com o tipo de ID do repositorio (%s).",
                    repositoryElement.getQualifiedName(),
                    entityElement.getQualifiedName(),
                    idField.getSimpleName(),
                    fieldIdType.toString(),
                    entityIdType.toString()
            );
            return false;
        }

        return true;
    }


    //methods
    private boolean validRepositoryMethods(TypeElement repositoryElement, List<ExecutableElement> methods, TypeMirror entityType){

        for(ExecutableElement element : methods){
            String methodName = element.getSimpleName().toString();

            Set<String> baseCrudMethods = Arrays.stream(baseRepoClass.getMethods())
                    .map(Method::getName)
                    .collect(Collectors.toSet());

            if (baseCrudMethods.contains(methodName)) {
                error(element,
                        "Declaracao invalida de metodo em %s. " +
                                "O metodo '%s' e reservado para operacoes base do CrudRepository e nao pode ser redeclarado. " +
                                "Remova a assinatura do metodo para utilizar a implementacao padrao do framework.",
                        repositoryElement.getSimpleName(), methodName);
                return false;
            }

            if(isAnnotationPresent(element, NonQueryable.class)) continue;
            AnnotationMirror annotationMirror = getAnnotationPresent(element, Query.class);
            if(annotationMirror != null){
                if(!validRepositoryMethodQueryAnnotate(repositoryElement, element, annotationMirror, entityType)) return false;
            }else{
                if(!validRepositoryMethodNoQueryAnnotate(repositoryElement, element, entityType)) return false;
            }
        }
        return true;
    }


    private boolean validRepositoryMethodNoQueryAnnotate(TypeElement repositoryElement, ExecutableElement method, TypeMirror entityType){
        if(!validRepositoryMethodReturnType(repositoryElement, method, entityType)) return false;
        if(!validRepositoryMethodSignatureQueryAnnotate(repositoryElement, method, entityType)) return false;

        return true;
    }

    private boolean validRepositoryMethodQueryAnnotate(TypeElement repositoryElement, ExecutableElement method, AnnotationMirror queryAnnotation, TypeMirror entityType){
        if(!validRepositoryMethodReturnType(repositoryElement, method, entityType)) return false;
        String query = getAnnotationsValue(queryAnnotation, "value");
        Boolean isNativeQuery = getAnnotationsValue(queryAnnotation, "nativeQuery");
        if (query == null || query.isBlank()) {
            error(
                    method,
                    "Metodo de repositorio %s em %s possui @Query vazio.",
                    method.getSimpleName(),
                    repositoryElement.getQualifiedName()
            );
            return false;
        }
        Set<String> queryParams = extractQueryParameters(query);
        if(!validJpqlParamsCountAndNames(queryParams, method)) return false;

        if(Boolean.TRUE.equals(isNativeQuery)){
            if(!isValidNativeSQL(query)){
                error(method, "Query nativa invalida em %s: %s", method.getSimpleName(), query);
                return false;
            }
        }else{
            if(!isValidJPQL(query)){
                error(method, "Query JPQL invalida em %s: %s", method.getSimpleName(), query);
                return false;
            }
        }

        return true;
    }

    private boolean validRepositoryMethodReturnType(TypeElement repositoryElement, ExecutableElement method, TypeMirror entityType){
        TypeMirror returnType = method.getReturnType();
        Types types = processingEnv.getTypeUtils();

        if (returnType.getKind() == TypeKind.VOID) {
            return true;
        }

        if (types.isAssignable(
                types.erasure(returnType),
                types.erasure(entityType)
        )) {
            return true;
        }

        if (isPrimitiveAggregate(returnType)) {
            return true;
        }

        if (isPrimitiveAggregate(returnType)) {
            return true;
        }

        if (!(returnType instanceof DeclaredType declaredReturn)) {
            error(
                    repositoryElement,
                    "Declaracao invalida de @Repository em %s. " +
                            "O tipo de retorno do metodo %s (%s) nao e suportado.",
                    repositoryElement.getQualifiedName(),
                    method.getSimpleName(),
                    returnType.toString()
            );
            return false;
        }

        TypeElement returnElement = (TypeElement) declaredReturn.asElement();
        List<? extends TypeMirror> typeArgs = declaredReturn.getTypeArguments();

        if (isSameRawType(returnElement, Optional.class, processingEnv)) {
            return validateSingleGeneric(
                    repositoryElement, method, typeArgs, entityType
            );
        }

        if (DeclarationTypeProcessorUtils.isCollection(returnElement, processingEnv)) {
            return validateSingleGeneric(
                    repositoryElement, method, typeArgs, entityType
            );
        }

        error(
                repositoryElement,
                "Declaracao invalida de @Repository em %s. " +
                        "O metodo %s possui um tipo de retorno nao suportado: %s. " +
                        "Tipos permitidos: %s, Optional<%s>, Collection<%s>, List<%s>, Set<%s>, " +
                        "ou tipos primitivos/Wrapper para agregacoes (ex: long, int, boolean).",
                repositoryElement.getQualifiedName(),
                method.getSimpleName(),
                returnType.toString(),
                entityType.toString(),
                entityType.toString(),
                entityType.toString(),
                entityType.toString(),
                entityType.toString()
        );
        return false;
    }

    private boolean validRepositoryMethodSignatureQueryAnnotate(TypeElement repositoryElement, ExecutableElement method, TypeMirror entityType){
        String methodName = method.getSimpleName().toString();
        ParsedQueryMethod parsed = parseQueryMethodName(methodName);

        if (parsed == null) {
            error(
                    method,
                    "Metodo de repositorio invalido em %s. " +
                            "Nao foi possivel interpretar o nome do metodo '%s'.",
                    repositoryElement.getQualifiedName(),
                    methodName
            );
            return false;
        }

        if (!(entityType instanceof DeclaredType declaredEntity)) {
            return false;
        }

        TypeElement entityElement = (TypeElement) declaredEntity.asElement();

        List<String> props = parsed.properties();
        List<? extends VariableElement> params = method.getParameters();

        // ---------- regra: ao menos uma propriedade ----------
        if (props.isEmpty()) {
            error(
                    method,
                    "Metodo de repositorio invalido em %s. " +
                            "O metodo '%s' deve declarar ao menos um criterio (ex: findByNome).",
                    repositoryElement.getQualifiedName(),
                    methodName
            );
            return false;
        }

        // ---------- regra: quantidade de parametros ----------
        if (params.size() != props.size()) {
            error(
                    method,
                    "Metodo de repositorio invalido em %s. " +
                            "O metodo '%s' declara %d criterio(s) mas possui %d parametro(s).",
                    repositoryElement.getQualifiedName(),
                    methodName,
                    props.size(),
                    params.size()
            );
            return false;
        }

        Types types = processingEnv.getTypeUtils();

        for (int i = 0; i < props.size(); i++) {
            String property = props.get(i);
            VariableElement param = params.get(i);

            VariableElement field = findEntityField(entityElement, property);

            if (field == null) {
                error(
                        method,
                        "Metodo de repositorio invalido em %s. " +
                                "A propriedade '%s' nao existe na entidade %s.",
                        repositoryElement.getQualifiedName(),
                        property,
                        entityElement.getQualifiedName()
                );
                return false;
            }

            if (!types.isAssignable(
                    types.erasure(normalizePrimitive(param.asType(), types)),
                    types.erasure(normalizePrimitive(field.asType(), types))
            )) {
                error(
                        method,
                        "Metodo de repositorio invalido em %s. " +
                                "O parametro %d ('%s') possui tipo %s, " +
                                "mas o campo correspondente (%s.%s) possui tipo %s.",
                        repositoryElement.getQualifiedName(),
                        i + 1,
                        param.getSimpleName(),
                        param.asType(),
                        entityElement.getQualifiedName(),
                        field.getSimpleName(),
                        field.asType()
                );
                return false;
            }
        }

        // ---------- regra: operadores invalidos ----------
        List<String> ops = parsed.operators();
        for (int i = 0; i < ops.size() - 1; i++) {
            if (ops.get(i).equals(ops.get(i + 1))) {
                error(
                        method,
                        "Metodo de repositorio invalido em %s. " +
                                "Uso invalido de operadores logicos no metodo '%s'.",
                        repositoryElement.getQualifiedName(),
                        methodName
                );
                return false;
            }
        }


        return true;
    }

    private boolean validJpqlParamsCountAndNames(Set<String> queryParams, ExecutableElement method){
        List<? extends VariableElement> methodParams = method.getParameters();
        boolean valid = true;

        for (VariableElement param : methodParams) {
            AnnotationMirror queryParam = getAnnotationPresent(param, QueryParam.class);

            String paramName = null;
            if (queryParam != null) {
                paramName = getAnnotationsValue(queryParam, "value");
            }

            if(paramName == null || paramName.isEmpty()){
                paramName = param.getSimpleName().toString();
            }


            if (!queryParams.contains(paramName)) {
                error(
                        param,
                        "Parametro '%s' do metodo '%s' nao encontrado na query.",
                        paramName,
                        method.getSimpleName()
                );
                valid = false;
            }
        }

        for (String paramName : queryParams) {
            boolean found = methodParams.stream()
                    .anyMatch(p -> {
                        AnnotationMirror queryParam = getAnnotationPresent(p, QueryParam.class);
                        String paramNameVar;
                        if (queryParam == null) {
                            paramNameVar = p.getSimpleName().toString();
                        }else{
                            paramNameVar = getAnnotationsValue(queryParam, "value");
                            if (paramNameVar == null || paramNameVar.isBlank()) {
                                paramNameVar = p.getSimpleName().toString();
                            }
                        }
                        return paramNameVar.equals(paramName);
                    });
            if (!found) {
                error(
                        method,
                        "Parametro da query ':%s' nao tem correspondente anotado com @QueryParam no metodo '%s'.",
                        paramName,
                        method.getSimpleName()
                );
                valid = false;
            }
        }

        return valid;
    }

    private boolean isValidJPQL(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }

        String trimmed = query.trim().toUpperCase();
        return trimmed.startsWith("SELECT")
                || trimmed.startsWith("UPDATE")
                || trimmed.startsWith("DELETE")
                || trimmed.startsWith("FROM");
    }

    private boolean isValidNativeSQL(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }

        String trimmed = query.trim().toUpperCase();

        return trimmed.startsWith("SELECT")
                || trimmed.startsWith("INSERT")
                || trimmed.startsWith("UPDATE")
                || trimmed.startsWith("DELETE");
    }

    private boolean validateSingleGeneric(
            TypeElement repositoryElement,
            ExecutableElement method,
            List<? extends TypeMirror> generics,
            TypeMirror entityType
    ) {
        if (generics.size() != 1) {
            error(
                    repositoryElement,
                    "Declaracao invalida de @Repository em %s. " +
                            "O metodo %s deve declarar exatamente um tipo generico.",
                    repositoryElement.getQualifiedName(),
                    method.getSimpleName()
            );
            return false;
        }

        TypeMirror generic = generics.get(0);
        Types types = processingEnv.getTypeUtils();

        if (generic.getKind() == TypeKind.TYPEVAR ||
                generic.getKind() == TypeKind.WILDCARD) {
            error(
                    repositoryElement,
                    "Declaracao invalida de @Repository em %s. " +
                            "O metodo %s deve usar um tipo concreto como generico, encontrado: %s.",
                    repositoryElement.getQualifiedName(),
                    method.getSimpleName(),
                    generic.toString()
            );
            return false;
        }

        if (!types.isAssignable(
                types.erasure(generic),
                types.erasure(entityType)
        )) {
            error(
                    repositoryElement,
                    "Declaracao invalida de @Repository em %s. " +
                            "O tipo generico do metodo %s (%s) nao e compativel com a entidade %s.",
                    repositoryElement.getQualifiedName(),
                    method.getSimpleName(),
                    generic.toString(),
                    entityType.toString()
            );
            return false;
        }

        return true;
    }


    //logs
    private void log(Diagnostic.Kind kind, Element element, String message, Object... args) {
        processingEnv.getMessager().printMessage(
                kind,
                String.format(message, args),
                element
        );
    }

    private void log(Diagnostic.Kind kind,String message, Object... args) {
        processingEnv.getMessager().printMessage(
                kind,
                String.format(message, args)
        );
    }


    private void error(Element element, String message, Object... args) {
        log(Diagnostic.Kind.ERROR, element, message, args);
    }

    private void error(String message, Object... args) {
        log(Diagnostic.Kind.ERROR, message, args);
    }


    private void warning(Element element, String message, Object... args) {
        log(Diagnostic.Kind.WARNING, element, message, args);
    }

    private void warning(String message, Object... args) {
        log(Diagnostic.Kind.WARNING, message, args);
    }


    private void warningMadatory(Element element, String message, Object... args) {
        log(Diagnostic.Kind.MANDATORY_WARNING, element, message, args);
    }

    private void warningMadatory(String message, Object... args) {
        log(Diagnostic.Kind.MANDATORY_WARNING, message, args);
    }



    private void note(Element element, String message, Object... args) {
        log(Diagnostic.Kind.NOTE, element, message, args);
    }

    private void note(String message, Object... args) {
        log(Diagnostic.Kind.NOTE, message, args);
    }


}
