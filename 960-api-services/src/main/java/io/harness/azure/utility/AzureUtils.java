/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.utility;

import static io.harness.network.Http.getOkHttpClientBuilder;

import static java.lang.String.format;

import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.HintException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.network.Http;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.okhttp.OkHttpAsyncClientProvider;
import com.azure.core.http.okhttp.implementation.ProxyAuthenticator;
import com.azure.core.http.policy.FixedDelayOptions;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.http.policy.RetryOptions;
import com.azure.core.http.policy.RetryPolicy;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.core.util.Configuration;
import com.azure.core.util.HttpClientOptions;
import com.azure.resourcemanager.resources.fluentcore.utils.HttpPipelineProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.DatatypeConverter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import okhttp3.Authenticator;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHost;
import org.slf4j.Logger;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@UtilityClass
@Slf4j
public class AzureUtils {
  public final List<String> AZURE_GOV_REGIONS_NAMES =
      Arrays.asList(Region.GOV_US_VIRGINIA.name(), Region.GOV_US_IOWA.name(), Region.GOV_US_ARIZONA.name(),
          Region.GOV_US_TEXAS.name(), Region.GOV_US_DOD_EAST.name(), Region.GOV_US_DOD_CENTRAL.name());

  private static String SCOPE_SUFFIX = ".default";

  private static HttpClient cachedAzureHttpClient;
  private static HttpClient cachedAzureHttpClientForLogStreaming;

  static String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
  static String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";
  static String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
  static String END_CERTIFICATE = "-----END CERTIFICATE-----";

  public static int EXECUTE_REST_CALL_MAX_ATTEMPTS = 3;

  public String convertToScope(String endpoint) {
    if (EmptyPredicate.isNotEmpty(endpoint)) {
      if (endpoint.charAt(endpoint.length() - 1) != '/') {
        return String.format("%s/%s", endpoint, SCOPE_SUFFIX);
      }
      return String.format("%s%s", endpoint, SCOPE_SUFFIX);
    }
    return null;
  }

  public final AzureEnvironment getAzureEnvironment(AzureEnvironmentType azureEnvironmentType) {
    if (azureEnvironmentType == null) {
      return AzureEnvironment.AZURE;
    }

    switch (azureEnvironmentType) {
      case AZURE_US_GOVERNMENT:
        return AzureEnvironment.AZURE_US_GOVERNMENT;

      case AZURE:
      default:
        return AzureEnvironment.AZURE;
    }
  }

  public String getCertificateThumbprintBase64Encoded(byte[] pem) {
    String errMsg;
    try {
      InputStream is = new ByteArrayInputStream(getCertificate(pem, true).getBytes());
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      Certificate cert = cf.generateCertificate(is);
      RSAPublicKey publicKey = (RSAPublicKey) cert.getPublicKey();

      String certThumbprintInHex =
          DatatypeConverter.printHexBinary(MessageDigest.getInstance("SHA-1").digest(cert.getEncoded()));

      byte[] decodedThumbprint = DatatypeConverter.parseHexBinary(certThumbprintInHex);
      return new String(java.util.Base64.getUrlEncoder().encode(decodedThumbprint));
    } catch (NoSuchAlgorithmException | CertificateException e) {
      errMsg = e.getMessage();
      log.error(errMsg);
    }
    throw NestedExceptionUtils.hintWithExplanationException(
        "Fail to retrieve certificate from Azure connector PEM file.",
        "Please check if the PEM file configured with Azure connector is proper.",
        new AzureAuthenticationException(errMsg));
  }

  public RSAPrivateKey getPrivateKeyFromPEMFile(byte[] pem) {
    String errMsg;
    try {
      String privateKeyPEM = getPrivateKey(pem, false);
      byte[] encoded = Base64.decodeBase64(privateKeyPEM);
      KeyFactory kf = null;

      kf = KeyFactory.getInstance("RSA");
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
      return (RSAPrivateKey) kf.generatePrivate(keySpec);

    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      errMsg = e.getMessage();
      log.error(errMsg);
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        "Failed to retrieve private key from Azure connector PEM file.",
        "Please check if the PEM file configured with Azure connector is proper.",
        new AzureAuthenticationException(errMsg));
  }

