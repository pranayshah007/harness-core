/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import com.google.inject.Inject;
import io.harness.beans.DelegateTask;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.ecs.EcsCanaryDeployStep;
import io.harness.cdng.ecs.EcsEntityHelper;
import io.harness.cdng.ecs.EcsRollingDeployStep;
import io.harness.cdng.ecs.EcsRunTaskStepParameters;
import io.harness.cdng.ecs.EcsSpecParameters;
import io.harness.cdng.ecs.EcsStepExecutor;
import io.harness.cdng.ecs.EcsStepHelper;
import io.harness.cdng.ecs.EcsStepUtils;
import io.harness.cdng.ecs.beans.EcsBlueGreenPrepareRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchPassThroughData.EcsGitFetchPassThroughDataBuilder;
import io.harness.cdng.ecs.beans.EcsManifestsContent;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsRollingRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsRollingRollbackDataOutcome.EcsRollingRollbackDataOutcomeBuilder;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStartupScriptFetchFailurePassThroughData;
import io.harness.cdng.elastigroup.beans.ElastigroupStepExceptionPassThroughData;
import io.harness.cdng.elastigroup.config.StartupScriptOutcome;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.EcsRunTaskRequestDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsTaskDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotManualConfigSpecDTO;
import io.harness.delegate.beans.ecs.EcsBlueGreenCreateServiceResult;
import io.harness.delegate.beans.ecs.EcsBlueGreenPrepareRollbackDataResult;
import io.harness.delegate.beans.ecs.EcsBlueGreenRollbackResult;
import io.harness.delegate.beans.ecs.EcsBlueGreenSwapTargetGroupsResult;
import io.harness.delegate.beans.ecs.EcsCanaryDeployResult;
import io.harness.delegate.beans.ecs.EcsPrepareRollbackDataResult;
import io.harness.delegate.beans.ecs.EcsRollingDeployResult;
import io.harness.delegate.beans.ecs.EcsRollingRollbackResult;
import io.harness.delegate.beans.ecs.EcsRunTaskResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.EcsTaskToServerInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.ecs.EcsGitFetchFileConfig;
import io.harness.delegate.task.ecs.EcsGitFetchRunTaskFileConfig;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsGitFetchRequest;
import io.harness.delegate.task.ecs.request.EcsGitFetchRunTaskRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenCreateServiceResponse;
import io.harness.delegate.task.ecs.response.EcsBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsBlueGreenRollbackResponse;
import io.harness.delegate.task.ecs.response.EcsBlueGreenSwapTargetGroupsResponse;
import io.harness.delegate.task.ecs.response.EcsCanaryDeployResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsGitFetchResponse;
import io.harness.delegate.task.ecs.response.EcsGitFetchRunTaskResponse;
import io.harness.delegate.task.ecs.response.EcsPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsRollingDeployResponse;
import io.harness.delegate.task.ecs.response.EcsRollingRollbackResponse;
import io.harness.delegate.task.ecs.response.EcsRunTaskResponse;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupStartupScriptFetchRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupStartupScriptFetchResponse;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.spotinst.request.SpotInstSetupTaskParameters;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.git.model.FetchFilesResult;
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
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.hibernate.validator.constraints.NotEmpty;
import software.wings.beans.TaskType;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.spotinst.SpotInstSetupStateExecutionData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.steps.StepUtils.prepareCDTaskRequest;
import static java.lang.String.format;

@Slf4j
public class ElastigroupStepCommonHelper extends ElastigroupStepUtils {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private ElastigroupEntityHelper elastigroupEntityHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  public TaskChainResponse startChainLink(ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance,
                                          StepElementParameters stepElementParameters) {

    // Get ManifestsOutcome
    Optional<ArtifactOutcome> artifactOutcome = resolveArtifactsOutcome(ambiance);

    StartupScriptOutcome startupScriptOutcome = resolveStartupScriptOutcome(ambiance);

    // Get InfrastructureOutcome
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    // Update expressions in ManifestsOutcome
    ExpressionEvaluatorUtils.updateExpressions(
            artifactOutcome, new CDExpressionResolveFunctor(engineExpressionService, ambiance));

    // Validate ManifestsOutcome
//    validateManifestsOutcome(ambiance, manifestsOutcome);
//
//    List<ManifestOutcome> ecsManifestOutcome = getEcsManifestOutcome(manifestsOutcome.values(), ecsStepHelper);
//
//      ElastigroupExecutionPassThroughData executionPassThroughData =
//              ElastigroupExecutionPassThroughData.builder()
//                      .infrastructure(infrastructureOutcome)
//                      .build();
//
//      EcsStepExecutorParams ecsStepExecutorParams =
//              EcsStepExecutorParams.builder()
//                      .build();
//
//      LogCallback logCallback = getLogCallback(ElastigroupCommandUnitConstants.createSetup.toString(), ambiance, true);
//
//      UnitProgressData unitProgressData =
//              getCommandUnitProgressData(ElastigroupCommandUnitConstants.createSetup.toString(), CommandExecutionStatus.SUCCESS);

//      return elastigroupStepExecutor.executeElastigroupTask(
//              ambiance, stepElementParameters, executionPassThroughData, unitProgressData, ecsStepExecutorParams);
    return prepareEcsManifestGitFetchTask(
            elastigroupStepExecutor, ambiance, stepElementParameters, infrastructureOutcome, startupScriptOutcome);
  }
//
//  public TaskChainResponse startChainLinkEcsRunTask(
//      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters) {
//    // Get InfrastructureOutcome
//    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
//        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
//
//    return prepareEcsRunTaskGitFetchTask(ecsStepExecutor, ambiance, stepElementParameters, infrastructureOutcome);
//  }
//
//  public List<ManifestOutcome> getStartupScriptOutcome(
//      @NotEmpty Collection<ManifestOutcome> manifestOutcomes, EcsStepHelper ecsStepHelper) {
//    return ecsStepHelper.getEcsManifestOutcome(manifestOutcomes);
//  }
//
  public StartupScriptOutcome resolveStartupScriptOutcome(Ambiance ambiance) {
    OptionalOutcome startupScriptOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.STARTUP_SCRIPT));

    if (!startupScriptOutcome.isFound()) {
      String stageName =
          AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
      String stepType =
          Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance)).map(StepType::getType).orElse("Elastigroup");
      throw new GeneralException(
          format("No startupScript found in stage %s. %s step requires a startupScript defined in stage service definition",
              stageName, stepType));
    }
    return (StartupScriptOutcome) startupScriptOutcome.getOutcome();
  }
