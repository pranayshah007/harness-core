/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.k8s.K8sCanaryDeployRequest.K8sCanaryDeployRequestBuilder;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskChainExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.k8s.K8sCanaryBaseStepInfo.K8sCanaryBaseStepInfoKeys;
import io.harness.cdng.k8s.beans.CustomFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sCanaryExecutionOutput;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.mapper.K8sPodToServiceInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.k8s.K8sCanaryDeployRequest;
import io.harness.delegate.task.k8s.K8sCanaryDeployResponse;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.delegate.task.k8s.data.K8sCanaryDataException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
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
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@OwnedBy(CDP)
public class K8sCanaryStep extends CdTaskChainExecutable implements K8sStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_CANARY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  private final String K8S_CANARY_DEPLOY_COMMAND_NAME = "Canary Deploy";

  @Inject private K8sStepHelper k8sStepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Noop
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    K8sCanaryStepParameters k8sCanaryStepParameters = (K8sCanaryStepParameters) stepElementParameters.getSpec();
    validate(k8sCanaryStepParameters);
    return k8sStepHelper.startChainLink(this, ambiance, stepElementParameters);
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepElementParameters, StepInputPackage inputPackage, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    return k8sStepHelper.executeNextLink(this, ambiance, stepElementParameters, passThroughData, responseSupplier);
  }

  @Override
  public TaskChainResponse executeK8sTask(ManifestOutcome k8sManifestOutcome, Ambiance ambiance,
      StepElementParameters stepElementParameters, List<String> manifestOverrideContents,
      K8sExecutionPassThroughData executionPassThroughData, boolean shouldOpenFetchFilesLogStream,
      UnitProgressData unitProgressData) {
    final InfrastructureOutcome infrastructure = executionPassThroughData.getInfrastructure();
    final String releaseName = cdStepHelper.getReleaseName(ambiance, infrastructure);
    final K8sCanaryStepParameters canaryStepParameters = (K8sCanaryStepParameters) stepElementParameters.getSpec();
    final Integer instancesValue = canaryStepParameters.getInstanceSelection().getSpec().getInstances();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    final boolean skipDryRun = CDStepHelper.getParameterFieldBooleanValue(
        canaryStepParameters.getSkipDryRun(), K8sCanaryBaseStepInfoKeys.skipDryRun, stepElementParameters);
    List<String> manifestFilesContents =
        k8sStepHelper.renderValues(k8sManifestOutcome, ambiance, manifestOverrideContents);
    boolean isOpenshiftTemplate = ManifestType.OpenshiftTemplate.equals(k8sManifestOutcome.getType());

    K8sCanaryDeployRequestBuilder canaryRequestBuilder =
        K8sCanaryDeployRequest.builder()
            .skipDryRun(skipDryRun)
            .releaseName(releaseName)
            .commandName(K8S_CANARY_DEPLOY_COMMAND_NAME)
            .taskType(K8sTaskType.CANARY_DEPLOY)
            .instanceUnitType(canaryStepParameters.getInstanceSelection().getType().getInstanceUnitType())
            .instances(instancesValue)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .valuesYamlList(!isOpenshiftTemplate ? manifestFilesContents : Collections.emptyList())
            .openshiftParamList(isOpenshiftTemplate ? manifestFilesContents : Collections.emptyList())
            .kustomizePatchesList(k8sStepHelper.renderPatches(k8sManifestOutcome, ambiance, manifestOverrideContents))
            .k8sInfraDelegateConfig(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
            .manifestDelegateConfig(
                k8sStepHelper.getManifestDelegateConfigWrapper(executionPassThroughData.getZippedManifestId(),
                    k8sManifestOutcome, ambiance, executionPassThroughData.getManifestFiles()))
            .accountId(accountId)
            .skipResourceVersioning(k8sStepHelper.getSkipResourceVersioning(k8sManifestOutcome))
            .shouldOpenFetchFilesLogStream(shouldOpenFetchFilesLogStream)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .useLatestKustomizeVersion(cdStepHelper.isUseLatestKustomizeVersion(accountId))
            .useNewKubectlVersion(cdStepHelper.isUseNewKubectlVersion(accountId))
            .cleanUpIncompleteCanaryDeployRelease(true)
            .useK8sApiForSteadyStateCheck(cdStepHelper.shouldUseK8sApiForSteadyStateCheck(accountId))
            .useDeclarativeRollback(k8sStepHelper.isDeclarativeRollbackEnabled(k8sManifestOutcome))
            .enabledSupportHPAAndPDB(cdStepHelper.isEnabledSupportHPAAndPDB(accountId));

    if (cdFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_K8S_SERVICE_HOOKS_NG)) {
      canaryRequestBuilder.serviceHooks(k8sStepHelper.getServiceHooks(ambiance));
    }
    Map<String, String> k8sCommandFlag =
        k8sStepHelper.getDelegateK8sCommandFlag(canaryStepParameters.getCommandFlags());
    canaryRequestBuilder.k8sCommandFlags(k8sCommandFlag);
    K8sCanaryDeployRequest k8sCanaryDeployRequest = canaryRequestBuilder.build();
    k8sStepHelper.publishReleaseNameStepDetails(ambiance, releaseName);
    TaskChainResponse response =
        k8sStepHelper.queueK8sTask(stepElementParameters, k8sCanaryDeployRequest, ambiance, executionPassThroughData);

    executionSweepingOutputService.consume(ambiance, K8sCanaryExecutionOutput.OUTPUT_NAME,
        K8sCanaryExecutionOutput.builder().build(), StepOutcomeGroup.STEP.name());

    return response;
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
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

    K8sExecutionPassThroughData executionPassThroughData = (K8sExecutionPassThroughData) passThroughData;
    InfrastructureOutcome infrastructure = executionPassThroughData.getInfrastructure();

    K8sDeployResponse k8sTaskExecutionResponse;
    try {
      k8sTaskExecutionResponse = (K8sDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing K8s Task response: {}", e.getMessage(), e);
      K8sCanaryDataException k8sCanaryDataException = ExceptionUtils.cause(K8sCanaryDataException.class, e);
      if (k8sCanaryDataException != null) {
        K8sCanaryOutcome k8sCanaryOutcome =
            K8sCanaryOutcome.builder()
                .releaseName(cdStepHelper.getReleaseName(ambiance, infrastructure))
                .canaryWorkload(k8sCanaryDataException.getCanaryWorkload())
                .canaryWorkloadDeployed(k8sCanaryDataException.isCanaryWorkloadDeployed())
                .build();

        executionSweepingOutputService.consume(
            ambiance, OutcomeExpressionConstants.K8S_CANARY_OUTCOME, k8sCanaryOutcome, StepOutcomeGroup.STEP.name());
      }

      return k8sStepHelper.handleTaskException(ambiance, executionPassThroughData, e);
    }

    StepResponseBuilder responseBuilder =
        StepResponse.builder().unitProgressList(k8sTaskExecutionResponse.getCommandUnitsProgress().getUnitProgresses());
    K8sCanaryDeployResponse k8sCanaryDeployResponse =
        (K8sCanaryDeployResponse) k8sTaskExecutionResponse.getK8sNGTaskResponse();

    K8sCanaryOutcome k8sCanaryOutcome = K8sCanaryOutcome.builder()
                                            .releaseName(cdStepHelper.getReleaseName(ambiance, infrastructure))
                                            .releaseNumber(k8sCanaryDeployResponse.getReleaseNumber())
                                            .targetInstances(k8sCanaryDeployResponse.getCurrentInstances())
                                            .canaryWorkload(k8sCanaryDeployResponse.getCanaryWorkload())
                                            .canaryWorkloadDeployed(k8sCanaryDeployResponse.isCanaryWorkloadDeployed())
                                            .manifest(executionPassThroughData.getK8sGitFetchInfo())
                                            .build();

    executionSweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.K8S_CANARY_OUTCOME, k8sCanaryOutcome, StepOutcomeGroup.STEP.name());

    if (k8sTaskExecutionResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return K8sStepHelper.getFailureResponseBuilder(k8sTaskExecutionResponse, responseBuilder).build();
    }

    instanceInfoService.saveServerInstancesIntoSweepingOutput(
        ambiance, K8sPodToServiceInstanceInfoMapper.toServerInstanceInfoList(k8sCanaryDeployResponse.getK8sPodList()));
    return responseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(k8sCanaryOutcome)
                         .build())
        .build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private void validate(K8sCanaryStepParameters stepParameters) {
    if (stepParameters.getInstanceSelection() == null || stepParameters.getInstanceSelection().getType() == null
        || stepParameters.getInstanceSelection().getSpec() == null) {
      throw new InvalidRequestException("Instance selection is mandatory");
    }

    String valueType = stepParameters.getInstanceSelection().getType().name().toLowerCase();
    if (stepParameters.getInstanceSelection().getSpec().getInstances() == null) {
      throw new InvalidArgumentsException(String.format("Instance selection %s value is mandatory", valueType));
    }

    if (stepParameters.getInstanceSelection().getSpec().getInstances() <= 0) {
      throw new InvalidArgumentsException(
          String.format("Instance selection %s value cannot be less than 1", valueType));
    }
  }
}
