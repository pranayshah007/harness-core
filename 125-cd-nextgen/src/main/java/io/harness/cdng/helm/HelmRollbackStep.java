/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.helm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.helm.NativeHelmRollbackOutcome.NativeHelmRollbackOutcomeBuilder;
import io.harness.cdng.helm.beans.NativeHelmExecutionPassThroughData;
import io.harness.cdng.helm.rollback.HelmRollbackBaseStepInfo.HelmRollbackBaseStepInfoKeys;
import io.harness.cdng.helm.rollback.HelmRollbackStepParams;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.container.ContainerInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.instancesync.mapper.K8sContainerToHelmServiceInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.helm.HelmCmdExecResponseNG;
import io.harness.delegate.task.helm.HelmInstallCmdResponseNG;
import io.harness.delegate.task.helm.HelmRollbackCommandRequestNG;
import io.harness.delegate.task.helm.HelmRollbackCommandRequestNG.HelmRollbackCommandRequestNGBuilder;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class HelmRollbackStep extends CdTaskExecutable<HelmCmdExecResponseNG> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.HELM_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String HELM_COMMAND_NAME = "Helm Rollback";

  @Inject NativeHelmStepHelper nativeHelmStepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private OutcomeService outcomeService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private StepHelper stepHelper;
  @Inject CDFeatureFlagHelper cdFeatureFlagHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // No validation
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<HelmCmdExecResponseNG> responseDataSupplier) throws Exception {
    StepResponse stepResponse = null;
    try {
      HelmCmdExecResponseNG executionResponse = responseDataSupplier.get();
      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(executionResponse.getCommandUnitsProgress().getUnitProgresses());
      stepResponse = generateStepResponse(ambiance, executionResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing Helm Task response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } finally {
      stepHelper.sendRollbackTelemetryEvent(ambiance, stepResponse == null ? Status.FAILED : stepResponse.getStatus());
    }
    return stepResponse;
  }

  private StepResponse generateStepResponse(
      Ambiance ambiance, HelmCmdExecResponseNG executionResponse, StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse = null;
    if (executionResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse = stepResponseBuilder.status(Status.FAILED)
                         .failureInfo(FailureInfo.newBuilder()
                                          .setErrorMessage(NativeHelmStepHelper.getErrorMessage(executionResponse))
                                          .build())
                         .build();
    } else {
      final HelmInstallCmdResponseNG response = (HelmInstallCmdResponseNG) executionResponse.getHelmCommandResponse();
      List<ContainerInfo> containerInfoList = response.getContainerInfoList();

      NativeHelmRollbackOutcomeBuilder nativeHelmRollbackOutcomeBuilder = NativeHelmRollbackOutcome.builder();
      int rollbackVersion = response.getPrevReleaseVersion();
      nativeHelmRollbackOutcomeBuilder.rollbackVersion(rollbackVersion);
      nativeHelmRollbackOutcomeBuilder.releaseName(response.getReleaseName());
      nativeHelmRollbackOutcomeBuilder.newReleaseVersion(2 + rollbackVersion);
      nativeHelmRollbackOutcomeBuilder.containerInfoList(containerInfoList);
      NativeHelmRollbackOutcome nativeHelmRollbackOutcome = nativeHelmRollbackOutcomeBuilder.build();

      StepOutcome stepOutcome = instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance,
          K8sContainerToHelmServiceInstanceInfoMapper.toServerInstanceInfoList(
              response.getContainerInfoList(), response.getHelmChartInfo(), response.getHelmVersion()));

      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.HELM_ROLLBACK_OUTCOME,
          nativeHelmRollbackOutcome, StepOutcomeGroup.STEP.name());
      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED)
                         .stepOutcome(StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(nativeHelmRollbackOutcome)
                                          .build())
                         .stepOutcome(stepOutcome)
                         .build();
    }

    return stepResponse;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    HelmRollbackStepParams helmRollbackStepParams = (HelmRollbackStepParams) stepParameters.getSpec();
    if (EmptyPredicate.isEmpty(helmRollbackStepParams.getHelmRollbackFqn())) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("Helm Deploy step was not executed. Skipping rollback.").build())
          .build();
    }

    OptionalSweepingOutput helmDeployOptionalOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            helmRollbackStepParams.getHelmRollbackFqn() + "." + OutcomeExpressionConstants.HELM_DEPLOY_OUTCOME));
    if (!helmDeployOptionalOutput.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("Helm Deploy step was not executed. Skipping rollback.").build())
          .build();
    }

    NativeHelmDeployOutcome nativeHelmDeployOutcome = (NativeHelmDeployOutcome) helmDeployOptionalOutput.getOutput();
    if (!nativeHelmDeployOutcome.isHasInstallUpgradeStarted()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("Helm install/upgrade was not executed. Skipping rollback.")
                                  .build())
          .build();
    }
    ManifestsOutcome manifestsOutcome = nativeHelmStepHelper.resolveManifestsOutcome(ambiance);
    ManifestOutcome manifestOutcome = nativeHelmStepHelper.getHelmSupportedManifestOutcome(manifestsOutcome.values());
    HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;

    HelmRollbackCommandRequestNGBuilder rollbackCommandRequestNGBuilder = HelmRollbackCommandRequestNG.builder();
    InfrastructureOutcome infrastructure = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    String releaseName = cdStepHelper.getReleaseName(ambiance, infrastructure);
    int rollbackVersion = nativeHelmDeployOutcome.getPrevReleaseVersion();
    boolean skipSteadyStateCheck =
        CDStepHelper.getParameterFieldBooleanValue(helmRollbackStepParams.getSkipSteadyStateCheck(),
            HelmRollbackBaseStepInfoKeys.skipSteadyStateCheck, stepParameters);

    rollbackCommandRequestNGBuilder.accountId(AmbianceUtils.getAccountId(ambiance))
        .commandName(HELM_COMMAND_NAME)
        .prevReleaseVersion(rollbackVersion)
        .k8sInfraDelegateConfig(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
        .manifestDelegateConfig(nativeHelmStepHelper.getManifestDelegateConfig(manifestOutcome, ambiance))
        .commandUnitsProgress(CommandUnitsProgress.builder().build())
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
        .useRefactorSteadyStateCheck(cdFeatureFlagHelper.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_HELM_STEADY_STATE_CHECK_1_16_V2_NG))
        .skipSteadyStateCheck(skipSteadyStateCheck);

    return nativeHelmStepHelper
        .queueNativeHelmTask(stepParameters, rollbackCommandRequestNGBuilder.build(), ambiance,
            NativeHelmExecutionPassThroughData.builder().infrastructure(infrastructure).build())
        .getTaskRequest();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
