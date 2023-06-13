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
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo;
import io.harness.cvng.cdng.beans.v2.BaselineType;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.cdng.entities.CVNGStepTask.CVNGStepTaskBuilder;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.cdng.services.api.VerifyStepMonitoredServiceResolutionService;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.beans.sidekick.DemoActivitySideKickData;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ExecutionStatus;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceBuilder;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.opaclient.OpaServiceClient;
import io.harness.plancreator.steps.common.StepElementParameters;
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
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.executable.AsyncExecutableWithCapabilities;
import io.harness.tasks.ProgressData;
import io.harness.tasks.ResponseData;
import io.harness.utils.PolicyEvalUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
public class CVNGStep extends AsyncExecutableWithCapabilities {
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
  @Inject
  private Map<MonitoredServiceSpecType, VerifyStepMonitoredServiceResolutionService> verifyStepCvConfigServiceMap;

  @Inject private OpaServiceClient opaServiceClient;

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
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
    MonitoredServiceSpecType monitoredServiceType = CVNGStepUtils.getMonitoredServiceSpecType(monitoredServiceNode);

    ResolvedCVConfigInfo resolvedCVConfigInfo =
        verifyStepCvConfigServiceMap.get(monitoredServiceType)
            .fetchAndPersistResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode);
    String monitoredServiceIdentifier = resolvedCVConfigInfo.getMonitoredServiceIdentifier();
    String monitoredServiceTemplateIdentifier = resolvedCVConfigInfo.getMonitoredServiceTemplateIdentifier();
    String monitoredServiceTemplateVersionLabel = resolvedCVConfigInfo.getMonitoredServiceTemplateVersionLabel();
    List<CVConfig> cvConfigs = resolvedCVConfigInfo.getCvConfigs();
    log.info("Resolved cvConfigIds {}", resolvedCVConfigInfo.getCvConfigIds());
    Instant deploymentStartTime = Instant.ofEpochMilli(
        AmbianceUtils.getStageLevelFromAmbiance(ambiance)
            .orElseThrow(() -> new IllegalStateException("verify step needs to be part of a stage."))
            .getStartTs());
    List<VerificationType> supportedDataTypesForVerification =
        stepParameters.getSpec().getSupportedDataTypesForVerification();
    if (Objects.nonNull(cvConfigs)) {
      cvConfigs = cvConfigs.stream()
                      .filter(cvConfig -> supportedDataTypesForVerification.contains(cvConfig.getVerificationType()))
                      .peek(cvConfig -> {
                        if (Objects.isNull(cvConfig.getDataSourceName())) {
                          cvConfig.setDataSourceName(cvConfig.getType());
                        }
                      })
                      .collect(Collectors.toList());
    }
    if (CollectionUtils.isEmpty(cvConfigs)) {
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
          deploymentStartTime, monitoredServiceIdentifier, monitoredServiceTemplateIdentifier,
          monitoredServiceTemplateVersionLabel, cvConfigs);
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
                monitoredServiceIdentifier, monitoredServiceTemplateIdentifier, monitoredServiceTemplateVersionLabel,
                cvConfigs);
        activity.setDemoActivity(true);
        verificationJobInstanceId =
            verificationJobInstanceService.createDemoInstances(Arrays.asList(verificationJobInstance)).get(0);
      } else {
        VerificationJobInstanceBuilder verificationJobInstanceBuilder =
            getVerificationJobInstanceBuilder(AmbianceUtils.obtainCurrentLevel(ambiance).getIdentifier(),
                stepParameters, serviceEnvironmentParams, deploymentStartTime, monitoredServiceIdentifier,
                monitoredServiceTemplateIdentifier, monitoredServiceTemplateVersionLabel, cvConfigs);
        activity.fillInVerificationJobInstanceDetails(verificationJobInstanceBuilder);
        verificationJobInstanceBuilder.monitoredServiceType(monitoredServiceType);
        verificationJobInstanceBuilder.nodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance));
        verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstanceBuilder.build());
      }
      verifyStepCvConfigServiceMap.get(monitoredServiceType)
          .managePerpetualTasks(serviceEnvironmentParams, resolvedCVConfigInfo, verificationJobInstanceId);
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
      String monitoredServiceIdentifier, String monitoredServiceTemplateIdentifier,
      String monitoredServiceTemplateVersionLabel, List<CVConfig> cvConfigs) {
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
            .monitoredServiceTemplateIdentifier(monitoredServiceTemplateIdentifier)
            .monitoredServiceTemplateVersionLabel(monitoredServiceTemplateVersionLabel)
            .cvConfigs(cvConfigs)
            .build();
    String baselineType = stepParameters.getBaseline() != null ? stepParameters.getBaseline().getValue() : null;
    VerificationJobInstanceBuilder verificationJobInstanceBuilder =
        fillOutCommonJobInstanceProperties(serviceEnvironmentParams.getAccountIdentifier(), deploymentStartTime,
            verificationJob.resolveAdditionsFields(verificationJobInstanceService,
                baselineType != null ? BaselineType.valueOf(baselineType) : BaselineType.LAST),
            verificationStartTime, baselineType != null ? BaselineType.valueOf(baselineType) : BaselineType.LAST);
    validateJob(verificationJob);
    verificationJobInstanceBuilder.name(stepName);
    return verificationJobInstanceBuilder;
  }

  private VerificationJobInstance getVerificationJobInstanceForDemo(String stepName, CVNGStepParameter stepParameters,
      ServiceEnvironmentParams serviceEnvironmentParams, Instant deploymentStartTime,
      ActivityVerificationStatus activityVerificationStatus, String monitoredServiceIdentifier,
      String monitoredServiceTemplateIdentifier, String monitoredServiceTemplateVersionLabel,
      List<CVConfig> cvConfigs) {
    VerificationJobInstanceBuilder verificationJobInstanceBuilder = getVerificationJobInstanceBuilder(stepName,
        stepParameters, serviceEnvironmentParams, deploymentStartTime, monitoredServiceIdentifier,
        monitoredServiceTemplateIdentifier, monitoredServiceTemplateVersionLabel, cvConfigs);
    verificationJobInstanceBuilder.verificationStatus(activityVerificationStatus);
    verificationJobInstanceBuilder.startTime(clock.instant().minus(Duration.ofMinutes(15)));
    verificationJobInstanceBuilder.deploymentStartTime(clock.instant().minus(Duration.ofMinutes(16)));
    Map<String, CVConfig> cvConfigMap =
        cvConfigs.stream().collect(Collectors.toMap(CVConfig::getUuid, cvConfig -> cvConfig, (u, v) -> v));
    verificationJobInstanceBuilder.cvConfigMap(cvConfigMap);
    return verificationJobInstanceBuilder.build();
  }

  private void validateJob(VerificationJob verificationJob) {
    Preconditions.checkNotNull(verificationJob);
    List<CVConfig> cvConfigs = verificationJob.getCvConfigs();
    Preconditions.checkState(EmptyPredicate.isNotEmpty(cvConfigs),
        "No monitoring sources with identifiers %s defined for environment %s and service %s",
        verificationJob.getMonitoringSources(), verificationJob.getEnvIdentifier(),
        verificationJob.getServiceIdentifier());
  }

  @NotNull
  private DeploymentActivity getDeploymentActivity(CVNGStepParameter stepParameters,
      ServiceEnvironmentParams serviceEnvironmentParams, Ambiance ambiance, Instant activityStartTime,
      String monitoredServiceIdentifier, String monitoredServiceTemplateIdentifier,
      String monitoredServiceTemplateVersionLabel, List<CVConfig> cvConfigs) {
    Instant startTime = clock.instant();
    VerificationJob verificationJob =
        stepParameters.getVerificationJobBuilder()
            .serviceIdentifier(RuntimeParameter.builder().value(stepParameters.getServiceIdentifier()).build())
            .envIdentifier(RuntimeParameter.builder().value(stepParameters.getEnvIdentifier()).build())
            .projectIdentifier(serviceEnvironmentParams.getProjectIdentifier())
            .orgIdentifier(serviceEnvironmentParams.getOrgIdentifier())
            .accountId(serviceEnvironmentParams.getAccountIdentifier())
            .monitoredServiceIdentifier(monitoredServiceIdentifier)
            .monitoredServiceTemplateIdentifier(monitoredServiceTemplateIdentifier)
            .monitoredServiceTemplateVersionLabel(monitoredServiceTemplateVersionLabel)
            .cvConfigs(cvConfigs)
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
    deploymentActivity.setAnalysisStatus(ActivityVerificationStatus.NOT_STARTED);
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
  public StepResponse handleAsyncResponseInternal(
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

  private VerificationJobInstanceBuilder fillOutCommonJobInstanceProperties(String accountId,
      Instant deploymentStartTime, VerificationJob verificationJob, Instant verficationStartTime,
      BaselineType baselineType) {
    return VerificationJobInstance.builder()
        .accountId(accountId)
        .executionStatus(ExecutionStatus.QUEUED)
        .deploymentStartTime(deploymentStartTime)
        .startTime(verficationStartTime)
        .baselineType(baselineType)
        .resolvedJob(verificationJob);
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public StepResponse postAsyncValidate(
      Ambiance ambiance, StepElementParameters stepParameters, StepResponse stepResponse) {
    if (Status.SUCCEEDED.equals(stepResponse.getStatus())) {
      return PolicyEvalUtils.evalPolicies(ambiance, stepParameters, stepResponse, opaServiceClient);
    }
    return stepResponse;
  }
}
