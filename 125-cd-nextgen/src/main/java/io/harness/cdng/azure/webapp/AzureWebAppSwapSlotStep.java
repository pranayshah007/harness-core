/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.azure.model.AzureConstants.SLOT_SWAP;
import static io.harness.azure.model.AzureConstants.TARGET_SLOT_NAME_BLANK_ERROR_MSG;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.AZURE_WEBAPP_SWAP_SLOTS_OUTCOME;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppSwapSlotsRequest;
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
public class AzureWebAppSwapSlotStep extends AbstractAzureWebAppStep {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_SWAP_SLOT.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Azure Connector is validated in InfrastructureStep
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    AzureWebAppInfrastructureOutcome azureWebAppInfraOutcome =
        (AzureWebAppInfrastructureOutcome) cdStepHelper.getInfrastructureOutcome(ambiance);

    if (isBlank(azureWebAppInfraOutcome.getTargetSlot())) {
      throw new InvalidArgumentsException(TARGET_SLOT_NAME_BLANK_ERROR_MSG);
    }

    AzureWebAppSwapSlotsRequest azureWebAppSwapSlotsRequest =
        AzureWebAppSwapSlotsRequest.builder()
            .targetSlot(azureWebAppInfraOutcome.getTargetSlot())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .infrastructure(getAzureWebAppInfrastructure(ambiance))
            .build();

    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.AZURE_WEB_APP_TASK_NG.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                            .parameters(new Object[] {azureWebAppSwapSlotsRequest})
                            .build();

    return prepareCDTaskRequest(ambiance, taskData, kryoSerializer, Collections.singletonList(SLOT_SWAP),
        TaskType.AZURE_WEB_APP_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(
            ((AzureWebAppSwapSlotStepParameters) stepParameters.getSpec()).getDelegateSelectors()),
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
      log.error("Error while processing Azure WebApp Swap Slot response: {}", ExceptionUtils.getMessage(ex), ex);
      throw ex;
    }
    builder.unitProgressList(response.getCommandUnitsProgress().getUnitProgresses());
    executionSweepingOutputService.consume(ambiance, AZURE_WEBAPP_SWAP_SLOTS_OUTCOME,
        AzureWebAppSwapSlotsOutcome.builder().build(), StepCategory.STEP.name());
    builder.status(Status.SUCCEEDED);
    return builder.build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
