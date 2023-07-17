/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.outcome.GcrArtifactOutcome;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.entities.SRMStepAnalysisActivity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.cvng.analysis.entities.SRMAnalysisStepExecutionDetail;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.change.SRMAnalysisStatus;
import io.harness.cvng.cdng.beans.CVNGDeploymentImpactStepParameter;
import io.harness.cvng.cdng.beans.ConfiguredMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.DefaultAndConfiguredMonitoredServiceNode;
import io.harness.cvng.cdng.beans.DefaultMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.MonitoredServiceSpecType;
import io.harness.cvng.cdng.services.api.PipelineStepMonitoredServiceResolutionService;
import io.harness.cvng.cdng.services.api.SRMAnalysisStepService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeGrpcServiceImpl;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class CVNGAnalyzeDeploymentStepTest extends CvNextGenTestBase {
  private CVNGAnalyzeDeploymentStep deploymentStep;
  @Inject private Injector injector;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private MetricPackService metricPackService;
  @Inject private ActivityService activityService;
  @Inject private ChangeSourceService changeSourceService;

  @Inject private SRMAnalysisStepService srmAnalysisStepService;
  @Inject
  private DefaultPipelineStepMonitoredServiceResolutionServiceImpl defaultVerifyStepMonitoredServiceResolutionService;

  @Inject
  private ConfiguredPipelineStepMonitoredServiceResolutionServiceImpl
      configuredPipelineStepMonitoredServiceResolutionService;

  @Mock OutcomeService outcomeService;

  private BuilderFactory builderFactory;
  private String accountId;
  private String projectIdentifier;
  private String orgIdentifier;
  private String serviceIdentifier;

  private String duration;
  private String envIdentifier;
  private MonitoredServiceDTO monitoredServiceDTO;

  private String configuredMonitoredServiceRef;

  private String configuredServiceRef;

  private String configuredEnvironmentRef;
  long activityStartTime;
  @Before
  public void setup() throws IllegalAccessException {
    deploymentStep = new CVNGAnalyzeDeploymentStep();
    Injector withPMSSDK = injector.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ExecutionSweepingOutputService.class).toInstance(mock(ExecutionSweepingOutputService.class));
        bind(OutcomeService.class).toInstance(mock(OutcomeGrpcServiceImpl.class));
      }
    });
    withPMSSDK.injectMembers(deploymentStep);
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    duration = "2D";
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    configuredMonitoredServiceRef = "service2_env2";
    configuredServiceRef = "service2";
    configuredEnvironmentRef = "env2";
    activityStartTime = builderFactory.getClock().instant().minus(Duration.ofMinutes(3)).toEpochMilli();
    Map<MonitoredServiceSpecType, PipelineStepMonitoredServiceResolutionService> verifyStepCvConfigServiceMap =
        new HashMap<>();
    DefaultPipelineStepMonitoredServiceResolutionServiceImpl spiedDefaultVerifyStepMonitoredServiceResolutionService =
        spy(defaultVerifyStepMonitoredServiceResolutionService);
    ConfiguredPipelineStepMonitoredServiceResolutionServiceImpl
        spiedConfiguredVerifyStepMonitoredServiceResolutionService =
            spy(configuredPipelineStepMonitoredServiceResolutionService);
    verifyStepCvConfigServiceMap.put(
        MonitoredServiceSpecType.DEFAULT, spiedDefaultVerifyStepMonitoredServiceResolutionService);
    verifyStepCvConfigServiceMap.put(
        MonitoredServiceSpecType.CONFIGURED, spiedConfiguredVerifyStepMonitoredServiceResolutionService);
    doReturn(
        OptionalOutcome.builder()
            .found(true)
            .outcome(
                ArtifactsOutcome.builder()
                    .primary(
                        GcrArtifactOutcome.builder().tag("tag").type(ArtifactSourceType.GCR.getDisplayName()).build())
                    .build())
            .build())
        .when(outcomeService)
        .resolveOptional(any(), any());
    FieldUtils.writeField(changeSourceService, "changeSourceUpdateHandlerMap", new HashMap<>(), true);
    FieldUtils.writeField(monitoredServiceService, "changeSourceService", changeSourceService, true);
    FieldUtils.writeField(deploymentStep, "clock", builderFactory.getClock(), true);
    FieldUtils.writeField(deploymentStep, "verifyStepCvConfigServiceMap", verifyStepCvConfigServiceMap, true);
    FieldUtils.writeField(deploymentStep, "monitoredServiceService", monitoredServiceService, true);
    FieldUtils.writeField(deploymentStep, "srmAnalysisStepService", srmAnalysisStepService, true);
    FieldUtils.writeField(deploymentStep, "activityService", activityService, true);
    FieldUtils.writeField(deploymentStep, "outcomeService", outcomeService, true);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteDefault_whenHealthSourcesAreEmpty() {
    monitoredServiceService.createDefault(
        builderFactory.getContext().getProjectParams(), serviceIdentifier, envIdentifier);
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getDefaultStepElementParameters();
    StepResponse stepResponse =
        deploymentStep.executeSyncAfterRbac(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo(String.format(
            "No healthSource is defined for monitoredServiceRef %s", monitoredServiceDTO.getIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteDefault_skipWhenMonitoredServiceDoesNotExists() {
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getDefaultStepElementParameters();
    StepResponse stepResponse =
        deploymentStep.executeSyncAfterRbac(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo(String.format("No monitoredService is defined for ref %s", monitoredServiceDTO.getIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteDefault_skipWhenMonitoredServiceIsDisabled() {
    Ambiance ambiance = getAmbiance();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getDefaultStepElementParameters();
    StepResponse stepResponse =
        deploymentStep.executeSyncAfterRbac(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo(String.format("Monitored service %s is disabled. Please enable it to run the analysis step.",
            monitoredServiceDTO.getIdentifier()));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteDefault_createActivity() {
    Ambiance ambiance = getAmbiance();
    monitoredServiceDTO.setEnabled(true);
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getDefaultStepElementParameters();
    StepResponse stepResponse =
        deploymentStep.executeSyncAfterRbac(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    CVNGAnalyzeDeploymentStep.AnalyzeDeploymentStepOutcome stepOutcome =
        (CVNGAnalyzeDeploymentStep.AnalyzeDeploymentStepOutcome) new ArrayList<>(stepResponse.getStepOutcomes())
            .get(0)
            .getOutcome();
    Activity activity = activityService.get(stepOutcome.getActivityId());
    assertThat(activity.getType()).isEqualTo(ActivityType.SRM_STEP_ANALYSIS);
    SRMStepAnalysisActivity srmStepAnalysisActivity = (SRMStepAnalysisActivity) activity;
    assertThat(srmStepAnalysisActivity.getActivityStartTime()).isEqualTo(builderFactory.getClock().instant());
    assertThat(srmStepAnalysisActivity.getPlanExecutionId()).isEqualTo(ambiance.getPlanExecutionId());
    assertThat(srmStepAnalysisActivity.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceDTO.getIdentifier());
    assertThat(srmStepAnalysisActivity.getArtifactTag()).isEqualTo("tag");
    assertThat(srmStepAnalysisActivity.getArtifactType()).isEqualTo(ArtifactSourceType.GCR.getDisplayName());
    assertThat(srmStepAnalysisActivity.getActivityName())
        .isEqualTo("SRM Step Analysis of " + monitoredServiceDTO.getIdentifier());
    SRMAnalysisStepExecutionDetail stepExecutionDetail = srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(
        srmStepAnalysisActivity.getExecutionNotificationDetailsId());
    assertThat(stepExecutionDetail.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.RUNNING);
    assertThat(stepExecutionDetail.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceDTO.getIdentifier());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteConfigured_whenHealthSourcesAreEmpty() {
    monitoredServiceService.createDefault(
        builderFactory.getContext().getProjectParams(), configuredServiceRef, configuredEnvironmentRef);
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getConfiguredStepElementParameters();
    StepResponse stepResponse =
        deploymentStep.executeSyncAfterRbac(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo(
            String.format("No healthSource is defined for monitoredServiceRef %s", configuredMonitoredServiceRef));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteConfigured_skipWhenMonitoredServiceDoesNotExists() {
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getConfiguredStepElementParameters();
    StepResponse stepResponse =
        deploymentStep.executeSyncAfterRbac(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo(String.format("No monitoredService is defined for ref %s", configuredMonitoredServiceRef));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteConfigured_skipWhenMonitoredServiceIsDisabled() {
    createConfiguredMonitoredService();
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getConfiguredStepElementParameters();
    StepResponse stepResponse =
        deploymentStep.executeSyncAfterRbac(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage())
        .isEqualTo(String.format("Monitored service %s is disabled. Please enable it to run the analysis step.",
            configuredMonitoredServiceRef));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteConfigured_createActivity() {
    createConfiguredMonitoredService();
    Ambiance ambiance = getAmbiance();
    monitoredServiceService.setHealthMonitoringFlag(
        builderFactory.getProjectParams(), configuredMonitoredServiceRef, true);
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    StepElementParameters stepElementParameters = getConfiguredStepElementParameters();
    StepResponse stepResponse =
        deploymentStep.executeSyncAfterRbac(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    CVNGAnalyzeDeploymentStep.AnalyzeDeploymentStepOutcome stepOutcome =
        (CVNGAnalyzeDeploymentStep.AnalyzeDeploymentStepOutcome) new ArrayList<>(stepResponse.getStepOutcomes())
            .get(0)
            .getOutcome();
    Activity activity = activityService.get(stepOutcome.getActivityId());
    assertThat(activity.getType()).isEqualTo(ActivityType.SRM_STEP_ANALYSIS);
    SRMStepAnalysisActivity srmStepAnalysisActivity = (SRMStepAnalysisActivity) activity;
    assertThat(srmStepAnalysisActivity.getActivityStartTime()).isEqualTo(builderFactory.getClock().instant());
    assertThat(srmStepAnalysisActivity.getPlanExecutionId()).isEqualTo(ambiance.getPlanExecutionId());
    assertThat(srmStepAnalysisActivity.getMonitoredServiceIdentifier()).isEqualTo(configuredMonitoredServiceRef);
    assertThat(srmStepAnalysisActivity.getActivityName())
        .isEqualTo("SRM Step Analysis of " + configuredMonitoredServiceRef);
    SRMAnalysisStepExecutionDetail stepExecutionDetail = srmAnalysisStepService.getSRMAnalysisStepExecutionDetail(
        srmStepAnalysisActivity.getExecutionNotificationDetailsId());
    assertThat(stepExecutionDetail.getAnalysisStatus()).isEqualTo(SRMAnalysisStatus.RUNNING);
    assertThat(stepExecutionDetail.getMonitoredServiceIdentifier()).isEqualTo(configuredMonitoredServiceRef);
  }

  private Ambiance getAmbiance() {
    return getAmbiance("srm_analysis");
  }

  private Ambiance getAmbiance(String srmAnalysisStepIdentifier) {
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
                       .setIdentifier(srmAnalysisStepIdentifier)
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                       .build())
        .putAllSetupAbstractions(setupAbstractions)
        .build();
  }

  private StepElementParameters getDefaultStepElementParameters() {
    return StepElementParameters.builder()
        .spec(CVNGDeploymentImpactStepParameter.builder()
                  .serviceIdentifier(ParameterField.createValueField(serviceIdentifier))
                  .envIdentifier(ParameterField.createValueField(envIdentifier))
                  .duration(ParameterField.createValueField(duration))
                  .monitoredService(DefaultAndConfiguredMonitoredServiceNode.builder()
                                        .type(MonitoredServiceSpecType.DEFAULT.getName())
                                        .spec(DefaultMonitoredServiceSpec.builder().build())
                                        .build())
                  .build())
        .build();
  }

  private StepElementParameters getConfiguredStepElementParameters() {
    return StepElementParameters.builder()
        .spec(CVNGDeploymentImpactStepParameter.builder()
                  .serviceIdentifier(ParameterField.createValueField(serviceIdentifier))
                  .envIdentifier(ParameterField.createValueField(envIdentifier))
                  .duration(ParameterField.createValueField(duration))
                  .monitoredService(
                      DefaultAndConfiguredMonitoredServiceNode.builder()
                          .type(MonitoredServiceSpecType.CONFIGURED.getName())
                          .spec(ConfiguredMonitoredServiceSpec.builder()
                                    .monitoredServiceRef(ParameterField.createValueField(configuredMonitoredServiceRef))
                                    .build())
                          .build())
                  .build())
        .build();
  }

  private void createConfiguredMonitoredService() {
    MonitoredServiceDTO configuredMonitoredServiceDTO = monitoredServiceDTO;
    configuredMonitoredServiceDTO.setEnvironmentRef(configuredEnvironmentRef);
    configuredMonitoredServiceDTO.setServiceRef(configuredServiceRef);
    configuredMonitoredServiceDTO.setIdentifier(configuredMonitoredServiceRef);
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceService.create(accountId, configuredMonitoredServiceDTO);
  }
}
