package dtm.database.internal;

public record JavaCode(
        String className,
        String classPackage,
        String code
) {

    public String classFullName(){
        return classPackage+"."+className;
    }

}
