/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.activity.entities.SRMStepAnalysisActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.cdng.beans.CVNGDeploymentImpactStepParameter;
import io.harness.cvng.cdng.beans.CVNGStepType;
import io.harness.cvng.cdng.beans.DefaultAndConfiguredMonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceSpecType;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo;
import io.harness.cvng.cdng.services.api.PipelineStepMonitoredServiceResolutionService;
import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.SyncExecutableWithCapabilities;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.annotation.TypeAlias;

@Slf4j
@OwnedBy(HarnessTeam.CV)

public class CVNGAnalyzeDeploymentStep extends SyncExecutableWithCapabilities {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(CVNGStepType.CVNG_ANALYZE_DEPLOYMENT.getType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject
  private Map<MonitoredServiceSpecType, PipelineStepMonitoredServiceResolutionService> verifyStepCvConfigServiceMap;

  @Inject ActivityService activityService;

  @Inject SRMAnalysisStepService srmAnalysisStepService;

  @Inject MonitoredServiceService monitoredServiceService;

  @Inject Clock clock;
  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepElementParameters stepElementParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    log.info("ExecuteSync called for CVNGAnalyzeDeploymentStep, Step Parameters: {} Ambiance: {}",
        stepElementParameters, ambiance);
    CVNGDeploymentImpactStepParameter deploymentImpactStepParameter =
        (CVNGDeploymentImpactStepParameter) stepElementParameters.getSpec();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String serviceIdentifier = deploymentImpactStepParameter.getServiceIdentifier();
    String envIdentifier = deploymentImpactStepParameter.getEnvIdentifier();
    ServiceEnvironmentParams serviceEnvironmentParams = ServiceEnvironmentParams.builder()
                                                            .accountIdentifier(accountId)
                                                            .orgIdentifier(orgIdentifier)
                                                            .projectIdentifier(projectIdentifier)
                                                            .serviceIdentifier(serviceIdentifier)
                                                            .environmentIdentifier(envIdentifier)
                                                            .build();

    DefaultAndConfiguredMonitoredServiceNode defaultAndConfiguredMonitoredServiceNode =
        deploymentImpactStepParameter.getMonitoredService();
    MonitoredServiceNode monitoredServiceNode = MonitoredServiceNode.builder()
                                                    .type(defaultAndConfiguredMonitoredServiceNode.getType())
                                                    .spec(defaultAndConfiguredMonitoredServiceNode.getSpec())
                                                    .build();
    MonitoredServiceSpecType monitoredServiceType = CVNGStepUtils.getMonitoredServiceSpecType(monitoredServiceNode);
    ResolvedCVConfigInfo resolvedCVConfigInfo =
        verifyStepCvConfigServiceMap.get(monitoredServiceType)
            .fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode);
    String monitoredServiceIdentifier = resolvedCVConfigInfo.getMonitoredServiceIdentifier();
    MonitoredServiceParams monitoredServiceParams =
        MonitoredServiceParams.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .monitoredServiceIdentifier(resolvedCVConfigInfo.getMonitoredServiceIdentifier())
            .build();
    MonitoredService monitoredService = monitoredServiceService.getMonitoredService(monitoredServiceParams);

    if (monitoredService == null) {
      return buildStepResponseForSkippedScenarioWithMessage(String.format(
          "No monitoredService is defined for ref %s", resolvedCVConfigInfo.getMonitoredServiceIdentifier()));
    }
    List<CVConfig> cvConfigs = resolvedCVConfigInfo.getCvConfigs();
    log.info("Resolved cvConfigIds {}", resolvedCVConfigInfo.getCvConfigIds());
    if (CollectionUtils.isEmpty(cvConfigs)) {
      return buildStepResponseForSkippedScenarioWithMessage(
          String.format("No healthSource is defined for monitoredServiceRef %s", monitoredServiceIdentifier));
    } else if (!monitoredService.isEnabled()) {
      return buildStepResponseForSkippedScenarioWithMessage(String.format(
          "Monitored service %s is disabled. Please enable it to run the analysis step.", monitoredServiceIdentifier));
    } else {
      SRMStepAnalysisActivity activity =
          getSRMAnalysisActivity(serviceEnvironmentParams, ambiance, monitoredServiceIdentifier);
      String duration = deploymentImpactStepParameter.getDuration();
      String executionDetailsId = srmAnalysisStepService.createSRMAnalysisStepExecution(
          ambiance, monitoredServiceIdentifier, serviceEnvironmentParams, getDurationFromString(duration));
      activity.setExecutionNotificationDetailsId(executionDetailsId);
      String activityId = activityService.upsert(activity);
      log.info("Saved Step Analysis Activity {}", activityId);
      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name("output")
                           .outcome(AnalyzeDeploymentStepOutcome.builder().activityId(activityId).build())
                           .build())
          .build();
    }
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Value
  @Builder
  @JsonTypeName("analyzeDeploymentStepOutcome")
  @TypeAlias("analyzeDeploymentStepOutcome")
  @RecasterAlias("io.harness.cvng.cdng.services.impl.AnalyzeDeploymentStepOutcome")
  public static class AnalyzeDeploymentStepOutcome implements Outcome {
    String activityId;
  }

  @NotNull
  private SRMStepAnalysisActivity getSRMAnalysisActivity(
      ServiceEnvironmentParams serviceEnvironmentParams, Ambiance ambiance, String monitoredServiceIdentifier) {
    return SRMStepAnalysisActivity.builder()
        .stageId(AmbianceUtils.getStageLevelFromAmbiance(ambiance).get().getIdentifier())
        .stageStepId(AmbianceUtils.getStageLevelFromAmbiance(ambiance).get().getSetupId())
        .pipelineId(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
        .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
        .accountId(serviceEnvironmentParams.getAccountIdentifier())
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .activityStartTime(clock.instant())
        .activityName(getActivityName(monitoredServiceIdentifier))
        .eventTime(clock.instant())
        .type(ActivityType.SRM_STEP_ANALYSIS)
        .build();
  }

  private String getActivityName(String monitoredServiceIdentifier) {
    return "SRM Step Analysis of " + monitoredServiceIdentifier;
  }

  private Duration getDurationFromString(String duration) {
    String number = duration.substring(0, duration.length() - 1);
    if (duration.charAt(duration.length() - 1) == 'H') {
      return Duration.ofHours(Integer.parseInt(number));
    }
    if (duration.charAt(duration.length() - 1) == 'D') {
      return Duration.ofDays(Integer.parseInt(number));
    }
    throw new IllegalArgumentException(String.format("Invalid duration %s", duration));
  }

  private StepResponse buildStepResponseForSkippedScenarioWithMessage(String message) {
    return StepResponse.builder()
        .status(Status.SKIPPED)
        .failureInfo(FailureInfo.newBuilder()
                         .addFailureData(FailureData.newBuilder()
                                             .setCode(ErrorCode.UNKNOWN_ERROR.name())
                                             .setLevel(Level.INFO.name())
                                             .addFailureTypes(FailureType.SKIPPING_FAILURE)
                                             .setMessage(message)
                                             .build())
                         .build())
        .build();
  }
}
