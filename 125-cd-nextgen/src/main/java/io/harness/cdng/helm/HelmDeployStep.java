/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.helm.HelmDeployBaseStepInfo.HelmDeployBaseStepInfoKeys;
import io.harness.cdng.helm.NativeHelmDeployOutcome.NativeHelmDeployOutcomeBuilder;
import io.harness.cdng.helm.beans.NativeHelmExecutionPassThroughData;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.NGTimeConversionHelper;
import io.harness.delegate.beans.instancesync.mapper.K8sContainerToHelmServiceInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.HelmNGException;
import io.harness.delegate.task.helm.HelmCmdExecResponseNG;
import io.harness.delegate.task.helm.HelmInstallCmdResponseNG;
import io.harness.delegate.task.helm.HelmInstallCommandRequestNG;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class HelmDeployStep extends TaskChainExecutableWithRollbackAndRbac implements NativeHelmStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.HELM_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  public static final String HELM_COMMAND_NAME = "Helm Deploy";

  @Inject NativeHelmStepHelper nativeHelmStepHelper;
  @Inject CDStepHelper cdStepHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Noop
  }

  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    return nativeHelmStepHelper.startChainLink(this, ambiance, stepElementParameters);
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    return nativeHelmStepHelper.executeNextLink(this, ambiance, stepParameters, passThroughData, responseSupplier);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof CustomFetchResponsePassThroughData) {
      return nativeHelmStepHelper.handleCustomTaskFailure((CustomFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof GitFetchResponsePassThroughData) {
      return cdStepHelper.handleGitTaskFailure((GitFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof HelmValuesFetchResponsePassThroughData) {
      return nativeHelmStepHelper.handleHelmValuesFetchFailure(
          (HelmValuesFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof StepExceptionPassThroughData) {
      return cdStepHelper.handleStepExceptionFailure((StepExceptionPassThroughData) passThroughData);
    }

    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());

    NativeHelmExecutionPassThroughData nativeHelmExecutionPassThroughData =
        (NativeHelmExecutionPassThroughData) passThroughData;

    HelmCmdExecResponseNG helmCmdExecResponseNG;

    NativeHelmDeployOutcomeBuilder nativeHelmDeployOutcomeBuilder = NativeHelmDeployOutcome.builder();
    NativeHelmDeployOutcome nativeHelmDeployOutcome;

    try {
      helmCmdExecResponseNG = (HelmCmdExecResponseNG) responseDataSupplier.get();
    } catch (Exception e) {
      HelmNGException ex = ExceptionUtils.cause(HelmNGException.class, e);
      if (ex == null) { // in case of any other unexpected exception (from fetch files task)
        log.error("Error while processing Helm Task response: {}", ExceptionUtils.getMessage(e), e);
        return nativeHelmStepHelper.handleTaskException(ambiance, nativeHelmExecutionPassThroughData, e);
      }
      nativeHelmDeployOutcomeBuilder.prevReleaseVersion(ex.getPrevReleaseVersion());
      nativeHelmDeployOutcomeBuilder.newReleaseVersion(ex.getPrevReleaseVersion() + 1);
      nativeHelmDeployOutcomeBuilder.hasInstallUpgradeStarted(ex.isInstallUpgrade());
      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.HELM_DEPLOY_OUTCOME,
          nativeHelmDeployOutcomeBuilder.build(), StepOutcomeGroup.STEP.name());

      log.error("Error while processing Helm Task response: {}", ExceptionUtils.getMessage(ex), ex);

      return nativeHelmStepHelper.handleTaskException(ambiance, nativeHelmExecutionPassThroughData, e);
    }

    HelmInstallCmdResponseNG helmInstallCmdResponseNG =
        (HelmInstallCmdResponseNG) helmCmdExecResponseNG.getHelmCommandResponse();
    nativeHelmDeployOutcomeBuilder.prevReleaseVersion(helmInstallCmdResponseNG.getPrevReleaseVersion());
    nativeHelmDeployOutcomeBuilder.newReleaseVersion(helmInstallCmdResponseNG.getPrevReleaseVersion() + 1);
    nativeHelmDeployOutcomeBuilder.hasInstallUpgradeStarted(true);

    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(helmCmdExecResponseNG.getCommandUnitsProgress().getUnitProgresses());

    if (helmCmdExecResponseNG.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.HELM_DEPLOY_OUTCOME,
          nativeHelmDeployOutcomeBuilder.build(), StepOutcomeGroup.STEP.name());
      return nativeHelmStepHelper.getFailureResponseBuilder(helmCmdExecResponseNG, stepResponseBuilder).build();
    }

    nativeHelmDeployOutcomeBuilder.containerInfoList(helmInstallCmdResponseNG.getContainerInfoList());
    nativeHelmDeployOutcomeBuilder.releaseName(helmInstallCmdResponseNG.getReleaseName());
    nativeHelmDeployOutcome = nativeHelmDeployOutcomeBuilder.build();
    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.HELM_DEPLOY_OUTCOME,
        nativeHelmDeployOutcome, StepOutcomeGroup.STEP.name());

    StepOutcome stepOutcome = instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance,
        K8sContainerToHelmServiceInstanceInfoMapper.toServerInstanceInfoList(
            helmInstallCmdResponseNG.getContainerInfoList(), helmInstallCmdResponseNG.getHelmChartInfo(),
            helmInstallCmdResponseNG.getHelmVersion()));

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(stepOutcome)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.HELM_DEPLOY_OUTCOME)
                         .outcome(nativeHelmDeployOutcome)
                         .build())
        .build();
  }

  @Override
  public TaskChainResponse executeHelmTask(ManifestOutcome manifestOutcome, Ambiance ambiance,
      StepElementParameters stepParameters, List<String> valuesFileContents,
      NativeHelmExecutionPassThroughData executionPassThroughData, boolean shouldOpenFetchFilesLogStream,
      UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructure = executionPassThroughData.getInfrastructure();
    String releaseName = cdStepHelper.getReleaseName(ambiance, infrastructure);
    List<String> manifestFilesContents =
        nativeHelmStepHelper.renderValues(manifestOutcome, ambiance, valuesFileContents);
    HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;

    boolean ignoreHelmHistFailure = CDStepHelper.getParameterFieldBooleanValue(
        ((HelmDeployStepParams) stepParameters.getSpec()).getIgnoreReleaseHistFailStatus(),
        HelmDeployBaseStepInfoKeys.ignoreReleaseHistFailStatus, stepParameters);

    HelmInstallCommandRequestNG helmCommandRequest =
        HelmInstallCommandRequestNG.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .commandName(HELM_COMMAND_NAME)
            .valuesYamlList(manifestFilesContents)
            .k8sInfraDelegateConfig(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
            .manifestDelegateConfig(
                nativeHelmStepHelper.getManifestDelegateConfigWrapper(executionPassThroughData.getZippedManifestId(),
                    manifestOutcome, ambiance, executionPassThroughData.getManifestFiles()))
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .releaseName(releaseName)
            .helmVersion(nativeHelmStepHelper.getHelmVersionBasedOnFF(
                helmChartManifestOutcome.getHelmVersion(), AmbianceUtils.getAccountId(ambiance)))
            .namespace(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance).getNamespace())
            .k8SteadyStateCheckEnabled(cdFeatureFlagHelper.isEnabled(
                AmbianceUtils.getAccountId(ambiance), FeatureName.HELM_STEADY_STATE_CHECK_1_16))
            .useLatestKubectlVersion(
                cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NEW_KUBECTL_VERSION))
            .releaseHistoryPrefix(nativeHelmStepHelper.getReleaseHistoryPrefix(ambiance))
            .shouldOpenFetchFilesLogStream(true)
            .ignoreReleaseHistFailStatus(ignoreHelmHistFailure)
            .build();

    if (!ParameterField.isNull(stepParameters.getTimeout())) {
      helmCommandRequest.setTimeoutInMillis(
          NGTimeConversionHelper.convertTimeStringToMilliseconds(stepParameters.getTimeout().getValue()));
    }

    nativeHelmStepHelper.publishReleaseNameStepDetails(ambiance, releaseName);

    return nativeHelmStepHelper.queueNativeHelmTask(
        stepParameters, helmCommandRequest, ambiance, executionPassThroughData);
  }
}
