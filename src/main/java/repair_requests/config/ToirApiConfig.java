package repair_requests.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Configuration DTO for the TOiR API connection settings.
 * <p>
 * This class handles deserialization from the YAML configuration file using Jackson
 * and includes a nested structure for flexible authentication handling.
 * </p>
 */

@Data
public class ToirApiConfig {

	@JsonProperty("endpoint")
	private String endpoint;
	
	@JsonProperty("auth")
	private AuthConfig auth;
	
	@Data
    public static class AuthConfig {
        
        @JsonProperty("type")
        private String type;

        @JsonProperty("username")
        private String username;

        @JsonProperty("password")
        private String password;

        @JsonProperty("token")
        private String token;
    }
}
