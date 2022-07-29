/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.serverless.ServerlessAwsLambdaRollbackDataOutcome.ServerlessAwsLambdaRollbackDataOutcomeBuilder;
import io.harness.cdng.serverless.beans.*;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaPrepareRollbackDataResult;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.serverless.*;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessGitFetchRequest;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessDeployResponse;
import io.harness.delegate.task.serverless.response.ServerlessGitFetchResponse;
import io.harness.delegate.task.serverless.response.ServerlessPrepareRollbackDataResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.git.model.FetchFilesResult;
import io.harness.logging.CommandExecutionStatus;
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
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;
import software.wings.beans.TaskType;

import java.util.*;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;
import static java.lang.String.format;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class ServerlessGenericStepCommonHelper extends ServerlessStepUtils {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private ServerlessEntityHelper serverlessEntityHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  private static final String PRIMARY_ARTIFACT_PATH_FOR_NON_ECR = "<+artifact.path>";
  private static final String PRIMARY_ARTIFACT_PATH_FOR_ECR = "<+artifact.image>";
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  private static final String ARTIFACT_ACTUAL_PATH = "harnessArtifact/artifactFile";
  private static final String SIDECAR_ARTIFACT_PATH_PREFIX = "<+sidecar.artifact.";
  private static final String SIDECAR_ARTIFACT_FILE_NAME_PREFIX = "harnessArtifact/sidecar-artifact-";

  public TaskChainResponse startChainLink(
      Ambiance ambiance, StepElementParameters stepElementParameters, ServerlessStepHelper serverlessStepHelper) {
    ManifestsOutcome manifestsOutcome = resolveServerlessManifestsOutcome(ambiance);
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    ExpressionEvaluatorUtils.updateExpressions(
        manifestsOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));
    validateManifestsOutcome(ambiance, manifestsOutcome);
    ManifestOutcome serverlessManifestOutcome =
        getServerlessManifestOutcome(manifestsOutcome.values(), serverlessStepHelper);
    return prepareServerlessManifestGitFetchTask(
        ambiance, stepElementParameters, infrastructureOutcome, serverlessManifestOutcome, serverlessStepHelper);
  }

  public TaskChainResponse executeNextLink(ServerlessStepExecutor serverlessStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier, ServerlessStepHelper serverlessStepHelper) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    ServerlessStepPassThroughData serverlessStepPassThroughData = (ServerlessStepPassThroughData) passThroughData;
    UnitProgressData unitProgressData = null;
    try {
      ServerlessGitFetchResponse serverlessGitFetchResponse = (ServerlessGitFetchResponse) responseData;
      return handleServerlessGitFetchFilesResponse(serverlessGitFetchResponse, serverlessStepExecutor, ambiance,
              stepElementParameters, serverlessStepPassThroughData, serverlessStepHelper);
    } catch (Exception e) {
      return TaskChainResponse.builder()
          .chainEnd(true)
          .passThroughData(ServerlessStepExceptionPassThroughData.builder()
                               .errorMessage(ExceptionUtils.getMessage(e))
                               .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
                               .build())
          .build();
    }
  }

  public ServerlessArtifactConfig getArtifactConfig(ArtifactOutcome artifactOutcome, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return serverlessEntityHelper.getServerlessArtifactConfig(artifactOutcome, ngAccess);
  }

  public ManifestsOutcome resolveServerlessManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    if (!manifestsOutcome.isFound()) {
      String stageName =
          AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
      String stepType =
          Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance)).map(StepType::getType).orElse("Serverless");
      throw new GeneralException(
          format("No manifests found in stage %s. %s step requires a manifest defined in stage service definition",
              stageName, stepType));
    }
    return (ManifestsOutcome) manifestsOutcome.getOutcome();
  }

  public ManifestOutcome getServerlessManifestOutcome(
      @NotEmpty Collection<ManifestOutcome> manifestOutcomes, ServerlessStepHelper serverlessStepHelper) {
    return serverlessStepHelper.getServerlessManifestOutcome(manifestOutcomes);
  }

  private TaskChainResponse prepareServerlessManifestGitFetchTask(Ambiance ambiance,
      StepElementParameters stepElementParameters, InfrastructureOutcome infrastructureOutcome,
      ManifestOutcome manifestOutcome, ServerlessStepHelper serverlessStepHelper) {
    StoreConfig storeConfig = manifestOutcome.getStore();
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Serverless step", USER);
    }
    ServerlessGitFetchFileConfig serverlessGitFetchFileConfig =
        getGitFetchFilesConfig(ambiance, gitStoreConfig, manifestOutcome, serverlessStepHelper);
    ServerlessStepPassThroughData serverlessStepPassThroughData = ServerlessStepPassThroughData.builder()
                                                                      .serverlessManifestOutcome(manifestOutcome)
                                                                      .infrastructureOutcome(infrastructureOutcome)
                                                                      .build();
    return getGitFetchFileTaskResponse(
        ambiance, true, stepElementParameters, serverlessStepPassThroughData, serverlessGitFetchFileConfig);
  }

  public TaskChainResponse queueServerlessTask(StepElementParameters stepElementParameters,
      ServerlessCommandRequest serverlessCommandRequest, Ambiance ambiance, PassThroughData passThroughData,
      boolean isChainEnd) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {serverlessCommandRequest})
                            .taskType(TaskType.SERVERLESS_COMMAND_TASK.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();
    String taskName =
        TaskType.SERVERLESS_COMMAND_TASK.getDisplayName() + " : " + serverlessCommandRequest.getCommandName();
    ServerlessSpecParameters serverlessSpecParameters = (ServerlessSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest =
        prepareCDTaskRequest(ambiance, taskData, kryoSerializer, serverlessSpecParameters.getCommandUnitsForGenericStep(), taskName,
            TaskSelectorYaml.toTaskSelector(
                emptyIfNull(getParameterFieldValue(serverlessSpecParameters.getDelegateSelectors()))),
            stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  private TaskChainResponse handleServerlessGitFetchFilesResponse(ServerlessGitFetchResponse serverlessGitFetchResponse,
      ServerlessStepExecutor serverlessStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      ServerlessStepPassThroughData serverlessStepPassThroughData, ServerlessStepHelper serverlessStepHelper) {
    if (serverlessGitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      ServerlessGitFetchFailurePassThroughData serverlessGitFetchFailurePassThroughData =
          ServerlessGitFetchFailurePassThroughData.builder()
              .errorMsg(serverlessGitFetchResponse.getErrorMessage())
              .unitProgressData(serverlessGitFetchResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder()
          .passThroughData(serverlessGitFetchFailurePassThroughData)
          .chainEnd(true)
          .build();
    }
    Map<String, FetchFilesResult> fetchFilesResultMap = serverlessGitFetchResponse.getFilesFromMultipleRepo();
    Optional<Pair<String, String>> manifestFilePathContent = getManifestFileContent(
        fetchFilesResultMap, serverlessStepPassThroughData.getServerlessManifestOutcome(), serverlessStepHelper);
    if (!manifestFilePathContent.isPresent()) {
      throw new GeneralException("Found No Manifest Content from serverless git fetch task");
    }

    ServerlessArtifactConfig serverlessArtifactConfig = null;
    Optional<ArtifactsOutcome> artifactsOutcome = getArtifactsOutcome(ambiance);
    Map<String, ServerlessArtifactConfig> sidecarServerlessArtifactConfigMap = new HashMap<>();
    if (artifactsOutcome.isPresent()) {
      if (artifactsOutcome.get().getPrimary() != null) {
        serverlessArtifactConfig = getArtifactConfig(artifactsOutcome.get().getPrimary(), ambiance);
      }
      if (artifactsOutcome.get().getSidecars() != null) {
        artifactsOutcome.get().getSidecars().forEach((key, value) -> {
          if (value != null) {
            sidecarServerlessArtifactConfigMap.put(key, getArtifactConfig(value, ambiance));
          }
        });
      }
    }

    String manifestFileOverrideContent = renderManifestContent(ambiance, manifestFilePathContent.get().getValue(),
        serverlessArtifactConfig, sidecarServerlessArtifactConfigMap);
    ServerlessGitFetchOutcome serverlessGitFetchOutcome = ServerlessGitFetchOutcome.builder()
                                                              .manifestFilePathContent(manifestFilePathContent.get())
                                                              .manifestFileOverrideContent(manifestFileOverrideContent)
                                                              .build();
    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.SERVERLESS_GIT_FETCH_OUTCOME,
        serverlessGitFetchOutcome, StepOutcomeGroup.STEP.name());
    ServerlessStepExecutorParams serverlessStepExecutorParams;
    if (serverlessStepExecutor instanceof ServerlessAwsLambdaGenericStep) {
      serverlessStepExecutorParams = ServerlessAwsLambdaStepExecutorParams.builder()
              .shouldOpenFetchFilesLogStream(false)
              .manifestFilePathContent(manifestFilePathContent.get())
              .manifestFileOverrideContent(manifestFileOverrideContent)
              .build();
    } else {
      throw new UnsupportedOperationException(
          format("Unsupported serverless step executer: [%s]", serverlessStepExecutor.getClass()));
    }
    return serverlessStepExecutor.executeServerlessTask(
        serverlessStepPassThroughData.getServerlessManifestOutcome(), ambiance, stepElementParameters,
        serverlessStepPassThroughData, serverlessGitFetchResponse.getUnitProgressData(), serverlessStepExecutorParams);
  }

  public StepResponse handleGitTaskFailure(ServerlessGitFetchFailurePassThroughData serverlessGitFetchResponse) {
    UnitProgressData unitProgressData = serverlessGitFetchResponse.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(serverlessGitFetchResponse.getErrorMsg()).build())
        .build();
  }

  public StepResponse handleStepExceptionFailure(ServerlessStepExceptionPassThroughData stepException) {
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
      Ambiance ambiance, ServerlessExecutionPassThroughData executionPassThroughData, Exception e) throws Exception {
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
      ServerlessCommandResponse serverlessCommandResponse, StepResponseBuilder stepResponseBuilder) {
    stepResponseBuilder.status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .setErrorMessage(ServerlessGenericStepCommonHelper.getErrorMessage(serverlessCommandResponse))
                         .build());
    return stepResponseBuilder;
  }

  public static String getErrorMessage(ServerlessCommandResponse serverlessCommandResponse) {
    return serverlessCommandResponse.getErrorMessage() == null ? "" : serverlessCommandResponse.getErrorMessage();
  }

  private Optional<Pair<String, String>> getManifestFileContent(Map<String, FetchFilesResult> fetchFilesResultMap,
      ManifestOutcome manifestOutcome, ServerlessStepHelper serverlessStepHelper) {
    return serverlessStepHelper.getManifestFileContent(fetchFilesResultMap, manifestOutcome);
  }

  private TaskChainResponse getGitFetchFileTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
      StepElementParameters stepElementParameters, ServerlessStepPassThroughData serverlessStepPassThroughData,
      ServerlessGitFetchFileConfig serverlessGitFetchFilesConfig) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    ServerlessGitFetchRequest serverlessGitFetchRequest =
        ServerlessGitFetchRequest.builder()
            .accountId(accountId)
            .serverlessGitFetchFileConfig(serverlessGitFetchFilesConfig)
            .shouldOpenLogStream(shouldOpenLogStream)
            .build();
    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.SERVERLESS_GIT_FETCH_TASK_NG.name())
                                  .parameters(new Object[] {serverlessGitFetchRequest})
                                  .build();
    String taskName = TaskType.SERVERLESS_GIT_FETCH_TASK_NG.getDisplayName();
    ServerlessSpecParameters serverlessSpecParameters = (ServerlessSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest =
        prepareCDTaskRequest(ambiance, taskData, kryoSerializer, serverlessSpecParameters.getCommandUnitsForGenericStep(), taskName,
            TaskSelectorYaml.toTaskSelector(
                emptyIfNull(getParameterFieldValue(serverlessSpecParameters.getDelegateSelectors()))),
            stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(serverlessStepPassThroughData)
        .build();
  }

  private ServerlessGitFetchFileConfig getGitFetchFilesConfig(Ambiance ambiance, GitStoreConfig gitStoreConfig,
      ManifestOutcome manifestOutcome, ServerlessStepHelper serverlessStepHelper) {
    return ServerlessGitFetchFileConfig.builder()
        .gitStoreDelegateConfig(getGitStoreDelegateConfig(ambiance, gitStoreConfig, manifestOutcome))
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .configOverridePath(serverlessStepHelper.getConfigOverridePath(manifestOutcome))
        .succeedIfFileNotFound(false)
        .build();
  }

  public String renderManifestContent(Ambiance ambiance, String manifestFileContent,
      ServerlessArtifactConfig serverlessArtifactConfig,
      Map<String, ServerlessArtifactConfig> sidecarServerlessArtifactConfigMap) {
    if (isEmpty(manifestFileContent)) {
      return manifestFileContent;
    }
    if (serverlessArtifactConfig != null
        && serverlessArtifactConfig.getServerlessArtifactType().equals(ServerlessArtifactType.ECR)) {
      manifestFileContent = manifestFileContent.replace(
          PRIMARY_ARTIFACT_PATH_FOR_ECR, ((ServerlessEcrArtifactConfig) serverlessArtifactConfig).getImage());
    } else if (manifestFileContent.contains(PRIMARY_ARTIFACT_PATH_FOR_NON_ECR)) {
      manifestFileContent = manifestFileContent.replace(PRIMARY_ARTIFACT_PATH_FOR_NON_ECR, ARTIFACT_ACTUAL_PATH);
    }

    for (Map.Entry<String, ServerlessArtifactConfig> entry : sidecarServerlessArtifactConfigMap.entrySet()) {
      String identifier = SIDECAR_ARTIFACT_PATH_PREFIX + entry.getKey() + ">";
      if (manifestFileContent.contains(identifier)) {
        if (entry.getValue().getServerlessArtifactType().equals(ServerlessArtifactType.ECR)) {
          manifestFileContent =
              manifestFileContent.replace(identifier, ((ServerlessEcrArtifactConfig) entry.getValue()).getImage());
        } else if (entry.getValue().getServerlessArtifactType().equals(ServerlessArtifactType.ARTIFACTORY)
            || entry.getValue().getServerlessArtifactType().equals(ServerlessArtifactType.AMAZON_S3)) {
          manifestFileContent =
              manifestFileContent.replace(identifier, SIDECAR_ARTIFACT_FILE_NAME_PREFIX + entry.getKey());
        }
      }
    }
    return engineExpressionService.renderExpression(ambiance, manifestFileContent);
  }

  public ServerlessInfraConfig getServerlessInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return serverlessEntityHelper.getServerlessInfraConfig(infrastructure, ngAccess);
  }

  public ServerlessManifestConfig getServerlessManifestConfig(Map<String, Object> manifestParams,
      ManifestOutcome serverlessManifestOutcome, Ambiance ambiance, ServerlessStepHelper serverlessStepHelper) {
    return serverlessStepHelper.getServerlessManifestConfig(serverlessManifestOutcome, ambiance, manifestParams);
  }
}
