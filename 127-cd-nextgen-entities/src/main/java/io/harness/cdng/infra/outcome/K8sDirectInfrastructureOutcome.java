/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.outcome;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.commons.InfrastructureKind;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName(InfrastructureKind.KUBERNETES_DIRECT)
@TypeAlias("cdng.infra.beans.K8sDirectInfrastructureOutcome")
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.infra.outcome.K8sDirectInfrastructureOutcome")
public class K8sDirectInfrastructureOutcome extends InfrastructureOutcomeAbstract {
  String connectorRef;
  String namespace;
  String releaseName;
  @VariableExpression(skipVariableExpression = true) EnvironmentOutcome environment;
  String infrastructureKey;

  @Override
  public String getKind() {
    return InfrastructureKind.KUBERNETES_DIRECT;
  }
}
