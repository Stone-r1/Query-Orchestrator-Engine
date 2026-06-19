package org.example.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class HelperFunctions {

    public static Properties loadProperties(
            String path
    ) throws IOException {
        try (FileInputStream file = new FileInputStream(path)) {
            Properties properties = new Properties();
            properties.load(file);
            return properties;
        }
    }
}
