package gc.demo.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a {@code float[]} embedding in a PostgreSQL {@code vector} column by using the text
 * representation (e.g. {@code [0.1,0.2,...]}). The entity keeps {@code float[]} while Hibernate
 * binds the JDBC value as text and the SQL casts to/from {@code vector}.
 */
@Converter(autoApply = false)
public class PgvectorStringFloatArrayConverter implements AttributeConverter<float[], String> {

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(attribute.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < attribute.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(Float.toString(attribute[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        String s = dbData.trim();
        if (s.isEmpty()) {
            return new float[0];
        }

        int start = 0;
        int end = s.length();
        if (s.charAt(0) == '[') {
            start++;
        }
        if (end > start && s.charAt(end - 1) == ']') {
            end--;
        }

        String inner = s.substring(start, end).trim();
        if (inner.isEmpty()) {
            return new float[0];
        }

        String[] parts = inner.split(",");
        List<Float> floats = new ArrayList<>(parts.length);
        for (String part : parts) {
            String token = part.trim();
            if (!token.isEmpty()) {
                floats.add(Float.parseFloat(token));
            }
        }

        float[] result = new float[floats.size()];
        for (int i = 0; i < floats.size(); i++) {
            result[i] = floats.get(i);
        }
        return result;
    }
}

