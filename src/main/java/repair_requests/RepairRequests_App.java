package repair_requests;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import my.CertUpdater;
import repair_requests.config.ToirApiConfig;
import repair_requests.hibernate.RepairRequests_DAO;
import repair_requests.jackson_json.RR_DataDeserializer;
import repair_requests.model.RepairRequests_Data;
import repair_requests.model.RepairRequests_Entity;
import repair_requests.model.RepairRequests_PK;

import org.apache.logging.log4j.core.config.Configuration;
import org.apache.hc.core5.http.Header;

/**
 * Orchestrates the ETL pipeline for repair requests, fetching remote data via Apache HttpClient 5,
 * validation, and batch persisting target domain entities to the MSSQL staging database.
 */

public class RepairRequests_App {
	private static RepairRequests_Data rrData;
    private static List<RepairRequests_Data> rrDataList = new ArrayList<>();
    private static List<RepairRequests_Entity> rrEntities;

    static {
    	// Build explicit cross-platform path mapping for systemic trust store configuration
        String relativeCacertsPath = "/lib/security/cacerts".replace("/", File.separator);
        String keyStoreFileName = System.getProperty("java.home") + relativeCacertsPath;
        System.setProperty("javax.net.ssl.trustStore", keyStoreFileName);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }
    
    /**
     * Target functional classifiers filtering baseline.
     * Only repair requests matching these custom identifiers (Coffee machines, Tire stations, Vacuums)
     * will be accepted for database ingestion.
     */
    private static final Set<String> ALLOWED_SUID_CODES = 
            new java.util.HashSet<>(Arrays.asList("_CF", "PPS", "_VC"));
    
    private static Logger logger = null;
    
