/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.execution.ExecutionDetails;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoBuilder;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.execution.sshwinrm.SshWinRmStageExecutionDetails;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.utils.StageStatus;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class StageExecutionHelper {
  @Inject private CDStepHelper cdStepHelper;
  @Inject private StageExecutionInfoService stageExecutionInfoService;

  public void saveStageExecutionInfo(
      @NotNull Ambiance ambiance, @Valid ExecutionInfoKey executionInfoKey, @NotNull final String infrastructureKind) {
    if (isEmpty(infrastructureKind)) {
      throw new InvalidArgumentsException(format(
          "Unable to save stage execution info, infrastructure kind cannot be null or empty, infrastructureKind: %s, executionInfoKey: %s",
          infrastructureKind, executionInfoKey.toString()));
    }
    if (executionInfoKey == null) {
      throw new InvalidArgumentsException("Execution info key cannot be null or empty");
    }

    ExecutionDetails executionDetails = getExecutionDetailsByInfraKind(ambiance, infrastructureKind);
    if (shouldSaveStageExecutionInfo(infrastructureKind)) {
      saveStageExecutionInfo(ambiance, executionInfoKey, executionDetails);
    }
  }

  private boolean shouldSaveStageExecutionInfo(String infrastructureKind) {
    return InfrastructureKind.PDC.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AWS.equals(infrastructureKind);
  }

  private void saveStageExecutionInfo(
      Ambiance ambiance, ExecutionInfoKey executionInfoKey, ExecutionDetails executionDetails) {
    StageExecutionInfoBuilder stageExecutionInfoBuilder =
        StageExecutionInfo.builder()
            .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .envIdentifier(executionInfoKey.getEnvIdentifier())
            .infraIdentifier(executionInfoKey.getInfraIdentifier())
            .serviceIdentifier(executionInfoKey.getServiceIdentifier())
            .stageExecutionId(ambiance.getStageExecutionId())
            .stageStatus(StageStatus.IN_PROGRESS)
            .executionDetails(executionDetails);

    if (isNotEmpty(executionInfoKey.getDeploymentIdentifier())) {
      stageExecutionInfoBuilder.deploymentIdentifier(executionInfoKey.getDeploymentIdentifier());
    }

    stageExecutionInfoService.save(stageExecutionInfoBuilder.build());
  }

  @Nullable
  private ExecutionDetails getExecutionDetailsByInfraKind(Ambiance ambiance, final String infrastructureKind) {
    if (InfrastructureKind.PDC.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructureKind)
        || InfrastructureKind.SSH_WINRM_AWS.equals(infrastructureKind)) {
      return SshWinRmStageExecutionDetails.builder()
          .artifactOutcome(cdStepHelper.resolveArtifactsOutcome(ambiance).orElse(null))
          .configFilesOutcome(cdStepHelper.getConfigFilesOutcome(ambiance).orElse(null))
          .build();
    }

    return null;
  }
}