//
  private TaskChainResponse prepareEcsManifestGitFetchTask(ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, InfrastructureOutcome infrastructureOutcome,
      StartupScriptOutcome startupScriptOutcome) {

    LogCallback logCallback = getLogCallback(ElastigroupCommandUnitConstants.fetchManifests.toString(), ambiance, true);

    String startupScript = null;

    if (ManifestStoreType.HARNESS.equals(startupScriptOutcome.getStore().getKind())) {
      startupScript =
          fetchFilesContentFromLocalStore(ambiance, startupScriptOutcome, logCallback).get(0);
    } else if (ManifestStoreType.INLINE.equals(startupScriptOutcome.getStore().getKind())) {
        startupScript = ((InlineStoreConfig)startupScriptOutcome.getStore()).extractContent();
    }

    EcsGitFetchPassThroughDataBuilder ecsGitFetchPassThroughDataBuilder = EcsGitFetchPassThroughData.builder();

    // Render expressions for all file content fetched from Harness File Store

    if (startupScript != null) {
      startupScript = engineExpressionService.renderExpression(ambiance, startupScript);
    }

    EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
        ecsGitFetchPassThroughDataBuilder.infrastructureOutcome(infrastructureOutcome)
            .build();

    logCallback.saveExecutionLog("Fetched startup Script", INFO, CommandExecutionStatus.SUCCESS);

//      UnitProgressData unitProgressData =
//          getCommandUnitProgressData(ElastigroupCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);

//      if (ecsStepExecutor instanceof EcsRollingDeployStep) {
//        EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
//            EcsPrepareRollbackDataPassThroughData.builder()
//                .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
//                .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
//                .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
//                .ecsScalableTargetManifestContentList(ecsScalableTargetFileContentList)
//                .ecsScalingPolicyManifestContentList(ecsScalingPolicyFileContentList)
//                .build();
//        return ecsStepExecutor.executeEcsPrepareRollbackTask(
//            ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData, unitProgressData);
//      } else if (ecsStepExecutor instanceof EcsBlueGreenCreateServiceStep) {
//        EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
//            EcsPrepareRollbackDataPassThroughData.builder()
//                .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
//                .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
//                .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
//                .ecsScalableTargetManifestContentList(ecsScalableTargetFileContentList)
//                .ecsScalingPolicyManifestContentList(ecsScalingPolicyFileContentList)
//                .targetGroupArnKey(key.toString())
//                .build();
//
//        return ecsStepExecutor.executeEcsPrepareRollbackTask(
//            ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData, unitProgressData);
//
//      } else if (ecsStepExecutor instanceof EcsCanaryDeployStep) {
//        EcsExecutionPassThroughData executionPassThroughData =
//            EcsExecutionPassThroughData.builder()
//                .infrastructure(ecsGitFetchPassThroughData.getInfrastructureOutcome())
//                .lastActiveUnitProgressData(unitProgressData)
//                .build();
//
//        EcsStepExecutorParams ecsStepExecutorParams =
//            EcsStepExecutorParams.builder()
//                .shouldOpenFetchFilesLogStream(false)
//                .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
//                .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
//                .ecsScalableTargetManifestContentList(ecsScalableTargetFileContentList)
//                .ecsScalingPolicyManifestContentList(ecsScalingPolicyFileContentList)
//                .build();
//
//        return ecsStepExecutor.executeEcsTask(
//            ambiance, stepElementParameters, executionPassThroughData, unitProgressData, ecsStepExecutorParams);
//      }
//    }

    return getGitFetchFileTaskResponse(ambiance, false, stepElementParameters, ecsGitFetchPassThroughData);
  }