    public static void main(String[] args) throws OAuthSystemException, OAuthProblemException, IOException {
    	
    	Class<?> currentClass = MethodHandles.lookup().lookupClass();

    	// Dynamically configures log file paths based on the application's runtime environment
    	try {
    		// Get the application's root directory
    		URI uri = currentClass.getProtectionDomain()
    				.getCodeSource()
    				.getLocation()
    				.toURI();
    		
    		Path currentPath = Paths.get(uri);
    		String pathStr = currentPath.toString();
    	
    		// Strip the "target" folder if running inside an IDE (Maven context)
    		if (pathStr.contains(File.separator + "target")) {
    			pathStr = pathStr.substring(0, pathStr.lastIndexOf(File.separator + "target"));
    		}
    	
    		// Set absolute paths for the logging subsystems
    		String commonsLog = Paths.get(pathStr, "logs", "commons.log").toString();
    		String p6spyLog = Paths.get(pathStr, "logs", "p6spy.log").toString();
    		System.setProperty("commonLogFilename", commonsLog);
    		System.setProperty("p6spyLogFilename", p6spyLog);
    	} catch (Exception e) {
    		e.printStackTrace();
    	}

    	// Initializes and verifies the Log4j2 logging configuration
    	LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
    	try {
    	    loggerContext.setConfigLocation(currentClass.getClassLoader().getResource("Log4j2.xml").toURI());
    	} catch (URISyntaxException e) { 
    	    e.printStackTrace(); 
    	}
    	loggerContext.reconfigure();
    	
    	// Print active loggers for debugging and verification
    	System.out.print("\nLoggers defined in Log4j2.xml -> ");		
    	
    	loggerContext.getConfiguration().getLoggers().values().stream()
        	.map(LoggerConfig::getName)
        	.forEach(name -> System.out.print("[" + name + "]; "));
    	System.out.println();
    	
    	Configuration conf = loggerContext.getConfiguration();
    	if (conf instanceof DefaultConfiguration) {
    		System.out.println("\nConfiguration context initialized as instance of DefaultConfiguration");
    	} else {
    		System.out.println("\nConfiguration context initialized as instance of XmlConfiguration");
    	}
    	logger = LogManager.getLogger(currentClass.getClass());
    	
    	System.out.println("\nActive configuration structural properties:");
    	conf.getProperties()
    		.forEach((key, value) -> System.out.println("\t" + key + " -> " + value));

        try {
        	// Infrastructure Profile Initialization and Configuration Loading
            logger.info("Initializing infrastructure profile synchronization sequence...");
            
            YAMLFactory yamlFactory = new YAMLFactory();
            ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
            
            // Loads the properties.yaml configuration file from the classpath resources. 
            InputStream configStream = null;
            try {
                configStream = currentClass.getClassLoader().getResourceAsStream("properties.yaml");
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // Validates that the configuration stream was successfully opened. 
            if (configStream == null) {
                throw new FileNotFoundException("Required configuration manifest 'properties.yaml' "
                											+ "is missing from classpath.");
            }
            
            // Parses the YAML layout and extracts the root infrastructure context nodes. 
            JsonNode rootNode = yamlMapper.readTree(configStream);
            JsonNode apiNode = rootNode.get("toir-api");
            
            // Validates the presence of the required 'toir-api' root node section. 
            if (apiNode == null) {
                throw new IllegalArgumentException("Missing required root infrastructure profile "
                									+ "layout node context in properties.yaml");
            }
            
            // Maps the extracted configuration node directly into the ToirApiConfig POJO mapping. 
            ToirApiConfig config = yamlMapper.treeToValue(apiNode, ToirApiConfig.class);

            // Time Zone Definitions and Query Parameter Generation for current year
            ZoneId z = ZoneId.of("Europe/Minsk");
            LocalDate today = LocalDate.now(z);  
            String currentYear = today.toString().substring(0, 4);
            String dt_from = currentYear + "0101000000";
            String dt_to = currentYear + "1231235959";
        
            String queryParams = "&DateBegin=" + dt_from + "&DateEnd=" + dt_to;

            String baseEndpoint = config.getEndpoint(); 
            
            // Initializes the URIBuilder to automatically parse existing query parameters. 
            URIBuilder uriBuilder = new URIBuilder(baseEndpoint);
            
            // Appends the calculated current year date boundaries as active query parameters. 
            uriBuilder.addParameter("DateBegin", dt_from);
            uriBuilder.addParameter("DateEnd", dt_to);
            
            // Constructs and compiles the final validated URI instance. 
            URI finalUri = uriBuilder.build();
            
            // Extracts the complete relative path including the processed query string safely. 
            String relativePath = finalUri.getRawPath(); 
            if (finalUri.getRawQuery() != null) {
                relativePath += "?" + finalUri.getRawQuery();
            }
            
            // Sets the full URL string reference for CertUpdater processing and logging execution. 
            String urlString = finalUri.toString();
            System.out.println("Calculated Relative Path for Request -> " + relativePath);

            // HTTP Preemptive Authentication and Context Configuration Setup
            BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
            ToirApiConfig.AuthConfig auth = config.getAuth();
            credsProvider.setCredentials(
                    new AuthScope(finalUri.getHost(), 443),
                    new UsernamePasswordCredentials(
                            auth.getUsername(), 
                            auth.getPassword().toCharArray()
                    )
            );

            // Defines the target network host node for establishing the secure SSL connection layer
            HttpHost targetHost = new HttpHost("https", finalUri.getHost(), 443);
            
            // Establishes preemptive Basic authentication mapping rules to eliminate 401 challenge processing overhead
            AuthCache authCache = new BasicAuthCache();

            BasicScheme basicAuthScheme = new BasicScheme();
            basicAuthScheme.initPreemptive(new UsernamePasswordCredentials(
            		auth.getUsername(),
            		auth.getPassword().toCharArray()
            ));
            
            // Enforces target host mapping registration within the explicit authentication cache pool
            authCache.put(targetHost, basicAuthScheme);

            // Constructs and configures the deterministic standalone runtime HTTP execution context block
            HttpClientContext localContext = HttpClientContext.create();
            localContext.setAuthCache(authCache);
            localContext.setCredentialsProvider(credsProvider);

            // Dynamic SSL Lifecycle Synchronization & Legacy Gateway Interoperability Mapping
            String authHeader = null;
            SSLContext sslContext = my.KeyStoreManager.getSSLContext();
            if (auth == null || auth.getType() == null) {
            	logger.warn("Authentication configuration is missing. Proceeding without auth headers.");
            } else {
            	String authType = auth.getType().toLowerCase();
            	switch (authType) {
            	case "basic":
            		String username = auth.getUsername();
            		String password = auth.getPassword();
            		String authString = username + ":" + password;
            		String encodedAuth = Base64.getEncoder()
            				.encodeToString(authString.getBytes(StandardCharsets.UTF_8));
            		authHeader = "Basic " + encodedAuth;
            		
            		// Propagate credentials to the dynamic SSL certificate lifecycle manager
            		CertUpdater.updateByBasicAuth(urlString, username, password);
            		break;
            		
            	case "token":
            		String token = auth.getToken();
            		authHeader = "Bearer " + token;
            		
            		// Execute a dedicated token authorization update route within the SSL manager
            		CertUpdater.updateByToken(urlString, token);
            		break;
            		
            	case "none":
            		logger.info("Executing request without authentication headers [auth type: none]");
            		break;
            		
            	default:
            		logger.warn("Unsupported authentication type detected: '{}'. No auth headers applied.", 
            						authType);
            		break;
            	}
            }
            
            // Client Connection, Timeout and Telemetry Interceptor Configuration
            
            //* Initializes the SSL socket factory and pooling connection manager. 
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
            
            PoolingHttpClientConnectionManager connManager = 
            		PoolingHttpClientConnectionManagerBuilder.create()
            			.setSSLSocketFactory(sslSocketFactory)
            			.build();

            // Establishes connection and response initial read timeouts set to 30 seconds. 
            RequestConfig requestConfig = RequestConfig.custom()
            		.setConnectTimeout(Timeout.ofSeconds(30))
            		.setResponseTimeout(Timeout.ofSeconds(30))
            		.build();

            // Configures resilient fault tolerance via an exponential backoff retry strategy with 5 attempts
            DefaultHttpRequestRetryStrategy retryStrategy = new DefaultHttpRequestRetryStrategy(
            		5,
            		TimeValue.ofSeconds(30)
            );
            
            // Builds the scoped HTTP client instance injected with enterprise-grade operational policies and console telemetry
            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connManager)
                    .setDefaultRequestConfig(requestConfig)
                    .setRetryStrategy(retryStrategy)
                    .setDefaultCredentialsProvider(credsProvider)
                    
                    // Intercepts the packet at the network interface level for telemetry logging. 
                    .addRequestInterceptorFirst(new HttpRequestInterceptor() {
                        @Override
                        public void process(HttpRequest requestObj, 
                        					EntityDetails entity, 
                                            HttpContext context) {
                            
                            System.out.println("\n==================================================");
                            System.out.println("FINAL OUTBOUND PACKET TELEMETRY (WHAT WE SEND TO SERVER)");
                            System.out.println("==================================================");
                            
                            // Outputs the basic HTTP request method and URI parameters. 
                            System.out.println("HTTP Method  -> " + requestObj.getMethod());
                            System.out.println("Request URI  -> " + requestObj.getRequestUri());
                            System.out.println("Protocol     -> " + requestObj.getVersion());
                            System.out.println("--------------------------------------------------");
                            
                            // Extracts and outputs the target physical host from the execution context. 
                            HttpClientContext clientContext = HttpClientContext.adapt(context);
                            HttpHost currentTarget = clientContext.getHttpRoute().getTargetHost();
                            
                            System.out.println("ACTUAL DESTINATION HOST DETAILS");
                            System.out.println("\tTarget Scheme -> " + currentTarget.getSchemeName());
                            System.out.println("\tTarget Host   -> " + currentTarget.getHostName());
                            System.out.println("\tTarget Port   -> " + currentTarget.getPort());
                            System.out.println("--------------------------------------------------");
                            
                            // Iterates through and outputs all outbound HTTP headers. 
                            System.out.println("SENT HTTP HEADERS");
                            Header[] headers = requestObj.getHeaders();
                            Arrays.stream(headers)
                                    .forEach(h -> System.out.println("\t" + h.getName() + ": " + h.getValue()));
                            
                            System.out.println("==================================================\n");
                        }
                    })
                    .build())
        {
        // Initialize the structural HTTP GET request
        ClassicHttpRequest request = ClassicRequestBuilder.get(relativePath)
                    .setHeader("Accept", "application/json, text/plain, */*")
                    .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) "
                    			+ "Gecko/20100101 Firefox/131.0")
                    .setHeader("Host", finalUri.getHost()) 
                    .build();

