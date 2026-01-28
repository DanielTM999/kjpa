package dtm.database.utils;

import dtm.database.internal.ParsedQueryMethod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RepositoryMetadataExtractor {

    private RepositoryMetadataExtractor(){
        throw new UnsupportedOperationException("utility class");
    }

    public static Set<String> extractQueryParameters(String query) {
        Set<String> params = new HashSet<>();
        Matcher matcher = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)").matcher(query);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return params;
    }

    public static ParsedQueryMethod parseQueryMethodName(String methodName) {
        String[] supportedPrefixes = {
                "findBy",
                "countBy",
                "existsBy",
                "deleteBy"
        };

        String prefix = null;
        for (String p : supportedPrefixes) {
            if (methodName.startsWith(p)) {
                prefix = p;
                break;
            }
        }

        if (prefix == null) {
            return null;
        }

        String criteria = methodName.substring(prefix.length());
        if (criteria.isEmpty()) {
            return null;
        }

        List<String> properties = new ArrayList<>();
        List<String> operators = new ArrayList<>();

        int index = 0;
        StringBuilder current = new StringBuilder();

        while (index < criteria.length()) {

            if (criteria.startsWith("And", index)) {
                properties.add(current.toString());
                operators.add("AND");
                current.setLength(0);
                index += 3;
                continue;
            }

            if (criteria.startsWith("Or", index)) {
                properties.add(current.toString());
                operators.add("OR");
                current.setLength(0);
                index += 2;
                continue;
            }

            current.append(criteria.charAt(index));
            index++;
        }

        if (!current.isEmpty()) {
            properties.add(current.toString());
        }

        return new ParsedQueryMethod(prefix, properties, operators);
    }

}
