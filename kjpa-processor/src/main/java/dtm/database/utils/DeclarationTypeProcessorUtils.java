package dtm.database.utils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public final class DeclarationTypeProcessorUtils {

    private DeclarationTypeProcessorUtils(){
        throw new UnsupportedOperationException("utility class");
    }


    public static boolean isAnnotationPresent(Element sourceElement, Class<? extends Annotation> annotationClass){
        if (sourceElement == null || annotationClass == null) {
            return false;
        }

        String annotationName = annotationClass.getCanonicalName();

        for(AnnotationMirror annotationMirror : sourceElement.getAnnotationMirrors()){
            Element annotationElement = annotationMirror
                    .getAnnotationType()
                    .asElement();

            if (annotationElement instanceof TypeElement annotationType) {
                if (annotationType.getQualifiedName().contentEquals(annotationName)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static AnnotationMirror getAnnotationPresent(Element sourceElement, Class<? extends Annotation> annotationClass){
        if (sourceElement == null || annotationClass == null) {
            return null;
        }

        String annotationName = annotationClass.getCanonicalName();

        for(AnnotationMirror annotationMirror : sourceElement.getAnnotationMirrors()){
            Element annotationElement = annotationMirror
                    .getAnnotationType()
                    .asElement();

            if (annotationElement instanceof TypeElement annotationType) {
                if (annotationType.getQualifiedName().contentEquals(annotationName)) {
                    return annotationMirror;
                }
            }
        }

        return null;
    }

    public static boolean implementsInterfaceOrSuperclass(TypeElement sourceElement, Class<?> targetDeclarationClass, ProcessingEnvironment processingEnv){
        Types types = processingEnv.getTypeUtils();

        TypeElement targetElement = processingEnv
                .getElementUtils()
                .getTypeElement(targetDeclarationClass.getCanonicalName());

        if (targetElement == null) {
            return false;
        }

        TypeMirror targetType = types.erasure(targetElement.asType());

        return hasInterfaceRecursive(
                types.erasure(sourceElement.asType()),
                targetType,
                processingEnv
        );
    }

    public static DeclaredType findInterfaceOrSuperclass(TypeElement sourceElement, Class<?> targetDeclarationClass, ProcessingEnvironment processingEnv){
        TypeElement targetElement = processingEnv
                .getElementUtils()
                .getTypeElement(targetDeclarationClass.getCanonicalName());

        if (targetElement == null) {
            return null;
        }

        return findRecursive(
                sourceElement.asType(),
                targetElement,
                processingEnv
        );
    }

    public static DeclaredType findFirstInterfaceOrSuperClassWithGenericExtending(
            TypeElement sourceElement,
            Class<?> targetDeclarationClass,
            ProcessingEnvironment processingEnv
    ){
        Types types = processingEnv.getTypeUtils();

        TypeElement targetElement = processingEnv
                .getElementUtils()
                .getTypeElement(targetDeclarationClass.getCanonicalName());

        if (targetElement == null) {
            return null;
        }

        return findInterfaceOrSuperclassGenericExtendingRecursive(
                sourceElement.asType(),
                targetDeclarationClass,
                processingEnv
        );
    }

    public static List<TypeMirror> getGenericTypes(DeclaredType declaredType){
        if (declaredType == null) {
            return List.of();
        }

        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

        if (typeArguments == null || typeArguments.isEmpty()) {
            return List.of();
        }

        return List.copyOf(typeArguments);
    }

    public static List<TypeMirror> getGenericTypes(TypeMirror typeMirror){
        if (typeMirror == null) {
            return List.of();
        }

        if (typeMirror.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

            if (typeArguments == null || typeArguments.isEmpty()) {
                return List.of();
            }

            return List.copyOf(typeArguments);
        }
        return List.of();
    }

    public static boolean isOptional(
            TypeMirror typeMirror,
            ProcessingEnvironment processingEnv
    ) {
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }

        Types types = processingEnv.getTypeUtils();
        Elements elements = processingEnv.getElementUtils();

        TypeElement optionalElement = elements.getTypeElement(Optional.class.getCanonicalName());

        if (optionalElement == null) {
            return false;
        }

        return types.isSameType(
                types.erasure(typeMirror),
                types.erasure(optionalElement.asType())
        );
    }

    public static String getSimpleName(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            return ((DeclaredType) type).asElement().getSimpleName().toString();
        }
        return type.toString();
    }

    public static VariableElement findFristFieldWithAnnotation(
            TypeElement sourceElement,
            Class<? extends Annotation> annotationClass
    ){
        if (sourceElement == null || annotationClass == null) {
            return null;
        }

        for (Element enclosed : sourceElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) {
                continue;
            }

            VariableElement field = (VariableElement) enclosed;
            if(isAnnotationPresent(field, annotationClass)) {
                return field;
            }
        }

        return null;
    }

    public static TypeMirror normalizePrimitive(TypeMirror type, Types types) {
        if (type.getKind().isPrimitive()) {
            return types.boxedClass((javax.lang.model.type.PrimitiveType) type).asType();
        }
        return type;
    }

    public static List<ExecutableElement> getMethods(Element element, boolean includeHierarchy){
        return getMethods(element, includeHierarchy, List.of());
    }

    public static List<ExecutableElement> getMethods(Element element, boolean includeHierarchy, List<Class<?>> ignoreOfClass) {
        if (!(element instanceof TypeElement typeElement)) {
            return List.of();
        }

        if(ignoreOfClass == null){
            ignoreOfClass = List.of();
        }

        List<ExecutableElement> methods = new ArrayList<>();
        Set<String> signatures = new HashSet<>();

        collectMethodsRecursive(
                typeElement,
                includeHierarchy,
                ignoreOfClass,
                methods,
                signatures
        );

        return methods;
    }

    public static boolean isPrimitiveAggregate(TypeMirror type) {
        return switch (type.getKind()) {
            case INT, LONG, SHORT, BYTE, DOUBLE, FLOAT, BOOLEAN -> true;
            default -> false;
        };
    }

    private boolean isBoxedAggregate(TypeMirror type, Elements elements, Types types) {
        type = normalizePrimitive(type, types);
        if (!(type instanceof DeclaredType declared)) {
            return false;
        }

        String name = ((TypeElement) declared.asElement())
                .getQualifiedName()
                .toString();

        return name.equals(Integer.class.getName())
                || name.equals(Long.class.getName())
                || name.equals(Short.class.getName())
                || name.equals(Byte.class.getName())
                || name.equals(Double.class.getName())
                || name.equals(Float.class.getName())
                || name.equals(Boolean.class.getName())
                || name.equals(BigInteger.class.getName())
                || name.equals(BigDecimal.class.getName());
    }

    public static boolean isSameRawType(
            TypeElement element,
            Class<?> targetClass,
            ProcessingEnvironment processingEnv
    ) {
        if (element == null || targetClass == null) {
            return false;
        }

        String elementName = element.getQualifiedName().toString();
        return elementName.equals(targetClass.getCanonicalName());
    }

    public static boolean isCollection(
            TypeElement element,
            ProcessingEnvironment processingEnv
    ) {
        Types types = processingEnv.getTypeUtils();
        Elements elements = processingEnv.getElementUtils();

        TypeElement collectionElement =
                elements.getTypeElement(Collection.class.getCanonicalName());

        if (collectionElement == null) {
            return false;
        }

        return types.isAssignable(
                types.erasure(element.asType()),
                types.erasure(collectionElement.asType())
        );
    }

    public static boolean isCollection(
            TypeMirror typeMirror,
            ProcessingEnvironment processingEnv
    ) {
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }

        Types types = processingEnv.getTypeUtils();
        Elements elements = processingEnv.getElementUtils();

        TypeElement collectionElement =
                elements.getTypeElement(Collection.class.getCanonicalName());

        if (collectionElement == null) {
            return false;
        }

        return types.isAssignable(
                types.erasure(typeMirror),
                types.erasure(collectionElement.asType())
        );
    }

    public static VariableElement findEntityField(TypeElement entityElement, String property) {
        String fieldName =
                Character.toLowerCase(property.charAt(0)) + property.substring(1);

        for (Element e : entityElement.getEnclosedElements()) {
            if (e.getKind() == ElementKind.FIELD &&
                    e.getSimpleName().contentEquals(fieldName)) {
                return (VariableElement) e;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getAnnotationsValue(AnnotationMirror annotationMirror, String argsName){
        try{
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet()) {

                String name = entry.getKey().getSimpleName().toString();

                if (argsName.equals(name)) {
                    return (T)entry.getValue().getValue();
                }
            }
        }catch (Exception ignored){}
        return null;
    }


    private static boolean hasInterfaceRecursive(
            TypeMirror current,
            TypeMirror target,
            ProcessingEnvironment processingEnv
    ) {
        Types types = processingEnv.getTypeUtils();

        if (types.isSameType(current, target)) {
            return true;
        }

        if (!(current instanceof DeclaredType declaredType)) {
            return false;
        }

        TypeElement element = (TypeElement) declaredType.asElement();

        for (TypeMirror iface : element.getInterfaces()) {
            if (hasInterfaceRecursive(types.erasure(iface), target, processingEnv)) {
                return true;
            }
        }

        TypeMirror superClass = element.getSuperclass();
        if (!superClass.getKind().isPrimitive()) {
            return hasInterfaceRecursive(types.erasure(superClass), target, processingEnv);
        }

        return false;
    }


    private static DeclaredType findInterfaceOrSuperclassGenericExtendingRecursive(
            TypeMirror current,
            Class<?> targetDeclarationClass,
            ProcessingEnvironment processingEnv
    ){
        if (!(current instanceof DeclaredType declaredType)) {
            return null;
        }

        TypeElement element = (TypeElement) declaredType.asElement();

        for (TypeMirror iface : element.getInterfaces()) {

            if (!(iface instanceof DeclaredType ifaceDeclared)) {
                continue;
            }
            TypeElement ifaceElement = (TypeElement) ifaceDeclared.asElement();
            if (!implementsInterfaceOrSuperclass(
                    ifaceElement,
                    targetDeclarationClass,
                    processingEnv
            )) {
                continue;
            }

            if (!ifaceDeclared.getTypeArguments().isEmpty()) {
                return ifaceDeclared;
            }

            DeclaredType nested = findInterfaceOrSuperclassGenericExtendingRecursive(
                    ifaceDeclared,
                    targetDeclarationClass,
                    processingEnv
            );
            if (nested != null) {
                return nested;
            }
        }

        TypeMirror superClass = element.getSuperclass();
        if (superClass.getKind().isPrimitive()) {
            return null;
        }

        if (superClass instanceof DeclaredType superDeclared) {

            TypeElement superElement = (TypeElement) superDeclared.asElement();

            if (implementsInterfaceOrSuperclass(
                    superElement,
                    targetDeclarationClass,
                    processingEnv
            )) {

                if (!superDeclared.getTypeArguments().isEmpty()) {
                    return superDeclared;
                }

                return findInterfaceOrSuperclassGenericExtendingRecursive(
                        superDeclared,
                        targetDeclarationClass,
                        processingEnv
                );
            }
        }

        return null;
    }

    private static DeclaredType findRecursive(
            TypeMirror current,
            TypeElement targetElement,
            ProcessingEnvironment processingEnv
    ) {
        Types types = processingEnv.getTypeUtils();

        if (!(current instanceof DeclaredType declaredType)) {
            return null;
        }

        TypeElement currentElement = (TypeElement) declaredType.asElement();

        if (types.isSameType(
                types.erasure(declaredType),
                types.erasure(targetElement.asType())
        )) {
            return declaredType;
        }

        for (TypeMirror iface : currentElement.getInterfaces()) {
            DeclaredType found = findRecursive(iface, targetElement, processingEnv);
            if (found != null) {
                return found;
            }
        }

        TypeMirror superClass = currentElement.getSuperclass();
        if (!superClass.getKind().isPrimitive()) {
            return findRecursive(superClass, targetElement, processingEnv);
        }

        return null;
    }

    private static void collectMethodsRecursive(
            TypeElement typeElement,
            boolean includeHierarchy,
            List<Class<?>> ignoreOfClass,
            List<ExecutableElement> result,
            Set<String> signatures
    ) {
        if (shouldIgnore(typeElement, ignoreOfClass)) {
            return;
        }

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;

                String signature = buildMethodSignature(method);
                if (signatures.add(signature)) {
                    result.add(method);
                }
            }
        }

        if (!includeHierarchy) {
            return;
        }

        for (TypeMirror iface : typeElement.getInterfaces()) {
            if (iface instanceof DeclaredType declaredIface) {
                collectMethodsRecursive(
                        (TypeElement) declaredIface.asElement(),
                        true,
                        ignoreOfClass,
                        result,
                        signatures
                );
            }
        }

        TypeMirror superClass = typeElement.getSuperclass();
        if (superClass instanceof DeclaredType declaredSuper) {
            TypeElement superElement = (TypeElement) declaredSuper.asElement();

            if (!superElement.getQualifiedName().contentEquals(Object.class.getCanonicalName())) {
                collectMethodsRecursive(
                        superElement,
                        true,
                        ignoreOfClass,
                        result,
                        signatures
                );
            }
        }
    }

    private static boolean shouldIgnore(
            TypeElement typeElement,
            List<Class<?>> ignoreOfClass
    ) {
        if (ignoreOfClass.isEmpty()) {
            return false;
        }

        String qualifiedName = typeElement.getQualifiedName().toString();

        for (Class<?> ignored : ignoreOfClass) {
            if (ignored.getCanonicalName().equals(qualifiedName)) {
                return true;
            }
        }

        return false;
    }


    private static String buildMethodSignature(ExecutableElement method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getSimpleName()).append("(");

        for (VariableElement param : method.getParameters()) {
            sb.append(param.asType().toString()).append(",");
        }

        sb.append(")");
        return sb.toString();
    }


}
