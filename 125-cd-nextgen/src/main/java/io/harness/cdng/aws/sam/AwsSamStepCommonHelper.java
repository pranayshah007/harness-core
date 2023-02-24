/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.aws.sam.beans.AwsSamGitFetchFailurePassThroughData;
import io.harness.cdng.aws.sam.beans.AwsSamStepExecutorParams;
import io.harness.cdng.aws.sam.validateBuildPackage.AwsSamValidateBuildPackageStep;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.serverless.ServerlessStepHelper;
import io.harness.cdng.serverless.ServerlessStepUtils;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.aws.sam.AwsSamFilePathContentConfig;
import io.harness.delegate.task.aws.sam.AwsSamGitFetchFileConfig;
import io.harness.delegate.task.aws.sam.AwsSamGitFetchFilesResult;
import io.harness.delegate.task.aws.sam.AwsSamManifestConfig;
import io.harness.delegate.task.aws.sam.request.AwsSamCommandRequest;
import io.harness.delegate.task.aws.sam.request.AwsSamGitFetchRequest;
import io.harness.delegate.task.aws.sam.response.AwsSamCommandResponse;
import io.harness.delegate.task.aws.sam.response.AwsSamGitFetchResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
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
import io.harness.serializer.KryoSerializer;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AwsSamStepCommonHelper extends AwsSamStepUtils {
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private AwsSamStepHelper awsSamStepHelper;

  public TaskChainResponse startChainLink(Ambiance ambiance, StepElementParameters stepElementParameters) {
    // Get ManifestsOutcome
    ManifestsOutcome manifestsOutcome = resolveEcsManifestsOutcome(ambiance);

    // Get InfrastructureOutcome
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    // Update expressions in ManifestsOutcome
    ExpressionEvaluatorUtils.updateExpressions(
        manifestsOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

    // Validate ManifestsOutcome
    validateManifestsOutcome(ambiance, manifestsOutcome);

    ManifestOutcome awsSamManifestOutcome = awsSamStepHelper.getAwsSamManifestOutcome(manifestsOutcome.values());
    AwsSamStepPassThroughData awsSamStepPassThroughData = AwsSamStepPassThroughData.builder()
                                                              .infrastructureOutcome(infrastructureOutcome)
                                                              .manifestOutcome(awsSamManifestOutcome)
                                                              .build();

    TaskChainResponse taskChainResponse = null;
    if (isGitManifest(awsSamManifestOutcome)) {
      taskChainResponse = prepareAwsSamManifestGitFetchTask(
          ambiance, stepElementParameters, infrastructureOutcome, awsSamManifestOutcome, awsSamStepPassThroughData);
    }

    return taskChainResponse;
  }

  private TaskChainResponse prepareAwsSamManifestGitFetchTask(Ambiance ambiance,
      StepElementParameters stepElementParameters, InfrastructureOutcome infrastructureOutcome,
      ManifestOutcome manifestOutcome, AwsSamStepPassThroughData awsSamStepPassThroughData) {
    StoreConfig storeConfig = manifestOutcome.getStore();
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for AWS SAM step", USER);
    }
    AwsSamGitFetchFileConfig awsSamGitFetchFileConfig =
        getGitFetchFilesConfig(ambiance, gitStoreConfig, manifestOutcome);

    return getGitFetchFileTaskResponse(
        ambiance, true, stepElementParameters, awsSamStepPassThroughData, awsSamGitFetchFileConfig);
  }
  private TaskChainResponse getGitFetchFileTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
      StepElementParameters stepElementParameters, AwsSamStepPassThroughData awsSamStepPassThroughData,
      AwsSamGitFetchFileConfig awsSamGitFetchFilesConfig) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    AwsSamGitFetchRequest awsSamGitFetchRequest = AwsSamGitFetchRequest.builder()
                                                      .accountId(accountId)
                                                      .awsSamGitFetchFileConfig(awsSamGitFetchFilesConfig)
                                                      .shouldOpenLogStream(shouldOpenLogStream)
                                                      .build();
    return queueGitTask(stepElementParameters, awsSamGitFetchRequest, ambiance, awsSamStepPassThroughData, false,
        TaskType.AWS_SAM_GIT_FETCH);
  }

  private AwsSamGitFetchFileConfig getGitFetchFilesConfig(
      Ambiance ambiance, GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome) {
    return AwsSamGitFetchFileConfig.builder()
        .gitStoreDelegateConfig(getGitStoreDelegateConfig(ambiance, gitStoreConfig, manifestOutcome))
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .templateFilePath(awsSamStepHelper.getTemplateFilePath(manifestOutcome))
        .configFilePath(awsSamStepHelper.getConfigFilePath(manifestOutcome))
        .succeedIfFileNotFound(false)
        .build();
  }

  public AwsSamManifestConfig getManifestConfig(
      Ambiance ambiance, ManifestOutcome manifestOutcome, AwsSamStepExecutorParams awsSamStepExecutorParams) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) manifestOutcome.getStore();

    return AwsSamManifestConfig.builder()
        .gitStoreDelegateConfig(getGitStoreDelegateConfig(ambiance, gitStoreConfig, manifestOutcome))
        .samTemplateFilePath(awsSamStepExecutorParams.getTemplateFilePath())
        .samConfigFilePath(awsSamStepExecutorParams.getConfigFilePath())
        .build();
  }

  public ManifestsOutcome resolveEcsManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    if (!manifestsOutcome.isFound()) {
      String stageName =
          AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
      String stepType =
          Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance)).map(StepType::getType).orElse("Ecs");
      throw new GeneralException(
          format("No manifests found in stage %s. %s step requires a manifest defined in stage service definition",
              stageName, stepType));
    }
    return (ManifestsOutcome) manifestsOutcome.getOutcome();
  }

  public TaskChainResponse queueTask(StepElementParameters stepElementParameters,
      AwsSamCommandRequest awsSamCommandRequest, Ambiance ambiance, PassThroughData passThroughData, boolean isChainEnd,
      TaskType taskType) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {awsSamCommandRequest})
                            .taskType(taskType.toString())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();
    String taskName = taskType.getDisplayName();
    AwsSamSpecParameters awsSamSpecParameters = (AwsSamSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, awsSamSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(awsSamSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  public TaskChainResponse executeNextLink(AwsSamStepExecutor awsSamStepExecutor, Ambiance ambiance,
      StepElementParameters stepParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    try {
      if (responseData instanceof AwsSamGitFetchResponse) {
        AwsSamGitFetchResponse awsSamGitFetchResponse = (AwsSamGitFetchResponse) responseData;
        return handleAwsSamGitFetchFilesResponse(awsSamStepExecutor, awsSamGitFetchResponse, ambiance, stepParameters,
            (AwsSamStepPassThroughData) passThroughData);
      }
    } catch (Exception ex) {
      throw ex;
    }
    return null;
  }

  private TaskChainResponse handleAwsSamGitFetchFilesResponse(AwsSamStepExecutor awsSamStepExecutor,
      AwsSamGitFetchResponse awsSamGitFetchResponse, Ambiance ambiance, StepElementParameters stepElementParameters,
      AwsSamStepPassThroughData awsSamStepPassThroughData) {
    if (awsSamGitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      AwsSamGitFetchFailurePassThroughData awsSamGitFetchFailurePassThroughData =
          AwsSamGitFetchFailurePassThroughData.builder()
              .errorMsg(awsSamGitFetchResponse.getErrorMessage())
              .unitProgressData(awsSamGitFetchResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().passThroughData(awsSamGitFetchFailurePassThroughData).chainEnd(true).build();
    }
    AwsSamGitFetchFilesResult awsSamGitFetchFilesResult = awsSamGitFetchResponse.getAwsSamGitFetchFilesResult();
    if (awsSamGitFetchFilesResult.getTemplateFileResult() == null) {
      throw new GeneralException("Found No Manifest Content from AWS SAM git fetch task");
    }
    AwsSamFilePathContentConfig templateFilePathRenderContentConfig = awsSamStepHelper.getFilePathRenderedContent(
        awsSamGitFetchFilesResult.getTemplateFileResult(), awsSamStepPassThroughData.getManifestOutcome(), ambiance);
    AwsSamFilePathContentConfig configFilePathRenderContentConfig = null;
    if (awsSamGitFetchFilesResult.getConfigFileResult() != null) {
      configFilePathRenderContentConfig = awsSamStepHelper.getFilePathRenderedContent(
          awsSamGitFetchFilesResult.getConfigFileResult(), awsSamStepPassThroughData.getManifestOutcome(), ambiance);
    }

    AwsSamStepExecutorParams awsSamStepExecutorParams =
        AwsSamStepExecutorParams.builder()
            .templateFilePath(templateFilePathRenderContentConfig.getFilePath())
            .templateFileContent(templateFilePathRenderContentConfig.getFileContent())
            .configFilePath(configFilePathRenderContentConfig.getFilePath())
            .configFileContent(configFilePathRenderContentConfig.getFileContent())
            .build();

    if (awsSamStepExecutor instanceof AwsSamValidateBuildPackageStep) {
      return ((AwsSamValidateBuildPackageStep) awsSamStepExecutor)
          .prepareAwsSamTask(ambiance, stepElementParameters, awsSamStepPassThroughData,
              awsSamGitFetchResponse.getUnitProgressData(), awsSamStepExecutorParams);
    }
    throw new GeneralException("Invalid AwsSamStepExecutor");
  }

  public TaskChainResponse queueGitTask(StepElementParameters stepElementParameters,
      AwsSamGitFetchRequest awsSamGitFetchRequest, Ambiance ambiance, PassThroughData passThroughData,
      boolean isChainEnd, TaskType taskType) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {awsSamGitFetchRequest})
                            .taskType(taskType.toString())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();
    String taskName = taskType.getDisplayName();
    AwsSamSpecParameters awsSamSpecParameters = (AwsSamSpecParameters) stepElementParameters.getSpec();
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, awsSamSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(
            emptyIfNull(getParameterFieldValue(awsSamSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  public static String getErrorMessage(AwsSamCommandResponse awsSamCommandResponse) {
    return awsSamCommandResponse.getErrorMessage() == null ? "" : awsSamCommandResponse.getErrorMessage();
  }
}
