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
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static java.lang.String.format;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStartupScriptFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStartupScriptFetchPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExceptionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExecutorParams;
import io.harness.cdng.elastigroup.config.StartupScriptOutcome;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.elastigroup.ElastigroupSetupResult;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupStartupScriptFetchRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupStartupScriptFetchResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.logging.LogCallback;
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
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ElastigroupStepCommonHelper extends ElastigroupStepUtils {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private ElastigroupEntityHelper elastigroupEntityHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public TaskChainResponse startChainLink(
      ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters) {
    // Get ManifestsOutcome
    Optional<ArtifactOutcome> artifactOutcome = resolveArtifactsOutcome(ambiance);

    OptionalOutcome startupScriptOptionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.STARTUP_SCRIPT));

    LogCallback logCallback =
        getLogCallback(ElastigroupCommandUnitConstants.fetchStartupScript.toString(), ambiance, true);

    String startupScript = null;

    if (startupScriptOptionalOutcome.isFound()) {
      StartupScriptOutcome startupScriptOutcome = (StartupScriptOutcome) startupScriptOptionalOutcome.getOutcome();

      if (ManifestStoreType.HARNESS.equals(startupScriptOutcome.getStore().getKind())) {
        startupScript = fetchFilesContentFromLocalStore(ambiance, startupScriptOutcome, logCallback).get(0);
      } else if (ManifestStoreType.INLINE.equals(startupScriptOutcome.getStore().getKind())) {
        startupScript = ((InlineStoreConfig) startupScriptOutcome.getStore()).extractContent();
      }
    }

    // Get InfrastructureOutcome
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    // Update expressions in ManifestsOutcome
    ExpressionEvaluatorUtils.updateExpressions(
        artifactOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

    return prepareStartupScriptFetchTask(
        elastigroupStepExecutor, ambiance, stepElementParameters, infrastructureOutcome, startupScript);
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

  private TaskChainResponse prepareStartupScriptFetchTask(ElastigroupStepExecutor elastigroupStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters, InfrastructureOutcome infrastructureOutcome,
      String startupScript) {
    //     Render expressions for all file content fetched from Harness File Store
    if (startupScript != null) {
      startupScript = engineExpressionService.renderExpression(ambiance, startupScript);
    }

    ElastigroupStartupScriptFetchPassThroughData elastigroupStartupScriptFetchPassThroughData =
        ElastigroupStartupScriptFetchPassThroughData.builder()
            .infrastructureOutcome(infrastructureOutcome)
            .startupScript(startupScript)
            .build();

    return getElastigroupStartupScriptTaskResponse(
        ambiance, false, stepElementParameters, elastigroupStartupScriptFetchPassThroughData);
  }

  private TaskChainResponse getElastigroupStartupScriptTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
      StepElementParameters stepElementParameters,
      ElastigroupStartupScriptFetchPassThroughData elastigroupStartupScriptFetchPassThroughData) {
    String accountId = AmbianceUtils.getAccountId(ambiance);

    ElastigroupStartupScriptFetchRequest elastigroupStartupScriptFetchRequest =
        ElastigroupStartupScriptFetchRequest.builder()
            .accountId(accountId)
            .shouldOpenLogStream(shouldOpenLogStream)
            .startupScript(elastigroupStartupScriptFetchPassThroughData.getStartupScript())
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.ELASTIGROUP_STARTUP_SCRIPT_FETCH_RUN_TASK_NG.name())
                                  .parameters(new Object[] {elastigroupStartupScriptFetchRequest})
                                  .build();

    String taskName = TaskType.ELASTIGROUP_STARTUP_SCRIPT_FETCH_RUN_TASK_NG.getDisplayName();

    ElastigroupSpecParameters elastigroupSpecParameters = (ElastigroupSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest =
        prepareCDTaskRequest(ambiance, taskData, kryoSerializer, elastigroupSpecParameters.getCommandUnits(), taskName,
            TaskSelectorYaml.toTaskSelector(
                emptyIfNull(getParameterFieldValue(elastigroupSpecParameters.getDelegateSelectors()))),
            stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(elastigroupStartupScriptFetchPassThroughData)
        .build();
  }

  public TaskChainResponse executeNextLink(ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    UnitProgressData unitProgressData = null;
    TaskChainResponse taskChainResponse = null;
    try {
      if (responseData
          instanceof ElastigroupStartupScriptFetchResponse) { // if ElastigroupStartupScriptFetchResponse is received

        ElastigroupStartupScriptFetchResponse elastigroupStartupScriptFetchResponse =
            (ElastigroupStartupScriptFetchResponse) responseData;
        ElastigroupStartupScriptFetchPassThroughData elastigroupStartupScriptFetchPassThroughData =
            (ElastigroupStartupScriptFetchPassThroughData) passThroughData;

        taskChainResponse = handleElastigroupStartupScriptFetchFilesResponse(elastigroupStartupScriptFetchResponse,
            elastigroupStepExecutor, ambiance, stepElementParameters, elastigroupStartupScriptFetchPassThroughData);
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

    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
        ElastigroupExecutionPassThroughData.builder()
            .infrastructure(elastigroupStartupScriptFetchPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(elastigroupStartupScriptFetchResponse.getUnitProgressData())
            .build();

    ElastigroupStepExecutorParams elastigroupStepExecutorParams =
        ElastigroupStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .startupScript(elastigroupStartupScriptFetchPassThroughData.getStartupScript())
            .build();

    return elastigroupStepExecutor.executeElastigroupTask(ambiance, stepElementParameters,
        elastigroupExecutionPassThroughData, elastigroupStartupScriptFetchResponse.getUnitProgressData(),
        elastigroupStepExecutorParams);
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
}
