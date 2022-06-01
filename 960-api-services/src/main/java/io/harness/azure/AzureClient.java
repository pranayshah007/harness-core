/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure;

import static io.harness.network.Http.getOkHttpClientBuilder;

import static com.google.common.base.Charsets.UTF_8;
import static java.lang.String.format;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.client.AzureBlueprintRestClient;
import io.harness.azure.client.AzureContainerRegistryRestClient;
import io.harness.azure.client.AzureManagementRestClient;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.utility.AzureUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.network.Http;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.inject.Singleton;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.credentials.MSICredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.rest.LogLevel;
import com.microsoft.rest.ServiceResponseBuilder;
import com.microsoft.rest.serializer.JacksonAdapter;
import java.security.InvalidKeyException;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class AzureClient {
  protected JacksonAdapter azureJacksonAdapter;
  protected ServiceResponseBuilder.Factory serviceResponseFactory;

  public AzureClient() {
    azureJacksonAdapter = new JacksonAdapter();
    serviceResponseFactory = new ServiceResponseBuilder.Factory();
  }

  protected Azure getAzureClientByContext(AzureClientContext context) {
    AzureConfig azureConfig = context.getAzureConfig();
    String subscriptionId = context.getSubscriptionId();
    return getAzureClient(azureConfig, subscriptionId);
  }

  protected Azure getAzureClientWithDefaultSubscription(AzureConfig azureConfig) {
    return getAzureClient(azureConfig, null);
  }

  protected Azure getAzureClient(AzureConfig azureConfig, String subscriptionId) {
    try {
      Azure.Authenticated authenticated =
          Azure.configure().withLogLevel(LogLevel.NONE).authenticate(getAuthenticationTokenCredentials(azureConfig));
      return isBlank(subscriptionId) ? authenticated.withDefaultSubscription()
                                     : authenticated.withSubscription(subscriptionId);

    } catch (Exception e) {
      handleAzureAuthenticationException(e);
    }

    return null;
  }

  protected void handleAzureAuthenticationException(Exception e) {
    String message = null;

    Throwable e1 = e;
    while (e1.getCause() != null) {
      e1 = e1.getCause();
      if (e1 instanceof AuthenticationException || e1 instanceof InvalidKeyException) {
        message = "Invalid Azure credentials." + e1.getMessage();
      }
      if (e1 instanceof InterruptedException) {
        message = "Failed to connect to Azure cluster. " + ExceptionUtils.getMessage(e1);
      }
    }

    if (EmptyPredicate.isEmpty(message)) {
      message = e.getMessage();
    }

    throw NestedExceptionUtils.hintWithExplanationException("Check your Azure credentials",
        "Failed to connect to Azure", new AzureAuthenticationException(message, WingsException.USER, e));
  }

  protected AzureManagementRestClient getAzureManagementRestClient(AzureEnvironmentType azureEnvironmentType) {
    String url = AzureUtils.getAzureEnvironment(azureEnvironmentType).resourceManagerEndpoint();
    return getAzureRestClient(url, AzureManagementRestClient.class);
  }

  protected AzureBlueprintRestClient getAzureBlueprintRestClient(AzureEnvironmentType azureEnvironmentType) {
    String url = AzureUtils.getAzureEnvironment(azureEnvironmentType).resourceManagerEndpoint();
    return getAzureRestClient(url, AzureBlueprintRestClient.class);
  }

  protected AzureContainerRegistryRestClient getAzureContainerRegistryRestClient(final String repositoryHost) {
    String repositoryHostUrl = buildRepositoryHostUrl(repositoryHost);
    return getAzureRestClient(repositoryHostUrl, AzureContainerRegistryRestClient.class);
  }

  protected <T> T getAzureRestClient(String url, Class<T> clazz) {
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(AzureConstants.REST_CLIENT_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                                    .readTimeout(AzureConstants.REST_CLIENT_READ_TIMEOUT, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(url))
                                    .retryOnConnectionFailure(true)
                                    .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(clazz);
  }

  protected String getAzureBearerAuthToken(AzureConfig azureConfig) {
    try {
      return "Bearer "
          + getAuthenticationTokenCredentials(azureConfig)
                .getToken(AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).managementEndpoint());
    } catch (Exception e) {
      handleAzureAuthenticationException(e);
    }
    return null;
  }

  protected String getAzureBasicAuthHeader(final String username, final String password) {
    return "Basic " + encodeBase64String(format("%s:%s", username, password).getBytes(UTF_8));
  }

  protected String getAzureBearerAuthHeader(final String token) {
    return format("Bearer %s", token);
  }

  protected String buildRepositoryHostUrl(String repositoryHost) {
    return format("https://%s%s", repositoryHost, repositoryHost.endsWith("/") ? "" : "/");
  }

  protected AzureTokenCredentials getAuthenticationTokenCredentials(AzureConfig azureConfig) {
    AzureEnvironment azureEnvironment = AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType());
    switch (azureConfig.getAzureAuthenticationType()) {
      case SERVICE_PRINCIPAL_CERT:
        return new ApplicationTokenCredentials(
            azureConfig.getClientId(), azureConfig.getTenantId(), azureConfig.getCert(), null, azureEnvironment);
      case MANAGED_IDENTITY_SYSTEM_ASSIGNED:
        return new MSICredentials(azureEnvironment);
      case MANAGED_IDENTITY_USER_ASSIGNED:
        return new MSICredentials(azureEnvironment).withClientId(azureConfig.getClientId());
      default:
        // by default, it should be SERVICE_PRINCIPAL_SECRET
        return new ApplicationTokenCredentials(azureConfig.getClientId(), azureConfig.getTenantId(),
            String.valueOf(azureConfig.getKey()), azureEnvironment);
    }
  }

  protected String createClientAssertion(AzureConfig azureConfig) {
    String certThumbprintInBase64 = AzureUtils.getCertificateThumbprintBase64Encoded(azureConfig.getCert());
    RSAPrivateKey privateKey = AzureUtils.getPrivateKeyFromPEMFile(azureConfig.getCert());

    Algorithm algorithm = Algorithm.RSA256(privateKey);

    Map<String, Object> headers = new HashMap<>();
    headers.put("x5t", certThumbprintInBase64);

    long currentTimestamp = System.currentTimeMillis();
    return JWT.create()
        .withHeader(headers)
        .withAudience(format("%s%s/oauth2/v2.0/token", AzureUtils.AUTH_URL, azureConfig.getTenantId()))
        .withIssuer(azureConfig.getClientId())
        .withIssuedAt(new Date(currentTimestamp))
        .withNotBefore(new Date(currentTimestamp))
        .withExpiresAt(new Date(currentTimestamp + 10 * 60 * 1000))
        .withJWTId(UUID.randomUUID().toString())
        .withSubject(azureConfig.getClientId())
        .sign(algorithm);
  }
}
