/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.executables;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.execution.service.StageExecutionInstanceInfoService;
import io.harness.delegate.beans.CDDelegateTaskNotifyResponseData;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;
import io.harness.opaclient.OpaServiceClient;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.TaskChainExecutableWithCapabilities;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.PolicyEvalUtils;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
// Task Executable with RBAC, Rollback and postTaskValidation
public abstract class CdTaskChainExecutable extends TaskChainExecutableWithCapabilities {
  @Inject OpaServiceClient opaServiceClient;
  @Inject StageExecutionInstanceInfoService stageExecutionInstanceInfoService;

  // evaluating policies added in advanced section of the steps and updating status and failure info in the step
  // response
  public StepResponse postTaskValidate(
      Ambiance ambiance, StepElementParameters stepParameters, StepResponse stepResponse) {
    if (Status.SUCCEEDED.equals(stepResponse.getStatus())) {
      return PolicyEvalUtils.evalPolicies(ambiance, stepParameters, stepResponse, opaServiceClient);
    }
    return stepResponse;
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    saveNodeInfo(ambiance, responseSupplier);
    return executeNextLinkWithSecurityContextAndNodeInfo(
        ambiance, stepParameters, inputPackage, passThroughData, responseSupplier);
  }

  public abstract TaskChainResponse executeNextLinkWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception;

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    saveNodeInfo(ambiance, responseDataSupplier);
    return finalizeExecutionWithSecurityContextAndNodeInfo(
        ambiance, stepParameters, passThroughData, responseDataSupplier);
  }

  public abstract StepResponse finalizeExecutionWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception;

  private void saveNodeInfo(Ambiance ambiance, ThrowingSupplier<ResponseData> responseSupplier) {
    if (responseSupplier != null) {
      try {
        ResponseData responseData = responseSupplier.get();
        if (responseData instanceof CDDelegateTaskNotifyResponseData) {
          StepExecutionInstanceInfo stepExecutionInstanceInfo =
              ((CDDelegateTaskNotifyResponseData) responseData).getStepExecutionInstanceInfo();
          if (stepExecutionInstanceInfo != null) {
            stageExecutionInstanceInfoService.append(AmbianceUtils.getAccountId(ambiance),
                AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
                AmbianceUtils.getPipelineExecutionIdentifier(ambiance),
                AmbianceUtils.getStageExecutionIdForExecutionMode(ambiance), stepExecutionInstanceInfo);
          }
        }
      } catch (Exception ex) {
        log.error("Failed to save node info", ex);
      }
    }
  }
}
