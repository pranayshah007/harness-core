package io.harness.cdng.infra.steps;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.ng.core.infrastructure.InfrastructureKind.SSH_WINRM_AWS;
import static io.harness.ng.core.infrastructure.InfrastructureKind.SSH_WINRM_AZURE;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.helper.ExecutionInfoKeyMapper;
import io.harness.cdng.execution.helper.StageExecutionHelper;
import io.harness.cdng.infra.InfrastructureMapper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.azure.response.AzureHostResponse;
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
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.steps.shellscript.HostsOutput;
import io.harness.steps.shellscript.SshInfraDelegateConfigOutput;
import io.harness.steps.shellscript.WinRmInfraDelegateConfigOutput;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;
import software.wings.service.impl.aws.model.AwsEC2Instance;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class AbstractInfrastructureTaskExecutableStep {
  private static final String DEFAULT_TIMEOUT = "10m";
  private static final long DEFAULT_START_TIME_INTERVAL = 10 * 60 * 1000L;

  @Inject private InfrastructureStepHelper infrastructureStepHelper;
  @Inject private OutcomeService outcomeService;
  @Inject private CDStepHelper cdStepHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private StageExecutionHelper stageExecutionHelper;

  @Data
  @AllArgsConstructor
  static class OutcomeSet {
    private ServiceStepOutcome serviceStepOutcome;
    private EnvironmentOutcome environmentOutcome;
  }

  protected OutcomeSet fetchRequiredOutcomes(Ambiance ambiance) {
    EnvironmentOutcome environmentOutcome = (EnvironmentOutcome) executionSweepingOutputService.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT));
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    return new OutcomeSet(serviceOutcome, environmentOutcome);
  }

  protected Optional<TaskRequest> obtainTaskInternal(
      Ambiance ambiance, Infrastructure infrastructureSpec, StepInputPackage inputPackage) {
    NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance, "Execute");
    infrastructureStepHelper.saveExecutionLog(logCallback, "Starting infrastructure step...");

    validateConnector(infrastructureSpec, ambiance);
    infrastructureStepHelper.saveExecutionLog(logCallback, "Fetching environment information...");
    validateInfrastructure(infrastructureSpec);

    final OutcomeSet outcomeSet = fetchRequiredOutcomes(ambiance);
    EnvironmentOutcome environmentOutcome = outcomeSet.getEnvironmentOutcome();
    ServiceStepOutcome serviceOutcome = outcomeSet.getServiceStepOutcome();
    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(infrastructureSpec, environmentOutcome, serviceOutcome);

    if (environmentOutcome != null) {
      if (EmptyPredicate.isNotEmpty(environmentOutcome.getName())) {
        infrastructureStepHelper.saveExecutionLog(
            logCallback, color(format("Environment Name: %s", environmentOutcome.getName()), Yellow));
      }

      if (environmentOutcome.getType() != null && EmptyPredicate.isNotEmpty(environmentOutcome.getType().name())) {
        infrastructureStepHelper.saveExecutionLog(
            logCallback, color(format("Environment Type: %s", environmentOutcome.getType().name()), Yellow));
      }
    }

    if (infrastructureOutcome != null && EmptyPredicate.isNotEmpty(infrastructureOutcome.getKind())) {
      infrastructureStepHelper.saveExecutionLog(
          logCallback, color(format("Infrastructure Definition Type: %s", infrastructureOutcome.getKind()), Yellow));
    }

    infrastructureStepHelper.saveExecutionLog(logCallback, color("Environment information fetched", Green));
    infrastructureStepHelper.saveExecutionLog(
        logCallback, format("Fetching instances from %s infrastructure", infrastructureOutcome.getKind()));

    return Optional.of(getTaskRequest(serviceOutcome, infrastructureOutcome, ambiance));
  }

  protected StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      InfrastructureOutcome infrastructureOutcome, ThrowingSupplier<DelegateResponseData> responseDataSupplier)
      throws Exception {
    log.info("Handling Task Result With Security Context for the Infrastructure Step");
    long startTime = System.currentTimeMillis() - DEFAULT_START_TIME_INTERVAL;
    EnvironmentOutcome environmentOutcome = (EnvironmentOutcome) executionSweepingOutputService.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT));
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    ExecutionInfoKey executionInfoKey = ExecutionInfoKeyMapper.getExecutionInfoKey(
        ambiance, infrastructureOutcome.getKind(), environmentOutcome, serviceOutcome, infrastructureOutcome);

    NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance, "Execute");
    DelegateResponseData responseData;
    try {
      responseData = responseDataSupplier.get();
    } catch (Exception ex) {
      log.error("Error while processing Infrastructure Step response: {}", ex.getMessage(), ex);
      return handleTaskException(startTime, ex, logCallback);
    }

    if (responseData instanceof AzureHostsResponse) {
      return handleAzureHostResponse(
          responseData, ambiance, logCallback, infrastructureOutcome, executionInfoKey, startTime);
    } else if (responseData instanceof AwsListEC2InstancesTaskResponse) {
      return handleAwsListEC2InstancesTaskResponse(
          responseData, ambiance, logCallback, infrastructureOutcome, executionInfoKey, startTime);
    }

    return buildFailureStepResponse(
        startTime, format("Unhandled response data: %s received", responseData.getClass()), logCallback);
  }

  protected void publishInfraOutput(Ambiance ambiance, Infrastructure stepParameters) {
    final NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance, true, "Execute");

    EnvironmentOutcome environmentOutcome = (EnvironmentOutcome) executionSweepingOutputService.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT));
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(stepParameters, environmentOutcome, serviceOutcome);
    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.OUTPUT, infrastructureOutcome, "");

    if (environmentOutcome != null) {
      if (EmptyPredicate.isNotEmpty(environmentOutcome.getName())) {
        infrastructureStepHelper.saveExecutionLog(
            logCallback, color(format("Environment Name: %s", environmentOutcome.getName()), Yellow));
      }

      if (environmentOutcome.getType() != null && EmptyPredicate.isNotEmpty(environmentOutcome.getType().name())) {
        infrastructureStepHelper.saveExecutionLog(
            logCallback, color(format("Environment Type: %s", environmentOutcome.getType().name()), Yellow));
      }
    }

    if (infrastructureOutcome != null && EmptyPredicate.isNotEmpty(infrastructureOutcome.getKind())) {
      infrastructureStepHelper.saveExecutionLog(
          logCallback, color(format("Infrastructure Definition Type: %s", infrastructureOutcome.getKind()), Yellow));
    }

    infrastructureStepHelper.saveExecutionLog(logCallback, color("Environment information fetched", Green));
  }

  protected InfrastructureOutcome fetchInfraOutputOrThrow(Ambiance ambiance) {
    OptionalSweepingOutput output = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.OUTPUT));
    if (!output.isFound()) {
      throw new InvalidRequestException("Infrastructure could not be resolved");
    }
    return (InfrastructureOutcome) output.getOutput();
  }
  StepResponse handleAzureHostResponse(DelegateResponseData responseData, Ambiance ambiance, NGLogCallback logCallback,
      InfrastructureOutcome infrastructureOutcome, ExecutionInfoKey executionInfoKey, long startTime) {
    AzureHostsResponse azureHostsResponse = (AzureHostsResponse) responseData;
    switch (azureHostsResponse.getCommandExecutionStatus()) {
      case SUCCESS:
        if (EmptyPredicate.isEmpty(azureHostsResponse.getHosts())) {
          return buildFailureStepResponse(
              startTime, "No instance(s) were found for specified infrastructure", logCallback);
        }
        return publishAzureHosts(
            azureHostsResponse, ambiance, logCallback, infrastructureOutcome, executionInfoKey, startTime);
      case FAILURE:
        return buildFailureStepResponse(
            startTime, HarnessStringUtils.emptyIfNull(azureHostsResponse.getErrorSummary()), logCallback);
      default:
        return buildFailureStepResponse(startTime,
            format("Unhandled command execution status: %s received", azureHostsResponse.getCommandExecutionStatus()),
            logCallback);
    }
  }

  StepResponse handleAwsListEC2InstancesTaskResponse(DelegateResponseData responseData, Ambiance ambiance,
      NGLogCallback logCallback, InfrastructureOutcome infrastructureOutcome, ExecutionInfoKey executionInfoKey,
      long startTime) {
    AwsListEC2InstancesTaskResponse awsListEC2InstancesTaskResponse = (AwsListEC2InstancesTaskResponse) responseData;
    switch (awsListEC2InstancesTaskResponse.getCommandExecutionStatus()) {
      case SUCCESS:
        if (EmptyPredicate.isEmpty(awsListEC2InstancesTaskResponse.getInstances())) {
          return buildFailureStepResponse(
              startTime, "No instance(s) were found for specified infrastructure", logCallback);
        }
        return publishAwsHosts(
            awsListEC2InstancesTaskResponse, ambiance, logCallback, infrastructureOutcome, executionInfoKey, startTime);
      case FAILURE:
        return buildFailureStepResponse(startTime, "Error getting EC2 instances", logCallback);
      default:
        return buildFailureStepResponse(startTime,
            format("Unhandled command execution status: %s received",
                awsListEC2InstancesTaskResponse.getCommandExecutionStatus()),
            logCallback);
    }
  }

  StepResponse publishAzureHosts(AzureHostsResponse azureHostsResponse, Ambiance ambiance, NGLogCallback logCallback,
      InfrastructureOutcome infrastructureOutcome, ExecutionInfoKey executionInfoKey, long startTime) {
    List<String> hostNames =
        azureHostsResponse.getHosts().stream().map(AzureHostResponse::getHostName).collect(Collectors.toList());
    infrastructureStepHelper.saveExecutionLog(
        logCallback, color(format("Successfully fetched %s instance(s)", hostNames.size()), Green));
    infrastructureStepHelper.saveExecutionLog(
        logCallback, color(format("Fetched following instance(s) %s)", hostNames), Green));

    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.OUTPUT,
        HostsOutput.builder().hosts(hostNames).build(), StepCategory.STAGE.name());

    return buildSuccessStepResponse(ambiance, infrastructureOutcome, logCallback, executionInfoKey, startTime);
  }

  StepResponse publishAwsHosts(AwsListEC2InstancesTaskResponse awsListEC2InstancesTaskResponse, Ambiance ambiance,
      NGLogCallback logCallback, InfrastructureOutcome infrastructureOutcome, ExecutionInfoKey executionInfoKey,
      long startTime) {
    List<String> hostNames = awsListEC2InstancesTaskResponse.getInstances()
                                 .stream()
                                 .map(AwsEC2Instance::getPublicDnsName)
                                 .collect(Collectors.toList());
    infrastructureStepHelper.saveExecutionLog(
        logCallback, color(format("Successfully fetched %s instance(s)", hostNames.size()), Green));
    infrastructureStepHelper.saveExecutionLog(
        logCallback, color(format("Fetched following instance(s) %s)", hostNames), Green));

    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.OUTPUT,
        HostsOutput.builder().hosts(hostNames).build(), StepCategory.STAGE.name());

    return buildSuccessStepResponse(ambiance, infrastructureOutcome, logCallback, executionInfoKey, startTime);
  }

  StepResponse handleTaskException(long startTime, Exception e, NGLogCallback logCallback) throws Exception {
    if (ExceptionUtils.cause(TaskNGDataException.class, e) != null) {
      throw e;
    }

    return buildFailureStepResponse(
        startTime, HarnessStringUtils.emptyIfNull(ExceptionUtils.getMessage(e)), logCallback);
  }

  StepResponse buildSuccessStepResponse(Ambiance ambiance, InfrastructureOutcome infrastructureOutcome,
      NGLogCallback logCallback, ExecutionInfoKey executionInfoKey, long startTime) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(
          color("Completed infrastructure step", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    }

    StepResponseBuilder stepResponseBuilder = StepResponse.builder().status(Status.SUCCEEDED);
    String infrastructureKind = infrastructureOutcome.getKind();
    stageExecutionHelper.saveStageExecutionInfoAndPublishExecutionInfoKey(
        ambiance, executionInfoKey, infrastructureKind);
    stageExecutionHelper.addRollbackArtifactToStageOutcomeIfPresent(
        ambiance, stepResponseBuilder, executionInfoKey, infrastructureKind);

    return stepResponseBuilder
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .outcome(infrastructureOutcome)
                         .name(OutcomeExpressionConstants.OUTPUT)
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

  StepResponse buildFailureStepResponse(long startTime, String message, NGLogCallback logCallback) {
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

  private TaskRequest getTaskRequest(
      ServiceStepOutcome serviceOutcome, InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
    switch (serviceOutcome.getType()) {
      case ServiceSpecType.SSH:
        return buildSshTaskRequest(infrastructureOutcome, ambiance);
      case ServiceSpecType.WINRM:
        return buildWinRmTaskRequest(infrastructureOutcome, ambiance);
      default:
        throw new UnsupportedOperationException(
            format("Service type %s not supported for following infrastructure step", serviceOutcome.getType()));
    }
  }

  TaskRequest buildSshTaskRequest(InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
    SshInfraDelegateConfig sshInfraDelegateConfig =
        publishSshInfraDelegateConfigOutput(infrastructureOutcome, ambiance);

    switch (infrastructureOutcome.getKind()) {
      case SSH_WINRM_AZURE:
        return buildAzureTaskRequest((AzureInfraDelegateConfig) sshInfraDelegateConfig, ambiance);
      case SSH_WINRM_AWS:
        return buildAwsTaskRequest((AwsInfraDelegateConfig) sshInfraDelegateConfig, ambiance);
      default:
        throw new UnsupportedOperationException(
            format("Specified infrastructure: %s is not supported for following infrastructure step",
                infrastructureOutcome.getKind()));
    }
  }

  TaskRequest buildWinRmTaskRequest(InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
    WinRmInfraDelegateConfig winRmInfraDelegateConfig =
        publishWinRmInfraDelegateConfigOutput(infrastructureOutcome, ambiance);

    switch (infrastructureOutcome.getKind()) {
      case SSH_WINRM_AZURE:
        return buildAzureTaskRequest((AzureInfraDelegateConfig) winRmInfraDelegateConfig, ambiance);
      case SSH_WINRM_AWS:
        return buildAwsTaskRequest((AwsInfraDelegateConfig) winRmInfraDelegateConfig, ambiance);
      default:
        throw new UnsupportedOperationException(
            format("Specified infrastructure: %s is not supported for following infrastructure step",
                infrastructureOutcome.getKind()));
    }
  }

  TaskRequest buildAzureTaskRequest(AzureInfraDelegateConfig azureInfraDelegateConfig, Ambiance ambiance) {
    Map<AzureAdditionalParams, String> additionalParams = new HashMap<>();
    additionalParams.put(AzureAdditionalParams.SUBSCRIPTION_ID, azureInfraDelegateConfig.getSubscriptionId());
    additionalParams.put(AzureAdditionalParams.RESOURCE_GROUP, azureInfraDelegateConfig.getResourceGroup());
    additionalParams.put(AzureAdditionalParams.OS_TYPE, azureInfraDelegateConfig.getOsType());

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
            .build();

    List<TaskSelectorYaml> taskSelectorYamlList = azureInfraDelegateConfig.getAzureConnectorDTO()
                                                      .getDelegateSelectors()
                                                      .stream()
                                                      .map(delegateSelector -> new TaskSelectorYaml(delegateSelector))
                                                      .collect(Collectors.toList());

    return StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer, Collections.singletonList("Execute"),
        taskData.getTaskType(), TaskSelectorYaml.toTaskSelector(emptyIfNull(taskSelectorYamlList)),
        stepHelper.getEnvironmentType(ambiance));
  }

  TaskRequest buildAwsTaskRequest(AwsInfraDelegateConfig awsInfraDelegateConfig, Ambiance ambiance) {
    boolean isWinRm = awsInfraDelegateConfig instanceof AwsWinrmInfraDelegateConfig;
    final AwsTaskParams awsTaskParams;

    if (EmptyPredicate.isEmpty(awsInfraDelegateConfig.getAutoScalingGroupName())) {
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
            .build();

    List<TaskSelectorYaml> taskSelectorYamlList = awsInfraDelegateConfig.getAwsConnectorDTO()
                                                      .getDelegateSelectors()
                                                      .stream()
                                                      .map(delegateSelector -> new TaskSelectorYaml(delegateSelector))
                                                      .collect(Collectors.toList());

    return StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer, Collections.singletonList("Execute"),
        taskData.getTaskType(), TaskSelectorYaml.toTaskSelector(emptyIfNull(taskSelectorYamlList)),
        stepHelper.getEnvironmentType(ambiance));
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
            sshWinRmAwsInfrastructure.getCredentialsRef(), sshWinRmAwsInfrastructure.getRegion());
        break;
      default:
        throw new UnsupportedOperationException(
            "Specified infrastructure kind is not supported in infrastructure step");
    }
  }

  @VisibleForTesting
  void validateConnector(Infrastructure infrastructure, Ambiance ambiance) {
    NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance);

    if (infrastructure == null) {
      return;
    }

    if (InfrastructureKind.PDC.equals(infrastructure.getKind())
        && ParameterField.isNull(infrastructure.getConnectorReference())) {
      return;
    }

    ConnectorInfoDTO connectorInfo =
        infrastructureStepHelper.validateAndGetConnector(infrastructure.getConnectorReference(), ambiance, logCallback);

    if (InfrastructureKind.KUBERNETES_GCP.equals(infrastructure.getKind())) {
      if (!(connectorInfo.getConnectorConfig() instanceof GcpConnectorDTO)) {
        throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
            connectorInfo.getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
            ConnectorType.GCP.name()));
      }
    }

    if (InfrastructureKind.SERVERLESS_AWS_LAMBDA.equals(infrastructure.getKind())) {
      if (!(connectorInfo.getConnectorConfig() instanceof AwsConnectorDTO)) {
        throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
            connectorInfo.getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
            ConnectorType.AWS.name()));
      }
    }

    if (InfrastructureKind.KUBERNETES_AZURE.equals(infrastructure.getKind())
        && !(connectorInfo.getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.AZURE.name()));
    }

    if (InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructure.getKind())
        && !(connectorInfo.getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.AZURE.name()));
    }

    if (InfrastructureKind.AZURE_WEB_APP.equals(infrastructure.getKind())
        && !(connectorInfo.getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.AZURE.name()));
    }

    saveExecutionLog(logCallback, color("Connector validated", Green));
  }

  void validateInfrastructure(Infrastructure infrastructure, Ambiance ambiance) {
    String k8sNamespaceLogLine = "Kubernetes Namespace: %s";

    NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance);

    if (infrastructure == null) {
      throw new InvalidRequestException("Infrastructure definition can't be null or empty");
    }
    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8SDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(
            k8SDirectInfrastructure.getConnectorRef(), k8SDirectInfrastructure.getNamespace());

        if (k8SDirectInfrastructure.getNamespace() != null
            && EmptyPredicate.isNotEmpty(k8SDirectInfrastructure.getNamespace().getValue())) {
          saveExecutionLog(logCallback,
              color(format(k8sNamespaceLogLine, k8SDirectInfrastructure.getNamespace().getValue()), Yellow));
        }
        break;

      case InfrastructureKind.KUBERNETES_GCP:
        K8sGcpInfrastructure k8sGcpInfrastructure = (K8sGcpInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(k8sGcpInfrastructure.getConnectorRef(),
            k8sGcpInfrastructure.getNamespace(), k8sGcpInfrastructure.getCluster());

        if (k8sGcpInfrastructure.getNamespace() != null
            && EmptyPredicate.isNotEmpty(k8sGcpInfrastructure.getNamespace().getValue())) {
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

      case InfrastructureKind.KUBERNETES_AZURE:
        K8sAzureInfrastructure k8sAzureInfrastructure = (K8sAzureInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(k8sAzureInfrastructure.getConnectorRef(),
            k8sAzureInfrastructure.getNamespace(), k8sAzureInfrastructure.getCluster(),
            k8sAzureInfrastructure.getSubscriptionId(), k8sAzureInfrastructure.getResourceGroup());

        if (k8sAzureInfrastructure.getNamespace() != null
            && EmptyPredicate.isNotEmpty(k8sAzureInfrastructure.getNamespace().getValue())) {
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
            sshWinRmAwsInfrastructure.getCredentialsRef(), sshWinRmAwsInfrastructure.getRegion());
        break;

      case InfrastructureKind.AZURE_WEB_APP:
        AzureWebAppInfrastructure azureWebAppInfrastructure = (AzureWebAppInfrastructure) infrastructure;
        infrastructureStepHelper.validateExpression(azureWebAppInfrastructure.getConnectorRef(),
            azureWebAppInfrastructure.getSubscriptionId(), azureWebAppInfrastructure.getResourceGroup());
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
}