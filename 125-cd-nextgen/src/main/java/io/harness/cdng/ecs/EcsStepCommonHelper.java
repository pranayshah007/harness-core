/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;

import static java.lang.String.format;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.ecs.beans.EcsBlueGreenPrepareRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchPassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsRollingRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsRollingRollbackDataOutcome.EcsRollingRollbackDataOutcomeBuilder;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ecs.EcsBlueGreenCreateServiceResult;
import io.harness.delegate.beans.ecs.EcsBlueGreenPrepareRollbackDataResult;
import io.harness.delegate.beans.ecs.EcsBlueGreenRollbackResult;
import io.harness.delegate.beans.ecs.EcsBlueGreenSwapTargetGroupsResult;
import io.harness.delegate.beans.ecs.EcsCanaryDeployResult;
import io.harness.delegate.beans.ecs.EcsPrepareRollbackDataResult;
import io.harness.delegate.beans.ecs.EcsRollingDeployResult;
import io.harness.delegate.beans.ecs.EcsRollingRollbackResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.EcsTaskToServerInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.ecs.EcsGitFetchFileConfig;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsGitFetchRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenCreateServiceResponse;
import io.harness.delegate.task.ecs.response.EcsBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsBlueGreenRollbackResponse;
import io.harness.delegate.task.ecs.response.EcsBlueGreenSwapTargetGroupsResponse;
import io.harness.delegate.task.ecs.response.EcsCanaryDeployResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsGitFetchResponse;
import io.harness.delegate.task.ecs.response.EcsPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.delegate.task.ecs.response.EcsRollingRollbackResponse;
import io.harness.delegate.task.git.TaskStatus;
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

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.hibernate.validator.constraints.NotEmpty;

public class EcsStepCommonHelper extends EcsStepUtils {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private EcsEntityHelper ecsEntityHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  private static final String TARGET_GROUP_ARN_EXPRESSION = "<+targetGroupArn>";

  public TaskChainResponse startChainLink(
      Ambiance ambiance, StepElementParameters stepElementParameters, EcsStepHelper ecsStepHelper) {
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

    List<ManifestOutcome> ecsManifestOutcome = getEcsManifestOutcome(manifestsOutcome.values(), ecsStepHelper);

    return prepareEcsManifestGitFetchTask(
        ambiance, stepElementParameters, infrastructureOutcome, ecsManifestOutcome, ecsStepHelper);
  }

  public List<ManifestOutcome> getEcsManifestOutcome(
      @NotEmpty Collection<ManifestOutcome> manifestOutcomes, EcsStepHelper ecsStepHelper) {
    return ecsStepHelper.getEcsManifestOutcome(manifestOutcomes);
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

  private TaskChainResponse prepareEcsManifestGitFetchTask(Ambiance ambiance,
      StepElementParameters stepElementParameters, InfrastructureOutcome infrastructureOutcome,
      List<ManifestOutcome> ecsManifestOutcomes, EcsStepHelper ecsStepHelper) {
    // Get EcsGitFetchFileConfig for task definition
    ManifestOutcome ecsTaskDefinitionManifestOutcome =
        ecsStepHelper.getEcsTaskDefinitionManifestOutcome(ecsManifestOutcomes);

    EcsGitFetchFileConfig ecsTaskDefinitionGitFetchFileConfig =
        getEcsGitFetchFilesConfigFromManifestOutcome(ecsTaskDefinitionManifestOutcome, ambiance, ecsStepHelper);

    // Get EcsGitFetchFileConfig for service definition
    ManifestOutcome ecsServiceDefinitionManifestOutcome =
        ecsStepHelper.getEcsServiceDefinitionManifestOutcome(ecsManifestOutcomes);

    EcsGitFetchFileConfig ecsServiceDefinitionGitFetchFileConfig =
        getEcsGitFetchFilesConfigFromManifestOutcome(ecsServiceDefinitionManifestOutcome, ambiance, ecsStepHelper);

    // Get EcsGitFetchFileConfig list for scalable targets if present
    List<EcsGitFetchFileConfig> ecsScalableTargetGitFetchFileConfigs = null;

    List<ManifestOutcome> ecsScalableTargetManifestOutcomes =
        ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalableTargetDefinition);

    if (CollectionUtils.isNotEmpty(ecsScalableTargetManifestOutcomes)) {
      ecsScalableTargetGitFetchFileConfigs =
          ecsScalableTargetManifestOutcomes.stream()
              .map(manifestOutcome
                  -> getEcsGitFetchFilesConfigFromManifestOutcome(manifestOutcome, ambiance, ecsStepHelper))
              .collect(Collectors.toList());
    }

    // Get EcsGitFetchFileConfig list for scaling policies if present
    List<EcsGitFetchFileConfig> ecsScalingPolicyGitFetchFileConfigs = null;

    List<ManifestOutcome> ecsScalingPolicyManifestOutcomes =
        ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalingPolicyDefinition);

