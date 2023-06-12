/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.helper.K8sAzureCloudConfigMetadata;
import io.harness.helper.K8sCloudConfigMetadata;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName(InfrastructureKind.KUBERNETES_AZURE)
@TypeAlias("cdng.infra.beans.K8sAzureInfrastructureOutcome")
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome")
public class K8sAzureInfrastructureOutcome extends InfrastructureOutcomeAbstract {
  String connectorRef;
  String namespace;
  String cluster;
  String releaseName;
  String infrastructureKey;
  String subscription;
  String resourceGroup;
  @VariableExpression(skipVariableExpression = true) EnvironmentOutcome environment;
  Boolean useClusterAdminCredentials;

  @Override
  public String getKind() {
    return InfrastructureKind.KUBERNETES_AZURE;
  }

  @Override
  public K8sCloudConfigMetadata getInfraOutcomeMetadata() {
    return K8sAzureCloudConfigMetadata.builder()
        .clusterName(cluster)
        .resourceGroup(resourceGroup)
        .subscription(subscription)
        .useClusterAdminCredentials(useClusterAdminCredentials)
        .build();
  }
}
