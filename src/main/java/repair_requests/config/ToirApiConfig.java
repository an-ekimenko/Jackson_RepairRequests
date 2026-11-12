package repair_requests.config;

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
	
	@JsonProperty("auth")
	private AuthConfig auth;

	/**
	 * Container for all supported authentication strategy configurations.
	 */
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

	/**
	 * Credentials for HTTP Basic authentication (used when auth_type = "basic").
	 */
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BasicAuthConfig {

		@JsonProperty("username")
		private String username;

		@JsonProperty("password")
		private String password;
	}

	/**
	 * Settings for OAuth2 Client Credentials Grant workflow (used when auth_type = "oauth2").
	 */
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
	
	@JsonProperty("connection")
	private ConnectionConfig connection = new ConnectionConfig();

	/**
	 * HTTP connection and socket timeout settings in milliseconds.
	 */
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ConnectionConfig {

		@JsonProperty("connect_timeout_ms")
		private int connectTimeoutMs = 5000;

		@JsonProperty("response_timeout_ms")
		private int responseTimeoutMs = 15000;
	}
}
