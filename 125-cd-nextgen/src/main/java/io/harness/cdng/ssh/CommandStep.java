/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.AwsSshWinrmToServiceInstanceInfoMapper;
import io.harness.delegate.beans.instancesync.mapper.AzureSshWinrmToServiceInstanceInfoMapper;
import io.harness.delegate.beans.instancesync.mapper.PdcToServiceInstanceInfoMapper;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.CommandTaskResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class CommandStep extends TaskExecutableWithRollbackAndRbac<CommandTaskResponse> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.COMMAND.getYamlType()).setStepCategory(StepCategory.STEP).build();

  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private SshCommandStepHelper sshCommandStepHelper;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private OutcomeService outcomeService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Noop
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    CommandStepParameters executeCommandStepParameters = (CommandStepParameters) stepParameters.getSpec();
    validateStepParameters(executeCommandStepParameters);

    CommandTaskParameters taskParameters =
        sshCommandStepHelper.buildCommandTaskParameters(ambiance, executeCommandStepParameters);

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(TaskType.COMMAND_TASK_NG.name())
            .parameters(new Object[] {taskParameters})
            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), StepUtils.DEFAULT_STEP_TIMEOUT))
            .build();

    List<String> commandExecutionUnits =
        taskParameters.getCommandUnits().stream().map(cu -> cu.getName()).collect(Collectors.toList());
    String taskName = TaskType.COMMAND_TASK_NG.getDisplayName();

    return StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer, commandExecutionUnits, taskName,
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(executeCommandStepParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<CommandTaskResponse> responseDataSupplier) throws Exception {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    CommandTaskResponse taskResponse;
    try {
      taskResponse = responseDataSupplier.get();
    } catch (Exception ex) {
      log.error("Error while processing Command Task response: {}", ex.getMessage(), ex);
      return sshCommandStepHelper.handleTaskException(ambiance, stepParameters, ex);
    }

    if (taskResponse == null) {
      return sshCommandStepHelper.handleTaskException(
          ambiance, stepParameters, new InvalidArgumentsException("Failed to process Command Task response"));
    }

    List<UnitProgress> unitProgresses = taskResponse.getUnitProgressData() == null
        ? emptyList()
        : taskResponse.getUnitProgressData().getUnitProgresses();
    stepResponseBuilder.unitProgressList(unitProgresses);

    stepResponseBuilder.status(StepUtils.getStepStatus(taskResponse.getStatus()));

    FailureInfo.Builder failureInfoBuilder = FailureInfo.newBuilder();
    if (taskResponse.getErrorMessage() != null) {
      failureInfoBuilder.setErrorMessage(taskResponse.getErrorMessage());
    }
    stepResponseBuilder.failureInfo(failureInfoBuilder.build());

    CommandStepParameters executeCommandStepParameters = (CommandStepParameters) stepParameters.getSpec();
    String host = ParameterFieldHelper.getParameterFieldValue(executeCommandStepParameters.getHost());
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    String serviceType = serviceOutcome.getType();
    if (!(serviceType.equals(ServiceSpecType.SSH) || serviceType.equals(ServiceSpecType.WINRM))) {
      throw new InvalidArgumentsException("Invalid service outcome found " + serviceOutcome);
    }

    CommandStepOutcome commandStepOutcome = CommandStepOutcome.builder().host(host).build();
    InfrastructureOutcome infrastructure = cdStepHelper.getInfrastructureOutcome(ambiance);

    ServerInstanceInfo serverInstanceInfo;
    if (infrastructure instanceof PdcInfrastructureOutcome) {
      serverInstanceInfo =
          PdcToServiceInstanceInfoMapper.toServerInstanceInfo(serviceType, host, infrastructure.getInfrastructureKey());
    } else if (infrastructure instanceof SshWinRmAzureInfrastructureOutcome) {
      serverInstanceInfo = AzureSshWinrmToServiceInstanceInfoMapper.toServerInstanceInfo(
          serviceType, host, infrastructure.getInfrastructureKey());
    } else if (infrastructure instanceof SshWinRmAwsInfrastructureOutcome) {
      serverInstanceInfo = AwsSshWinrmToServiceInstanceInfoMapper.toServerInstanceInfo(
          serviceType, host, infrastructure.getInfrastructureKey());
    } else {
      throw new InvalidArgumentsException(
          "Invalid infrastructure outcome found " + infrastructure.getClass().getSimpleName());
    }

    if (CommandExecutionStatus.SUCCESS.equals(taskResponse.getStatus())) {
      instanceInfoService.saveServerInstancesIntoSweepingOutput(
          ambiance, Collections.singletonList(serverInstanceInfo));
    }

    return stepResponseBuilder
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(commandStepOutcome)
                         .build())
        .build();
  }

  private void validateStepParameters(CommandStepParameters executeCommandStepParameters) {
    boolean onDelegate =
        ParameterFieldHelper.getBooleanParameterFieldValue(executeCommandStepParameters.getOnDelegate());
    if (!onDelegate) {
      String host = ParameterFieldHelper.getParameterFieldValue(executeCommandStepParameters.getHost());
      if (isEmpty(host)) {
        throw new InvalidArgumentsException("Host information is missing in Command Step.");
      }
    }
  }
}
