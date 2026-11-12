package repair_requests.network;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import my.CertUpdater;
import my.KeyStoreManager;
import repair_requests.config.ToirApiConfig;

@ExtendWith(MockitoExtension.class)
class RR_HttpClientTest {

    @Mock
    private HttpClientBuilder mockClientBuilder;

    @Mock
    private CloseableHttpClient mockHttpClient;

    private ToirApiConfig config;
    private ObjectMapper objectMapper;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        config = new ToirApiConfig();
        config.setDataEndpoint("https://beloil.by");
        objectMapper = new ObjectMapper();
        fixedClock = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("Europe/Minsk"));
    }

    @Test
    @DisplayName("Should build target URI with DateBegin and DateEnd for the current year")
    void testBuildTargetUri() {
        RR_HttpClient client = new RR_HttpClient(config, mockClientBuilder, objectMapper, fixedClock);
        URI uri = client.buildTargetUri();

        assertEquals("https", uri.getScheme());
        assertEquals("beloil.by", uri.getHost());

        assertTrue(uri.getQuery().contains("DateBegin=20260101000000"));
        assertTrue(uri.getQuery().contains("DateEnd=20261231235959"));
    }

    @Test
    @DisplayName("Should configure Preemptive Basic Auth header, AuthCache, and update certificates")
    void testBasicAuthenticationSetup() throws Exception {
        config.setAuthType("basic");
        ToirApiConfig.AuthConfig auth = new ToirApiConfig.AuthConfig();
        ToirApiConfig.BasicAuthConfig basic = new ToirApiConfig.BasicAuthConfig();
        basic.setUsername("acn_user");
        basic.setPassword("secret_pass");
        auth.setBasic(basic);
        config.setAuth(auth);

        RR_HttpClient client = new RR_HttpClient(config, mockClientBuilder, objectMapper, fixedClock);
        HttpHost host = new HttpHost("https", "ias.it.beloil.by", -1);
        HttpClientContext context = HttpClientContext.create();
        ClassicRequestBuilder reqBuilder = ClassicRequestBuilder.get().setUri("/toir/hs/RepairRequests/get");

        try (MockedStatic<CertUpdater> certMock = mockStatic(CertUpdater.class)) {
            client.configureAuthAndCertificates("https://beloil.by", host, context, reqBuilder);

            certMock.verify(() -> CertUpdater.updateByBasicAuth("https://beloil.by", "acn_user", "secret_pass"));
            assertNotNull(context.getAuthCache());
            assertNotNull(context.getCredentialsProvider());

            String expectedHeader = "Basic " + Base64.getEncoder().encodeToString("acn_user:secret_pass".getBytes(StandardCharsets.UTF_8));
            assertEquals(expectedHeader, reqBuilder.build().getFirstHeader("Authorization").getValue());
        }
    }

    @Test
    @DisplayName("Should configure Bearer header for static token authentication")
    void testStaticTokenAuthenticationSetup() throws Exception {
        config.setAuthType("token");
        ToirApiConfig.AuthConfig auth = new ToirApiConfig.AuthConfig();
        auth.setStaticToken("my-static-token-123");
        config.setAuth(auth);

        RR_HttpClient client = new RR_HttpClient(config, mockClientBuilder, objectMapper, fixedClock);
        HttpHost host = new HttpHost("https", "ias.it.beloil.by", -1);
        HttpClientContext context = HttpClientContext.create();
        ClassicRequestBuilder reqBuilder = ClassicRequestBuilder.get().setUri("/test");

        try (MockedStatic<CertUpdater> certMock = mockStatic(CertUpdater.class)) {
            client.configureAuthAndCertificates("https://beloil.by", host, context, reqBuilder);

            certMock.verify(() -> CertUpdater.updateByToken("https://beloil.by", "my-static-token-123"));
            assertEquals("Bearer my-static-token-123", reqBuilder.build().getFirstHeader("Authorization").getValue());
        }
    }

    @Test
    @DisplayName("Should fail-fast on unsupported auth_type")
    void testUnsupportedAuthTypeThrowsException() {
        config.setAuthType("kerberos");
        RR_HttpClient client = new RR_HttpClient(config, mockClientBuilder, objectMapper, fixedClock);
        HttpHost host = new HttpHost("https", "ias.it.beloil.by", -1);
        HttpClientContext context = HttpClientContext.create();
        ClassicRequestBuilder reqBuilder = ClassicRequestBuilder.get().setUri("/test");

        assertThrows(IllegalStateException.class, () ->
                client.configureAuthAndCertificates("https://beloil.by", host, context, reqBuilder)
        );
    }

    @Test
    @DisplayName("Should execute end-to-end GET request and return response payload")
    @SuppressWarnings("unchecked")
    void testFetchRepairRequestsJsonSuccess() throws Exception {
        config.setAuthType("none");

        when(mockClientBuilder.setConnectionManager(any())).thenReturn(mockClientBuilder);
        when(mockClientBuilder.setDefaultRequestConfig(any())).thenReturn(mockClientBuilder);
        when(mockClientBuilder.setRetryStrategy(any())).thenReturn(mockClientBuilder);
        when(mockClientBuilder.addRequestInterceptorFirst(any())).thenReturn(mockClientBuilder);
        when(mockClientBuilder.build()).thenReturn(mockHttpClient);

        when(mockHttpClient.execute(any(HttpHost.class), any(ClassicHttpRequest.class), any(HttpClientContext.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    HttpClientResponseHandler<String> handler = invocation.getArgument(3);
                    ClassicHttpResponse mockResponse = mock(ClassicHttpResponse.class);
                    when(mockResponse.getCode()).thenReturn(200);
                    when(mockResponse.getEntity()).thenReturn(
                            new BasicHttpEntity(new ByteArrayInputStream("[{\"Номер\":\"1\"}]".getBytes(StandardCharsets.UTF_8)), null)
                    );
                    return handler.handleResponse(mockResponse);
                });

        try (MockedStatic<CertUpdater> certMock = mockStatic(CertUpdater.class);
             MockedStatic<KeyStoreManager> ksMock = mockStatic(KeyStoreManager.class)) {

            ksMock.when(KeyStoreManager::getSSLContext).thenReturn(SSLContext.getDefault());

            RR_HttpClient client = new RR_HttpClient(config, mockClientBuilder, objectMapper, fixedClock);
            String result = client.fetchRepairRequestsJson();

            assertEquals("[{\"Номер\":\"1\"}]", result);
            certMock.verify(() -> CertUpdater.updateWithoutAuth(anyString()));
        }
    }
}
