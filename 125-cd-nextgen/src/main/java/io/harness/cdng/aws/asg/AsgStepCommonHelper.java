/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.aws.asg.AsgRollingPrepareRollbackDataOutcome.AsgRollingPrepareRollbackDataOutcomeBuilder;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.aws.asg.AsgCommandResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataResponse;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataResult;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.IllegalArgumentException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
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
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.LogWrapper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsgStepCommonHelper extends CDStepHelper {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private AsgEntityHelper asgEntityHelper;
  @Inject private AsgStepHelper asgStepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private TaskSetupAbstractionHelper taskSetupAbstractionHelper;

  private final LogWrapper logger = new LogWrapper(log);

  public TaskChainResponse startChainLink(
      AsgStepExecutor asgStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters) {
    // Get ManifestsOutcome
    ManifestsOutcome manifestsOutcome = resolveAsgManifestsOutcome(ambiance);

    // Get InfrastructureOutcome
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    // Update expressions in ManifestsOutcome
    ExpressionEvaluatorUtils.updateExpressions(
        manifestsOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

    // Validate ManifestsOutcome
    validateManifestsOutcome(ambiance, manifestsOutcome);

    LogCallback logCallback = getLogCallback(AsgCommandUnitConstants.fetchManifests.toString(), ambiance, true);

    Collection<ManifestOutcome> manifestOutcomeList = manifestsOutcome.values();
    List<ManifestOutcome> manifestOutcomesFromHarnessStore =
        asgStepHelper.getManifestOutcomesFromHarnessStore(manifestOutcomeList);
    List<ManifestOutcome> manifestOutcomesFromGitStore =
        asgStepHelper.getManifestOutcomesFromGitStore(manifestOutcomeList);
    List<GitFetchFilesConfig> gitFetchFilesConfigs =
        manifestOutcomesFromGitStore.stream()
            .map(manifestOutcome -> getGitFetchFilesConfigFromManifestOutcome(manifestOutcome, ambiance))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    logger.info(logCallback, "Fetching manifest files ...");

    Map<String, List<String>> manifestContentsMapFromHarness =
        getManifestFileContentsFromHarnessStore(ambiance, manifestOutcomesFromHarnessStore, logCallback);

    // all manifests from Harness store
    if (manifestOutcomesFromGitStore.isEmpty()) {
      return prepareAsgTask(asgStepExecutor, ambiance, stepElementParameters, manifestContentsMapFromHarness,
          infrastructureOutcome, logCallback);
    }

    // extract Git manifests
    AsgManifestFetchData asgManifestFetchData = AsgManifestFetchData.builder()
                                                    .harnessFetchedManifestContentMap(manifestContentsMapFromHarness)
                                                    .gitFetchFilesConfigs(gitFetchFilesConfigs)
                                                    .gitFetchedManifestOutcomeIdentifiersMap(new HashMap<>())
                                                    .build();

    UnitProgressData unitProgressData = cdStepHelper.getCommandUnitProgressData(
        AsgCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.RUNNING);

    AsgExecutionPassThroughData executionPassThroughData = AsgExecutionPassThroughData.builder()
                                                               .infrastructure(infrastructureOutcome)
                                                               .lastActiveUnitProgressData(unitProgressData)
                                                               .asgManifestFetchData(asgManifestFetchData)
                                                               .build();

    return chainFetchGitTaskUntilAllGitManifestsFetched(executionPassThroughData, ambiance, stepElementParameters);
  }

  public ManifestsOutcome resolveAsgManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    if (!manifestsOutcome.isFound()) {
      String stageName =
          AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
      String stepType =
          Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance)).map(StepType::getType).orElse("Asg");
      throw new InvalidRequestException(
          format("No manifests found in stage %s. %s step requires a manifest defined in stage service definition",
              stageName, stepType));
    }
    return (ManifestsOutcome) manifestsOutcome.getOutcome();
  }

  private Map<String, List<String>> getManifestFileContentsFromHarnessStore(
      Ambiance ambiance, List<ManifestOutcome> manifestOutcomes, LogCallback logCallback) {
    Map<String, List<String>> resultMap = new HashMap<>();
    manifestOutcomes.forEach(manifestOutcome -> {
      String manifestType = manifestOutcome.getType();
      List<String> contents = cdStepHelper.fetchFilesContentFromLocalStore(ambiance, manifestOutcome, logCallback);
      resultMap.putIfAbsent(manifestType, new ArrayList<>());
      contents.stream().forEach(
          o -> { resultMap.get(manifestType).add(renderExpressionsForManifestContent(o, ambiance)); });
    });
    return resultMap;
  }

  private List<GitFetchFilesConfig> getGitFetchFilesConfigFromManifestOutcome(
      ManifestOutcome manifestOutcome, Ambiance ambiance) {
    StoreConfig storeConfig = manifestOutcome.getStore();
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Git subset", USER);
    }

    List<GitFetchFilesConfig> gitFetchFilesConfigs = new ArrayList<>();
    gitFetchFilesConfigs.add(cdStepHelper.getGitFetchFilesConfig(
        ambiance, gitStoreConfig, manifestOutcome.getType(), manifestOutcome.getIdentifier()));

    return gitFetchFilesConfigs;
  }

  private String renderExpressionsForManifestContent(String content, Ambiance ambiance) {
    return engineExpressionService.renderExpression(ambiance, content);
  }

  private TaskChainResponse prepareAsgTask(AsgStepExecutor asgStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, Map<String, List<String>> harnessFetchedManifestContentMap,
      InfrastructureOutcome infrastructureOutcome, LogCallback logCallback) {
    logCallback.saveExecutionLog(
        color("Fetched all manifest files", Green, Bold), INFO, CommandExecutionStatus.SUCCESS);

    UnitProgressData unitProgressData = cdStepHelper.getCommandUnitProgressData(
        AsgCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);

    AsgManifestFetchData asgManifestFetchData =
        AsgManifestFetchData.builder().harnessFetchedManifestContentMap(harnessFetchedManifestContentMap).build();

    Map<String, List<String>> asgStoreManifestsContent = buildManifestContentMap(asgManifestFetchData, ambiance);

    TaskChainResponse taskChainResponse;
    if (asgStepExecutor instanceof AsgCanaryDeployStep) {
      AsgExecutionPassThroughData executionPassThroughData = AsgExecutionPassThroughData.builder()
                                                                 .infrastructure(infrastructureOutcome)
                                                                 .lastActiveUnitProgressData(unitProgressData)
                                                                 .asgManifestFetchData(asgManifestFetchData)
                                                                 .build();

      AsgStepExecutorParams asgStepExecutorParams = AsgStepExecutorParams.builder()
                                                        .shouldOpenFetchFilesLogStream(false)
                                                        .asgStoreManifestsContent(asgStoreManifestsContent)
                                                        .build();

      taskChainResponse = asgStepExecutor.executeAsgTask(
          ambiance, stepElementParameters, executionPassThroughData, unitProgressData, asgStepExecutorParams);
    } else if (asgStepExecutor instanceof AsgRollingDeployStep) {
      AsgPrepareRollbackDataPassThroughData asgPrepareRollbackDataPassThroughData =
          AsgPrepareRollbackDataPassThroughData.builder()
              .infrastructureOutcome(infrastructureOutcome)
              .asgStoreManifestsContent(asgStoreManifestsContent)
              .build();
      taskChainResponse = asgStepExecutor.executeAsgPrepareRollbackDataTask(
          ambiance, stepElementParameters, asgPrepareRollbackDataPassThroughData, unitProgressData);
    } else {
      // TODO
      throw new RuntimeException("Not implemented yet");
    }
    return taskChainResponse;
  }

  public TaskChainResponse queueAsgTask(StepElementParameters stepElementParameters, AsgCommandRequest commandRequest,
      Ambiance ambiance, PassThroughData passThroughData, boolean isChainEnd, TaskType taskType) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {commandRequest})
                            .taskType(taskType.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();

    String taskName = taskType.getDisplayName() + " : " + commandRequest.getCommandName();

    AsgSpecParameters asgSpecParameters = (AsgSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        asgSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(asgSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  public static String getErrorMessage(AsgCommandResponse asgCommandResponse) {
    return asgCommandResponse.getErrorMessage() == null ? "" : asgCommandResponse.getErrorMessage();
  }

  public StepResponse handleTaskException(
      Ambiance ambiance, AsgExecutionPassThroughData executionPassThroughData, Exception e) throws Exception {
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
      AsgCommandResponse asgCommandResponse, StepResponseBuilder stepResponseBuilder) {
    stepResponseBuilder.status(Status.FAILED)
        .failureInfo(
            FailureInfo.newBuilder().setErrorMessage(AsgStepCommonHelper.getErrorMessage(asgCommandResponse)).build());
    return stepResponseBuilder;
  }

  public AsgInfraConfig getAsgInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return asgEntityHelper.getAsgInfraConfig(infrastructure, ngAccess);
  }

  public TaskChainResponse executeNextLinkRolling(AsgStepExecutor asgStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      DelegateResponseData delegateResponseData) throws Exception {
    UnitProgressData unitProgressData = null;
    TaskChainResponse taskChainResponse = null;
    try {
      AsgPrepareRollbackDataResponse asgPrepareRollbackDataResponse =
          (AsgPrepareRollbackDataResponse) delegateResponseData;
      AsgPrepareRollbackDataPassThroughData asgStepPassThroughData =
          (AsgPrepareRollbackDataPassThroughData) passThroughData;

      taskChainResponse = handleAsgPrepareRollbackDataResponseRolling(
          asgPrepareRollbackDataResponse, asgStepExecutor, ambiance, stepElementParameters, asgStepPassThroughData);
    } catch (Exception e) {
      log.error("Error while processing asg task: {}", e.getMessage(), e);
      taskChainResponse =
          TaskChainResponse.builder()
              .chainEnd(true)
              .passThroughData(
                  AsgStepExceptionPassThroughData.builder()
                      .errorMessage(ExceptionUtils.getMessage(e))
                      .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
                      .build())
              .build();
    }

    return taskChainResponse;
  }

  private TaskChainResponse handleAsgPrepareRollbackDataResponseRolling(
      AsgPrepareRollbackDataResponse asgPrepareRollbackDataResponse, AsgStepExecutor asgStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, AsgPrepareRollbackDataPassThroughData asgStepPassThroughData) {
    if (asgPrepareRollbackDataResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      AsgStepExceptionPassThroughData asgStepExceptionPassThroughData =
          AsgStepExceptionPassThroughData.builder()
              .errorMessage(asgPrepareRollbackDataResponse.getErrorMessage())
              .unitProgressData(asgPrepareRollbackDataResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().passThroughData(asgStepExceptionPassThroughData).chainEnd(true).build();
    }

    if (asgStepExecutor instanceof AsgRollingDeployStep) {
      AsgPrepareRollbackDataResult asgPrepareRollbackDataResult =
          asgPrepareRollbackDataResponse.getAsgPrepareRollbackDataResult();

      AsgRollingPrepareRollbackDataOutcomeBuilder asgPrepareRollbackDataOutcomeBuilder =
          AsgRollingPrepareRollbackDataOutcome.builder();

      asgPrepareRollbackDataOutcomeBuilder.asgStoreManifestsContent(
          asgPrepareRollbackDataResult.getAsgStoreManifestsContent());

      executionSweepingOutputService.consume(ambiance,
          OutcomeExpressionConstants.ASG_ROLLING_PREPARE_ROLLBACK_DATA_OUTCOME,
          asgPrepareRollbackDataOutcomeBuilder.build(), StepOutcomeGroup.STEP.name());
    }

    AsgExecutionPassThroughData asgExecutionPassThroughData =
        AsgExecutionPassThroughData.builder()
            .infrastructure(asgStepPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(asgPrepareRollbackDataResponse.getUnitProgressData())
            .build();

    Map<String, List<String>> asgStoreManifestsContent = asgStepPassThroughData.getAsgStoreManifestsContent();

    AsgStepExecutorParams asgStepExecutorParams = AsgStepExecutorParams.builder()
                                                      .shouldOpenFetchFilesLogStream(false)
                                                      .asgStoreManifestsContent(asgStoreManifestsContent)
                                                      .build();

    return asgStepExecutor.executeAsgTask(ambiance, stepElementParameters, asgExecutionPassThroughData,
        asgPrepareRollbackDataResponse.getUnitProgressData(), asgStepExecutorParams);
  }

  public StepResponse handleStepExceptionFailure(AsgStepExceptionPassThroughData stepException) {
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

  public TaskChainResponse queueFetchGitTask(AsgExecutionPassThroughData asgExecutionPassThroughData, Ambiance ambiance,
      StepElementParameters stepElementParameters) {
    GitFetchFilesConfig gitFetchFilesConfig =
        asgExecutionPassThroughData.getAsgManifestFetchData().getNextGitFetchFilesConfig();

    String accountId = AmbianceUtils.getAccountId(ambiance);
    GitFetchRequest gitFetchRequest = GitFetchRequest.builder()
                                          .gitFetchFilesConfigs(Arrays.asList(gitFetchFilesConfig))
                                          .shouldOpenLogStream(false)
                                          .closeLogStream(false)
                                          .accountId(accountId)
                                          .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.GIT_FETCH_NEXT_GEN_TASK.name())
                                  .parameters(new Object[] {gitFetchRequest})
                                  .build();

    AsgSpecParameters asgSpecParameters = (AsgSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        asgSpecParameters.getCommandUnits(), TaskType.GIT_FETCH_NEXT_GEN_TASK.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(asgSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(false)
        .passThroughData(asgExecutionPassThroughData)
        .build();
  }

  public TaskChainResponse chainFetchGitTaskUntilAllGitManifestsFetched(
      AsgExecutionPassThroughData asgExecutionPassThroughData, Ambiance ambiance,
      StepElementParameters stepElementParameters) {
    return chainFetchGitTaskUntilAllGitManifestsFetched(
        asgExecutionPassThroughData, null, ambiance, stepElementParameters, null);
  }

  public TaskChainResponse chainFetchGitTaskUntilAllGitManifestsFetched(
      AsgExecutionPassThroughData asgExecutionPassThroughData, DelegateResponseData delegateResponseData,
      Ambiance ambiance, StepElementParameters stepElementParameters, Supplier<TaskChainResponse> taskSupplier) {
    LogCallback logCallback = getLogCallback(AsgCommandUnitConstants.fetchManifests.toString(), ambiance, false);

    AsgManifestFetchData asgManifestFetchData = asgExecutionPassThroughData.getAsgManifestFetchData();
    GitFetchFilesConfig gitFetchFilesConfig = asgManifestFetchData.getNextGitFetchFilesConfig();
    String identifier = gitFetchFilesConfig.getIdentifier();
    String manifestType = gitFetchFilesConfig.getManifestType();

    // not first attempt, already receiving GitFetchResponse from chain
    if (delegateResponseData != null) {
      GitFetchResponse gitFetchResponse = (GitFetchResponse) delegateResponseData;

      if (gitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
        throw new IllegalArgumentException(
            format("Could not fetch manifest %s with identifier `%s` from Git", manifestType, identifier), USER);
      }

      List<GitFile> gitFiles = gitFetchResponse.getFilesFromMultipleRepo()
                                   .values()
                                   .stream()
                                   .filter(f -> isNotEmpty(f.getFiles()))
                                   .map(f -> f.getFiles())
                                   .flatMap(Collection::stream)
                                   .peek(f -> {
                                     if (isEmpty(f.getFileContent())) {
                                       throw new InvalidRequestException(format(
                                           "The following file %s in Git Store has empty content", f.getFilePath()));
                                     }
                                   })
                                   .collect(Collectors.toList());

      List<String> gitFilePaths = gitFiles.stream().map(f -> f.getFilePath()).collect(Collectors.toList());

      List<String> gitContents = gitFiles.stream().map(f -> f.getFileContent()).collect(Collectors.toList());

      asgManifestFetchData.getGitFetchedManifestOutcomeIdentifiersMap().put(identifier, gitContents);
      logger.infoBold(logCallback,
          "Successfully fetched %s manifest files with identifier `%s` from Git:", manifestType, identifier);
      gitFilePaths.stream().forEach(fp -> logger.info(logCallback, "- %s", fp));
    }

    gitFetchFilesConfig = asgManifestFetchData.getNextGitFetchFilesConfig();

    if (gitFetchFilesConfig != null) {
      identifier = gitFetchFilesConfig.getIdentifier();
      manifestType = gitFetchFilesConfig.getManifestType();
      logger.infoBold(
          logCallback, "%nFetching %s manifest files with identifier `%s` from Git", manifestType, identifier);
      logger.info(logCallback, "Fetching following Files:");
      gitFetchFilesConfig.getGitStoreDelegateConfig().getPaths().stream().forEach(
          fp -> logger.info(logCallback, "- %s", fp));

      return queueFetchGitTask(asgExecutionPassThroughData, ambiance, stepElementParameters);
    }

    logCallback.saveExecutionLog(
        color("Fetched all manifest files", Green, Bold), INFO, CommandExecutionStatus.SUCCESS);

    UnitProgressData unitProgressData = cdStepHelper.getCommandUnitProgressData(
        AsgCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);

    asgExecutionPassThroughData.setLastActiveUnitProgressData(unitProgressData);

    return taskSupplier.get();
  }

  public Map<String, List<String>> buildManifestContentMap(
      AsgManifestFetchData asgManifestFetchData, Ambiance ambiance) {
    Map<String, List<String>> map = asgManifestFetchData.getHarnessFetchedManifestContentMap() != null
        ? asgManifestFetchData.getHarnessFetchedManifestContentMap()
        : new HashMap<>();

    if (isNotEmpty(asgManifestFetchData.getGitFetchedManifestOutcomeIdentifiersMap())) {
      asgManifestFetchData.getGitFetchedManifestOutcomeIdentifiersMap().entrySet().stream().forEach(entry -> {
        String identifier = entry.getKey();
        String manifestType = getManifestTypeByIdentifier(asgManifestFetchData.getGitFetchFilesConfigs(), identifier);
        List<String> contents = entry.getValue();
        map.putIfAbsent(manifestType, new ArrayList<>());
        contents.stream().forEach(
            o -> { map.get(manifestType).add(renderExpressionsForManifestContent(o, ambiance)); });
      });
    }

    return map;
  }

  private String getManifestTypeByIdentifier(List<GitFetchFilesConfig> gitFetchFilesConfigs, String identifier) {
    Optional<String> manifestType = gitFetchFilesConfigs.stream()
                                        .filter(o -> o.getIdentifier().equals(identifier))
                                        .map(GitFetchFilesConfig::getManifestType)
                                        .findFirst();

    if (manifestType.isEmpty()) {
      throw new IllegalArgumentException("Invalid identifier provided", WingsException.USER);
    }

    return manifestType.get();
  }
}
