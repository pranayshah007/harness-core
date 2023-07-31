/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.azure.model.AzureConstants.DEPLOY_TO_SLOT;
import static io.harness.azure.model.AzureConstants.FETCH_ARTIFACT_FILE;
import static io.harness.azure.model.AzureConstants.SLOT_SWAP;
import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.azure.model.AzureConstants.UPDATE_SLOT_CONFIGURATION_SETTINGS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.azure.webapp.beans.AzureWebAppPreDeploymentDataOutput;
import io.harness.cdng.azure.webapp.beans.AzureWebAppSlotDeploymentDataOutput;
import io.harness.cdng.azure.webapp.beans.AzureWebAppSwapSlotsDataOutput;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.execution.azure.webapps.AzureWebAppsStageExecutionDetails;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.delegate.beans.instancesync.mapper.AzureWebAppToServerInstanceInfoMapper;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppRollbackRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppNGRollbackResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureArtifactType;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
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
public class AzureWebAppRollbackStep extends CdTaskExecutable<AzureWebAppTaskResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_WEBAPP_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private CDStepHelper cdStepHelper;
  @Inject private AzureWebAppStepHelper azureWebAppStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        azureWebAppStepHelper.getPreDeploymentData(ambiance,
            ((AzureWebAppRollbackStepParameters) stepParameters.getSpec()).slotDeploymentStepFqn + "."
                + AzureWebAppPreDeploymentDataOutput.OUTPUT_NAME);
    if (azureAppServicePreDeploymentData == null) {
      return getSkipTaskRequest("Slot deployment step was not successful, rollback data not found");
    } else {
      OptionalSweepingOutput swapSlotsSweepingOutput = getSwapSlotsSweepingOutput(ambiance, stepParameters);
      String lastDeploymentProgressMarker =
          getDeploymentProgressMarker(ambiance, stepParameters, swapSlotsSweepingOutput);
      if (isEmpty(lastDeploymentProgressMarker)) {
        lastDeploymentProgressMarker = AppServiceDeploymentProgress.DEPLOY_TO_SLOT.name();
      }
      azureAppServicePreDeploymentData.setDeploymentProgressMarker(lastDeploymentProgressMarker);

      AzureWebAppInfraDelegateConfig infraDelegateConfig = azureWebAppStepHelper.getInfraDelegateConfig(
          ambiance, azureAppServicePreDeploymentData.getAppName(), azureAppServicePreDeploymentData.getSlotName());
      ArtifactOutcome artifactOutcome = azureWebAppStepHelper.getPrimaryArtifactOutcome(ambiance);
      boolean isPackageType = azureWebAppStepHelper.isPackageArtifactType(artifactOutcome);
      AzureArtifactConfig previousArtifactConfig = null;
      if (isPackageType) {
        previousArtifactConfig = getPreviousArtifactConfig(ambiance, infraDelegateConfig);
        if (previousArtifactConfig == null && !swapSlotsSweepingOutput.isFound()) {
          return getSkipTaskRequest("No swap slots done and previous artifact not found, skipping rollback");
        }
        if (previousArtifactConfig != null
            && previousArtifactConfig.getArtifactType().equals(AzureArtifactType.CONTAINER)) {
          return getSkipTaskRequest(
              "Rollback is not possible when previous artifact is of type Docker and current artifact is of type package");
        }
      }

      AzureWebAppRollbackRequest azureWebAppRollbackRequest =
          AzureWebAppRollbackRequest.builder()
              .accountId(AmbianceUtils.getAccountId(ambiance))
              .preDeploymentData(azureAppServicePreDeploymentData)
              .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
              .infrastructure(infraDelegateConfig)
              .targetSlot(
                  AzureResourceUtility.fixDeploymentSlotName(getTargetSlotFromSweepingOutput(swapSlotsSweepingOutput),
                      azureAppServicePreDeploymentData.getAppName()))
              .artifact(previousArtifactConfig)
              .azureArtifactType(isPackageType ? AzureArtifactType.PACKAGE : AzureArtifactType.CONTAINER)
              .build();

      List<String> units = getUnits(swapSlotsSweepingOutput, azureWebAppRollbackRequest.getArtifact() != null);
      return azureWebAppStepHelper.prepareTaskRequest(
          stepParameters, ambiance, azureWebAppRollbackRequest, TaskType.AZURE_WEB_APP_TASK_NG, units);
    }
  }

  private TaskRequest getSkipTaskRequest(String message) {
    return TaskRequest.newBuilder()
        .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(message).build())
        .build();
  }

  private AzureArtifactConfig getPreviousArtifactConfig(
      Ambiance ambiance, AzureWebAppInfraDelegateConfig infraDelegateConfig) {
    AzureWebAppsStageExecutionDetails executionDetails =
        azureWebAppStepHelper.findLastSuccessfulStageExecutionDetails(ambiance, infraDelegateConfig);
    return executionDetails != null ? executionDetails.getArtifactConfig() : null;
  }

  private String getTargetSlotFromSweepingOutput(OptionalSweepingOutput swapSlotsSweepingOutput) {
    if (swapSlotsSweepingOutput.isFound()) {
      return ((AzureWebAppSwapSlotsDataOutput) swapSlotsSweepingOutput.getOutput()).getTargetSlot();
    }
    return null;
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
      log.error("Error while processing Azure WebApp Rollback response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }

    StepResponse.StepOutcome stepOutcome = instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance,
        AzureWebAppToServerInstanceInfoMapper.toServerInstanceInfoList(
            ((AzureWebAppNGRollbackResponse) response.getRequestResponse()).getAzureAppDeploymentData()));
    builder.unitProgressList(response.getCommandUnitsProgress().getUnitProgresses());
    builder.stepOutcome(stepOutcome).status(Status.SUCCEEDED);
    return builder.build();
  }

  @NotNull
  private List<String> getUnits(OptionalSweepingOutput swapSlotsSweepingOutput, boolean shouldFetchArtifact) {
    List<String> units = new ArrayList<>();
    if (shouldFetchArtifact) {
      units.add(FETCH_ARTIFACT_FILE);
    }
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

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
