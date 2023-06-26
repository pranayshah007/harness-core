/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.helper.K8sCloudConfigMetadata;
import io.harness.helper.K8sRancherCloudConfigMetadata;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName(InfrastructureKind.KUBERNETES_RANCHER)
@TypeAlias("cdng.infra.beans.K8sRancherInfrastructureOutcome")
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.infra.beans.K8sRancherInfrastructureOutcome")
public class K8sRancherInfrastructureOutcome extends InfrastructureOutcomeAbstract {
  String connectorRef;
  String namespace;
  String clusterName;
  String releaseName;
  @VariableExpression(skipVariableExpression = true) EnvironmentOutcome environment;
  String infrastructureKey;

  @Override
  public String getKind() {
    return InfrastructureKind.KUBERNETES_RANCHER;
  }

  @Override
  public K8sCloudConfigMetadata getInfraOutcomeMetadata() {
    return K8sRancherCloudConfigMetadata.builder().clusterName(clusterName).build();
  }
}
