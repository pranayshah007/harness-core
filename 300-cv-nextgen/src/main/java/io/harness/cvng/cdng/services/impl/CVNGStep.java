/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.activity.entities.DeploymentActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.beans.activity.ActivityStatusDTO;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.cdng.beans.CVNGStepParameter;
import io.harness.cvng.cdng.beans.CVNGStepType;
import io.harness.cvng.cdng.beans.ConfiguredMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType;
import io.harness.cvng.cdng.beans.TemplateMonitoredServiceSpec;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.cdng.entities.CVNGStepTask.CVNGStepTaskBuilder;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.beans.sidekick.DemoActivitySideKickData;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ExecutionStatus;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.AsyncExecutableWithRollback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
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
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.tasks.ProgressData;
import io.harness.tasks.ResponseData;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.annotation.TypeAlias;

@Slf4j
@OwnedBy(HarnessTeam.CV)
public class CVNGStep extends AsyncExecutableWithRollback {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(CVNGStepType.CVNG_VERIFY.getDisplayName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ActivityService activityService;
  @Inject private CVNGStepTaskService cvngStepTaskService;
  @Inject private Clock clock;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private SideKickService sideKickService;

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepElementParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    log.info("ExecuteAsync called for CVNGStep");
    CVNGStepParameter stepParameters = (CVNGStepParameter) stepElementParameters.getSpec();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    validate(stepParameters);
    String serviceIdentifier = stepParameters.getServiceIdentifier();
    String envIdentifier = stepParameters.getEnvIdentifier();

    ServiceEnvironmentParams serviceEnvironmentParams = ServiceEnvironmentParams.builder()
                                                            .accountIdentifier(accountId)
                                                            .orgIdentifier(orgIdentifier)
                                                            .projectIdentifier(projectIdentifier)
                                                            .serviceIdentifier(serviceIdentifier)
                                                            .environmentIdentifier(envIdentifier)
                                                            .build();
    MonitoredServiceNode monitoredServiceNode = stepParameters.getMonitoredService();
    MonitoredServiceSpecType monitoredServiceType = MonitoredServiceSpecType.DEFAULT;
    // TODO: Modify this code to fetch CvConfig directly from db or create from MonitoredServiceDTO and save in
    // VerificationJob.
    List<String> healthSourceIdentifiers = new ArrayList<>();
    String monitoredServiceIdentifier = null;
    if (Objects.nonNull(monitoredServiceNode)) {
      try {
        monitoredServiceType = MonitoredServiceSpecType.valueOf(monitoredServiceNode.getType());
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("Invalid monitored service type" + monitoredServiceNode.getType() + ".", ex);
      }
    }
    switch (monitoredServiceType) {
      case TEMPLATE: {
        TemplateMonitoredServiceSpec templateMonitoredServiceSpec =
            (TemplateMonitoredServiceSpec) monitoredServiceNode.getSpec();
        MonitoredServiceDTO monitoredServiceDTO =
            monitoredServiceService.getExpandedMonitoredServiceFromYaml(ProjectParams.builder()
                                                                            .accountIdentifier(accountId)
                                                                            .orgIdentifier(orgIdentifier)
                                                                            .projectIdentifier(projectIdentifier)
                                                                            .build(),
                getTemplateYaml(templateMonitoredServiceSpec));
        if (Objects.nonNull(monitoredServiceDTO) && Objects.nonNull(monitoredServiceDTO.getSources())
            && CollectionUtils.isNotEmpty(monitoredServiceDTO.getSources().getHealthSources())) {
          // TODO: Add code to create perpetual tasks.
          healthSourceIdentifiers.addAll(monitoredServiceDTO.getSources()
                                             .getHealthSources()
                                             .stream()
                                             .map(HealthSource::getIdentifier)
                                             .collect(Collectors.toList()));
        }
        break;
      }
      case CONFIGURED: {
        ConfiguredMonitoredServiceSpec configuredMonitoredServiceSpec =
            (ConfiguredMonitoredServiceSpec) monitoredServiceNode.getSpec();
        String monitoredServiceRef = configuredMonitoredServiceSpec.getMonitoredServiceRef().getValue();
        MonitoredService monitoredService = monitoredServiceService.getMonitoredService(
            MonitoredServiceParams.builder().monitoredServiceIdentifier(monitoredServiceRef).build());
        if (Objects.nonNull(monitoredService)
            && CollectionUtils.isNotEmpty(monitoredService.getHealthSourceIdentifiers())) {
          healthSourceIdentifiers.addAll(monitoredService.getHealthSourceIdentifiers());
          monitoredServiceIdentifier = monitoredService.getIdentifier();
        }
        break;
      }
      case DEFAULT:
      default: {
        MonitoredService monitoredService =
            monitoredServiceService.getApplicationMonitoredService(serviceEnvironmentParams).orElse(null);
        if (Objects.nonNull(monitoredService)
            && CollectionUtils.isNotEmpty(monitoredService.getHealthSourceIdentifiers())) {
          healthSourceIdentifiers.addAll(monitoredService.getHealthSourceIdentifiers());
          monitoredServiceIdentifier = monitoredService.getIdentifier();
        }
      }
    }

    Instant deploymentStartTime = Instant.ofEpochMilli(
        AmbianceUtils.getStageLevelFromAmbiance(ambiance)
            .orElseThrow(() -> new IllegalStateException("verify step needs to be part of a stage."))
            .getStartTs());
    if (healthSourceIdentifiers.isEmpty()) {
      CVNGStepTaskBuilder cvngStepTaskBuilder = CVNGStepTask.builder();
      cvngStepTaskBuilder.skip(true);
      cvngStepTaskBuilder.callbackId(UUID.randomUUID().toString());
      CVNGStepTask cvngStepTask = cvngStepTaskBuilder.accountId(serviceEnvironmentParams.getAccountIdentifier())
                                      .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
                                      .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
                                      .serviceIdentifier(serviceEnvironmentParams.getServiceIdentifier())
                                      .environmentIdentifier(serviceEnvironmentParams.getEnvironmentIdentifier())
                                      .status(CVNGStepTask.Status.IN_PROGRESS)
                                      .deploymentStartTime(deploymentStartTime)
                                      .build();
      cvngStepTaskService.create(cvngStepTask);
      return AsyncExecutableResponse.newBuilder().addCallbackIds(cvngStepTaskBuilder.build().getCallbackId()).build();
    } else {
      String verificationJobInstanceId;
      DeploymentActivity activity = getDeploymentActivity(stepParameters, serviceEnvironmentParams, ambiance,
          deploymentStartTime, healthSourceIdentifiers, monitoredServiceIdentifier);
      boolean isDemoEnabled = isDemoEnabled(accountId, ambiance);
      boolean shouldFailVerification = false;
      if (isDemoEnabled) {
        shouldFailVerification = shouldFailVerification(ambiance, stepParameters.getSensitivity());
        VerificationJobInstance verificationJobInstance =
            getVerificationJobInstanceForDemo(AmbianceUtils.obtainCurrentLevel(ambiance).getIdentifier(),
                stepParameters, serviceEnvironmentParams, deploymentStartTime,
                shouldFailVerification(ambiance, stepParameters.getSensitivity())
                    ? ActivityVerificationStatus.VERIFICATION_FAILED
                    : ActivityVerificationStatus.VERIFICATION_PASSED,
                healthSourceIdentifiers, monitoredServiceIdentifier);
        activity.setDemoActivity(true);
        verificationJobInstanceId =
            verificationJobInstanceService.createDemoInstances(Arrays.asList(verificationJobInstance)).get(0);
      } else {
        VerificationJobInstanceBuilder verificationJobInstanceBuilder = getVerificationJobInstanceBuilder(
            AmbianceUtils.obtainCurrentLevel(ambiance).getIdentifier(), stepParameters, serviceEnvironmentParams,
            deploymentStartTime, healthSourceIdentifiers, monitoredServiceIdentifier);
        activity.fillInVerificationJobInstanceDetails(verificationJobInstanceBuilder);
        verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstanceBuilder.build());
      }
      activity.setVerificationJobInstanceIds(Arrays.asList(verificationJobInstanceId));
      String activityId = activityService.upsert(activity);
      CVNGStepTask cvngStepTask = CVNGStepTask.builder()
                                      .accountId(serviceEnvironmentParams.getAccountIdentifier())
                                      .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
                                      .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
                                      .serviceIdentifier(serviceEnvironmentParams.getServiceIdentifier())
                                      .environmentIdentifier(serviceEnvironmentParams.getEnvironmentIdentifier())
                                      .status(CVNGStepTask.Status.IN_PROGRESS)
                                      .deploymentStartTime(deploymentStartTime)
                                      .activityId(activityId)
                                      .callbackId(verificationJobInstanceId)
                                      .verificationJobInstanceId(verificationJobInstanceId)
                                      .build();
      cvngStepTaskService.create(cvngStepTask);
      if (isDemoEnabled && !shouldFailVerification) {
        sideKickService.schedule(DemoActivitySideKickData.builder().deploymentActivityId(activityId).build(),
            activity.getActivityStartTime().plus(Duration.ofMinutes(10)));
      }
      return AsyncExecutableResponse.newBuilder().addCallbackIds(verificationJobInstanceId).build();
    }
  }

  private boolean isDemoEnabled(String accountId, Ambiance ambiance) {
    String identifier = AmbianceUtils.obtainCurrentLevel(ambiance).getIdentifier();
    return (identifier.endsWith("_dev") || identifier.endsWith("_prod") || identifier.endsWith("_demo"))
        && featureFlagService.isFeatureFlagEnabled(accountId, "CVNG_VERIFY_STEP_DEMO");
  }

  private boolean shouldFailVerification(Ambiance ambiance, ParameterField<String> sensitivity) {
    String identifier = AmbianceUtils.obtainCurrentLevel(ambiance).getIdentifier();
    if (identifier.endsWith("_demo")) {
      return Sensitivity.HIGH.getValue().equalsIgnoreCase(sensitivity.getValue());
    }
    return identifier.endsWith("_dev");
  }

  private VerificationJobInstanceBuilder getVerificationJobInstanceBuilder(String stepName,
      CVNGStepParameter stepParameters, ServiceEnvironmentParams serviceEnvironmentParams, Instant deploymentStartTime,
      List<String> healthSourceIdentifiers, String monitoredServiceIdentifier) {
    Instant verificationStartTime = clock.instant();
    VerificationJob verificationJob =
        stepParameters.getVerificationJobBuilder()
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .serviceIdentifier(RuntimeParameter.builder().value(stepParameters.getServiceIdentifier()).build())
            .envIdentifier(RuntimeParameter.builder().value(stepParameters.getEnvIdentifier()).build())
            .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
            .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
            .accountId(serviceEnvironmentParams.getAccountIdentifier())
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .monitoringSources(healthSourceIdentifiers.stream()
                                   .map(healthSourceIdentifier
                                       -> HealthSourceService.getNameSpacedIdentifier(
                                           monitoredServiceIdentifier, healthSourceIdentifier))
                                   .collect(Collectors.toList()))
            .build();
    VerificationJobInstanceBuilder verificationJobInstanceBuilder =
        fillOutCommonJobInstanceProperties(serviceEnvironmentParams.getAccountIdentifier(), deploymentStartTime,
            verificationJob.resolveAdditionsFields(verificationJobInstanceService), verificationStartTime);
    validateJob(verificationJob);
    verificationJobInstanceBuilder.name(stepName);
    return verificationJobInstanceBuilder;
  }

  private VerificationJobInstance getVerificationJobInstanceForDemo(String stepName, CVNGStepParameter stepParameters,
      ServiceEnvironmentParams serviceEnvironmentParams, Instant deploymentStartTime,
      ActivityVerificationStatus activityVerificationStatus, List<String> healthSourceIdentifiers,
      String monitoredServiceIdentifier) {
    VerificationJobInstanceBuilder verificationJobInstanceBuilder =
        getVerificationJobInstanceBuilder(stepName, stepParameters, serviceEnvironmentParams, deploymentStartTime,
            healthSourceIdentifiers, monitoredServiceIdentifier);
    verificationJobInstanceBuilder.verificationStatus(activityVerificationStatus);
    verificationJobInstanceBuilder.startTime(clock.instant().minus(Duration.ofMinutes(15)));
    verificationJobInstanceBuilder.deploymentStartTime(clock.instant().minus(Duration.ofMinutes(16)));
    return verificationJobInstanceBuilder.build();
  }

  private void validateJob(VerificationJob verificationJob) {
    List<CVConfig> cvConfigs = verificationJobInstanceService.getCVConfigsForVerificationJob(verificationJob);
    Preconditions.checkState(EmptyPredicate.isNotEmpty(cvConfigs),
        "No monitoring sources with identifiers %s defined for environment %s and service %s",
        verificationJob.getMonitoringSources(), verificationJob.getEnvIdentifier(),
        verificationJob.getServiceIdentifier());
  }

  @NotNull
  private DeploymentActivity getDeploymentActivity(CVNGStepParameter stepParameters,
      ServiceEnvironmentParams serviceEnvironmentParams, Ambiance ambiance, Instant activityStartTime,
      List<String> healthSourceIdentifiers, String monitoredServiceIdentifier) {
    Instant startTime = clock.instant();
    VerificationJob verificationJob =
        stepParameters.getVerificationJobBuilder()
            .serviceIdentifier(RuntimeParameter.builder().value(stepParameters.getServiceIdentifier()).build())
            .envIdentifier(RuntimeParameter.builder().value(stepParameters.getEnvIdentifier()).build())
            .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
            .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
            .accountId(serviceEnvironmentParams.getAccountIdentifier())
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .monitoringSources(healthSourceIdentifiers.stream()
                                   .map(healthSourceIdentifier
                                       -> HealthSourceService.getNameSpacedIdentifier(
                                           monitoredServiceIdentifier, healthSourceIdentifier))
                                   .collect(Collectors.toList()))
            .build();
    DeploymentActivity deploymentActivity =
        DeploymentActivity.builder()
            .deploymentTag(stepParameters.getDeploymentTag().getValue())
            .verificationStartTime(startTime.toEpochMilli())
            .stageId(AmbianceUtils.getStageLevelFromAmbiance(ambiance).get().getIdentifier())
            .stageStepId(AmbianceUtils.getStageLevelFromAmbiance(ambiance).get().getSetupId())
            .pipelineId(ambiance.getMetadata().getPipelineIdentifier())
            .planExecutionId(ambiance.getPlanExecutionId())
            .build();
    deploymentActivity.setVerificationJobs(Collections.singletonList(verificationJob));
    deploymentActivity.setActivityStartTime(activityStartTime);
    deploymentActivity.setOrgIdentifier(serviceEnvironmentParams.getOrgIdentifier());
    deploymentActivity.setAccountId(serviceEnvironmentParams.getAccountIdentifier());
    deploymentActivity.setProjectIdentifier(serviceEnvironmentParams.getProjectIdentifier());
    deploymentActivity.setMonitoredServiceIdentifier(monitoredServiceIdentifier);
    deploymentActivity.setActivityName(getActivityName(stepParameters));
    deploymentActivity.setType(ActivityType.DEPLOYMENT);
    return deploymentActivity;
  }

  private void validate(CVNGStepParameter stepParameters) {
    Preconditions.checkNotNull(stepParameters.getDeploymentTag().getValue(),
        "Could not resolve expression for deployment tag. Please check your expression.");
  }

  private String getActivityName(CVNGStepParameter stepParameters) {
    return "CD Nextgen - " + stepParameters.getServiceIdentifier() + " - "
        + stepParameters.getDeploymentTag().getValue();
  }

  @Value
  @Builder
  public static class CVNGResponseData implements ResponseData, ProgressData {
    boolean skip;
    @Deprecated String activityId;
    String verifyStepExecutionId;
    ActivityStatusDTO activityStatusDTO;
  }
  @Value
  @Builder
  @JsonTypeName("verifyStepOutcome")
  @TypeAlias("verifyStepOutcome")
  @RecasterAlias("io.harness.cvng.cdng.services.impl.CVNGStep$VerifyStepOutcome")
  public static class VerifyStepOutcome implements ProgressData, Outcome {
    int progressPercentage;
    String estimatedRemainingTime;
    @Deprecated String activityId;
    String verifyStepExecutionId;
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepElementParameters, Map<String, ResponseData> responseDataMap) {
    log.info("handleAsyncResponse async response");
    CVNGStepParameter stepParameters = (CVNGStepParameter) stepElementParameters.getSpec();
    CVNGResponseData cvngResponseData = (CVNGResponseData) responseDataMap.values().iterator().next();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    if (cvngResponseData.isSkip()) {
      stepResponseBuilder.status(Status.SKIPPED)
          .failureInfo(
              FailureInfo.newBuilder()
                  .addFailureData(
                      FailureData.newBuilder()
                          .setCode(ErrorCode.UNKNOWN_ERROR.name())
                          .setLevel(Level.INFO.name())
                          .addFailureTypes(FailureType.SKIPPING_FAILURE)
                          .setMessage(String.format("No monitoredServiceRef is defined for service %s and env %s",
                              stepParameters.getServiceIdentifier(), stepParameters.getEnvIdentifier()))
                          .build())
                  .build());
    } else {
      Status status;
      FailureType failureType = null;
      String failureMessage = null;
      ErrorCode errorCode = null;
      switch (cvngResponseData.getActivityStatusDTO().getStatus()) {
        case VERIFICATION_PASSED:
          status = Status.SUCCEEDED;
          break;
        case VERIFICATION_FAILED:
          status = Status.FAILED;
          errorCode = ErrorCode.DEFAULT_ERROR_CODE;
          failureType = FailureType.VERIFICATION_FAILURE;
          failureMessage = "Verification failed";
          break;
        case ERROR:
        case IGNORED:
          status = Status.FAILED;
          errorCode = ErrorCode.UNKNOWN_ERROR;
          failureType = FailureType.UNKNOWN_FAILURE;
          failureMessage = "Verification could not complete due to an unknown error";
          break;
        default:
          throw new IllegalStateException(
              "Invalid status value: " + cvngResponseData.getActivityStatusDTO().getStatus());
      }
      stepResponseBuilder.status(status).stepOutcome(
          StepResponse.StepOutcome.builder()
              .name("output")
              .outcome(VerifyStepOutcome.builder()
                           .progressPercentage(cvngResponseData.getActivityStatusDTO().getProgressPercentage())
                           .estimatedRemainingTime(TimeUnit.MILLISECONDS.toMinutes(
                                                       cvngResponseData.getActivityStatusDTO().getRemainingTimeMs())
                               + " minutes")
                           .activityId(cvngResponseData.getActivityId())
                           .verifyStepExecutionId(cvngResponseData.verifyStepExecutionId)
                           .build())
              .build());
      if (status == Status.FAILED) {
        stepResponseBuilder.failureInfo(FailureInfo.newBuilder()
                                            .addFailureData(FailureData.newBuilder()
                                                                .setCode(errorCode.name())
                                                                .setLevel(Level.ERROR.name())
                                                                .addFailureTypes(failureType)
                                                                .setMessage(failureMessage)
                                                                .build())
                                            .build());
      }
    }
    return stepResponseBuilder.build();
  }

  @Override
  public ProgressData handleProgress(
      Ambiance ambiance, StepElementParameters stepElementParameters, ProgressData progressData) {
    CVNGResponseData cvngResponseData = (CVNGResponseData) progressData;
    return VerifyStepOutcome.builder()
        .progressPercentage(cvngResponseData.getActivityStatusDTO().getProgressPercentage())
        .estimatedRemainingTime(
            TimeUnit.MILLISECONDS.toMinutes(cvngResponseData.getActivityStatusDTO().getRemainingTimeMs()) + " minutes")
        .activityId(cvngResponseData.getActivityId())
        .build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepElementParameters, AsyncExecutableResponse executableResponse) {
    CVNGStepTask cvngStepTask = cvngStepTaskService.getByCallBackId(executableResponse.getCallbackIds(0));
    activityService.abort(cvngStepTask.getActivityId());
  }

  private VerificationJobInstanceBuilder fillOutCommonJobInstanceProperties(
      String accountId, Instant deploymentStartTime, VerificationJob verificationJob, Instant verficationStartTime) {
    return VerificationJobInstance.builder()
        .accountId(accountId)
        .executionStatus(ExecutionStatus.QUEUED)
        .deploymentStartTime(deploymentStartTime)
        .startTime(verficationStartTime)
        .resolvedJob(verificationJob);
  }

  private String getTemplateYaml(TemplateMonitoredServiceSpec templateMonitoredServiceSpec) {
    // TODO: Add logic to generate template yaml.
    //    String monitoredServiceTemplateRef = templateMonitoredServiceSpec.getMonitoredServiceTemplateRef().getValue();
    //    String versionLabel = templateMonitoredServiceSpec.getVersionLabel();
    return null;
  }
}
