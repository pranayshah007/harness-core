/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl;
import static io.harness.azure.model.AzureConstants.SUBSCRIPTION_ID_NULL_VALIDATION_MSG;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.azure.AzureClient;
import io.harness.azure.client.AzureKubernetesClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureKubeconfigFormat;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.NestedExceptionUtils;

import software.wings.helpers.ext.azure.AksClusterCredentials;

import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.containerservice.models.KubernetesCluster;
import groovy.lang.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
@Slf4j
public class AzureKubernetesClientImpl extends AzureClient implements AzureKubernetesClient {
  @Override
  public List<KubernetesCluster> listKubernetesClusters(final AzureConfig azureConfig, final String subscriptionId) {
    if (isBlank(subscriptionId)) {
      throw new IllegalArgumentException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG);
    }
    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start listing Kubernetes clusters for subscriptionId {}", subscriptionId);
    return azure.kubernetesClusters().list().stream().collect(Collectors.toList());
  }

  @Override
  public String getClusterCredentials(AzureConfig azureConfig, String accessToken, String subscriptionId,
      String resourceGroup, String aksClusterName, boolean shouldGetAdminCredentials,
      AzureKubeconfigFormat azureKubeconfigFormat) {
    log.info(format(
        "Fetching cluster credentials [subscription: %s] [resourceGroup: %s] [aksClusterName: %s] [credentials: %s]",
        subscriptionId, resourceGroup, aksClusterName, shouldGetAdminCredentials ? "admin" : "user"));

    Call<AksClusterCredentials> request;
    String error;
    try {
      if (shouldGetAdminCredentials) {
        request = getAzureKubernetesRestClient(azureConfig.getAzureEnvironmentType())
                      .listClusterAdminCredential(accessToken, subscriptionId, resourceGroup, aksClusterName);
      } else {
        request = getAzureKubernetesRestClient(azureConfig.getAzureEnvironmentType())
                      .listClusterUserCredential(
                          accessToken, subscriptionId, resourceGroup, aksClusterName, azureKubeconfigFormat.getName());
      }

      Response<AksClusterCredentials> response = request.execute();
      if (response.isSuccessful()) {
        return response.body().getKubeconfigs().get(0).getValue();
      }

      error = response.errorBody().string();

    } catch (Exception e) {
      error = e.getMessage();
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        format("Failed to retrieve cluster %s credentials", shouldGetAdminCredentials ? "admin" : "user"),
        "Please check assigned permissions in Azure", new AzureAuthenticationException(error));
  }
}
