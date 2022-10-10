/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.infra.AzureKubernetesService;

@OwnedBy(HarnessTeam.CDP)
public class InfraDefMapperUtils {
  public static K8sAzureInfrastructure buildK8sAzureInfrastructure(
      AzureKubernetesService aks, NgEntityDetail connectorDetail) {
    return K8sAzureInfrastructure.builder()
        .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
        .subscriptionId(ParameterField.createValueField(aks.getSubscriptionId()))
        .resourceGroup(ParameterField.createValueField(aks.getResourceGroup()))
        .cluster(ParameterField.createValueField(aks.getClusterName()))
        .namespace(ParameterField.createValueField(aks.getNamespace()))
        .releaseName(ParameterField.createValueField(aks.getReleaseName()))
        .build();
  }
}
