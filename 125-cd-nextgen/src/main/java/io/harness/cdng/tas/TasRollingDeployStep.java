/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.execution.tas.TasStageExecutionDetails;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.outcome.TasRollingDeployOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfRollingDeployRequestNG;
import io.harness.delegate.task.pcf.response.CfRollingDeployResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pcf.CfCommandUnitConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasRollingDeployStep extends TaskChainExecutableWithRollbackAndRbac implements TasStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TAS_ROLLING_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private OutcomeService outcomeService;
  @Inject private TasStepHelper tasStepHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer kryoSerializer;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private StepHelper stepHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NG_SVC_ENV_REDESIGN)) {
      throw new AccessDeniedException(
          "CDS_TAS_NG FF is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    return tasStepHelper.executeNextLink(this, ambiance, stepParameters, passThroughData, responseSupplier);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    try {
      if (passThroughData instanceof GitFetchResponsePassThroughData) {
        GitFetchResponsePassThroughData stepExceptionPassThroughData =
            (GitFetchResponsePassThroughData) passThroughData;
        return StepResponse.builder()
            .status(Status.FAILED)
            .unitProgressList(stepExceptionPassThroughData.getUnitProgressData().getUnitProgresses())
            .failureInfo(FailureInfo.newBuilder().setErrorMessage(stepExceptionPassThroughData.getErrorMsg()).build())
            .build();
      }
      if (passThroughData instanceof CustomFetchResponsePassThroughData) {
        CustomFetchResponsePassThroughData stepExceptionPassThroughData =
            (CustomFetchResponsePassThroughData) passThroughData;
        return StepResponse.builder()
            .status(Status.FAILED)
            .unitProgressList(stepExceptionPassThroughData.getUnitProgressData().getUnitProgresses())
            .failureInfo(FailureInfo.newBuilder().setErrorMessage(stepExceptionPassThroughData.getErrorMsg()).build())
            .build();
      }
      if (passThroughData instanceof StepExceptionPassThroughData) {
        StepExceptionPassThroughData stepExceptionPassThroughData = (StepExceptionPassThroughData) passThroughData;
        return StepResponse.builder()
            .status(Status.FAILED)
            .unitProgressList(tasStepHelper
                                  .completeUnitProgressData(stepExceptionPassThroughData.getUnitProgressData(),
                                      ambiance, stepExceptionPassThroughData.getErrorMessage())
                                  .getUnitProgresses())
            .failureInfo(
                FailureInfo.newBuilder().setErrorMessage(stepExceptionPassThroughData.getErrorMessage()).build())
            .build();
      }
      CfRollingDeployResponseNG response;
      TasExecutionPassThroughData tasExecutionPassThroughData = (TasExecutionPassThroughData) passThroughData;
      try {
        response = (CfRollingDeployResponseNG) responseDataSupplier.get();
      } catch (Exception ex) {
        log.error("Error while processing Tas response: {}", ExceptionUtils.getMessage(ex), ex);
        throw ex;
      }
      if (!response.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
        TasRollingDeployOutcome tasRollingDeployOutcome =
            TasRollingDeployOutcome.builder()
                .appName(tasExecutionPassThroughData.getApplicationName())
                .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
                .isFirstDeployment(response.getCurrentProdInfo() == null)
                .cfCliVersion(tasExecutionPassThroughData.getCfCliVersion())
                .deploymentStarted(response.isDeploymentStarted())
                .build();
        return StepResponse.builder()
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder().setErrorMessage(response.getErrorMessage()).build())
            .unitProgressList(
                tasStepHelper
                    .completeUnitProgressData(response.getUnitProgressData(), ambiance, response.getErrorMessage())
                    .getUnitProgresses())
            .build();
      }
      InfrastructureOutcome infrastructureOutcome = cdStepHelper.getInfrastructureOutcome(ambiance);
      TasInfraConfig tasInfraConfig = cdStepHelper.getTasInfraConfig(infrastructureOutcome, ambiance);
      String appName = response.getNewApplicationInfo().getApplicationName();
      TasRollingDeployOutcome tasRollingDeployOutcome =
          TasRollingDeployOutcome.builder()
              .appName(response.getNewApplicationInfo().getApplicationName())
              .appGuid(response.getNewApplicationInfo().getApplicationGuid())
              .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
              .isFirstDeployment(response.getCurrentProdInfo() == null)
              .cfCliVersion(tasExecutionPassThroughData.getCfCliVersion())
              .deploymentStarted(response.isDeploymentStarted())
              .routeMaps(response.getNewApplicationInfo().getAttachedRoutes())
              .build();
      List<ServerInstanceInfo> serverInstanceInfoList = getServerInstanceInfoList(response, ambiance);
      StepResponse.StepOutcome stepOutcome =
          instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);
      tasStepHelper.updateManifestFiles(
          ambiance, tasExecutionPassThroughData.getUnresolvedTasManifestsPackage(), appName, tasInfraConfig);
      tasStepHelper.updateAutoscalarEnabledField(ambiance,
          !isNull(tasExecutionPassThroughData.getTasManifestsPackage().getAutoscalarManifestYml()), appName,
          tasInfraConfig);
      tasStepHelper.updateRouteMapsField(
          ambiance, response.getNewApplicationInfo().getAttachedRoutes(), appName, tasInfraConfig);
      tasStepHelper.updateDesiredCountField(
          ambiance, tasExecutionPassThroughData.getDesiredCountInFinalYaml(), appName, tasInfraConfig);
      tasStepHelper.updateIsFirstDeploymentField(
          ambiance, response.getCurrentProdInfo() == null, appName, tasInfraConfig);

      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .unitProgressList(response.getUnitProgressData().getUnitProgresses())
          .stepOutcome(stepOutcome)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .outcome(tasRollingDeployOutcome)
                           .name(OutcomeExpressionConstants.TAS_ROLLING_DEPLOY_OUTCOME)
                           .group(StepCategory.STAGE.name())
                           .build())
          .build();
    } finally {
      tasStepHelper.closeLogStream(ambiance);
    }
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return tasStepHelper.startChainLink(this, ambiance, stepParameters);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public TaskChainResponse executeTasTask(ManifestOutcome tasManifestOutcome, Ambiance ambiance,
      StepElementParameters stepParameters, TasExecutionPassThroughData executionPassThroughData,
      boolean shouldOpenFetchFilesLogStream, UnitProgressData unitProgressData) {
    TasRollingDeployStepParameters tasRollingDeployStepParameters =
        (TasRollingDeployStepParameters) stepParameters.getSpec();
    ArtifactOutcome artifactOutcome = cdStepHelper.resolveArtifactsOutcome(ambiance).orElseThrow(
        () -> new InvalidArgumentsException(Pair.of("artifacts", "Primary artifact is required for TAS")));
    InfrastructureOutcome infrastructureOutcome = cdStepHelper.getInfrastructureOutcome(ambiance);
    List<String> routeMaps = tasStepHelper.getRouteMaps(executionPassThroughData.getTasManifestsPackage(),
        getParameterFieldValue(tasRollingDeployStepParameters.getAdditionalRoutes()));
    TasInfraConfig tasInfraConfig = cdStepHelper.getTasInfraConfig(infrastructureOutcome, ambiance);
    TaskParameters taskParameters =
        CfRollingDeployRequestNG.builder()
            .applicationName(executionPassThroughData.getApplicationName())
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .cfCommandTypeNG(CfCommandTypeNG.TAS_ROLLING_DEPLOY)
            .commandName(CfCommandUnitConstants.Deploy)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .tasInfraConfig(tasInfraConfig)
            .useCfCLI(true)
            .routeMaps(routeMaps)
            .tasArtifactConfig(tasStepHelper.getPrimaryArtifactConfig(ambiance, artifactOutcome))
            .cfCliVersion(tasStepHelper.cfCliVersionNGMapper(executionPassThroughData.getCfCliVersion()))
            .tasManifestsPackage(executionPassThroughData.getTasManifestsPackage())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .useAppAutoScalar(!isNull(executionPassThroughData.getTasManifestsPackage().getAutoscalarManifestYml()))
            .desiredCount(executionPassThroughData.getDesiredCountInFinalYaml())
            .build();

    TasStageExecutionDetails tasStageExecutionDetails = tasStepHelper.findLastSuccessfulStageExecutionDetails(
        ambiance, tasInfraConfig, executionPassThroughData.getApplicationName());

    TasRollingDeployOutcome tasRollingDeployOutcome =
        TasRollingDeployOutcome.builder()
            .appName(executionPassThroughData.getApplicationName())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .cfCliVersion(executionPassThroughData.getCfCliVersion())
            .tasStageExecutionDetails(tasStageExecutionDetails)
            .build();
    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.TAS_ROLLING_DEPLOY_OUTCOME,
        tasRollingDeployOutcome, StepCategory.STEP.name());

    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {taskParameters})
                            .taskType(TaskType.TAS_ROLLING_DEPLOY.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                            .async(true)
                            .build();
    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        executionPassThroughData.getCommandUnits(), TaskType.TAS_ROLLING_DEPLOY.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(tasRollingDeployStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(true)
        .passThroughData(executionPassThroughData)
        .build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(CfRollingDeployResponseNG response, Ambiance ambiance) {
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        (TanzuApplicationServiceInfrastructureOutcome) outcomeService.resolve(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    if (response == null) {
      log.error("Could not generate server instance info for rolling deploy step");
      return Collections.emptyList();
    }
    List<CfInternalInstanceElement> instances = response.getNewAppInstances();
    if (!isNull(instances)) {
      return instances.stream()
          .map(instance -> getServerInstance(instance, infrastructureOutcome))
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  private ServerInstanceInfo getServerInstance(
      CfInternalInstanceElement instance, TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome) {
    return TasServerInstanceInfo.builder()
        .id(instance.getApplicationId() + ":" + instance.getInstanceIndex())
        .instanceIndex(instance.getInstanceIndex())
        .tasApplicationName(instance.getDisplayName())
        .tasApplicationGuid(instance.getApplicationId())
        .organization(infrastructureOutcome.getOrganization())
        .space(infrastructureOutcome.getSpace())
        .build();
  }
}
