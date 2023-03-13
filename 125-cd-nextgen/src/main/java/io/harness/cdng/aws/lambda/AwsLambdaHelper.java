/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.lambda;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.aws.v2.lambda.AwsLambdaCommandUnitConstants;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.aws.lambda.beans.AwsLambdaHarnessStoreFilesResult;
import io.harness.cdng.aws.lambda.beans.AwsLambdaPrepareRollbackOutcome;
import io.harness.cdng.aws.lambda.beans.AwsLambdaStepOutcome;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.mappers.ManifestOutcomeValidator;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.AwsLambdaToServerInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.aws.lambda.AwsLambda;
import io.harness.delegate.task.aws.lambda.AwsLambdaCommandTypeNG;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaInfraConfig;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaCommandRequest;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaDeployRequest;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaPrepareRollbackRequest;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaCommandResponse;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaPrepareRollbackResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.gitcommon.GitFetchFilesResult;
import io.harness.delegate.task.gitcommon.GitRequestFileConfig;
import io.harness.delegate.task.gitcommon.GitTaskNGRequest;
import io.harness.delegate.task.gitcommon.GitTaskNGResponse;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
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
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

@Slf4j
public class AwsLambdaHelper extends CDStepHelper {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private AwsLambdaEntityHelper awsLambdaEntityHelper;

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;

  @Inject private PipelineRbacHelper pipelineRbacHelper;

  private final String AWS_LAMBDA_PREPARE_ROLLBACK_COMMAND_NAME = "PrepareRollbackAwsLambda";
  private final String AWS_LAMBDA_DEPLOY_COMMAND_NAME = "DeployAwsLambda";

