/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.outcome.TasRollingDeployOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.task.pcf.response.CfRollingDeployResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
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
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasRouteMappingStep extends TaskChainExecutableWithRollbackAndRbac implements TasStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ROUTE_MAPPING.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private OutcomeService outcomeService;
  @Inject private TasStepHelper tasStepHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private StepHelper stepHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_TAS_NG)) {
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
        executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.TAS_ROLLING_DEPLOY_OUTCOME,
            tasRollingDeployOutcome, StepCategory.STEP.name());
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
          ambiance, tasExecutionPassThroughData.getTasManifestsPackage(), appName, tasInfraConfig);
      tasStepHelper.updateAutoscalarEnabledField(ambiance,
          !isNull(tasExecutionPassThroughData.getTasManifestsPackage().getAutoscalarManifestYml()), appName,
          tasInfraConfig);
      tasStepHelper.updateRouteMapsField(
          ambiance, response.getNewApplicationInfo().getAttachedRoutes(), appName, tasInfraConfig);
      tasStepHelper.updateDesiredCountField(
          ambiance, tasExecutionPassThroughData.getDesiredCountInFinalYaml(), appName, tasInfraConfig);
      tasStepHelper.updateIsFirstDeploymentField(
          ambiance, response.getCurrentProdInfo() == null, appName, tasInfraConfig);

      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.TAS_ROLLING_DEPLOY_OUTCOME,
          tasRollingDeployOutcome, StepCategory.STEP.name());

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
    return TaskChainResponse.builder()
        .taskRequest(TaskRequest.newBuilder().build())
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