//  private TaskChainResponse prepareEcsRunTaskGitFetchTask(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
//      StepElementParameters stepElementParameters, InfrastructureOutcome infrastructureOutcome) {
//    EcsRunTaskStepParameters ecsRunTaskStepParameters = (EcsRunTaskStepParameters) stepElementParameters.getSpec();
//
//    LogCallback logCallback = getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);
//
//    if (ecsRunTaskStepParameters.getTaskDefinition() == null
//        || ecsRunTaskStepParameters.getTaskDefinition().getValue() == null) {
//      String errorMessage = "ECS Task Definition is empty in ECS Run Task Step";
//      throw new InvalidRequestException(errorMessage);
//    }
//
//    if (ecsRunTaskStepParameters.getRunTaskRequestDefinition() == null
//        || ecsRunTaskStepParameters.getRunTaskRequestDefinition().getValue() == null) {
//      String errorMessage = "ECS Run Task Request Definition is empty in ECS Run Task Step";
//      throw new InvalidRequestException(errorMessage);
//    }
//
//    StoreConfig ecsRunTaskDefinitionStoreConfig = ecsRunTaskStepParameters.getTaskDefinition().getValue().getSpec();
//    StoreConfig ecsRunTaskRequestDefinitionStoreConfig =
//        ecsRunTaskStepParameters.getRunTaskRequestDefinition().getValue().getSpec();
//
//    EcsGitFetchRunTaskFileConfig taskDefinitionEcsGitFetchRunTaskFileConfig = null;
//    String taskDefinitionFileContent = null;
//    ManifestOutcome ecsRunTaskDefinitionManifestOutcome =
//        EcsTaskDefinitionManifestOutcome.builder().store(ecsRunTaskDefinitionStoreConfig).build();
//
//    if (ecsRunTaskDefinitionStoreConfig.getKind() == HARNESS_STORE_TYPE) {
//      taskDefinitionFileContent =
//          fetchFilesContentFromLocalStore(ambiance, ecsRunTaskDefinitionManifestOutcome, logCallback).get(0);
//      taskDefinitionFileContent = engineExpressionService.renderExpression(ambiance, taskDefinitionFileContent);
//    } else {
//      taskDefinitionEcsGitFetchRunTaskFileConfig =
//          getEcsGitFetchRunTaskFileConfig(ecsRunTaskDefinitionManifestOutcome, ambiance);
//    }
//
//    EcsGitFetchRunTaskFileConfig ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig = null;
//    String ecsRunTaskRequestDefinitionFileContent = null;
//    ManifestOutcome ecsRunTaskRequestDefinitionManifestOutcome =
//        EcsRunTaskRequestDefinitionManifestOutcome.builder().store(ecsRunTaskRequestDefinitionStoreConfig).build();
//
//    if (ecsRunTaskRequestDefinitionStoreConfig.getKind() == HARNESS_STORE_TYPE) {
//      ecsRunTaskRequestDefinitionFileContent =
//          fetchFilesContentFromLocalStore(ambiance, ecsRunTaskRequestDefinitionManifestOutcome, logCallback).get(0);
//      ecsRunTaskRequestDefinitionFileContent =
//          engineExpressionService.renderExpression(ambiance, ecsRunTaskRequestDefinitionFileContent);
//    } else {
//      ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig =
//          getEcsGitFetchRunTaskFileConfig(ecsRunTaskRequestDefinitionManifestOutcome, ambiance);
//    }
//
//    EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
//        EcsGitFetchPassThroughData.builder()
//            .infrastructureOutcome(infrastructureOutcome)
//            .taskDefinitionHarnessFileContent(taskDefinitionFileContent)
//            .ecsRunTaskRequestDefinitionHarnessFileContent(ecsRunTaskRequestDefinitionFileContent)
//            .build();
//
//    // if both task definition, ecs run task request definition are from Harness Store
//    if (ecsRunTaskDefinitionStoreConfig.getKind() == HARNESS_STORE_TYPE
//        && ecsRunTaskRequestDefinitionStoreConfig.getKind() == HARNESS_STORE_TYPE) {
//      logCallback.saveExecutionLog("Fetched both task definition and run task request definition from Harness Store ",
//          INFO, CommandExecutionStatus.SUCCESS);
//
//      CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
//      UnitProgressData unitProgressData = UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress);
//
//      EcsStepExecutorParams ecsStepExecutorParams =
//          EcsStepExecutorParams.builder()
//              .shouldOpenFetchFilesLogStream(false)
//              .ecsTaskDefinitionManifestContent(taskDefinitionFileContent)
//              .ecsRunTaskRequestDefinitionManifestContent(ecsRunTaskRequestDefinitionFileContent)
//              .build();
//
//      EcsExecutionPassThroughData ecsExecutionPassThroughData = EcsExecutionPassThroughData.builder()
//                                                                    .infrastructure(infrastructureOutcome)
//                                                                    .lastActiveUnitProgressData(unitProgressData)
//                                                                    .build();
//
//      return ecsStepExecutor.executeEcsTask(
//          ambiance, stepElementParameters, ecsExecutionPassThroughData, unitProgressData, ecsStepExecutorParams);
//    }
//
//    return getGitFetchFileRunTaskResponse(ambiance, true, stepElementParameters, ecsGitFetchPassThroughData,
//        taskDefinitionEcsGitFetchRunTaskFileConfig, ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig);
//  }
//
//  private EcsGitFetchFileConfig getEcsGitFetchFilesConfigFromManifestOutcome(
//      ManifestOutcome manifestOutcome, Ambiance ambiance, EcsStepHelper ecsStepHelper) {
//    StoreConfig storeConfig = manifestOutcome.getStore();
//    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
//    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
//      throw new InvalidRequestException("Invalid kind of storeConfig for Ecs step", USER);
//    }
//    return getEcsGitFetchFilesConfig(ambiance, gitStoreConfig, manifestOutcome, ecsStepHelper);
//  }
//
//  private EcsGitFetchFileConfig getEcsGitFetchFilesConfig(
//      Ambiance ambiance, GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome, EcsStepHelper ecsStepHelper) {
//    return EcsGitFetchFileConfig.builder()
//        .gitStoreDelegateConfig(getGitStoreDelegateConfig(ambiance, gitStoreConfig, manifestOutcome))
//        .identifier(manifestOutcome.getIdentifier())
//        .manifestType(manifestOutcome.getType())
//        .succeedIfFileNotFound(false)
//        .build();
//  }
//
//  private EcsGitFetchRunTaskFileConfig getEcsGitFetchRunTaskFileConfig(
//      ManifestOutcome manifestOutcome, Ambiance ambiance) {
//    StoreConfig storeConfig = manifestOutcome.getStore();
//
//    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
//      throw new InvalidRequestException(
//          format("Invalid kind %s of storeConfig for Ecs run task step", storeConfig.getKind()), USER);
//    }
//
//    return getEcsGitFetchRunTaskFileConfig(ambiance, manifestOutcome);
//  }
//
//  private EcsGitFetchRunTaskFileConfig getEcsGitFetchRunTaskFileConfig(
//      Ambiance ambiance, ManifestOutcome manifestOutcome) {
//    GitStoreConfig gitStoreConfig = (GitStoreConfig) manifestOutcome.getStore();
//    return EcsGitFetchRunTaskFileConfig.builder()
//        .gitStoreDelegateConfig(getGitStoreDelegateConfigForRunTask(ambiance, manifestOutcome))
//        .succeedIfFileNotFound(false)
//        .build();
//  }

  private TaskChainResponse getGitFetchFileTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
      StepElementParameters stepElementParameters, EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
    String accountId = AmbianceUtils.getAccountId(ambiance);

    ElastigroupStartupScriptFetchRequest ecsGitFetchRequest =
            ElastigroupStartupScriptFetchRequest.builder()
            .accountId(accountId)
            .shouldOpenLogStream(shouldOpenLogStream)
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.ELASTIGROUP_STARTUP_SCRIPT_FETCH_RUN_TASK_NG.name())
                                  .parameters(new Object[] {ecsGitFetchRequest})
                                  .build();

    String taskName = TaskType.ELASTIGROUP_STARTUP_SCRIPT_FETCH_RUN_TASK_NG.getDisplayName();

    ElastigroupSpecParameters elastigroupSpecParameters = (ElastigroupSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
            elastigroupSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(elastigroupSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(ecsGitFetchPassThroughData)
        .build();
  }

//  private TaskChainResponse getGitFetchFileRunTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
//      StepElementParameters stepElementParameters, EcsGitFetchPassThroughData ecsGitFetchPassThroughData,
//      EcsGitFetchRunTaskFileConfig taskDefinitionEcsGitFetchRunTaskFileConfig,
//      EcsGitFetchRunTaskFileConfig ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig) {
//    String accountId = AmbianceUtils.getAccountId(ambiance);
//
//    EcsGitFetchRunTaskRequest ecsGitFetchRunTaskRequest =
//        EcsGitFetchRunTaskRequest.builder()
//            .accountId(accountId)
//            .taskDefinitionEcsGitFetchRunTaskFileConfig(taskDefinitionEcsGitFetchRunTaskFileConfig)
//            .ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig(
//                ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig)
//            .shouldOpenLogStream(shouldOpenLogStream)
//            .build();
//
//    final TaskData taskData = TaskData.builder()
//                                  .async(true)
//                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
//                                  .taskType(TaskType.ECS_GIT_FETCH_RUN_TASK_NG.name())
//                                  .parameters(new Object[] {ecsGitFetchRunTaskRequest})
//                                  .build();
//
//    String taskName = TaskType.ECS_GIT_FETCH_RUN_TASK_NG.getDisplayName();
//
//    EcsSpecParameters ecsSpecParameters = (EcsSpecParameters) stepElementParameters.getSpec();
//
//    final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
//        ecsSpecParameters.getCommandUnits(), taskName,
//        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(ecsSpecParameters.getDelegateSelectors()))),
//        stepHelper.getEnvironmentType(ambiance));
//
//    return TaskChainResponse.builder()
//        .chainEnd(false)
//        .taskRequest(taskRequest)
//        .passThroughData(ecsGitFetchPassThroughData)
//        .build();
//  }
//
//  public TaskChainResponse executeNextLinkRolling(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
//      StepElementParameters stepElementParameters, PassThroughData passThroughData,
//      ThrowingSupplier<ResponseData> responseDataSupplier, EcsStepHelper ecsStepHelper) throws Exception {
//    ResponseData responseData = responseDataSupplier.get();
//    UnitProgressData unitProgressData = null;
//    TaskChainResponse taskChainResponse = null;
//    try {
//      if (responseData instanceof EcsGitFetchResponse) { // if EcsGitFetchResponse is received
//
//        EcsGitFetchResponse ecsGitFetchResponse = (EcsGitFetchResponse) responseData;
//        EcsGitFetchPassThroughData ecsGitFetchPassThroughData = (EcsGitFetchPassThroughData) passThroughData;
//
//        taskChainResponse = handleEcsGitFetchFilesResponseRolling(
//            ecsGitFetchResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsGitFetchPassThroughData);
//
//      } else if (responseData
//          instanceof EcsPrepareRollbackDataResponse) { // if EcsPrepareRollbackDataResponse is received
//
//        EcsPrepareRollbackDataResponse ecsPrepareRollbackDataResponse = (EcsPrepareRollbackDataResponse) responseData;
//        EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData =
//            (EcsPrepareRollbackDataPassThroughData) passThroughData;
//
//        taskChainResponse = handleEcsPrepareRollbackDataResponseRolling(
//            ecsPrepareRollbackDataResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsStepPassThroughData);
//      }
//    } catch (Exception e) {
//      taskChainResponse =
//          TaskChainResponse.builder()
//              .chainEnd(true)
//              .passThroughData(
//                  EcsStepExceptionPassThroughData.builder()
//                      .errorMessage(ExceptionUtils.getMessage(e))
//                      .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
//                      .build())
//              .build();
//    }
//
//    return taskChainResponse;
//  }
//
  public TaskChainResponse executeNextLink(ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    UnitProgressData unitProgressData = null;
    TaskChainResponse taskChainResponse = null;
    try {
      if (responseData instanceof ElastigroupStartupScriptFetchResponse) { // if EcsGitFetchResponse is received

        ElastigroupStartupScriptFetchResponse elastigroupStartupScriptFetchResponse = (ElastigroupStartupScriptFetchResponse) responseData;
        EcsGitFetchPassThroughData ecsGitFetchPassThroughData = (EcsGitFetchPassThroughData) passThroughData;

        taskChainResponse = handleEcsGitFetchFilesResponse(
                elastigroupStartupScriptFetchResponse, elastigroupStepExecutor, ambiance, stepElementParameters, ecsGitFetchPassThroughData);
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
//
//  public TaskChainResponse executeNextLinkBlueGreen(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
//      StepElementParameters stepElementParameters, PassThroughData passThroughData,
//      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
//    ResponseData responseData = responseDataSupplier.get();
//    UnitProgressData unitProgressData = null;
//    TaskChainResponse taskChainResponse = null;
//    try {
//      if (responseData instanceof EcsGitFetchResponse) { // if EcsGitFetchResponse is received
//
//        EcsGitFetchResponse ecsGitFetchResponse = (EcsGitFetchResponse) responseData;
//        EcsGitFetchPassThroughData ecsGitFetchPassThroughData = (EcsGitFetchPassThroughData) passThroughData;
//
//        taskChainResponse = handleEcsGitFetchFilesResponseBlueGreen(
//            ecsGitFetchResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsGitFetchPassThroughData);
//
//      } else if (responseData
//          instanceof EcsBlueGreenPrepareRollbackDataResponse) { // if EcsBlueGreenPrepareRollbackDataResponse is
//                                                                // received
//
//        EcsBlueGreenPrepareRollbackDataResponse ecsBlueGreenPrepareRollbackDataResponse =
//            (EcsBlueGreenPrepareRollbackDataResponse) responseData;
//        EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData =
//            (EcsPrepareRollbackDataPassThroughData) passThroughData;
//
//        taskChainResponse = handleEcsBlueGreenPrepareRollbackDataResponse(ecsBlueGreenPrepareRollbackDataResponse,
//            ecsStepExecutor, ambiance, stepElementParameters, ecsStepPassThroughData);
//      }
//    } catch (Exception e) {
//      taskChainResponse =
//          TaskChainResponse.builder()
//              .chainEnd(true)
//              .passThroughData(
//                  EcsStepExceptionPassThroughData.builder()
//                      .errorMessage(ExceptionUtils.getMessage(e))
//                      .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
//                      .build())
//              .build();
//    }
//
//    return taskChainResponse;
//  }
//
//  public TaskChainResponse executeNextLinkRunTask(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
//      StepElementParameters stepElementParameters, PassThroughData passThroughData,
//      ThrowingSupplier<ResponseData> responseDataSupplier, EcsStepHelper ecsStepHelper) throws Exception {
//    ResponseData responseData = responseDataSupplier.get();
//    UnitProgressData unitProgressData = null;
//    TaskChainResponse taskChainResponse = null;
//    try {
//      if (responseData instanceof EcsGitFetchRunTaskResponse) { // if EcsGitFetchRunTaskResponse is received
//
//        EcsGitFetchRunTaskResponse ecsGitFetchRunTaskResponse = (EcsGitFetchRunTaskResponse) responseData;
//        EcsGitFetchPassThroughData ecsGitFetchPassThroughData = (EcsGitFetchPassThroughData) passThroughData;
//
//        taskChainResponse = handleEcsGitFetchFilesResponseRunTask(
//            ecsGitFetchRunTaskResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsGitFetchPassThroughData);
//      }
//    } catch (Exception e) {
//      taskChainResponse =
//          TaskChainResponse.builder()
//              .chainEnd(true)
//              .passThroughData(
//                  EcsStepExceptionPassThroughData.builder()
//                      .errorMessage(ExceptionUtils.getMessage(e))
//                      .unitProgressData(completeUnitProgressData(unitProgressData, ambiance, e.getMessage()))
//                      .build())
//              .build();
//    }
//
//    return taskChainResponse;
//  }
//


  public SpotInstConfig getSpotInstConfig(InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return elastigroupEntityHelper.getSpotInstConfig(infrastructureOutcome, ngAccess);
  }

//  public EcsInfraConfig getEcsInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
//    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
//    return ecsEntityHelper.getEcsInfraConfig(infrastructure, ngAccess);
//  }
//
//  private EcsManifestsContent mergeManifestsFromGitAndHarnessFileStore(EcsGitFetchResponse ecsGitFetchResponse,
//      Ambiance ambiance, EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
//    // Get ecsTaskDefinitionFileContent from ecsGitFetchResponse
//    FetchFilesResult ecsTaskDefinitionFetchFileResult = ecsGitFetchResponse.getEcsTaskDefinitionFetchFilesResult();
//
//    // Get task definition either from Git ot Harness File Store
//    String ecsTaskDefinitionFileContent;
//    if (ecsTaskDefinitionFetchFileResult != null) {
//      ecsTaskDefinitionFileContent = getRenderedTaskDefinitionFileContent(ecsGitFetchResponse, ambiance);
//    } else {
//      ecsTaskDefinitionFileContent = ecsGitFetchPassThroughData.getTaskDefinitionHarnessFileContent();
//    }
//
//    // Get ecsServiceDefinitionFetchFileResult from ecsGitFetchResponse
//    FetchFilesResult ecsServiceDefinitionFetchFileResult =
//        ecsGitFetchResponse.getEcsServiceDefinitionFetchFilesResult();
//
//    // Get service definition either from Git ot Harness File Store
//    String ecsServiceDefinitionFileContent;
//    if (ecsServiceDefinitionFetchFileResult != null) {
//      ecsServiceDefinitionFileContent = getRenderedServiceDefinitionFileContent(ecsGitFetchResponse, ambiance);
//    } else {
//      ecsServiceDefinitionFileContent = ecsGitFetchPassThroughData.getServiceDefinitionHarnessFileContent();
//    }
//
//    // Get ecsScalableTargetManifestContentList from ecsGitFetchResponse if present
//    List<String> ecsScalableTargetManifestContentList = new ArrayList<>();
//    List<FetchFilesResult> ecsScalableTargetFetchFilesResults =
//        ecsGitFetchResponse.getEcsScalableTargetFetchFilesResults();
//
//    if (CollectionUtils.isNotEmpty(ecsScalableTargetFetchFilesResults)) {
//      ecsScalableTargetManifestContentList = getRenderedScalableTargetsFileContent(ecsGitFetchResponse, ambiance);
//    }
//
//    // Add scalable targets from Harness File Store
//    if (CollectionUtils.isNotEmpty(ecsGitFetchPassThroughData.getScalableTargetHarnessFileContentList())) {
//      ecsScalableTargetManifestContentList.addAll(ecsGitFetchPassThroughData.getScalableTargetHarnessFileContentList());
//    }
//
//    // Get ecsScalingPolicyManifestContentList from ecsGitFetchResponse if present
//    List<String> ecsScalingPolicyManifestContentList = new ArrayList<>();
//    List<FetchFilesResult> ecsScalingPolicyFetchFilesResults =
//        ecsGitFetchResponse.getEcsScalingPolicyFetchFilesResults();
//    if (CollectionUtils.isNotEmpty(ecsScalingPolicyFetchFilesResults)) {
//      ecsScalingPolicyManifestContentList = getRenderedScalingPoliciesFileContent(ecsGitFetchResponse, ambiance);
//    }
//
//    // Add scaling policies from Harness File Store
//    if (CollectionUtils.isNotEmpty(ecsGitFetchPassThroughData.getScalingPolicyHarnessFileContentList())) {
//      ecsScalingPolicyManifestContentList.addAll(ecsGitFetchPassThroughData.getScalingPolicyHarnessFileContentList());
//    }
//
//    return EcsManifestsContent.builder()
//        .ecsTaskDefinitionFileContent(ecsTaskDefinitionFileContent)
//        .ecsServiceDefinitionFileContent(ecsServiceDefinitionFileContent)
//        .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
//        .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
//        .build();
//  }
//
//  private TaskChainResponse handleEcsGitFetchFilesResponseRolling(EcsGitFetchResponse ecsGitFetchResponse,
//      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
//      EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
//    if (ecsGitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
//      return handleFailureGitTask(ecsGitFetchResponse);
//    }
//
//    EcsManifestsContent ecsManifestsContent =
//        mergeManifestsFromGitAndHarnessFileStore(ecsGitFetchResponse, ambiance, ecsGitFetchPassThroughData);
//
//    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
//        EcsPrepareRollbackDataPassThroughData.builder()
//            .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
//            .ecsTaskDefinitionManifestContent(ecsManifestsContent.getEcsTaskDefinitionFileContent())
//            .ecsServiceDefinitionManifestContent(ecsManifestsContent.getEcsServiceDefinitionFileContent())
//            .ecsScalableTargetManifestContentList(ecsManifestsContent.getEcsScalableTargetManifestContentList())
//            .ecsScalingPolicyManifestContentList(ecsManifestsContent.getEcsScalingPolicyManifestContentList())
//            .build();
//
//    return ecsStepExecutor.executeEcsPrepareRollbackTask(ambiance, stepElementParameters,
//        ecsPrepareRollbackDataPassThroughData, ecsGitFetchResponse.getUnitProgressData());
//  }
//
  private TaskChainResponse handleEcsGitFetchFilesResponse(ElastigroupStartupScriptFetchResponse elastigroupStartupScriptFetchResponse,
      ElastigroupStepExecutor elastigroupStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
    if (elastigroupStartupScriptFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureGitTask(elastigroupStartupScriptFetchResponse);
    }

    ElastigroupExecutionPassThroughData elastigroupExecutionPassThroughData =
            ElastigroupExecutionPassThroughData.builder()
            .infrastructure(ecsGitFetchPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(elastigroupStartupScriptFetchResponse.getUnitProgressData())
            .build();

    EcsStepExecutorParams ecsStepExecutorParams =
        EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .build();

    return elastigroupStepExecutor.executeElastigroupTask(ambiance, stepElementParameters, elastigroupExecutionPassThroughData,
            elastigroupStartupScriptFetchResponse.getUnitProgressData(), ecsStepExecutorParams);
  }
//
//  private TaskChainResponse handleEcsGitFetchFilesResponseRunTask(EcsGitFetchRunTaskResponse ecsGitFetchRunTaskResponse,
//      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
//      EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
//    if (ecsGitFetchRunTaskResponse.getTaskStatus() != TaskStatus.SUCCESS) {
//      EcsGitFetchFailurePassThroughData ecsGitFetchFailurePassThroughData =
//          EcsGitFetchFailurePassThroughData.builder()
//              .errorMsg(ecsGitFetchRunTaskResponse.getErrorMessage())
//              .unitProgressData(ecsGitFetchRunTaskResponse.getUnitProgressData())
//              .build();
//      return TaskChainResponse.builder().passThroughData(ecsGitFetchFailurePassThroughData).chainEnd(true).build();
//    }
//
//    // Get ecsTaskDefinitionFileContent from ecsGitFetchResponse
//    FetchFilesResult ecsTaskDefinitionFetchFileResult =
//        ecsGitFetchRunTaskResponse.getEcsTaskDefinitionFetchFilesResult();
//    String ecsTaskDefinitionFileContent = null;
//
//    if (ecsTaskDefinitionFetchFileResult != null) {
//      ecsTaskDefinitionFileContent = ecsTaskDefinitionFetchFileResult.getFiles().get(0).getFileContent();
//      ecsTaskDefinitionFileContent = engineExpressionService.renderExpression(ambiance, ecsTaskDefinitionFileContent);
//    } else {
//      ecsTaskDefinitionFileContent = ecsGitFetchPassThroughData.getTaskDefinitionHarnessFileContent();
//    }
//
//    FetchFilesResult ecsRunTaskRequestDefinitionFetchFilesResult =
//        ecsGitFetchRunTaskResponse.getEcsRunTaskDefinitionRequestFetchFilesResult();
//
//    String ecsRunTaskRequestDefinitionFileContent = null;
//
//    if (ecsRunTaskRequestDefinitionFetchFilesResult != null) {
//      ecsRunTaskRequestDefinitionFileContent =
//          ecsRunTaskRequestDefinitionFetchFilesResult.getFiles().get(0).getFileContent();
//      ecsRunTaskRequestDefinitionFileContent =
//          engineExpressionService.renderExpression(ambiance, ecsRunTaskRequestDefinitionFileContent);
//    } else {
//      ecsRunTaskRequestDefinitionFileContent =
//          ecsGitFetchPassThroughData.getEcsRunTaskRequestDefinitionHarnessFileContent();
//    }
//
//    EcsStepExecutorParams ecsStepExecutorParams =
//        EcsStepExecutorParams.builder()
//            .shouldOpenFetchFilesLogStream(false)
//            .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
//            .ecsRunTaskRequestDefinitionManifestContent(ecsRunTaskRequestDefinitionFileContent)
//            .build();
//
//    EcsExecutionPassThroughData ecsExecutionPassThroughData =
//        EcsExecutionPassThroughData.builder()
//            .infrastructure(ecsGitFetchPassThroughData.getInfrastructureOutcome())
//            .lastActiveUnitProgressData(ecsGitFetchRunTaskResponse.getUnitProgressData())
//            .build();
//
//    return ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
//        ecsGitFetchRunTaskResponse.getUnitProgressData(), ecsStepExecutorParams);
//  }
//
//  private TaskChainResponse handleEcsGitFetchFilesResponseBlueGreen(EcsGitFetchResponse ecsGitFetchResponse,
//      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
//      EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
//    if (ecsGitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
//      return handleFailureGitTask(ecsGitFetchResponse);
//    }
//
//    // Get ecsTaskDefinitionFileContent from ecsGitFetchResponse
//    FetchFilesResult ecsTaskDefinitionFetchFileResult = ecsGitFetchResponse.getEcsTaskDefinitionFetchFilesResult();
//
//    // Get task definition either from Git ot Harness File Store
//    String ecsTaskDefinitionFileContent;
//    if (ecsTaskDefinitionFetchFileResult != null) {
//      ecsTaskDefinitionFileContent = getRenderedTaskDefinitionFileContent(ecsGitFetchResponse, ambiance);
//    } else {
//      ecsTaskDefinitionFileContent = ecsGitFetchPassThroughData.getTaskDefinitionHarnessFileContent();
//    }
//
//    StringBuilder key = new StringBuilder();
//    if (ecsGitFetchPassThroughData.getTargetGroupArnKey() != null) {
//      key = key.append(ecsGitFetchPassThroughData.getTargetGroupArnKey());
//    } else {
//      long timeStamp = System.currentTimeMillis();
//      key = key.append(timeStamp).append("targetGroup");
//    }
//
//    // Get ecsServiceDefinitionFileContent from ecsGitFetchResponse
//    FetchFilesResult ecsServiceDefinitionFetchFileResult =
//        ecsGitFetchResponse.getEcsServiceDefinitionFetchFilesResult();
//
//    // Get service definition either from Git ot Harness File Store
//    String ecsServiceDefinitionFileContent;
//    if (ecsServiceDefinitionFetchFileResult != null) {
//      ecsServiceDefinitionFileContent = ecsServiceDefinitionFetchFileResult.getFiles().get(0).getFileContent();
//      if (ecsServiceDefinitionFileContent.contains(TARGET_GROUP_ARN_EXPRESSION)) {
//        ecsServiceDefinitionFileContent =
//            ecsServiceDefinitionFileContent.replace(TARGET_GROUP_ARN_EXPRESSION, key.toString());
//      }
//      ecsServiceDefinitionFileContent =
//          engineExpressionService.renderExpression(ambiance, ecsServiceDefinitionFileContent);
//    } else {
//      ecsServiceDefinitionFileContent = ecsGitFetchPassThroughData.getServiceDefinitionHarnessFileContent();
//    }
//
//    // Get ecsScalableTargetManifestContentList from ecsGitFetchResponse if present
//    List<String> ecsScalableTargetManifestContentList = new ArrayList<>();
//    List<FetchFilesResult> ecsScalableTargetFetchFilesResults =
//        ecsGitFetchResponse.getEcsScalableTargetFetchFilesResults();
//
//    if (CollectionUtils.isNotEmpty(ecsScalableTargetFetchFilesResults)) {
//      ecsScalableTargetManifestContentList = getRenderedScalableTargetsFileContent(ecsGitFetchResponse, ambiance);
//    }
//
//    // Add scalable targets from Harness File Store
//    if (CollectionUtils.isNotEmpty(ecsGitFetchPassThroughData.getScalableTargetHarnessFileContentList())) {
//      ecsScalableTargetManifestContentList.addAll(ecsGitFetchPassThroughData.getScalableTargetHarnessFileContentList());
//    }
//
//    // Get ecsScalingPolicyManifestContentList from ecsGitFetchResponse if present
//    List<String> ecsScalingPolicyManifestContentList = new ArrayList<>();
//    List<FetchFilesResult> ecsScalingPolicyFetchFilesResults =
//        ecsGitFetchResponse.getEcsScalingPolicyFetchFilesResults();
//    if (CollectionUtils.isNotEmpty(ecsScalingPolicyFetchFilesResults)) {
//      ecsScalingPolicyManifestContentList = getRenderedScalingPoliciesFileContent(ecsGitFetchResponse, ambiance);
//    }
//
//    // Add scaling policies from Harness File Store
//    if (CollectionUtils.isNotEmpty(ecsGitFetchPassThroughData.getScalingPolicyHarnessFileContentList())) {
//      ecsScalingPolicyManifestContentList.addAll(ecsGitFetchPassThroughData.getScalingPolicyHarnessFileContentList());
//    }
//
//    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
//        EcsPrepareRollbackDataPassThroughData.builder()
//            .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
//            .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
//            .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
//            .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
//            .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
//            .targetGroupArnKey(key.toString())
//            .build();
//
//    return ecsStepExecutor.executeEcsPrepareRollbackTask(ambiance, stepElementParameters,
//        ecsPrepareRollbackDataPassThroughData, ecsGitFetchResponse.getUnitProgressData());
//  }
//
  private TaskChainResponse handleFailureGitTask(ElastigroupStartupScriptFetchResponse elastigroupStartupScriptFetchResponse) {
    ElastigroupStartupScriptFetchFailurePassThroughData elastigroupStartupScriptFetchFailurePassThroughData =
            ElastigroupStartupScriptFetchFailurePassThroughData.builder()
            .errorMsg(elastigroupStartupScriptFetchResponse.getErrorMessage())
            .unitProgressData(elastigroupStartupScriptFetchResponse.getUnitProgressData())
            .build();
    return TaskChainResponse.builder().passThroughData(elastigroupStartupScriptFetchFailurePassThroughData).chainEnd(true).build();
  }
//
//  private String getRenderedTaskDefinitionFileContent(EcsGitFetchResponse ecsGitFetchResponse, Ambiance ambiance) {
//    FetchFilesResult ecsTaskDefinitionFetchFileResult = ecsGitFetchResponse.getEcsTaskDefinitionFetchFilesResult();
//    String ecsTaskDefinitionFileContent = ecsTaskDefinitionFetchFileResult.getFiles().get(0).getFileContent();
//    return engineExpressionService.renderExpression(ambiance, ecsTaskDefinitionFileContent);
//  }
//
//  private String getRenderedServiceDefinitionFileContent(EcsGitFetchResponse ecsGitFetchResponse, Ambiance ambiance) {
//    FetchFilesResult ecsServiceDefinitionFetchFileResult =
//        ecsGitFetchResponse.getEcsServiceDefinitionFetchFilesResult();
//    String ecsServiceDefinitionFileContent = ecsServiceDefinitionFetchFileResult.getFiles().get(0).getFileContent();
//    return engineExpressionService.renderExpression(ambiance, ecsServiceDefinitionFileContent);
//  }
//
//  private List<String> getRenderedScalableTargetsFileContent(
//      EcsGitFetchResponse ecsGitFetchResponse, Ambiance ambiance) {
//    List<String> ecsScalableTargetManifestContentList = null;
//    List<FetchFilesResult> ecsScalableTargetFetchFilesResults =
//        ecsGitFetchResponse.getEcsScalableTargetFetchFilesResults();
//    if (CollectionUtils.isNotEmpty(ecsScalableTargetFetchFilesResults)) {
//      ecsScalableTargetManifestContentList =
//          ecsScalableTargetFetchFilesResults.stream()
//              .map(ecsScalableTargetFetchFilesResult
//                  -> ecsScalableTargetFetchFilesResult.getFiles().get(0).getFileContent())
//              .collect(Collectors.toList());
//
//      ecsScalableTargetManifestContentList =
//          ecsScalableTargetManifestContentList.stream()
//              .map(ecsScalableTargetManifestContent
//                  -> engineExpressionService.renderExpression(ambiance, ecsScalableTargetManifestContent))
//              .collect(Collectors.toList());
//    }
//    return ecsScalableTargetManifestContentList;
//  }
//
//  private List<String> getRenderedScalingPoliciesFileContent(
//      EcsGitFetchResponse ecsGitFetchResponse, Ambiance ambiance) {
//    List<String> ecsScalingPolicyManifestContentList = null;
//    List<FetchFilesResult> ecsScalingPolicyFetchFilesResults =
//        ecsGitFetchResponse.getEcsScalingPolicyFetchFilesResults();
//    if (CollectionUtils.isNotEmpty(ecsScalingPolicyFetchFilesResults)) {
//      ecsScalingPolicyManifestContentList =
//          ecsScalingPolicyFetchFilesResults.stream()
//              .map(ecsScalingPolicyFetchFilesResult
//                  -> ecsScalingPolicyFetchFilesResult.getFiles().get(0).getFileContent())
//              .collect(Collectors.toList());
//
//      ecsScalingPolicyManifestContentList =
//          ecsScalingPolicyManifestContentList.stream()
//              .map(ecsScalingPolicyManifestContent
//                  -> engineExpressionService.renderExpression(ambiance, ecsScalingPolicyManifestContent))
//              .collect(Collectors.toList());
//    }
//    return ecsScalingPolicyManifestContentList;
//  }
//
//  private TaskChainResponse handleEcsPrepareRollbackDataResponseRolling(
//      EcsPrepareRollbackDataResponse ecsPrepareRollbackDataResponse, EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
//      StepElementParameters stepElementParameters, EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData) {
//    if (ecsPrepareRollbackDataResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
//      EcsStepExceptionPassThroughData ecsStepExceptionPassThroughData =
//          EcsStepExceptionPassThroughData.builder()
//              .errorMessage(ecsPrepareRollbackDataResponse.getErrorMessage())
//              .unitProgressData(ecsPrepareRollbackDataResponse.getUnitProgressData())
//              .build();
//      return TaskChainResponse.builder().passThroughData(ecsStepExceptionPassThroughData).chainEnd(true).build();
//    }
//
//    if (ecsStepExecutor instanceof EcsRollingDeployStep) {
//      EcsPrepareRollbackDataResult ecsPrepareRollbackDataResult =
//          ecsPrepareRollbackDataResponse.getEcsPrepareRollbackDataResult();
//
//      EcsRollingRollbackDataOutcomeBuilder ecsRollbackDataOutcomeBuilder = EcsRollingRollbackDataOutcome.builder();
//
//      ecsRollbackDataOutcomeBuilder.serviceName(ecsPrepareRollbackDataResult.getServiceName());
//      ecsRollbackDataOutcomeBuilder.createServiceRequestBuilderString(
//          ecsPrepareRollbackDataResult.getCreateServiceRequestBuilderString());
//      ecsRollbackDataOutcomeBuilder.isFirstDeployment(ecsPrepareRollbackDataResult.isFirstDeployment());
//      ecsRollbackDataOutcomeBuilder.registerScalableTargetRequestBuilderStrings(
//          ecsPrepareRollbackDataResult.getRegisterScalableTargetRequestBuilderStrings());
//      ecsRollbackDataOutcomeBuilder.registerScalingPolicyRequestBuilderStrings(
//          ecsPrepareRollbackDataResult.getRegisterScalingPolicyRequestBuilderStrings());
//
//      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.ECS_ROLLING_ROLLBACK_OUTCOME,
//          ecsRollbackDataOutcomeBuilder.build(), StepOutcomeGroup.STEP.name());
//    }
//
//    EcsExecutionPassThroughData ecsExecutionPassThroughData =
//        EcsExecutionPassThroughData.builder()
//            .infrastructure(ecsStepPassThroughData.getInfrastructureOutcome())
//            .lastActiveUnitProgressData(ecsPrepareRollbackDataResponse.getUnitProgressData())
//            .build();
//
//    String ecsTaskDefinitionFileContent = ecsStepPassThroughData.getEcsTaskDefinitionManifestContent();
//
//    String ecsServiceDefinitionFileContent = ecsStepPassThroughData.getEcsServiceDefinitionManifestContent();
//
//    List<String> ecsScalableTargetManifestContentList =
//        ecsStepPassThroughData.getEcsScalableTargetManifestContentList();
//
//    List<String> ecsScalingPolicyManifestContentList = ecsStepPassThroughData.getEcsScalingPolicyManifestContentList();
//
//    EcsStepExecutorParams ecsStepExecutorParams =
//        EcsStepExecutorParams.builder()
//            .shouldOpenFetchFilesLogStream(false)
//            .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
//            .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
//            .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
//            .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
//            .build();
//
//    return ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
//        ecsPrepareRollbackDataResponse.getUnitProgressData(), ecsStepExecutorParams);
//  }
//
//  private TaskChainResponse handleEcsBlueGreenPrepareRollbackDataResponse(
//      EcsBlueGreenPrepareRollbackDataResponse ecsBlueGreenPrepareRollbackDataResponse, EcsStepExecutor ecsStepExecutor,
//      Ambiance ambiance, StepElementParameters stepElementParameters,
//      EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData) {
//    if (ecsBlueGreenPrepareRollbackDataResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
//      EcsStepExceptionPassThroughData ecsStepExceptionPassThroughData =
//          EcsStepExceptionPassThroughData.builder()
//              .errorMessage(ecsBlueGreenPrepareRollbackDataResponse.getErrorMessage())
//              .unitProgressData(ecsBlueGreenPrepareRollbackDataResponse.getUnitProgressData())
//              .build();
//      return TaskChainResponse.builder().passThroughData(ecsStepExceptionPassThroughData).chainEnd(true).build();
//    }
//
//    String prodTargetGroupArn = null;
//    String stageTargetGroupArn = null;
//
//    if (ecsStepExecutor instanceof EcsBlueGreenCreateServiceStep) {
//      EcsBlueGreenPrepareRollbackDataResult ecsBlueGreenPrepareRollbackDataResult =
//          ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult();
//
//      prodTargetGroupArn = ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getProdTargetGroupArn();
//      stageTargetGroupArn = ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getStageTargetGroupArn();
//
//      EcsBlueGreenPrepareRollbackDataOutcome ecsBlueGreenPrepareRollbackDataOutcome =
//          EcsBlueGreenPrepareRollbackDataOutcome.builder()
//              .serviceName(ecsBlueGreenPrepareRollbackDataResult.getServiceName())
//              .createServiceRequestBuilderString(
//                  ecsBlueGreenPrepareRollbackDataResult.getCreateServiceRequestBuilderString())
//              .registerScalableTargetRequestBuilderStrings(
//                  ecsBlueGreenPrepareRollbackDataResult.getRegisterScalableTargetRequestBuilderStrings())
//              .registerScalingPolicyRequestBuilderStrings(
//                  ecsBlueGreenPrepareRollbackDataResult.getRegisterScalingPolicyRequestBuilderStrings())
//              .isFirstDeployment(ecsBlueGreenPrepareRollbackDataResult.isFirstDeployment())
//              .loadBalancer(ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getLoadBalancer())
//              .prodListenerArn(ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getProdListenerArn())
//              .prodListenerRuleArn(
//                  ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getProdListenerRuleArn())
//              .prodTargetGroupArn(prodTargetGroupArn)
//              .stageListenerArn(ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getStageListenerArn())
//              .stageListenerRuleArn(
//                  ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getStageListenerRuleArn())
//              .stageTargetGroupArn(stageTargetGroupArn)
//              .build();
//
//      executionSweepingOutputService.consume(ambiance,
//          OutcomeExpressionConstants.ECS_BLUE_GREEN_PREPARE_ROLLBACK_DATA_OUTCOME,
//          ecsBlueGreenPrepareRollbackDataOutcome, StepOutcomeGroup.STEP.name());
//    }
//
//    EcsExecutionPassThroughData ecsExecutionPassThroughData =
//        EcsExecutionPassThroughData.builder()
//            .infrastructure(ecsStepPassThroughData.getInfrastructureOutcome())
//            .lastActiveUnitProgressData(ecsBlueGreenPrepareRollbackDataResponse.getUnitProgressData())
//            .build();
//
//    String ecsTaskDefinitionFileContent = ecsStepPassThroughData.getEcsTaskDefinitionManifestContent();
//
//    String ecsServiceDefinitionFileContent = ecsStepPassThroughData.getEcsServiceDefinitionManifestContent();
//
//    List<String> ecsScalableTargetManifestContentList =
//        ecsStepPassThroughData.getEcsScalableTargetManifestContentList();
//
//    List<String> ecsScalingPolicyManifestContentList = ecsStepPassThroughData.getEcsScalingPolicyManifestContentList();
//
//    EcsStepExecutorParams ecsStepExecutorParams =
//        EcsStepExecutorParams.builder()
//            .shouldOpenFetchFilesLogStream(false)
//            .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
//            .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
//            .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
//            .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
//            .targetGroupArnKey(ecsStepPassThroughData.getTargetGroupArnKey())
//            .prodTargetGroupArn(prodTargetGroupArn)
//            .stageTargetGroupArn(stageTargetGroupArn)
//            .build();
//
//    return ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
//        ecsBlueGreenPrepareRollbackDataResponse.getUnitProgressData(), ecsStepExecutorParams);
//  }
//
  public TaskChainResponse queueElastigroupTask(StepElementParameters stepElementParameters,
                                        ElastigroupCommandRequest elastigroupCommandRequest, Ambiance ambiance, PassThroughData passThroughData, boolean isChainEnd) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {elastigroupCommandRequest})
                            .taskType(TaskType.ELASTIGROUP_COMMAND_TASK_NG.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();

    String taskName = TaskType.ELASTIGROUP_COMMAND_TASK_NG.getDisplayName() + " : " + elastigroupCommandRequest.getCommandName();

    ElastigroupSpecParameters elastigroupSpecParameters = (ElastigroupSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
            elastigroupSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(elastigroupSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  public StepResponse handleGitTaskFailure(ElastigroupStartupScriptFetchFailurePassThroughData elastigroupStartupScriptFetchFailurePassThroughData) {
    UnitProgressData unitProgressData = elastigroupStartupScriptFetchFailurePassThroughData.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(elastigroupStartupScriptFetchFailurePassThroughData.getErrorMsg()).build())
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
          ElastigroupCommandResponse elastigroupCommandResponse, StepResponseBuilder stepResponseBuilder) {
    stepResponseBuilder.status(Status.FAILED)
        .failureInfo(
            FailureInfo.newBuilder().setErrorMessage(ElastigroupStepCommonHelper.getErrorMessage(elastigroupCommandResponse)).build());
    return stepResponseBuilder;
  }

  public static String getErrorMessage(ElastigroupCommandResponse elastigroupCommandResponse) {
    return elastigroupCommandResponse.getErrorMessage() == null ? "" : elastigroupCommandResponse.getErrorMessage();
  }
//
//  public List<ServerInstanceInfo> getServerInstanceInfos(
//      EcsCommandResponse ecsCommandResponse, String infrastructureKey) {
//    if (ecsCommandResponse instanceof EcsRollingDeployResponse) {
//      EcsRollingDeployResult ecsRollingDeployResult =
//          ((EcsRollingDeployResponse) ecsCommandResponse).getEcsRollingDeployResult();
//      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
//          ecsRollingDeployResult.getEcsTasks(), infrastructureKey, ecsRollingDeployResult.getRegion());
//    } else if (ecsCommandResponse instanceof EcsRollingRollbackResponse) {
//      EcsRollingRollbackResult ecsRollingRollbackResult =
//          ((EcsRollingRollbackResponse) ecsCommandResponse).getEcsRollingRollbackResult();
//      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
//          ecsRollingRollbackResult.getEcsTasks(), infrastructureKey, ecsRollingRollbackResult.getRegion());
//    } else if (ecsCommandResponse instanceof EcsCanaryDeployResponse) {
//      EcsCanaryDeployResult ecsCanaryDeployResult =
//          ((EcsCanaryDeployResponse) ecsCommandResponse).getEcsCanaryDeployResult();
//      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
//          ecsCanaryDeployResult.getEcsTasks(), infrastructureKey, ecsCanaryDeployResult.getRegion());
//    } else if (ecsCommandResponse instanceof EcsBlueGreenCreateServiceResponse) {
//      EcsBlueGreenCreateServiceResult ecsBlueGreenCreateServiceResult =
//          ((EcsBlueGreenCreateServiceResponse) ecsCommandResponse).getEcsBlueGreenCreateServiceResult();
//      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(ecsBlueGreenCreateServiceResult.getEcsTasks(),
//          infrastructureKey, ecsBlueGreenCreateServiceResult.getRegion());
//    } else if (ecsCommandResponse instanceof EcsBlueGreenSwapTargetGroupsResponse) {
//      EcsBlueGreenSwapTargetGroupsResult ecsBlueGreenSwapTargetGroupsResult =
//          ((EcsBlueGreenSwapTargetGroupsResponse) ecsCommandResponse).getEcsBlueGreenSwapTargetGroupsResult();
//      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
//          ecsBlueGreenSwapTargetGroupsResult.getEcsTasks(), infrastructureKey,
//          ecsBlueGreenSwapTargetGroupsResult.getRegion());
//    } else if (ecsCommandResponse instanceof EcsBlueGreenRollbackResponse) {
//      EcsBlueGreenRollbackResult ecsBlueGreenRollbackResult =
//          ((EcsBlueGreenRollbackResponse) ecsCommandResponse).getEcsBlueGreenRollbackResult();
//      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
//          ecsBlueGreenRollbackResult.getEcsTasks(), infrastructureKey, ecsBlueGreenRollbackResult.getRegion());
//    } else if (ecsCommandResponse instanceof EcsRunTaskResponse) {
//      EcsRunTaskResult ecsRunTaskResult = ((EcsRunTaskResponse) ecsCommandResponse).getEcsRunTaskResult();
//      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
//          ecsRunTaskResult.getEcsTasks(), infrastructureKey, ecsRunTaskResult.getRegion());
//    }
//    throw new GeneralException("Invalid ecs command response instance");
//  }
}
