package repair_requests.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

import lombok.Data;

/**
 * Configuration DTO for the TOiR API connection settings.
 * Maps directly to the 'toir-api' section in properties.yaml.
 */
@Data
@JsonRootName("toir-api")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToirApiConfig {

    @JsonProperty("data_endpoint")
    private String dataEndpoint;

    @JsonProperty("auth_type")
    private String authType;

    @JsonProperty("equipment_classifier_codes")
    private List<String> equipmentClassifierCodes = List.of("_CF", "_PPS", "_VC");

    @JsonProperty("auth")
    private AuthConfig auth;

    @JsonProperty("connection")
    private ConnectionConfig connection = new ConnectionConfig();

    @JsonProperty("retry")
    private RetryConfig retry = new RetryConfig();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuthConfig {
        @JsonProperty("basic")
        private BasicAuthConfig basic;

        @JsonProperty("static_token")
        private String staticToken;

        @JsonProperty("oauth2")
        private OAuth2Config oauth2;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BasicAuthConfig {
        @JsonProperty("username")
        private String username;

        @JsonProperty("password")
        private String password;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OAuth2Config {
        @JsonProperty("token_endpoint")
        private String tokenEndpoint;

        @JsonProperty("client_id")
        private String clientId;

        @JsonProperty("client_secret")
        private String clientSecret;

        @JsonProperty("scope")
        private String scope;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConnectionConfig {
        @JsonProperty("connect_timeout_ms")
        private int connectTimeoutMs = 50000;

        @JsonProperty("response_timeout_ms")
        private int responseTimeoutMs = 120000;
    }

    /**
     * Exponential Random Backoff (Jitter) retry policy boundaries.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RetryConfig {
        @JsonProperty("initial_timeout_ms")
        private long initialTimeoutMs = 10000L;

        @JsonProperty("max_timeout_ms")
        private long maxTimeoutMs = 180000L;

        @JsonProperty("multiplier")
        private double multiplier = 2.0;

        @JsonProperty("randomization_factor")
        private double randomizationFactor = 0.2;

        @JsonProperty("max_retries")
        private int maxRetries = 5;
    }
}
