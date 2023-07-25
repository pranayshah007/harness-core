/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.serverless.ServerlessAwsLambdaRollbackDataOutcome.ServerlessAwsLambdaRollbackDataOutcomeBuilder;
import io.harness.cdng.serverless.beans.ServerlessAwsLambdaStepExecutorParams;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessGitFetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessS3FetchFailurePassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExceptionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExecutorParams;
import io.harness.cdng.serverless.beans.ServerlessV2ValuesYamlDataOutcome;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaPrepareRollbackV2StepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.ServerlessAwsLambdaFunctionToServerInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunctionsWithServiceName;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaPrepareRollbackDataResult;
import io.harness.delegate.beans.serverless.ServerlessS3FetchFileResult;
import io.harness.delegate.beans.serverless.StackDetails;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.serverless.ServerlessArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessArtifactType;
import io.harness.delegate.task.serverless.ServerlessDeployConfig;
import io.harness.delegate.task.serverless.ServerlessEcrArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessGitFetchFileConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfig;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.delegate.task.serverless.ServerlessS3FetchFileConfig;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessGitFetchRequest;
import io.harness.delegate.task.serverless.request.ServerlessRollbackV2Request;
import io.harness.delegate.task.serverless.request.ServerlessS3FetchRequest;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessDeployResponse;
import io.harness.delegate.task.serverless.response.ServerlessGitFetchResponse;
import io.harness.delegate.task.serverless.response.ServerlessPrepareRollbackDataResponse;
import io.harness.delegate.task.serverless.response.ServerlessS3FetchResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
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
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ECS, HarnessModuleComponent.CDS_PIPELINE,
        HarnessModuleComponent.CDS_SERVERLESS})
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class ServerlessStepCommonHelper extends ServerlessStepUtils {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private ServerlessEntityHelper serverlessEntityHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  private static final String PRIMARY_ARTIFACT_PATH_FOR_NON_ECR = "<+artifact.path>";
  private static final String PRIMARY_ARTIFACT_PATH_FOR_ECR = "<+artifact.image>";
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  private static final String ARTIFACT_ACTUAL_PATH = "harnessArtifact/artifactFile";
  private static final String SIDECAR_ARTIFACT_PATH_PREFIX = "<+artifacts.sidecars.";
  private static final String SIDECAR_ARTIFACT_FILE_NAME_PREFIX = "harnessArtifact/sidecar-artifact-";

  public TaskChainResponse startChainLink(
      Ambiance ambiance, StepElementParameters stepElementParameters, ServerlessStepHelper serverlessStepHelper) {
    ManifestsOutcome manifestsOutcome = resolveServerlessManifestsOutcome(ambiance);
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    cdExpressionResolver.updateExpressions(ambiance, manifestsOutcome);
    validateManifestsOutcome(ambiance, manifestsOutcome);
    ManifestOutcome serverlessManifestOutcome =
        getServerlessManifestOutcome(manifestsOutcome.values(), serverlessStepHelper);

    TaskChainResponse taskChainResponse = null;
    if (isGitManifest(serverlessManifestOutcome)) {
      taskChainResponse = prepareServerlessManifestGitFetchTask(
          ambiance, stepElementParameters, infrastructureOutcome, serverlessManifestOutcome, serverlessStepHelper);
    } else { // s3 store
      taskChainResponse = prepareServerlessManifestS3FetchTask(
          ambiance, stepElementParameters, infrastructureOutcome, serverlessManifestOutcome, serverlessStepHelper);
    }
    return taskChainResponse;
  }

  public List<ServerlessAwsLambdaFunction> getServerlessAwsLambdaFunctions(String instancesList)
      throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    return Arrays.asList(objectMapper.readValue(instancesList, ServerlessAwsLambdaFunction[].class));
  }

  public ServerlessAwsLambdaFunctionsWithServiceName getServerlessAwsLambdaFunctionsWithServiceName(String instances)
      throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    return objectMapper.readValue(instances, ServerlessAwsLambdaFunctionsWithServiceName.class);
  }

  public List<ServerInstanceInfo> getServerlessDeployFunctionInstanceInfo(
      List<ServerlessAwsLambdaFunction> serverlessAwsLambdaFunctions, String region, String stage, String service,
      String infraStructureKey) {
    if (EmptyPredicate.isEmpty(serverlessAwsLambdaFunctions)) {
      return Collections.emptyList();
    }
    return ServerlessAwsLambdaFunctionToServerInstanceInfoMapper.toServerInstanceInfoList(
        serverlessAwsLambdaFunctions, region, stage, service, infraStructureKey);
  }

  @NotNull
  public String convertByte64ToString(String input) {
    if (EmptyPredicate.isEmpty(input)) {
      return input;
    }
    return new String(Base64.getDecoder().decode(input));
  }

  public StackDetails getStackDetails(String stackDetailsString) throws JsonProcessingException {
    if (EmptyPredicate.isEmpty(stackDetailsString)) {
      return null;
    }
    ObjectMapper objectMapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    return objectMapper.readValue(stackDetailsString, StackDetails.class);
  }

  public TaskChainResponse executeNextLink(ServerlessStepExecutor serverlessStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier, ServerlessStepHelper serverlessStepHelper) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    ServerlessStepPassThroughData serverlessStepPassThroughData = (ServerlessStepPassThroughData) passThroughData;
    UnitProgressData unitProgressData = null;
    try {
      if (responseData instanceof ServerlessGitFetchResponse) {
        ServerlessGitFetchResponse serverlessGitFetchResponse = (ServerlessGitFetchResponse) responseData;
        return handleServerlessGitFetchFilesResponse(serverlessGitFetchResponse, serverlessStepExecutor, ambiance,
            stepElementParameters, serverlessStepPassThroughData, serverlessStepHelper);
      } else if (responseData instanceof ServerlessS3FetchResponse) {
        ServerlessS3FetchResponse serverlessS3FetchResponse = (ServerlessS3FetchResponse) responseData;
        return handleServerlessS3FetchFilesResponse(serverlessS3FetchResponse, serverlessStepExecutor, ambiance,
            stepElementParameters, serverlessStepPassThroughData, serverlessStepHelper);
      } else {
        ServerlessPrepareRollbackDataResponse serverlessPrepareRollbackDataResponse =
            (ServerlessPrepareRollbackDataResponse) responseData;
        return handleServerlessPrepareRollbackDataResponse(serverlessPrepareRollbackDataResponse,
            serverlessStepExecutor, ambiance, stepElementParameters, serverlessStepPassThroughData);
      }
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

  private TaskChainResponse prepareServerlessManifestS3FetchTask(Ambiance ambiance,
      StepElementParameters stepElementParameters, InfrastructureOutcome infrastructureOutcome,
      ManifestOutcome manifestOutcome, ServerlessStepHelper serverlessStepHelper) {
    StoreConfig storeConfig = manifestOutcome.getStore();
    if (!ManifestStoreType.S3.equals(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Serverless step", USER);
    }
    S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
    ServerlessS3FetchFileConfig serverlessS3FetchFileConfig =
        getS3FetchFilesConfig(ambiance, s3StoreConfig, manifestOutcome, serverlessStepHelper);
    ServerlessStepPassThroughData serverlessStepPassThroughData = ServerlessStepPassThroughData.builder()
                                                                      .serverlessManifestOutcome(manifestOutcome)
                                                                      .infrastructureOutcome(infrastructureOutcome)
                                                                      .build();

    return getS3FetchFileTaskResponse(
        ambiance, true, stepElementParameters, serverlessStepPassThroughData, serverlessS3FetchFileConfig);
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
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, serverlessSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(serverlessSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  public TaskChainResponse queueServerlessTaskWithTaskType(StepElementParameters stepElementParameters,
      ServerlessRollbackV2Request serverlessCommandRequest, Ambiance ambiance, PassThroughData passThroughData,
      boolean isChainEnd, TaskType taskType) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {serverlessCommandRequest})
                            .taskType(taskType.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();
    String taskName = TaskType.SERVERLESS_COMMAND_TASK.getDisplayName();
    ServerlessSpecParameters serverlessSpecParameters = (ServerlessSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, serverlessSpecParameters.getCommandUnits(), taskName,
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

    return prepareServerlessPrepareRollbackTask(manifestFilePathContent, serverlessStepExecutor, ambiance,
        stepElementParameters, serverlessStepPassThroughData, serverlessStepHelper,
        serverlessGitFetchResponse.getUnitProgressData());
  }

  private TaskChainResponse prepareServerlessPrepareRollbackTask(Optional<Pair<String, String>> manifestFilePathContent,
      ServerlessStepExecutor serverlessStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      ServerlessStepPassThroughData serverlessStepPassThroughData, ServerlessStepHelper serverlessStepHelper,
      UnitProgressData unitProgressData) {
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
    ServerlessFetchFileOutcome serverlessFetchFileOutcome =
        ServerlessFetchFileOutcome.builder()
            .manifestFilePathContent(manifestFilePathContent.get())
            .manifestFileOverrideContent(manifestFileOverrideContent)
            .build();
    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.SERVERLESS_FETCH_FILE_OUTCOME,
        serverlessFetchFileOutcome, StepOutcomeGroup.STEP.name());
    ServerlessStepExecutorParams serverlessStepExecutorParams;
    if (serverlessStepExecutor instanceof ServerlessAwsLambdaDeployStep) {
      serverlessStepExecutorParams = ServerlessAwsLambdaStepExecutorParams.builder()
                                         .shouldOpenFetchFilesLogStream(false)
                                         .manifestFilePathContent(manifestFilePathContent.get())
                                         .manifestFileOverrideContent(manifestFileOverrideContent)
                                         .build();
    } else {
      throw new UnsupportedOperationException(
          format("Unsupported serverless step executer: [%s]", serverlessStepExecutor.getClass()));
    }
    return serverlessStepExecutor.executeServerlessPrepareRollbackTask(
        serverlessStepPassThroughData.getServerlessManifestOutcome(), ambiance, stepElementParameters,
        serverlessStepPassThroughData, unitProgressData, serverlessStepExecutorParams);
  }

  private TaskChainResponse handleServerlessS3FetchFilesResponse(ServerlessS3FetchResponse serverlessS3FetchResponse,
      ServerlessStepExecutor serverlessStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      ServerlessStepPassThroughData serverlessStepPassThroughData, ServerlessStepHelper serverlessStepHelper) {
    if (serverlessS3FetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      ServerlessS3FetchFailurePassThroughData serverlessS3FetchFailurePassThroughData =
          ServerlessS3FetchFailurePassThroughData.builder()
              .errorMsg(serverlessS3FetchResponse.getErrorMessage())
              .unitProgressData(serverlessS3FetchResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder()
          .passThroughData(serverlessS3FetchFailurePassThroughData)
          .chainEnd(true)
          .build();
    }

    ServerlessS3FetchFileResult serverlessS3FetchFileResult =
        serverlessS3FetchResponse.getServerlessS3FetchFileResult();
    Optional<Pair<String, String>> manifestFilePathContent = Optional.of(
        ImmutablePair.of(serverlessS3FetchFileResult.getFilePath(), serverlessS3FetchFileResult.getFileContent()));

    return prepareServerlessPrepareRollbackTask(manifestFilePathContent, serverlessStepExecutor, ambiance,
        stepElementParameters, serverlessStepPassThroughData, serverlessStepHelper,
        serverlessS3FetchResponse.getUnitProgressData());
  }

  private TaskChainResponse handleServerlessPrepareRollbackDataResponse(
      ServerlessPrepareRollbackDataResponse serverlessPrepareRollbackDataResponse,
      ServerlessStepExecutor serverlessStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      ServerlessStepPassThroughData serverlessStepPassThroughData) {
    if (serverlessPrepareRollbackDataResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      ServerlessStepExceptionPassThroughData serverlessStepExceptionPassThroughData =
          ServerlessStepExceptionPassThroughData.builder()
              .errorMessage(serverlessPrepareRollbackDataResponse.getErrorMessage())
              .unitProgressData(serverlessPrepareRollbackDataResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().passThroughData(serverlessStepExceptionPassThroughData).chainEnd(true).build();
    }
    ServerlessAwsLambdaDeployStepParameters deployStepParameters =
        (ServerlessAwsLambdaDeployStepParameters) stepElementParameters.getSpec();
    OptionalSweepingOutput serverlessFetchFileOptionalOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutcomeExpressionConstants.SERVERLESS_FETCH_FILE_OUTCOME));
    if (!serverlessFetchFileOptionalOutput.isFound()) {
      throw new GeneralException("Found Null Manifest Content from last serverless git fetch task");
    }
    ServerlessFetchFileOutcome serverlessFetchFileOutcome =
        (ServerlessFetchFileOutcome) serverlessFetchFileOptionalOutput.getOutput();
    ServerlessExecutionPassThroughData serverlessExecutionPassThroughData =
        ServerlessExecutionPassThroughData.builder()
            .infrastructure(serverlessStepPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(serverlessPrepareRollbackDataResponse.getUnitProgressData())
            .build();
    ServerlessStepExecutorParams serverlessStepExecutorParams;
    if (serverlessStepExecutor instanceof ServerlessAwsLambdaDeployStep) {
      ServerlessAwsLambdaPrepareRollbackDataResult serverlessAwsLambdaPrepareRollbackDataResult =
          (ServerlessAwsLambdaPrepareRollbackDataResult)
              serverlessPrepareRollbackDataResponse.getServerlessPrepareRollbackDataResult();
      ServerlessAwsLambdaRollbackDataOutcomeBuilder serverlessRollbackDataOutcomeBuilder =
          ServerlessAwsLambdaRollbackDataOutcome.builder();
      serverlessRollbackDataOutcomeBuilder.previousVersionTimeStamp(
          serverlessAwsLambdaPrepareRollbackDataResult.getPreviousVersionTimeStamp());
      serverlessRollbackDataOutcomeBuilder.isFirstDeployment(
          serverlessAwsLambdaPrepareRollbackDataResult.isFirstDeployment());
      executionSweepingOutputService.consume(ambiance,
          OutcomeExpressionConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK_DATA_OUTCOME,
          serverlessRollbackDataOutcomeBuilder.build(), StepOutcomeGroup.STEP.name());
      serverlessStepExecutorParams =
          ServerlessAwsLambdaStepExecutorParams.builder()
              .shouldOpenFetchFilesLogStream(false)
              .manifestFilePathContent(serverlessFetchFileOutcome.getManifestFilePathContent())
              .manifestFileOverrideContent(serverlessFetchFileOutcome.getManifestFileOverrideContent())
              .build();
    } else {
      throw new UnsupportedOperationException(
          format("Unsupported serverless step executer: [%s]", serverlessStepExecutor.getClass()));
    }
    return serverlessStepExecutor.executeServerlessTask(serverlessStepPassThroughData.getServerlessManifestOutcome(),
        ambiance, stepElementParameters, serverlessExecutionPassThroughData,
        serverlessPrepareRollbackDataResponse.getUnitProgressData(), serverlessStepExecutorParams);
  }

  public StepResponse handleGitTaskFailure(ServerlessGitFetchFailurePassThroughData serverlessGitFetchResponse) {
    UnitProgressData unitProgressData = serverlessGitFetchResponse.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(serverlessGitFetchResponse.getErrorMsg()).build())
        .build();
  }

  public StepResponse handleS3TaskFailure(ServerlessS3FetchFailurePassThroughData serverlessS3FetchResponse) {
    UnitProgressData unitProgressData = serverlessS3FetchResponse.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(serverlessS3FetchResponse.getErrorMsg()).build())
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
                         .setErrorMessage(ServerlessStepCommonHelper.getErrorMessage(serverlessCommandResponse))
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
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, serverlessSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(serverlessSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(serverlessStepPassThroughData)
        .build();
  }

  private TaskChainResponse getS3FetchFileTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
      StepElementParameters stepElementParameters, ServerlessStepPassThroughData serverlessStepPassThroughData,
      ServerlessS3FetchFileConfig serverlessS3FetchFileConfig) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    ServerlessS3FetchRequest serverlessS3FetchRequest = ServerlessS3FetchRequest.builder()
                                                            .accountId(accountId)
                                                            .serverlessS3FetchFileConfig(serverlessS3FetchFileConfig)
                                                            .shouldOpenLogStream(shouldOpenLogStream)
                                                            .build();
    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.SERVERLESS_S3_FETCH_TASK_NG.name())
                                  .parameters(new Object[] {serverlessS3FetchRequest})
                                  .build();
    String taskName = TaskType.SERVERLESS_S3_FETCH_TASK_NG.getDisplayName();
    ServerlessSpecParameters serverlessSpecParameters = (ServerlessSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, serverlessSpecParameters.getCommandUnits(), taskName,
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

  private ServerlessS3FetchFileConfig getS3FetchFilesConfig(Ambiance ambiance, S3StoreConfig s3StoreConfig,
      ManifestOutcome manifestOutcome, ServerlessStepHelper serverlessStepHelper) {
    return ServerlessS3FetchFileConfig.builder()
        .s3StoreDelegateConfig(getS3StoreDelegateConfig(ambiance, s3StoreConfig, manifestOutcome))
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
    return cdExpressionResolver.renderExpression(ambiance, manifestFileContent);
  }

  public ServerlessInfraConfig getServerlessInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return serverlessEntityHelper.getServerlessInfraConfig(infrastructure, ngAccess);
  }

  public ServerlessDeployConfig getServerlessDeployConfig(
      ServerlessSpecParameters serverlessDeployStepParameters, ServerlessStepHelper serverlessStepHelper) {
    return serverlessStepHelper.getServerlessDeployConfig(serverlessDeployStepParameters);
  }

  public ServerlessManifestConfig getServerlessManifestConfig(Map<String, Object> manifestParams,
      ManifestOutcome serverlessManifestOutcome, Ambiance ambiance, ServerlessStepHelper serverlessStepHelper) {
    return serverlessStepHelper.getServerlessManifestConfig(serverlessManifestOutcome, ambiance, manifestParams);
  }

  public List<ServerInstanceInfo> getFunctionInstanceInfo(ServerlessCommandResponse serverlessCommandResponse,
      ServerlessStepHelper serverlessStepHelper, String infraStructureKey) {
    if (serverlessCommandResponse instanceof ServerlessDeployResponse) {
      ServerlessDeployResponse serverlessDeployResponse = (ServerlessDeployResponse) serverlessCommandResponse;
      return serverlessStepHelper.getServerlessDeployFunctionInstanceInfo(
          serverlessDeployResponse.getServerlessDeployResult(), infraStructureKey);
    }
    throw new GeneralException("Invalid serverless command response instance");
  }

  private ConnectorInfoDTO getConnectorDTO(String connectorId, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return serverlessEntityHelper.getConnectorInfoDTO(connectorId, ngAccess);
  }

  public void verifyPluginImageIsProvider(ParameterField<String> image) {
    if (ParameterField.isNull(image) || image.getValue() == null) {
      throw new InvalidRequestException("Plugin Image must be provided");
    }
  }

  public void putValuesYamlEnvVars(Ambiance ambiance,
      ServerlessAwsLambdaPrepareRollbackV2StepParameters serverlessAwsLambdaPrepareRollbackV2StepParameters,
      Map<String, String> envVarMap) {
    OptionalSweepingOutput serverlessValuesYamlDataOptionalOutput =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                serverlessAwsLambdaPrepareRollbackV2StepParameters.getDownloadManifestsFqn() + "."
                + OutcomeExpressionConstants.SERVERLESS_VALUES_YAML_DATA_OUTCOME));

    if (serverlessValuesYamlDataOptionalOutput.isFound()) {
      ServerlessV2ValuesYamlDataOutcome serverlessV2ValuesYamlDataOutcome =
          (ServerlessV2ValuesYamlDataOutcome) serverlessValuesYamlDataOptionalOutput.getOutput();

      String valuesYamlContent = serverlessV2ValuesYamlDataOutcome.getValuesYamlContent();
      String valuesYamlPath = serverlessV2ValuesYamlDataOutcome.getValuesYamlPath();

      if (StringUtils.isNotBlank(valuesYamlContent) && StringUtils.isNotBlank(valuesYamlPath)) {
        envVarMap.put("PLUGIN_VALUES_YAML_CONTENT", valuesYamlContent);
        envVarMap.put("PLUGIN_VALUES_YAML_FILE_PATH", valuesYamlPath);
      }
    }
  }
}