  public AwsLambdaFunctionsInfraConfig getInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    return awsLambdaEntityHelper.getInfraConfig(infrastructure, AmbianceUtils.getNgAccess(ambiance));
  }

  public TaskChainResponse queueTask(StepElementParameters stepElementParameters,
      AwsLambdaCommandRequest awsLambdaCommandRequest, TaskType taskType, Ambiance ambiance,
      PassThroughData passThroughData, boolean isChainEnd) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {awsLambdaCommandRequest})
                            .taskType(taskType.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();
    String taskName = taskType.getDisplayName() + " : " + awsLambdaCommandRequest.getCommandName();
    AwsLambdaSpecParameters awsLambdaSpecParameters = (AwsLambdaSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, awsLambdaSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(awsLambdaSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  public List<ManifestOutcome> getAwsLambdaManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes) {
    // Filter only Aws Lambda supported manifest types
    return manifestOutcomes.stream()
        .filter(manifestOutcome -> ManifestType.AWS_LAMBDA_SUPPORTED_MANIFEST_TYPES.contains(manifestOutcome.getType()))
        .collect(Collectors.toList());
  }

  public TaskChainResponse executeNextLink(Ambiance ambiance, StepElementParameters stepElementParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();

    AwsLambdaStepPassThroughData awsLambdaStepPassThroughData = (AwsLambdaStepPassThroughData) passThroughData;

    UnitProgressData unitProgressData = null;
    TaskChainResponse taskChainResponse = null;

    try {
      if (responseData instanceof GitTaskNGResponse) {
        GitTaskNGResponse gitTaskResponse = (GitTaskNGResponse) responseData;
        taskChainResponse =
            handleGitFetchFilesResponse(ambiance, stepElementParameters, gitTaskResponse, awsLambdaStepPassThroughData);
      } else if (responseData instanceof AwsLambdaPrepareRollbackResponse) {
        AwsLambdaPrepareRollbackResponse awsLambdaPrepareRollbackResponse =
            (AwsLambdaPrepareRollbackResponse) responseData;
        taskChainResponse = handlePrepareRollbackDataResponse(
            awsLambdaPrepareRollbackResponse, ambiance, stepElementParameters, awsLambdaStepPassThroughData);
      }
    } catch (Exception e) {
      return TaskChainResponse.builder()
          .chainEnd(true)
          .passThroughData(AwsLambdaStepExceptionPassThroughData.builder()
                               .errorMsg(ExceptionUtils.getMessage(e))
                               .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
                               .build())
          .build();
    }

    return taskChainResponse;
  }

  private TaskChainResponse handlePrepareRollbackDataResponse(
      AwsLambdaPrepareRollbackResponse awsLambdaPrepareRollbackResponse, Ambiance ambiance,
      StepElementParameters stepElementParameters, AwsLambdaStepPassThroughData awsLambdaStepPassThroughData) {
    if (awsLambdaPrepareRollbackResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      AwsLambdaStepExceptionPassThroughData awsLambdaStepExceptionPassThroughData =
          AwsLambdaStepExceptionPassThroughData.builder()
              .errorMsg(awsLambdaPrepareRollbackResponse.getErrorMessage())
              .unitProgressData(awsLambdaPrepareRollbackResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().passThroughData(awsLambdaStepExceptionPassThroughData).chainEnd(true).build();
    }

    List<String> awsLambdaFunctionAliasDefinitionContents =
        getManifestContentsOfManifestType(awsLambdaStepPassThroughData, ManifestType.AwsLambdaFunctionAliasDefinition);

    AwsLambdaPrepareRollbackOutcome awsLambdaPrepareRollbackOutcome =
        AwsLambdaPrepareRollbackOutcome.builder()
            .awsLambdaDeployManifestContent(awsLambdaPrepareRollbackResponse.getManifestContent())
            .firstDeployment(awsLambdaPrepareRollbackResponse.isFirstDeployment())
            .functionName(awsLambdaPrepareRollbackResponse.getFunctionName())
            .awsLambdaArtifactConfig(awsLambdaEntityHelper.getAwsLambdaArtifactConfig(
                getArtifactOutcome(ambiance), AmbianceUtils.getNgAccess(ambiance)))
            .awsLambdaFunctionAliasDefinitionContents(awsLambdaFunctionAliasDefinitionContents)
            .functionCode(awsLambdaPrepareRollbackResponse.getFunctionCode())
            .functionConfiguration(awsLambdaPrepareRollbackResponse.getFunctionConfiguration())
            .build();

    executionSweepingOutputService.consume(ambiance,
        OutcomeExpressionConstants.AWS_LAMBDA_FUNCTION_PREPARE_ROLLBACK_OUTCOME, awsLambdaPrepareRollbackOutcome,
        StepOutcomeGroup.STEP.name());

    return executeTask(ambiance, stepElementParameters, awsLambdaStepPassThroughData,
        awsLambdaPrepareRollbackResponse.getUnitProgressData());
  }

  private TaskChainResponse handleGitFetchFilesResponse(Ambiance ambiance, StepElementParameters stepParameters,
      GitTaskNGResponse gitTaskResponse, AwsLambdaStepPassThroughData awsLambdaStepPassThroughData) {
    if (gitTaskResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      AwsLambdaStepExceptionPassThroughData awsLambdaStepExceptionPassThroughData =
          AwsLambdaStepExceptionPassThroughData.builder()
              .errorMsg(gitTaskResponse.getErrorMessage())
              .unitProgressData(gitTaskResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().passThroughData(awsLambdaStepExceptionPassThroughData).chainEnd(true).build();
    }

    Map<String, List<String>> manifestFileContentsMap = getManifestContentFromGitResponse(gitTaskResponse, ambiance);
    Map<String, List<String>> harnessManifestFileContentsMap =
        awsLambdaStepPassThroughData.getManifestFileContentsMap();
    for (Map.Entry<String, List<String>> entry : harnessManifestFileContentsMap.entrySet()) {
      manifestFileContentsMap.put(entry.getKey(), entry.getValue());
    }

    // Add manifestContent
    AwsLambdaStepPassThroughData awsLambdaStepPassThroughDataWithManifestContent =
        AwsLambdaStepPassThroughData.builder()
            .manifestFileContentsMap(manifestFileContentsMap)
            .manifestsOutcomes(awsLambdaStepPassThroughData.getManifestsOutcomes())
            .infrastructureOutcome(awsLambdaStepPassThroughData.getInfrastructureOutcome())
            .unitProgressData(gitTaskResponse.getUnitProgressData())
            .build();

    return executePrepareRollbackTask(ambiance, stepParameters, awsLambdaStepPassThroughDataWithManifestContent,
        gitTaskResponse.getUnitProgressData());
  }

  private TaskChainResponse executePrepareRollbackTask(Ambiance ambiance, StepElementParameters stepParameters,
      AwsLambdaStepPassThroughData awsLambdaStepPassThroughData, UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructureOutcome = awsLambdaStepPassThroughData.getInfrastructureOutcome();

    List<String> awsLambdaFunctionDefinitionContents =
        getManifestContentsOfManifestType(awsLambdaStepPassThroughData, ManifestType.AwsLambdaFunctionDefinition);

    validateAwsLambdaFunctionDefinitionContents(awsLambdaFunctionDefinitionContents);

    AwsLambdaPrepareRollbackRequest awsLambdaPrepareRollbackRequest =
        AwsLambdaPrepareRollbackRequest.builder()
            .awsLambdaCommandTypeNG(AwsLambdaCommandTypeNG.AWS_LAMBDA_PREPARE_ROLLBACK)
            .commandName(AWS_LAMBDA_PREPARE_ROLLBACK_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .awsLambdaInfraConfig(getInfraConfig(infrastructureOutcome, ambiance))
            .awsLambdaDeployManifestContent(awsLambdaFunctionDefinitionContents.get(0))
            .awsLambdaArtifactConfig(awsLambdaEntityHelper.getAwsLambdaArtifactConfig(
                getArtifactOutcome(ambiance), AmbianceUtils.getNgAccess(ambiance)))
            .build();

    return queueTask(stepParameters, awsLambdaPrepareRollbackRequest,
        TaskType.AWS_LAMBDA_PREPARE_ROLLBACK_COMMAND_TASK_NG, ambiance, awsLambdaStepPassThroughData, false);
  }

  private List<String> getManifestContentsOfManifestType(
      AwsLambdaStepPassThroughData awsLambdaStepPassThroughData, String manifestType) {
    List<ManifestOutcome> manifestOutcomes = awsLambdaStepPassThroughData.getManifestsOutcomes();

    List<String> manifestContentsOfManifestType = new ArrayList<>();

    for (ManifestOutcome manifestOutcome : manifestOutcomes) {
      if (manifestOutcome.getType().equals(manifestType)) {
        List<String> fileContentsList =
            awsLambdaStepPassThroughData.getManifestFileContentsMap().get(manifestOutcome.getIdentifier());
        if (CollectionUtils.isNotEmpty(fileContentsList)) {
          manifestContentsOfManifestType.add(fileContentsList.get(0));
        }
      }
    }

    return manifestContentsOfManifestType;
  }

  private Map<String, List<String>> getManifestContentFromGitResponse(
      GitTaskNGResponse gitTaskResponse, Ambiance ambiance) {
    Map<String, List<String>> manifestFileContentsMap = new HashMap<>();

    List<GitFetchFilesResult> gitFetchFilesResults = gitTaskResponse.getGitFetchFilesResults();

    for (GitFetchFilesResult gitFetchFilesResult : gitFetchFilesResults) {
      List<String> manifestContentList = new ArrayList<>();

      for (GitFile gitFile : gitFetchFilesResult.getFiles()) {
        String manifestContent = gitFile.getFileContent();
        manifestContent = engineExpressionService.renderExpression(ambiance, manifestContent);
        manifestContentList.add(manifestContent);
      }

      manifestFileContentsMap.put(gitFetchFilesResult.getIdentifier(), manifestContentList);
    }

    return manifestFileContentsMap;
  }

  public StepResponse handleStepFailureException(
      Ambiance ambiance, AwsLambdaStepPassThroughData stepPassThroughData, Exception e) throws Exception {
    if (ExceptionUtils.cause(TaskNGDataException.class, e) != null) {
      throw e;
    }

    UnitProgressData unitProgressData =
        completeUnitProgressData(stepPassThroughData.getUnitProgressData(), ambiance, e.getMessage());
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
      AwsLambdaCommandResponse awsLambdaCommandResponse, StepResponseBuilder stepResponseBuilder) {
    stepResponseBuilder.status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder()
                         .setErrorMessage(awsLambdaCommandResponse.getErrorMessage() == null
                                 ? ""
                                 : awsLambdaCommandResponse.getErrorMessage())
                         .build());
    return stepResponseBuilder;
  }

  public AwsLambdaStepOutcome getAwsLambdaStepOutcome(AwsLambda awsLambda) {
    if (awsLambda == null) {
      return AwsLambdaStepOutcome.builder().build();
    }
    return AwsLambdaStepOutcome.builder()
        .functionName(awsLambda.getFunctionName())
        .runtime(awsLambda.getRuntime())
        .version(awsLambda.getVersion())
        .functionArn(awsLambda.getFunctionArn())
        .build();
  }

  public TaskChainResponse startChainLink(Ambiance ambiance, StepElementParameters stepElementParameters) {
    // Get ManifestsOutcome
    ManifestsOutcome manifestsOutcome = resolveAwsLambdaManifestsOutcome(ambiance);

    // Get InfrastructureOutcome
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    // Update expressions in ManifestsOutcome
    ExpressionEvaluatorUtils.updateExpressions(
        manifestsOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

    // Validate ManifestsOutcome
    validateManifestsOutcome(ambiance, manifestsOutcome);

    List<ManifestOutcome> awsLambdaManifestOutcomeList = getAwsLambdaManifestOutcome(manifestsOutcome.values());
    validateAwsLambdaManifestOutcomes(awsLambdaManifestOutcomeList);

    LogCallback logCallback = getLogCallback(AwsLambdaCommandUnitConstants.fetchManifests.toString(), ambiance, true);
    List<AwsLambdaHarnessStoreFilesResult> harnessStoreFilesResultList =
        getHarnessStoreManifestFilesContent(ambiance, awsLambdaManifestOutcomeList, logCallback);

    TaskChainResponse taskChainResponse = null;
    Map<String, List<String>> harnessManifestFilesContentMap =
        getManifestContentFromHarnessStore(harnessStoreFilesResultList, ambiance);
    AwsLambdaStepPassThroughData awsLambdaStepPassThroughData =
        AwsLambdaStepPassThroughData.builder()
            .manifestsOutcomes(awsLambdaManifestOutcomeList)
            .infrastructureOutcome(infrastructureOutcome)
            .manifestFileContentsMap(harnessManifestFilesContentMap)
            .build();

    if (isAnyGitManifest(awsLambdaManifestOutcomeList)) {
      taskChainResponse = prepareManifestGitFetchTask(infrastructureOutcome, ambiance, stepElementParameters,
          awsLambdaManifestOutcomeList, awsLambdaStepPassThroughData);
    } else {
      taskChainResponse = prepareManifestHarnessStoreTask(
          ambiance, stepElementParameters, infrastructureOutcome, awsLambdaStepPassThroughData, logCallback);
    }

    return taskChainResponse;
  }

  private TaskChainResponse prepareManifestHarnessStoreTask(Ambiance ambiance, StepElementParameters stepParameters,
      InfrastructureOutcome infrastructureOutcome, AwsLambdaStepPassThroughData awsLambdaStepPassThroughData,
      LogCallback logCallback) {
    TaskChainResponse taskChainResponse = null;
    UnitProgressData unitProgressData = getCommandUnitProgressData(
        AwsLambdaCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);
    logCallback.saveExecutionLog("Done.. ", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    taskChainResponse =
        executePrepareRollbackTask(ambiance, stepParameters, awsLambdaStepPassThroughData, unitProgressData);
    return taskChainResponse;
  }

  private Map<String, List<String>> getManifestContentFromHarnessStore(
      List<AwsLambdaHarnessStoreFilesResult> harnessStoreFilesResultList, Ambiance ambiance) {
    Map<String, List<String>> manifestFileContentsMap = new HashMap<>();

    for (AwsLambdaHarnessStoreFilesResult harnessStoreFilesResult : harnessStoreFilesResultList) {
      List<String> manifestContentList = new ArrayList<>();

      for (String fileContent : harnessStoreFilesResult.getFilesContent()) {
        fileContent = engineExpressionService.renderExpression(ambiance, fileContent);
        manifestContentList.add(fileContent);
      }

      manifestFileContentsMap.put(harnessStoreFilesResult.getIdentifier(), manifestContentList);
    }

    return manifestFileContentsMap;
  }

  private List<AwsLambdaHarnessStoreFilesResult> getHarnessStoreManifestFilesContent(
      Ambiance ambiance, List<ManifestOutcome> manifestOutcomeList, LogCallback logCallback) {
    List<AwsLambdaHarnessStoreFilesResult> harnessStoreFilesResultList = new ArrayList<>();

    for (ManifestOutcome manifestOutcome : manifestOutcomeList) {
      if (ManifestStoreType.HARNESS.equals(manifestOutcome.getStore().getKind())) {
        List<String> manifestContent = fetchFilesContentFromLocalStore(ambiance, manifestOutcome, logCallback);
        AwsLambdaHarnessStoreFilesResult harnessStoreFilesResult = AwsLambdaHarnessStoreFilesResult.builder()
                                                                       .filesContent(manifestContent)
                                                                       .manifestType(manifestOutcome.getType())
                                                                       .identifier(manifestOutcome.getIdentifier())
                                                                       .build();
        harnessStoreFilesResultList.add(harnessStoreFilesResult);
      }
    }
    return harnessStoreFilesResultList;
  }

  public void validateAwsLambdaManifestOutcomes(List<ManifestOutcome> awsLambdaManifestOutcomeList) {
    List<ManifestOutcome> awsLambdaFunctionDefinitionManifests =
        getManifestOutcomesByType(awsLambdaManifestOutcomeList, ManifestType.AwsLambdaFunctionDefinition);

    if (CollectionUtils.isEmpty(awsLambdaFunctionDefinitionManifests)) {
      throw new InvalidRequestException(
          format("AWS Native Lambda deployment expects at least one manifest of type AWS Lambda Function Definition.\n"
              + "Please configure it in Harness Service."));
    }
  }

  public List<ManifestOutcome> getManifestOutcomesByType(List<ManifestOutcome> manifestOutcomes, String manifestType) {
    List<ManifestOutcome> manifestOutcomesOfManifestType = null;

    if (CollectionUtils.isNotEmpty(manifestOutcomes) && StringUtils.isNotEmpty(manifestType)) {
      manifestOutcomesOfManifestType = manifestOutcomes.stream()
                                           .filter(manifestOutcome -> manifestType.equals(manifestOutcome.getType()))
                                           .collect(Collectors.toList());
    }

    return manifestOutcomesOfManifestType;
  }

  private ManifestsOutcome resolveAwsLambdaManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    if (!manifestsOutcome.isFound()) {
      String stageName =
          AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
      String stepType = Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance))
                            .map(StepType::getType)
                            .orElse("Aws Lambda Function");
      throw new GeneralException(
          format("No manifests found in stage %s. %s step requires a manifest defined in stage service definition",
              stageName, stepType));
    }
    return (ManifestsOutcome) manifestsOutcome.getOutcome();
  }

  public void validateManifestsOutcome(Ambiance ambiance, ManifestsOutcome manifestsOutcome) {
    Set<EntityDetailProtoDTO> entityDetails = new HashSet<>();
    manifestsOutcome.values().forEach(value -> {
      entityDetails.addAll(entityReferenceExtractorUtils.extractReferredEntities(ambiance, value.getStore()));
      ManifestOutcomeValidator.validate(value, false);
    });

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
  }

  private TaskChainResponse prepareManifestGitFetchTask(InfrastructureOutcome infrastructureOutcome, Ambiance ambiance,
      StepElementParameters stepElementParameters, List<ManifestOutcome> awsLambdaManifestOutcomeList,
      AwsLambdaStepPassThroughData awsLambdaStepPassThroughData) {
    List<GitRequestFileConfig> gitRequestFileConfigs = new ArrayList<>();

    for (ManifestOutcome manifestOutcome : awsLambdaManifestOutcomeList) {
      if (ManifestStoreType.isInGitSubset(manifestOutcome.getStore().getKind())) {
        GitRequestFileConfig currentGitRequestFileConfig =
            getGitFetchFilesConfigFromManifestOutcome(manifestOutcome, ambiance);

        gitRequestFileConfigs.add(currentGitRequestFileConfig);
      }
    }

    return getGitFetchFileTaskResponse(
        ambiance, false, stepElementParameters, awsLambdaStepPassThroughData, gitRequestFileConfigs);
  }

  private GitRequestFileConfig getGitFetchFilesConfigFromManifestOutcome(
      ManifestOutcome manifestOutcome, Ambiance ambiance) {
    StoreConfig storeConfig = manifestOutcome.getStore();
    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for AwsLambda step", USER);
    }
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
    return getGitFetchFilesConfig(ambiance, gitStoreConfig, manifestOutcome);
  }

  private GitRequestFileConfig getGitFetchFilesConfig(
      Ambiance ambiance, GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome) {
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    String validationMessage = format("Aws Lambda manifest with Id [%s]", manifestOutcome.getIdentifier());
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorInfoDTO connectorDTO = awsLambdaEntityHelper.getConnectorInfoDTO(connectorId, ngAccess);
    validateManifest(gitStoreConfig.getKind(), connectorDTO, validationMessage);
    return GitRequestFileConfig.builder()
        .gitStoreDelegateConfig(getGitStoreDelegateConfig(
            gitStoreConfig, connectorDTO, manifestOutcome, gitStoreConfig.getPaths().getValue(), ambiance))
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .succeedIfFileNotFound(false)
        .build();
  }

  private TaskChainResponse getGitFetchFileTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
      StepElementParameters stepElementParameters, AwsLambdaStepPassThroughData awsLambdaStepPassThroughData,
      List<GitRequestFileConfig> gitRequestFileConfigs) {
    String accountId = AmbianceUtils.getAccountId(ambiance);

    GitTaskNGRequest gitTaskNGRequest = GitTaskNGRequest.builder()
                                            .accountId(accountId)
                                            .gitRequestFileConfigs(gitRequestFileConfigs)
                                            .shouldOpenLogStream(shouldOpenLogStream)
                                            .commandUnitName(AwsLambdaCommandUnitConstants.fetchManifests.toString())
                                            .closeLogStream(true)
                                            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.GIT_TASK_NG.name())
                                  .parameters(new Object[] {gitTaskNGRequest})
                                  .build();

    String taskName = TaskType.GIT_TASK_NG.getDisplayName();

    AwsLambdaSpecParameters awsLambdaSpecParameters = (AwsLambdaSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest =
        TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
            Arrays.asList(AwsLambdaCommandUnitConstants.fetchManifests.toString(),
                AwsLambdaCommandUnitConstants.prepareRollbackData.toString(),
                AwsLambdaCommandUnitConstants.deploy.toString()),
            taskName,
            TaskSelectorYaml.toTaskSelector(
                emptyIfNull(getParameterFieldValue(awsLambdaSpecParameters.getDelegateSelectors()))),
            stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(awsLambdaStepPassThroughData)
        .build();
  }

  public TaskChainResponse executeTask(Ambiance ambiance, StepElementParameters stepParameters,
      AwsLambdaStepPassThroughData awsLambdaStepPassThroughData, UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructureOutcome = awsLambdaStepPassThroughData.getInfrastructureOutcome();

    List<String> awsLambdaFunctionDefinitionContents =
        getManifestContentsOfManifestType(awsLambdaStepPassThroughData, ManifestType.AwsLambdaFunctionDefinition);

    validateAwsLambdaFunctionDefinitionContents(awsLambdaFunctionDefinitionContents);

    List<String> awsLambdaFunctionAliasDefinitionContents =
        getManifestContentsOfManifestType(awsLambdaStepPassThroughData, ManifestType.AwsLambdaFunctionAliasDefinition);

    AwsLambdaDeployRequest awsLambdaDeployRequest =
        AwsLambdaDeployRequest.builder()
            .awsLambdaCommandTypeNG(AwsLambdaCommandTypeNG.AWS_LAMBDA_DEPLOY)
            .commandName(AWS_LAMBDA_DEPLOY_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .awsLambdaInfraConfig(getInfraConfig(infrastructureOutcome, ambiance))
            .awsLambdaDeployManifestContent(awsLambdaFunctionDefinitionContents.get(0))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .awsLambdaArtifactConfig(awsLambdaEntityHelper.getAwsLambdaArtifactConfig(
                getArtifactOutcome(ambiance), AmbianceUtils.getNgAccess(ambiance)))
            .awsLambdaAliasManifestContent(awsLambdaFunctionAliasDefinitionContents)
            .build();

    return queueTask(stepParameters, awsLambdaDeployRequest, TaskType.AWS_LAMBDA_DEPLOY_COMMAND_TASK_NG, ambiance,
        awsLambdaStepPassThroughData, true);
  }

  public void validateAwsLambdaFunctionDefinitionContents(List<String> awsLambdaFunctionDefinitionContents) {
    if (CollectionUtils.isEmpty(awsLambdaFunctionDefinitionContents)) {
      throw new InvalidRequestException(
          "AWS Lambda Function Definition Manifest is not configured. Please configure in Harness Service.");
    }
  }

  private ArtifactOutcome getArtifactOutcome(Ambiance ambiance) {
    OptionalOutcome artifactsOutcomeOption = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (artifactsOutcomeOption.isFound()) {
      ArtifactsOutcome artifactsOutcome = (ArtifactsOutcome) artifactsOutcomeOption.getOutcome();
      if (artifactsOutcome.getPrimary() != null) {
        return artifactsOutcome.getPrimary();
      }
    }
    throw new InvalidRequestException("Aws Lambda Artifact is mandatory.", USER);
  }

  public StepResponse generateStepResponse(
      AwsLambdaCommandResponse awsLambdaCommandResponse, StepResponseBuilder stepResponseBuilder, Ambiance ambiance) {
    if (awsLambdaCommandResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return getFailureResponseBuilder(awsLambdaCommandResponse, stepResponseBuilder).build();
    } else {
      InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
          ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
      AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig =
          (AwsLambdaFunctionsInfraConfig) getInfraConfig(infrastructureOutcome, ambiance);
      //      List<ServerInstanceInfo> serverInstanceInfoList = getServerInstanceInfo(
      //              googleFunctionCommandResponse, gcpGoogleFunctionInfraConfig,
      //              infrastructureOutcome.getInfrastructureKey());
      //      instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);
      AwsLambdaStepOutcome awsLambdaStepOutcome = getAwsLambdaStepOutcome(awsLambdaCommandResponse.getAwsLambda());

      return stepResponseBuilder.status(Status.SUCCEEDED)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(OutcomeExpressionConstants.OUTPUT)
                           .outcome(awsLambdaStepOutcome)
                           .build())
          .build();
    }
  }

  public StepResponse handleStepExceptionFailure(AwsLambdaStepExceptionPassThroughData stepException) {
    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(io.harness.eraro.Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(HarnessStringUtils.emptyIfNull(stepException.getErrorMsg()))
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

  public List<ServerInstanceInfo> getServerInstanceInfo(AwsLambdaCommandResponse awsLambdaCommandResponse,
      AwsLambdaInfraConfig awsLambdaInfraConfig, String infrastructureKey) {
    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();
    AwsLambda awsLambdaFunction = awsLambdaCommandResponse.getAwsLambda();
    if (awsLambdaFunction != null) {
      serverInstanceInfoList.add(AwsLambdaToServerInstanceInfoMapper.toServerInstanceInfo(
          awsLambdaFunction, ((AwsLambdaFunctionsInfraConfig) awsLambdaInfraConfig).getRegion(), infrastructureKey));
    }
    return serverInstanceInfoList;
  }
}
