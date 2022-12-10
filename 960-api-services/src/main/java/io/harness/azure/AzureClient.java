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
import io.harness.azure.client.AzureKubernetesRestClient;
import io.harness.azure.client.AzureManagementRestClient;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.utility.AzureUtils;
import io.harness.exception.AzureAuthenticationException;
import io.harness.network.Http;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.http.policy.RetryPolicy;
import com.azure.core.management.profile.AzureProfile;
import com.azure.core.util.Configuration;
import com.azure.core.util.HttpClientOptions;
import com.azure.identity.ClientCertificateCredentialBuilder;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.identity.implementation.util.ScopeUtil;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.resources.fluentcore.utils.HttpPipelineProvider;
import com.azure.resourcemanager.resources.models.Subscription;
import com.google.inject.Singleton;
import com.jakewharton.retrofit2.adapter.reactor.ReactorCallAdapterFactory;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class AzureClient extends AzureClientBase {
  protected AzureResourceManager getAzureClientByContext(AzureClientContext context) {
    AzureConfig azureConfig = context.getAzureConfig();
    String subscriptionId = context.getSubscriptionId();
    return getAzureClient(azureConfig, subscriptionId);
  }

  protected AzureResourceManager getAzureClientWithDefaultSubscription(AzureConfig azureConfig) {
    return getAzureClient(azureConfig, null);
  }

  protected HttpPipeline getAzureHttpPipeline(
      TokenCredential tokenCredential, AzureProfile azureProfile, RetryPolicy retryPolicy, HttpClient httpClient) {
    return HttpPipelineProvider.buildHttpPipeline(tokenCredential, azureProfile, (String[]) null,
        (new HttpLogOptions()).setLogLevel(AzureUtils.getAzureLogLevel(log)), (Configuration) null, retryPolicy,
        (List) null, httpClient);
  }

  protected HttpPipeline getAzureHttpPipeline(TokenCredential tokenCredential, AzureProfile azureProfile) {
    return HttpPipelineProvider.buildHttpPipeline(tokenCredential, azureProfile);
  }

  protected HttpPipeline getAzureHttpPipeline(AzureConfig azureConfig, String subscriptionId) {
    return getAzureHttpPipeline(getAuthenticationTokenCredentials(azureConfig),
        AzureUtils.getAzureProfile(azureConfig.getTenantId(), subscriptionId,
            AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType())),
        AzureUtils.getRetryPolicy(AzureUtils.getRetryOptions(AzureUtils.getDefaultDelayOptions())),
        getAzureHttpClient());
  }

  protected AzureResourceManager getAzureClient(AzureConfig azureConfig, String subscriptionId) {
    try {
      AzureResourceManager.Authenticated authenticated =
          AzureResourceManager.configure()
              .withLogLevel(AzureUtils.getAzureLogLevel(log))
              .withHttpClient(getAzureHttpClient())
              .withRetryPolicy(
                  AzureUtils.getRetryPolicy(AzureUtils.getRetryOptions(AzureUtils.getDefaultDelayOptions())))
              .authenticate(getAuthenticationTokenCredentials(azureConfig),
                  AzureUtils.getAzureProfile(AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType())));

      if (isBlank(subscriptionId)) {
        Subscription subscription = authenticated.subscriptions().list().stream().findFirst().get();
        subscriptionId = subscription.subscriptionId();
      }

      return authenticated.withSubscription(subscriptionId);
    } catch (Exception e) {
      handleAzureAuthenticationException(e);
    }

    return null;
  }

  protected AzureManagementRestClient getAzureManagementRestClient(AzureEnvironmentType azureEnvironmentType) {
    String url = AzureUtils.getAzureEnvironment(azureEnvironmentType).getResourceManagerEndpoint();
    return getAzureRestClient(url, AzureManagementRestClient.class);
  }

  protected AzureKubernetesRestClient getAzureKubernetesRestClient(AzureEnvironmentType azureEnvironmentType) {
    String url = AzureUtils.getAzureEnvironment(azureEnvironmentType).getResourceManagerEndpoint();
    return getAzureRestClient(url, AzureKubernetesRestClient.class);
  }

  protected AzureBlueprintRestClient getAzureBlueprintRestClient(AzureEnvironmentType azureEnvironmentType) {
    String url = AzureUtils.getAzureEnvironment(azureEnvironmentType).getResourceManagerEndpoint();
    return getAzureRestClient(url, AzureBlueprintRestClient.class);
  }

  protected AzureContainerRegistryRestClient getAzureContainerRegistryRestClient(final String repositoryHost) {
    String repositoryHostUrl = buildRepositoryHostUrl(repositoryHost);
    return getAzureRestClient(repositoryHostUrl, AzureContainerRegistryRestClient.class);
  }

  protected <T> T getAzureRestClient(String url, Class<T> clazz) {
    OkHttpClient.Builder okHttpClientBuilder =
        getOkHttpClientBuilder()
            .connectTimeout(AzureConstants.REST_CLIENT_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(AzureConstants.REST_CLIENT_READ_TIMEOUT, TimeUnit.SECONDS)
            .proxy(Http.checkAndGetNonProxyIfApplicable(url))
            .retryOnConnectionFailure(true);

    OkHttpClient okHttpClient = okHttpClientBuilder.build();

    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addCallAdapterFactory(ReactorCallAdapterFactory.create())
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(clazz);
  }

  protected String getAzureBearerAuthToken(AzureConfig azureConfig) {
    try {
      return "Bearer "
          + getAuthenticationTokenCredentials(azureConfig)
                .getToken(AzureUtils.getTokenRequestContext(ScopeUtil.resourceToScopes(
                    AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getManagementEndpoint())))
                .block()
                .getToken();
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

  protected TokenCredential getAuthenticationTokenCredentials(AzureConfig azureConfig) {
    switch (azureConfig.getAzureAuthenticationType()) {
      case SERVICE_PRINCIPAL_CERT:
        switch (azureConfig.getAzureCertAuthenticationType()) {
          case PEM:
            return new ClientCertificateCredentialBuilder()
                .clientId(azureConfig.getClientId())
                .tenantId(azureConfig.getTenantId())
                .pemCertificate(azureConfig.getCertFilePath())
                .build();
          case PFX:
            return new ClientCertificateCredentialBuilder()
                .clientId(azureConfig.getClientId())
                .tenantId(azureConfig.getTenantId())
                .pfxCertificate(azureConfig.getCertFilePath(), azureConfig.getCertPassword())
                .build();
          default:
            throw new AzureAuthenticationException(
                format("Service Principal certificate authentication type %s is invalid.",
                    azureConfig.getAzureCertAuthenticationType().name()));
        }
      case MANAGED_IDENTITY_SYSTEM_ASSIGNED:
        return new ManagedIdentityCredentialBuilder().build();
      case MANAGED_IDENTITY_USER_ASSIGNED:
        return new ManagedIdentityCredentialBuilder().clientId(azureConfig.getClientId()).build();
      default:
        // by default, it should be SERVICE_PRINCIPAL_SECRET
        return new ClientSecretCredentialBuilder()
            .clientId(azureConfig.getClientId())
            .tenantId(azureConfig.getTenantId())
            .clientSecret(String.valueOf(azureConfig.getKey()))
            .build();
    }
  }

  protected HttpClient getAzureHttpClient() {
    HttpClientOptions httpClientOptions = new HttpClientOptions();
    httpClientOptions.setConnectTimeout(Duration.ofSeconds(AzureConstants.REST_CLIENT_CONNECT_TIMEOUT))
        .setReadTimeout(Duration.ofSeconds(AzureConstants.REST_CLIENT_READ_TIMEOUT));
    return HttpClient.createDefault(httpClientOptions);
  }

  protected HttpClient getAzureHttpClient(HttpClientOptions httpClientOptions) {
    if (httpClientOptions == null) {
      return getAzureHttpClient();
    }

    return HttpClient.createDefault(httpClientOptions);
  }
}