  public String getCertificate(byte[] pem, boolean withWrapperText) {
    try {
      return extract(pem, withWrapperText, BEGIN_CERTIFICATE, END_CERTIFICATE);
    } catch (Exception e) {
      log.error(e.getMessage());
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        "Failed to retrieve certificate part from Azure connector PEM file.",
        "Please check if the PEM file configured with Azure connector is proper.",
        new AzureAuthenticationException("PEM file provided for Azure connector is not valid!"));
  }

  public String getPrivateKey(byte[] pem, boolean withWrapperText) {
    try {
      return extract(pem, withWrapperText, BEGIN_PRIVATE_KEY, END_PRIVATE_KEY);
    } catch (Exception e) {
      log.error(e.getMessage());
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        "Failed to retrieve private key from Azure connector PEM file.",
        "Please check if the PEM file configured with Azure connector is proper.",
        new AzureAuthenticationException("PEM file provided for Azure connector is not valid!"));
  }

  protected String extract(byte[] data, boolean withWrapperText, String startPoint, String endPoint) throws Exception {
    String fullFile = new String(data);

    if (EmptyPredicate.isNotEmpty(fullFile)) {
      int startIndex = fullFile.indexOf(startPoint);
      int endIndex = fullFile.indexOf(endPoint);

      if (startIndex > -1 && endIndex > -1) {
        if (withWrapperText) {
          return fullFile.substring(startIndex, endIndex + endPoint.length());
        }

        return fullFile.substring(startIndex + startPoint.length(), endIndex);
      }
    }

    throw new Exception("Failed to parse provided data.");
  }

  public static HttpLogDetailLevel getAzureLogLevel(Logger logger) {
    if (logger.isTraceEnabled()) {
      return HttpLogDetailLevel.BODY_AND_HEADERS;
    }

    if (logger.isDebugEnabled()) {
      return HttpLogDetailLevel.BASIC;
    }

    return HttpLogDetailLevel.NONE;
  }

  public AzureProfile getAzureProfile(AzureEnvironment environment) {
    return getAzureProfile(null, null, environment);
  }

  public AzureProfile getAzureProfile(String tenantId, String subscriptionId, AzureEnvironment environment) {
    return new AzureProfile(tenantId, subscriptionId, environment);
  }

