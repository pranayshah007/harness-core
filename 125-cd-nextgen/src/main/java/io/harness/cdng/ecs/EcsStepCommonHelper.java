/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.ecs.beans.EcsBlueGreenPrepareRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchPassThroughData;
import io.harness.cdng.ecs.beans.EcsHarnessStoreManifestsContent;
import io.harness.cdng.ecs.beans.EcsHarnessStoreManifestsContent.EcsHarnessStoreManifestsContentBuilder;
import io.harness.cdng.ecs.beans.EcsManifestsContent;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsRollingRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsRollingRollbackDataOutcome.EcsRollingRollbackDataOutcomeBuilder;
import io.harness.cdng.ecs.beans.EcsRunTaskManifestsContent;
import io.harness.cdng.ecs.beans.EcsRunTaskS3FileConfigs;
import io.harness.cdng.ecs.beans.EcsS3FetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsS3FetchPassThroughData;
import io.harness.cdng.ecs.beans.EcsS3ManifestFileConfigs;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.expressions.CDExpressionResolveFunctor;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.EcsRunTaskRequestDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsTaskDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.sweepingoutput.EcsServiceCustomSweepingOutput;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
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
import io.harness.delegate.beans.ecs.EcsRunTaskResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.EcsTaskToServerInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.ecs.EcsGitFetchFileConfig;
import io.harness.delegate.task.ecs.EcsGitFetchRunTaskFileConfig;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsS3FetchFileConfig;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsGitFetchRequest;
import io.harness.delegate.task.ecs.request.EcsGitFetchRunTaskRequest;
import io.harness.delegate.task.ecs.request.EcsS3FetchRequest;
import io.harness.delegate.task.ecs.request.EcsS3FetchRunTaskRequest;
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
import io.harness.delegate.task.ecs.response.EcsS3FetchResponse;
import io.harness.delegate.task.ecs.response.EcsS3FetchRunTaskResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.ecs.EcsCommandUnitConstants;
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
import io.harness.pms.contracts.plan.ExpressionMode;
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
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.hibernate.validator.constraints.NotEmpty;

@Slf4j
public class EcsStepCommonHelper extends EcsStepUtils {
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private EcsEntityHelper ecsEntityHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CDExpressionResolver cdExpressionResolver;
  private static final String TARGET_GROUP_ARN_EXPRESSION = "<+targetGroupArn>";
  @Inject private CDStepHelper cdStepHelper;
  private static final String INVALID_ECS_TASK_DEFINITION =
      "Either Ecs Task definition manifest or ecsTaskDefinitionArn field should be present "
      + "in service";

  public TaskChainResponse startChainLink(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, EcsStepHelper ecsStepHelper) {
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

    List<ManifestOutcome> ecsManifestOutcomes = getEcsManifestOutcome(manifestsOutcome.values(), ecsStepHelper);

    // validate task definition presence
    validateTaskDefinitionPresence(ambiance, ecsManifestOutcomes, ecsStepHelper);

    LogCallback logCallback = getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);

    // get Harness Store Manifests Content
    EcsHarnessStoreManifestsContent ecsHarnessStoreContent =
        getHarnessStoreManifestFilesContent(ambiance, ecsManifestOutcomes, ecsStepHelper, logCallback);

    EcsS3ManifestFileConfigs ecsS3ManifestFileConfigs = null;
    if (isAnyS3Manifest(ecsManifestOutcomes)) {
      ecsS3ManifestFileConfigs = getS3ManifestFileConfigs(ambiance, ecsManifestOutcomes, ecsStepHelper);
    }

