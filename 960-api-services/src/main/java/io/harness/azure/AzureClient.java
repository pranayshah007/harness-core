/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure;

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
import io.harness.azure.utility.AzureUtils;
import io.harness.exception.AzureAuthenticationException;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpPipeline;
import com.azure.identity.ClientCertificateCredentialBuilder;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.identity.implementation.util.ScopeUtil;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.resources.models.Subscription;
import com.google.inject.Singleton;
import com.jakewharton.retrofit2.adapter.reactor.ReactorCallAdapterFactory;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class AzureClient extends AzureClientBase {
  protected AzureResourceManager getAzureClientByContext(AzureClientContext context) {
    AzureConfig azureConfig = context.getAzureConfig();
    String subscriptionId = context.getSubscriptionId();
    return getAzureClient(azureConfig, subscriptionId, context.isExtendedReadTimeout());
  }

  protected AzureResourceManager getAzureClientWithDefaultSubscription(AzureConfig azureConfig) {
    return getAzureClient(azureConfig, null);
  }

  protected HttpPipeline getAzureHttpPipeline(AzureConfig azureConfig, String subscriptionId) {
    return getAzureHttpPipeline(azureConfig, subscriptionId, false);
  }

  protected HttpPipeline getAzureHttpPipeline(
      AzureConfig azureConfig, String subscriptionId, boolean useExtendedReadTimeout) {
    return AzureUtils.getAzureHttpPipeline(getAuthenticationTokenCredentials(azureConfig, useExtendedReadTimeout),
        AzureUtils.getAzureProfile(azureConfig.getTenantId(), subscriptionId,
            AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType())),
        AzureUtils.getRetryPolicy(AzureUtils.getRetryOptions(AzureUtils.getDefaultDelayOptions())),
        AzureUtils.getAzureHttpClient(useExtendedReadTimeout));
  }

  protected AzureResourceManager getAzureClient(AzureConfig azureConfig, String subscriptionId) {
    return getAzureClient(azureConfig, subscriptionId, false);
  }

  protected AzureResourceManager getAzureClient(
      AzureConfig azureConfig, String subscriptionId, boolean useExtendedReadTimeout) {
    try {
      AzureResourceManager.Authenticated authenticated =
          AzureResourceManager.authenticate(getAzureHttpPipeline(azureConfig, subscriptionId, useExtendedReadTimeout),
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
    return AzureUtils.getAzureRestClient(url, clazz, ReactorCallAdapterFactory.create());
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
    return getAuthenticationTokenCredentials(azureConfig, false);
  }

  protected TokenCredential getAuthenticationTokenCredentials(AzureConfig azureConfig, boolean useExtendedReadTimeout) {
    HttpClient httpClient = AzureUtils.getAzureHttpClient(useExtendedReadTimeout);
    switch (azureConfig.getAzureAuthenticationType()) {
      case SERVICE_PRINCIPAL_CERT:
        switch (azureConfig.getAzureCertAuthenticationType()) {
          case PEM:
            return new ClientCertificateCredentialBuilder()
                .clientId(azureConfig.getClientId())
                .tenantId(azureConfig.getTenantId())
                .pemCertificate(azureConfig.getCertFilePath())
                .authorityHost(
                    AzureUtils.getAuthorityHost(azureConfig.getAzureEnvironmentType(), azureConfig.getTenantId()))
                .httpClient(httpClient)
                .build();
          case PFX:
            return new ClientCertificateCredentialBuilder()
                .clientId(azureConfig.getClientId())
                .tenantId(azureConfig.getTenantId())
                .pfxCertificate(azureConfig.getCertFilePath(), azureConfig.getCertPassword())
                .authorityHost(
                    AzureUtils.getAuthorityHost(azureConfig.getAzureEnvironmentType(), azureConfig.getTenantId()))
                .httpClient(httpClient)
                .build();
          default:
            throw new AzureAuthenticationException(
                format("Service Principal certificate authentication type %s is invalid.",
                    azureConfig.getAzureCertAuthenticationType().name()));
        }
      case MANAGED_IDENTITY_SYSTEM_ASSIGNED:
        return new ManagedIdentityCredentialBuilder().httpClient(httpClient).build();
      case MANAGED_IDENTITY_USER_ASSIGNED:
        return new ManagedIdentityCredentialBuilder()
            .clientId(azureConfig.getClientId())
            .httpClient(httpClient)
            .build();
      default:
        // by default, it should be SERVICE_PRINCIPAL_SECRET
        return new ClientSecretCredentialBuilder()
            .clientId(azureConfig.getClientId())
            .tenantId(azureConfig.getTenantId())
            .clientSecret(String.valueOf(azureConfig.getKey()))
            .authorityHost(
                AzureUtils.getAuthorityHost(azureConfig.getAzureEnvironmentType(), azureConfig.getTenantId()))
            .httpClient(httpClient)
            .build();
    }
  }
}
