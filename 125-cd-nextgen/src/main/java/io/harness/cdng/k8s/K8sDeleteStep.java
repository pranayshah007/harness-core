/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.DeleteReleaseNameSpec.DeleteReleaseNameSpecKeys;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.k8s.DeleteResourcesType;
import io.harness.delegate.task.k8s.K8sDeleteRequest;
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
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class K8sDeleteStep extends TaskChainExecutableWithRollbackAndRbac implements K8sStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_DELETE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String K8S_DELETE_COMMAND_NAME = "Delete";

  @Inject private OutcomeService outcomeService;
  @Inject private K8sStepHelper k8sStepHelper;
  @Inject private CDStepHelper cdStepHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Noop
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    K8sDeleteStepParameters k8sDeleteStepParameters = (K8sDeleteStepParameters) stepElementParameters.getSpec();
    validate(k8sDeleteStepParameters);
    K8sDeleteStepParameters deleteStepParameters = (K8sDeleteStepParameters) stepElementParameters.getSpec();
    if (DeleteResourcesType.ManifestPath == deleteStepParameters.getDeleteResources().getType()) {
      return k8sStepHelper.startChainLink(this, ambiance, stepElementParameters);
    } else {
      InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
          ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
      return executeK8sTask(null, ambiance, stepElementParameters, Collections.emptyList(),
          K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), false, null);
    }
  }

  private void validate(K8sDeleteStepParameters stepParameters) {
    if (stepParameters.getDeleteResources() == null) {
      throw new InvalidRequestException("DeleteResources is mandatory");
    }

    if (stepParameters.getDeleteResources().getType() == null) {
      throw new InvalidRequestException("DeleteResources type is mandatory");
    }

    if (stepParameters.getDeleteResources().getSpec() == null) {
      throw new InvalidRequestException("DeleteResources spec is mandatory");
    }

    if (DeleteResourcesType.ResourceName.equals(stepParameters.getDeleteResources().getSpec().getType())
        && StringUtils.isBlank(stepParameters.getDeleteResources().getSpec().getResourceNamesValue())) {
      throw new InvalidRequestException("DeleteResources spec should contain at least one valid Resource Name");
    }
  }

  @Override
  public TaskChainResponse executeK8sTask(ManifestOutcome k8sManifestOutcome, Ambiance ambiance,
      StepElementParameters stepParameters, List<String> manifestOverrideContents,
      K8sExecutionPassThroughData executionPassThroughData, boolean shouldOpenFetchFilesLogStream,
      UnitProgressData unitProgressData) {
    K8sDeleteStepParameters deleteStepParameters = (K8sDeleteStepParameters) stepParameters.getSpec();
    boolean isResourceName = io.harness.delegate.task.k8s.DeleteResourcesType.ResourceName
        == deleteStepParameters.getDeleteResources().getType();
    boolean isManifestFiles = DeleteResourcesType.ManifestPath == deleteStepParameters.getDeleteResources().getType();

    InfrastructureOutcome infrastructure = executionPassThroughData.getInfrastructure();
    String releaseName = cdStepHelper.getReleaseName(ambiance, infrastructure);
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    K8sDeleteRequest request =
        K8sDeleteRequest.builder()
            .accountId(accountId)
            .releaseName(releaseName)
            .commandName(K8S_DELETE_COMMAND_NAME)
            .deleteResourcesType(deleteStepParameters.getDeleteResources().getType())
            .resources(
                isResourceName ? deleteStepParameters.getDeleteResources().getSpec().getResourceNamesValue() : "")
            .deleteNamespacesForRelease(CDStepHelper.getParameterFieldBooleanValue(
                deleteStepParameters.getDeleteResources().getSpec().getDeleteNamespaceParameterField(),
                DeleteReleaseNameSpecKeys.deleteNamespace, stepParameters))
            .filePaths(
                isManifestFiles ? deleteStepParameters.getDeleteResources().getSpec().getManifestPathsValue() : "")
            .valuesYamlList(k8sManifestOutcome != null
                    ? k8sStepHelper.renderValues(k8sManifestOutcome, ambiance, manifestOverrideContents)
                    : Collections.emptyList())
            .kustomizePatchesList(k8sStepHelper.renderPatches(k8sManifestOutcome, ambiance, manifestOverrideContents))
            .taskType(K8sTaskType.DELETE)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .k8sInfraDelegateConfig(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
            .manifestDelegateConfig(k8sManifestOutcome != null
                    ? k8sStepHelper.getManifestDelegateConfigWrapper(executionPassThroughData.getZippedManifestId(),
                        k8sManifestOutcome, ambiance, executionPassThroughData.getManifestFiles())
                    : null)
            .shouldOpenFetchFilesLogStream(shouldOpenFetchFilesLogStream)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .useLatestKustomizeVersion(cdStepHelper.isUseLatestKustomizeVersion(accountId))
            .useNewKubectlVersion(cdStepHelper.isUseNewKubectlVersion(accountId))
            .useDeclarativeRollback(k8sStepHelper.isDeclarativeRollbackEnabled(ambiance))
            .build();

    k8sStepHelper.publishReleaseNameStepDetails(ambiance, releaseName);
    return k8sStepHelper.queueK8sTask(stepParameters, request, ambiance, executionPassThroughData);
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    return k8sStepHelper.executeNextLink(this, ambiance, stepParameters, passThroughData, responseSupplier);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof CustomFetchResponsePassThroughData) {
      return k8sStepHelper.handleCustomTaskFailure((CustomFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof GitFetchResponsePassThroughData) {
      return cdStepHelper.handleGitTaskFailure((GitFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof HelmValuesFetchResponsePassThroughData) {
      return k8sStepHelper.handleHelmValuesFetchFailure((HelmValuesFetchResponsePassThroughData) passThroughData);
    }

    if (passThroughData instanceof StepExceptionPassThroughData) {
      return cdStepHelper.handleStepExceptionFailure((StepExceptionPassThroughData) passThroughData);
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

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