    if (CollectionUtils.isNotEmpty(ecsScalingPolicyManifestOutcomes)) {
      ecsScalingPolicyGitFetchFileConfigs =
          ecsScalingPolicyManifestOutcomes.stream()
              .map(manifestOutcome
                  -> getEcsGitFetchFilesConfigFromManifestOutcome(manifestOutcome, ambiance, ecsStepHelper))
              .collect(Collectors.toList());
    }

    EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
        EcsGitFetchPassThroughData.builder().infrastructureOutcome(infrastructureOutcome).build();

    return getGitFetchFileTaskResponse(ambiance, true, stepElementParameters, ecsGitFetchPassThroughData,
        ecsTaskDefinitionGitFetchFileConfig, ecsServiceDefinitionGitFetchFileConfig,
        ecsScalableTargetGitFetchFileConfigs, ecsScalingPolicyGitFetchFileConfigs);
  }

  private EcsGitFetchFileConfig getEcsGitFetchFilesConfigFromManifestOutcome(
      ManifestOutcome manifestOutcome, Ambiance ambiance, EcsStepHelper ecsStepHelper) {
    StoreConfig storeConfig = manifestOutcome.getStore();
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Ecs step", USER);
    }
    return getEcsGitFetchFilesConfig(ambiance, gitStoreConfig, manifestOutcome, ecsStepHelper);
  }

  private EcsGitFetchFileConfig getEcsGitFetchFilesConfig(
      Ambiance ambiance, GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome, EcsStepHelper ecsStepHelper) {
    return EcsGitFetchFileConfig.builder()
        .gitStoreDelegateConfig(getGitStoreDelegateConfig(ambiance, gitStoreConfig, manifestOutcome))
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .succeedIfFileNotFound(false)
        .build();
  }

  private TaskChainResponse getGitFetchFileTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
      StepElementParameters stepElementParameters, EcsGitFetchPassThroughData ecsGitFetchPassThroughData,
      EcsGitFetchFileConfig ecsTaskDefinitionGitFetchFileConfig,
      EcsGitFetchFileConfig ecsServiceDefinitionGitFetchFileConfig,
      List<EcsGitFetchFileConfig> ecsScalableTargetGitFetchFileConfigs,
      List<EcsGitFetchFileConfig> ecsScalingPolicyGitFetchFileConfigs) {
    String accountId = AmbianceUtils.getAccountId(ambiance);

    EcsGitFetchRequest ecsGitFetchRequest =
        EcsGitFetchRequest.builder()
            .accountId(accountId)
            .ecsTaskDefinitionGitFetchFileConfig(ecsTaskDefinitionGitFetchFileConfig)
            .ecsServiceDefinitionGitFetchFileConfig(ecsServiceDefinitionGitFetchFileConfig)
            .ecsScalableTargetGitFetchFileConfigs(ecsScalableTargetGitFetchFileConfigs)
            .ecsScalingPolicyGitFetchFileConfigs(ecsScalingPolicyGitFetchFileConfigs)
            .shouldOpenLogStream(shouldOpenLogStream)
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.ECS_GIT_FETCH_TASK_NG.name())
                                  .parameters(new Object[] {ecsGitFetchRequest})
                                  .build();

    String taskName = TaskType.ECS_GIT_FETCH_TASK_NG.getDisplayName();

    EcsSpecParameters ecsSpecParameters = (EcsSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        ecsSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(ecsSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(ecsGitFetchPassThroughData)
        .build();
  }

  public TaskChainResponse executeNextLinkRolling(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier, EcsStepHelper ecsStepHelper) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    UnitProgressData unitProgressData = null;
    TaskChainResponse taskChainResponse = null;
    try {
      if (responseData instanceof EcsGitFetchResponse) { // if EcsGitFetchResponse is received

        EcsGitFetchResponse ecsGitFetchResponse = (EcsGitFetchResponse) responseData;
        EcsGitFetchPassThroughData ecsGitFetchPassThroughData = (EcsGitFetchPassThroughData) passThroughData;

        taskChainResponse = handleEcsGitFetchFilesResponseRolling(ecsGitFetchResponse, ecsStepExecutor, ambiance,
            stepElementParameters, ecsGitFetchPassThroughData, ecsStepHelper);

      } else if (responseData
          instanceof EcsPrepareRollbackDataResponse) { // if EcsPrepareRollbackDataResponse is received

        EcsPrepareRollbackDataResponse ecsPrepareRollbackDataResponse = (EcsPrepareRollbackDataResponse) responseData;
        EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData =
            (EcsPrepareRollbackDataPassThroughData) passThroughData;

        taskChainResponse = handleEcsPrepareRollbackDataResponseRolling(
            ecsPrepareRollbackDataResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsStepPassThroughData);
      }
    } catch (Exception e) {
      taskChainResponse =
          TaskChainResponse.builder()
              .chainEnd(true)
              .passThroughData(
                  EcsStepExceptionPassThroughData.builder()
                      .errorMessage(ExceptionUtils.getMessage(e))
                      .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
                      .build())
              .build();
    }

    return taskChainResponse;
  }

  public TaskChainResponse executeNextLinkCanary(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier, EcsStepHelper ecsStepHelper) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    UnitProgressData unitProgressData = null;
    TaskChainResponse taskChainResponse = null;
    try {
      if (responseData instanceof EcsGitFetchResponse) { // if EcsGitFetchResponse is received

        EcsGitFetchResponse ecsGitFetchResponse = (EcsGitFetchResponse) responseData;
        EcsGitFetchPassThroughData ecsGitFetchPassThroughData = (EcsGitFetchPassThroughData) passThroughData;

        taskChainResponse = handleEcsGitFetchFilesResponseCanary(ecsGitFetchResponse, ecsStepExecutor, ambiance,
            stepElementParameters, ecsGitFetchPassThroughData, ecsStepHelper);
      }
    } catch (Exception e) {
      taskChainResponse =
          TaskChainResponse.builder()
              .chainEnd(true)
              .passThroughData(
                  EcsStepExceptionPassThroughData.builder()
                      .errorMessage(ExceptionUtils.getMessage(e))
                      .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
                      .build())
              .build();
    }

    return taskChainResponse;
  }

  public TaskChainResponse executeNextLinkBlueGreen(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier, EcsStepHelper ecsStepHelper) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    UnitProgressData unitProgressData = null;
    TaskChainResponse taskChainResponse = null;
    try {
      if (responseData instanceof EcsGitFetchResponse) { // if EcsGitFetchResponse is received

        EcsGitFetchResponse ecsGitFetchResponse = (EcsGitFetchResponse) responseData;
        EcsGitFetchPassThroughData ecsGitFetchPassThroughData = (EcsGitFetchPassThroughData) passThroughData;

        taskChainResponse = handleEcsGitFetchFilesResponseBlueGreen(
            ecsGitFetchResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsGitFetchPassThroughData);

      } else if (responseData
          instanceof EcsBlueGreenPrepareRollbackDataResponse) { // if EcsBlueGreenPrepareRollbackDataResponse is
                                                                // received

        EcsBlueGreenPrepareRollbackDataResponse ecsBlueGreenPrepareRollbackDataResponse =
            (EcsBlueGreenPrepareRollbackDataResponse) responseData;
        EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData =
            (EcsPrepareRollbackDataPassThroughData) passThroughData;

        taskChainResponse = handleEcsBlueGreenPrepareRollbackDataResponse(ecsBlueGreenPrepareRollbackDataResponse,
            ecsStepExecutor, ambiance, stepElementParameters, ecsStepPassThroughData);
      }
    } catch (Exception e) {
      taskChainResponse =
          TaskChainResponse.builder()
              .chainEnd(true)
              .passThroughData(
                  EcsStepExceptionPassThroughData.builder()
                      .errorMessage(ExceptionUtils.getMessage(e))
                      .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
                      .build())
              .build();
    }

    return taskChainResponse;
  }

  public EcsInfraConfig getEcsInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return ecsEntityHelper.getEcsInfraConfig(infrastructure, ngAccess);
  }

  private TaskChainResponse handleEcsGitFetchFilesResponseRolling(EcsGitFetchResponse ecsGitFetchResponse,
      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsGitFetchPassThroughData ecsGitFetchPassThroughData, EcsStepHelper ecsStepHelper) {
    if (ecsGitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureGitTask(ecsGitFetchResponse);
    }

    // Get ecsTaskDefinitionFileContent from ecsGitFetchResponse
    String ecsTaskDefinitionFileContent = getRenderedTaskDefinitionFileContent(ecsGitFetchResponse, ambiance);

    // Get ecsServiceDefinitionFetchFileResult from ecsGitFetchResponse
    String ecsServiceDefinitionFileContent = getRenderedServiceDefinitionFileContent(ecsGitFetchResponse, ambiance);

    // Get ecsScalableTargetManifestContentList from ecsGitFetchResponse if present
    List<String> ecsScalableTargetManifestContentList =
        getRenderedScalableTargetsFileContent(ecsGitFetchResponse, ambiance);

    // Get ecsScalingPolicyManifestContentList from ecsGitFetchResponse if present
    List<String> ecsScalingPolicyManifestContentList =
        getRenderedScalingPoliciesFileContent(ecsGitFetchResponse, ambiance);

    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
            .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
            .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
            .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
            .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
            .build();

    return ecsStepExecutor.executeEcsPrepareRollbackTask(ambiance, stepElementParameters,
        ecsPrepareRollbackDataPassThroughData, ecsGitFetchResponse.getUnitProgressData());
  }

  private TaskChainResponse handleEcsGitFetchFilesResponseCanary(EcsGitFetchResponse ecsGitFetchResponse,
      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsGitFetchPassThroughData ecsGitFetchPassThroughData, EcsStepHelper ecsStepHelper) {
    if (ecsGitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureGitTask(ecsGitFetchResponse);
    }

    // Get ecsTaskDefinitionFileContent from ecsGitFetchResponse
    String ecsTaskDefinitionFileContent = getRenderedTaskDefinitionFileContent(ecsGitFetchResponse, ambiance);

    // Get ecsServiceDefinitionFetchFileResult from ecsGitFetchResponse
    String ecsServiceDefinitionFileContent = getRenderedServiceDefinitionFileContent(ecsGitFetchResponse, ambiance);

    // Get ecsScalableTargetManifestContentList from ecsGitFetchResponse if present
    List<String> ecsScalableTargetManifestContentList =
        getRenderedScalableTargetsFileContent(ecsGitFetchResponse, ambiance);

    // Get ecsScalingPolicyManifestContentList from ecsGitFetchResponse if present
    List<String> ecsScalingPolicyManifestContentList =
        getRenderedScalingPoliciesFileContent(ecsGitFetchResponse, ambiance);

    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(ecsGitFetchPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(ecsGitFetchResponse.getUnitProgressData())
            .build();

    EcsStepExecutorParams ecsStepExecutorParams =
        EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
            .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
            .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
            .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
            .build();

    return ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
        ecsGitFetchResponse.getUnitProgressData(), ecsStepExecutorParams);
  }

  private TaskChainResponse handleEcsGitFetchFilesResponseBlueGreen(EcsGitFetchResponse ecsGitFetchResponse,
      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
    if (ecsGitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureGitTask(ecsGitFetchResponse);
    }

    // Get ecsTaskDefinitionFileContent from ecsGitFetchResponse
    String ecsTaskDefinitionFileContent = getRenderedTaskDefinitionFileContent(ecsGitFetchResponse, ambiance);

    // Get ecsServiceDefinitionFetchFileResult from ecsGitFetchResponse
    FetchFilesResult ecsServiceDefinitionFetchFileResult =
        ecsGitFetchResponse.getEcsServiceDefinitionFetchFilesResult();
    String ecsServiceDefinitionFileContent = ecsServiceDefinitionFetchFileResult.getFiles().get(0).getFileContent();
    long timeStamp = System.currentTimeMillis();
    StringBuilder key = new StringBuilder().append(timeStamp).append("targetGroup");
    if (ecsServiceDefinitionFileContent.contains(TARGET_GROUP_ARN_EXPRESSION)) {
      ecsServiceDefinitionFileContent =
          ecsServiceDefinitionFileContent.replace(TARGET_GROUP_ARN_EXPRESSION, key.toString());
    }
    ecsServiceDefinitionFileContent =
        engineExpressionService.renderExpression(ambiance, ecsServiceDefinitionFileContent);

    // Get ecsScalableTargetManifestContentList from ecsGitFetchResponse if present
    List<String> ecsScalableTargetManifestContentList =
        getRenderedScalableTargetsFileContent(ecsGitFetchResponse, ambiance);

    // Get ecsScalingPolicyManifestContentList from ecsGitFetchResponse if present
    List<String> ecsScalingPolicyManifestContentList =
        getRenderedScalingPoliciesFileContent(ecsGitFetchResponse, ambiance);

    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
            .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
            .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
            .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
            .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
            .targetGroupArnKey(key.toString())
            .build();

    return ecsStepExecutor.executeEcsPrepareRollbackTask(ambiance, stepElementParameters,
        ecsPrepareRollbackDataPassThroughData, ecsGitFetchResponse.getUnitProgressData());
  }

  private TaskChainResponse handleFailureGitTask(EcsGitFetchResponse ecsGitFetchResponse) {
    EcsGitFetchFailurePassThroughData ecsGitFetchFailurePassThroughData =
        EcsGitFetchFailurePassThroughData.builder()
            .errorMsg(ecsGitFetchResponse.getErrorMessage())
            .unitProgressData(ecsGitFetchResponse.getUnitProgressData())
            .build();
    return TaskChainResponse.builder().passThroughData(ecsGitFetchFailurePassThroughData).chainEnd(true).build();
  }

  private String getRenderedTaskDefinitionFileContent(EcsGitFetchResponse ecsGitFetchResponse, Ambiance ambiance) {
    FetchFilesResult ecsTaskDefinitionFetchFileResult = ecsGitFetchResponse.getEcsTaskDefinitionFetchFilesResult();
    String ecsTaskDefinitionFileContent = ecsTaskDefinitionFetchFileResult.getFiles().get(0).getFileContent();
    return engineExpressionService.renderExpression(ambiance, ecsTaskDefinitionFileContent);
  }

  private String getRenderedServiceDefinitionFileContent(EcsGitFetchResponse ecsGitFetchResponse, Ambiance ambiance) {
    FetchFilesResult ecsServiceDefinitionFetchFileResult =
        ecsGitFetchResponse.getEcsServiceDefinitionFetchFilesResult();
    String ecsServiceDefinitionFileContent = ecsServiceDefinitionFetchFileResult.getFiles().get(0).getFileContent();
    return engineExpressionService.renderExpression(ambiance, ecsServiceDefinitionFileContent);
  }

  private List<String> getRenderedScalableTargetsFileContent(
      EcsGitFetchResponse ecsGitFetchResponse, Ambiance ambiance) {
    List<String> ecsScalableTargetManifestContentList = null;
    List<FetchFilesResult> ecsScalableTargetFetchFilesResults =
        ecsGitFetchResponse.getEcsScalableTargetFetchFilesResults();

    if (CollectionUtils.isNotEmpty(ecsScalableTargetFetchFilesResults)) {
      ecsScalableTargetManifestContentList =
          ecsScalableTargetFetchFilesResults.stream()
              .map(ecsScalableTargetFetchFilesResult
                  -> ecsScalableTargetFetchFilesResult.getFiles().get(0).getFileContent())
              .collect(Collectors.toList());

      ecsScalableTargetManifestContentList =
          ecsScalableTargetManifestContentList.stream()
              .map(ecsScalableTargetManifestContent
                  -> engineExpressionService.renderExpression(ambiance, ecsScalableTargetManifestContent))
              .collect(Collectors.toList());
    }
    return ecsScalableTargetManifestContentList;
  }

  private List<String> getRenderedScalingPoliciesFileContent(
      EcsGitFetchResponse ecsGitFetchResponse, Ambiance ambiance) {
    List<String> ecsScalingPolicyManifestContentList = null;
    List<FetchFilesResult> ecsScalingPolicyFetchFilesResults =
        ecsGitFetchResponse.getEcsScalingPolicyFetchFilesResults();

    if (CollectionUtils.isNotEmpty(ecsScalingPolicyFetchFilesResults)) {
      ecsScalingPolicyManifestContentList =
          ecsScalingPolicyFetchFilesResults.stream()
              .map(ecsScalingPolicyFetchFilesResult
                  -> ecsScalingPolicyFetchFilesResult.getFiles().get(0).getFileContent())
              .collect(Collectors.toList());

      ecsScalingPolicyManifestContentList =
          ecsScalingPolicyManifestContentList.stream()
              .map(ecsScalingPolicyManifestContent
                  -> engineExpressionService.renderExpression(ambiance, ecsScalingPolicyManifestContent))
              .collect(Collectors.toList());
    }
    return ecsScalingPolicyManifestContentList;
  }

  private TaskChainResponse handleEcsPrepareRollbackDataResponseRolling(
      EcsPrepareRollbackDataResponse ecsPrepareRollbackDataResponse, EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData) {
    if (ecsPrepareRollbackDataResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      EcsStepExceptionPassThroughData ecsStepExceptionPassThroughData =
          EcsStepExceptionPassThroughData.builder()
              .errorMessage(ecsPrepareRollbackDataResponse.getErrorMessage())
              .unitProgressData(ecsPrepareRollbackDataResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().passThroughData(ecsStepExceptionPassThroughData).chainEnd(true).build();
    }

    if (ecsStepExecutor instanceof EcsRollingDeployStep) {
      EcsPrepareRollbackDataResult ecsPrepareRollbackDataResult =
          ecsPrepareRollbackDataResponse.getEcsPrepareRollbackDataResult();

      EcsRollingRollbackDataOutcomeBuilder ecsRollbackDataOutcomeBuilder = EcsRollingRollbackDataOutcome.builder();

      ecsRollbackDataOutcomeBuilder.serviceName(ecsPrepareRollbackDataResult.getServiceName());
      ecsRollbackDataOutcomeBuilder.createServiceRequestBuilderString(
          ecsPrepareRollbackDataResult.getCreateServiceRequestBuilderString());
      ecsRollbackDataOutcomeBuilder.isFirstDeployment(ecsPrepareRollbackDataResult.isFirstDeployment());
      ecsRollbackDataOutcomeBuilder.registerScalableTargetRequestBuilderStrings(
          ecsPrepareRollbackDataResult.getRegisterScalableTargetRequestBuilderStrings());
      ecsRollbackDataOutcomeBuilder.registerScalingPolicyRequestBuilderStrings(
          ecsPrepareRollbackDataResult.getRegisterScalingPolicyRequestBuilderStrings());

      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ECS_ROLLING_ROLLBACK_OUTCOME,
          ecsRollbackDataOutcomeBuilder.build(), StepOutcomeGroup.STEP.name());
    }

    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(ecsStepPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(ecsPrepareRollbackDataResponse.getUnitProgressData())
            .build();

    String ecsTaskDefinitionFileContent = ecsStepPassThroughData.getEcsTaskDefinitionManifestContent();

    String ecsServiceDefinitionFileContent = ecsStepPassThroughData.getEcsServiceDefinitionManifestContent();

    List<String> ecsScalableTargetManifestContentList =
        ecsStepPassThroughData.getEcsScalableTargetManifestContentList();

    List<String> ecsScalingPolicyManifestContentList = ecsStepPassThroughData.getEcsScalingPolicyManifestContentList();

    EcsStepExecutorParams ecsStepExecutorParams =
        EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
            .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
            .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
            .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
            .build();

    return ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
        ecsPrepareRollbackDataResponse.getUnitProgressData(), ecsStepExecutorParams);
  }

  private TaskChainResponse handleEcsBlueGreenPrepareRollbackDataResponse(
      EcsBlueGreenPrepareRollbackDataResponse ecsBlueGreenPrepareRollbackDataResponse, EcsStepExecutor ecsStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData) {
    if (ecsBlueGreenPrepareRollbackDataResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      EcsStepExceptionPassThroughData ecsStepExceptionPassThroughData =
          EcsStepExceptionPassThroughData.builder()
              .errorMessage(ecsBlueGreenPrepareRollbackDataResponse.getErrorMessage())
              .unitProgressData(ecsBlueGreenPrepareRollbackDataResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().passThroughData(ecsStepExceptionPassThroughData).chainEnd(true).build();
    }

    if (ecsStepExecutor instanceof EcsBlueGreenCreateServiceStep) {
      EcsBlueGreenPrepareRollbackDataResult ecsBlueGreenPrepareRollbackDataResult =
          ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult();

      EcsBlueGreenPrepareRollbackDataOutcome ecsBlueGreenPrepareRollbackDataOutcome =
          EcsBlueGreenPrepareRollbackDataOutcome.builder()
              .serviceName(ecsBlueGreenPrepareRollbackDataResult.getServiceName())
              .createServiceRequestBuilderString(
                  ecsBlueGreenPrepareRollbackDataResult.getCreateServiceRequestBuilderString())
              .registerScalableTargetRequestBuilderStrings(
                  ecsBlueGreenPrepareRollbackDataResult.getRegisterScalableTargetRequestBuilderStrings())
              .registerScalingPolicyRequestBuilderStrings(
                  ecsBlueGreenPrepareRollbackDataResult.getRegisterScalingPolicyRequestBuilderStrings())
              .isFirstDeployment(ecsBlueGreenPrepareRollbackDataResult.isFirstDeployment())
              .loadBalancer(ecsBlueGreenPrepareRollbackDataResult.getLoadBalancer())
              .listenerArn(ecsBlueGreenPrepareRollbackDataResult.getListenerArn())
              .listenerRuleArn(ecsBlueGreenPrepareRollbackDataResult.getListenerRuleArn())
              .targetGroupArn(ecsBlueGreenPrepareRollbackDataResult.getTargetGroupArn())
              .build();

      executionSweepingOutputService.consume(ambiance,
          OutcomeExpressionConstants.ECS_BLUE_GREEN_PREPARE_ROLLBACK_DATA_OUTCOME,
          ecsBlueGreenPrepareRollbackDataOutcome, StepOutcomeGroup.STEP.name());
    }

    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(ecsStepPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(ecsBlueGreenPrepareRollbackDataResponse.getUnitProgressData())
            .build();

    String ecsTaskDefinitionFileContent = ecsStepPassThroughData.getEcsTaskDefinitionManifestContent();

    String ecsServiceDefinitionFileContent = ecsStepPassThroughData.getEcsServiceDefinitionManifestContent();

    List<String> ecsScalableTargetManifestContentList =
        ecsStepPassThroughData.getEcsScalableTargetManifestContentList();

    List<String> ecsScalingPolicyManifestContentList = ecsStepPassThroughData.getEcsScalingPolicyManifestContentList();

    EcsStepExecutorParams ecsStepExecutorParams =
        EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
            .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
            .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
            .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
            .targetGroupArnKey(ecsStepPassThroughData.getTargetGroupArnKey())
            .build();

    return ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
        ecsBlueGreenPrepareRollbackDataResponse.getUnitProgressData(), ecsStepExecutorParams);
  }

  public TaskChainResponse queueEcsTask(StepElementParameters stepElementParameters,
      EcsCommandRequest ecsCommandRequest, Ambiance ambiance, PassThroughData passThroughData, boolean isChainEnd) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {ecsCommandRequest})
                            .taskType(TaskType.ECS_COMMAND_TASK_NG.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();

    String taskName = TaskType.ECS_COMMAND_TASK_NG.getDisplayName() + " : " + ecsCommandRequest.getCommandName();

    EcsSpecParameters ecsSpecParameters = (EcsSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        ecsSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(ecsSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  public StepResponse handleGitTaskFailure(EcsGitFetchFailurePassThroughData ecsGitFetchFailurePassThroughData) {
    UnitProgressData unitProgressData = ecsGitFetchFailurePassThroughData.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(ecsGitFetchFailurePassThroughData.getErrorMsg()).build())
        .build();
  }

  public StepResponse handleStepExceptionFailure(EcsStepExceptionPassThroughData stepException) {
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
      Ambiance ambiance, EcsExecutionPassThroughData executionPassThroughData, Exception e) throws Exception {
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
      EcsCommandResponse ecsCommandResponse, StepResponseBuilder stepResponseBuilder) {
    stepResponseBuilder.status(Status.FAILED)
        .failureInfo(
            FailureInfo.newBuilder().setErrorMessage(EcsStepCommonHelper.getErrorMessage(ecsCommandResponse)).build());
    return stepResponseBuilder;
  }

  public static String getErrorMessage(EcsCommandResponse ecsCommandResponse) {
    return ecsCommandResponse.getErrorMessage() == null ? "" : ecsCommandResponse.getErrorMessage();
  }

  public List<ServerInstanceInfo> getServerInstanceInfos(
      EcsCommandResponse ecsCommandResponse, String infrastructureKey) {
    if (ecsCommandResponse instanceof EcsRollingDeployResponse) {
      EcsRollingDeployResult ecsRollingDeployResult =
          ((EcsRollingDeployResponse) ecsCommandResponse).getEcsRollingDeployResult();
      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
          ecsRollingDeployResult.getEcsTasks(), infrastructureKey, ecsRollingDeployResult.getRegion());
    } else if (ecsCommandResponse instanceof EcsRollingRollbackResponse) {
      EcsRollingRollbackResult ecsRollingRollbackResult =
          ((EcsRollingRollbackResponse) ecsCommandResponse).getEcsRollingRollbackResult();
      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
          ecsRollingRollbackResult.getEcsTasks(), infrastructureKey, ecsRollingRollbackResult.getRegion());
    } else if (ecsCommandResponse instanceof EcsCanaryDeployResponse) {
      EcsCanaryDeployResult ecsCanaryDeployResult =
          ((EcsCanaryDeployResponse) ecsCommandResponse).getEcsCanaryDeployResult();
      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
          ecsCanaryDeployResult.getEcsTasks(), infrastructureKey, ecsCanaryDeployResult.getRegion());
    } else if (ecsCommandResponse instanceof EcsBlueGreenCreateServiceResponse) {
      EcsBlueGreenCreateServiceResult ecsBlueGreenCreateServiceResult =
          ((EcsBlueGreenCreateServiceResponse) ecsCommandResponse).getEcsBlueGreenCreateServiceResult();
      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(ecsBlueGreenCreateServiceResult.getEcsTasks(),
          infrastructureKey, ecsBlueGreenCreateServiceResult.getRegion());
    } else if (ecsCommandResponse instanceof EcsBlueGreenSwapTargetGroupsResponse) {
      EcsBlueGreenSwapTargetGroupsResult ecsBlueGreenSwapTargetGroupsResult =
          ((EcsBlueGreenSwapTargetGroupsResponse) ecsCommandResponse).getEcsBlueGreenSwapTargetGroupsResult();
      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
          ecsBlueGreenSwapTargetGroupsResult.getEcsTasks(), infrastructureKey,
          ecsBlueGreenSwapTargetGroupsResult.getRegion());
    } else if (ecsCommandResponse instanceof EcsBlueGreenRollbackResponse) {
      EcsBlueGreenRollbackResult ecsBlueGreenRollbackResult =
          ((EcsBlueGreenRollbackResponse) ecsCommandResponse).getEcsBlueGreenRollbackResult();
      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
          ecsBlueGreenRollbackResult.getEcsTasks(), infrastructureKey, ecsBlueGreenRollbackResult.getRegion());
    }
    throw new GeneralException("Invalid ecs command response instance");
  }
}
