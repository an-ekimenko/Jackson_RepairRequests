package repair_requests;

import java.io.File;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import repair_requests.config.ConfigPathResolver;
import repair_requests.config.ToirApiConfig;
import repair_requests.config.YAMLConfigLoader;
import repair_requests.jackson_json.RR_DataDeserializer;
import repair_requests.model.RepairRequests_Data;
import repair_requests.model.RepairRequests_Entity;

/**
 * Core Google Guice Dependency Injection module for the application.
 */
public class RRAppModule extends AbstractModule {

    private static final Logger logger = LogManager.getLogger(RRAppModule.class);

    @Provides
    @Singleton
    public ToirApiConfig provideToirApiConfig() {
        return YAMLConfigLoader.load("properties.yaml");
    }

    @Provides
    @Singleton
    public ObjectMapper provideObjectMapper(ToirApiConfig config) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        SimpleModule module = new SimpleModule();
        module.addDeserializer(
                RepairRequests_Data.class,
                new RR_DataDeserializer(config.getEquipmentClassifierCodes())
        );
        mapper.registerModule(module);

        return mapper;
    }


    @Provides
    public HttpClientBuilder provideHttpClientBuilder() {
        return HttpClients.custom();
    }

    @Provides
    @Singleton
    public SessionFactory provideSessionFactory() {
        ServiceRegistry serviceRegistry = null;
        try {
            File hibernateCfgFile = ConfigPathResolver.locateConfigFile("Hibernate.cfg.xml");
            logger.info("Loading Hibernate configuration from external folder: {}", hibernateCfgFile.getAbsolutePath());

            Configuration configuration = new Configuration().configure(hibernateCfgFile);
            configuration.addAnnotatedClass(RepairRequests_Entity.class);

            serviceRegistry = new StandardServiceRegistryBuilder()
                    .applySettings(configuration.getProperties())
                    .build();

            return configuration.buildSessionFactory(serviceRegistry);
        } catch (Exception e) {
            logger.fatal("Failed to initialize Hibernate SessionFactory", e);
            if (serviceRegistry != null) {
                StandardServiceRegistryBuilder.destroy(serviceRegistry);
            }
            throw new RuntimeException("Fatal Hibernate initialization error", e);
        }
    }
}