  public Proxy getProxyForRestClient(String url) {
    HttpHost httpHost = Http.getHttpProxyHost(url);
    if (httpHost != null) {
      return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpHost.getHostName(), httpHost.getPort()));
    }

    log.info(format("Request with destination %s will not go through proxy", url));
    return null;
  }

  public Authenticator getProxyAuthenticatorForRestClient() {
    String proxyUsername = Http.getProxyUserName();
    String proxyPassword = Http.getProxyPassword();
    if (EmptyPredicate.isNotEmpty(proxyUsername) && EmptyPredicate.isNotEmpty(proxyPassword)) {
      return new ProxyAuthenticator(proxyUsername, proxyPassword);
    }
    return null;
  }

  public ProxyOptions getProxyOptions() {
    HttpHost httpHost = Http.getHttpProxyHost();
    if (httpHost != null) {
      String hostName = httpHost.getHostName();
      if (hostName != null) {
        ProxyOptions proxyOptions =
            new ProxyOptions(ProxyOptions.Type.HTTP, new InetSocketAddress(hostName, httpHost.getPort()));

        String proxyUsername = Http.getProxyUserName();
        String proxyPassword = Http.getProxyPassword();
        if (EmptyPredicate.isNotEmpty(proxyUsername) && EmptyPredicate.isNotEmpty(proxyPassword)) {
          log.info(format("Using Proxy(%s:%s) for AzureSDK with auth", hostName, httpHost.getPort()));
          proxyOptions.setCredentials(proxyUsername, proxyPassword);
        } else {
          log.info(format("Using Proxy(%s:%s) for AzureSDK without auth", hostName, httpHost.getPort()));
        }

        String nonProxyHosts = System.getProperty("http.nonProxyHosts");
        if (EmptyPredicate.isNotEmpty(nonProxyHosts)) {
          log.info(format("Proxy will not be used for following destinations: %s", nonProxyHosts));
          proxyOptions.setNonProxyHosts(nonProxyHosts);
        }

        return proxyOptions;
      }
    }
    return null;
  }

  public TokenRequestContext getTokenRequestContext(String[] resourceToScopes) {
    TokenRequestContext tokenRequestContext = new TokenRequestContext();
    tokenRequestContext.addScopes(resourceToScopes);
    return tokenRequestContext;
  }

  public FixedDelayOptions getDefaultDelayOptions() {
    return getFixedDelayOptions(1, Duration.ofSeconds(3));
  }

  public FixedDelayOptions getFixedDelayOptions(int maxRetries, Duration delay) {
    return new FixedDelayOptions(maxRetries, delay);
  }

  public RetryOptions getRetryOptions(FixedDelayOptions delayOptions) {
    return new RetryOptions(delayOptions);
  }

  public RetryPolicy getRetryPolicy(RetryOptions retryOptions) {
    return new RetryPolicy(retryOptions);
  }

  public synchronized HttpClient getAzureHttpClient(boolean useExtendedReadTimeout) {
    int readTimeout = AzureConstants.REST_CLIENT_READ_TIMEOUT_SECONDS;

    if (useExtendedReadTimeout) {
      if (cachedAzureHttpClientForLogStreaming != null) {
        return cachedAzureHttpClientForLogStreaming;
      }
      readTimeout = AzureConstants.REST_CLIENT_READ_TIMEOUT_EXTENDED_SECONDS;
    } else {
      if (cachedAzureHttpClient != null) {
        return cachedAzureHttpClient;
      }
    }

    HttpClientOptions httpClientOptions = new HttpClientOptions();
    httpClientOptions.setConnectTimeout(Duration.ofSeconds(AzureConstants.REST_CLIENT_CONNECT_TIMEOUT_SECONDS))
        .setReadTimeout(Duration.ofSeconds(readTimeout))
        .setWriteTimeout(Duration.ofSeconds(AzureConstants.REST_CLIENT_WRITE_TIMEOUT_SECONDS))
        .setConnectionIdleTimeout(Duration.ofSeconds(AzureConstants.REST_CLIENT_IDLE_TIMEOUT_SECONDS))
        .setMaximumConnectionPoolSize(AzureConstants.REST_CONNECTION_POOL_SIZE);

    ProxyOptions proxyOptions = AzureUtils.getProxyOptions();
    if (proxyOptions != null) {
      httpClientOptions.setProxyOptions(proxyOptions);
    }

    if (useExtendedReadTimeout) {
      cachedAzureHttpClientForLogStreaming = new OkHttpAsyncClientProvider().createInstance(httpClientOptions);
      return cachedAzureHttpClientForLogStreaming;
    } else {
      cachedAzureHttpClient = new OkHttpAsyncClientProvider().createInstance(httpClientOptions);
      return cachedAzureHttpClient;
    }
  }

  public HttpClient getAzureHttpClient() {
    return getAzureHttpClient(false);
  }

  public HttpPipeline getAzureHttpPipeline(
      TokenCredential tokenCredential, AzureProfile azureProfile, RetryPolicy retryPolicy, HttpClient httpClient) {
    return HttpPipelineProvider.buildHttpPipeline(tokenCredential, azureProfile, (String[]) null,
        (new HttpLogOptions()).setLogLevel(AzureUtils.getAzureLogLevel(log)), (Configuration) null, retryPolicy,
        (List) null, httpClient);
  }

  public OkHttpClient getOkHtttpClientWithProxy(String url) {
    OkHttpClient.Builder okHttpClientBuilder =
        getOkHttpClientBuilder()
            .connectTimeout(AzureConstants.REST_CLIENT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(AzureConstants.REST_CLIENT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true);

    Proxy proxy = AzureUtils.getProxyForRestClient(url);
    if (proxy != null) {
      okHttpClientBuilder.proxy(proxy);
      Authenticator authenticator = AzureUtils.getProxyAuthenticatorForRestClient();
      if (authenticator != null) {
        log.info(format("Using Proxy(%s) for AzureRest with auth", proxy.address()));
        okHttpClientBuilder.proxyAuthenticator(authenticator);
      } else {
        log.info(format("Using Proxy(%s) for AzureRest without auth", proxy.address()));
      }
    }

    return okHttpClientBuilder.build();
  }

  public <T> T getAzureRestClient(String url, Class<T> clazz) {
    return getAzureRestClient(url, clazz, null);
  }

  public <T> T getAzureRestClient(String url, Class<T> clazz, CallAdapter.Factory callAdapterFactory) {
    OkHttpClient okHttpClient = AzureUtils.getOkHtttpClientWithProxy(url);

    Retrofit.Builder retrofitBuilder =
        new Retrofit.Builder().client(okHttpClient).baseUrl(url).addConverterFactory(JacksonConverterFactory.create());

    if (callAdapterFactory != null) {
      retrofitBuilder.addCallAdapterFactory(callAdapterFactory);
    }

    return retrofitBuilder.build().create(clazz);
  }

  public String getAuthorityHost(AzureEnvironment azureEnvironment, String tenantId) {
    if (azureEnvironment != null && tenantId != null) {
      String authorityHost = format("%s%s", azureEnvironment.getActiveDirectoryEndpoint(), tenantId);
      log.info(format("Using authority host [%s]", authorityHost));
      return authorityHost;
    }
    log.error(format("Failed to create authority host [AzureEnvType=%s], [TenantId=%s]", azureEnvironment, tenantId));
    return null;
  }

  public String getAuthorityHost(AzureEnvironmentType azureEnvironmentType, String tenantId) {
    return getAuthorityHost(getAzureEnvironment(azureEnvironmentType), tenantId);
  }

  public static <T> T executeRestCall(Call<T> restRequest, WingsException defaultException) {
    net.jodah.failsafe.RetryPolicy<Response<T>> retryPolicy =
        new net.jodah.failsafe.RetryPolicy<Response<T>>()
            .withBackoff(1, 10, ChronoUnit.SECONDS)
            .withMaxAttempts(EXECUTE_REST_CALL_MAX_ATTEMPTS)
            .handle(IOException.class)
            .handleResultIf(result -> !result.isSuccessful() && isRetryableHttpCode(result.code()))
            .onRetry(e -> log.warn("Failure #{}. Retrying. Exception {}", e.getAttemptCount(), e.getLastFailure()))
            .onRetriesExceeded(e -> log.warn("Failed to connect. Max retries exceeded"));

    try {
      Response<T> response = Failsafe.with(retryPolicy).get(() -> restRequest.clone().execute());
      if (response == null) {
        return null;
      }

      if (!response.isSuccessful()) {
        if (defaultException != null) {
          throw defaultException;
        }

        String error = null;
        try (ResponseBody responseBody = response.errorBody()) {
          if (null != responseBody) {
            error = responseBody.string();
          }
        }

        throw new HintException("Some problems occurred during Azure Rest API call: " + error);
      }
      return response.body();
    } catch (FailsafeException | IOException ex) {
      if (defaultException != null) {
        throw defaultException;
      }
      throw new HintException("Some problems occurred during Azure Rest API call", ex.getCause());
    }
  }

  private boolean isRetryableHttpCode(int httpCode) {
    // https://stackoverflow.com/questions/51770071/what-are-the-http-codes-to-automatically-retry-the-request
    return httpCode == 408 || httpCode == 502 || httpCode == 503 || httpCode == 504;
  }
}
