package repair_requests.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO modeling the execution summary payload written to exchange/result.yaml.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

    @JsonProperty("datetime")
    private String datetime;

    @JsonProperty("returned_code")
    private int returnedCode;

    @JsonProperty("records_processed")
    private int recordsProcessed;
}
