/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.azure.model.AzureConstants.SUBSCRIPTION_ID_NULL_VALIDATION_MSG;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.AzureClient;
import io.harness.azure.client.AzureKubernetesClient;
import io.harness.azure.model.AzureConfig;

import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.containerservice.KubernetesCluster;
import groovy.lang.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class AzureKubernetesClientImpl extends AzureClient implements AzureKubernetesClient {
  @Override
  public List<KubernetesCluster> listKubernetesClusters(final AzureConfig azureConfig, final String subscriptionId) {
    if (isBlank(subscriptionId)) {
      throw new IllegalArgumentException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG);
    }
    Azure azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start listing Kubernetes clusters for subscriptionId {}", subscriptionId);
    return new ArrayList<>(azure.kubernetesClusters().list());
  }
}