    TaskChainResponse taskChainResponse = null;
    if (isAnyGitManifest(ecsManifestOutcomes)) { // at least one git
      EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
          EcsGitFetchPassThroughData.builder()
              .infrastructureOutcome(infrastructureOutcome)
              .taskDefinitionHarnessFileContent(ecsHarnessStoreContent.getTaskDefinitionHarnessContent())
              .serviceDefinitionHarnessFileContent(ecsHarnessStoreContent.getServiceDefinitionHarnessContent())
              .scalableTargetHarnessFileContentList(ecsHarnessStoreContent.getScalableTargetHarnessContentList())
              .scalingPolicyHarnessFileContentList(ecsHarnessStoreContent.getScalingPolicyHarnessContentList())
              .targetGroupArnKey(ecsHarnessStoreContent.getTargetGroupArnKey())
              .ecsS3ManifestFileConfigs(ecsS3ManifestFileConfigs)
              .build();

      taskChainResponse = prepareEcsManifestGitFetchTask(ecsStepExecutor, ambiance, stepElementParameters,
          ecsGitFetchPassThroughData, ecsManifestOutcomes, ecsStepHelper);
    } else {
      if (areAllManifestsFromHarnessFileStore(ecsManifestOutcomes)) { // all harness store
        taskChainResponse = prepareEcsHarnessStoreTask(ecsStepExecutor, ambiance, stepElementParameters,
            ecsHarnessStoreContent, infrastructureOutcome, logCallback);
      } else { // at least one s3
        EcsManifestsContent ecsHarnessManifestsContent =
            EcsManifestsContent.builder()
                .ecsTaskDefinitionFileContent(ecsHarnessStoreContent.getTaskDefinitionHarnessContent())
                .ecsServiceDefinitionFileContent(ecsHarnessStoreContent.getServiceDefinitionHarnessContent())
                .ecsScalableTargetManifestContentList(ecsHarnessStoreContent.getScalableTargetHarnessContentList())
                .ecsScalingPolicyManifestContentList(ecsHarnessStoreContent.getScalingPolicyHarnessContentList())
                .build();
        EcsS3FetchPassThroughData ecsS3FetchPassThroughData =
            EcsS3FetchPassThroughData.builder()
                .infrastructureOutcome(infrastructureOutcome)
                .ecsOtherStoreContents(ecsHarnessManifestsContent)
                .otherStoreTargetGroupArnKey(ecsHarnessStoreContent.getTargetGroupArnKey())
                .build();
        taskChainResponse = prepareEcsManifestS3FetchTask(
            ambiance, stepElementParameters, ecsS3FetchPassThroughData, ecsS3ManifestFileConfigs);
      }
    }
    return taskChainResponse;
  }

  private EcsHarnessStoreManifestsContent getHarnessStoreManifestFilesContent(Ambiance ambiance,
      List<ManifestOutcome> ecsManifestOutcomes, EcsStepHelper ecsStepHelper, LogCallback logCallback) {
    // Harness Store manifests
    EcsHarnessStoreManifestsContentBuilder ecsHarnessStoreContentBuilder = EcsHarnessStoreManifestsContent.builder();

    // Get Harness Store Task Definition file content
    ManifestOutcome ecsTaskDefinitionManifestOutcome =
        ecsStepHelper.getEcsTaskDefinitionManifestOutcome(ecsManifestOutcomes);
    String ecsTaskDefinitionHarnessContent = null;
    if (ecsTaskDefinitionManifestOutcome != null
        && ManifestStoreType.HARNESS.equals(ecsTaskDefinitionManifestOutcome.getStore().getKind())) {
      ecsTaskDefinitionHarnessContent =
          fetchFilesContentFromLocalStore(ambiance, ecsTaskDefinitionManifestOutcome, logCallback).get(0);
    }

    ManifestOutcome ecsServiceDefinitionManifestOutcome =
        ecsStepHelper.getEcsServiceDefinitionManifestOutcome(ecsManifestOutcomes);

    String ecsServiceDefinitionHarnessContent = null;

    if (ManifestStoreType.HARNESS.equals(ecsServiceDefinitionManifestOutcome.getStore().getKind())) {
      ecsServiceDefinitionHarnessContent =
          fetchFilesContentFromLocalStore(ambiance, ecsServiceDefinitionManifestOutcome, logCallback).get(0);
    }

    // Render expressions for all file content fetched from Harness File Store

    if (ecsTaskDefinitionHarnessContent != null) {
      ecsTaskDefinitionHarnessContent =
          engineExpressionService.renderExpression(ambiance, ecsTaskDefinitionHarnessContent);
    }

    long timeStamp = System.currentTimeMillis();
    StringBuilder key = new StringBuilder().append(timeStamp).append("targetGroup");

    if (ecsServiceDefinitionHarnessContent != null) {
      ecsServiceDefinitionHarnessContent = engineExpressionService.renderExpression(
          ambiance, ecsServiceDefinitionHarnessContent, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      if (ecsServiceDefinitionHarnessContent.contains(TARGET_GROUP_ARN_EXPRESSION)) {
        ecsServiceDefinitionHarnessContent =
            ecsServiceDefinitionHarnessContent.replace(TARGET_GROUP_ARN_EXPRESSION, key.toString());
      }
      ecsHarnessStoreContentBuilder.targetGroupArnKey(key.toString());
    }

    return ecsHarnessStoreContentBuilder.taskDefinitionHarnessContent(ecsTaskDefinitionHarnessContent)
        .serviceDefinitionHarnessContent(ecsServiceDefinitionHarnessContent)
        .scalableTargetHarnessContentList(
            getScalableTargetHarnessContentList(ambiance, ecsStepHelper, logCallback, ecsManifestOutcomes))
        .scalingPolicyHarnessContentList(
            getScalingPolicyHarnessContentList(ambiance, ecsStepHelper, logCallback, ecsManifestOutcomes))
        .build();
  }

  private List<String> getScalableTargetHarnessContentList(Ambiance ambiance, EcsStepHelper ecsStepHelper,
      LogCallback logCallback, List<ManifestOutcome> ecsManifestOutcomes) {
    // Get EcsGitFetchFileConfig list for scalable targets if present
    List<ManifestOutcome> ecsScalableTargetManifestOutcomes =
        ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalableTargetDefinition);

    List<String> ecsScalableTargetHarnessContentList = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(ecsScalableTargetManifestOutcomes)) {
      for (ManifestOutcome ecsScalableTargetManifestOutcome : ecsScalableTargetManifestOutcomes) {
        if (ManifestStoreType.HARNESS.equals(ecsScalableTargetManifestOutcome.getStore().getKind())) {
          ecsScalableTargetHarnessContentList.add(
              fetchFilesContentFromLocalStore(ambiance, ecsScalableTargetManifestOutcome, logCallback).get(0));
        }
      }
    }
    if (CollectionUtils.isNotEmpty(ecsScalableTargetHarnessContentList)) {
      ecsScalableTargetHarnessContentList =
          ecsScalableTargetHarnessContentList.stream()
              .map(ecsScalableTargetHarnessContent
                  -> engineExpressionService.renderExpression(ambiance, ecsScalableTargetHarnessContent))
              .collect(Collectors.toList());
    }
    return ecsScalableTargetHarnessContentList;
  }

  private List<String> getScalingPolicyHarnessContentList(Ambiance ambiance, EcsStepHelper ecsStepHelper,
      LogCallback logCallback, List<ManifestOutcome> ecsManifestOutcomes) {
    // Get EcsGitFetchFileConfig list for scaling policies if present
    List<ManifestOutcome> ecsScalingPolicyManifestOutcomes =
        ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalingPolicyDefinition);

    List<String> ecsScalingPolicyHarnessContentList = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(ecsScalingPolicyManifestOutcomes)) {
      for (ManifestOutcome ecsScalingPolicyManifestOutcome : ecsScalingPolicyManifestOutcomes) {
        if (ManifestStoreType.HARNESS.equals(ecsScalingPolicyManifestOutcome.getStore().getKind())) {
          ecsScalingPolicyHarnessContentList.add(
              fetchFilesContentFromLocalStore(ambiance, ecsScalingPolicyManifestOutcome, logCallback).get(0));
        }
      }
    }
    if (CollectionUtils.isNotEmpty(ecsScalingPolicyHarnessContentList)) {
      ecsScalingPolicyHarnessContentList =
          ecsScalingPolicyHarnessContentList.stream()
              .map(ecsScalingPolicyHarnessContent
                  -> engineExpressionService.renderExpression(ambiance, ecsScalingPolicyHarnessContent))
              .collect(Collectors.toList());
    }
    return ecsScalingPolicyHarnessContentList;
  }

  private TaskChainResponse prepareEcsHarnessStoreTask(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, EcsHarnessStoreManifestsContent ecsHarnessStoreContent,
      InfrastructureOutcome infrastructureOutcome, LogCallback logCallback) {
    logCallback.saveExecutionLog("Fetched all manifests from Harness Store ", INFO, CommandExecutionStatus.SUCCESS);

    UnitProgressData unitProgressData =
        getCommandUnitProgressData(EcsCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);

    String ecsTaskDefinitionHarnessContent = ecsHarnessStoreContent.getTaskDefinitionHarnessContent();
    String ecsServiceDefinitionHarnessContent = ecsHarnessStoreContent.getServiceDefinitionHarnessContent();
    List<String> ecsScalableTargetHarnessContentList = ecsHarnessStoreContent.getScalableTargetHarnessContentList();
    List<String> ecsScalingPolicyHarnessContentList = ecsHarnessStoreContent.getScalingPolicyHarnessContentList();

    TaskChainResponse taskChainResponse = null;
    if (ecsStepExecutor instanceof EcsRollingDeployStep) {
      EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
          EcsPrepareRollbackDataPassThroughData.builder()
              .infrastructureOutcome(infrastructureOutcome)
              .ecsTaskDefinitionManifestContent(ecsTaskDefinitionHarnessContent)
              .ecsServiceDefinitionManifestContent(ecsServiceDefinitionHarnessContent)
              .ecsScalableTargetManifestContentList(ecsScalableTargetHarnessContentList)
              .ecsScalingPolicyManifestContentList(ecsScalingPolicyHarnessContentList)
              .build();
      taskChainResponse = ecsStepExecutor.executeEcsPrepareRollbackTask(
          ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData, unitProgressData);
    } else if (ecsStepExecutor instanceof EcsBlueGreenCreateServiceStep) {
      EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
          EcsPrepareRollbackDataPassThroughData.builder()
              .infrastructureOutcome(infrastructureOutcome)
              .ecsTaskDefinitionManifestContent(ecsTaskDefinitionHarnessContent)
              .ecsServiceDefinitionManifestContent(ecsServiceDefinitionHarnessContent)
              .ecsScalableTargetManifestContentList(ecsScalableTargetHarnessContentList)
              .ecsScalingPolicyManifestContentList(ecsScalingPolicyHarnessContentList)
              .targetGroupArnKey(ecsHarnessStoreContent.getTargetGroupArnKey())
              .build();

      taskChainResponse = ecsStepExecutor.executeEcsPrepareRollbackTask(
          ambiance, stepElementParameters, ecsPrepareRollbackDataPassThroughData, unitProgressData);

    } else if (ecsStepExecutor instanceof EcsCanaryDeployStep) {
      EcsExecutionPassThroughData executionPassThroughData = EcsExecutionPassThroughData.builder()
                                                                 .infrastructure(infrastructureOutcome)
                                                                 .lastActiveUnitProgressData(unitProgressData)
                                                                 .build();

      EcsStepExecutorParams ecsStepExecutorParams =
          EcsStepExecutorParams.builder()
              .shouldOpenFetchFilesLogStream(false)
              .ecsTaskDefinitionManifestContent(ecsTaskDefinitionHarnessContent)
              .ecsServiceDefinitionManifestContent(ecsServiceDefinitionHarnessContent)
              .ecsScalableTargetManifestContentList(ecsScalableTargetHarnessContentList)
              .ecsScalingPolicyManifestContentList(ecsScalingPolicyHarnessContentList)
              .build();

      taskChainResponse = ecsStepExecutor.executeEcsTask(
          ambiance, stepElementParameters, executionPassThroughData, unitProgressData, ecsStepExecutorParams);
    }
    return taskChainResponse;
  }

  private TaskChainResponse prepareEcsRunTaskHarnessStoreTask(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, InfrastructureOutcome infrastructureOutcome,
      EcsRunTaskManifestsContent ecsRunTaskManifestsContent, LogCallback logCallback) {
    logCallback.saveExecutionLog("Fetched both task definition and run task request definition from Harness Store ",
        INFO, CommandExecutionStatus.SUCCESS);

    UnitProgressData unitProgressData =
        getCommandUnitProgressData(EcsCommandUnitConstants.fetchManifests.toString(), CommandExecutionStatus.SUCCESS);

    EcsStepExecutorParams ecsStepExecutorParams =
        EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .ecsTaskDefinitionManifestContent(ecsRunTaskManifestsContent.getRunTaskDefinitionFileContent())
            .ecsRunTaskRequestDefinitionManifestContent(
                ecsRunTaskManifestsContent.getRunTaskRequestDefinitionFileContent())
            .build();

    EcsExecutionPassThroughData ecsExecutionPassThroughData = EcsExecutionPassThroughData.builder()
                                                                  .infrastructure(infrastructureOutcome)
                                                                  .lastActiveUnitProgressData(unitProgressData)
                                                                  .build();

    return ecsStepExecutor.executeEcsTask(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, unitProgressData, ecsStepExecutorParams);
  }

  public TaskChainResponse startChainLinkEcsRunTask(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, EcsStepHelper ecsStepHelper) {
    // Get InfrastructureOutcome
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    cdExpressionResolver.updateExpressions(ambiance, stepElementParameters);

    List<ManifestOutcome> ecsRunTaskManifestOutcomes = getEcsRunTaskManifestOutcomes(stepElementParameters);

    LogCallback logCallback = getLogCallback(EcsCommandUnitConstants.fetchManifests.toString(), ambiance, true);

    EcsRunTaskManifestsContent runTaskHarnessStoreContent =
        getHarnessStoreRunTaskFilesContent(ambiance, ecsRunTaskManifestOutcomes, logCallback, ecsStepHelper);

    EcsRunTaskS3FileConfigs ecsRunTaskS3FileConfigs = null;
    if (isAnyS3Manifest(ecsRunTaskManifestOutcomes)) {
      ecsRunTaskS3FileConfigs = getRunTaskS3ManifestFileConfigs(ambiance, ecsRunTaskManifestOutcomes, ecsStepHelper);
    }

    TaskChainResponse taskChainResponse = null;
    if (isAnyGitManifest(ecsRunTaskManifestOutcomes)) {
      EcsGitFetchPassThroughData ecsGitFetchPassThroughData =
          EcsGitFetchPassThroughData.builder()
              .infrastructureOutcome(infrastructureOutcome)
              .taskDefinitionHarnessFileContent(runTaskHarnessStoreContent.getRunTaskDefinitionFileContent())
              .ecsRunTaskRequestDefinitionHarnessFileContent(
                  runTaskHarnessStoreContent.getRunTaskRequestDefinitionFileContent())
              .ecsRunTaskS3FileConfigs(ecsRunTaskS3FileConfigs)
              .build();
      taskChainResponse = prepareEcsRunTaskGitFetchTask(
          ambiance, stepElementParameters, ecsRunTaskManifestOutcomes, ecsGitFetchPassThroughData, ecsStepHelper);
    } else {
      if (areAllManifestsFromHarnessFileStore(ecsRunTaskManifestOutcomes)) { // all harness store
        taskChainResponse = prepareEcsRunTaskHarnessStoreTask(ecsStepExecutor, ambiance, stepElementParameters,
            infrastructureOutcome, runTaskHarnessStoreContent, logCallback);
      } else { // at least one s3 no git
        EcsS3FetchPassThroughData ecsS3FetchRunTaskPassThroughData =
            EcsS3FetchPassThroughData.builder()
                .infrastructureOutcome(infrastructureOutcome)
                .ecsOtherStoreRunTaskContent(runTaskHarnessStoreContent)
                .build();
        taskChainResponse = prepareEcsRunTaskS3FetchTask(
            ambiance, stepElementParameters, ecsS3FetchRunTaskPassThroughData, ecsRunTaskS3FileConfigs);
      }
    }

    return taskChainResponse;
  }

  public List<ManifestOutcome> getEcsManifestOutcome(
      @NotEmpty Collection<ManifestOutcome> manifestOutcomes, EcsStepHelper ecsStepHelper) {
    return ecsStepHelper.getEcsManifestOutcome(manifestOutcomes);
  }

  public List<ManifestOutcome> getEcsRunTaskManifestOutcomes(StepElementParameters stepElementParameters) {
    EcsRunTaskStepParameters ecsRunTaskStepParameters = (EcsRunTaskStepParameters) stepElementParameters.getSpec();

    if ((ecsRunTaskStepParameters.getTaskDefinition() == null
            || ecsRunTaskStepParameters.getTaskDefinition().getValue() == null)
        && (ecsRunTaskStepParameters.getTaskDefinitionArn() == null
            || ecsRunTaskStepParameters.getTaskDefinitionArn().getValue() == null)) {
      String errorMessage = "ECS Task Definition is empty in ECS Run Task Step";
      throw new InvalidRequestException(errorMessage);
    }

    if ((ecsRunTaskStepParameters.getTaskDefinition() != null
            && ecsRunTaskStepParameters.getTaskDefinition().getValue() != null)
        && (ecsRunTaskStepParameters.getTaskDefinitionArn() != null
            && ecsRunTaskStepParameters.getTaskDefinitionArn().getValue() != null)) {
      String errorMessage = "Both Task Definition, Task Definition Arn are configured. Only one of them is expected.";
      throw new InvalidRequestException(errorMessage);
    }

    if (ecsRunTaskStepParameters.getRunTaskRequestDefinition() == null
        || ecsRunTaskStepParameters.getRunTaskRequestDefinition().getValue() == null) {
      String errorMessage = "ECS Run Task Request Definition is empty in ECS Run Task Step";
      throw new InvalidRequestException(errorMessage);
    }

    StoreConfig ecsRunTaskDefinitionStoreConfig = null;
    ManifestOutcome ecsRunTaskDefinitionManifestOutcome = null;
    if (ecsRunTaskStepParameters.getTaskDefinition() != null
        && ecsRunTaskStepParameters.getTaskDefinition().getValue() != null) {
      ecsRunTaskDefinitionStoreConfig = ecsRunTaskStepParameters.getTaskDefinition().getValue().getSpec();
    }
    ecsRunTaskDefinitionManifestOutcome =
        EcsTaskDefinitionManifestOutcome.builder().store(ecsRunTaskDefinitionStoreConfig).build();

    StoreConfig ecsRunTaskRequestDefinitionStoreConfig =
        ecsRunTaskStepParameters.getRunTaskRequestDefinition().getValue().getSpec();

    ManifestOutcome ecsRunTaskRequestDefinitionManifestOutcome =
        EcsRunTaskRequestDefinitionManifestOutcome.builder().store(ecsRunTaskRequestDefinitionStoreConfig).build();

    return Arrays.asList(ecsRunTaskDefinitionManifestOutcome, ecsRunTaskRequestDefinitionManifestOutcome);
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

  private EcsS3ManifestFileConfigs getS3ManifestFileConfigs(
      Ambiance ambiance, Collection<ManifestOutcome> ecsManifestOutcomes, EcsStepHelper ecsStepHelper) {
    ManifestOutcome ecsTaskDefinitionManifestOutcome =
        ecsStepHelper.getEcsTaskDefinitionManifestOutcome(ecsManifestOutcomes);
    EcsS3FetchFileConfig ecsTaskDefinitionS3FetchFileConfig = null;
    if (ecsTaskDefinitionManifestOutcome != null
        && ManifestStoreType.S3.equals(ecsTaskDefinitionManifestOutcome.getStore().getKind())) {
      ecsTaskDefinitionS3FetchFileConfig =
          getEcsS3FetchFilesConfigFromManifestOutcome(ecsTaskDefinitionManifestOutcome, ambiance, ecsStepHelper);
    }

    // Get EcsS3FetchFileConfig for service definition
    ManifestOutcome ecsServiceDefinitionManifestOutcome =
        ecsStepHelper.getEcsServiceDefinitionManifestOutcome(ecsManifestOutcomes);
    EcsS3FetchFileConfig ecsServiceDefinitionS3FetchFileConfig = null;
    if (ManifestStoreType.S3.equals(ecsServiceDefinitionManifestOutcome.getStore().getKind())) {
      ecsServiceDefinitionS3FetchFileConfig =
          getEcsS3FetchFilesConfigFromManifestOutcome(ecsServiceDefinitionManifestOutcome, ambiance, ecsStepHelper);
    }

    // Get EcsS3FetchFileConfig list for scalable targets if present
    List<ManifestOutcome> ecsScalableTargetManifestOutcomes =
        ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalableTargetDefinition);
    List<EcsS3FetchFileConfig> ecsScalableTargetS3FetchFileConfigs = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(ecsScalableTargetManifestOutcomes)) {
      for (ManifestOutcome ecsScalableTargetManifestOutcome : ecsScalableTargetManifestOutcomes) {
        if (ManifestStoreType.S3.equals(ecsScalableTargetManifestOutcome.getStore().getKind())) {
          ecsScalableTargetS3FetchFileConfigs.add(
              getEcsS3FetchFilesConfigFromManifestOutcome(ecsScalableTargetManifestOutcome, ambiance, ecsStepHelper));
        }
      }
    }

    // Get EcsS3FetchFileConfig list for scaling policies if present
    List<ManifestOutcome> ecsScalingPolicyManifestOutcomes =
        ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalingPolicyDefinition);

    List<EcsS3FetchFileConfig> ecsScalingPolicyS3FetchFileConfigs = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(ecsScalingPolicyManifestOutcomes)) {
      for (ManifestOutcome ecsScalingPolicyManifestOutcome : ecsScalingPolicyManifestOutcomes) {
        if (ManifestStoreType.S3.equals(ecsScalingPolicyManifestOutcome.getStore().getKind())) {
          ecsScalingPolicyS3FetchFileConfigs.add(
              getEcsS3FetchFilesConfigFromManifestOutcome(ecsScalingPolicyManifestOutcome, ambiance, ecsStepHelper));
        }
      }
    }

    return EcsS3ManifestFileConfigs.builder()
        .ecsS3TaskDefinitionFileConfig(ecsTaskDefinitionS3FetchFileConfig)
        .ecsS3ServiceDefinitionFileConfig(ecsServiceDefinitionS3FetchFileConfig)
        .ecsS3ScalableTargetFileConfigs(ecsScalableTargetS3FetchFileConfigs)
        .ecsS3ScalingPolicyFileConfigs(ecsScalingPolicyS3FetchFileConfigs)
        .build();
  }

  private EcsRunTaskS3FileConfigs getRunTaskS3ManifestFileConfigs(
      Ambiance ambiance, List<ManifestOutcome> ecsRunTaskManifestOutcomes, EcsStepHelper ecsStepHelper) {
    ManifestOutcome ecsRunTaskDefinitionManifestOutcome =
        ecsStepHelper.getEcsTaskDefinitionManifestOutcome(ecsRunTaskManifestOutcomes);
    EcsS3FetchFileConfig taskDefinitionEcsS3FetchRunTaskFileConfig = null;

    if (ecsRunTaskDefinitionManifestOutcome.getStore() != null
        && ManifestStoreType.S3.equals(ecsRunTaskDefinitionManifestOutcome.getStore().getKind())) {
      taskDefinitionEcsS3FetchRunTaskFileConfig =
          getEcsRunTaskS3FetchFilesConfigFromManifestOutcome(ambiance, ecsRunTaskDefinitionManifestOutcome);
    }
    ManifestOutcome ecsRunTaskRequestDefinitionManifestOutcome =
        ecsStepHelper.getEcsRunTaskRequestDefinitionManifestOutcome(ecsRunTaskManifestOutcomes);
    EcsS3FetchFileConfig ecsRunTaskRequestEcsS3FetchRunTaskFileConfig = null;

    if (ManifestStoreType.S3.equals(ecsRunTaskRequestDefinitionManifestOutcome.getStore().getKind())) {
      ecsRunTaskRequestEcsS3FetchRunTaskFileConfig =
          getEcsRunTaskS3FetchFilesConfigFromManifestOutcome(ambiance, ecsRunTaskRequestDefinitionManifestOutcome);
    }

    return EcsRunTaskS3FileConfigs.builder()
        .runTaskDefinitionS3FetchFileConfig(taskDefinitionEcsS3FetchRunTaskFileConfig)
        .runTaskRequestDefinitionS3FetchFileConfig(ecsRunTaskRequestEcsS3FetchRunTaskFileConfig)
        .build();
  }

  private TaskChainResponse prepareEcsManifestS3FetchTask(Ambiance ambiance,
      StepElementParameters stepElementParameters, EcsS3FetchPassThroughData ecsS3FetchPassThroughData,
      EcsS3ManifestFileConfigs ecsS3ManifestFileConfigs) {
    // Get EcsS3FetchFileConfig for task definition

    EcsS3FetchFileConfig ecsTaskDefinitionS3FetchFileConfig =
        ecsS3ManifestFileConfigs.getEcsS3TaskDefinitionFileConfig();
    EcsS3FetchFileConfig ecsServiceDefinitionS3FetchFileConfig =
        ecsS3ManifestFileConfigs.getEcsS3ServiceDefinitionFileConfig();
    List<EcsS3FetchFileConfig> ecsScalableTargetS3FetchFileConfigs =
        ecsS3ManifestFileConfigs.getEcsS3ScalableTargetFileConfigs();
    List<EcsS3FetchFileConfig> ecsScalingPolicyS3FetchFileConfigs =
        ecsS3ManifestFileConfigs.getEcsS3ScalingPolicyFileConfigs();

    return getS3FetchFileTaskResponse(ambiance, false, stepElementParameters, ecsS3FetchPassThroughData,
        ecsTaskDefinitionS3FetchFileConfig, ecsServiceDefinitionS3FetchFileConfig, ecsScalableTargetS3FetchFileConfigs,
        ecsScalingPolicyS3FetchFileConfigs);
  }

  private TaskChainResponse getS3FetchFileTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
      StepElementParameters stepElementParameters, EcsS3FetchPassThroughData ecsS3FetchPassThroughData,
      EcsS3FetchFileConfig ecsTaskDefinitionS3FetchFileConfig,
      EcsS3FetchFileConfig ecsServiceDefinitionS3FetchFileConfig,
      List<EcsS3FetchFileConfig> ecsScalableTargetS3FetchFileConfigs,
      List<EcsS3FetchFileConfig> ecsScalingPolicyS3FetchFileConfigs) {
    String accountId = AmbianceUtils.getAccountId(ambiance);

    EcsS3FetchRequest ecsS3FetchRequest =
        EcsS3FetchRequest.builder()
            .accountId(accountId)
            .ecsTaskDefinitionS3FetchFileConfig(ecsTaskDefinitionS3FetchFileConfig)
            .ecsServiceDefinitionS3FetchFileConfig(ecsServiceDefinitionS3FetchFileConfig)
            .ecsScalableTargetS3FetchFileConfigs(ecsScalableTargetS3FetchFileConfigs)
            .ecsScalingPolicyS3FetchFileConfigs(ecsScalingPolicyS3FetchFileConfigs)
            .shouldOpenLogStream(shouldOpenLogStream)
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.ECS_S3_FETCH_TASK_NG.name())
                                  .parameters(new Object[] {ecsS3FetchRequest})
                                  .build();
    String taskName = TaskType.ECS_S3_FETCH_TASK_NG.getDisplayName();

    EcsSpecParameters ecsSpecParameters = (EcsSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, ecsSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(ecsSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(ecsS3FetchPassThroughData)
        .build();
  }

  private TaskChainResponse prepareEcsManifestGitFetchTask(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, EcsGitFetchPassThroughData ecsGitFetchPassThroughData,
      List<ManifestOutcome> ecsManifestOutcomes, EcsStepHelper ecsStepHelper) {
    // Get EcsGitFetchFileConfig for task definition
    ManifestOutcome ecsTaskDefinitionManifestOutcome =
        ecsStepHelper.getEcsTaskDefinitionManifestOutcome(ecsManifestOutcomes);

    EcsGitFetchFileConfig ecsTaskDefinitionGitFetchFileConfig = null;

    if (ecsTaskDefinitionManifestOutcome != null
        && ManifestStoreType.isInGitSubset(ecsTaskDefinitionManifestOutcome.getStore().getKind())) {
      ecsTaskDefinitionGitFetchFileConfig =
          getEcsGitFetchFilesConfigFromManifestOutcome(ecsTaskDefinitionManifestOutcome, ambiance, ecsStepHelper);
    }

    // Get EcsGitFetchFileConfig for service definition
    ManifestOutcome ecsServiceDefinitionManifestOutcome =
        ecsStepHelper.getEcsServiceDefinitionManifestOutcome(ecsManifestOutcomes);

    EcsGitFetchFileConfig ecsServiceDefinitionGitFetchFileConfig = null;

    if (ManifestStoreType.isInGitSubset(ecsServiceDefinitionManifestOutcome.getStore().getKind())) {
      ecsServiceDefinitionGitFetchFileConfig =
          getEcsGitFetchFilesConfigFromManifestOutcome(ecsServiceDefinitionManifestOutcome, ambiance, ecsStepHelper);
    }

    // Get EcsGitFetchFileConfig list for scalable targets if present
    List<ManifestOutcome> ecsScalableTargetManifestOutcomes =
        ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalableTargetDefinition);

    List<EcsGitFetchFileConfig> ecsScalableTargetGitFetchFileConfigs = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(ecsScalableTargetManifestOutcomes)) {
      for (ManifestOutcome ecsScalableTargetManifestOutcome : ecsScalableTargetManifestOutcomes) {
        if (ManifestStoreType.isInGitSubset(ecsScalableTargetManifestOutcome.getStore().getKind())) {
          ecsScalableTargetGitFetchFileConfigs.add(
              getEcsGitFetchFilesConfigFromManifestOutcome(ecsScalableTargetManifestOutcome, ambiance, ecsStepHelper));
        }
      }
    }

    // Get EcsGitFetchFileConfig list for scaling policies if present
    List<ManifestOutcome> ecsScalingPolicyManifestOutcomes =
        ecsStepHelper.getManifestOutcomesByType(ecsManifestOutcomes, ManifestType.EcsScalingPolicyDefinition);

    List<EcsGitFetchFileConfig> ecsScalingPolicyGitFetchFileConfigs = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(ecsScalingPolicyManifestOutcomes)) {
      for (ManifestOutcome ecsScalingPolicyManifestOutcome : ecsScalingPolicyManifestOutcomes) {
        if (ManifestStoreType.isInGitSubset(ecsScalingPolicyManifestOutcome.getStore().getKind())) {
          ecsScalingPolicyGitFetchFileConfigs.add(
              getEcsGitFetchFilesConfigFromManifestOutcome(ecsScalingPolicyManifestOutcome, ambiance, ecsStepHelper));
        }
      }
    }

    return getGitFetchFileTaskResponse(ambiance, false, stepElementParameters, ecsGitFetchPassThroughData,
        ecsTaskDefinitionGitFetchFileConfig, ecsServiceDefinitionGitFetchFileConfig,
        ecsScalableTargetGitFetchFileConfigs, ecsScalingPolicyGitFetchFileConfigs);
  }

  private EcsRunTaskManifestsContent getHarnessStoreRunTaskFilesContent(Ambiance ambiance,
      List<ManifestOutcome> ecsRunTaskManifestOutcomes, LogCallback logCallback, EcsStepHelper ecsStepHelper) {
    ManifestOutcome ecsRunTaskDefinitionManifestOutcome =
        ecsStepHelper.getEcsTaskDefinitionManifestOutcome(ecsRunTaskManifestOutcomes);
    StoreConfig ecsRunTaskDefinitionStoreConfig = ecsRunTaskDefinitionManifestOutcome.getStore();

    String taskDefinitionFileContent = null;

    if (ecsRunTaskDefinitionStoreConfig != null && ecsRunTaskDefinitionStoreConfig.getKind() == HARNESS_STORE_TYPE) {
      taskDefinitionFileContent =
          fetchFilesContentFromLocalStore(ambiance, ecsRunTaskDefinitionManifestOutcome, logCallback).get(0);
      taskDefinitionFileContent = engineExpressionService.renderExpression(ambiance, taskDefinitionFileContent);
    }

    ManifestOutcome ecsRunTaskRequestDefinitionManifestOutcome =
        ecsStepHelper.getEcsRunTaskRequestDefinitionManifestOutcome(ecsRunTaskManifestOutcomes);
    StoreConfig ecsRunTaskRequestDefinitionStoreConfig = ecsRunTaskRequestDefinitionManifestOutcome.getStore();
    String ecsRunTaskRequestDefinitionFileContent = null;

    if (ecsRunTaskRequestDefinitionStoreConfig.getKind() == HARNESS_STORE_TYPE) {
      ecsRunTaskRequestDefinitionFileContent =
          fetchFilesContentFromLocalStore(ambiance, ecsRunTaskRequestDefinitionManifestOutcome, logCallback).get(0);
      ecsRunTaskRequestDefinitionFileContent =
          engineExpressionService.renderExpression(ambiance, ecsRunTaskRequestDefinitionFileContent);
    }

    return EcsRunTaskManifestsContent.builder()
        .runTaskDefinitionFileContent(taskDefinitionFileContent)
        .runTaskRequestDefinitionFileContent(ecsRunTaskRequestDefinitionFileContent)
        .build();
  }

  private TaskChainResponse prepareEcsRunTaskGitFetchTask(Ambiance ambiance,
      StepElementParameters stepElementParameters, List<ManifestOutcome> ecsRunTaskManifestOutcomes,
      EcsGitFetchPassThroughData ecsGitFetchPassThroughData, EcsStepHelper ecsStepHelper) {
    ManifestOutcome ecsRunTaskDefinitionManifestOutcome =
        ecsStepHelper.getEcsTaskDefinitionManifestOutcome(ecsRunTaskManifestOutcomes);
    StoreConfig ecsRunTaskDefinitionStoreConfig = ecsRunTaskDefinitionManifestOutcome.getStore();

    EcsGitFetchRunTaskFileConfig taskDefinitionEcsGitFetchRunTaskFileConfig = null;
    if (ecsRunTaskDefinitionStoreConfig != null
        && ManifestStoreType.isInGitSubset(ecsRunTaskDefinitionStoreConfig.getKind())) {
      taskDefinitionEcsGitFetchRunTaskFileConfig =
          getEcsGitFetchRunTaskFileConfig(ecsRunTaskDefinitionManifestOutcome, ambiance);
    }

    EcsGitFetchRunTaskFileConfig ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig = null;
    ManifestOutcome ecsRunTaskRequestDefinitionManifestOutcome =
        ecsStepHelper.getEcsRunTaskRequestDefinitionManifestOutcome(ecsRunTaskManifestOutcomes);
    StoreConfig ecsRunTaskRequestDefinitionStoreConfig = ecsRunTaskRequestDefinitionManifestOutcome.getStore();
    if (ManifestStoreType.isInGitSubset(ecsRunTaskRequestDefinitionStoreConfig.getKind())) {
      ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig =
          getEcsGitFetchRunTaskFileConfig(ecsRunTaskRequestDefinitionManifestOutcome, ambiance);
    }

    // if both task definition, ecs run task request definition are from Harness Store

    return getGitFetchFileRunTaskResponse(ambiance, false, stepElementParameters, ecsGitFetchPassThroughData,
        taskDefinitionEcsGitFetchRunTaskFileConfig, ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig);
  }

  private TaskChainResponse prepareEcsRunTaskS3FetchTask(Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsS3FetchPassThroughData ecsS3FetchRunTaskPassThroughData, EcsRunTaskS3FileConfigs ecsRunTaskS3FileConfigs) {
    EcsS3FetchFileConfig runTaskDefinitionS3FetchFileConfig =
        ecsRunTaskS3FileConfigs.getRunTaskDefinitionS3FetchFileConfig();

    EcsS3FetchFileConfig runTaskRequestDefinitionS3FetchFileConfig =
        ecsRunTaskS3FileConfigs.getRunTaskRequestDefinitionS3FetchFileConfig();

    return getS3FetchFileTaskRunTaskResponse(ambiance, false, stepElementParameters, ecsS3FetchRunTaskPassThroughData,
        runTaskDefinitionS3FetchFileConfig, runTaskRequestDefinitionS3FetchFileConfig);
  }

  EcsGitFetchFileConfig getEcsGitFetchFilesConfigFromManifestOutcome(
      ManifestOutcome manifestOutcome, Ambiance ambiance, EcsStepHelper ecsStepHelper) {
    StoreConfig storeConfig = manifestOutcome.getStore();
    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Ecs step", USER);
    }
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
    return getEcsGitFetchFilesConfig(ambiance, gitStoreConfig, manifestOutcome, ecsStepHelper);
  }

  public String getTaskDefinitionArn(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.ECS_SERVICE_SWEEPING_OUTPUT));
    if (!optionalSweepingOutput.isFound()) {
      throw new InvalidRequestException(INVALID_ECS_TASK_DEFINITION, USER);
    }
    EcsServiceCustomSweepingOutput ecsServiceCustomSweepingOutput =
        (EcsServiceCustomSweepingOutput) optionalSweepingOutput.getOutput();
    if (EmptyPredicate.isEmpty(ecsServiceCustomSweepingOutput.getEcsTaskDefinitionArn())) {
      throw new InvalidRequestException(INVALID_ECS_TASK_DEFINITION, USER);
    }
    return engineExpressionService.renderExpression(ambiance, ecsServiceCustomSweepingOutput.getEcsTaskDefinitionArn());
  }

  private boolean validateTaskDefinitionPresence(
      Ambiance ambiance, List<ManifestOutcome> manifestOutcomes, EcsStepHelper ecsStepHelper) {
    if (ecsStepHelper.getEcsTaskDefinitionManifestOutcome(manifestOutcomes) != null) {
      return true;
    }
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.ECS_SERVICE_SWEEPING_OUTPUT));
    if (!optionalSweepingOutput.isFound()) {
      throw new InvalidRequestException(INVALID_ECS_TASK_DEFINITION, USER);
    }
    EcsServiceCustomSweepingOutput ecsServiceCustomSweepingOutput =
        (EcsServiceCustomSweepingOutput) optionalSweepingOutput.getOutput();
    if (EmptyPredicate.isEmpty(ecsServiceCustomSweepingOutput.getEcsTaskDefinitionArn())) {
      throw new InvalidRequestException(INVALID_ECS_TASK_DEFINITION, USER);
    }
    return true;
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

  private EcsS3FetchFileConfig getEcsS3FetchFilesConfigFromManifestOutcome(
      ManifestOutcome manifestOutcome, Ambiance ambiance, EcsStepHelper ecsStepHelper) {
    StoreConfig storeConfig = manifestOutcome.getStore();
    if (!ManifestStoreType.S3.equals(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Ecs step", USER);
    }
    S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
    return getEcsS3FetchFilesConfig(ambiance, s3StoreConfig, manifestOutcome, ecsStepHelper);
  }

  private EcsS3FetchFileConfig getEcsRunTaskS3FetchFilesConfigFromManifestOutcome(
      Ambiance ambiance, ManifestOutcome manifestOutcome) {
    StoreConfig storeConfig = manifestOutcome.getStore();
    if (!ManifestStoreType.S3.equals(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Ecs step", USER);
    }
    S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
    return getEcsRunTaskS3FetchFilesConfig(ambiance, s3StoreConfig, manifestOutcome);
  }

  private EcsS3FetchFileConfig getEcsS3FetchFilesConfig(
      Ambiance ambiance, S3StoreConfig s3StoreConfig, ManifestOutcome manifestOutcome, EcsStepHelper ecsStepHelper) {
    return EcsS3FetchFileConfig.builder()
        .s3StoreDelegateConfig(getS3StoreDelegateConfig(ambiance, s3StoreConfig, manifestOutcome))
        .identifier(manifestOutcome.getIdentifier())
        .manifestType(manifestOutcome.getType())
        .succeedIfFileNotFound(false)
        .build();
  }

  private EcsS3FetchFileConfig getEcsRunTaskS3FetchFilesConfig(
      Ambiance ambiance, S3StoreConfig s3StoreConfig, ManifestOutcome manifestOutcome) {
    return EcsS3FetchFileConfig.builder()
        .s3StoreDelegateConfig(getS3StoreDelegateConfig(ambiance, s3StoreConfig, manifestOutcome))
        .succeedIfFileNotFound(false)
        .build();
  }

  private EcsGitFetchRunTaskFileConfig getEcsGitFetchRunTaskFileConfig(
      ManifestOutcome manifestOutcome, Ambiance ambiance) {
    StoreConfig storeConfig = manifestOutcome.getStore();

    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException(
          format("Invalid kind %s of storeConfig for Ecs run task step", storeConfig.getKind()), USER);
    }

    return getEcsGitFetchRunTaskFileConfig(ambiance, manifestOutcome);
  }

  private EcsGitFetchRunTaskFileConfig getEcsGitFetchRunTaskFileConfig(
      Ambiance ambiance, ManifestOutcome manifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) manifestOutcome.getStore();
    return EcsGitFetchRunTaskFileConfig.builder()
        .gitStoreDelegateConfig(getGitStoreDelegateConfigForRunTask(ambiance, manifestOutcome))
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

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, ecsSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(ecsSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(ecsGitFetchPassThroughData)
        .build();
  }

  private TaskChainResponse getS3FetchFileTaskRunTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
      StepElementParameters stepElementParameters, EcsS3FetchPassThroughData ecsS3FetchRunTaskPassThroughData,
      EcsS3FetchFileConfig runTaskDefinitionS3FetchFileConfig,
      EcsS3FetchFileConfig runTaskRequestDefinitionS3FetchFileConfig) {
    EcsS3FetchRunTaskRequest ecsS3FetchRunTaskRequest =
        EcsS3FetchRunTaskRequest.builder()
            .runTaskDefinitionS3FetchFileConfig(runTaskDefinitionS3FetchFileConfig)
            .runTaskRequestDefinitionS3FetchFileConfig(runTaskRequestDefinitionS3FetchFileConfig)
            .shouldOpenLogStream(shouldOpenLogStream)
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.ECS_S3_FETCH_TASK_NG.name())
                                  .parameters(new Object[] {ecsS3FetchRunTaskRequest})
                                  .build();

    String taskName = TaskType.ECS_S3_FETCH_TASK_NG.getDisplayName();

    EcsSpecParameters ecsSpecParameters = (EcsSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, ecsSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(ecsSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(ecsS3FetchRunTaskPassThroughData)
        .build();
  }

  TaskChainResponse getGitFetchFileRunTaskResponse(Ambiance ambiance, boolean shouldOpenLogStream,
      StepElementParameters stepElementParameters, EcsGitFetchPassThroughData ecsGitFetchPassThroughData,
      EcsGitFetchRunTaskFileConfig taskDefinitionEcsGitFetchRunTaskFileConfig,
      EcsGitFetchRunTaskFileConfig ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig) {
    String accountId = AmbianceUtils.getAccountId(ambiance);

    EcsGitFetchRunTaskRequest ecsGitFetchRunTaskRequest =
        EcsGitFetchRunTaskRequest.builder()
            .accountId(accountId)
            .taskDefinitionEcsGitFetchRunTaskFileConfig(taskDefinitionEcsGitFetchRunTaskFileConfig)
            .ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig(
                ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig)
            .shouldOpenLogStream(shouldOpenLogStream)
            .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.ECS_GIT_FETCH_RUN_TASK_NG.name())
                                  .parameters(new Object[] {ecsGitFetchRunTaskRequest})
                                  .build();

    String taskName = TaskType.ECS_GIT_FETCH_RUN_TASK_NG.getDisplayName();

    EcsSpecParameters ecsSpecParameters = (EcsSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, ecsSpecParameters.getCommandUnits(), taskName,
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

        taskChainResponse = handleEcsGitFetchFilesResponseRolling(
            ecsGitFetchResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsGitFetchPassThroughData);

      } else if (responseData instanceof EcsS3FetchResponse) {
        EcsS3FetchResponse ecsS3FetchResponse = (EcsS3FetchResponse) responseData;
        EcsS3FetchPassThroughData ecsS3FetchPassThroughData = (EcsS3FetchPassThroughData) passThroughData;

        taskChainResponse = handleEcsS3FetchFilesResponseRolling(
            ecsS3FetchResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsS3FetchPassThroughData);
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

        taskChainResponse = handleEcsGitFetchFilesResponseCanary(
            ecsGitFetchResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsGitFetchPassThroughData);
      } else if (responseData instanceof EcsS3FetchResponse) { // if EcsS3FetchResponse is received

        EcsS3FetchResponse ecsS3FetchResponse = (EcsS3FetchResponse) responseData;
        EcsS3FetchPassThroughData ecsS3FetchPassThroughData = (EcsS3FetchPassThroughData) passThroughData;

        taskChainResponse = handleEcsS3FetchFilesResponseCanary(
            ecsS3FetchResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsS3FetchPassThroughData);
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
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    UnitProgressData unitProgressData = null;
    TaskChainResponse taskChainResponse = null;
    try {
      if (responseData instanceof EcsGitFetchResponse) { // if EcsGitFetchResponse is received

        EcsGitFetchResponse ecsGitFetchResponse = (EcsGitFetchResponse) responseData;
        EcsGitFetchPassThroughData ecsGitFetchPassThroughData = (EcsGitFetchPassThroughData) passThroughData;

        taskChainResponse = handleEcsGitFetchFilesResponseBlueGreen(
            ecsGitFetchResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsGitFetchPassThroughData);

      } else if (responseData instanceof EcsS3FetchResponse) {
        EcsS3FetchResponse ecsS3FetchResponse = (EcsS3FetchResponse) responseData;
        EcsS3FetchPassThroughData ecsS3FetchPassThroughData = (EcsS3FetchPassThroughData) passThroughData;

        taskChainResponse = handleEcsS3FetchFilesResponseBlueGreen(
            ecsS3FetchResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsS3FetchPassThroughData);
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

  public TaskChainResponse executeNextLinkRunTask(EcsStepExecutor ecsStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier, EcsStepHelper ecsStepHelper) throws Exception {
    ResponseData responseData = responseDataSupplier.get();
    UnitProgressData unitProgressData = null;
    TaskChainResponse taskChainResponse = null;
    cdExpressionResolver.updateExpressions(ambiance, stepElementParameters);
    try {
      if (responseData instanceof EcsGitFetchRunTaskResponse) { // if EcsGitFetchRunTaskResponse is received

        EcsGitFetchRunTaskResponse ecsGitFetchRunTaskResponse = (EcsGitFetchRunTaskResponse) responseData;
        EcsGitFetchPassThroughData ecsGitFetchPassThroughData = (EcsGitFetchPassThroughData) passThroughData;

        taskChainResponse = handleEcsGitFetchFilesResponseRunTask(
            ecsGitFetchRunTaskResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsGitFetchPassThroughData);
      } else if (responseData instanceof EcsS3FetchRunTaskResponse) {
        EcsS3FetchRunTaskResponse ecsS3FetchRunTaskResponse = (EcsS3FetchRunTaskResponse) responseData;
        EcsS3FetchPassThroughData ecsS3FetchPassThroughData = (EcsS3FetchPassThroughData) passThroughData;
        taskChainResponse = handleEcsS3FetchFilesResponseRunTask(
            ecsS3FetchRunTaskResponse, ecsStepExecutor, ambiance, stepElementParameters, ecsS3FetchPassThroughData);
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

  private EcsManifestsContent mergeManifestsFromS3AndOtherFileStore(
      EcsS3FetchResponse ecsS3FetchResponse, Ambiance ambiance, EcsS3FetchPassThroughData ecsS3FetchPassThroughData) {
    EcsManifestsContent ecsOtherStoreContent = ecsS3FetchPassThroughData.getEcsOtherStoreContents();

    String ecsTaskDefinitionFileContent;
    if (ecsS3FetchResponse.getEcsS3TaskDefinitionContent() != null) {
      ecsTaskDefinitionFileContent =
          engineExpressionService.renderExpression(ambiance, ecsS3FetchResponse.getEcsS3TaskDefinitionContent());
    } else {
      ecsTaskDefinitionFileContent = ecsOtherStoreContent.getEcsTaskDefinitionFileContent();
    }

    String ecsServiceDefinitionFileContent;
    if (ecsS3FetchResponse.getEcsS3ServiceDefinitionContent() != null) {
      ecsServiceDefinitionFileContent =
          engineExpressionService.renderExpression(ambiance, ecsS3FetchResponse.getEcsS3ServiceDefinitionContent());
    } else {
      ecsServiceDefinitionFileContent = ecsOtherStoreContent.getEcsServiceDefinitionFileContent();
    }

    List<String> ecsScalableTargetFileContentList = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(ecsS3FetchResponse.getEcsS3ScalableTargetContents())) {
      for (String fileContent : ecsS3FetchResponse.getEcsS3ScalableTargetContents()) {
        ecsScalableTargetFileContentList.add(engineExpressionService.renderExpression(ambiance, fileContent));
      }
    }

    if (CollectionUtils.isNotEmpty(ecsOtherStoreContent.getEcsScalableTargetManifestContentList())) {
      ecsScalableTargetFileContentList.addAll(ecsOtherStoreContent.getEcsScalableTargetManifestContentList());
    }

    List<String> ecsScalingPolicyFileContentList = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(ecsS3FetchResponse.getEcsS3ScalingPolicyContents())) {
      for (String fileContent : ecsS3FetchResponse.getEcsS3ScalingPolicyContents()) {
        ecsScalingPolicyFileContentList.add(engineExpressionService.renderExpression(ambiance, fileContent));
      }
    }

    if (CollectionUtils.isNotEmpty(ecsOtherStoreContent.getEcsScalingPolicyManifestContentList())) {
      ecsScalingPolicyFileContentList.addAll(ecsOtherStoreContent.getEcsScalingPolicyManifestContentList());
    }
    return EcsManifestsContent.builder()
        .ecsTaskDefinitionFileContent(ecsTaskDefinitionFileContent)
        .ecsServiceDefinitionFileContent(ecsServiceDefinitionFileContent)
        .ecsScalableTargetManifestContentList(ecsScalableTargetFileContentList)
        .ecsScalingPolicyManifestContentList(ecsScalingPolicyFileContentList)
        .build();
  }

  private EcsManifestsContent mergeManifestsFromGitAndHarnessFileStore(EcsGitFetchResponse ecsGitFetchResponse,
      Ambiance ambiance, EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
    // Get ecsTaskDefinitionFileContent from ecsGitFetchResponse
    FetchFilesResult ecsTaskDefinitionFetchFileResult = ecsGitFetchResponse.getEcsTaskDefinitionFetchFilesResult();

    // Get task definition either from Git ot Harness File Store
    String ecsTaskDefinitionFileContent;
    if (ecsTaskDefinitionFetchFileResult != null) {
      ecsTaskDefinitionFileContent = getRenderedTaskDefinitionFileContent(ecsGitFetchResponse, ambiance);
    } else {
      ecsTaskDefinitionFileContent = ecsGitFetchPassThroughData.getTaskDefinitionHarnessFileContent();
    }

    // Get ecsServiceDefinitionFetchFileResult from ecsGitFetchResponse
    FetchFilesResult ecsServiceDefinitionFetchFileResult =
        ecsGitFetchResponse.getEcsServiceDefinitionFetchFilesResult();

    // Get service definition either from Git ot Harness File Store
    String ecsServiceDefinitionFileContent;
    if (ecsServiceDefinitionFetchFileResult != null) {
      ecsServiceDefinitionFileContent = getRenderedServiceDefinitionFileContent(ecsGitFetchResponse, ambiance);
    } else {
      ecsServiceDefinitionFileContent = ecsGitFetchPassThroughData.getServiceDefinitionHarnessFileContent();
    }

    // Get ecsScalableTargetManifestContentList from ecsGitFetchResponse if present
    List<String> ecsScalableTargetManifestContentList = new ArrayList<>();
    List<FetchFilesResult> ecsScalableTargetFetchFilesResults =
        ecsGitFetchResponse.getEcsScalableTargetFetchFilesResults();

    if (CollectionUtils.isNotEmpty(ecsScalableTargetFetchFilesResults)) {
      ecsScalableTargetManifestContentList = getRenderedScalableTargetsFileContent(ecsGitFetchResponse, ambiance);
    }

    // Add scalable targets from Harness File Store
    if (CollectionUtils.isNotEmpty(ecsGitFetchPassThroughData.getScalableTargetHarnessFileContentList())) {
      ecsScalableTargetManifestContentList.addAll(ecsGitFetchPassThroughData.getScalableTargetHarnessFileContentList());
    }

    // Get ecsScalingPolicyManifestContentList from ecsGitFetchResponse if present
    List<String> ecsScalingPolicyManifestContentList = new ArrayList<>();
    List<FetchFilesResult> ecsScalingPolicyFetchFilesResults =
        ecsGitFetchResponse.getEcsScalingPolicyFetchFilesResults();
    if (CollectionUtils.isNotEmpty(ecsScalingPolicyFetchFilesResults)) {
      ecsScalingPolicyManifestContentList = getRenderedScalingPoliciesFileContent(ecsGitFetchResponse, ambiance);
    }

    // Add scaling policies from Harness File Store
    if (CollectionUtils.isNotEmpty(ecsGitFetchPassThroughData.getScalingPolicyHarnessFileContentList())) {
      ecsScalingPolicyManifestContentList.addAll(ecsGitFetchPassThroughData.getScalingPolicyHarnessFileContentList());
    }

    return EcsManifestsContent.builder()
        .ecsTaskDefinitionFileContent(ecsTaskDefinitionFileContent)
        .ecsServiceDefinitionFileContent(ecsServiceDefinitionFileContent)
        .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
        .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
        .build();
  }

  private TaskChainResponse handleEcsS3FetchFilesResponseRolling(EcsS3FetchResponse ecsS3FetchResponse,
      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsS3FetchPassThroughData ecsS3FetchPassThroughData) {
    if (ecsS3FetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureS3Task(ecsS3FetchResponse);
    }

    // mergeManifests Content
    EcsManifestsContent ecsManifestsContent =
        mergeManifestsFromS3AndOtherFileStore(ecsS3FetchResponse, ambiance, ecsS3FetchPassThroughData);

    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .infrastructureOutcome(ecsS3FetchPassThroughData.getInfrastructureOutcome())
            .ecsTaskDefinitionManifestContent(ecsManifestsContent.getEcsTaskDefinitionFileContent())
            .ecsServiceDefinitionManifestContent(ecsManifestsContent.getEcsServiceDefinitionFileContent())
            .ecsScalableTargetManifestContentList(ecsManifestsContent.getEcsScalableTargetManifestContentList())
            .ecsScalingPolicyManifestContentList(ecsManifestsContent.getEcsScalingPolicyManifestContentList())
            .build();
    return ecsStepExecutor.executeEcsPrepareRollbackTask(ambiance, stepElementParameters,
        ecsPrepareRollbackDataPassThroughData, ecsS3FetchResponse.getUnitProgressData());
  }

  private TaskChainResponse handleEcsGitFetchFilesResponseRolling(EcsGitFetchResponse ecsGitFetchResponse,
      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
    if (ecsGitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureGitTask(ecsGitFetchResponse);
    }

    EcsManifestsContent ecsManifestsContent =
        mergeManifestsFromGitAndHarnessFileStore(ecsGitFetchResponse, ambiance, ecsGitFetchPassThroughData);

    TaskChainResponse taskChainResponse = null;
    if (ecsGitFetchPassThroughData.getEcsS3ManifestFileConfigs() == null) {
      EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
          EcsPrepareRollbackDataPassThroughData.builder()
              .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
              .ecsTaskDefinitionManifestContent(ecsManifestsContent.getEcsTaskDefinitionFileContent())
              .ecsServiceDefinitionManifestContent(ecsManifestsContent.getEcsServiceDefinitionFileContent())
              .ecsScalableTargetManifestContentList(ecsManifestsContent.getEcsScalableTargetManifestContentList())
              .ecsScalingPolicyManifestContentList(ecsManifestsContent.getEcsScalingPolicyManifestContentList())
              .build();
      taskChainResponse = ecsStepExecutor.executeEcsPrepareRollbackTask(ambiance, stepElementParameters,
          ecsPrepareRollbackDataPassThroughData, ecsGitFetchResponse.getUnitProgressData());
    } else {
      EcsS3FetchPassThroughData ecsS3FetchPassThroughData =
          EcsS3FetchPassThroughData.builder()
              .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
              .ecsOtherStoreContents(ecsManifestsContent)
              .build();
      taskChainResponse = prepareEcsManifestS3FetchTask(ambiance, stepElementParameters, ecsS3FetchPassThroughData,
          ecsGitFetchPassThroughData.getEcsS3ManifestFileConfigs());
    }
    return taskChainResponse;
  }

  private TaskChainResponse handleEcsS3FetchFilesResponseCanary(EcsS3FetchResponse ecsS3FetchResponse,
      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsS3FetchPassThroughData ecsS3FetchPassThroughData) {
    if (ecsS3FetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureS3Task(ecsS3FetchResponse);
    }

    EcsManifestsContent ecsManifestsContent =
        mergeManifestsFromS3AndOtherFileStore(ecsS3FetchResponse, ambiance, ecsS3FetchPassThroughData);

    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(ecsS3FetchPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(ecsS3FetchResponse.getUnitProgressData())
            .build();

    EcsStepExecutorParams ecsStepExecutorParams =
        EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .ecsTaskDefinitionManifestContent(ecsManifestsContent.getEcsTaskDefinitionFileContent())
            .ecsServiceDefinitionManifestContent(ecsManifestsContent.getEcsServiceDefinitionFileContent())
            .ecsScalableTargetManifestContentList(ecsManifestsContent.getEcsScalableTargetManifestContentList())
            .ecsScalingPolicyManifestContentList(ecsManifestsContent.getEcsScalingPolicyManifestContentList())
            .build();

    return ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
        ecsS3FetchResponse.getUnitProgressData(), ecsStepExecutorParams);
  }

  private TaskChainResponse handleEcsGitFetchFilesResponseCanary(EcsGitFetchResponse ecsGitFetchResponse,
      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
    if (ecsGitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureGitTask(ecsGitFetchResponse);
    }

    EcsManifestsContent ecsManifestsContent =
        mergeManifestsFromGitAndHarnessFileStore(ecsGitFetchResponse, ambiance, ecsGitFetchPassThroughData);

    TaskChainResponse taskChainResponse = null;
    if (ecsGitFetchPassThroughData.getEcsS3ManifestFileConfigs() == null) {
      EcsExecutionPassThroughData ecsExecutionPassThroughData =
          EcsExecutionPassThroughData.builder()
              .infrastructure(ecsGitFetchPassThroughData.getInfrastructureOutcome())
              .lastActiveUnitProgressData(ecsGitFetchResponse.getUnitProgressData())
              .build();

      EcsStepExecutorParams ecsStepExecutorParams =
          EcsStepExecutorParams.builder()
              .shouldOpenFetchFilesLogStream(false)
              .ecsTaskDefinitionManifestContent(ecsManifestsContent.getEcsTaskDefinitionFileContent())
              .ecsServiceDefinitionManifestContent(ecsManifestsContent.getEcsServiceDefinitionFileContent())
              .ecsScalableTargetManifestContentList(ecsManifestsContent.getEcsScalableTargetManifestContentList())
              .ecsScalingPolicyManifestContentList(ecsManifestsContent.getEcsScalingPolicyManifestContentList())
              .build();

      taskChainResponse = ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
          ecsGitFetchResponse.getUnitProgressData(), ecsStepExecutorParams);
    } else {
      EcsS3FetchPassThroughData ecsS3FetchPassThroughData =
          EcsS3FetchPassThroughData.builder()
              .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
              .ecsOtherStoreContents(ecsManifestsContent)
              .build();
      taskChainResponse = prepareEcsManifestS3FetchTask(ambiance, stepElementParameters, ecsS3FetchPassThroughData,
          ecsGitFetchPassThroughData.getEcsS3ManifestFileConfigs());
    }
    return taskChainResponse;
  }

  private TaskChainResponse handleEcsGitFetchFilesResponseRunTask(EcsGitFetchRunTaskResponse ecsGitFetchRunTaskResponse,
      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
    if (ecsGitFetchRunTaskResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      EcsGitFetchFailurePassThroughData ecsGitFetchFailurePassThroughData =
          EcsGitFetchFailurePassThroughData.builder()
              .errorMsg(ecsGitFetchRunTaskResponse.getErrorMessage())
              .unitProgressData(ecsGitFetchRunTaskResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().passThroughData(ecsGitFetchFailurePassThroughData).chainEnd(true).build();
    }

    // Get ecsTaskDefinitionFileContent from ecsGitFetchResponse
    FetchFilesResult ecsTaskDefinitionFetchFileResult =
        ecsGitFetchRunTaskResponse.getEcsTaskDefinitionFetchFilesResult();
    String ecsTaskDefinitionFileContent = null;

    if (ecsTaskDefinitionFetchFileResult != null) {
      ecsTaskDefinitionFileContent = ecsTaskDefinitionFetchFileResult.getFiles().get(0).getFileContent();
      ecsTaskDefinitionFileContent = engineExpressionService.renderExpression(ambiance, ecsTaskDefinitionFileContent);
    } else {
      ecsTaskDefinitionFileContent = ecsGitFetchPassThroughData.getTaskDefinitionHarnessFileContent();
    }

    FetchFilesResult ecsRunTaskRequestDefinitionFetchFilesResult =
        ecsGitFetchRunTaskResponse.getEcsRunTaskDefinitionRequestFetchFilesResult();

    String ecsRunTaskRequestDefinitionFileContent = null;

    if (ecsRunTaskRequestDefinitionFetchFilesResult != null) {
      ecsRunTaskRequestDefinitionFileContent =
          ecsRunTaskRequestDefinitionFetchFilesResult.getFiles().get(0).getFileContent();
      ecsRunTaskRequestDefinitionFileContent =
          engineExpressionService.renderExpression(ambiance, ecsRunTaskRequestDefinitionFileContent);
    } else {
      ecsRunTaskRequestDefinitionFileContent =
          ecsGitFetchPassThroughData.getEcsRunTaskRequestDefinitionHarnessFileContent();
    }

    TaskChainResponse taskChainResponse = null;
    if (ecsGitFetchPassThroughData.getEcsRunTaskS3FileConfigs() == null) {
      EcsStepExecutorParams ecsStepExecutorParams =
          EcsStepExecutorParams.builder()
              .shouldOpenFetchFilesLogStream(false)
              .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
              .ecsRunTaskRequestDefinitionManifestContent(ecsRunTaskRequestDefinitionFileContent)
              .build();

      EcsExecutionPassThroughData ecsExecutionPassThroughData =
          EcsExecutionPassThroughData.builder()
              .infrastructure(ecsGitFetchPassThroughData.getInfrastructureOutcome())
              .lastActiveUnitProgressData(ecsGitFetchRunTaskResponse.getUnitProgressData())
              .build();

      taskChainResponse = ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
          ecsGitFetchRunTaskResponse.getUnitProgressData(), ecsStepExecutorParams);
    } else {
      EcsRunTaskManifestsContent ecsOtherStoreRunTaskContent =
          EcsRunTaskManifestsContent.builder()
              .runTaskDefinitionFileContent(ecsTaskDefinitionFileContent)
              .runTaskRequestDefinitionFileContent(ecsRunTaskRequestDefinitionFileContent)
              .build();
      EcsS3FetchPassThroughData ecsS3FetchRunTaskPassThroughData =
          EcsS3FetchPassThroughData.builder()
              .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
              .ecsOtherStoreRunTaskContent(ecsOtherStoreRunTaskContent)
              .build();
      taskChainResponse = prepareEcsRunTaskS3FetchTask(ambiance, stepElementParameters,
          ecsS3FetchRunTaskPassThroughData, ecsGitFetchPassThroughData.getEcsRunTaskS3FileConfigs());
    }
    return taskChainResponse;
  }

  private TaskChainResponse handleEcsS3FetchFilesResponseRunTask(EcsS3FetchRunTaskResponse ecsS3FetchRunTaskResponse,
      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsS3FetchPassThroughData ecsS3FetchPassThroughData) {
    if (ecsS3FetchRunTaskResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      EcsS3FetchFailurePassThroughData ecsS3FetchFailurePassThroughData =
          EcsS3FetchFailurePassThroughData.builder()
              .errorMsg(ecsS3FetchRunTaskResponse.getErrorMessage())
              .unitProgressData(ecsS3FetchRunTaskResponse.getUnitProgressData())
              .build();
      return TaskChainResponse.builder().passThroughData(ecsS3FetchFailurePassThroughData).chainEnd(true).build();
    }

    EcsRunTaskManifestsContent ecsRunTaskManifestsContent = ecsS3FetchPassThroughData.getEcsOtherStoreRunTaskContent();

    String ecsRunTaskDefinitionFileContent = null;
    if (ecsS3FetchRunTaskResponse.getRunTaskDefinitionFileContent() != null) {
      ecsRunTaskDefinitionFileContent = ecsS3FetchRunTaskResponse.getRunTaskDefinitionFileContent();
      ecsRunTaskDefinitionFileContent =
          engineExpressionService.renderExpression(ambiance, ecsRunTaskDefinitionFileContent);
    } else {
      ecsRunTaskDefinitionFileContent = ecsRunTaskManifestsContent.getRunTaskDefinitionFileContent();
    }

    String ecsRunTaskRequestDefinitionFileContent = null;
    if (ecsS3FetchRunTaskResponse.getRunTaskRequestDefinitionFileContent() != null) {
      ecsRunTaskRequestDefinitionFileContent = ecsS3FetchRunTaskResponse.getRunTaskRequestDefinitionFileContent();
      ecsRunTaskRequestDefinitionFileContent =
          engineExpressionService.renderExpression(ambiance, ecsRunTaskRequestDefinitionFileContent);
    } else {
      ecsRunTaskRequestDefinitionFileContent = ecsRunTaskManifestsContent.getRunTaskRequestDefinitionFileContent();
    }

    EcsStepExecutorParams ecsStepExecutorParams =
        EcsStepExecutorParams.builder()
            .shouldOpenFetchFilesLogStream(false)
            .ecsTaskDefinitionManifestContent(ecsRunTaskDefinitionFileContent)
            .ecsRunTaskRequestDefinitionManifestContent(ecsRunTaskRequestDefinitionFileContent)
            .build();

    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder()
            .infrastructure(ecsS3FetchPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(ecsS3FetchRunTaskResponse.getUnitProgressData())
            .build();

    return ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
        ecsS3FetchRunTaskResponse.getUnitProgressData(), ecsStepExecutorParams);
  }

  private TaskChainResponse handleEcsS3FetchFilesResponseBlueGreen(EcsS3FetchResponse ecsS3FetchResponse,
      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsS3FetchPassThroughData ecsS3FetchPassThroughData) {
    if (ecsS3FetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureS3Task(ecsS3FetchResponse);
    }
    EcsManifestsContent ecsOtherStoreContent = ecsS3FetchPassThroughData.getEcsOtherStoreContents();

    String ecsTaskDefinitionFileContent;
    if (ecsS3FetchResponse.getEcsS3TaskDefinitionContent() != null) {
      ecsTaskDefinitionFileContent =
          engineExpressionService.renderExpression(ambiance, ecsS3FetchResponse.getEcsS3TaskDefinitionContent());
    } else {
      ecsTaskDefinitionFileContent = ecsOtherStoreContent.getEcsTaskDefinitionFileContent();
    }

    StringBuilder key = new StringBuilder();
    if (ecsS3FetchPassThroughData.getOtherStoreTargetGroupArnKey() != null) {
      key = key.append(ecsS3FetchPassThroughData.getOtherStoreTargetGroupArnKey());
    } else {
      long timeStamp = System.currentTimeMillis();
      key = key.append(timeStamp).append("targetGroup");
    }

    String ecsServiceDefinitionFileContent;
    if (ecsS3FetchResponse.getEcsS3ServiceDefinitionContent() != null) {
      ecsServiceDefinitionFileContent = ecsS3FetchResponse.getEcsS3ServiceDefinitionContent();
      ecsServiceDefinitionFileContent = engineExpressionService.renderExpression(
          ambiance, ecsServiceDefinitionFileContent, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      if (ecsServiceDefinitionFileContent.contains(TARGET_GROUP_ARN_EXPRESSION)) {
        ecsServiceDefinitionFileContent =
            ecsServiceDefinitionFileContent.replace(TARGET_GROUP_ARN_EXPRESSION, key.toString());
      }
    } else {
      ecsServiceDefinitionFileContent = ecsOtherStoreContent.getEcsServiceDefinitionFileContent();
    }

    List<String> ecsScalableTargetFileContentList = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(ecsS3FetchResponse.getEcsS3ScalableTargetContents())) {
      for (String fileContent : ecsS3FetchResponse.getEcsS3ScalableTargetContents()) {
        ecsScalableTargetFileContentList.add(engineExpressionService.renderExpression(ambiance, fileContent));
      }
    }

    if (CollectionUtils.isNotEmpty(ecsOtherStoreContent.getEcsScalableTargetManifestContentList())) {
      ecsScalableTargetFileContentList.addAll(ecsOtherStoreContent.getEcsScalableTargetManifestContentList());
    }

    List<String> ecsScalingPolicyFileContentList = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(ecsS3FetchResponse.getEcsS3ScalingPolicyContents())) {
      for (String fileContent : ecsS3FetchResponse.getEcsS3ScalingPolicyContents()) {
        ecsScalingPolicyFileContentList.add(engineExpressionService.renderExpression(ambiance, fileContent));
      }
    }

    if (CollectionUtils.isNotEmpty(ecsOtherStoreContent.getEcsScalingPolicyManifestContentList())) {
      ecsScalingPolicyFileContentList.addAll(ecsOtherStoreContent.getEcsScalingPolicyManifestContentList());
    }

    EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder()
            .infrastructureOutcome(ecsS3FetchPassThroughData.getInfrastructureOutcome())
            .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
            .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
            .ecsScalableTargetManifestContentList(ecsScalableTargetFileContentList)
            .ecsScalingPolicyManifestContentList(ecsScalingPolicyFileContentList)
            .targetGroupArnKey(key.toString())
            .build();

    return ecsStepExecutor.executeEcsPrepareRollbackTask(ambiance, stepElementParameters,
        ecsPrepareRollbackDataPassThroughData, ecsS3FetchResponse.getUnitProgressData());
  }

  private TaskChainResponse handleEcsGitFetchFilesResponseBlueGreen(EcsGitFetchResponse ecsGitFetchResponse,
      EcsStepExecutor ecsStepExecutor, Ambiance ambiance, StepElementParameters stepElementParameters,
      EcsGitFetchPassThroughData ecsGitFetchPassThroughData) {
    if (ecsGitFetchResponse.getTaskStatus() != TaskStatus.SUCCESS) {
      return handleFailureGitTask(ecsGitFetchResponse);
    }

    // Get ecsTaskDefinitionFileContent from ecsGitFetchResponse
    FetchFilesResult ecsTaskDefinitionFetchFileResult = ecsGitFetchResponse.getEcsTaskDefinitionFetchFilesResult();

    // Get task definition either from Git ot Harness File Store
    String ecsTaskDefinitionFileContent;
    if (ecsTaskDefinitionFetchFileResult != null) {
      ecsTaskDefinitionFileContent = getRenderedTaskDefinitionFileContent(ecsGitFetchResponse, ambiance);
    } else {
      ecsTaskDefinitionFileContent = ecsGitFetchPassThroughData.getTaskDefinitionHarnessFileContent();
    }

    StringBuilder key = new StringBuilder();
    if (ecsGitFetchPassThroughData.getTargetGroupArnKey() != null) {
      key = key.append(ecsGitFetchPassThroughData.getTargetGroupArnKey());
    } else {
      long timeStamp = System.currentTimeMillis();
      key = key.append(timeStamp).append("targetGroup");
    }

    // Get ecsServiceDefinitionFileContent from ecsGitFetchResponse
    FetchFilesResult ecsServiceDefinitionFetchFileResult =
        ecsGitFetchResponse.getEcsServiceDefinitionFetchFilesResult();

    // Get service definition either from Git ot Harness File Store
    String ecsServiceDefinitionFileContent;
    if (ecsServiceDefinitionFetchFileResult != null) {
      ecsServiceDefinitionFileContent = ecsServiceDefinitionFetchFileResult.getFiles().get(0).getFileContent();
      ecsServiceDefinitionFileContent = engineExpressionService.renderExpression(
          ambiance, ecsServiceDefinitionFileContent, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      if (ecsServiceDefinitionFileContent.contains(TARGET_GROUP_ARN_EXPRESSION)) {
        ecsServiceDefinitionFileContent =
            ecsServiceDefinitionFileContent.replace(TARGET_GROUP_ARN_EXPRESSION, key.toString());
      }
    } else {
      ecsServiceDefinitionFileContent = ecsGitFetchPassThroughData.getServiceDefinitionHarnessFileContent();
    }

    // Get ecsScalableTargetManifestContentList from ecsGitFetchResponse if present
    List<String> ecsScalableTargetManifestContentList = new ArrayList<>();
    List<FetchFilesResult> ecsScalableTargetFetchFilesResults =
        ecsGitFetchResponse.getEcsScalableTargetFetchFilesResults();

    if (CollectionUtils.isNotEmpty(ecsScalableTargetFetchFilesResults)) {
      ecsScalableTargetManifestContentList = getRenderedScalableTargetsFileContent(ecsGitFetchResponse, ambiance);
    }

    // Add scalable targets from Harness File Store
    if (CollectionUtils.isNotEmpty(ecsGitFetchPassThroughData.getScalableTargetHarnessFileContentList())) {
      ecsScalableTargetManifestContentList.addAll(ecsGitFetchPassThroughData.getScalableTargetHarnessFileContentList());
    }

    // Get ecsScalingPolicyManifestContentList from ecsGitFetchResponse if present
    List<String> ecsScalingPolicyManifestContentList = new ArrayList<>();
    List<FetchFilesResult> ecsScalingPolicyFetchFilesResults =
        ecsGitFetchResponse.getEcsScalingPolicyFetchFilesResults();
    if (CollectionUtils.isNotEmpty(ecsScalingPolicyFetchFilesResults)) {
      ecsScalingPolicyManifestContentList = getRenderedScalingPoliciesFileContent(ecsGitFetchResponse, ambiance);
    }

    // Add scaling policies from Harness File Store
    if (CollectionUtils.isNotEmpty(ecsGitFetchPassThroughData.getScalingPolicyHarnessFileContentList())) {
      ecsScalingPolicyManifestContentList.addAll(ecsGitFetchPassThroughData.getScalingPolicyHarnessFileContentList());
    }

    TaskChainResponse taskChainResponse = null;
    if (ecsGitFetchPassThroughData.getEcsS3ManifestFileConfigs() == null) {
      EcsPrepareRollbackDataPassThroughData ecsPrepareRollbackDataPassThroughData =
          EcsPrepareRollbackDataPassThroughData.builder()
              .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
              .ecsTaskDefinitionManifestContent(ecsTaskDefinitionFileContent)
              .ecsServiceDefinitionManifestContent(ecsServiceDefinitionFileContent)
              .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
              .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
              .targetGroupArnKey(key.toString())
              .build();

      taskChainResponse = ecsStepExecutor.executeEcsPrepareRollbackTask(ambiance, stepElementParameters,
          ecsPrepareRollbackDataPassThroughData, ecsGitFetchResponse.getUnitProgressData());
    } else {
      EcsManifestsContent ecsOtherStoreContents =
          EcsManifestsContent.builder()
              .ecsTaskDefinitionFileContent(ecsTaskDefinitionFileContent)
              .ecsServiceDefinitionFileContent(ecsServiceDefinitionFileContent)
              .ecsScalableTargetManifestContentList(ecsScalableTargetManifestContentList)
              .ecsScalingPolicyManifestContentList(ecsScalingPolicyManifestContentList)
              .build();
      EcsS3FetchPassThroughData ecsS3FetchPassThroughData =
          EcsS3FetchPassThroughData.builder()
              .infrastructureOutcome(ecsGitFetchPassThroughData.getInfrastructureOutcome())
              .ecsOtherStoreContents(ecsOtherStoreContents)
              .otherStoreTargetGroupArnKey(key.toString())
              .build();
      taskChainResponse = prepareEcsManifestS3FetchTask(ambiance, stepElementParameters, ecsS3FetchPassThroughData,
          ecsGitFetchPassThroughData.getEcsS3ManifestFileConfigs());
    }
    return taskChainResponse;
  }

  private TaskChainResponse handleFailureGitTask(EcsGitFetchResponse ecsGitFetchResponse) {
    EcsGitFetchFailurePassThroughData ecsGitFetchFailurePassThroughData =
        EcsGitFetchFailurePassThroughData.builder()
            .errorMsg(ecsGitFetchResponse.getErrorMessage())
            .unitProgressData(ecsGitFetchResponse.getUnitProgressData())
            .build();
    return TaskChainResponse.builder().passThroughData(ecsGitFetchFailurePassThroughData).chainEnd(true).build();
  }

  private TaskChainResponse handleFailureS3Task(EcsS3FetchResponse ecsS3FetchResponse) {
    EcsS3FetchFailurePassThroughData ecsS3FetchFailurePassThroughData =
        EcsS3FetchFailurePassThroughData.builder()
            .errorMsg(ecsS3FetchResponse.getErrorMessage())
            .unitProgressData(ecsS3FetchResponse.getUnitProgressData())
            .build();
    return TaskChainResponse.builder().passThroughData(ecsS3FetchFailurePassThroughData).chainEnd(true).build();
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
    return engineExpressionService.renderExpression(
        ambiance, ecsServiceDefinitionFileContent, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
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

    String prodTargetGroupArn = null;
    String stageTargetGroupArn = null;

    if (ecsStepExecutor instanceof EcsBlueGreenCreateServiceStep) {
      EcsBlueGreenPrepareRollbackDataResult ecsBlueGreenPrepareRollbackDataResult =
          ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult();

      prodTargetGroupArn = ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getProdTargetGroupArn();
      stageTargetGroupArn = ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getStageTargetGroupArn();

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
              .loadBalancer(ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getLoadBalancer())
              .prodListenerArn(ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getProdListenerArn())
              .prodListenerRuleArn(
                  ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getProdListenerRuleArn())
              .prodTargetGroupArn(prodTargetGroupArn)
              .stageListenerArn(ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getStageListenerArn())
              .stageListenerRuleArn(
                  ecsBlueGreenPrepareRollbackDataResult.getEcsLoadBalancerConfig().getStageListenerRuleArn())
              .stageTargetGroupArn(stageTargetGroupArn)
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
            .prodTargetGroupArn(prodTargetGroupArn)
            .stageTargetGroupArn(stageTargetGroupArn)
            .build();

    return ecsStepExecutor.executeEcsTask(ambiance, stepElementParameters, ecsExecutionPassThroughData,
        ecsBlueGreenPrepareRollbackDataResponse.getUnitProgressData(), ecsStepExecutorParams);
  }

  public TaskChainResponse queueEcsTask(StepElementParameters stepElementParameters,
      EcsCommandRequest ecsCommandRequest, Ambiance ambiance, PassThroughData passThroughData, boolean isChainEnd,
      TaskType taskType) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {ecsCommandRequest})
                            .taskType(taskType.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();

    String taskName = taskType.getDisplayName() + " : " + ecsCommandRequest.getCommandName();

    EcsSpecParameters ecsSpecParameters = (EcsSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, ecsSpecParameters.getCommandUnits(), taskName,
        TaskSelectorYaml.toTaskSelector(emptyIfNull(getParameterFieldValue(ecsSpecParameters.getDelegateSelectors()))),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(isChainEnd)
        .passThroughData(passThroughData)
        .build();
  }

  public TaskChainResponse queueEcsRunTaskArnTask(StepElementParameters stepElementParameters,
      EcsCommandRequest ecsCommandRequest, Ambiance ambiance, PassThroughData passThroughData, boolean isChainEnd) {
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {ecsCommandRequest})
                            .taskType(TaskType.ECS_RUN_TASK_ARN.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                            .async(true)
                            .build();

    String taskName = TaskType.ECS_RUN_TASK_ARN.getDisplayName() + " : " + ecsCommandRequest.getCommandName();

    EcsSpecParameters ecsSpecParameters = (EcsSpecParameters) stepElementParameters.getSpec();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, ecsSpecParameters.getCommandUnits(), taskName,
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

  public StepResponse handleS3TaskFailure(EcsS3FetchFailurePassThroughData ecsS3FetchFailurePassThroughData) {
    UnitProgressData unitProgressData = ecsS3FetchFailurePassThroughData.getUnitProgressData();
    return StepResponse.builder()
        .unitProgressList(unitProgressData.getUnitProgresses())
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(ecsS3FetchFailurePassThroughData.getErrorMsg()).build())
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
    } else if (ecsCommandResponse instanceof EcsRunTaskResponse) {
      EcsRunTaskResult ecsRunTaskResult = ((EcsRunTaskResponse) ecsCommandResponse).getEcsRunTaskResult();
      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
          ecsRunTaskResult.getEcsTasks(), infrastructureKey, ecsRunTaskResult.getRegion());
    }
    throw new GeneralException("Invalid ecs command response instance");
  }
}
