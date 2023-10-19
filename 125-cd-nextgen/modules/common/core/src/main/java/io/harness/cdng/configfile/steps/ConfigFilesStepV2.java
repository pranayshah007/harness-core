/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.entityusageactivity.EntityUsageTypes.PIPELINE_EXECUTION;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.StepDelegateInfo;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.configfile.ConfigFilesOutcome;
import io.harness.cdng.configfile.ConfigGitFile;
import io.harness.cdng.configfile.mapper.ConfigFileOutcomeMapper;
import io.harness.cdng.configfile.mapper.ConfigGitFilesMapper;
import io.harness.cdng.configfile.validator.IndividualConfigFileStepValidator;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.utilities.ServiceEnvironmentsLogCallbackUtility;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.gitcommon.GitFetchFilesResult;
import io.harness.delegate.task.gitcommon.GitRequestFileConfig;
import io.harness.delegate.task.gitcommon.GitTaskNGRequest;
import io.harness.delegate.task.gitcommon.GitTaskNGResponse;
import io.harness.eventsframework.schemas.entity.EntityUsageDetailProto;
import io.harness.eventsframework.schemas.entity.PipelineExecutionUsageDataProto;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.invokers.StrategyHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.secretusage.SecretRuntimeUsageService;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;
import io.harness.validation.JavaxValidator;
import io.harness.walktree.visitor.entityreference.beans.VisitedSecretReference;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.beans.LogWeight;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ConfigFilesStepV2 extends AbstractConfigFileStep
    implements AsyncExecutableWithRbac<EmptyStepParameters>, SyncExecutable<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.CONFIG_FILES_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  private static final String CONFIG_FILES_STEP_V2 = "CONFIG_FILES_STEP_V2";
  static final String CONFIG_FILE_COMMAND_UNIT = "configFiles";
  private static final long CONFIG_FILE_GIT_TASK_TIMEOUT = TimeUnit.MINUTES.toMillis(10);
  private static final String CONFIG_FILES_STEP = "Config Files Step";

  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private ConfigGitFilesMapper configGitFilesMapper;
  @Inject private SecretRuntimeUsageService secretRuntimeUsageService;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StrategyHelper strategyHelper;
  @Inject private ServiceEnvironmentsLogCallbackUtility serviveEnvironmentsLogUtility;
  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, EmptyStepParameters stepParameters) {
    // nothing to validate here
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    final NgConfigFilesMetadataSweepingOutput configFilesSweepingOutput =
        fetchConfigFilesMetadataFromSweepingOutput(ambiance);

    final List<ConfigFileWrapper> configFiles = configFilesSweepingOutput.getFinalSvcConfigFiles();
    final NGLogCallback logCallback = serviveEnvironmentsLogUtility.getLogCallback(ambiance, false);
    if (EmptyPredicate.isEmpty(configFiles)) {
      logCallback.saveExecutionLog(
          "No config files configured in the service or in overrides. configFiles expressions will not work",
          LogLevel.WARN);
      return StepResponse.builder().status(Status.SKIPPED).build();
    }
    cdExpressionResolver.updateExpressions(ambiance, configFiles);

    publishRuntimeSecretUsage(ambiance, configFiles);

    JavaxValidator.validateBeanOrThrow(new ConfigFileValidatorDTO(configFiles));
    checkForAccessOrThrow(ambiance, configFiles);

    final ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    for (int i = 0; i < configFiles.size(); i++) {
      ConfigFileWrapper file = configFiles.get(i);
      ConfigFileAttributes spec = file.getConfigFile().getSpec();
      String identifier = file.getConfigFile().getIdentifier();
      validateConfigFileParametersAtRuntime(logCallback, spec, identifier);
      verifyConfigFileReference(identifier, spec, ambiance);
      configFilesOutcome.put(identifier, ConfigFileOutcomeMapper.toConfigFileOutcome(identifier, i + 1, spec));
    }

    sweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.CONFIG_FILES, configFilesOutcome, StepCategory.STAGE.name());

    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage) {
    final NgConfigFilesMetadataSweepingOutput configFilesSweepingOutput =
        fetchConfigFilesMetadataFromSweepingOutput(ambiance);

    final List<ConfigFileWrapper> configFiles = configFilesSweepingOutput.getFinalSvcConfigFiles();
    final Map<String, String> configFileLocation = configFilesSweepingOutput.getConfigFileLocation();
    final NGLogCallback logCallback = serviveEnvironmentsLogUtility.getLogCallback(ambiance, false);
    if (EmptyPredicate.isEmpty(configFiles)) {
      logCallback.saveExecutionLog(
          "No config files configured in the service or in overrides. configFiles expressions will not work",
          LogLevel.WARN);
      return AsyncExecutableResponse.newBuilder().build();
    }
    cdExpressionResolver.updateExpressions(ambiance, configFiles);
    JavaxValidator.validateBeanOrThrow(new ConfigFileValidatorDTO(configFiles));
    checkForAccessOrThrow(ambiance, configFiles);

    publishRuntimeSecretUsage(ambiance, configFiles);

    List<ConfigFileOutcome> gitConfigFilesOutcome = new ArrayList<>();
    List<ConfigFileOutcome> harnessConfigFilesOutcome = new ArrayList<>();
    for (int i = 0; i < configFiles.size(); i++) {
      ConfigFileWrapper file = configFiles.get(i);
      ConfigFileAttributes spec = file.getConfigFile().getSpec();
      String identifier = file.getConfigFile().getIdentifier();
      String fileLocation = configFileLocation != null ? configFileLocation.get(identifier) : null;
      validateConfigFileParametersAtRuntime(logCallback, spec, identifier);
      verifyConfigFileReference(identifier, spec, ambiance, fileLocation);
      ConfigFileOutcome configFileOutcome = ConfigFileOutcomeMapper.toConfigFileOutcome(identifier, i + 1, spec);
      if (ManifestStoreType.isInGitSubset(configFileOutcome.getStore().getKind())) {
        gitConfigFilesOutcome.add(configFileOutcome);
      } else if (ManifestStoreType.HARNESS.equals(configFileOutcome.getStore().getKind())) {
        harnessConfigFilesOutcome.add(configFileOutcome);
      } else {
        throw new InvalidRequestException(
            format("Invalid store kind for config file, configFileIdentifier: %s", configFileOutcome.getIdentifier()));
      }
    }

    Set<String> taskIds = new HashSet<>();
    List<StepDelegateInfo> stepDelegateInfos = new ArrayList<>();
    Map<String, ConfigFileOutcome> gitConfigFileOutcomesMapTaskIds = new HashMap<>();
    if (isNotEmpty(gitConfigFilesOutcome)) {
      for (ConfigFileOutcome gitConfigFileOutcome : gitConfigFilesOutcome) {
        String taskId = createGitDelegateTask(ambiance, gitConfigFileOutcome, logCallback);
        taskIds.add(taskId);
        stepDelegateInfos.add(StepDelegateInfo.builder()
                                  .taskName("Config File Task: " + gitConfigFileOutcome.getIdentifier())
                                  .taskId(taskId)
                                  .build());
        gitConfigFileOutcomesMapTaskIds.put(taskId, gitConfigFileOutcome);
      }
    }

    sweepingOutputService.consume(ambiance, CONFIG_FILES_STEP_V2,
        new ConfigFilesStepV2SweepingOutput(gitConfigFileOutcomesMapTaskIds, harnessConfigFilesOutcome),
        StepCategory.STAGE.name());

    if (isEmpty(taskIds)) {
      ConfigFilesOutcome configFilesOutcomes = new ConfigFilesOutcome();
      for (ConfigFileOutcome fileOutcome : harnessConfigFilesOutcome) {
        configFilesOutcomes.put(fileOutcome.getIdentifier(), fileOutcome);
      }
      sweepingOutputService.consume(
          ambiance, OutcomeExpressionConstants.CONFIG_FILES, configFilesOutcomes, StepCategory.STAGE.name());
    }

    serviceStepsHelper.publishTaskIdsStepDetailsForServiceStep(ambiance, stepDelegateInfos, CONFIG_FILES_STEP);

    return AsyncExecutableResponse.newBuilder().addAllCallbackIds(taskIds).build();
  }

  private void validateConfigFileParametersAtRuntime(
      NGLogCallback logCallback, ConfigFileAttributes spec, String identifier) {
    Set<String> invalidParameters =
        IndividualConfigFileStepValidator.validateConfigFileAttributes(identifier, spec, true);
    if (isNotEmpty(invalidParameters)) {
      logCallback.saveExecutionLog(
          String.format(
              "Values for following parameters for config file %s are either empty or not provided: {%s}. This may result in failure of deployment.",
              identifier, invalidParameters.stream().collect(Collectors.joining(","))),
          LogLevel.WARN);
    }
  }

  private void publishRuntimeSecretUsage(Ambiance ambiance, List<ConfigFileWrapper> configFiles) {
    if (EmptyPredicate.isEmpty(configFiles)) {
      return;
    }

    for (ConfigFileWrapper configFile : configFiles) {
      Set<VisitedSecretReference> secretReferences =
          configFile == null ? Set.of() : entityReferenceExtractorUtils.extractReferredSecrets(ambiance, configFile);

      secretRuntimeUsageService.createSecretRuntimeUsage(secretReferences,
          EntityUsageDetailProto.newBuilder()
              .setPipelineExecutionUsageData(PipelineExecutionUsageDataProto.newBuilder()
                                                 .setPlanExecutionId(ambiance.getPlanExecutionId())
                                                 .setStageExecutionId(ambiance.getStageExecutionId())
                                                 .build())
              .setUsageType(PIPELINE_EXECUTION)
              .build());
    }
  }

  private String createGitDelegateTask(
      final Ambiance ambiance, final ConfigFileOutcome configFileOutcome, LogCallback logCallback) {
    GitRequestFileConfig gitRequestFileConfig = getGitRequestFetchFileConfig(ambiance, configFileOutcome);
    String filePaths = gitRequestFileConfig.getGitStoreDelegateConfig() != null
            && gitRequestFileConfig.getGitStoreDelegateConfig().getPaths() != null
        ? String.join(", ", gitRequestFileConfig.getGitStoreDelegateConfig().getPaths())
        : StringUtils.EMPTY;
    logCallback.saveExecutionLog(LogHelper.color(
        format("Starting delegate task to fetch git config files: %s", filePaths), LogColor.Cyan, LogWeight.Bold));

    GitTaskNGRequest gitTaskNGRequest = GitTaskNGRequest.builder()
                                            .accountId(AmbianceUtils.getAccountId(ambiance))
                                            .gitRequestFileConfigs(Collections.singletonList(gitRequestFileConfig))
                                            .shouldOpenLogStream(true)
                                            .commandUnitName(CONFIG_FILE_COMMAND_UNIT)
                                            .closeLogStream(true)
                                            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CONFIG_FILE_GIT_TASK_TIMEOUT)
                                  .taskType(TaskType.GIT_TASK_NG.name())
                                  .parameters(new Object[] {gitTaskNGRequest})
                                  .build();

    TaskRequest taskRequest = TaskRequestsUtils.prepareTaskRequestWithTaskSelector(ambiance, taskData,
        referenceFalseKryoSerializer, TaskCategory.DELEGATE_TASK_V2, Collections.emptyList(), false,
        TaskType.GIT_TASK_NG.getDisplayName(), new ArrayList<>());

    final DelegateTaskRequest delegateTaskRequest =
        cdStepHelper.mapTaskRequestToDelegateTaskRequest(taskRequest, taskData, new HashSet<>(), "", false);

    return delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
  }

  private List<TaskSelector> getDelegateSelectorsFromGitConnector(StoreConfig storeConfig, Ambiance ambiance) {
    if (ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
      String connectorId = ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getConnectorRef());
      ConnectorInfoDTO connectorInfoDTO = cdStepHelper.getConnector(connectorId, ambiance);
      return cdStepHelper.getDelegateSelectors(connectorInfoDTO);
    }
    throw new InvalidRequestException("Invalid Store Config for delegate selector");
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, EmptyStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    final OptionalSweepingOutput outputOptional = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(CONFIG_FILES_STEP_V2));

    // If there are no config files that did not require a delegate task, we cannot skip here.
    if (isEmpty(responseDataMap) && !nonDelegateTaskExist(outputOptional)) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    final List<ErrorNotifyResponseData> failedResponses = responseDataMap.values()
                                                              .stream()
                                                              .filter(ErrorNotifyResponseData.class ::isInstance)
                                                              .map(ErrorNotifyResponseData.class ::cast)
                                                              .collect(Collectors.toList());

    if (isNotEmpty(failedResponses)) {
      log.error("Error notify response found for config files step " + failedResponses);
      return strategyHelper.handleException(failedResponses.get(0).getException());
    }

    if (!outputOptional.isFound()) {
      log.error(CONFIG_FILES_STEP_V2 + " sweeping output not found. Failing...");
      throw new InvalidRequestException("Unable to read config files");
    }

    ConfigFilesStepV2SweepingOutput configFilesStepV2SweepingOutput =
        (ConfigFilesStepV2SweepingOutput) outputOptional.getOutput();

    final NGLogCallback logCallback = serviveEnvironmentsLogUtility.getLogCallback(ambiance, false);
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    for (String taskId : responseDataMap.keySet()) {
      ConfigFileOutcome configFileOutcome =
          configFilesStepV2SweepingOutput.getGitConfigFileOutcomesMapTaskIds().get(taskId);
      GitTaskNGResponse taskResponse = (GitTaskNGResponse) responseDataMap.get(taskId);
      List<ConfigGitFile> gitFiles = getGitFilesFromGitTaskNGResponse(taskResponse);
      String identifier = configFileOutcome.getIdentifier();
      ConfigFileOutcome gitConfigFileOutcome = ConfigFileOutcome.builder()
                                                   .identifier(identifier)
                                                   .store(configFileOutcome.getStore())
                                                   .gitFiles(gitFiles)
                                                   .build();
      configFilesOutcome.put(identifier, gitConfigFileOutcome);
    }
    logCallback.saveExecutionLog(LogHelper.color("Fetched details of config files ", LogColor.Cyan, LogWeight.Bold));

    List<ConfigFileOutcome> harnessConfigFileOutcomes = configFilesStepV2SweepingOutput.getHarnessConfigFileOutcomes();

    for (ConfigFileOutcome harnessConfigFileOutcome : harnessConfigFileOutcomes) {
      configFilesOutcome.put(harnessConfigFileOutcome.getIdentifier(), harnessConfigFileOutcome);
    }
    sweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.CONFIG_FILES, configFilesOutcome, StepCategory.STAGE.name());

    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private List<ConfigGitFile> getGitFilesFromGitTaskNGResponse(GitTaskNGResponse gitTaskNGResponse) {
    List<GitFetchFilesResult> gitFetchFilesResultList = gitTaskNGResponse.getGitFetchFilesResults();
    List<ConfigGitFile> gitFiles = new ArrayList<>();
    for (GitFetchFilesResult gitFetchFilesResult : gitFetchFilesResultList) {
      gitFiles.addAll(configGitFilesMapper.getConfigGitFiles(gitFetchFilesResult.getFiles()));
    }
    return gitFiles;
  }

  private boolean nonDelegateTaskExist(OptionalSweepingOutput outputOptional) {
    return outputOptional != null && outputOptional.isFound()
        && isNotEmpty(
            ((ConfigFilesStepV2SweepingOutput) outputOptional.getOutput()).getGitConfigFileOutcomesMapTaskIds());
  }

  @Override
  public void handleAbort(Ambiance ambiance, EmptyStepParameters stepParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    final NGLogCallback logCallback = serviveEnvironmentsLogUtility.getLogCallback(ambiance, false);
    logCallback.saveExecutionLog(
        "Fetching Config Files Step was aborted", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
  }

  @NotNull
  private GitRequestFileConfig getGitRequestFetchFileConfig(Ambiance ambiance, ConfigFileOutcome configFileOutcome) {
    StoreConfig storeConfig = configFileOutcome.getStore();
    if (ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
      List<String> paths = ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getPaths());
      return GitRequestFileConfig.builder()
          .gitStoreDelegateConfig(
              getGitStoreDelegateConfig(gitStoreConfig, configFileOutcome.getIdentifier(), paths, ambiance))
          .identifier(configFileOutcome.getIdentifier())
          .succeedIfFileNotFound(false)
          .supportFolders(true)
          .build();
    }
    throw new InvalidRequestException(
        format("Invalid store kind for config file, configFileIdentifier: %s store kind: %s",
            configFileOutcome.getIdentifier(), storeConfig.getKind()));
  }

  @NotNull
  public GitStoreDelegateConfig getGitStoreDelegateConfig(
      @Nonnull GitStoreConfig gitStoreConfig, String identifier, List<String> paths, Ambiance ambiance) {
    String connectorId = ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getConnectorRef());
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);
    boolean optimizedFilesFetch =
        cdStepHelper.isOptimizedFilesFetch(connectorDTO, AmbianceUtils.getAccountId(ambiance));

    return cdStepHelper.getGitStoreDelegateConfig(
        gitStoreConfig, connectorDTO, paths, ambiance, null, identifier, optimizedFilesFetch);
  }

  @Data
  @Builder
  private static class ConfigFileValidatorDTO {
    @Valid List<ConfigFileWrapper> configFiles;
  }
}
