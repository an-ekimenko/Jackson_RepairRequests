package repair_requests.util;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import repair_requests.config.ConfigPathResolver;
import repair_requests.model.ExecutionResult;

/**
 * Reporting utility responsible for serializing execution summaries (ExecutionResult)
 * into exchange/result.yaml.
 */
public class ResultFileWriter {

    private static final Logger logger = LogManager.getLogger(ResultFileWriter.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    );

    private ResultFileWriter() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Writes the execution summary to exchange/result.yaml.
     *
     * @param returnedCode     0 for success, 1 for failure
     * @param recordsProcessed count of records successfully persisted to the database
     */
    public static void writeResult(int returnedCode, int recordsProcessed) {
        try {
            File exchangeDir = ConfigPathResolver.locateOrCreateDir("exchange");
            File resultFile = new File(exchangeDir, "result.yaml");

            String timestamp = LocalDateTime.now().format(FORMATTER);
            ExecutionResult result = new ExecutionResult(timestamp, returnedCode, recordsProcessed);

            YAML_MAPPER.writeValue(resultFile, result);
            logger.info("Execution result written to {}: {}", resultFile.getAbsolutePath(), result);
        } catch (Exception e) {
            logger.error("Failed to write execution result file", e);
        }
    }
}

