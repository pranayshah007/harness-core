/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;
import static io.harness.cdng.elastigroup.ElastigroupBGStageSetupStep.ELASTIGROUP_BG_STAGE_SETUP_COMMAND_NAME;
import static io.harness.cdng.elastigroup.ElastigroupSetupStep.ELASTIGROUP_SETUP_COMMAND_NAME;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MAX_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_MIN_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.DEFAULT_ELASTIGROUP_TARGET_INSTANCES;
import static io.harness.spotinst.model.SpotInstConstants.GROUP_CONFIG_ELEMENT;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.AMIArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupParametersFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupPreFetchOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupStartupScriptFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExceptionPassThroughData;
import io.harness.cdng.elastigroup.config.StartupScriptOutcome;
import io.harness.cdng.elastigroup.output.ElastigroupConfigurationOutput;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.execution.spot.elastigroup.ElastigroupStageExecutionDetails.ElastigroupStageExecutionDetailsKeys;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.elastigroup.ElastigroupPreFetchResult;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.SpotServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupPreFetchRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupPreFetchResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class ElastigroupStepCommonHelper extends ElastigroupStepUtils {
  private static final String STAGE_EXECUTION_INFO_KEY_FORMAT = "%s.%s";
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private ElastigroupEntityHelper elastigroupEntityHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private StageExecutionInfoService stageExecutionInfoService;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private CDStepHelper cdStepHelper;

  private static final String STARTUP_SCRIPT = "Startup Script";
  private static final String ELASTIGROUP_CONFIGURATION = "Elastigroup Configuration";

  public ElastiGroup generateConfigFromJson(String elastiGroupJson) {
    java.lang.reflect.Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
    Gson gson = new Gson();

    // Map<"group": {...entire config...}>, this is elastiGroupConfig json that spotinst exposes
    Map<String, Object> jsonConfigMap = gson.fromJson(elastiGroupJson, mapType);
    Map<String, Object> elastiGroupConfigMap = (Map<String, Object>) jsonConfigMap.get(GROUP_CONFIG_ELEMENT);
    String groupConfigJson = gson.toJson(elastiGroupConfigMap);
    return gson.fromJson(groupConfigJson, ElastiGroup.class);
  }

  public StepResponse.StepOutcome saveSpotServerInstanceInfosToSweepingOutput(
      List<String> newEc2Instances, List<String> existingEc2Instances, Ambiance ambiance) {
    List<ServerInstanceInfo> spotServerInstanceInfos =
        createSpotServerInstanceInfos(newEc2Instances, new ArrayList<>(), ambiance);
    if (isNotEmpty(spotServerInstanceInfos)) {
      return instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, spotServerInstanceInfos);
    }
    return null;
  }

  private ElastigroupSetupDataOutcome getElastigroupSetupOutcome(Ambiance ambiance) {
    OptionalSweepingOutput optionalSetupDataOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ELASTIGROUP_SETUP_OUTCOME));
    if (!optionalSetupDataOutput.isFound()) {
      throw new InvalidRequestException("No elastigroup setup output found.");
    }
    return (ElastigroupSetupDataOutcome) optionalSetupDataOutput.getOutput();
  }

  private List<ServerInstanceInfo> createSpotServerInstanceInfos(
      List<String> newEc2Instances, List<String> existingEc2Instances, Ambiance ambiance) {
    if (isEmpty(newEc2Instances) && isEmpty(existingEc2Instances)) {
      return new ArrayList<>();
    }

    InfrastructureOutcome infrastructure = cdStepHelper.getInfrastructureOutcome(ambiance);

    ElastigroupSetupDataOutcome elastigroupSetupDataOutcome = getElastigroupSetupOutcome(ambiance);
    ElastiGroup oldElastigroup = elastigroupSetupDataOutcome.getOldElastigroupOriginalConfig();
    ElastiGroup newElastigroup = elastigroupSetupDataOutcome.getNewElastigroupOriginalConfig();

    if (oldElastigroup == null && newElastigroup == null) {
      return new ArrayList<>();
    }

    List<SpotServerInstanceInfo> oldSpotServerInstanceInfos =
        getSpotServerInstanceInfos(oldElastigroup, existingEc2Instances, infrastructure);

    List<SpotServerInstanceInfo> newSpotServerInstanceInfos =
        getSpotServerInstanceInfos(newElastigroup, newEc2Instances, infrastructure);

    return Stream.of(oldSpotServerInstanceInfos, newSpotServerInstanceInfos)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  @NotNull
  private List<SpotServerInstanceInfo> getSpotServerInstanceInfos(
      ElastiGroup elastigroup, List<String> ec2InstanceIds, InfrastructureOutcome infrastructure) {
    List<SpotServerInstanceInfo> newSpotServerInstanceInfos;
    if (elastigroup != null && isNotEmpty(elastigroup.getId())) {
      String elastigroupId = elastigroup.getId();
      newSpotServerInstanceInfos = ec2InstanceIds == null
          ? Collections.emptyList()
          : ec2InstanceIds.stream()
                .map(id -> mapToSpotServerInstanceInfo(infrastructure.getInfrastructureKey(), elastigroupId, id))
                .collect(Collectors.toList());

    } else {
      newSpotServerInstanceInfos = Collections.emptyList();
    }
    return newSpotServerInstanceInfos;
  }

  private SpotServerInstanceInfo mapToSpotServerInstanceInfo(
      String infrastructureKey, String groupId, String instanceId) {
    return SpotServerInstanceInfo.builder()
        .infrastructureKey(infrastructureKey)
        .ec2InstanceId(instanceId)
        .elastigroupId(groupId)
        .build();
  }

  public int renderCount(ParameterField<Integer> field, int defaultValue, Ambiance ambiance) {
    if (field == null || field.isExpression() || field.getValue() == null) {
      return defaultValue;
    } else {
      try {
        return Integer.parseInt(renderExpression(ambiance, field.fetchFinalValue().toString()));
      } catch (NumberFormatException e) {
        log.error(format("Number format Exception while evaluating: [%s]", field.fetchFinalValue().toString()), e);
        return defaultValue;
      }
    }
  }

  public String renderExpression(Ambiance ambiance, String stringObject) {
    return engineExpressionService.renderExpression(ambiance, stringObject);
  }

  public InfrastructureOutcome getInfrastructureOutcome(Ambiance ambiance) {
    return (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
  }

  public TaskChainResponse startChainLink(Ambiance ambiance, StepElementParameters stepElementParameters,
      ElastigroupExecutionPassThroughData passThroughData) {
    OptionalOutcome startupScriptOptionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.STARTUP_SCRIPT));

    LogCallback logCallback =
        getLogCallback(ElastigroupCommandUnitConstants.FETCH_STARTUP_SCRIPT.toString(), ambiance, true);

    // Get InfrastructureOutcome
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    String startupScript = null;
    UnitProgressData unitProgressData = UnitProgressData.builder().build();

    if (startupScriptOptionalOutcome.isFound()) {
      StartupScriptOutcome startupScriptOutcome = (StartupScriptOutcome) startupScriptOptionalOutcome.getOutcome();

      if (ManifestStoreType.HARNESS.equals(startupScriptOutcome.getStore().getKind())) {
        startupScript =
            fetchFileFromHarnessStore(ambiance, startupScriptOutcome.getStore(), STARTUP_SCRIPT, logCallback);
        unitProgressData = getCommandUnitProgressData(
            ElastigroupCommandUnitConstants.FETCH_STARTUP_SCRIPT.toString(), CommandExecutionStatus.SUCCESS);

        // Render expressions for all file content fetched from Harness File Store
        if (startupScript != null) {
          startupScript = renderExpression(ambiance, startupScript);
        }
      } else {
        return stepFailureTaskResponseWithMessage(
            unitProgressData, "Store Type provided for Startup Script Not Supported");
      }
    } else {
      logCallback.saveExecutionLog(
          color(format("Startup Script Not Available.", "startupScript"), LogColor.White, LogWeight.Bold), INFO,
          CommandExecutionStatus.SUCCESS);
      unitProgressData = getCommandUnitProgressData(
          ElastigroupCommandUnitConstants.FETCH_STARTUP_SCRIPT.toString(), CommandExecutionStatus.SUCCESS);
    }

    if (isNotEmpty(startupScript)) {
      passThroughData.setBase64EncodedStartupScript(getBase64EncodedStartupScript(ambiance, startupScript));
    }
    passThroughData.setInfrastructure(infrastructureOutcome);
    return fetchElastigroupConfiguration(ambiance, stepElementParameters, unitProgressData, passThroughData);
  }

  private TaskChainResponse fetchElastigroupConfiguration(Ambiance ambiance,
      StepElementParameters stepElementParameters, UnitProgressData unitProgressData,
      ElastigroupExecutionPassThroughData passThroughData) {
    LogCallback logCallback =
        getLogCallback(ElastigroupCommandUnitConstants.FETCH_ELASTIGROUP_CONFIGURATION.toString(), ambiance, true);

    ElastigroupConfigurationOutput elastigroupConfigurationOutput;
    OptionalSweepingOutput optionalElastigroupConfigurationOutput =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ELASTIGROUP_CONFIGURATION_OUTPUT));
    String elastigroupConfiguration = null;
    if (optionalElastigroupConfigurationOutput.isFound()) {
      elastigroupConfigurationOutput =
          (ElastigroupConfigurationOutput) optionalElastigroupConfigurationOutput.getOutput();

      StoreConfig storeConfig = elastigroupConfigurationOutput.getStoreConfig();
      if (ManifestStoreType.HARNESS.equals(storeConfig.getKind())) {
        elastigroupConfiguration = fetchFileFromHarnessStore(
            ambiance, elastigroupConfigurationOutput.getStoreConfig(), ELASTIGROUP_CONFIGURATION, logCallback);
        unitProgressData.getUnitProgresses().add(
            UnitProgress.newBuilder()
                .setUnitName(ElastigroupCommandUnitConstants.FETCH_ELASTIGROUP_CONFIGURATION.toString())
                .setStatus(CommandExecutionStatus.SUCCESS.getUnitStatus())
                .build());

        if (isNotEmpty(elastigroupConfiguration)) {
          elastigroupConfiguration = renderExpression(ambiance, elastigroupConfiguration);
        }
      } else {
        return stepFailureTaskResponseWithMessage(
            unitProgressData, "Store Type provided for Elastigroup Configuration is not supported");
      }
    }

    if (isEmpty(elastigroupConfiguration)) {
      return stepFailureTaskResponseWithMessage(unitProgressData, "Elastigroup Configuration provided is empty");
    } else {
      passThroughData.setElastigroupConfiguration(elastigroupConfiguration);
    }

    return executeArtifactTask(ambiance, stepElementParameters, unitProgressData, passThroughData);
  }

  public ElastiGroup fetchOldElasticGroup(ElastigroupSetupResult elastigroupSetupResult) {
    if (isEmpty(elastigroupSetupResult.getGroupToBeDownsized())) {
      return null;
    }

    return elastigroupSetupResult.getGroupToBeDownsized().get(0);
  }

  public TaskChainResponse executeNextLink(ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    UnitProgressData unitProgressData = null;
    TaskChainResponse taskChainResponse = null;
    try {
      if (responseData instanceof ElastigroupPreFetchResponse) {
        ElastigroupPreFetchResponse preFetchResponse = (ElastigroupPreFetchResponse) responseData;
        ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
            (ElastigroupExecutionPassThroughData) passThroughData;

        taskChainResponse = handlePreFetchResponse(preFetchResponse, elastigroupStepExecutor, ambiance,
            stepElementParameters, elastigroupExecutionPassThroughData);
      }
    } catch (Exception e) {
      taskChainResponse =
          TaskChainResponse.builder()
              .chainEnd(true)
              .passThroughData(
                  ElastigroupStepExceptionPassThroughData.builder()
                      .errorMessage(ExceptionUtils.getMessage(e))
                      .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
                      .build())
              .build();
    }

    return taskChainResponse;
  }

  public SpotInstConfig getSpotInstConfig(InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return elastigroupEntityHelper.getSpotInstConfig(infrastructureOutcome, ngAccess);
  }

  public List<EncryptedDataDetail> getEncryptedDataDetail(ConnectorInfoDTO connectorInfoDTO, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return elastigroupEntityHelper.getEncryptionDataDetails(connectorInfoDTO, ngAccess);
  }
  private TaskChainResponse handlePreFetchResponse(ElastigroupPreFetchResponse elastigroupPreFetchResponse,
      ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      ElastigroupExecutionPassThroughData passThroughData) {
    if (elastigroupPreFetchResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return handleFailureElastigroupPrepareRollbackTask(elastigroupPreFetchResponse);
    }

    saveStageExecutionInfo(ambiance, passThroughData, elastigroupPreFetchResponse.getElastigroupPreFetchResult());
    saveSweepingOutput(ambiance, passThroughData, elastigroupPreFetchResponse.getElastigroupPreFetchResult());

    return elastigroupStepExecutor.executeElastigroupTask(
        ambiance, stepElementParameters, passThroughData, passThroughData.getLastActiveUnitProgressData());
  }

  private void saveStageExecutionInfo(
      Ambiance ambiance, ElastigroupExecutionPassThroughData passThroughData, ElastigroupPreFetchResult result) {
    Scope scope = Scope.of(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance));
    Map<String, Object> updates = new HashMap<>();
    updates.put(String.format(STAGE_EXECUTION_INFO_KEY_FORMAT, StageExecutionInfoKeys.executionDetails,
                    ElastigroupStageExecutionDetailsKeys.elastigroupNamePrefix),
        passThroughData.getElastigroupNamePrefix());
    updates.put(String.format(STAGE_EXECUTION_INFO_KEY_FORMAT, StageExecutionInfoKeys.executionDetails,
                    ElastigroupStageExecutionDetailsKeys.elastigroups),
        result.getElastigroups());
    updates.put(String.format(STAGE_EXECUTION_INFO_KEY_FORMAT, StageExecutionInfoKeys.executionDetails,
                    ElastigroupStageExecutionDetailsKeys.blueGreen),
        passThroughData.isBlueGreen());
    stageExecutionInfoService.update(scope, ambiance.getStageExecutionId(), updates);
  }

  private void saveSweepingOutput(
      Ambiance ambiance, ElastigroupExecutionPassThroughData passThroughData, ElastigroupPreFetchResult result) {
    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ELASTIGROUP_PRE_FETCH_OUTCOME,
        ElastigroupPreFetchOutcome.builder()
            .blueGreen(passThroughData.isBlueGreen())
            .elastigroups(result.getElastigroups())
            .elastigroupNamePrefix(passThroughData.getElastigroupNamePrefix())
            .build(),
        StepOutcomeGroup.STAGE.name());
  }

  private TaskChainResponse executeArtifactTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      UnitProgressData unitProgressData, ElastigroupExecutionPassThroughData passThroughData) {
    // Get ArtifactsOutcome
    Optional<ArtifactOutcome> artifactOutcome = resolveArtifactsOutcome(ambiance);

    // Update expressions in ArtifactsOutcome
    String image = null;
    if (artifactOutcome.isPresent()) {
      AMIArtifactOutcome amiArtifactOutcome = (AMIArtifactOutcome) artifactOutcome.get();
      cdExpressionResolver.updateExpressions(ambiance, amiArtifactOutcome);
      image = amiArtifactOutcome.getAmiId();
    }
    if (isEmpty(image)) {
      return stepFailureTaskResponseWithMessage(unitProgressData, "AMI not available. Please specify the AMI artifact");
    } else {
      passThroughData.setImage(image);
      passThroughData.setLastActiveUnitProgressData(unitProgressData);
    }

    return executePreFetchTask(ambiance, stepElementParameters, passThroughData, unitProgressData);
  }

  public TaskChainResponse executePreFetchTask(Ambiance ambiance, StepElementParameters stepParameters,
      ElastigroupExecutionPassThroughData executionPassThroughData, UnitProgressData unitProgressData) {
    ElastigroupInfrastructureOutcome infrastructureOutcome =
        (ElastigroupInfrastructureOutcome) executionPassThroughData.getInfrastructure();
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    SpotInstConfig spotInstConfig = getSpotInstConfig(infrastructureOutcome, ambiance);

    String elastigroupNamePrefix;
    if (executionPassThroughData.isBlueGreen()) {
      elastigroupNamePrefix = getElastigroupNamePrefixForBG(ambiance, stepParameters);
    } else {
      elastigroupNamePrefix = getElastigroupNamePrefixForNonBG(ambiance, stepParameters);
    }

    if (isBlank(elastigroupNamePrefix)) {
      return stepFailureTaskResponseWithMessage(
          unitProgressData, "Name not provided for new elastigroup to be created");
    }

    executionPassThroughData.setElastigroupNamePrefix(elastigroupNamePrefix);
    executionPassThroughData.setSpotInstConfig(spotInstConfig);

    ElastigroupPreFetchRequest preFetchRequest =
        ElastigroupPreFetchRequest.builder()
            .blueGreen(true)
            .elastigroupNamePrefix(elastigroupNamePrefix)
            .accountId(accountId)
            .spotInstConfig(spotInstConfig)
            .commandName(executionPassThroughData.isBlueGreen() ? ELASTIGROUP_BG_STAGE_SETUP_COMMAND_NAME
                                                                : ELASTIGROUP_SETUP_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(getSteadyStateTimeout(stepParameters))
            .build();

    return queueElastigroupTask(stepParameters, preFetchRequest, ambiance, executionPassThroughData, false,
        TaskType.ELASTIGROUP_PRE_FETCH_TASK_NG);
  }

  private int getSteadyStateTimeout(StepElementParameters stepParameters) {
    return CDStepHelper.getTimeoutInMin(stepParameters);
  }

  private String getElastigroupNamePrefixForBG(Ambiance ambiance, StepElementParameters stepParameters) {
    ElastigroupBGStageSetupStepParameters elastigroupBGStageSetupStepParameters =
        (ElastigroupBGStageSetupStepParameters) stepParameters.getSpec();

    ParameterField<String> elastigroupSetupStepParametersName = elastigroupBGStageSetupStepParameters.getName();
    return elastigroupSetupStepParametersName.isExpression()
        ? renderExpression(ambiance, elastigroupSetupStepParametersName.getExpressionValue())
        : elastigroupSetupStepParametersName.getValue();
  }

  private String getElastigroupNamePrefixForNonBG(Ambiance ambiance, StepElementParameters stepParameters) {
    ElastigroupSetupStepParameters elastigroupSetupStepParameters =
        (ElastigroupSetupStepParameters) stepParameters.getSpec();

    ParameterField<String> elastigroupSetupStepParametersName = elastigroupSetupStepParameters.getName();
    return elastigroupSetupStepParametersName.isExpression()
        ? renderExpression(ambiance, elastigroupSetupStepParametersName.getExpressionValue())
        : elastigroupSetupStepParametersName.getValue();
  }

  public ElastiGroup generateOriginalConfigFromJson(
      String elastiGroupOriginalJson, ElastigroupInstances elastigroupInstances, Ambiance ambiance) {
    ElastiGroup elastiGroup = generateConfigFromJson(elastiGroupOriginalJson);
    ElastiGroupCapacity groupCapacity = elastiGroup.getCapacity();
    if (ElastigroupInstancesType.CURRENT_RUNNING.equals(elastigroupInstances.getType())) {
      groupCapacity.setMinimum(DEFAULT_ELASTIGROUP_MIN_INSTANCES);
      groupCapacity.setMaximum(DEFAULT_ELASTIGROUP_MAX_INSTANCES);
      groupCapacity.setTarget(DEFAULT_ELASTIGROUP_TARGET_INSTANCES);
    } else {
      ElastigroupFixedInstances elastigroupFixedInstances = (ElastigroupFixedInstances) elastigroupInstances.getSpec();
      groupCapacity.setMinimum(
          renderCount(elastigroupFixedInstances.getMin(), DEFAULT_ELASTIGROUP_MIN_INSTANCES, ambiance));
      groupCapacity.setMaximum(
          renderCount(elastigroupFixedInstances.getMax(), DEFAULT_ELASTIGROUP_MAX_INSTANCES, ambiance));
      groupCapacity.setTarget(
          renderCount(elastigroupFixedInstances.getDesired(), DEFAULT_ELASTIGROUP_TARGET_INSTANCES, ambiance));
    }
    return elastiGroup;
  }

  public TaskChainResponse stepFailureTaskResponseWithMessage(UnitProgressData unitProgressData, String msg) {
    ElastigroupStepExceptionPassThroughData elastigroupStepExceptionPassThroughData =
        ElastigroupStepExceptionPassThroughData.builder().errorMessage(msg).unitProgressData(unitProgressData).build();
    return TaskChainResponse.builder().passThroughData(elastigroupStepExceptionPassThroughData).chainEnd(true).build();
  }

  private TaskChainResponse handleFailureElastigroupPrepareRollbackTask(
      ElastigroupPreFetchResponse elastigroupPrepareRollbackResponse) {
    ElastigroupStepExceptionPassThroughData elastigroupStepExceptionPassThroughData =
        ElastigroupStepExceptionPassThroughData.builder()
            .errorMessage(elastigroupPrepareRollbackResponse.getErrorMessage())
            .unitProgressData(elastigroupPrepareRollbackResponse.getUnitProgressData())
            .build();
    return TaskChainResponse.builder().passThroughData(elastigroupStepExceptionPassThroughData).chainEnd(true).build();
  }

  public String getBase64EncodedStartupScript(Ambiance ambiance, String startupScript) {
    if (startupScript != null) {
      String startupScriptAfterEvaluation = renderExpression(ambiance, startupScript);
      return java.util.Base64.getEncoder().encodeToString(startupScriptAfterEvaluation.getBytes(Charsets.UTF_8));
    }
    return null;
  }

  public TaskChainResponse queueElastigroupTask(StepElementParameters stepElementParameters,
      ElastigroupCommandRequest elastigroupCommandRequest, Ambiance ambiance, PassThroughData passThroughData,
      boolean isChainEnd, TaskType taskType) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {elastigroupCommandRequest})
                            .taskType(taskType.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();

    String taskName = taskType.getDisplayName();

    ElastigroupSpecParameters elastigroupSpecParameters = (ElastigroupSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, elastigroupSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(elastigroupSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  public StepResponse handleStartupScriptTaskFailure(
      ElastigroupStartupScriptFetchFailurePassThroughData elastigroupStartupScriptFetchFailurePassThroughData) {
    UnitProgressData unitProgressData = elastigroupStartupScriptFetchFailurePassThroughData.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .setErrorMessage(elastigroupStartupScriptFetchFailurePassThroughData.getErrorMsg())
                         .build())
        .build();
  }

  public StepResponse handleElastigroupParametersTaskFailure(
      ElastigroupParametersFetchFailurePassThroughData elastigroupParametersFetchFailurePassThroughData) {
    UnitProgressData unitProgressData = elastigroupParametersFetchFailurePassThroughData.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .setErrorMessage(elastigroupParametersFetchFailurePassThroughData.getErrorMsg())
                         .build())
        .build();
  }

  public StepResponse handleStepExceptionFailure(ElastigroupStepExceptionPassThroughData stepException) {
    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(io.harness.eraro.Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(HarnessStringUtils.emptyIfNull(stepException.getErrorMessage()))
                                  .build();
    return StepResponse.builder()
        .unitProgressList(stepException.getUnitProgressData().getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .addAllFailureTypes(failureData.getFailureTypesList())
                         .setErrorMessage(failureData.getMessage())
                         .addFailureData(failureData)
                         .build())
        .build();
  }

  public StepResponse handleTaskException(
      Ambiance ambiance, ElastigroupExecutionPassThroughData executionPassThroughData, Exception e) throws Exception {
    if (ExceptionUtils.cause(TaskNGDataException.class, e) != null) {
      throw e;
    }

    UnitProgressData unitProgressData =
        completeUnitProgressData(executionPassThroughData.getLastActiveUnitProgressData(), ambiance, e.getMessage());
    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(io.harness.eraro.Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(HarnessStringUtils.emptyIfNull(ExceptionUtils.getMessage(e)))
                                  .build();

    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .addAllFailureTypes(failureData.getFailureTypesList())
                         .setErrorMessage(failureData.getMessage())
                         .addFailureData(failureData)
                         .build())
        .build();
  }

  public static StepResponseBuilder getFailureResponseBuilder(
      ElastigroupCommandResponse elastigroupCommandResponse, StepResponseBuilder stepResponseBuilder) {
    stepResponseBuilder.status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .setErrorMessage(ElastigroupStepCommonHelper.getErrorMessage(elastigroupCommandResponse))
                         .build());
    return stepResponseBuilder;
  }

  public static String getErrorMessage(ElastigroupCommandResponse elastigroupCommandResponse) {
    return elastigroupCommandResponse.getErrorMessage() == null ? "" : elastigroupCommandResponse.getErrorMessage();
  }

  public UnitProgressData getCommandUnitProgressData(
      String commandName, CommandExecutionStatus commandExecutionStatus) {
    LinkedHashMap<String, CommandUnitProgress> commandUnitProgressMap = new LinkedHashMap<>();
    CommandUnitProgress commandUnitProgress = CommandUnitProgress.builder().status(commandExecutionStatus).build();
    commandUnitProgressMap.put(commandName, commandUnitProgress);
    CommandUnitsProgress commandUnitsProgress =
        CommandUnitsProgress.builder().commandUnitProgressMap(commandUnitProgressMap).build();
    return UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress);
  }

  @VisibleForTesting
  List<LoadBalancerDetailsForBGDeployment> addLoadBalancerConfigAfterExpressionEvaluation(
      List<AwsLoadBalancerConfigYaml> awsLoadBalancerConfigs, Ambiance ambiance) {
    List<LoadBalancerDetailsForBGDeployment> loadBalancerConfigs = new ArrayList<>();

    Map<String, LoadBalancerDetailsForBGDeployment> lbMap = new HashMap<>();
    // Use a map with key as <lbName + prodPort + stagePort>, and value as actual LbConfig.
    // This will get rid of any duplicate config.
    if (isNotEmpty(awsLoadBalancerConfigs)) {
      awsLoadBalancerConfigs.forEach(awsLoadBalancerConfig -> {
        lbMap.put(getLBKey(awsLoadBalancerConfig),
            LoadBalancerDetailsForBGDeployment.builder()
                .loadBalancerName(renderExpression(ambiance, awsLoadBalancerConfig.getLoadBalancer().getValue()))
                .prodListenerPort(renderExpression(ambiance, awsLoadBalancerConfig.getProdListenerPort().getValue()))
                .stageListenerPort(renderExpression(ambiance, awsLoadBalancerConfig.getStageListenerPort().getValue()))
                .useSpecificRules(true)
                .prodRuleArn(renderExpression(ambiance, awsLoadBalancerConfig.getProdListenerRuleArn().getValue()))
                .stageRuleArn(renderExpression(ambiance, awsLoadBalancerConfig.getStageListenerRuleArn().getValue()))
                .build());
      });

      loadBalancerConfigs.addAll(lbMap.values());
    }

    return loadBalancerConfigs;
  }

  @NotNull
  private String getLBKey(AwsLoadBalancerConfigYaml awsLoadBalancerConfig) {
    return new StringBuilder(128)
        .append(awsLoadBalancerConfig.getLoadBalancer())
        .append('_')
        .append(awsLoadBalancerConfig.getProdListenerPort())
        .append('_')
        .append(awsLoadBalancerConfig.getStageListenerPort())
        .toString();
  }
}
