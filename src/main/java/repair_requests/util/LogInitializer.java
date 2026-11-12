package repair_requests.util;

import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import repair_requests.config.ConfigPathResolver;

/**
 * Utility class responsible for dynamic runtime initialization of the Log4j2/SLF4J logging subsystem.
 */
public class LogInitializer {

    private LogInitializer() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void init() {
        try {
            File logsDir = ConfigPathResolver.locateOrCreateDir("logs");
            String logPath = logsDir.getAbsolutePath().replace("\\", "/");
            System.setProperty("log.dir", logPath);

            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            context.reconfigure();
        } catch (Exception e) {
            System.err.println("Failed to dynamically initialize logging directory: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

