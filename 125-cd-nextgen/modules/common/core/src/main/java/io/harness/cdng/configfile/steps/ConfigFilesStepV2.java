/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.cdng.CDStepHelper;
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
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;
import io.harness.validation.JavaxValidator;

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
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

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
  static final int CONFIG_FILE_GIT_TASK_TIMEOUT = 10;

  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private ConfigGitFilesMapper configGitFilesMapper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StrategyHelper strategyHelper;
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
    final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    if (EmptyPredicate.isEmpty(configFiles)) {
      logCallback.saveExecutionLog(
          String.format("No config files configured in the service. <+%s> expressions will not work",
              OutcomeExpressionConstants.CONFIG_FILES),
          LogLevel.WARN);
      return StepResponse.builder().status(Status.SKIPPED).build();
    }
    cdExpressionResolver.updateExpressions(ambiance, configFiles);

    JavaxValidator.validateBeanOrThrow(new ConfigFileValidatorDTO(configFiles));
    checkForAccessOrThrow(ambiance, configFiles);

    final ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    for (int i = 0; i < configFiles.size(); i++) {
      ConfigFileWrapper file = configFiles.get(i);
      ConfigFileAttributes spec = file.getConfigFile().getSpec();
      String identifier = file.getConfigFile().getIdentifier();
      IndividualConfigFileStepValidator.validateConfigFileAttributes(identifier, spec, true);
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
    final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    if (EmptyPredicate.isEmpty(configFiles)) {
      logCallback.saveExecutionLog(
          String.format("No config files configured in the service. <+%s> expressions will not work",
              OutcomeExpressionConstants.CONFIG_FILES),
          LogLevel.WARN);
      return AsyncExecutableResponse.newBuilder().setStatus(Status.SKIPPED).build();
    }
    cdExpressionResolver.updateExpressions(ambiance, configFiles);
    JavaxValidator.validateBeanOrThrow(new ConfigFileValidatorDTO(configFiles));
    checkForAccessOrThrow(ambiance, configFiles);

    List<ConfigFileOutcome> gitConfigFilesOutcome = new ArrayList<>();
    List<ConfigFileOutcome> harnessConfigFilesOutcome = new ArrayList<>();
    for (int i = 0; i < configFiles.size(); i++) {
      ConfigFileWrapper file = configFiles.get(i);
      ConfigFileAttributes spec = file.getConfigFile().getSpec();
      String identifier = file.getConfigFile().getIdentifier();
      IndividualConfigFileStepValidator.validateConfigFileAttributes(identifier, spec, true);
      verifyConfigFileReference(identifier, spec, ambiance);
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
    Map<String, ConfigFileOutcome> gitConfigFileOutcomesMapTaskIds = new HashMap<>();
    if (isNotEmpty(gitConfigFilesOutcome)) {
      for (ConfigFileOutcome gitConfigFileOutcome : gitConfigFilesOutcome) {
        String taskId = createGitDelegateTask(ambiance, gitConfigFileOutcome, logCallback);
        taskIds.add(taskId);
        gitConfigFileOutcomesMapTaskIds.put(taskId, gitConfigFileOutcome);
      }
    }

    sweepingOutputService.consume(ambiance, CONFIG_FILES_STEP_V2,
        new ConfigFilesStepV2SweepingOutput(gitConfigFileOutcomesMapTaskIds, harnessConfigFilesOutcome),
        StepCategory.STAGE.name());

    return AsyncExecutableResponse.newBuilder().addAllCallbackIds(taskIds).setStatus(Status.SUCCEEDED).build();
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

    final List<TaskSelector> delegateSelectors =
        getDelegateSelectorsFromGitConnector(configFileOutcome.getStore(), ambiance);

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
        TaskType.GIT_TASK_NG.getDisplayName(), delegateSelectors);

    final DelegateTaskRequest delegateTaskRequest = cdStepHelper.mapTaskRequestToDelegateTaskRequest(taskRequest,
        taskData, delegateSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toSet()), "", false);

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

    final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
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
  public void handleAbort(
      Ambiance ambiance, EmptyStepParameters stepParameters, AsyncExecutableResponse executableResponse) {
    final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
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
