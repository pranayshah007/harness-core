/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.K8sApplyBaseStepInfo.K8sApplyBaseStepInfoKeys;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.k8s.K8sApplyRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class K8sApplyStep extends TaskChainExecutableWithRollbackAndRbac implements K8sStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_APPLY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  private final String K8S_APPLY_COMMAND_NAME = "K8s Apply";

  @Inject private K8sStepHelper k8sStepHelper;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Noop
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    K8sApplyStepParameters k8sApplyStepParameters = (K8sApplyStepParameters) stepElementParameters.getSpec();
    validateFilePaths(k8sApplyStepParameters);
    validateManifestType(ambiance);
    return k8sStepHelper.startChainLink(this, ambiance, stepElementParameters);
  }

  private void validateManifestType(Ambiance ambiance) {
    ManifestsOutcome manifestsOutcomes = k8sStepHelper.resolveManifestsOutcome(ambiance);
    ManifestOutcome manifestOutcome = k8sStepHelper.getK8sSupportedManifestOutcome(manifestsOutcomes.values());
    if (ManifestType.OpenshiftTemplate.equals(manifestOutcome.getType())) {
      throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestOutcome.getType()));
    }
  }

  private void validateFilePaths(K8sApplyStepParameters k8sApplyStepParameters) {
    if (ParameterField.isNull(k8sApplyStepParameters.getFilePaths())) {
      throw new InvalidRequestException("File/Folder path must be present");
    }

    if (isEmpty(getParameterFieldValue(k8sApplyStepParameters.getFilePaths()))) {
      throw new InvalidRequestException("File/Folder path must be present");
    }

    List<String> filePaths = getParameterFieldValue(k8sApplyStepParameters.getFilePaths());
    for (String filePath : filePaths) {
      if (isEmpty(filePath)) {
        throw new InvalidRequestException("File/Folder path must be present");
      }
    }
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    return k8sStepHelper.executeNextLink(this, ambiance, stepElementParameters, passThroughData, responseSupplier);
  }

  @Override
  public TaskChainResponse executeK8sTask(ManifestOutcome k8sManifestOutcome, Ambiance ambiance,
      StepElementParameters stepElementParameters, List<String> manifestOverrideContents,
      K8sExecutionPassThroughData executionPassThroughData, boolean shouldOpenFetchFilesLogStream,
      UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructure = executionPassThroughData.getInfrastructure();
    String releaseName = k8sStepHelper.getReleaseName(ambiance, infrastructure);
    K8sApplyStepParameters k8sApplyStepParameters = (K8sApplyStepParameters) stepElementParameters.getSpec();
    boolean skipDryRun = CDStepHelper.getParameterFieldBooleanValue(
        k8sApplyStepParameters.getSkipDryRun(), K8sApplyBaseStepInfoKeys.skipDryRun, stepElementParameters);
    boolean skipSteadyStateCheck =
        CDStepHelper.getParameterFieldBooleanValue(k8sApplyStepParameters.getSkipSteadyStateCheck(),
            K8sApplyBaseStepInfoKeys.skipSteadyStateCheck, stepElementParameters);

    final String accountId = AmbianceUtils.getAccountId(ambiance);
    K8sApplyRequest k8sApplyRequest =
        K8sApplyRequest.builder()
            .skipDryRun(skipDryRun)
            .releaseName(releaseName)
            .commandName(K8S_APPLY_COMMAND_NAME)
            .taskType(K8sTaskType.APPLY)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .valuesYamlList(k8sStepHelper.renderValues(k8sManifestOutcome, ambiance, manifestOverrideContents))
            .k8sInfraDelegateConfig(k8sStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
            .kustomizePatchesList(k8sStepHelper.renderPatches(k8sManifestOutcome, ambiance, manifestOverrideContents))
            .manifestDelegateConfig(k8sStepHelper.getManifestDelegateConfig(k8sManifestOutcome, ambiance))
            .accountId(accountId)
            .deprecateFabric8Enabled(true)
            .filePaths(k8sApplyStepParameters.getFilePaths().getValue())
            .skipSteadyStateCheck(skipSteadyStateCheck)
            .shouldOpenFetchFilesLogStream(shouldOpenFetchFilesLogStream)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .useLatestKustomizeVersion(k8sStepHelper.isUseLatestKustomizeVersion(accountId))
            .useNewKubectlVersion(k8sStepHelper.isUseNewKubectlVersion(accountId))
            .useK8sApiForSteadyStateCheck(k8sStepHelper.shouldUseK8sApiForSteadyStateCheck(accountId))
            .build();

    k8sStepHelper.publishReleaseNameStepDetails(ambiance, releaseName);

    return k8sStepHelper.queueK8sTask(stepElementParameters, k8sApplyRequest, ambiance, executionPassThroughData);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof GitFetchResponsePassThroughData) {
      return k8sStepHelper.handleGitTaskFailure((GitFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof HelmValuesFetchResponsePassThroughData) {
      return k8sStepHelper.handleHelmValuesFetchFailure((HelmValuesFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof StepExceptionPassThroughData) {
      return k8sStepHelper.handleStepExceptionFailure((StepExceptionPassThroughData) passThroughData);
    }

    K8sDeployResponse k8sTaskExecutionResponse;
    try {
      k8sTaskExecutionResponse = (K8sDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing K8s Task response: {}", e.getMessage(), e);
      return k8sStepHelper.handleTaskException(ambiance, (K8sExecutionPassThroughData) passThroughData, e);
    }

    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(k8sTaskExecutionResponse.getCommandUnitsProgress().getUnitProgresses());

    if (k8sTaskExecutionResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return K8sStepHelper.getFailureResponseBuilder(k8sTaskExecutionResponse, stepResponseBuilder).build();
    }
    return stepResponseBuilder.status(Status.SUCCEEDED).build();
  }
}
