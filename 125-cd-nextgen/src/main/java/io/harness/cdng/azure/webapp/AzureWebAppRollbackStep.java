/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.azure.model.AzureConstants.DEPLOY_TO_SLOT;
import static io.harness.azure.model.AzureConstants.SLOT_SWAP;
import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.azure.model.AzureConstants.UPDATE_SLOT_CONFIGURATION_SETTINGS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.azure.webapp.beans.AzureWebAppPreDeploymentDataOutput;
import io.harness.cdng.azure.webapp.beans.AzureWebAppSlotDeploymentDataOutput;
import io.harness.cdng.azure.webapp.beans.AzureWebAppSwapSlotsDataOutput;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppRollbackRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AzureWebAppRollbackStep extends TaskExecutableWithRollbackAndRbac<AzureWebAppTaskResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_WEBAPP_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private CDStepHelper cdStepHelper;
  @Inject private AzureWebAppStepHelper azureWebAppStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData = getPreDeploymentData(ambiance, stepParameters);
    if (azureAppServicePreDeploymentData == null) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("Slot deployment step was not successful, rollback data not found")
                                  .build())
          .build();

    } else {
      OptionalSweepingOutput swapSlotsSweepingOutput = getSwapSlotsSweepingOutput(ambiance, stepParameters);
      String lastDeploymentProgressMarker =
          getDeploymentProgressMarker(ambiance, stepParameters, swapSlotsSweepingOutput);
      if (isEmpty(lastDeploymentProgressMarker)) {
        lastDeploymentProgressMarker = AppServiceDeploymentProgress.DEPLOY_TO_SLOT.name();
      }
      azureAppServicePreDeploymentData.setDeploymentProgressMarker(lastDeploymentProgressMarker);

      AzureWebAppRollbackRequest azureWebAppRollbackRequest =
          AzureWebAppRollbackRequest.builder()
              .accountId(AmbianceUtils.getAccountId(ambiance))
              .preDeploymentData(azureAppServicePreDeploymentData)
              .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
              .infrastructure(azureWebAppStepHelper.getInfraDelegateConfig(ambiance))
              .build();

      List<String> units = getUnits(swapSlotsSweepingOutput);
      return azureWebAppStepHelper.prepareTaskRequest(
          stepParameters, ambiance, azureWebAppRollbackRequest, TaskType.AZURE_WEB_APP_TASK_NG, units);
    }
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<AzureWebAppTaskResponse> responseDataSupplier) throws Exception {
    StepResponseBuilder builder = StepResponse.builder();
    AzureWebAppTaskResponse response;
    try {
      response = responseDataSupplier.get();
    } catch (Exception ex) {
      log.error("Error while processing Azure WebApp Rollback response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }
    builder.unitProgressList(response.getCommandUnitsProgress().getUnitProgresses());
    builder.status(Status.SUCCEEDED);
    return builder.build();
  }

  @NotNull
  private List<String> getUnits(OptionalSweepingOutput swapSlotsSweepingOutput) {
    List<String> units = new ArrayList<>();
    if (swapSlotsSweepingOutput.isFound()) {
      units.add(SLOT_SWAP);
    }
    units.addAll(asList(UPDATE_SLOT_CONFIGURATION_SETTINGS, DEPLOY_TO_SLOT, SLOT_TRAFFIC_PERCENTAGE));
    return units;
  }

  private String getDeploymentProgressMarker(
      Ambiance ambiance, StepElementParameters stepParameters, OptionalSweepingOutput swapSlotsSweepingOutput) {
    if (swapSlotsSweepingOutput.isFound()) {
      return ((AzureWebAppSwapSlotsDataOutput) swapSlotsSweepingOutput.getOutput()).getDeploymentProgressMarker();
    }

    OptionalSweepingOutput slotDeploymentSweepingOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            ((AzureWebAppRollbackStepParameters) stepParameters.getSpec()).getSlotDeploymentStepFqn() + "."
            + AzureWebAppSlotDeploymentDataOutput.OUTPUT_NAME));
    if (slotDeploymentSweepingOutput.isFound()) {
      return ((AzureWebAppSlotDeploymentDataOutput) slotDeploymentSweepingOutput.getOutput())
          .getDeploymentProgressMarker();
    }
    return null;
  }

  private OptionalSweepingOutput getSwapSlotsSweepingOutput(Ambiance ambiance, StepElementParameters stepParameters) {
    return executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            ((AzureWebAppRollbackStepParameters) stepParameters.getSpec()).getSwapSlotStepFqn() + "."
            + AzureWebAppSwapSlotsDataOutput.OUTPUT_NAME));
  }

  private AzureAppServicePreDeploymentData getPreDeploymentData(
      Ambiance ambiance, StepElementParameters stepParameters) {
    OptionalSweepingOutput sweepingOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            ((AzureWebAppRollbackStepParameters) stepParameters.getSpec()).slotDeploymentStepFqn + "."
            + AzureWebAppPreDeploymentDataOutput.OUTPUT_NAME));
    if (sweepingOutput.isFound()) {
      return ((AzureWebAppPreDeploymentDataOutput) sweepingOutput.getOutput()).getPreDeploymentData();
    }
    return null;
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
