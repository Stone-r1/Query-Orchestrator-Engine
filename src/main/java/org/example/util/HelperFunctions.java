package org.example.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HelperFunctions {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    public static Properties loadProperties(
            String path
    ) throws IOException {
        try (FileInputStream file = new FileInputStream(path)) {
            Properties properties = new Properties();
            properties.load(file);
            return properties;
        }
    }

    /*
     * Expands ${VAR} placeholders in every property value, resolving each against
     * environment variables first, then JVM system properties. This is what makes
     * queryRunner.properties entries like hibernate.connection.url=${DB_URL} actually work -
     * java.util.Properties performs no substitution on its own.
     */
    public static void resolvePlaceholders(
            Properties properties
    ) {
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            Matcher matcher = PLACEHOLDER.matcher(value);
            StringBuilder resolved = new StringBuilder();

            while (matcher.find()) {
                String name = matcher.group(1);
                String replacement = System.getenv(name);
                if (replacement == null) {
                    replacement = System.getProperty(name);
                }
                if (replacement == null) {
                    throw new IllegalStateException(
                            "Missing environment variable or system property for placeholder: " + name);
                }
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(resolved);

            properties.setProperty(key, resolved.toString());
        }
    }

    public static LocalDateTime toLocalDateTime(
            Object value
    ) {
        return switch (value) {
            case null -> null;
            case LocalDateTime localDateTime -> localDateTime;
            case Timestamp timestamp -> timestamp.toLocalDateTime();
            default -> throw new IllegalStateException(
                    "Unexpected timestamp type: " + value.getClass().getName());
        };
    }
}
