package repair_requests.config;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Utility for locating, loading, and deserializing external YAML configuration files into DTOs.
 */
public class YAMLConfigLoader {
	
	private static final Logger logger = LogManager.getLogger(YAMLConfigLoader.class);
	
	private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
	
	private YAMLConfigLoader() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	/**
	 * Loads the configuration from a YAML file and deserializes it into a DTO class.
	 * 
	 * @param fileName the name of the configuration file (e.g., "properties.yaml")
	 * @return the configured GSPApiConfig object
	 */
    public static ToirApiConfig load(String fileName) {
        try {
            File externalFile = ConfigPathResolver.locateConfigFile(fileName);
            
            logger.info("Loading configuration from external folder: {}", externalFile.getAbsolutePath());
            return yamlMapper.readValue(externalFile, ToirApiConfig.class);

        } catch (Exception e) {
            logger.fatal("Application initialization failed due to configuration error", e);
            throw new RuntimeException("Fatal error during application startup", e);
        }
    }
}
