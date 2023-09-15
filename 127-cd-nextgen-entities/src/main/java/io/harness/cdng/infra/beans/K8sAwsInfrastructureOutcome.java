/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.helper.K8sAWSCloudConfigMetadata;
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
@JsonTypeName(InfrastructureKind.KUBERNETES_AWS)
@TypeAlias("cdng.infra.beans.K8sAwsInfrastructureOutcome")
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.infra.beans.K8sAwsInfrastructureOutcome")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public class K8sAwsInfrastructureOutcome extends InfrastructureOutcomeAbstract {
  String connectorRef;
  String namespace;
  String cluster;
  String releaseName;
  @VariableExpression(skipVariableExpression = true) EnvironmentOutcome environment;
  String infrastructureKey;
  String infrastructureKeyShort;

  @Override
  public String getKind() {
    return InfrastructureKind.KUBERNETES_AWS;
  }

  @Override
  public K8sCloudConfigMetadata getInfraOutcomeMetadata() {
    return K8sAWSCloudConfigMetadata.builder().clusterName(cluster).build();
  }
}
