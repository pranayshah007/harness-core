/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.beans.activity.ActivityStatusDTO;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.cdng.beans.CVNGStepParameter;
import io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType;
import io.harness.cvng.cdng.beans.TestVerificationJobSpec;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.cdng.entities.CVNGStepTask.CVNGStepTaskKeys;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.cdng.services.api.VerifyStepMonitoredServiceResolutionService;
import io.harness.cvng.cdng.services.impl.CVNGStep.VerifyStepOutcome;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter;
import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobBuilder;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.eraro.ErrorCode;
import io.harness.persistence.HPersistence;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class CVNGStepTest extends CvNextGenTestBase {
  private CVNGStep cvngStep;
  @Inject private Injector injector;
  @Inject private HPersistence hPersistence;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private MetricPackService metricPackService;
  @Inject private ActivityService activityService;
  @Inject private ChangeSourceService changeSourceService;
  @Inject private CVNGStepTaskService cvngStepTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject
  private DefaultVerifyStepMonitoredServiceResolutionServiceImpl defaultVerifyStepMonitoredServiceResolutionService;

  private Map<MonitoredServiceSpecType, VerifyStepMonitoredServiceResolutionService> verifyStepCvConfigServiceMap;
  private DefaultVerifyStepMonitoredServiceResolutionServiceImpl
      spiedDefaultVerifyStepMonitoredServiceResolutionService;
  private BuilderFactory builderFactory;
  private String accountId;
  private String projectIdentifier;
  private String orgIdentifier;
  private String serviceIdentifier;
  private String envIdentifier;
  private MonitoredServiceDTO monitoredServiceDTO;
  long activityStartTime;
  @Before
  public void setup() throws IllegalAccessException {
    cvngStep = new CVNGStep();
    Injector withPMSSDK = injector.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ExecutionSweepingOutputService.class).toInstance(mock(ExecutionSweepingOutputService.class));
      }
    });
    withPMSSDK.injectMembers(cvngStep);
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    activityStartTime = builderFactory.getClock().instant().minus(Duration.ofMinutes(3)).toEpochMilli();
    verifyStepCvConfigServiceMap = new HashMap<>();
    spiedDefaultVerifyStepMonitoredServiceResolutionService = spy(defaultVerifyStepMonitoredServiceResolutionService);
    verifyStepCvConfigServiceMap.put(
        MonitoredServiceSpecType.DEFAULT, spiedDefaultVerifyStepMonitoredServiceResolutionService);
    FieldUtils.writeField(changeSourceService, "changeSourceUpdateHandlerMap", new HashMap<>(), true);
    FieldUtils.writeField(monitoredServiceService, "changeSourceService", changeSourceService, true);
    FieldUtils.writeField(cvngStep, "clock", builderFactory.getClock(), true);
    FieldUtils.writeField(cvngStep, "verifyStepCvConfigServiceMap", verifyStepCvConfigServiceMap, true);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testExecuteAsync_noMonitoringSourceDefined() {
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getStepElementParameters();
    AsyncExecutableResponse asyncExecutableResponse =
        cvngStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse.getCallbackIdsList()).hasSize(1);
    String callbackId = asyncExecutableResponse.getCallbackIds(0);
    CVNGStepTask cvngStepTask =
        hPersistence.createQuery(CVNGStepTask.class).filter(CVNGStepTaskKeys.callbackId, callbackId).get();
    assertThat(cvngStepTask.getStatus()).isEqualTo(CVNGStepTask.Status.IN_PROGRESS);
    assertThat(cvngStepTask.isSkip()).isTrue();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testExecuteAsync_whenHealthSourcesAreEmpty() {
    monitoredServiceService.createDefault(
        builderFactory.getContext().getProjectParams(), serviceIdentifier, envIdentifier);
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getStepElementParameters();
    AsyncExecutableResponse asyncExecutableResponse =
        cvngStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse.getCallbackIdsList()).hasSize(1);
    String callbackId = asyncExecutableResponse.getCallbackIds(0);
    CVNGStepTask cvngStepTask =
        hPersistence.createQuery(CVNGStepTask.class).filter(CVNGStepTaskKeys.callbackId, callbackId).get();
    assertThat(cvngStepTask.getStatus()).isEqualTo(CVNGStepTask.Status.IN_PROGRESS);
    assertThat(cvngStepTask.isSkip()).isTrue();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testExecuteAsync_skipWhenMonitoredServiceDoesNotExists() {
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getStepElementParameters();
    AsyncExecutableResponse asyncExecutableResponse =
        cvngStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse.getCallbackIdsList()).hasSize(1);
    String callbackId = asyncExecutableResponse.getCallbackIds(0);
    CVNGStepTask cvngStepTask = cvngStepTaskService.getByCallBackId(callbackId);
    assertThat(cvngStepTask.getStatus()).isEqualTo(CVNGStepTask.Status.IN_PROGRESS);
    assertThat(cvngStepTask.isSkip()).isTrue();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testExecuteAsync_createActivity() {
    Ambiance ambiance = getAmbiance();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getStepElementParameters();
    AsyncExecutableResponse asyncExecutableResponse =
        cvngStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse.getCallbackIdsList()).hasSize(1);
    String callbackId = asyncExecutableResponse.getCallbackIds(0);
    CVNGStepTask cvngStepTask = cvngStepTaskService.getByCallBackId(callbackId);
    assertThat(cvngStepTask.getStatus()).isEqualTo(CVNGStepTask.Status.IN_PROGRESS);
    Activity activity = activityService.get(cvngStepTask.getActivityId());
    assertThat(activity.getActivityStartTime()).isEqualTo(Instant.ofEpochMilli(activityStartTime));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecuteAsync_createDemoActivityFFOn() throws IllegalAccessException {
    FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
    when(featureFlagService.isFeatureFlagEnabled(eq(accountId), eq("CVNG_VERIFY_STEP_DEMO"))).thenReturn(true);
    FieldUtils.writeField(cvngStep, "featureFlagService", featureFlagService, true);
    Ambiance ambiance = getAmbiance("verify_dev");
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getStepElementParameters();
    AsyncExecutableResponse asyncExecutableResponse =
        cvngStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse.getCallbackIdsList()).hasSize(1);
    String callbackId = asyncExecutableResponse.getCallbackIds(0);
    CVNGStepTask cvngStepTask =
        hPersistence.createQuery(CVNGStepTask.class).filter(CVNGStepTaskKeys.callbackId, callbackId).get();
    assertThat(cvngStepTask.getStatus()).isEqualTo(CVNGStepTask.Status.IN_PROGRESS);
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(cvngStepTask.getVerificationJobInstanceId());
    assertThat(verificationJobInstance.getName()).isEqualTo("verify_dev");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecuteAsync_createDemoActivityFFOnAndSensitivityHigh() throws IllegalAccessException {
    FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
    when(featureFlagService.isFeatureFlagEnabled(eq(accountId), eq("CVNG_VERIFY_STEP_DEMO"))).thenReturn(true);
    FieldUtils.writeField(cvngStep, "featureFlagService", featureFlagService, true);
    Ambiance ambiance = getAmbiance("verify_demo");
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getStepElementParameters();
    AsyncExecutableResponse asyncExecutableResponse =
        cvngStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse.getCallbackIdsList()).hasSize(1);
    String callbackId = asyncExecutableResponse.getCallbackIds(0);
    CVNGStepTask cvngStepTask =
        hPersistence.createQuery(CVNGStepTask.class).filter(CVNGStepTaskKeys.callbackId, callbackId).get();
    assertThat(cvngStepTask.getStatus()).isEqualTo(CVNGStepTask.Status.IN_PROGRESS);
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(cvngStepTask.getVerificationJobInstanceId());
    assertThat(verificationJobInstance.getName()).isEqualTo("verify_demo");
    assertThat(verificationJobInstance.getVerificationStatus())
        .isEqualTo(ActivityVerificationStatus.VERIFICATION_FAILED);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecuteAsync_createDemoActivityFFOnAndSensitivityMedium() throws IllegalAccessException {
    FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
    when(featureFlagService.isFeatureFlagEnabled(eq(accountId), eq("CVNG_VERIFY_STEP_DEMO"))).thenReturn(true);
    FieldUtils.writeField(cvngStep, "featureFlagService", featureFlagService, true);
    Ambiance ambiance = getAmbiance("verify_demo");
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getStepElementParameters();
    ((CVNGStepParameter) stepElementParameters.getSpec()).setSensitivity(ParameterField.createValueField("Medium"));
    AsyncExecutableResponse asyncExecutableResponse =
        cvngStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse.getCallbackIdsList()).hasSize(1);
    String callbackId = asyncExecutableResponse.getCallbackIds(0);
    CVNGStepTask cvngStepTask =
        hPersistence.createQuery(CVNGStepTask.class).filter(CVNGStepTaskKeys.callbackId, callbackId).get();
    assertThat(cvngStepTask.getStatus()).isEqualTo(CVNGStepTask.Status.IN_PROGRESS);
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(cvngStepTask.getVerificationJobInstanceId());
    assertThat(verificationJobInstance.getName()).isEqualTo("verify_demo");
    assertThat(verificationJobInstance.getVerificationStatus())
        .isEqualTo(ActivityVerificationStatus.VERIFICATION_PASSED);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = getStepElementParameters();
    String activityId = generateUuid();
    ActivityStatusDTO activityStatusDTO = ActivityStatusDTO.builder()
                                              .activityId(activityId)
                                              .status(ActivityVerificationStatus.VERIFICATION_PASSED)
                                              .progressPercentage(100)
                                              .remainingTimeMs(0)
                                              .durationMs(Duration.ofMinutes(30).toMillis())
                                              .build();
    StepResponse stepResponse = cvngStep.handleAsyncResponse(ambiance, stepElementParameters,
        Collections.singletonMap(activityId,
            CVNGStep.CVNGResponseData.builder().activityId(activityId).activityStatusDTO(activityStatusDTO).build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getFailureInfo()).isNull();
    StepOutcome stepOutcome = StepOutcome.builder()
                                  .name("output")
                                  .outcome(VerifyStepOutcome.builder()
                                               .progressPercentage(100)
                                               .estimatedRemainingTime("0 minutes")
                                               .activityId(activityId)
                                               .build())
                                  .build();
    assertThat(stepResponse.getStepOutcomes()).isEqualTo(Collections.singletonList(stepOutcome));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_verificationFailure() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = getStepElementParameters();
    String activityId = generateUuid();
    ActivityStatusDTO activityStatusDTO = ActivityStatusDTO.builder()
                                              .activityId(activityId)
                                              .status(ActivityVerificationStatus.VERIFICATION_FAILED)
                                              .progressPercentage(100)
                                              .remainingTimeMs(0)
                                              .durationMs(Duration.ofMinutes(30).toMillis())
                                              .build();
    StepResponse stepResponse = cvngStep.handleAsyncResponse(ambiance, stepElementParameters,
        Collections.singletonMap(activityId,
            CVNGStep.CVNGResponseData.builder().activityId(activityId).activityStatusDTO(activityStatusDTO).build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo())
        .isEqualTo(FailureInfo.newBuilder()
                       .addFailureData(FailureData.newBuilder()
                                           .setCode(ErrorCode.DEFAULT_ERROR_CODE.name())
                                           .setLevel(io.harness.eraro.Level.ERROR.name())
                                           .addFailureTypes(FailureType.VERIFICATION_FAILURE)
                                           .setMessage("Verification failed")
                                           .build())
                       .build());
    StepOutcome stepOutcome = StepOutcome.builder()
                                  .name("output")
                                  .outcome(VerifyStepOutcome.builder()
                                               .progressPercentage(100)
                                               .estimatedRemainingTime("0 minutes")
                                               .activityId(activityId)
                                               .build())
                                  .build();
    assertThat(stepResponse.getStepOutcomes()).isEqualTo(Collections.singletonList(stepOutcome));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_inProgress() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = getStepElementParameters();
    String activityId = generateUuid();
    ActivityStatusDTO activityStatusDTO = ActivityStatusDTO.builder()
                                              .activityId(activityId)
                                              .status(ActivityVerificationStatus.IN_PROGRESS)
                                              .progressPercentage(50)
                                              .remainingTimeMs(Duration.ofMinutes(3).toMillis())
                                              .durationMs(Duration.ofMinutes(30).toMillis())
                                              .build();
    assertThatThrownBy(()
                           -> cvngStep.handleAsyncResponse(ambiance, stepElementParameters,
                               Collections.singletonMap(activityId,
                                   CVNGStep.CVNGResponseData.builder()
                                       .activityId(activityId)
                                       .activityStatusDTO(activityStatusDTO)
                                       .build())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_skip() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = getStepElementParameters();
    String activityId = generateUuid();
    StepResponse stepResponse = cvngStep.handleAsyncResponse(ambiance, stepElementParameters,
        Collections.singletonMap(activityId, CVNGStep.CVNGResponseData.builder().skip(true).build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
    assertThat(stepResponse.getFailureInfo())
        .isEqualTo(FailureInfo.newBuilder()
                       .addFailureData(FailureData.newBuilder()
                                           .setCode(ErrorCode.UNKNOWN_ERROR.name())
                                           .setLevel(io.harness.eraro.Level.INFO.name())
                                           .addFailureTypes(FailureType.SKIPPING_FAILURE)
                                           .setMessage("No monitoredServiceRef is defined for service "
                                               + serviceIdentifier + " and env " + envIdentifier + "")
                                           .build())
                       .build());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_error() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = getStepElementParameters();
    String activityId = generateUuid();
    ActivityStatusDTO activityStatusDTO = ActivityStatusDTO.builder()
                                              .activityId(activityId)
                                              .status(ActivityVerificationStatus.ERROR)
                                              .progressPercentage(50)
                                              .remainingTimeMs(Duration.ofMinutes(3).toMillis())
                                              .durationMs(Duration.ofMinutes(30).toMillis())
                                              .build();
    StepResponse stepResponse = cvngStep.handleAsyncResponse(ambiance, stepElementParameters,
        Collections.singletonMap(activityId,
            CVNGStep.CVNGResponseData.builder().activityId(activityId).activityStatusDTO(activityStatusDTO).build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo())
        .isEqualTo(FailureInfo.newBuilder()
                       .addFailureData(FailureData.newBuilder()
                                           .setCode(ErrorCode.UNKNOWN_ERROR.name())
                                           .setLevel(io.harness.eraro.Level.ERROR.name())
                                           .addFailureTypes(FailureType.UNKNOWN_FAILURE)
                                           .setMessage("Verification could not complete due to an unknown error")
                                           .build())
                       .build());
    StepOutcome stepOutcome = StepOutcome.builder()
                                  .name("output")
                                  .outcome(VerifyStepOutcome.builder()
                                               .progressPercentage(50)
                                               .estimatedRemainingTime("3 minutes")
                                               .activityId(activityId)
                                               .build())
                                  .build();
    assertThat(stepResponse.getStepOutcomes()).isEqualTo(Collections.singletonList(stepOutcome));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleProgress() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = getStepElementParameters();
    String activityId = generateUuid();
    ActivityStatusDTO activityStatusDTO = ActivityStatusDTO.builder()
                                              .activityId(activityId)
                                              .status(ActivityVerificationStatus.ERROR)
                                              .progressPercentage(50)
                                              .remainingTimeMs(Duration.ofMinutes(3).toMillis())
                                              .durationMs(Duration.ofMinutes(30).toMillis())
                                              .build();
    VerifyStepOutcome verifyStepOutcome = (VerifyStepOutcome) cvngStep.handleProgress(ambiance, stepElementParameters,
        CVNGStep.CVNGResponseData.builder().activityId(activityId).activityStatusDTO(activityStatusDTO).build());
    VerifyStepOutcome expected = VerifyStepOutcome.builder()
                                     .progressPercentage(50)
                                     .estimatedRemainingTime("3 minutes")
                                     .activityId(activityId)
                                     .build();
    assertThat(verifyStepOutcome).isEqualTo(expected);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testHandleAbort() throws IllegalAccessException {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters = getStepElementParameters();
    CVNGStepTask cvngStepTask = builderFactory.cvngStepTaskBuilder().build();
    hPersistence.save(cvngStepTask);
    ActivityService activityService = mock(ActivityService.class);
    FieldUtils.writeField(cvngStep, "activityService", activityService, true);

    cvngStep.handleAbort(ambiance, stepElementParameters,
        AsyncExecutableResponse.newBuilder().addCallbackIds(cvngStepTask.getCallbackId()).build());
    Mockito.verify(activityService).abort(cvngStepTask.getActivityId());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecuteAsync_verifyManagePerpetualTasks() {
    Ambiance ambiance = getAmbiance();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getStepElementParameters();
    AsyncExecutableResponse asyncExecutableResponse =
        cvngStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    verify(spiedDefaultVerifyStepMonitoredServiceResolutionService, times(1)).managePerpetualTasks(any(), any(), any());
  }

  private StepElementParameters getStepElementParameters() {
    TestVerificationJobSpec spec = TestVerificationJobSpec.builder()
                                       .deploymentTag(randomParameter())
                                       .duration(ParameterField.<String>builder().value("5m").build())
                                       .sensitivity(ParameterField.<String>builder().value("High").build())
                                       .build();
    return StepElementParameters.builder()
        .spec(CVNGStepParameter.builder()
                  .serviceIdentifier(ParameterField.createValueField(serviceIdentifier))
                  .envIdentifier(ParameterField.createValueField(envIdentifier))
                  .deploymentTag(spec.getDeploymentTag())
                  .sensitivity(spec.getSensitivity())
                  .spec(spec)
                  .build())
        .build();
  }
  private Ambiance getAmbiance() {
    return getAmbiance("verify");
  }

  private Ambiance getAmbiance(String verifyStepIdentifier) {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", accountId);
    setupAbstractions.put("projectIdentifier", projectIdentifier);
    setupAbstractions.put("orgIdentifier", orgIdentifier);

    return Ambiance.newBuilder()
        .setPlanExecutionId(generateUuid())
        .addLevels(Level.newBuilder()
                       .setRuntimeId(generateUuid())
                       .setStartTs(activityStartTime)
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                       .build())
        .addLevels(Level.newBuilder()
                       .setRuntimeId(generateUuid())
                       .setIdentifier(verifyStepIdentifier)
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                       .build())
        .putAllSetupAbstractions(setupAbstractions)
        .build();
  }
  private VerificationJobBuilder getVerificationJobBuilder() {
    return TestVerificationJob.builder()
        .sensitivity(RuntimeParameter.builder().value("Low").build())
        .duration(RuntimeParameter.builder().value("5m").build());
  }

  private ParameterField<String> randomParameter() {
    return ParameterField.<String>builder().value(generateUuid()).build();
  }
}
