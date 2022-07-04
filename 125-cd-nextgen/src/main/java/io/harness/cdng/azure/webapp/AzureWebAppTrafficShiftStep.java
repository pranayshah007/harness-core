/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.azure.model.AzureConstants.TRAFFIC_WEIGHT_IN_PERCENTAGE_INVALID_ERROR_MSG;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppTrafficShiftRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AzureWebAppTrafficShiftStep extends AbstractAzureWebAppStep {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_TRAFFIC_SHIFT.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Azure Connector is validated in InfrastructureStep
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    AzureWebAppTrafficShiftStepParameters azureWebAppTrafficShiftStepParameters =
        (AzureWebAppTrafficShiftStepParameters) stepParameters.getSpec();

    double trafficPercent =
        getTrafficPercentage(azureWebAppTrafficShiftStepParameters.getTraffic().getValue(), ambiance);

    if (trafficPercent > 100.0 || trafficPercent < 0) {
      throw new InvalidArgumentsException(TRAFFIC_WEIGHT_IN_PERCENTAGE_INVALID_ERROR_MSG);
    }

    AzureWebAppTrafficShiftRequest azureWebAppTrafficShiftRequest =
        AzureWebAppTrafficShiftRequest.builder()
            .trafficPercentage(trafficPercent)
            .infrastructure(getAzureWebAppInfrastructure(ambiance))
            .build();

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.AZURE_WEB_APP_TASK_NG.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                            .parameters(new Object[] {azureWebAppTrafficShiftRequest})
                            .build();

    return prepareCDTaskRequest(ambiance, taskData, kryoSerializer, Collections.singletonList(SLOT_TRAFFIC_PERCENTAGE),
        TaskType.AZURE_WEB_APP_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(azureWebAppTrafficShiftStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<AzureWebAppTaskResponse> responseDataSupplier) throws Exception {
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
