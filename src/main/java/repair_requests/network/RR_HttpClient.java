package repair_requests.network;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import my.CertUpdater;
import my.KeyStoreManager;
import repair_requests.config.ToirApiConfig;

/**
 * Resilient HTTP client tailored for 1C:TOiR / IIS gateways.
 * Supports dynamic certificate synchronization before SSLContext initialization,
 * Preemptive Basic Auth, Bearer Token, OAuth2 Client Credentials, and full wire diagnostics.
 */
@Singleton
public class RR_HttpClient {

    private static final Logger logger = LogManager.getLogger(RR_HttpClient.class);
    private static final ZoneId MINSK_ZONE = ZoneId.of("Europe/Minsk");

    private final ToirApiConfig config;
    private final HttpClientBuilder httpClientBuilder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private volatile CloseableHttpClient httpClient;

    // Cached OAuth2 token state
    private String cachedOAuthToken;
    private Instant tokenExpiresAt = Instant.MIN;

    @Inject
    public RR_HttpClient(ToirApiConfig config, HttpClientBuilder httpClientBuilder, ObjectMapper objectMapper) {
        this(config, httpClientBuilder, objectMapper, Clock.system(MINSK_ZONE));
    }

    // Package-private constructor for deterministic unit testing with fixed Clock
    RR_HttpClient(ToirApiConfig config, HttpClientBuilder httpClientBuilder, ObjectMapper objectMapper, Clock clock) {
        this.config = config;
        this.httpClientBuilder = httpClientBuilder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Executes a GET request against the configured TOiR endpoint with dynamic year bounds.
     *
     * @return raw JSON payload as String
     * @throws IOException if network or HTTP error occurs
     */
    public String fetchRepairRequestsJson() throws IOException {
        URI finalUri = buildTargetUri();
        String urlString = finalUri.toString();

        // Extract relative path + query to satisfy Apache HttpClient 5 & IIS/1C rules
        String relativePath = finalUri.getRawPath();
        if (finalUri.getRawQuery() != null && !finalUri.getRawQuery().isEmpty()) {
            relativePath += "?" + finalUri.getRawQuery();
        }

        HttpHost targetHost = new HttpHost(finalUri.getScheme(), finalUri.getHost(), finalUri.getPort());
        HttpClientContext localContext = HttpClientContext.create();

        ClassicRequestBuilder requestBuilder = ClassicRequestBuilder.get()
                .setUri(relativePath)
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0")
                .addHeader("Host", finalUri.getHost());

        // 1. Synchronize TLS certificates & configure auth headers BEFORE initializing SSLContext
        configureAuthAndCertificates(urlString, targetHost, localContext, requestBuilder);

        // 2. Lazily initialize the HTTP client after trustStore has been updated
        CloseableHttpClient client = getOrCreateHttpClient();

        ClassicHttpRequest request = requestBuilder.build();

        HttpClientResponseHandler<String> responseHandler = new HttpClientResponseHandler<>() {
            @Override
            public String handleResponse(final ClassicHttpResponse response) throws HttpException, IOException {
                int status = response.getCode();
                System.out.println("HTTP Response Code: " + status);

                String responseBody = (response.getEntity() != null)
                        ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
                        : "";

                if (status >= 200 && status < 300) {
                    return responseBody;
                } else {
                    System.err.println("SERVER ERROR BODY: " + responseBody);
                    throw new IOException("Unexpected response status: " + status + " | Body: " + responseBody);
                }
            }
        };

        return client.execute(targetHost, request, localContext, responseHandler);
    }

    /**
     * Builds the target URI appending DateBegin and DateEnd for the current year.
     */
    URI buildTargetUri() {
        String baseUrl = config.getDataEndpoint();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Configuration error: 'toir-api.data_endpoint' is missing or empty.");
        }

        int currentYear = LocalDate.now(clock).getYear();
        String dateBegin = currentYear + "0101000000";
        String dateEnd = currentYear + "1231235959";

        try {
            return new URIBuilder(baseUrl)
                    .addParameter("DateBegin", dateBegin)
                    .addParameter("DateEnd", dateEnd)
                    .build();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid 'data_endpoint' URI in configuration: " + baseUrl, e);
        }
    }

