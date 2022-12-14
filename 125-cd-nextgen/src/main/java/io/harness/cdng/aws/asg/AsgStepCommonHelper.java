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
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static java.lang.String.format;

import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.beans.DelegateTask;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.aws.asg.AsgCommandRequest;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsgStepCommonHelper extends CDStepHelper {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private AsgStepHelper asgStepHelper;
  @Inject private CDStepHelper cdStepHelper;

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

    Map<String, Map<String, List<ManifestOutcome>>> storeManifestMap =
        asgStepHelper.getStoreManifestMap(manifestsOutcome.values());

    Map<String, List<String>> asgStoreManifestsContent =
        getManifestFilesContentFromHarnessStore(ambiance, storeManifestMap.get(ManifestStoreType.HARNESS), logCallback);

    TaskChainResponse taskChainResponse;
    if (areAllManifestsFromHarnessFileStore(storeManifestMap)) {
      taskChainResponse = prepareAsgTask(asgStepExecutor, ambiance, stepElementParameters, asgStoreManifestsContent,
          infrastructureOutcome, logCallback);
    } else {
      // TODO
      throw new RuntimeException("Not implemented yet");
    }

    return taskChainResponse;
  }

  public ManifestsOutcome resolveAsgManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    if (!manifestsOutcome.isFound()) {
      String stageName =
          AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
      String stepType =
          Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance)).map(StepType::getType).orElse("Asg");
      throw new GeneralException(
          format("No manifests found in stage %s. %s step requires a manifest defined in stage service definition",
              stageName, stepType));
    }
    return (ManifestsOutcome) manifestsOutcome.getOutcome();
  }

  private boolean areAllManifestsFromHarnessFileStore(
      Map<String, Map<String, List<ManifestOutcome>>> storeManifestMap) {
    Set<String> set = storeManifestMap.keySet();
    return set.size() == 1 && set.contains(ManifestStoreType.HARNESS);
  }

  private Map<String, List<String>> getManifestFilesContentFromHarnessStore(
      Ambiance ambiance, Map<String, List<ManifestOutcome>> manifestOutcomeMap, LogCallback logCallback) {
    Map<String, List<String>> contentMap = new HashMap<>();
    if (isEmpty(manifestOutcomeMap)) {
      return contentMap;
    }

    // Get Harness Store AsgLaunchTemplate file content
    List<ManifestOutcome> asgLaunchTemplates = manifestOutcomeMap.get(ManifestType.AsgLaunchTemplate);
    if (isNotEmpty(asgLaunchTemplates)) {
      ManifestOutcome asgLaunchTemplate = asgLaunchTemplates.get(0);
      String asgLaunchTemplateContent =
          cdStepHelper.fetchFilesContentFromLocalStore(ambiance, asgLaunchTemplate, logCallback).get(0);
      contentMap.put(ManifestType.AsgLaunchTemplate,
          Arrays.asList(renderExpressionsForManifestContent(asgLaunchTemplateContent, ambiance)));
    }

    // Get Harness Store AsgConfiguration file content
    List<ManifestOutcome> asgConfigurations = manifestOutcomeMap.get(ManifestType.AsgConfiguration);
    if (isNotEmpty(asgConfigurations)) {
      ManifestOutcome asgConfiguration = asgConfigurations.get(0);
      String asgConfigurationContent =
          cdStepHelper.fetchFilesContentFromLocalStore(ambiance, asgConfiguration, logCallback).get(0);
      contentMap.put(ManifestType.AsgConfiguration,
          Arrays.asList(renderExpressionsForManifestContent(asgConfigurationContent, ambiance)));
    }

    // Get Harness Store AsgScalingPolicy file content
    List<ManifestOutcome> asgScalingPolicies = manifestOutcomeMap.get(ManifestType.AsgScalingPolicy);
    if (isNotEmpty(asgScalingPolicies)) {
      List<String> asgScalingPolicyContentList =
          asgScalingPolicies.stream()
              .map(outcome -> {
                String content = cdStepHelper.fetchFilesContentFromLocalStore(ambiance, outcome, logCallback).get(0);
                return renderExpressionsForManifestContent(content, ambiance);
              })
              .collect(Collectors.toList());
      contentMap.put(ManifestType.AsgScalingPolicy, asgScalingPolicyContentList);
    }

    // Get Harness Store AsgScheduledUpdateGroupAction file content
    List<ManifestOutcome> asgScheduledUpdateGroupActions =
        manifestOutcomeMap.get(ManifestType.AsgScheduledUpdateGroupAction);
    if (isNotEmpty(asgScheduledUpdateGroupActions)) {
      List<String> asgScheduledUpdateGroupActionContentList =
          asgScheduledUpdateGroupActions.stream()
              .map(outcome -> {
                String content = cdStepHelper.fetchFilesContentFromLocalStore(ambiance, outcome, logCallback).get(0);
                return renderExpressionsForManifestContent(content, ambiance);
              })
              .collect(Collectors.toList());
      contentMap.put(ManifestType.AsgScheduledUpdateGroupAction, asgScheduledUpdateGroupActionContentList);
    }

    return contentMap;
  }

  // TODO adjust this as soon we decide Async vs Sync approach
  private void getManifestFilesContentFromGitStore(
      Ambiance ambiance, Map<String, List<ManifestOutcome>> manifestOutcomeMap) {
    UnitProgressData commandUnitsProgress = cdStepHelper.getCommandUnitProgressData(
        AsgCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);

    ManifestOutcome manifestOutcome = new ArrayList<>(manifestOutcomeMap.values()).get(0).get(0);
    String accountId = AmbianceUtils.getAccountId(ambiance);

    List<GitFetchFilesConfig> gitFetchFilesConfigs =
        getGitFetchFilesConfigFromManifestOutcome(manifestOutcome, ambiance);

    GitFetchRequest gitFetchRequest = GitFetchRequest.builder()
                                          .gitFetchFilesConfigs(gitFetchFilesConfigs)
                                          .accountId(accountId)
                                          .closeLogStream(true)
                                          .build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.GIT_FETCH_NEXT_GEN_TASK.name())
                                              .parameters(new Object[] {gitFetchRequest})
                                              .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                                              .build())
                                    .accountId(accountId)
                                    .setupAbstractions(new HashMap<>())
                                    .build();
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
        ambiance, gitStoreConfig, gitStoreConfig.getKind(), manifestOutcome.getIdentifier()));

    return gitFetchFilesConfigs;
  }

  private String renderExpressionsForManifestContent(String content, Ambiance ambiance) {
    return engineExpressionService.renderExpression(ambiance, content);
  }

  private TaskChainResponse prepareAsgTask(AsgStepExecutor asgStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, Map<String, List<String>> asgStoreManifestsContent,
      InfrastructureOutcome infrastructureOutcome, LogCallback logCallback) {
    logCallback.saveExecutionLog("Fetched all manifest files ", INFO, CommandExecutionStatus.SUCCESS);

    UnitProgressData unitProgressData = cdStepHelper.getCommandUnitProgressData(
        AsgCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);

    TaskChainResponse taskChainResponse;
    if (asgStepExecutor instanceof AsgCanaryDeployStep) {
      AsgExecutionPassThroughData executionPassThroughData = AsgExecutionPassThroughData.builder()
                                                                 .infrastructure(infrastructureOutcome)
                                                                 .lastActiveUnitProgressData(unitProgressData)
                                                                 .build();

      AsgStepExecutorParams asgStepExecutorParams = AsgStepExecutorParams.builder()
                                                        .shouldOpenFetchFilesLogStream(false)
                                                        .asgStoreManifestsContent(asgStoreManifestsContent)
                                                        .build();

      taskChainResponse = asgStepExecutor.executeAsgTask(
          ambiance, stepElementParameters, executionPassThroughData, unitProgressData, asgStepExecutorParams);
    } else {
      // TODO
      throw new RuntimeException("Not implemented yet");
    }
    return taskChainResponse;
  }

  public TaskChainResponse queueAsgTask(StepElementParameters stepElementParameters, AsgCommandRequest commandRequest,
      Ambiance ambiance, PassThroughData passThroughData, boolean isChainEnd) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {commandRequest})
                            .taskType(TaskType.AWS_ASG_CANARY_DEPLOY_TASK_NG.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();

    String taskName = TaskType.AWS_ASG_CANARY_DEPLOY_TASK_NG.getDisplayName() + " : " + commandRequest.getCommandName();

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
}
