/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.INFRA_TASK_EXECUTABLE_STEP_OUTPUT;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.logging.LogCallbackUtils.saveExecutionLogSafely;
import static io.harness.ng.core.infrastructure.InfrastructureKind.SSH_WINRM_AWS;
import static io.harness.ng.core.infrastructure.InfrastructureKind.SSH_WINRM_AZURE;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.helper.ExecutionInfoKeyMapper;
import io.harness.cdng.execution.helper.StageExecutionHelper;
import io.harness.cdng.infra.InfrastructureOutcomeProvider;
import io.harness.cdng.infra.InfrastructureValidator;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.AwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.AwsSamInfrastructure;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure;
import io.harness.cdng.infra.yaml.GoogleFunctionsInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureDetailsAbstract;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.K8sRancherInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.infra.yaml.TanzuApplicationServiceInfrastructure;
import io.harness.cdng.instance.InstanceOutcomeHelper;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.ssh.output.HostsOutput;
import io.harness.cdng.ssh.output.SshInfraDelegateConfigOutput;
import io.harness.cdng.ssh.output.WinRmInfraDelegateConfigOutput;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.azure.response.AzureHostsResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGInstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.beans.connector.azureconnector.AzureAdditionalParams;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.ssh.AwsInfraDelegateConfig;
import io.harness.delegate.task.ssh.AwsWinrmInfraDelegateConfig;
import io.harness.delegate.task.ssh.AzureInfraDelegateConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.environment.EnvironmentOutcome;