    /**
     * Updates certificates via CertUpdater and attaches authentication headers / Preemptive Auth context.
     */
    void configureAuthAndCertificates(String urlString, HttpHost targetHost, HttpClientContext localContext,
                                      ClassicRequestBuilder requestBuilder) throws IOException {
        String authType = (config.getAuthType() != null) ? config.getAuthType().trim().toLowerCase() : "none";

        switch (authType) {
            case "none" -> {
                logger.info("Connecting without authentication (auth_type: none)");
                CertUpdater.updateWithoutAuth(urlString);
            }

            case "basic" -> {
                if (config.getAuth() == null || config.getAuth().getBasic() == null
                        || config.getAuth().getBasic().getUsername() == null
                        || config.getAuth().getBasic().getPassword() == null) {
                    throw new IllegalStateException("Basic auth selected, but username or password is missing in config.");
                }

                String username = config.getAuth().getBasic().getUsername();
                String password = config.getAuth().getBasic().getPassword();

                // Update certificates first
                CertUpdater.updateByBasicAuth(urlString, username, password);

                // Configure Preemptive Basic Authentication for IIS / 1C stateless gateway
                BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                        new AuthScope(targetHost),
                        new UsernamePasswordCredentials(username, password.toCharArray())
                );
                localContext.setCredentialsProvider(credsProvider);

                AuthCache authCache = new BasicAuthCache();
                BasicScheme basicAuth = new BasicScheme();
                basicAuth.initPreemptive(new UsernamePasswordCredentials(username, password.toCharArray()));
                authCache.put(targetHost, basicAuth);
                localContext.setAuthCache(authCache);

                // Explicit Authorization header retained for IIS compatibility
                String authString = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(authString.getBytes(StandardCharsets.UTF_8));
                requestBuilder.addHeader("Authorization", "Basic " + encodedAuth);
            }

            case "token" -> {
                if (config.getAuth() == null || config.getAuth().getStaticToken() == null
                        || config.getAuth().getStaticToken().isBlank()) {
                    throw new IllegalStateException("Token auth selected, but 'static_token' is missing in config.");
                }

                String staticToken = config.getAuth().getStaticToken();
                CertUpdater.updateByToken(urlString, staticToken);
                requestBuilder.addHeader("Authorization", "Bearer " + staticToken);
            }

            case "oauth2" -> {
                String oauthToken = getOrFetchOAuth2Token();
                CertUpdater.updateByToken(urlString, oauthToken);
                requestBuilder.addHeader("Authorization", "Bearer " + oauthToken);
            }

            default -> throw new IllegalStateException("Unsupported auth_type in configuration: '" + config.getAuthType() + "'");
        }
    }

    /**
      * Retrieves a cached OAuth2 access token or requests a new one using Client Credentials Grant.
      */
    private synchronized String getOrFetchOAuth2Token() throws IOException {
       if (cachedOAuthToken != null && Instant.now(clock).isBefore(tokenExpiresAt)) 
           return cachedOAuthToken;
            	
       if (config.getAuth() == null || config.getAuth().getOauth2() == null) 
           throw new IllegalStateException("OAuth2 auth selected, but 'oauth2' configuration block is missing.");
            	
       ToirApiConfig.OAuth2Config oauthCfg = config.getAuth().getOauth2();
       if (oauthCfg.getTokenEndpoint() == null || oauthCfg.getClientId() == null || oauthCfg.getClientSecret() == null) 
            	throw new IllegalStateException("Incomplete OAuth2 configuration (token_endpoint, client_id, client_secret required).");
            	
       CertUpdater.updateWithoutAuth(oauthCfg.getTokenEndpoint());
       CloseableHttpClient client = getOrCreateHttpClient();
       List formParams = new ArrayList<>();
       formParams.add(new BasicNameValuePair("grant_type", "client_credentials"));
       formParams.add(new BasicNameValuePair("client_id", oauthCfg.getClientId()));
       formParams.add(new BasicNameValuePair("client_secret", oauthCfg.getClientSecret()));
       if (oauthCfg.getScope() != null && !oauthCfg.getScope().isBlank()) 
           formParams.add(new BasicNameValuePair("scope", oauthCfg.getScope()));
       
       ClassicHttpRequest tokenRequest = ClassicRequestBuilder
    		   	.post(oauthCfg.getTokenEndpoint())
    		   	.setEntity(new UrlEncodedFormEntity(formParams, StandardCharsets.UTF_8))
            	.build();
       return client.execute(tokenRequest, response -> {
            	int status = response.getCode();
            	String body = (response.getEntity() != null)
            			? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8)
            			: "";
            	if (status < 200 || status >= 300) 
            		throw new IOException("Failed to obtain OAuth2 token. HTTP status: " 
            							  + status + ", Body: " + body);
            	
            	JsonNode json = objectMapper.readTree(body);
            	String accessToken = json.path("access_token").asText(null);
            	if (accessToken == null || accessToken.isBlank()) 
            		throw new IOException("OAuth2 token response does not contain 'access_token': " + body);
            	
            	long expiresIn = json.path("expires_in").asLong(3600);
            	this.cachedOAuthToken = accessToken;
            	this.tokenExpiresAt = Instant.now(clock).plusSeconds(Math.max(30, expiresIn - 60));
            	return accessToken;
       });
    }
    
    /**
      * State-aware lazy initialization of CloseableHttpClient.
      * Guarantees SSLContext is loaded only AFTER CertUpdater has updated cacerts.
      */
    private CloseableHttpClient getOrCreateHttpClient() {
      if (httpClient == null) {
          synchronized (this) {
           	if (httpClient == null) {
           		try {
           			SSLContext sslContext = KeyStoreManager.getSSLContext();
           			SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
           			int connectTimeoutMs = config.getConnection() != null
           					? config.getConnection().getConnectTimeoutMs() : 50000;
           			int responseTimeoutMs = config.getConnection() != null
           					? config.getConnection().getResponseTimeoutMs() : 120000;
           			
           			ConnectionConfig connectionConfig = ConnectionConfig.custom()
           					.setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
           					.build();
           			PoolingHttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
           					.setSSLSocketFactory(sslSocketFactory)
           					.setDefaultConnectionConfig(connectionConfig)
           					.build();
           			RequestConfig requestConfig = RequestConfig.custom()
           					.setResponseTimeout(Timeout.ofMilliseconds(responseTimeoutMs))
           					.build();
           			// Full wire diagnostics retained for 1C/IIS troubleshooting
           			HttpRequestInterceptor wireLogger = new HttpRequestInterceptor() {
           				@Override
           				public void process(HttpRequest requestObj, EntityDetails entity, HttpContext context) {
           					System.out.println("\n=== OUTGOING HTTP REQUEST ===");
           					System.out.println("Method: " + requestObj.getMethod());
           					System.out.println("Request URI: " + requestObj.getRequestUri());
           					if (context instanceof HttpClientContext clientCtx
           					        && clientCtx.getHttpRoute() != null
           					        && clientCtx.getHttpRoute().getTargetHost() != null) 
           					    System.out.println("Target Host: " + clientCtx.getHttpRoute().getTargetHost().toURI());
           					
           					System.out.println("Headers:");
           					for (Header header : requestObj.getHeaders()) 
           						System.out.println("  " + header.getName() + ": " + header.getValue());
           					
           					System.out.println("=============================\n");
           				}
           			};
           			httpClient = httpClientBuilder
           					.setConnectionManager(cm)
           					.setDefaultRequestConfig(requestConfig)
           					.setRetryStrategy(new DefaultHttpRequestRetryStrategy(5, TimeValue.ofSeconds(30)))
           					.addRequestInterceptorFirst(wireLogger)
           					.build();
           		} catch (Exception e) {
           			throw new RuntimeException("Failed to initialize secure HttpClient", e);
            	}
            }
          }
      }
      return httpClient;
    }
}
