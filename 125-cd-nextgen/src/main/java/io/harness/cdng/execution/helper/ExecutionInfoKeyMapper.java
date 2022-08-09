/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution.helper;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.steps.environment.EnvironmentOutcome;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class ExecutionInfoKeyMapper {
  public static ExecutionInfoKey getExecutionInfoKey(Ambiance ambiance, final String infrastructureKind,
      EnvironmentOutcome environmentOutcome, ServiceStepOutcome serviceOutcome,
      InfrastructureOutcome infrastructureOutcome) {
    if (InfrastructureKind.PDC.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AWS.equals(infrastructureKind)) {
      // infra identifier could be null in service/env version v1
      String infraIdentifier = infrastructureOutcome.getInfraIdentifier();
      if (isBlank(infraIdentifier)) {
        infraIdentifier = infrastructureOutcome.getInfrastructureKey();
      }
      return ExecutionInfoKey.builder()
          .scope(getScope(ambiance))
          .envIdentifier(environmentOutcome.getIdentifier())
          .infraIdentifier(infraIdentifier)
          .serviceIdentifier(serviceOutcome.getIdentifier())
          .build();
    } else if (InfrastructureKind.AZURE_WEB_APP.equals(infrastructureKind)) {
      return ExecutionInfoKey.builder()
          .scope(getScope(ambiance))
          .envIdentifier(environmentOutcome.getIdentifier())
          .infraIdentifier(infrastructureOutcome.getInfraIdentifier())
          .serviceIdentifier(serviceOutcome.getIdentifier())
          .build();
    }

    throw new InvalidArgumentsException(
        String.format("Not supported execution info key for infrastructure kind, %s", infrastructureKind));
  }

  private static Scope getScope(Ambiance ambiance) {
    return Scope.of(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance));
  }
}
