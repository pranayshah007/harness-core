/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers.ext.azure;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.utility.AzureUtils;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.management.profile.AzureProfile;
import com.azure.core.util.HttpClientOptions;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.resources.fluentcore.utils.HttpPipelineProvider;
import com.azure.resourcemanager.resources.models.Subscription;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import java.time.Duration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class KeyVaultAuthenticator {
  protected static String getKeyVaultUri(String keyVaultName) {
    return String.format("https://%s.vault.azure.net", keyVaultName);
  }

  public static TokenCredential getAuthenticationTokenCredentials(String clientId, String clientKey, String tenantId) {
    return new ClientSecretCredentialBuilder().clientId(clientId).clientSecret(clientKey).tenantId(tenantId).build();
  }

  public static AzureProfile getAzureProfile(
      String tenantId, String subscriptionId, AzureEnvironmentType azureEnvironmentType) {
    return AzureUtils.getAzureProfile(tenantId, subscriptionId, AzureUtils.getAzureEnvironment(azureEnvironmentType));
  }

  public static SecretClient getSecretsClient(String keyVaultName, TokenCredential credential, HttpClient httpClient) {
    return new SecretClientBuilder()
        .httpLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BASIC))
        .httpClient(httpClient)
        .credential(credential)
        .retryPolicy(AzureUtils.getRetryPolicy(AzureUtils.getRetryOptions(AzureUtils.getDefaultDelayOptions())))
        .vaultUrl(getKeyVaultUri(keyVaultName))
        .buildClient();
  }

  public static HttpPipeline getAzureHttpPipeline(TokenCredential tokenCredential, AzureProfile azureProfile) {
    return HttpPipelineProvider.buildHttpPipeline(tokenCredential, azureProfile);
  }

  public static HttpPipeline getAzureHttpPipeline(String clientId, String clientKey, String tenantId,
      String subscriptionId, AzureEnvironmentType azureEnvironmentType) {
    return getAzureHttpPipeline(getAuthenticationTokenCredentials(clientId, clientKey, tenantId),
        getAzureProfile(tenantId, subscriptionId, azureEnvironmentType));
  }

  public static HttpClient getAzureHttpClient() {
    HttpClientOptions httpClientOptions = new HttpClientOptions();
    httpClientOptions.setConnectTimeout(Duration.ofSeconds(AzureConstants.REST_CLIENT_CONNECT_TIMEOUT))
        .setReadTimeout(Duration.ofSeconds(AzureConstants.REST_CLIENT_READ_TIMEOUT));
    return HttpClient.createDefault(httpClientOptions);
  }

  public static HttpClient getAzureHttpClient(HttpClientOptions httpClientOptions) {
    if (httpClientOptions == null) {
      return getAzureHttpClient();
    }

    return HttpClient.createDefault(httpClientOptions);
  }

  public static AzureResourceManager getAzureResourceManager(
      TokenCredential credentials, AzureKeyVaultConnectorDTO azureKeyVaultConnectorDTO) {
    AzureResourceManager.Authenticated authenticated =
        AzureResourceManager.configure()
            .withLogLevel(AzureUtils.getAzureLogLevel(log))
            .withHttpClient(getAzureHttpClient())
            .withRetryPolicy(AzureUtils.getRetryPolicy(AzureUtils.getRetryOptions(AzureUtils.getDefaultDelayOptions())))
            .authenticate(credentials,
                getAzureProfile(azureKeyVaultConnectorDTO.getTenantId(), azureKeyVaultConnectorDTO.getSubscription(),
                    azureKeyVaultConnectorDTO.getAzureEnvironmentType()));

    String subscriptionId = azureKeyVaultConnectorDTO.getSubscription();
    if (isBlank(subscriptionId)) {
      Subscription subscription = authenticated.subscriptions().list().stream().findFirst().get();
      subscriptionId = subscription.subscriptionId();
    }

    return authenticated.withSubscription(subscriptionId);
  }
}