        // For reliability, we duplicate the standard authorization header.
        if (authHeader != null) {
              request.setHeader("Authorization", authHeader.trim()); 
        }
           
        logger.info("Executing API request to: " + urlString);
          
        // Execute the request via Preemptive context routing channel to override wsisapi.dll authentication blocks
        HttpClientResponseHandler<String> responseHandler = 
                  new HttpClientResponseHandler<String>() {
                       @Override
                       public String handleResponse(ClassicHttpResponse response) 
                               throws HttpException, IOException {
                            int status = response.getCode();
                         
                            System.out.println("\n==================================================");
                            System.out.println("DIAGNOSTIC TELEMETRY:");
                            System.out.println("HTTP Response Code: " + status);
                            System.out.println("==================================================");
                            
                            if (status >= 200 && status < 300) {
                                return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                            } else {
                                String errorBody = response.getEntity() != null 
                                    ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) 
                                    : "No error body";
                                System.out.println("SERVER ERROR BODY:\n" + errorBody);
                                throw new RuntimeException("The server returned an error code: " + status);
                            }
                       }
                  };

        // Execute the request, passing the authorization context and the created handler.
        String responseBody = httpClient.execute(targetHost, request, localContext, responseHandler);
        logger.info("Raw payload response received successfully.");

        // Parse the JSON response into Data Transfer Objects (DTOs)
        rrDataList = parse_json(responseBody);
            
        // Filter and map the DTOs to persistent domain database entities
        if (rrDataList.size() > 0) {
          	rrEntities = rrDataList.stream()
           		.filter(o -> ALLOWED_SUID_CODES.contains(o.getClassifierCodeSUID()))
           		.map(o -> new RepairRequests_Entity(
           					new RepairRequests_PK(o.getDate(), o.getApplicationNumber(),
           										  o.getOrganizationCodeMDM(), o.getDepartmentCodeMDM()
           										 ),
           					o.getDt_from(), o.getDt_to(),
           					o.getApplicationNumber_PON(), o.getApplicationType(),
           					o.getAuthor(), o.getStatus(),
           					o.getNumberOfOverdueDays(), o.getReasonForNon_compliance(),
           					o.getRepairFacility(), o.getRepairFacilityCodeMDM(),
           					o.getPurchaseRequest(), o.getDescriptionOfRenovation()
           			))
           		.collect(Collectors.toList());
        }
        if (rrEntities != null) {
           	logger.info("Successfully filtered and mapped {} staging entities for database write.", rrEntities.size());
           	System.out.printf("Successfully filtered and mapped %d staging entities for database write.%n", rrEntities.size());
        }
        // Persist the mapped entities via database batch ingestion layer
        writeParsedJSONToDatabase(rrEntities);
     }
   } catch (Exception ex) {
        logger.error("ETL orchestration sequence failed execution: " + ex.getMessage(), ex);
   }
   System.exit(0);
  }
    
    /**
     * Parses the raw JSON response string into a list of RepairRequests_Data DTOs.
     * Configured defensively to bypass unknown structural fields.
     */
    public static List<RepairRequests_Data> parse_json(String json) throws JsonMappingException, JsonProcessingException {
    	ObjectMapper objectMapper = new ObjectMapper();
        
    	// Prevent runtime failures if the upstream API introduces unexpected properties
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        
        // Register the custom deserializer for targeted repair request entity extraction
        SimpleModule module = new SimpleModule();
        module.addDeserializer(RepairRequests_Data.class, new RR_DataDeserializer());
        objectMapper.registerModule(module);
        
        return objectMapper.readValue(json, new TypeReference<List<RepairRequests_Data>>() {});
    }
    
    /**
     * Persists the mapped Hibernate entities into the MS SQL staging database
     * using optimized batch processing operations.
     */
    public static void writeParsedJSONToDatabase(List<RepairRequests_Entity> acnEntities) throws UnsupportedEncodingException {
    	RepairRequests_DAO dao = new RepairRequests_DAO();

    	// Execute a transaction-aware batch save/update operation
        dao.saveOrUpdateBatch(acnEntities);
    }
}