import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class AbstractInfrastructureTaskExecutableStep {
  public static final String LOG_SUFFIX = "Execute";
  private static final String DEFAULT_TIMEOUT = "10m";
  protected static final long DEFAULT_START_TIME_INTERVAL = 10 * 60 * 1000L;
  @Inject protected InfrastructureStepHelper infrastructureStepHelper;
  @Inject protected OutcomeService outcomeService;
  @Inject protected CDStepHelper cdStepHelper;
  @Inject protected ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject @Named("referenceFalseKryoSerializer") protected KryoSerializer referenceFalseKryoSerializer;
  @Inject protected StepHelper stepHelper;
  @Inject protected StageExecutionHelper stageExecutionHelper;
  @Inject CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  @Inject private InfrastructureValidator infrastructureValidator;
  @Inject protected InstanceOutcomeHelper instanceOutcomeHelper;
  @Inject protected InfrastructureOutcomeProvider infrastructureOutcomeProvider;

  @Data
  @AllArgsConstructor
  protected static class OutcomeSet {
    private ServiceStepOutcome serviceStepOutcome;
    private EnvironmentOutcome environmentOutcome;
  }
  @Data
  @AllArgsConstructor
  protected static class TaskRequestData {
    private TaskRequest taskRequest;
    private TaskData taskData;
    private List<TaskSelectorYaml> taskSelectorYamls;
  }

  protected OutcomeSet fetchRequiredOutcomes(Ambiance ambiance) {
    EnvironmentOutcome environmentOutcome = (EnvironmentOutcome) executionSweepingOutputService.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT));
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    return new OutcomeSet(serviceOutcome, environmentOutcome);
  }

  protected TaskRequestData obtainTaskInternal(Ambiance ambiance, Infrastructure infrastructure,
      NGLogCallback logCallback, Boolean addRcStep, boolean skipInstances, Map<String, String> tags) {
    saveExecutionLog(logCallback, "Starting infrastructure step...");

    validateConnector(infrastructure, ambiance, logCallback);
    validateInfrastructure(infrastructure);

    saveExecutionLog(logCallback, "Fetching environment information...");
    final OutcomeSet outcomeSet = fetchRequiredOutcomes(ambiance);
    final EnvironmentOutcome environmentOutcome = outcomeSet.getEnvironmentOutcome();
    final ServiceStepOutcome serviceOutcome = outcomeSet.getServiceStepOutcome();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    infrastructureValidator.validate(infrastructure);

    final InfrastructureOutcome infrastructureOutcome =
        infrastructureOutcomeProvider.getOutcome(ambiance, infrastructure, environmentOutcome, serviceOutcome,
            ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), tags);

    executionSweepingOutputService.consume(ambiance, INFRA_TASK_EXECUTABLE_STEP_OUTPUT,
        InfrastructureTaskExecutableStepSweepingOutput.builder()
            .infrastructureOutcome(infrastructureOutcome)
            .skipInstances(skipInstances)
            .addRcStep(addRcStep != null && addRcStep)
            .build(),
        "");

    if (environmentOutcome != null) {
      if (isNotEmpty(environmentOutcome.getName())) {
        saveExecutionLog(logCallback, color(format("Environment Name: %s", environmentOutcome.getName()), Yellow));
      }

      if (environmentOutcome.getType() != null && isNotEmpty(environmentOutcome.getType().name())) {
        saveExecutionLog(
            logCallback, color(format("Environment Type: %s", environmentOutcome.getType().name()), Yellow));
      }
      saveExecutionLog(logCallback, color("Environment information fetched", Green));
    }

    if (infrastructureOutcome != null && isNotEmpty(infrastructureOutcome.getKind())) {
      saveExecutionLog(
          logCallback, color(format("Infrastructure Definition Type: %s", infrastructureOutcome.getKind()), Yellow));
      saveExecutionLog(
          logCallback, format("Fetching instances from %s infrastructure", infrastructureOutcome.getKind()));
    }

    return getTaskRequest(ambiance, serviceOutcome, infrastructureOutcome);
  }

  protected StepResponse handleTaskResult(Ambiance ambiance,
      InfrastructureTaskExecutableStepSweepingOutput stepSweepingOutput, DelegateResponseData responseData,
      NGLogCallback logCallback) {
    log.info("Handling Task Result With Security Context for the Infrastructure Step");
    long startTime = System.currentTimeMillis() - DEFAULT_START_TIME_INTERVAL;

    final OutcomeSet outcomeSet = fetchRequiredOutcomes(ambiance);
    final ExecutionInfoKey executionInfoKey =
        ExecutionInfoKeyMapper.getExecutionInfoKey(ambiance, outcomeSet.getEnvironmentOutcome(),
            outcomeSet.getServiceStepOutcome(), stepSweepingOutput.getInfrastructureOutcome());

    boolean skipInstances = stepSweepingOutput.isSkipInstances();
    if (responseData instanceof AzureHostsResponse) {
      return handleAzureHostResponse(responseData, skipInstances, ambiance, logCallback,
          stepSweepingOutput.getInfrastructureOutcome(), executionInfoKey, startTime);
    } else if (responseData instanceof AwsListEC2InstancesTaskResponse) {
      return handleAwsListEC2InstancesTaskResponse(responseData, skipInstances, ambiance, logCallback,
          stepSweepingOutput.getInfrastructureOutcome(), executionInfoKey, startTime);
    }

    return buildFailureStepResponse(
        startTime, format("Unhandled response data: %s received", responseData.getClass()), logCallback);
  }

  protected InfrastructureTaskExecutableStepSweepingOutput fetchInfraStepOutputOrThrow(Ambiance ambiance) {
    OptionalSweepingOutput output = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.INFRA_TASK_EXECUTABLE_STEP_OUTPUT));
    if (!output.isFound()) {
      throw new InvalidRequestException("Infrastructure could not be resolved");
    }
    return (InfrastructureTaskExecutableStepSweepingOutput) output.getOutput();
  }
  StepResponse handleAzureHostResponse(DelegateResponseData responseData, boolean skipInstances, Ambiance ambiance,
      NGLogCallback logCallback, InfrastructureOutcome infrastructureOutcome, ExecutionInfoKey executionInfoKey,
      long startTime) {
    AzureHostsResponse azureHostsResponse = (AzureHostsResponse) responseData;
    switch (azureHostsResponse.getCommandExecutionStatus()) {
      case SUCCESS:
        return publishAzureHosts(azureHostsResponse, skipInstances, ambiance, logCallback, infrastructureOutcome,
            executionInfoKey, startTime);
      case FAILURE:
        return buildFailureStepResponse(
            startTime, HarnessStringUtils.emptyIfNull(azureHostsResponse.getErrorSummary()), logCallback);
      default:
        return buildFailureStepResponse(startTime,
            format("Unhandled command execution status: %s received", azureHostsResponse.getCommandExecutionStatus()),
            logCallback);
    }
  }

  StepResponse handleAwsListEC2InstancesTaskResponse(DelegateResponseData responseData, boolean skipInstances,
      Ambiance ambiance, NGLogCallback logCallback, InfrastructureOutcome infrastructureOutcome,
      ExecutionInfoKey executionInfoKey, long startTime) {
    AwsListEC2InstancesTaskResponse awsListEC2InstancesTaskResponse = (AwsListEC2InstancesTaskResponse) responseData;
    switch (awsListEC2InstancesTaskResponse.getCommandExecutionStatus()) {
      case SUCCESS:
        return publishAwsHosts(awsListEC2InstancesTaskResponse, skipInstances, ambiance, logCallback,
            infrastructureOutcome, executionInfoKey, startTime);
      case FAILURE:
        return buildFailureStepResponse(startTime, "Error getting EC2 instances", logCallback);
      default:
        return buildFailureStepResponse(startTime,
            format("Unhandled command execution status: %s received",
                awsListEC2InstancesTaskResponse.getCommandExecutionStatus()),
            logCallback);
    }
  }

  StepResponse publishAzureHosts(AzureHostsResponse azureHostsResponse, boolean skipInstances, Ambiance ambiance,
      NGLogCallback logCallback, InfrastructureOutcome infrastructureOutcome, ExecutionInfoKey executionInfoKey,
      long startTime) {
    Set<String> hostNames =
        azureHostsResponse.getHosts()
            .stream()
            .map(azureHost
                -> instanceOutcomeHelper.mapToHostNameBasedOnHostConnectionTypeAzure(infrastructureOutcome, azureHost))
            .collect(Collectors.toSet());
    if (isEmpty(hostNames)) {
      saveExecutionLogSafely(logCallback,
          color("No host(s) found for specified infrastructure or filter did not match any instance(s)", Red));
    } else {
      saveExecutionLogSafely(
          logCallback, color(format("Successfully fetched %s instance(s)", hostNames.size()), Green));
      saveExecutionLogSafely(logCallback, color(format("Fetched following instance(s) %s)", hostNames), Green));
    }

    Set<String> filteredHosts = stageExecutionHelper.saveAndExcludeHostsWithSameArtifactDeployedIfNeeded(
        ambiance, executionInfoKey, infrastructureOutcome, hostNames, ServiceSpecType.SSH, skipInstances, logCallback);
    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.OUTPUT,
        HostsOutput.builder().hosts(filteredHosts).build(), StepCategory.STAGE.name());

    InstancesOutcome instancesOutcome = instanceOutcomeHelper.saveAndGetInstancesOutcome(
        ambiance, infrastructureOutcome, azureHostsResponse, filteredHosts);
    return buildSuccessStepResponse(
        ambiance, infrastructureOutcome, logCallback, executionInfoKey, startTime, instancesOutcome);
  }

  StepResponse publishAwsHosts(AwsListEC2InstancesTaskResponse awsListEC2InstancesTaskResponse, boolean skipInstances,
      Ambiance ambiance, NGLogCallback logCallback, InfrastructureOutcome infrastructureOutcome,
      ExecutionInfoKey executionInfoKey, long startTime) {
    Set<String> hostNames = awsListEC2InstancesTaskResponse.getInstances()
                                .stream()
                                .map(awsEC2Instance
                                    -> instanceOutcomeHelper.mapToHostNameBasedOnHostConnectionTypeAWS(
                                        infrastructureOutcome, awsEC2Instance))
                                .collect(Collectors.toSet());
    if (isEmpty(hostNames)) {
      saveExecutionLogSafely(logCallback,
          color("No host(s) found for specified infrastructure or filter did not match any instance(s)", Red));
    } else {
      saveExecutionLogSafely(
          logCallback, color(format("Successfully fetched %s instance(s)", hostNames.size()), Green));
      saveExecutionLogSafely(logCallback, color(format("Fetched following instance(s) %s)", hostNames), Green));
    }

    Set<String> filteredHosts = stageExecutionHelper.saveAndExcludeHostsWithSameArtifactDeployedIfNeeded(
        ambiance, executionInfoKey, infrastructureOutcome, hostNames, ServiceSpecType.SSH, skipInstances, logCallback);
    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.OUTPUT,
        HostsOutput.builder().hosts(filteredHosts).build(), StepCategory.STAGE.name());

    InstancesOutcome instancesOutcome = instanceOutcomeHelper.saveAndGetInstancesOutcome(
        ambiance, infrastructureOutcome, awsListEC2InstancesTaskResponse, filteredHosts);
    return buildSuccessStepResponse(
        ambiance, infrastructureOutcome, logCallback, executionInfoKey, startTime, instancesOutcome);
  }

  StepResponse handleTaskException(long startTime, Exception e, NGLogCallback logCallback) throws Exception {
    if (ExceptionUtils.cause(TaskNGDataException.class, e) != null) {
      throw e;
    }

    return buildFailureStepResponse(
        startTime, HarnessStringUtils.emptyIfNull(ExceptionUtils.getMessage(e)), logCallback);
  }

  StepResponse buildSuccessStepResponse(Ambiance ambiance, InfrastructureOutcome infrastructureOutcome,
      NGLogCallback logCallback, ExecutionInfoKey executionInfoKey, long startTime, InstancesOutcome instancesOutcome) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(
          color("Completed infrastructure step", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    }

    StepResponseBuilder stepResponseBuilder = StepResponse.builder().status(Status.SUCCEEDED);
    String infrastructureKind = infrastructureOutcome.getKind();
    stageExecutionHelper.saveStageExecutionInfo(ambiance, executionInfoKey, infrastructureKind);
    stageExecutionHelper.addRollbackArtifactToStageOutcomeIfPresent(
        ambiance, stepResponseBuilder, executionInfoKey, infrastructureKind);

    return stepResponseBuilder
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .outcome(infrastructureOutcome)
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                         .build())
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .outcome(instancesOutcome)
                         .name(OutcomeExpressionConstants.INSTANCES)
                         .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                         .build())
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName("Execute")
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))

        .build();
  }

  protected StepResponse buildFailureStepResponse(long startTime, String message, NGLogCallback logCallback) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(
          color("Infrastructure step failed", Red), LogLevel.INFO, CommandExecutionStatus.FAILURE);
    }

    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(message)
                                  .build();

    return StepResponse.builder()
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().addFailureData(failureData).build())
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName("Execute")
                                                        .setStatus(UnitStatus.FAILURE)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))

        .build();
  }

  private TaskRequestData getTaskRequest(
      Ambiance ambiance, ServiceStepOutcome serviceOutcome, InfrastructureOutcome infrastructureOutcome) {
    if (ServiceDefinitionType.SSH.name()
            .toLowerCase(Locale.ROOT)
            .equals(serviceOutcome.getType().toLowerCase(Locale.ROOT))) {
      return buildSshTaskRequest(ambiance, infrastructureOutcome);
    } else if (ServiceDefinitionType.WINRM.name()
                   .toLowerCase(Locale.ROOT)
                   .equals(serviceOutcome.getType().toLowerCase(Locale.ROOT))) {
      return buildWinRmTaskRequest(ambiance, infrastructureOutcome);
    }
    throw new UnsupportedOperationException(
        format("Service type %s not supported for following infrastructure step", serviceOutcome.getType()));
  }

  TaskRequestData buildSshTaskRequest(Ambiance ambiance, InfrastructureOutcome infrastructureOutcome) {
    SshInfraDelegateConfig sshInfraDelegateConfig =
        publishSshInfraDelegateConfigOutput(infrastructureOutcome, ambiance);

    switch (infrastructureOutcome.getKind()) {
      case SSH_WINRM_AZURE:
        return buildAzureTaskRequest(ambiance, (AzureInfraDelegateConfig) sshInfraDelegateConfig);
      case SSH_WINRM_AWS:
        return buildAwsTaskRequest(ambiance, (AwsInfraDelegateConfig) sshInfraDelegateConfig);
      default:
        throw new UnsupportedOperationException(
            format("Specified infrastructure: %s is not supported for following infrastructure step",
                infrastructureOutcome.getKind()));
    }
  }

  TaskRequestData buildWinRmTaskRequest(Ambiance ambiance, InfrastructureOutcome infrastructureOutcome) {
    WinRmInfraDelegateConfig winRmInfraDelegateConfig =
        publishWinRmInfraDelegateConfigOutput(infrastructureOutcome, ambiance);

    switch (infrastructureOutcome.getKind()) {
      case SSH_WINRM_AZURE:
        return buildAzureTaskRequest(ambiance, (AzureInfraDelegateConfig) winRmInfraDelegateConfig);
      case SSH_WINRM_AWS:
        return buildAwsTaskRequest(ambiance, (AwsInfraDelegateConfig) winRmInfraDelegateConfig);
      default:
        throw new UnsupportedOperationException(
            format("Specified infrastructure: %s is not supported for following infrastructure step",
                infrastructureOutcome.getKind()));
    }
  }

  TaskRequestData buildAzureTaskRequest(Ambiance ambiance, AzureInfraDelegateConfig azureInfraDelegateConfig) {
    Map<AzureAdditionalParams, String> additionalParams = new HashMap<>();
    additionalParams.put(AzureAdditionalParams.SUBSCRIPTION_ID, azureInfraDelegateConfig.getSubscriptionId());
    additionalParams.put(AzureAdditionalParams.RESOURCE_GROUP, azureInfraDelegateConfig.getResourceGroup());
    additionalParams.put(AzureAdditionalParams.OS_TYPE, azureInfraDelegateConfig.getOsType());
    additionalParams.put(AzureAdditionalParams.HOST_CONNECTION_TYPE, azureInfraDelegateConfig.getHostConnectionType());

    Map<String, Object> params = new HashMap<>();
    params.put("tags", azureInfraDelegateConfig.getTags());
    AzureTaskParams azureTaskParamsTaskParams =
        AzureTaskParams.builder()
            .azureTaskType(AzureTaskType.LIST_HOSTS)
            .azureConnector(azureInfraDelegateConfig.getAzureConnectorDTO())
            .encryptionDetails(azureInfraDelegateConfig.getConnectorEncryptionDataDetails())
            .delegateSelectors(azureInfraDelegateConfig.getAzureConnectorDTO().getDelegateSelectors())
            .additionalParams(additionalParams)
            .params(params)
            .build();

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(TaskType.NG_AZURE_TASK.name())
            .timeout(StepUtils.getTimeoutMillis(ParameterField.createValueField("10m"), DEFAULT_TIMEOUT))
            .parameters(new Object[] {azureTaskParamsTaskParams})
            .expressionFunctorToken((int) ambiance.getExpressionFunctorToken())
            .build();

    List<TaskSelectorYaml> taskSelectorYamlList =
        emptyIfNull(azureInfraDelegateConfig.getAzureConnectorDTO().getDelegateSelectors())
            .stream()
            .map(TaskSelectorYaml::new)
            .collect(Collectors.toList());

    TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Collections.singletonList("Execute"), taskData.getTaskType(),
        TaskSelectorYaml.toTaskSelector(emptyIfNull(taskSelectorYamlList)), stepHelper.getEnvironmentType(ambiance));
    return new TaskRequestData(taskRequest, taskData, taskSelectorYamlList);
  }

  TaskRequestData buildAwsTaskRequest(Ambiance ambiance, AwsInfraDelegateConfig awsInfraDelegateConfig) {
    boolean isWinRm = awsInfraDelegateConfig instanceof AwsWinrmInfraDelegateConfig;
    final AwsTaskParams awsTaskParams;

    if (isEmpty(awsInfraDelegateConfig.getAutoScalingGroupName())) {
      awsTaskParams = AwsListEC2InstancesTaskParamsRequest.builder()
                          .awsConnector(awsInfraDelegateConfig.getAwsConnectorDTO())
                          .awsTaskType(AwsTaskType.LIST_EC2_INSTANCES)
                          .encryptionDetails(awsInfraDelegateConfig.getConnectorEncryptionDataDetails())
                          .region(awsInfraDelegateConfig.getRegion())
                          .vpcIds(awsInfraDelegateConfig.getVpcIds())
                          .tags(awsInfraDelegateConfig.getTags())
                          .winRm(isWinRm)
                          .build();
    } else {
      awsTaskParams = AwsListASGInstancesTaskParamsRequest.builder()
                          .awsConnector(awsInfraDelegateConfig.getAwsConnectorDTO())
                          .awsTaskType(AwsTaskType.LIST_ASG_INSTANCES)
                          .encryptionDetails(awsInfraDelegateConfig.getConnectorEncryptionDataDetails())
                          .region(awsInfraDelegateConfig.getRegion())
                          .autoScalingGroupName(awsInfraDelegateConfig.getAutoScalingGroupName())
                          .build();
    }

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(TaskType.NG_AWS_TASK.name())
            .timeout(StepUtils.getTimeoutMillis(ParameterField.createValueField("10m"), DEFAULT_TIMEOUT))
            .parameters(new Object[] {awsTaskParams})
            .expressionFunctorToken((int) ambiance.getExpressionFunctorToken())
            .build();

    List<TaskSelectorYaml> taskSelectorYamlList = awsInfraDelegateConfig.getAwsConnectorDTO()
                                                      .getDelegateSelectors()
                                                      .stream()
                                                      .map(delegateSelector -> new TaskSelectorYaml(delegateSelector))
                                                      .collect(Collectors.toList());

    TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Collections.singletonList("Execute"), taskData.getTaskType(),
        TaskSelectorYaml.toTaskSelector(emptyIfNull(taskSelectorYamlList)), stepHelper.getEnvironmentType(ambiance));

    return new TaskRequestData(taskRequest, taskData, taskSelectorYamlList);
  }

  SshInfraDelegateConfig publishSshInfraDelegateConfigOutput(
      InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
    SshInfraDelegateConfig sshInfraDelegateConfig =
        cdStepHelper.getSshInfraDelegateConfig(infrastructureOutcome, ambiance);

    SshInfraDelegateConfigOutput sshInfraDelegateConfigOutput =
        SshInfraDelegateConfigOutput.builder().sshInfraDelegateConfig(sshInfraDelegateConfig).build();
    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME,
        sshInfraDelegateConfigOutput, StepCategory.STAGE.name());
    return sshInfraDelegateConfig;
  }

  WinRmInfraDelegateConfig publishWinRmInfraDelegateConfigOutput(
      InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
    WinRmInfraDelegateConfig winRmInfraDelegateConfig =
        cdStepHelper.getWinRmInfraDelegateConfig(infrastructureOutcome, ambiance);

    WinRmInfraDelegateConfigOutput winRmInfraDelegateConfigOutput =
        WinRmInfraDelegateConfigOutput.builder().winRmInfraDelegateConfig(winRmInfraDelegateConfig).build();
    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.WINRM_INFRA_DELEGATE_CONFIG_OUTPUT_NAME,
        winRmInfraDelegateConfigOutput, StepCategory.STAGE.name());
    return winRmInfraDelegateConfig;
  }

  void validateInfrastructure(Infrastructure infrastructure) {
    if (infrastructure == null) {
      throw new InvalidRequestException("Infrastructure definition can't be null or empty");
    }
    switch (infrastructure.getKind()) {
      case SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructure sshWinRmAzureInfrastructure = (SshWinRmAzureInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(sshWinRmAzureInfrastructure.getConnectorRef(),
            sshWinRmAzureInfrastructure.getSubscriptionId(), sshWinRmAzureInfrastructure.getResourceGroup(),
            sshWinRmAzureInfrastructure.getCredentialsRef());
        break;

      case SSH_WINRM_AWS:
        SshWinRmAwsInfrastructure sshWinRmAwsInfrastructure = (SshWinRmAwsInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(sshWinRmAwsInfrastructure.getConnectorRef(),
            sshWinRmAwsInfrastructure.getCredentialsRef(), sshWinRmAwsInfrastructure.getRegion(),
            sshWinRmAwsInfrastructure.getHostConnectionType());
        break;
      default:
        throw new UnsupportedOperationException(
            "Specified infrastructure kind is not supported in infrastructure step");
    }
  }

  @VisibleForTesting
  void validateConnector(Infrastructure infrastructure, Ambiance ambiance, NGLogCallback logCallback) {
    if (infrastructure == null) {
      return;
    }

    if (InfrastructureKind.PDC.equals(infrastructure.getKind())
        && ParameterField.isNull(infrastructure.getConnectorReference())) {
      return;
    }

    List<ConnectorInfoDTO> connectorInfo = infrastructureStepHelper.validateAndGetConnectors(
        infrastructure.getConnectorReferences(), ambiance, logCallback);

    if (InfrastructureKind.KUBERNETES_GCP.equals(infrastructure.getKind())) {
      if (!(connectorInfo.get(0).getConnectorConfig() instanceof GcpConnectorDTO)) {
        throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
            connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
            ConnectorType.GCP.name()));
      }
    }

    if (InfrastructureKind.SERVERLESS_AWS_LAMBDA.equals(infrastructure.getKind())) {
      if (!(connectorInfo.get(0).getConnectorConfig() instanceof AwsConnectorDTO)) {
        throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
            connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
            ConnectorType.AWS.name()));
      }
    }

    if (InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS.equals(infrastructure.getKind())) {
      if (!(connectorInfo.get(0).getConnectorConfig() instanceof GcpConnectorDTO)) {
        throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
            connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
            ConnectorType.GCP.name()));
      }
    }

    if (InfrastructureKind.KUBERNETES_AZURE.equals(infrastructure.getKind())
        && !(connectorInfo.get(0).getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.AZURE.name()));
    }

    if (InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructure.getKind())
        && !(connectorInfo.get(0).getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.AZURE.name()));
    }

    if (InfrastructureKind.AZURE_WEB_APP.equals(infrastructure.getKind())
        && !(connectorInfo.get(0).getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.AZURE.name()));
    }

    if (InfrastructureKind.ELASTIGROUP.equals(infrastructure.getKind())
        && !(connectorInfo.get(0).getConnectorConfig() instanceof SpotConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.SPOT.name()));
    }

    if (InfrastructureKind.ASG.equals(infrastructure.getKind())
        && !(connectorInfo.get(0).getConnectorConfig() instanceof AwsConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.AWS.name()));
    }

    if (InfrastructureKind.ECS.equals(infrastructure.getKind())) {
      if (!(connectorInfo.get(0).getConnectorConfig() instanceof AwsConnectorDTO)) {
        throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
            connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
            ConnectorType.AWS.name()));
      }
    }

    if (InfrastructureKind.AWS_SAM.equals(infrastructure.getKind())) {
      if (!(connectorInfo.get(0).getConnectorConfig() instanceof AwsConnectorDTO)) {
        throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
            connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
            ConnectorType.AWS.name()));
      }
    }

    if (InfrastructureKind.AWS_LAMBDA.equals(infrastructure.getKind())
        && !(connectorInfo.get(0).getConnectorConfig() instanceof AwsConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.AWS.name()));
    }

    if (InfrastructureKind.KUBERNETES_AWS.equals(infrastructure.getKind())) {
      if (!(connectorInfo.get(0).getConnectorConfig() instanceof AwsConnectorDTO)) {
        throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
            connectorInfo.get(0).getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
            ConnectorType.AWS.name()));
      }
    }

    saveExecutionLog(logCallback, color("Connector validated", Green));
  }

  void validateInfrastructure(Infrastructure infrastructure, Ambiance ambiance, NGLogCallback logCallback) {
    String k8sNamespaceLogLine = "Kubernetes Namespace: %s";

    if (infrastructure == null) {
      throw new InvalidRequestException("Infrastructure definition can't be null or empty");
    }
    if (infrastructure instanceof InfrastructureDetailsAbstract) {
      saveExecutionLog(logCallback,
          "Infrastructure Name: " + ((InfrastructureDetailsAbstract) infrastructure).getInfraName()
              + " , Identifier: " + ((InfrastructureDetailsAbstract) infrastructure).getInfraIdentifier());
    }

    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8sDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(
            k8sDirectInfrastructure.getConnectorRef(), k8sDirectInfrastructure.getNamespace());

        if (k8sDirectInfrastructure.getNamespace() != null
            && isNotEmpty(k8sDirectInfrastructure.getNamespace().getValue())) {
          saveExecutionLog(logCallback,
              color(format(k8sNamespaceLogLine, k8sDirectInfrastructure.getNamespace().getValue()), Yellow));
        }
        break;

      case InfrastructureKind.CUSTOM_DEPLOYMENT:
        CustomDeploymentInfrastructure customDeploymentInfrastructure = (CustomDeploymentInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(customDeploymentInfrastructure.getConnectorReference(),
            ParameterField.createValueField(customDeploymentInfrastructure.getCustomDeploymentRef().getTemplateRef()),
            ParameterField.createValueField(customDeploymentInfrastructure.getCustomDeploymentRef().getVersionLabel()));
        customDeploymentInfrastructureHelper.validateInfra(ambiance, customDeploymentInfrastructure);
        break;

      case InfrastructureKind.KUBERNETES_GCP:
        K8sGcpInfrastructure k8sGcpInfrastructure = (K8sGcpInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(k8sGcpInfrastructure.getConnectorRef(),
            k8sGcpInfrastructure.getNamespace(), k8sGcpInfrastructure.getCluster());

        if (k8sGcpInfrastructure.getNamespace() != null && isNotEmpty(k8sGcpInfrastructure.getNamespace().getValue())) {
          saveExecutionLog(
              logCallback, color(format(k8sNamespaceLogLine, k8sGcpInfrastructure.getNamespace().getValue()), Yellow));
        }
        break;
      case InfrastructureKind.SERVERLESS_AWS_LAMBDA:
        ServerlessAwsLambdaInfrastructure serverlessAwsLambdaInfrastructure =
            (ServerlessAwsLambdaInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(serverlessAwsLambdaInfrastructure.getConnectorRef(),
            serverlessAwsLambdaInfrastructure.getRegion(), serverlessAwsLambdaInfrastructure.getStage());
        break;

      case InfrastructureKind.ELASTIGROUP:
        ElastigroupInfrastructure elastigroupInfrastructure = (ElastigroupInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(elastigroupInfrastructure.getConnectorRef());
        break;

      case InfrastructureKind.ASG:
        AsgInfrastructure asgInfrastructure = (AsgInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(asgInfrastructure.getConnectorRef(), asgInfrastructure.getRegion());
        break;

      case InfrastructureKind.KUBERNETES_AZURE:
        K8sAzureInfrastructure k8sAzureInfrastructure = (K8sAzureInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(k8sAzureInfrastructure.getConnectorRef(),
            k8sAzureInfrastructure.getNamespace(), k8sAzureInfrastructure.getCluster(),
            k8sAzureInfrastructure.getSubscriptionId(), k8sAzureInfrastructure.getResourceGroup());

        if (k8sAzureInfrastructure.getNamespace() != null
            && isNotEmpty(k8sAzureInfrastructure.getNamespace().getValue())) {
          saveExecutionLog(logCallback,
              color(format(k8sNamespaceLogLine, k8sAzureInfrastructure.getNamespace().getValue()), Yellow));
        }
        break;

      case InfrastructureKind.SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructure sshWinRmAzureInfrastructure = (SshWinRmAzureInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(sshWinRmAzureInfrastructure.getConnectorRef(),
            sshWinRmAzureInfrastructure.getSubscriptionId(), sshWinRmAzureInfrastructure.getResourceGroup(),
            sshWinRmAzureInfrastructure.getCredentialsRef());
        break;

      case InfrastructureKind.PDC:
        PdcInfrastructure pdcInfrastructure = (PdcInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(pdcInfrastructure.getCredentialsRef());
        infrastructureStepHelper.requireOne(pdcInfrastructure.getHosts(), pdcInfrastructure.getConnectorRef());
        break;
      case InfrastructureKind.SSH_WINRM_AWS:
        SshWinRmAwsInfrastructure sshWinRmAwsInfrastructure = (SshWinRmAwsInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(sshWinRmAwsInfrastructure.getConnectorRef(),
            sshWinRmAwsInfrastructure.getCredentialsRef(), sshWinRmAwsInfrastructure.getRegion(),
            sshWinRmAwsInfrastructure.getHostConnectionType());
        break;

      case InfrastructureKind.AZURE_WEB_APP:
        AzureWebAppInfrastructure azureWebAppInfrastructure = (AzureWebAppInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(azureWebAppInfrastructure.getConnectorRef(),
            azureWebAppInfrastructure.getSubscriptionId(), azureWebAppInfrastructure.getResourceGroup());
        break;

      case InfrastructureKind.ECS:
        EcsInfrastructure ecsInfrastructure = (EcsInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(
            ecsInfrastructure.getConnectorRef(), ecsInfrastructure.getRegion(), ecsInfrastructure.getCluster());
        break;
      case InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS:
        GoogleFunctionsInfrastructure googleFunctionsInfrastructure = (GoogleFunctionsInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(googleFunctionsInfrastructure.getConnectorRef(),
            googleFunctionsInfrastructure.getRegion(), googleFunctionsInfrastructure.getProject());
        break;
      case InfrastructureKind.TAS:
        TanzuApplicationServiceInfrastructure tasInfrastructure =
            (TanzuApplicationServiceInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(
            tasInfrastructure.getConnectorRef(), tasInfrastructure.getOrganization(), tasInfrastructure.getSpace());
        break;
      case InfrastructureKind.AWS_SAM:
        AwsSamInfrastructure awsSamInfrastructure = (AwsSamInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(
            awsSamInfrastructure.getConnectorRef(), awsSamInfrastructure.getRegion());
        break;
      case InfrastructureKind.AWS_LAMBDA:
        AwsLambdaInfrastructure awsLambdaInfrastructure = (AwsLambdaInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(
            awsLambdaInfrastructure.getConnectorRef(), awsLambdaInfrastructure.getRegion());
        break;
      case InfrastructureKind.KUBERNETES_AWS:
        K8sAwsInfrastructure k8sAwsInfrastructure = (K8sAwsInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(k8sAwsInfrastructure.getConnectorRef(),
            k8sAwsInfrastructure.getNamespace(), k8sAwsInfrastructure.getCluster());

        if (k8sAwsInfrastructure.getNamespace() != null && isNotEmpty(k8sAwsInfrastructure.getNamespace().getValue())) {
          saveExecutionLog(
              logCallback, color(format(k8sNamespaceLogLine, k8sAwsInfrastructure.getNamespace().getValue()), Yellow));
        }
        break;
      case InfrastructureKind.KUBERNETES_RANCHER:
        K8sRancherInfrastructure rancherInfrastructure = (K8sRancherInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(rancherInfrastructure.getConnectorRef(),
            rancherInfrastructure.getNamespace(), rancherInfrastructure.getCluster());
        if (ParameterField.isNotNull(rancherInfrastructure.getNamespace())
            && isNotEmpty(rancherInfrastructure.getNamespace().getValue())) {
          saveExecutionLog(
              logCallback, color(format(k8sNamespaceLogLine, rancherInfrastructure.getNamespace().getValue()), Yellow));
        }
        break;
      default:
        throw new InvalidArgumentsException(format("Unknown Infrastructure Kind : [%s]", infrastructure.getKind()));
    }
  }

  void saveExecutionLog(NGLogCallback logCallback, String line) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line);
    }
  }

  void saveExecutionLog(NGLogCallback logCallback, String line, LogLevel logLevel, CommandExecutionStatus status) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line, logLevel, status);
    }
  }
}
