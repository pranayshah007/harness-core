/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.ssh.utils.CommandStepUtils.prepareOutputVariables;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
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
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.SkipRollbackException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.shell.ShellExecutionData;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class CommandStep extends CdTaskExecutable<CommandTaskResponse> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.COMMAND.getYamlType()).setStepCategory(StepCategory.STEP).build();

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private SshCommandStepHelper sshCommandStepHelper;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private OutcomeService outcomeService;
  @Inject private CommandTaskDataFactory commandTaskDataFactory;

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
    try {
      CommandStepParameters executeCommandStepParameters = (CommandStepParameters) stepParameters.getSpec();
      validateStepParameters(executeCommandStepParameters);

      CommandTaskParameters taskParameters =
          sshCommandStepHelper.buildCommandTaskParameters(ambiance, executeCommandStepParameters);

      TaskData taskData = commandTaskDataFactory.create(taskParameters, stepParameters.getTimeout());

      List<String> commandExecutionUnits =
          taskParameters.getCommandUnits().stream().map(NgCommandUnit::getName).collect(Collectors.toList());
      String taskName = TaskType.valueOf(taskData.getTaskType()).getDisplayName();

      return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
          commandExecutionUnits, taskName,
          TaskSelectorYaml.toTaskSelector(
              emptyIfNull(getParameterFieldValue(executeCommandStepParameters.getDelegateSelectors()))),
          stepHelper.getEnvironmentType(ambiance));
    } catch (SkipRollbackException e) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(e.getMessage()).build())
          .build();
    }
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
    if (!(serviceType.equals(ServiceSpecType.SSH) || serviceType.equals(ServiceSpecType.WINRM)
            || serviceType.equals(ServiceSpecType.CUSTOM_DEPLOYMENT))) {
      throw new InvalidArgumentsException("Invalid service outcome found " + serviceOutcome);
    }

    InfrastructureOutcome infrastructure = cdStepHelper.getInfrastructureOutcome(ambiance);

    Optional<ServerInstanceInfo> serverInstanceInfo = getServerInstanceInfo(infrastructure, serviceType, host);

    if (CommandExecutionStatus.SUCCESS.equals(taskResponse.getStatus())) {
      serverInstanceInfo.ifPresent(instanceInfo
          -> instanceInfoService.saveServerInstancesIntoSweepingOutput(
              ambiance, Collections.singletonList(instanceInfo)));

      CommandStepOutcome commandStepOutcome = getCommandStepOutcome(taskResponse, executeCommandStepParameters, host);
      stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(commandStepOutcome)
                                          .build());
    }

    return stepResponseBuilder.build();
  }

  private Optional<ServerInstanceInfo> getServerInstanceInfo(
      InfrastructureOutcome infrastructure, String serviceType, String host) {
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
    } else if (infrastructure instanceof CustomDeploymentInfrastructureOutcome) {
      return Optional.empty();
    } else {
      throw new InvalidArgumentsException(
          "Invalid infrastructure outcome found " + infrastructure.getClass().getSimpleName());
    }
    return Optional.of(serverInstanceInfo);
  }

  private void validateStepParameters(CommandStepParameters executeCommandStepParameters) {
    boolean onDelegate =
        ParameterFieldHelper.getBooleanParameterFieldValue(executeCommandStepParameters.getOnDelegate());
    if (!onDelegate) {
      String host = ParameterFieldHelper.getParameterFieldValue(executeCommandStepParameters.getHost());
      if (isEmpty(host)) {
        throw new InvalidArgumentsException(
            "Host information is missing in Command Step. Please make sure the looping strategy (repeat) is provided.");
      }
    }
  }

  private CommandStepOutcome getCommandStepOutcome(
      CommandTaskResponse taskResponse, CommandStepParameters executeCommandStepParameters, String host) {
    Map<String, String> outputVariables = getOutputVariables(taskResponse, executeCommandStepParameters);
    return CommandStepOutcome.builder().host(host).outputVariables(outputVariables).build();
  }

  private Map<String, String> getOutputVariables(
      CommandTaskResponse taskResponse, CommandStepParameters executeCommandStepParameters) {
    Map<String, String> outputVariables = null;
    if (taskResponse.getExecuteCommandResponse() != null
        && taskResponse.getExecuteCommandResponse().getCommandExecutionData() != null) {
      ShellExecutionData commandExecutionData =
          (ShellExecutionData) taskResponse.getExecuteCommandResponse().getCommandExecutionData();
      outputVariables = prepareOutputVariables(commandExecutionData.getSweepingOutputEnvVariables(),
          executeCommandStepParameters.getOutputVariables(),
          executeCommandStepParameters.getSecretOutputVariablesNames());
    }
    return outputVariables;
  }
}
