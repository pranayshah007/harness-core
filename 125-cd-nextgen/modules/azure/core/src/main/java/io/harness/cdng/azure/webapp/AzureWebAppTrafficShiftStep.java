/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_PRODUCTION_NAME;
import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.azure.model.AzureConstants.TRAFFIC_WEIGHT_IN_PERCENTAGE_INVALID_ERROR_MSG;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.azure.webapp.beans.AzureWebAppPreDeploymentDataOutput;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppTrafficShiftRequest;
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
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AzureWebAppTrafficShiftStep extends CdTaskExecutable<AzureWebAppTaskResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_TRAFFIC_SHIFT.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private AzureWebAppStepHelper azureWebAppStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Azure Connector is validated in InfrastructureStep
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    AzureWebAppTrafficShiftStepParameters azureWebAppTrafficShiftStepParameters =
        (AzureWebAppTrafficShiftStepParameters) stepParameters.getSpec();
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        azureWebAppStepHelper.getPreDeploymentData(ambiance,
            ((AzureWebAppTrafficShiftStepParameters) stepParameters.getSpec()).slotDeploymentStepFqn + "."
                + AzureWebAppPreDeploymentDataOutput.OUTPUT_NAME);
    if (azureAppServicePreDeploymentData == null) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("No successful Slot deployment step found, traffic shift can't be done")
                                  .build())
          .build();
    } else {
      double trafficPercent =
          getTrafficPercentage(azureWebAppTrafficShiftStepParameters.getTraffic().getValue(), ambiance);

      if (trafficPercent > 100.0 || trafficPercent < 0) {
        throw new InvalidArgumentsException(TRAFFIC_WEIGHT_IN_PERCENTAGE_INVALID_ERROR_MSG);
      }
      if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equalsIgnoreCase(azureAppServicePreDeploymentData.getSlotName())) {
        throw new InvalidArgumentsException(
            "Traffic shift is supposed to shift traffic from PRODUCTION slot to deployment slot. Traffic shift is not applicable when deployment slot is PRODUCTION.");
      }

      AzureWebAppTrafficShiftRequest azureWebAppTrafficShiftRequest =
          AzureWebAppTrafficShiftRequest.builder()
              .accountId(AmbianceUtils.getAccountId(ambiance))
              .trafficPercentage(trafficPercent)
              .infrastructure(azureWebAppStepHelper.getInfraDelegateConfig(ambiance,
                  azureAppServicePreDeploymentData.getAppName(), azureAppServicePreDeploymentData.getSlotName()))
              .build();

      return azureWebAppStepHelper.prepareTaskRequest(stepParameters, ambiance, azureWebAppTrafficShiftRequest,
          TaskType.AZURE_WEB_APP_TASK_NG, Collections.singletonList(SLOT_TRAFFIC_PERCENTAGE));
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
      log.error("Error while processing Azure WebApp Traffic Shift response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }
    builder.unitProgressList(response.getCommandUnitsProgress().getUnitProgresses());
    builder.status(Status.SUCCEEDED);
    return builder.build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private double getTrafficPercentage(String trafficPercentage, Ambiance ambiance) {
    try {
      return Double.parseDouble(trafficPercentage);
    } catch (NumberFormatException ex) {
      throw new InvalidArgumentsException(
          format("Failed to parse traffic percentage for step Id '%s' and step type '%s'",
              AmbianceUtils.obtainStepIdentifier(ambiance), STEP_TYPE.getType()));
    }
  }
}
