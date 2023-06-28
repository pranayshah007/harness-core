/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.outcome.TasSetupDataOutcome;
import io.harness.cdng.tas.outcome.TasSetupVariablesOutcome;
import io.harness.cdng.tas.outcome.TasSetupVariablesOutcome.TasSetupVariablesOutcomeBuilder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.pcf.TasResizeStrategyType;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfBlueGreenSetupRequestNG;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.delegate.task.pcf.response.CfBlueGreenSetupResponseNG;
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
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasBGAppSetupStep extends TaskChainExecutableWithRollbackAndRbac implements TasStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TAS_BG_APP_SETUP.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private TasStepHelper tasStepHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
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
      if (passThroughData instanceof StepExceptionPassThroughData) {
        StepExceptionPassThroughData stepExceptionPassThroughData = (StepExceptionPassThroughData) passThroughData;
        return StepResponse.builder()
            .status(Status.FAILED)
            .unitProgressList(stepExceptionPassThroughData.getUnitProgressData().getUnitProgresses())
            .failureInfo(
                FailureInfo.newBuilder().setErrorMessage(stepExceptionPassThroughData.getErrorMessage()).build())
            .build();
      }
      CfBlueGreenSetupResponseNG response;
      try {
        response = (CfBlueGreenSetupResponseNG) responseDataSupplier.get();
      } catch (Exception ex) {
        log.error("Error while processing Tas response: {}", ExceptionUtils.getMessage(ex), ex);
        throw ex;
      }
      if (!response.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
        return StepResponse.builder()
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder().setErrorMessage(response.getErrorMessage()).build())
            .unitProgressList(response.getUnitProgressData().getUnitProgresses())
            .build();
      }
      TasExecutionPassThroughData tasExecutionPassThroughData = (TasExecutionPassThroughData) passThroughData;
      TasBGAppSetupStepParameters tasBGAppSetupStepParameters = (TasBGAppSetupStepParameters) stepParameters.getSpec();
      Integer desiredCount = 0;
      if (tasBGAppSetupStepParameters.getTasInstanceCountType().equals(TasInstanceCountType.MATCH_RUNNING_INSTANCES)) {
        if (isNull(response.getActiveApplicationInfo())) {
          desiredCount = 0;
        } else {
          desiredCount = response.getActiveApplicationInfo().getRunningCount();
        }
      } else {
        desiredCount = tasStepHelper.fetchMaxCountFromManifest(tasExecutionPassThroughData.getTasManifestsPackage());
      }

      List<String> routeMaps = applyVarsYmlSubstitutionIfApplicable(
          tasStepHelper.getRouteMaps(tasExecutionPassThroughData.getTasManifestsPackage().getManifestYml(),
              getParameterFieldValue(tasBGAppSetupStepParameters.getAdditionalRoutes())),
          tasExecutionPassThroughData.getTasManifestsPackage());
      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME,
          TasSetupDataOutcome.builder()
              .routeMaps(routeMaps)
              .tempRouteMap(response.getNewApplicationInfo().getAttachedRoutes())
              .cfCliVersion(tasStepHelper.cfCliVersionNGMapper(tasExecutionPassThroughData.getCfCliVersion()))
              .timeoutIntervalInMinutes(CDStepHelper.getTimeoutInMin(stepParameters))
              .resizeStrategy(TasResizeStrategyType.UPSCALE_NEW_FIRST)
              .maxCount(desiredCount)
              .useAppAutoScalar(
                  !isNull(tasExecutionPassThroughData.getTasManifestsPackage().getAutoscalarManifestYml()))
              .desiredActualFinalCount(desiredCount)
              .manifestsPackage(tasExecutionPassThroughData.getTasManifestsPackage())
              .newReleaseName(response.getNewApplicationInfo().getApplicationName())
              .newApplicationDetails(response.getNewApplicationInfo())
              .activeApplicationDetails(response.getActiveApplicationInfo())
              .inActiveApplicationDetails(response.getInActiveApplicationInfo())
              .cfAppNamePrefix(tasExecutionPassThroughData.getApplicationName())
              .isBlueGreen(true)
              .instanceCountType(tasBGAppSetupStepParameters.getTasInstanceCountType())
              .build(),
          StepCategory.STEP.name());

      TasSetupVariablesOutcomeBuilder tasSetupVariablesOutcome =
          TasSetupVariablesOutcome.builder()
              .inActiveAppName(response.getNewApplicationInfo().getApplicationName())
              .newAppGuid(response.getNewApplicationInfo().getApplicationGuid())
              .finalRoutes(routeMaps)
              .tempRoutes(response.getNewApplicationInfo().getAttachedRoutes());
      if (!isNull(response.getActiveApplicationInfo())) {
        tasSetupVariablesOutcome.activeAppName(response.getActiveApplicationInfo().getApplicationName())
            .activeAppName(response.getActiveApplicationInfo().getApplicationName())
            .oldAppGuid(response.getActiveApplicationInfo().getApplicationGuid())
            .oldAppRoutes(response.getActiveApplicationInfo().getAttachedRoutes());
      }
      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .unitProgressList(response.getUnitProgressData().getUnitProgresses())
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .outcome(tasSetupVariablesOutcome.build())
                           .name(OutcomeExpressionConstants.TAS_INBUILT_VARIABLES_OUTCOME)
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
    TasBGAppSetupStepParameters tasBGAppSetupStepParameters = (TasBGAppSetupStepParameters) stepParameters.getSpec();
    ArtifactOutcome artifactOutcome = cdStepHelper.resolveArtifactsOutcome(ambiance).orElseThrow(
        () -> new InvalidArgumentsException(Pair.of("artifacts", "Primary artifact is required for TAS")));
    InfrastructureOutcome infrastructureOutcome = cdStepHelper.getInfrastructureOutcome(ambiance);
    Integer maxCount = null;
    if (tasBGAppSetupStepParameters.getTasInstanceCountType().equals(TasInstanceCountType.FROM_MANIFEST)) {
      maxCount = tasStepHelper.fetchMaxCountFromManifest(executionPassThroughData.getTasManifestsPackage());
    }
    Integer olderActiveVersionCountToKeep =
        new BigDecimal(getParameterFieldValue(tasBGAppSetupStepParameters.getExistingVersionToKeep())).intValueExact();
    TaskParameters taskParameters =
        CfBlueGreenSetupRequestNG.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .cfCommandTypeNG(CfCommandTypeNG.TAS_BG_SETUP)
            .commandName(CfCommandUnitConstants.PcfSetup)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .releaseNamePrefix(executionPassThroughData.getApplicationName())
            .tasInfraConfig(cdStepHelper.getTasInfraConfig(infrastructureOutcome, ambiance))
            .useCfCLI(true)
            .tasArtifactConfig(tasStepHelper.getPrimaryArtifactConfig(ambiance, artifactOutcome))
            .cfCliVersion(tasStepHelper.cfCliVersionNGMapper(executionPassThroughData.getCfCliVersion()))
            .tasManifestsPackage(executionPassThroughData.getTasManifestsPackage())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .olderActiveVersionCountToKeep(olderActiveVersionCountToKeep)
            .maxCount(maxCount)
            .routeMaps(getParameterFieldValue(tasBGAppSetupStepParameters.getTempRoutes()))
            .useAppAutoScalar(!isNull(executionPassThroughData.getTasManifestsPackage().getAutoscalarManifestYml()))
            .tempRoutes(getParameterFieldValue(tasBGAppSetupStepParameters.getTempRoutes()))
            .build();

    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {taskParameters})
                            .taskType(TaskType.TAS_BG_SETUP.name())
                            .timeout(CDStepHelper.getTimeoutInMillis(stepParameters))
                            .async(true)
                            .build();

    final TaskRequest taskRequest =
        TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
            executionPassThroughData.getCommandUnits(), TaskType.TAS_BG_SETUP.getDisplayName(),
            TaskSelectorYaml.toTaskSelector(tasBGAppSetupStepParameters.getDelegateSelectors()),
            stepHelper.getEnvironmentType(ambiance));
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .chainEnd(true)
        .passThroughData(executionPassThroughData)
        .build();
  }

  public List<String> applyVarsYmlSubstitutionIfApplicable(
      List<String> routeMaps, TasManifestsPackage tasManifestsPackage) {
    if (isEmpty(tasManifestsPackage.getVariableYmls())) {
      return routeMaps;
    }
    return routeMaps.stream()
        .filter(EmptyPredicate::isNotEmpty)
        .map(route -> tasStepHelper.finalizeSubstitution(tasManifestsPackage, route))
        .collect(toList());
  }
}
