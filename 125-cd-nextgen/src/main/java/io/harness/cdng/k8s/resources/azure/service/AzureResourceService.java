/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s.resources.azure.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.azure.resources.dtos.AzureTagsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureClustersDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureDeploymentSlotsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureResourceGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureWebAppNamesDTO;

@OwnedBy(HarnessTeam.CDP)
public interface AzureResourceService {
  AzureSubscriptionsDTO getSubscriptions(IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier);

  AzureResourceGroupsDTO getResourceGroups(
      IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier, String subscriptionId);

  AzureClustersDTO getClusters(IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier,
      String subscriptionId, String resourceGroup);

  AzureWebAppNamesDTO getWebAppNames(IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier,
      String subscriptionId, String resourceGroup);

  AzureDeploymentSlotsDTO getAppServiceDeploymentSlots(IdentifierRef connectorRef, String orgIdentifier,
      String projectIdentifier, String subscriptionId, String resourceGroup, String webAppName);

  AzureTagsDTO getTags(
      IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier, String subscriptionId);
}
