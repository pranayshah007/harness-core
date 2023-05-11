/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@JsonTypeName(InfrastructureKind.SSH_WINRM_AWS)
@TypeAlias("cdng.infra.beans.SshWinRmAwsInfrastructureOutcome")
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome")
public class SshWinRmAwsInfrastructureOutcome extends InfrastructureOutcomeAbstract {
  private String connectorRef;
  private String credentialsRef;
  private String region;
  Map<String, String> hostTags;
  private String hostConnectionType;

  @VariableExpression(skipVariableExpression = true) private EnvironmentOutcome environment;
  private String infrastructureKey;

  @Override
  public String getKind() {
    return InfrastructureKind.SSH_WINRM_AWS;
  }
}
