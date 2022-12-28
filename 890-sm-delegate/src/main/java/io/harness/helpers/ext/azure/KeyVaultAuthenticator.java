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
import io.harness.azure.utility.AzureUtils;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.policy.RetryPolicy;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.resources.models.Subscription;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class KeyVaultAuthenticator {
  protected static String getKeyVaultUri(String keyVaultName) {
    return String.format("https://%s.vault.azure.net", keyVaultName);
  }

  public static TokenCredential getAuthenticationTokenCredentials(
      String clientId, String clientKey, String tenantId, HttpClient httpClient) {
    return new ClientSecretCredentialBuilder()
        .clientId(clientId)
        .clientSecret(clientKey)
        .tenantId(tenantId)
        .httpClient(httpClient)
        .build();
  }

  public static AzureProfile getAzureProfile(
      String tenantId, String subscriptionId, AzureEnvironmentType azureEnvironmentType) {
    return AzureUtils.getAzureProfile(tenantId, subscriptionId, AzureUtils.getAzureEnvironment(azureEnvironmentType));
  }

  public static SecretClient getSecretsClient(String keyVaultName, HttpPipeline httpPipeline) {
    return new SecretClientBuilder().pipeline(httpPipeline).vaultUrl(getKeyVaultUri(keyVaultName)).buildClient();
  }

  public static HttpPipeline getAzureHttpPipeline(String clientId, String clientKey, String tenantId,
      String subscriptionId, AzureEnvironmentType azureEnvironmentType) {
    HttpClient httpClient = AzureUtils.getAzureHttpClient();
    TokenCredential tokenCredential = getAuthenticationTokenCredentials(clientId, clientKey, tenantId, httpClient);
    AzureProfile azureProfile = getAzureProfile(tenantId, subscriptionId, azureEnvironmentType);
    RetryPolicy retryPolicy =
        AzureUtils.getRetryPolicy(AzureUtils.getRetryOptions(AzureUtils.getDefaultDelayOptions()));
    return AzureUtils.getAzureHttpPipeline(tokenCredential, azureProfile, retryPolicy, httpClient);
  }

  public static AzureResourceManager getAzureResourceManager(
      TokenCredential credentials, AzureKeyVaultConnectorDTO azureKeyVaultConnectorDTO, HttpClient httpClient) {
    AzureProfile azureProfile = getAzureProfile(azureKeyVaultConnectorDTO.getTenantId(),
        azureKeyVaultConnectorDTO.getSubscription(), azureKeyVaultConnectorDTO.getAzureEnvironmentType());
    AzureResourceManager.Authenticated authenticated = AzureResourceManager.authenticate(
        AzureUtils.getAzureHttpPipeline(credentials, azureProfile,
            AzureUtils.getRetryPolicy(AzureUtils.getRetryOptions(AzureUtils.getDefaultDelayOptions())), httpClient),
        azureProfile);
    String subscriptionId = azureKeyVaultConnectorDTO.getSubscription();
    if (isBlank(subscriptionId)) {
      Subscription subscription = authenticated.subscriptions().list().stream().findFirst().get();
      subscriptionId = subscription.subscriptionId();
    }

    return authenticated.withSubscription(subscriptionId);
  }
}
