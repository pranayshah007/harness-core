/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.azure.model.AzureConstants.SLOT_SWAP;
import static io.harness.azure.model.AzureConstants.TARGET_SLOT_NAME_BLANK_ERROR_MSG;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.beans.Scope;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.azure.webapp.beans.AzureWebAppPreDeploymentDataOutput;
import io.harness.cdng.azure.webapp.beans.AzureWebAppSwapSlotsDataOutput;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.cdng.execution.azure.webapps.AzureWebAppsStageExecutionDetails.AzureWebAppsStageExecutionDetailsKeys;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppSwapSlotsRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AzureWebAppSwapSlotStep extends CdTaskExecutable<AzureWebAppTaskResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_SWAP_SLOT.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private AzureWebAppStepHelper stepHelper;
  @Inject private StageExecutionInfoService stageExecutionInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Azure Connector is validated in InfrastructureStep
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData = stepHelper.getPreDeploymentData(ambiance,
        ((AzureWebAppSwapSlotStepParameters) stepParameters.getSpec()).slotDeploymentStepFqn + "."
            + AzureWebAppPreDeploymentDataOutput.OUTPUT_NAME);
    if (azureAppServicePreDeploymentData == null) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("No successful Slot deployment step found, swap slots can't be done")
                                  .build())
          .build();
    } else {
      AzureWebAppInfraDelegateConfig azureWebAppInfraDelegateConfig = stepHelper.getInfraDelegateConfig(
          ambiance, azureAppServicePreDeploymentData.getAppName(), azureAppServicePreDeploymentData.getSlotName());
      AzureWebAppSwapSlotStepParameters azureWebAppSwapSlotStepParameters =
          (AzureWebAppSwapSlotStepParameters) stepParameters.getSpec();

      if (isEmpty(getParameterFieldValue(azureWebAppSwapSlotStepParameters.getTargetSlot()))) {
        throw new InvalidArgumentsException(TARGET_SLOT_NAME_BLANK_ERROR_MSG);
      }

      AzureWebAppSwapSlotsRequest azureWebAppSwapSlotsRequest =
          AzureWebAppSwapSlotsRequest.builder()
              .accountId(AmbianceUtils.getAccountId(ambiance))
              .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
              .infrastructure(azureWebAppInfraDelegateConfig)
              .targetSlot(AzureResourceUtility.fixDeploymentSlotName(
                  getParameterFieldValue(azureWebAppSwapSlotStepParameters.getTargetSlot()),
                  azureAppServicePreDeploymentData.getAppName()))
              .build();

      return stepHelper.prepareTaskRequest(stepParameters, ambiance, azureWebAppSwapSlotsRequest,
          TaskType.AZURE_WEB_APP_TASK_NG, Collections.singletonList(SLOT_SWAP));
    }
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepParameters, ThrowingSupplier<AzureWebAppTaskResponse> responseDataSupplier)
      throws Exception {
    StepResponseBuilder builder = StepResponse.builder();
    AzureWebAppTaskResponse response;
    try {
      response = responseDataSupplier.get();
    } catch (Exception ex) {
      log.error("Error while processing Azure WebApp Swap Slot response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }
    AzureWebAppSwapSlotStepParameters azureWebAppSwapSlotStepParameters =
        (AzureWebAppSwapSlotStepParameters) stepParameters.getSpec();
    executionSweepingOutputService.consume(ambiance, AzureWebAppSwapSlotsDataOutput.OUTPUT_NAME,
        AzureWebAppSwapSlotsDataOutput.builder()
            .deploymentProgressMarker(AppServiceDeploymentProgress.SWAP_SLOT.name())
            .targetSlot(getParameterFieldValue(azureWebAppSwapSlotStepParameters.getTargetSlot()))
            .build(),
        StepCategory.STEP.name());

    updateTargetSlot(ambiance, getParameterFieldValue(azureWebAppSwapSlotStepParameters.getTargetSlot()));

    builder.unitProgressList(response.getCommandUnitsProgress().getUnitProgresses());
    builder.status(Status.SUCCEEDED);
    return builder.build();
  }

  private void updateTargetSlot(Ambiance ambiance, String targetSlot) {
    Scope scope = Scope.of(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance));
    Map<String, Object> updates = new HashMap<>();
    updates.put(String.format(
                    "%s.%s", StageExecutionInfoKeys.executionDetails, AzureWebAppsStageExecutionDetailsKeys.targetSlot),
        targetSlot);
    stageExecutionInfoService.update(scope, ambiance.getStageExecutionId(), updates);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
