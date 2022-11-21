/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.spotinst.model.SpotInstConstants.GROUP_CONFIG_ELEMENT;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.AMIArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupParametersFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupParametersFetchPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStartupScriptFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStartupScriptFetchPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExceptionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExecutorParams;
import io.harness.cdng.elastigroup.config.StartupScriptOutcome;
import io.harness.cdng.elastigroup.output.ElastigroupConfigurationOutput;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupParametersFetchRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupStartupScriptFetchRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupParametersFetchResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupStartupScriptFetchResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.TaskType;

import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ElastigroupStepCommonHelper extends ElastigroupStepUtils {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private ElastigroupEntityHelper elastigroupEntityHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public ElastiGroup generateConfigFromJson(String elastiGroupJson) {
    java.lang.reflect.Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
    Gson gson = new Gson();

    // Map<"group": {...entire config...}>, this is elastiGroupConfig json that spotinst exposes
    Map<String, Object> jsonConfigMap = gson.fromJson(elastiGroupJson, mapType);
    Map<String, Object> elastiGroupConfigMap = (Map<String, Object>) jsonConfigMap.get(GROUP_CONFIG_ELEMENT);
    String groupConfigJson = gson.toJson(elastiGroupConfigMap);
    return gson.fromJson(groupConfigJson, ElastiGroup.class);
  }

  public int renderCount(ParameterField<Integer> field, int defaultValue) {
    if (field == null || field.isExpression() || field.getValue() == null) {
      return defaultValue;
    } else {
      try {
        return Integer.parseInt(field.fetchFinalValue().toString());
      } catch (NumberFormatException e) {
        log.error(format("Number format Exception while evaluating: [%s]", field.fetchFinalValue().toString()), e);
        return defaultValue;
      }
    }
  }

  public String renderExpression(Ambiance ambiance, String stringObject) {
    return engineExpressionService.renderExpression(ambiance, stringObject);
  }

  public TaskChainResponse startChainLink(
      ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters){
    OptionalOutcome startupScriptOptionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.STARTUP_SCRIPT));

    LogCallback logCallback =
        getLogCallback(ElastigroupCommandUnitConstants.FETCH_STARTUP_SCRIPT.toString(), ambiance, true);

    // Get InfrastructureOutcome
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    String startupScript = null;
    UnitProgressData unitProgressData = null;

    if (startupScriptOptionalOutcome.isFound()) {
      StartupScriptOutcome startupScriptOutcome = (StartupScriptOutcome) startupScriptOptionalOutcome.getOutcome();

      if (ManifestStoreType.HARNESS.equals(startupScriptOutcome.getStore().getKind())) {
        startupScript = fetchFilesContentFromLocalStore(ambiance, startupScriptOutcome, logCallback).get(0);
        unitProgressData = getCommandUnitProgressData(
            ElastigroupCommandUnitConstants.FETCH_STARTUP_SCRIPT.toString(), CommandExecutionStatus.SUCCESS);

        //     Render expressions for all file content fetched from Harness File Store
        if (startupScript != null) {
          startupScript = renderExpression(ambiance, startupScript);
        }
      } else if (ManifestStoreType.INLINE.equals(startupScriptOutcome.getStore().getKind())) {
        logCallback.saveExecutionLog(
            color(format("Fetching %s from Inline Store", "startupScript"), LogColor.White, LogWeight.Bold));
        startupScript = ((InlineStoreConfig) startupScriptOutcome.getStore()).extractContent();
        logCallback.saveExecutionLog("Fetched Startup Script ", INFO, CommandExecutionStatus.SUCCESS);
        unitProgressData = getCommandUnitProgressData(
            ElastigroupCommandUnitConstants.FETCH_STARTUP_SCRIPT.toString(), CommandExecutionStatus.SUCCESS);

        //     Render expressions for all file content fetched from Harness File Store
        if (isNotEmpty(startupScript)) {
          startupScript = renderExpression(ambiance, startupScript);
        }
      }
    }
    return fetchElastigroupParameters(elastigroupStepExecutor, ambiance, stepElementParameters, unitProgressData,
            startupScript, infrastructureOutcome);
  }

  public TaskChainResponse fetchElastigroupParameters(ElastigroupStepExecutor elastigroupStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, UnitProgressData unitProgressData,
      String startupScript, InfrastructureOutcome infrastructureOutcome) {
    LogCallback logCallback =
        getLogCallback(ElastigroupCommandUnitConstants.FETCH_ELASTIGROUP_JSON.toString(), ambiance, true);

    ElastigroupConfigurationOutput elastigroupConfigurationOutput = null;
    OptionalSweepingOutput optionalElastigroupConfigurationOutput =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.ELASTIGROUP_CONFIGURATION_OUTPUT));
    String elastigroupParameters = null;
    if (optionalElastigroupConfigurationOutput.isFound()) {
      elastigroupConfigurationOutput =
          (ElastigroupConfigurationOutput) optionalElastigroupConfigurationOutput.getOutput();

      StoreConfig storeConfig = elastigroupConfigurationOutput.getStoreConfig();
      if (ManifestStoreType.HARNESS.equals(storeConfig.getKind())) {
        elastigroupParameters =
                fetchElastigroupJsonFilesContentFromLocalStore(ambiance, elastigroupConfigurationOutput, logCallback).get(0);
        unitProgressData.getUnitProgresses().add(
                UnitProgress.newBuilder()
                        .setUnitName(ElastigroupCommandUnitConstants.FETCH_ELASTIGROUP_JSON.toString())
                        .setStatus(CommandExecutionStatus.SUCCESS.getUnitStatus())
                        .build());

        //     Render expressions for all file content fetched from Harness File Store
        if (elastigroupParameters != null) {
          elastigroupParameters = renderExpression(ambiance, elastigroupParameters);
        }
      } else if (ManifestStoreType.INLINE.equals(storeConfig.getKind())) {
        logCallback.saveExecutionLog(
                color(format("%nFetching %s from Inline Store", "elastigroup json"), LogColor.White, LogWeight.Bold));
        elastigroupParameters = ((InlineStoreConfig) storeConfig).extractContent();
        logCallback.saveExecutionLog("Fetched Elastigroup Json", INFO, CommandExecutionStatus.SUCCESS);
        unitProgressData.getUnitProgresses().add(
                UnitProgress.newBuilder()
                        .setUnitName(ElastigroupCommandUnitConstants.FETCH_ELASTIGROUP_JSON.toString())
                        .setStatus(CommandExecutionStatus.SUCCESS.getUnitStatus())
                        .build());

        // Render expressions for all file content fetched from Harness File Store
        if (elastigroupParameters != null) {
          elastigroupParameters = renderExpression(ambiance, elastigroupParameters);
        }
      }
    }

    if (isEmpty(elastigroupParameters)) {
      return stepFailureTaskResponseWithMessage(
              unitProgressData, "Either Store Type Not Supported For Elastigroup Json or content inside it is empty");
    }

    return executeElastigroupTask(elastigroupStepExecutor, ambiance, stepElementParameters, unitProgressData,
            startupScript, infrastructureOutcome, elastigroupParameters);
  }

  public StartupScriptOutcome resolveStartupScriptOutcome(Ambiance ambiance) {
    OptionalOutcome startupScriptOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.STARTUP_SCRIPT));

    if (!startupScriptOutcome.isFound()) {
      String stageName =
          AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
      String stepType =
          Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance)).map(StepType::getType).orElse("Elastigroup");
      throw new GeneralException(format(
          "No startupScript found in stage %s. %s step requires a startupScript defined in stage service definition",
          stageName, stepType));
    }
    return (StartupScriptOutcome) startupScriptOutcome.getOutcome();
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
      if (responseData instanceof ElastigroupStartupScriptFetchResponse) {

        ElastigroupStartupScriptFetchResponse elastigroupStartupScriptFetchResponse =
            (ElastigroupStartupScriptFetchResponse) responseData;
        ElastigroupStartupScriptFetchPassThroughData elastigroupStartupScriptFetchPassThroughData =
            (ElastigroupStartupScriptFetchPassThroughData) passThroughData;

        taskChainResponse = handleElastigroupStartupScriptFetchFilesResponse(elastigroupStartupScriptFetchResponse,
            elastigroupStepExecutor, ambiance, stepElementParameters, elastigroupStartupScriptFetchPassThroughData);
      } else if (responseData instanceof ElastigroupParametersFetchResponse) {

        ElastigroupParametersFetchResponse elastigroupParametersFetchResponse =
            (ElastigroupParametersFetchResponse) responseData;
        ElastigroupParametersFetchPassThroughData elastigroupParametersFetchPassThroughData =
            (ElastigroupParametersFetchPassThroughData) passThroughData;

        taskChainResponse = handleElastigroupParametersFetchResponse(elastigroupParametersFetchResponse,
            elastigroupStepExecutor, ambiance, stepElementParameters, elastigroupParametersFetchPassThroughData);
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

  private TaskChainResponse handleElastigroupStartupScriptFetchFilesResponse(
      ElastigroupStartupScriptFetchResponse elastigroupStartupScriptFetchResponse,
      ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      ElastigroupStartupScriptFetchPassThroughData elastigroupStartupScriptFetchPassThroughData) {
    if (elastigroupStartupScriptFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureStartupScriptFetchTask(elastigroupStartupScriptFetchResponse);
    }

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    return fetchElastigroupParameters(elastigroupStepExecutor, ambiance, stepElementParameters,
        elastigroupStartupScriptFetchResponse.getUnitProgressData(),
        elastigroupStartupScriptFetchPassThroughData.getStartupScript(), infrastructureOutcome);
  }

  private TaskChainResponse handleElastigroupParametersFetchResponse(
      ElastigroupParametersFetchResponse elastigroupParametersFetchResponse,
      ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      ElastigroupParametersFetchPassThroughData elastigroupParametersFetchPassThroughData) {
    if (elastigroupParametersFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureElastigroupParametersFetchTask(elastigroupParametersFetchResponse);
    }

    return executeElastigroupTask(elastigroupStepExecutor, ambiance, stepElementParameters,
        elastigroupParametersFetchResponse.getUnitProgressData(),
        elastigroupParametersFetchPassThroughData.getStartupScript(),
        elastigroupParametersFetchPassThroughData.getInfrastructureOutcome(),
        renderExpression(ambiance, elastigroupParametersFetchResponse.getElastigroupParameters()));
  }

  private TaskChainResponse executeElastigroupTask(ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, UnitProgressData unitProgressData, String startupScript,
      InfrastructureOutcome infrastructureOutcome, String elastigroupParameters) {
    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder()
            .lastActiveUnitProgressData(unitProgressData)
            .infrastructure(infrastructureOutcome)
            .build();

    // Get ArtifactsOutcome
    Optional<ArtifactOutcome> artifactOutcome = resolveArtifactsOutcome(ambiance);
    // Update expressions in ArtifactsOutcome

    String image = null;
    if (artifactOutcome.isPresent()) {
      AMIArtifactOutcome amiArtifactOutcome = (AMIArtifactOutcome) artifactOutcome.get();
      ExpressionEvaluatorUtils.updateExpressions(
          amiArtifactOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));
      image = amiArtifactOutcome.getAmiId();
    }
    if (isEmpty(image)) {
      return stepFailureTaskResponseWithMessage(
          unitProgressData, "AMI not available. Please specify the AMI artifact in the pipeline.");
    }

    ElastigroupStepExecutorParams elastigroupStepExecutorParams = ElastigroupStepExecutorParams.builder()
                                                                      .shouldOpenFetchFilesLogStream(false)
                                                                      .startupScript(startupScript)
                                                                      .image(image)
                                                                      .elastigroupParameters(elastigroupParameters)
                                                                      .build();

    return elastigroupStepExecutor.executeElastigroupTask(ambiance, stepElementParameters,
        elastigroupExecutionPassThroughData, unitProgressData, elastigroupStepExecutorParams);
  }

  public TaskChainResponse stepFailureTaskResponseWithMessage(UnitProgressData unitProgressData, String msg) {
    ElastigroupStepExceptionPassThroughData elastigroupStepExceptionPassThroughData =
        ElastigroupStepExceptionPassThroughData.builder().errorMessage(msg).unitProgressData(unitProgressData).build();
    return TaskChainResponse.builder().passThroughData(elastigroupStepExceptionPassThroughData).chainEnd(true).build();
  }

  private TaskChainResponse handleFailureStartupScriptFetchTask(
      ElastigroupStartupScriptFetchResponse elastigroupStartupScriptFetchResponse) {
    ElastigroupStartupScriptFetchFailurePassThroughData elastigroupStartupScriptFetchFailurePassThroughData =
        ElastigroupStartupScriptFetchFailurePassThroughData.builder()
            .errorMsg(elastigroupStartupScriptFetchResponse.getErrorMessage())
            .unitProgressData(elastigroupStartupScriptFetchResponse.getUnitProgressData())
            .build();
    return TaskChainResponse.builder()
        .passThroughData(elastigroupStartupScriptFetchFailurePassThroughData)
        .chainEnd(true)
        .build();
  }

  private TaskChainResponse handleFailureElastigroupParametersFetchTask(
      ElastigroupParametersFetchResponse elastigroupParametersFetchResponse) {
    ElastigroupParametersFetchFailurePassThroughData elastigroupParametersFetchFailurePassThroughData =
        ElastigroupParametersFetchFailurePassThroughData.builder()
            .errorMsg(elastigroupParametersFetchResponse.getErrorMessage())
            .unitProgressData(elastigroupParametersFetchResponse.getUnitProgressData())
            .build();
    return TaskChainResponse.builder()
        .passThroughData(elastigroupParametersFetchFailurePassThroughData)
        .chainEnd(true)
        .build();
  }

  String getBase64EncodedStartupScript(Ambiance ambiance, String startupScript) {
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

    final TaskRequest taskRequest =
        prepareCDTaskRequest(ambiance, taskData, kryoSerializer, elastigroupSpecParameters.getCommandUnits(), taskName,
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
}
