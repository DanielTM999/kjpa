package dtm.database.internal;

import java.util.List;

public record ParsedQueryMethod(
        String prefix,
        List<String> properties,
        List<String> operators
) {}

