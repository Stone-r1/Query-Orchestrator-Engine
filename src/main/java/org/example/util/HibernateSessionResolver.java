package org.example.util;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.example.data.PathConstants;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;


public abstract class HibernateSessionResolver {

    // SessionFactory is thread-safe and must be reused - never recreated per call.
    private static final ConcurrentHashMap<String, SessionFactory> sessionFactories = new ConcurrentHashMap<>();

    /*
     * Returns the cached SessionFactory for the given config, building it on first call.
     * Annotated entity classes must be passed so Hibernate knows what tables to map.
     */
    private SessionFactory resolveSessionFactory(
            String propertiesFilePath
    ) {
        return sessionFactories.computeIfAbsent(propertiesFilePath, path -> {
            try (FileInputStream file = new FileInputStream(path)) {

                Properties base = HelperFunctions.loadProperties(
                        PathConstants.HIBERNATE_BASE_PROPERTIES
                );

                Properties properties = new Properties();
                properties.load(file);
                base.putAll(properties);

                HelperFunctions.resolvePlaceholders(base);

                Configuration configuration = new Configuration();
                configuration.setProperties(base);

                for (Class<?> entity : scanEntities()) {
                    configuration.addAnnotatedClass(entity);
                }

                return configuration.buildSessionFactory();

            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Failed to load Hibernate properties file: " + path, exception);
            }
        });
    }

    /* Purpose:
     * Instead of providing entities manually, we can scan the models
     * package for all classes annotated with @Entity and add them to the configuration.
     */
    private List<Class<?>> scanEntities() {
        try (ScanResult result = new ClassGraph()
                .enableAnnotationInfo()
                .acceptPackages(PathConstants.MODELS_DIRECTORY)
                .scan()) {
            return result
                    .getClassesWithAnnotation(jakarta.persistence.Entity.class)
                    .loadClasses();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to scan entities in package: " + PathConstants.MODELS_DIRECTORY, e);
        }
    }

    protected final Session openSession() {
        return resolveSessionFactory(PathConstants.DB_PROPERTIES).openSession();
    }
}
