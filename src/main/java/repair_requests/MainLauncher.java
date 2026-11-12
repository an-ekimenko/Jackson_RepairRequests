package repair_requests;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;

import com.google.inject.Guice;
import com.google.inject.Injector;

import repair_requests.util.LogInitializer;
import repair_requests.util.ResultFileWriter;

/**
 * Definitive bootstrapper and entry point for the Jackson_RepairRequests application.
 */
public class MainLauncher {

    public static void main(String[] args) {
        // 1. Initialize logging and trustStore system properties BEFORE loading Log4j2 loggers
        LogInitializer.init();
        Logger logger = LogManager.getLogger(MainLauncher.class);

        int exitCode = 0;
        int recordsProcessed = 0;
        Injector injector = null;

        try {
            logger.info("Bootstrapping Guice Dependency Injection container...");
            injector = Guice.createInjector(new RRAppModule());

            RR_App app = injector.getInstance(RR_App.class);
            recordsProcessed = app.run();

            logger.info("Application finished successfully. Records processed: {}", recordsProcessed);
        } catch (Exception e) {
            exitCode = 1;
            logger.fatal("Application execution terminated with a fatal error", e);
        } finally {
            // Safely close Hibernate SessionFactory if initialized
            if (injector != null) {
                try {
                    SessionFactory sf = injector.getInstance(SessionFactory.class);
                    if (sf != null && !sf.isClosed()) {
                        sf.close();
                    }
                } catch (Exception ignored) {
                    // Ignore cleanup errors if SessionFactory itself failed to initialize
                }
            }

            // Write execution summary for external job schedulers
            ResultFileWriter.writeResult(exitCode, recordsProcessed);
            System.exit(exitCode);
        }
    }
}

