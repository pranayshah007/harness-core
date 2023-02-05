/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.azure;

import io.harness.azure.utility.AzureUtils;

import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVaultConfig;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.policy.RetryPolicy;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class AzureCGHelper {
  public HttpPipeline createCredentialsAndHttpPipeline(AzureConfig azureConfig, String subscriptionId) {
    return createCredentialsAndHttpPipeline(subscriptionId,
        AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()), azureConfig.getTenantId(),
        azureConfig.getClientId(), String.valueOf(azureConfig.getKey()));
  }

  public HttpPipeline createCredentialsAndHttpPipeline(AzureConfig azureConfig) {
    return createCredentialsAndHttpPipeline(azureConfig, null);
  }

  public HttpPipeline createCredentialsAndHttpPipeline(AzureVaultConfig azureVaultConfig) {
    return createCredentialsAndHttpPipeline(azureVaultConfig.getSubscription(),
        AzureUtils.getAzureEnvironment(azureVaultConfig.getAzureEnvironmentType()), azureVaultConfig.getTenantId(),
        azureVaultConfig.getClientId(), azureVaultConfig.getSecretKey());
  }

  private HttpPipeline createCredentialsAndHttpPipeline(
      String subscriptionId, AzureEnvironment azureEnvironment, String tenantId, String clientId, String clientSecret) {
    HttpClient httpClient = AzureUtils.getAzureHttpClient();
    AzureProfile azureProfile = AzureUtils.getAzureProfile(tenantId, subscriptionId, azureEnvironment);
    RetryPolicy retryPolicy =
        AzureUtils.getRetryPolicy(AzureUtils.getRetryOptions(AzureUtils.getDefaultDelayOptions()));

    ClientSecretCredential clientSecretCredential =
        new ClientSecretCredentialBuilder()
            .clientId(clientId)
            .tenantId(tenantId)
            .clientSecret(clientSecret)
            .httpClient(httpClient)
            .authorityHost(AzureUtils.getAuthorityHost(azureEnvironment, tenantId))
            .build();

    return AzureUtils.getAzureHttpPipeline(clientSecretCredential, azureProfile, retryPolicy, httpClient);
  }
}
