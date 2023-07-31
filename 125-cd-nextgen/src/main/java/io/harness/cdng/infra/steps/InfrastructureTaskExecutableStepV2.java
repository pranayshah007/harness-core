/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.ELASTIGROUP_CONFIGURATION_OUTPUT;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.INFRA_TASK_EXECUTABLE_STEP_OUTPUT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.logging.LogCallbackUtils.saveExecutionLogSafely;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.StepDelegateInfo;
import io.harness.cdng.common.beans.StepDetailsDelegateInfo;
import io.harness.cdng.elastigroup.output.ElastigroupConfigurationOutput;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.helper.ExecutionInfoKeyMapper;
import io.harness.cdng.execution.helper.StageExecutionHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.InfrastructureValidator;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sRancherInfrastructureOutcome;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDetailsAbstract;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.ssh.output.HostsOutput;
import io.harness.cdng.ssh.output.SshInfraDelegateConfigOutput;
import io.harness.cdng.ssh.output.WinRmInfraDelegateConfigOutput;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.eraro.Level;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureYamlSchemaHelper;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.execution.invokers.StrategyHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.security.PmsSecurityContextEventGuard;
import io.harness.pms.yaml.ParameterField;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.steps.shellscript.K8sInfraDelegateConfigOutput;
import io.harness.tasks.ResponseData;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@OwnedBy(CDP)
public class InfrastructureTaskExecutableStepV2 extends AbstractInfrastructureTaskExecutableStep
    implements AsyncExecutableWithRbac<InfrastructureTaskExecutableStepV2Params> {
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.INFRASTRUCTURE_TASKSTEP_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  private static final String INFRASTRUCTURE_STEP = "Infrastructure Step";

  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private InfrastructureStepHelper infrastructureStepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private StageExecutionHelper stageExecutionHelper;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private InfrastructureValidator infrastructureValidator;
  @Inject private CDExpressionResolver resolver;
  @Inject private StrategyHelper strategyHelper;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Inject private InfrastructureYamlSchemaHelper infrastructureYamlSchemaHelper;

  @Override
  public Class<InfrastructureTaskExecutableStepV2Params> getStepParametersClass() {
    return InfrastructureTaskExecutableStepV2Params.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, InfrastructureTaskExecutableStepV2Params stepParameters) {
    // Not validating here. Instead, validation is done in obtainTaskWithRBAC method to avoid unnecessary db calls of
    // fetching infrastructure/sweeping outputs
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, InfrastructureTaskExecutableStepV2Params stepParameters, StepInputPackage inputPackage) {
    validateStepParameters(stepParameters);

    final InfrastructureConfig infrastructureConfig = fetchInfraConfigFromDBorThrow(ambiance, stepParameters);
    final Infrastructure infraSpec = infrastructureConfig.getInfrastructureDefinitionConfig().getSpec();
    boolean skipInstances = ParameterFieldHelper.getBooleanParameterFieldValue(stepParameters.getSkipInstances());

    validateResources(ambiance, infraSpec);
    setInfraIdentifierAndName(infraSpec, infrastructureConfig);
    resolver.updateExpressions(ambiance, infraSpec);

    final NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance, true, LOG_SUFFIX);
    // Create delegate task for infra if needed
    if (isTaskStep(infraSpec.getKind())) {
      final TaskRequestData taskRequest = obtainTaskInternal(ambiance, infraSpec, logCallback,
          !infrastructureConfig.getInfrastructureDefinitionConfig().isAllowSimultaneousDeployments(), skipInstances,
          infrastructureConfig.getInfrastructureDefinitionConfig().getTags());
      final DelegateTaskRequest delegateTaskRequest =
          cdStepHelper.mapTaskRequestToDelegateTaskRequest(taskRequest.getTaskRequest(), taskRequest.getTaskData(),
              CollectionUtils.emptyIfNull(taskRequest.getTaskSelectorYamls())
                  .stream()
                  .map(TaskSelectorYaml::getDelegateSelectors)
                  .collect(Collectors.toSet()));
      String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
      List<StepDelegateInfo> stepDelegateInfos =
          Collections.singletonList(StepDelegateInfo.builder().taskName("Infrastructure Task").taskId(taskId).build());
      sdkGraphVisualizationDataService.publishStepDetailInformation(ambiance,
          StepDetailsDelegateInfo.builder().stepDelegateInfos(stepDelegateInfos).build(), INFRASTRUCTURE_STEP);
      return AsyncExecutableResponse.newBuilder()
          .addCallbackIds(taskId)
          .addAllLogKeys(StepUtils.generateLogKeys(ambiance, List.of(LOG_SUFFIX)))
          .build();
    }

    // If delegate task is not needed, just validate the infra spec
    executeSync(ambiance, infrastructureConfig, logCallback, skipInstances);
    return AsyncExecutableResponse.newBuilder()
        .addAllLogKeys(StepUtils.generateLogKeys(ambiance, List.of(LOG_SUFFIX)))
        .build();
  }

  @Override
  public StepResponse handleAsyncResponse(Ambiance ambiance, InfrastructureTaskExecutableStepV2Params stepParameters,
      Map<String, ResponseData> responseDataMap) {
    StepResponse stepResponse;
    try (PmsSecurityContextEventGuard securityContextEventGuard = new PmsSecurityContextEventGuard(ambiance)) {
      stepResponse = handleAsyncResponseInternal(ambiance, responseDataMap);
    } catch (Exception ex) {
      stepResponse = prepareFailureResponse(ex);
    }
    infrastructureStepHelper.saveInfraExecutionDataToStageInfo(ambiance, stepResponse);
    return stepResponse;
  }

  StepResponse handleAsyncResponseInternal(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    final InfrastructureTaskExecutableStepSweepingOutput infraOutput = fetchInfraStepOutputOrThrow(ambiance);
    final NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance, LOG_SUFFIX);

    // handle response from delegate if task was created
    if (isTaskStep(infraOutput.getInfrastructureOutcome().getKind())) {
      final List<ErrorNotifyResponseData> failedResponses = responseDataMap.values()
                                                                .stream()
                                                                .filter(ErrorNotifyResponseData.class ::isInstance)
                                                                .map(ErrorNotifyResponseData.class ::cast)
                                                                .collect(Collectors.toList());

      if (isNotEmpty(failedResponses)) {
        log.error("Error notify response found for Infrastructure step " + failedResponses);
        return strategyHelper.handleException(failedResponses.get(0).getException());
      }

      Iterator<ResponseData> dataIterator = responseDataMap.values().iterator();
      if (!dataIterator.hasNext()) {
        throw new InvalidRequestException("No Delegate Response received. Failed to complete Infrastructure step.");
      }
      DelegateResponseData delegateResponseData = (DelegateResponseData) dataIterator.next();
      return handleTaskResult(ambiance, infraOutput, delegateResponseData, logCallback);
    }

    // just produce step response. Sync flow
    return produceStepResponseForNonTaskStepInfra(ambiance, infraOutput, logCallback);
  }

  private StepResponse produceStepResponseForNonTaskStepInfra(
      Ambiance ambiance, InfrastructureTaskExecutableStepSweepingOutput stepSweepingOutput, NGLogCallback logCallback) {
    final long startTime = System.currentTimeMillis();

    final OutcomeSet outcomeSet = fetchRequiredOutcomes(ambiance);
    final EnvironmentOutcome environmentOutcome = outcomeSet.getEnvironmentOutcome();
    final ServiceStepOutcome serviceOutcome = outcomeSet.getServiceStepOutcome();

    final InfrastructureOutcome infrastructureOutcome = stepSweepingOutput.getInfrastructureOutcome();

    Optional<InstancesOutcome> instancesOutcomeOpt = publishInfraOutput(logCallback, serviceOutcome,
        infrastructureOutcome, ambiance, environmentOutcome, stepSweepingOutput.isSkipInstances());

    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    instancesOutcomeOpt.ifPresent(instancesOutcome
        -> stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                               .outcome(instancesOutcome)
                                               .name(OutcomeExpressionConstants.INSTANCES)
                                               .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                                               .build()));

    String infrastructureKind = infrastructureOutcome.getKind();
    ExecutionInfoKey executionInfoKey =
        ExecutionInfoKeyMapper.getExecutionInfoKey(ambiance, environmentOutcome, serviceOutcome, infrastructureOutcome);
    stageExecutionHelper.saveStageExecutionInfo(ambiance, executionInfoKey, infrastructureKind);
    stageExecutionHelper.addRollbackArtifactToStageOutcomeIfPresent(
        ambiance, stepResponseBuilder, executionInfoKey, infrastructureKind);

    saveExecutionLog(
        logCallback, color("Completed infrastructure step", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .outcome(infrastructureOutcome)
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                         .build())

        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName(LOG_SUFFIX)
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }

  private void validateResources(Ambiance ambiance, Infrastructure infraSpec) {
    final ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    final String principal = executionPrincipalInfo.getPrincipal();

    if (isEmpty(principal)) {
      log.warn("no principal found while executing the infrastructure step. skipping resource validation");
      return;
    }

    Set<EntityDetailProtoDTO> entityDetailsProto =
        entityReferenceExtractorUtils.extractReferredEntities(ambiance, infraSpec);
    List<EntityDetail> entityDetails =
        entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(entityDetailsProto));

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }

  public void setInfraIdentifierAndName(Infrastructure infraSpec, InfrastructureConfig infrastructureConfig) {
    if (infraSpec instanceof InfrastructureDetailsAbstract) {
      ((InfrastructureDetailsAbstract) infraSpec)
          .setInfraIdentifier(infrastructureConfig.getInfrastructureDefinitionConfig().getIdentifier());
      ((InfrastructureDetailsAbstract) infraSpec)
          .setInfraName(infrastructureConfig.getInfrastructureDefinitionConfig().getName());
    }
  }

  private void executeSync(
      Ambiance ambiance, InfrastructureConfig infrastructure, NGLogCallback logCallback, boolean skipInstances) {
    final Infrastructure spec = infrastructure.getInfrastructureDefinitionConfig().getSpec();
    validateConnector(spec, ambiance, logCallback);
    saveExecutionLog(logCallback, "Fetching environment information...");
    validateInfrastructure(spec, ambiance, logCallback);

    final OutcomeSet outcomeSet = fetchRequiredOutcomes(ambiance);
    final EnvironmentOutcome environmentOutcome = outcomeSet.getEnvironmentOutcome();

    saveExecutionLog(logCallback,
        "Environment Name: " + environmentOutcome.getName() + " , Identifier: " + environmentOutcome.getIdentifier());

    final ServiceStepOutcome serviceOutcome = outcomeSet.getServiceStepOutcome();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    infrastructureValidator.validate(spec);

    final InfrastructureOutcome infrastructureOutcome = infrastructureOutcomeProvider.getOutcome(ambiance, spec,
        environmentOutcome, serviceOutcome, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(),
        ngAccess.getProjectIdentifier(), infrastructure.getInfrastructureDefinitionConfig().getTags());

    // save spec sweeping output for further use within the step
    executionSweepingOutputService.consume(ambiance, INFRA_TASK_EXECUTABLE_STEP_OUTPUT,
        InfrastructureTaskExecutableStepSweepingOutput.builder()
            .infrastructureOutcome(infrastructureOutcome)
            .skipInstances(skipInstances)
            .addRcStep(!infrastructure.getInfrastructureDefinitionConfig().isAllowSimultaneousDeployments())
            .build(),
        StepCategory.STAGE.name());

    publishOutput(spec, ambiance);
  }

  private void publishOutput(Infrastructure infrastructure, Ambiance ambiance) {
    if (InfrastructureKind.ELASTIGROUP.equals(infrastructure.getKind())) {
      executionSweepingOutputService.consume(ambiance, ELASTIGROUP_CONFIGURATION_OUTPUT,
          ElastigroupConfigurationOutput.builder()
              .storeConfig(((ElastigroupInfrastructure) infrastructure).getConfiguration().getStore().getSpec())
              .build(),
          StepCategory.STAGE.name());
    }
  }

  private boolean isTaskStep(String infraKind) {
    return InfrastructureKind.SSH_WINRM_AZURE.equals(infraKind) || InfrastructureKind.SSH_WINRM_AWS.equals(infraKind);
  }

  private InfrastructureConfig fetchInfraConfigFromDBorThrow(
      Ambiance ambiance, InfrastructureTaskExecutableStepV2Params stepParameters) {
    Optional<InfrastructureEntity> infrastructureEntityOpt =
        infrastructureEntityService.get(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
            AmbianceUtils.getProjectIdentifier(ambiance), stepParameters.getEnvRef().getValue(),
            stepParameters.getInfraRef().getValue());
    if (infrastructureEntityOpt.isEmpty()) {
      throw new InvalidRequestException(String.format("Infrastructure definition %s not found in environment %s",
          stepParameters.getInfraRef().getValue(), stepParameters.getEnvRef().getValue()));
    }

    if (infrastructureEntityOpt.get().getDeploymentType() != null && stepParameters.getDeploymentType() != null
        && infrastructureEntityOpt.get().getDeploymentType() != stepParameters.getDeploymentType()) {
      throw new InvalidRequestException(
          format("Deployment type of the stage [%s] and the infrastructure [%s] do not match",
              stepParameters.getDeploymentType().getYamlName(),
              infrastructureEntityOpt.get().getDeploymentType().getYamlName()));
    }

    final InfrastructureEntity infrastructureEntity = infrastructureEntityOpt.get();
    if (ParameterField.isNotNull(stepParameters.getInfraInputs())
        && isNotEmpty(stepParameters.getInfraInputs().getValue())) {
      String mergedYaml = mergeInfraInputs(infrastructureEntity.getYaml(), stepParameters.getInfraInputs().getValue());

      infrastructureEntity.setYaml(mergedYaml);
    }

    return getInfrastructureConfig(infrastructureEntity);
  }

  private InfrastructureConfig getInfrastructureConfig(InfrastructureEntity infrastructureEntity) {
    try {
      return InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructureEntity);
    } catch (Exception ex) {
      infrastructureYamlSchemaHelper.validateSchema(
          infrastructureEntity.getAccountId(), infrastructureEntity.getYaml());
      log.error(String.format(
          "Infrastructure schema validation succeeded but failed to convert yaml to Infrastructure config [%s]",
          infrastructureEntity.getIdentifier()));
      throw ex;
    }
  }

  private Optional<InstancesOutcome> publishInfraOutput(NGLogCallback logCallback, ServiceStepOutcome serviceOutcome,
      InfrastructureOutcome infrastructureOutcome, Ambiance ambiance, EnvironmentOutcome environmentOutcome,
      boolean skipInstances) {
    if (serviceOutcome.getType() == null) {
      throw new InvalidRequestException("service type cannot be null");
    }
    if (ServiceSpecType.SSH.toLowerCase(Locale.ROOT).equals(serviceOutcome.getType().toLowerCase(Locale.ROOT))
        || ServiceSpecType.CUSTOM_DEPLOYMENT.toLowerCase(Locale.ROOT)
               .equals(serviceOutcome.getType().toLowerCase(Locale.ROOT))) {
      ExecutionInfoKey executionInfoKey = ExecutionInfoKeyMapper.getExecutionInfoKey(
          ambiance, environmentOutcome, serviceOutcome, infrastructureOutcome);
      return Optional.ofNullable(publishSshInfraDelegateConfigOutput(
          ambiance, logCallback, infrastructureOutcome, executionInfoKey, skipInstances));
    }

    if (ServiceSpecType.WINRM.toLowerCase(Locale.ROOT).equals(serviceOutcome.getType().toLowerCase(Locale.ROOT))) {
      ExecutionInfoKey executionInfoKey = ExecutionInfoKeyMapper.getExecutionInfoKey(
          ambiance, environmentOutcome, serviceOutcome, infrastructureOutcome);
      return Optional.ofNullable(publishWinRmInfraDelegateConfigOutput(
          ambiance, logCallback, infrastructureOutcome, executionInfoKey, skipInstances));
    }

    if (infrastructureOutcome instanceof K8sGcpInfrastructureOutcome
        || infrastructureOutcome instanceof K8sDirectInfrastructureOutcome
        || infrastructureOutcome instanceof K8sAzureInfrastructureOutcome
        || infrastructureOutcome instanceof K8sAwsInfrastructureOutcome
        || infrastructureOutcome instanceof K8sRancherInfrastructureOutcome) {
      publishK8sInfraDelegateConfigOutput(infrastructureOutcome, ambiance);
    }

    return Optional.empty();
  }

  private void publishK8sInfraDelegateConfigOutput(InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
    K8sInfraDelegateConfig k8sInfraDelegateConfig =
        cdStepHelper.getK8sInfraDelegateConfig(infrastructureOutcome, ambiance);

    K8sInfraDelegateConfigOutput k8sInfraDelegateConfigOutput =
        K8sInfraDelegateConfigOutput.builder().k8sInfraDelegateConfig(k8sInfraDelegateConfig).build();
    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME,
        k8sInfraDelegateConfigOutput, StepCategory.STAGE.name());
  }

  private InstancesOutcome publishSshInfraDelegateConfigOutput(Ambiance ambiance, NGLogCallback logCallback,
      InfrastructureOutcome infrastructureOutcome, ExecutionInfoKey executionInfoKey, boolean skipInstances) {
    SshInfraDelegateConfig sshInfraDelegateConfig =
        cdStepHelper.getSshInfraDelegateConfig(infrastructureOutcome, ambiance);

    SshInfraDelegateConfigOutput sshInfraDelegateConfigOutput =
        SshInfraDelegateConfigOutput.builder().sshInfraDelegateConfig(sshInfraDelegateConfig).build();
    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME,
        sshInfraDelegateConfigOutput, StepCategory.STAGE.name());

    if (InfrastructureKind.CUSTOM_DEPLOYMENT.equals(infrastructureOutcome.getKind())) {
      return InstancesOutcome.builder().instances(Collections.emptyList()).build();
    }

    Set<String> hosts = sshInfraDelegateConfig.getHosts();
    if (EmptyPredicate.isEmpty(hosts)) {
      saveExecutionLogSafely(logCallback,
          color("No host(s) were provided for specified infrastructure or filter did not match any instance(s)", Red));
    } else {
      saveExecutionLogSafely(logCallback, color(format("Successfully fetched %s instance(s)", hosts.size()), Green));
      saveExecutionLogSafely(logCallback, color(format("Fetched following instance(s) %s)", hosts), Green));
    }

    Set<String> filteredHosts = stageExecutionHelper.saveAndExcludeHostsWithSameArtifactDeployedIfNeeded(
        ambiance, executionInfoKey, infrastructureOutcome, hosts, ServiceSpecType.SSH, skipInstances, logCallback);
    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.OUTPUT,
        HostsOutput.builder().hosts(filteredHosts).build(), StepCategory.STAGE.name());
    return instanceOutcomeHelper.saveAndGetInstancesOutcome(ambiance, infrastructureOutcome, filteredHosts);
  }

  private InstancesOutcome publishWinRmInfraDelegateConfigOutput(Ambiance ambiance, NGLogCallback logCallback,
      InfrastructureOutcome infrastructureOutcome, ExecutionInfoKey executionInfoKey, boolean skipInstances) {
    WinRmInfraDelegateConfig winRmInfraDelegateConfig =
        cdStepHelper.getWinRmInfraDelegateConfig(infrastructureOutcome, ambiance);

    WinRmInfraDelegateConfigOutput winRmInfraDelegateConfigOutput =
        WinRmInfraDelegateConfigOutput.builder().winRmInfraDelegateConfig(winRmInfraDelegateConfig).build();
    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.WINRM_INFRA_DELEGATE_CONFIG_OUTPUT_NAME,
        winRmInfraDelegateConfigOutput, StepCategory.STAGE.name());
    Set<String> hosts = winRmInfraDelegateConfig.getHosts();
    if (EmptyPredicate.isEmpty(hosts)) {
      saveExecutionLogSafely(logCallback,
          color("No host(s) were provided for specified infrastructure or filter did not match any instance(s)", Red));
    } else {
      saveExecutionLogSafely(logCallback, color(format("Successfully fetched %s instance(s)", hosts.size()), Green));
      saveExecutionLogSafely(logCallback, color(format("Fetched following instance(s) %s)", hosts), Green));
    }

    Set<String> filteredHosts = stageExecutionHelper.saveAndExcludeHostsWithSameArtifactDeployedIfNeeded(
        ambiance, executionInfoKey, infrastructureOutcome, hosts, ServiceSpecType.WINRM, skipInstances, logCallback);
    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.OUTPUT,
        HostsOutput.builder().hosts(filteredHosts).build(), StepCategory.STAGE.name());
    return instanceOutcomeHelper.saveAndGetInstancesOutcome(ambiance, infrastructureOutcome, filteredHosts);
  }

  private void validateStepParameters(InfrastructureTaskExecutableStepV2Params stepParameters) {
    if (ParameterField.isNull(stepParameters.getEnvRef())) {
      throw new InvalidRequestException("environment reference is not specified");
    }

    if (ParameterField.isNull(stepParameters.getInfraRef())) {
      throw new InvalidRequestException("infrastructure definition reference is not specified");
    }

    if (stepParameters.getEnvRef().isExpression()) {
      throw new InvalidRequestException(
          "environment reference " + stepParameters.getEnvRef().getExpressionValue() + " not resolved");
    }

    if (stepParameters.getInfraRef().isExpression()) {
      throw new InvalidRequestException(
          "infrastructure definition reference" + stepParameters.getInfraRef().getExpressionValue() + " not resolved");
    }
  }

  private String mergeInfraInputs(String originalYaml, Map<String, Object> inputs) {
    if (isEmpty(inputs)) {
      return originalYaml;
    }
    Map<String, Object> inputMap = new HashMap<>();
    inputMap.put(YamlTypes.INFRASTRUCTURE_DEF, inputs);
    return MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
        originalYaml, YamlPipelineUtils.writeYamlString(inputMap), true, true);
  }

  @Override
  public void handleAbort(Ambiance ambiance, InfrastructureTaskExecutableStepV2Params stepParameters,
      AsyncExecutableResponse executableResponse) {
    final NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance, LOG_SUFFIX);
    logCallback.saveExecutionLog("Infrastructure Step was aborted", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
  }

  private StepResponse prepareFailureResponse(Exception ex) {
    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(ExceptionUtils.getMessage(ex))
                                  .build();
    return StepResponse.builder()
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().addFailureData(failureData).build())
        .build();
  }
}
