/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_MINUTES;
import static io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus.FAILED;
import static io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus.TIMEOUT;
import static io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType.SERVICE_GUARD_TIME_SERIES;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.NAVEEN;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.SOWMYA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.DataGenerator;
import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.DeploymentInfo;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.entities.CanaryTimeSeriesAnalysisState;
import io.harness.cvng.statemachine.entities.DeploymentLogClusterState;
import io.harness.cvng.statemachine.entities.DeploymentMetricHostSamplingState;
import io.harness.cvng.statemachine.entities.PreDeploymentLogClusterState;
import io.harness.cvng.statemachine.entities.ServiceGuardLogClusterState;
import io.harness.cvng.statemachine.entities.ServiceGuardTimeSeriesAnalysisState;
import io.harness.cvng.statemachine.entities.TimeSeriesAnalysisState;
import io.harness.cvng.statemachine.exception.AnalysisStateMachineException;
import io.harness.cvng.statemachine.services.api.AnalysisStateMachineService;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class AnalysisStateMachineServiceImplTest extends CvNextGenTestBase {
  @Inject AnalysisStateMachineService stateMachineService;
  @Inject private Clock clock;
  @Inject HPersistence hPersistence;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private CVConfigService cvConfigService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private Map<VerificationTask.TaskType, AnalysisStateMachineService> taskTypeAnalysisStateMachineServiceMap;

  private final DataGenerator dataGenerator = DataGenerator.builder().accountId(generateUuid()).build();
  private String accountId;
  private String cvConfigId;
  private String verificationTaskId;
  private String deploymentVerificationTaskId;

  private TimeSeriesAnalysisState timeSeriesAnalysisState;
  private TimeSeriesAnalysisState deploymentTimeSeriesAnalysisState;

  private BuilderFactory builderFactory;
  private String monitoredServiceIdentifier;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    AppDynamicsCVConfig appDConfig = builderFactory.appDynamicsCVConfigBuilder().build();
    CVConfig cvConfig = cvConfigService.save(appDConfig);
    accountId = builderFactory.getContext().getAccountId();
    cvConfigId = cvConfig.getUuid();
    verificationTaskId = cvConfigId;
    monitoredServiceIdentifier = "monitoredServiceIdentifier";
    createMonitoredService();
    String verificationJobInstanceId =
        verificationJobInstanceService.create(builderFactory.verificationJobInstanceBuilder()
                                                  .resolvedJob(builderFactory.getDeploymentVerificationJob())
                                                  .build());
    deploymentVerificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, DataSourceType.APP_DYNAMICS);

    timeSeriesAnalysisState = ServiceGuardTimeSeriesAnalysisState.builder().build();
    timeSeriesAnalysisState.setStatus(AnalysisStatus.CREATED);
    timeSeriesAnalysisState.setInputs(AnalysisInput.builder()
                                          .verificationTaskId(verificationTaskId)
                                          .startTime(clock.instant())
                                          .endTime(clock.instant())
                                          .build());

    deploymentTimeSeriesAnalysisState = CanaryTimeSeriesAnalysisState.builder().build();
    deploymentTimeSeriesAnalysisState.setStatus(AnalysisStatus.CREATED);
    deploymentTimeSeriesAnalysisState.setInputs(AnalysisInput.builder()
                                                    .verificationTaskId(deploymentVerificationTaskId)
                                                    .startTime(clock.instant())
                                                    .endTime(clock.instant())
                                                    .build());

    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateStateMachine_forServiceGuard() {
    AnalysisInput inputs = AnalysisInput.builder()
                               .verificationTaskId(verificationTaskId)
                               .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                               .endTime(Instant.now())
                               .build();

    AnalysisStateMachine stateMachine =
        taskTypeAnalysisStateMachineServiceMap.get(VerificationTask.TaskType.LIVE_MONITORING)
            .createStateMachine(inputs);
    assertThat(stateMachine).isNotNull();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreateStateMachine_forSLI() {
    List<String> serviceLevelIndicatorIdentifiers =
        serviceLevelIndicatorService.create(builderFactory.getProjectParams(),
            Collections.singletonList(builderFactory.getServiceLevelIndicatorDTOBuilder()), generateUuid(),
            monitoredServiceIdentifier, generateUuid());
    ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
        builderFactory.getProjectParams(), serviceLevelIndicatorIdentifiers.get(0));
    String sliId = serviceLevelIndicator.getUuid();
    verificationTaskService.createSLIVerificationTask(generateUuid(), sliId);
    AnalysisInput inputs = AnalysisInput.builder()
                               .verificationTaskId(sliId)
                               .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                               .endTime(Instant.now())
                               .build();

    AnalysisStateMachine stateMachine =
        taskTypeAnalysisStateMachineServiceMap.get(VerificationTask.TaskType.SLI).createStateMachine(inputs);
    assertThat(stateMachine).isNotNull();
    assertThat(stateMachine.getCurrentState().getType()).isEqualTo(AnalysisState.StateType.SLI_METRIC_ANALYSIS);
    assertThat(stateMachine.getStatus()).isEqualTo(AnalysisStatus.CREATED);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateStateMachine_forDeployment() {
    String verificationTaskId = generateUuid();
    String verificationJobInstanceId = generateUuid();
    VerificationTask verificationTask = VerificationTask.builder()
                                            .accountId(accountId)
                                            .taskInfo(DeploymentInfo.builder()
                                                          .verificationJobInstanceId(verificationJobInstanceId)
                                                          .cvConfigId(cvConfigId)
                                                          .build())
                                            .build();
    verificationTask.setUuid(verificationTaskId);
    hPersistence.save(verificationTask);
    VerificationJobInstance verificationJobInstance =
        VerificationJobInstance.builder()
            .deploymentStartTime(Instant.now())
            .startTime(Instant.now().plus(Duration.ofMinutes(2)))
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .build();
    verificationJobInstance.setUuid(verificationJobInstanceId);
    hPersistence.save(verificationJobInstance);
    AnalysisInput inputs = AnalysisInput.builder()
                               .verificationTaskId(verificationTaskId)
                               .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                               .endTime(Instant.now())
                               .build();

    AnalysisStateMachine stateMachine =
        taskTypeAnalysisStateMachineServiceMap.get(VerificationTask.TaskType.DEPLOYMENT).createStateMachine(inputs);
    assertThat(stateMachine).isNotNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateStateMachine_forDeployment_withTransientCvConfig() {
    String verificationTaskId = generateUuid();
    String verificationJobInstanceId = generateUuid();
    VerificationTask verificationTask = VerificationTask.builder()
                                            .accountId(accountId)
                                            .taskInfo(DeploymentInfo.builder()
                                                          .verificationJobInstanceId(verificationJobInstanceId)
                                                          .cvConfigId(cvConfigId)
                                                          .build())
                                            .build();
    verificationTask.setUuid(verificationTaskId);
    hPersistence.save(verificationTask);
    VerificationJob verificationJob = builderFactory.canaryVerificationJobBuilder().build();
    List<CVConfig> cvConfigs = verificationJob.getCvConfigs();
    if (Objects.isNull(cvConfigs)) {
      cvConfigs = new ArrayList<>();
    }
    CVConfig cvConfig = cvConfigService.get(cvConfigId);
    cvConfigs.add(cvConfig);
    verificationJob.setCvConfigs(cvConfigs);
    VerificationJobInstance verificationJobInstance = VerificationJobInstance.builder()
                                                          .deploymentStartTime(Instant.now())
                                                          .startTime(Instant.now().plus(Duration.ofMinutes(2)))
                                                          .resolvedJob(verificationJob)
                                                          .build();
    hPersistence.delete(CVConfig.class, cvConfigId);
    assertThat(cvConfigService.get(cvConfigId)).isNull();
    verificationJobInstance.setUuid(verificationJobInstanceId);
    hPersistence.save(verificationJobInstance);
    AnalysisInput inputs = AnalysisInput.builder()
                               .verificationTaskId(verificationTaskId)
                               .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                               .endTime(Instant.now())
                               .build();

    AnalysisStateMachine stateMachine =
        taskTypeAnalysisStateMachineServiceMap.get(VerificationTask.TaskType.DEPLOYMENT).createStateMachine(inputs);
    assertThat(stateMachine).isNotNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateStateMachine_logWorkflow_preDeployment() {
    CVConfig cvConfig = cvConfigService.save(builderFactory.splunkCVConfigBuilder().build());
    String verificationTaskId = generateUuid();
    String verificationJobInstanceId = generateUuid();
    VerificationTask verificationTask = VerificationTask.builder()
                                            .accountId(accountId)
                                            .taskInfo(DeploymentInfo.builder()
                                                          .verificationJobInstanceId(verificationJobInstanceId)
                                                          .cvConfigId(cvConfig.getUuid())
                                                          .build())
                                            .build();
    verificationTask.setUuid(verificationTaskId);
    hPersistence.save(verificationTask);
    VerificationJobInstance verificationJobInstance =
        VerificationJobInstance.builder()
            .deploymentStartTime(Instant.now())
            .startTime(Instant.now().plus(Duration.ofMinutes(2)))
            .resolvedJob(builderFactory.canaryVerificationJobBuilder()
                             .duration(VerificationJob.RuntimeParameter.builder().value("15m").build())
                             .build())
            .build();
    verificationJobInstance.setUuid(verificationJobInstanceId);
    hPersistence.save(verificationJobInstance);
    AnalysisInput inputs = AnalysisInput.builder()
                               .verificationTaskId(verificationTaskId)
                               .startTime(verificationJobInstance.getStartTime().minus(17, ChronoUnit.MINUTES))
                               .endTime(verificationJobInstance.getStartTime().minus(2, ChronoUnit.MINUTES))
                               .build();

    AnalysisStateMachine stateMachine =
        taskTypeAnalysisStateMachineServiceMap.get(VerificationTask.TaskType.DEPLOYMENT).createStateMachine(inputs);
    assertThat(stateMachine).isNotNull();
    assertThat(stateMachine.getCurrentState()).isInstanceOf(PreDeploymentLogClusterState.class);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateStateMachine_logWorkflow_postDeploymentCluster() {
    CVConfig cvConfig = cvConfigService.save(builderFactory.splunkCVConfigBuilder().build());
    String verificationTaskId = generateUuid();
    String verificationJobInstanceId = generateUuid();
    VerificationTask verificationTask = VerificationTask.builder()
                                            .accountId(accountId)
                                            .taskInfo(DeploymentInfo.builder()
                                                          .verificationJobInstanceId(verificationJobInstanceId)
                                                          .cvConfigId(cvConfig.getUuid())
                                                          .build())
                                            .build();
    verificationTask.setUuid(verificationTaskId);
    hPersistence.save(verificationTask);
    VerificationJobInstance verificationJobInstance =
        VerificationJobInstance.builder()
            .deploymentStartTime(Instant.now())
            .startTime(Instant.now().plus(Duration.ofMinutes(2)))
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .build();
    verificationJobInstance.setUuid(verificationJobInstanceId);
    hPersistence.save(verificationJobInstance);
    AnalysisInput inputs = AnalysisInput.builder()
                               .verificationTaskId(verificationTaskId)
                               .startTime(verificationJobInstance.getStartTime().plus(1, ChronoUnit.MINUTES))
                               .endTime(verificationJobInstance.getStartTime().plus(2, ChronoUnit.MINUTES))
                               .build();

    AnalysisStateMachine stateMachine =
        taskTypeAnalysisStateMachineServiceMap.get(VerificationTask.TaskType.DEPLOYMENT).createStateMachine(inputs);
    assertThat(stateMachine).isNotNull();
    assertThat(stateMachine.getCurrentState()).isInstanceOf(DeploymentLogClusterState.class);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testInitiateStateMachine() {
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.CREATED, verificationTaskId, timeSeriesAnalysisState);

    stateMachineService.initiateStateMachine(verificationTaskId, stateMachine);

    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedStateMachine).isNotNull();
    assertThat(savedStateMachine.getVerificationTaskId()).isEqualTo(verificationTaskId);
    assertThat(savedStateMachine.getStatus()).isEqualByComparingTo(AnalysisStatus.RUNNING);

    LearningEngineTask learningEngineTask = hPersistence.createQuery(LearningEngineTask.class).get();
    assertThat(learningEngineTask).isNotNull();
    assertThat(learningEngineTask.getAnalysisType()).isEqualByComparingTo(SERVICE_GUARD_TIME_SERIES);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testInitiateStateMachine_badStateMachine() {
    AnalysisStateMachine stateMachine = AnalysisStateMachine.builder()
                                            .verificationTaskId(verificationTaskId)
                                            .analysisStartTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                                            .analysisEndTime(Instant.now())
                                            .build();
    TimeSeriesAnalysisState timeSeriesAnalysisState = ServiceGuardTimeSeriesAnalysisState.builder().build();
    timeSeriesAnalysisState.setInputs(AnalysisInput.builder()
                                          .verificationTaskId(verificationTaskId)
                                          .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                                          .endTime(Instant.now())
                                          .build());

    stateMachine.setCurrentState(timeSeriesAnalysisState);
    assertThatThrownBy(() -> stateMachineService.initiateStateMachine(cvConfigId, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testInitiateStateMachine_badStateMachineNoFirstState() {
    AnalysisStateMachine stateMachine = AnalysisStateMachine.builder()
                                            .verificationTaskId(verificationTaskId)
                                            .analysisStartTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                                            .analysisEndTime(Instant.now())
                                            .build();

    assertThatThrownBy(() -> stateMachineService.initiateStateMachine(verificationTaskId, stateMachine))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testInitiateStateMachine_alreadyRunningStateMachine() {
    AnalysisStateMachine stateMachine = AnalysisStateMachine.builder()
                                            .verificationTaskId(verificationTaskId)
                                            .analysisStartTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                                            .analysisEndTime(Instant.now())
                                            .build();
    TimeSeriesAnalysisState timeSeriesAnalysisState = ServiceGuardTimeSeriesAnalysisState.builder().build();
    timeSeriesAnalysisState.setInputs(AnalysisInput.builder()
                                          .verificationTaskId(verificationTaskId)
                                          .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                                          .endTime(Instant.now())
                                          .build());

    stateMachine.setCurrentState(timeSeriesAnalysisState);
    stateMachine.setStatus(AnalysisStatus.RUNNING);
    hPersistence.save(stateMachine);

    AnalysisStateMachine anotherStateMachine = AnalysisStateMachine.builder()
                                                   .analysisStartTime(Instant.now())
                                                   .analysisEndTime(Instant.now().plus(5, ChronoUnit.MINUTES))
                                                   .verificationTaskId(verificationTaskId)
                                                   .build();
    timeSeriesAnalysisState.getInputs().setStartTime(Instant.now());
    timeSeriesAnalysisState.getInputs().setStartTime(Instant.now().plus(5, ChronoUnit.MINUTES));
    anotherStateMachine.setCurrentState(timeSeriesAnalysisState);

    assertThatThrownBy(() -> stateMachineService.initiateStateMachine(cvConfigId, anotherStateMachine))
        .isInstanceOf(AnalysisStateMachineException.class);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testExecuteStateMachine_currentlyRunning() {
    timeSeriesAnalysisState.setStatus(AnalysisStatus.RUNNING);
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.RUNNING, verificationTaskId, timeSeriesAnalysisState);
    stateMachineService.initiateStateMachine(cvConfigId, stateMachine);
    stateMachineService.executeStateMachine(cvConfigId);

    AnalysisStateMachine savedState = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedState).isNotNull();
    assertThat(savedState.getStatus()).isEqualByComparingTo(AnalysisStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testExecuteStateMachine_currentlyCreated() {
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.CREATED, verificationTaskId, timeSeriesAnalysisState);
    stateMachineService.initiateStateMachine(cvConfigId, stateMachine);
    stateMachineService.executeStateMachine(cvConfigId);

    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedStateMachine.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testExecuteStateMachine_currentlySuccess() {
    timeSeriesAnalysisState.setStatus(AnalysisStatus.SUCCESS);
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.RUNNING, verificationTaskId, timeSeriesAnalysisState);
    hPersistence.save(stateMachine);
    stateMachineService.executeStateMachine(cvConfigId);

    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedStateMachine.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testExecuteStateMachine_currentlyTransition() {
    ServiceGuardLogClusterState logClusterState =
        ServiceGuardLogClusterState.builder().clusterLevel(LogClusterLevel.L1).build();
    logClusterState.setInputs(AnalysisInput.builder()
                                  .verificationTaskId(verificationTaskId)
                                  .startTime(clock.instant())
                                  .endTime(clock.instant())
                                  .build());
    logClusterState.setStatus(AnalysisStatus.SUCCESS);
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.RUNNING, verificationTaskId, logClusterState);
    hPersistence.save(stateMachine);
    stateMachineService.executeStateMachine(verificationTaskId);

    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedStateMachine.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(((ServiceGuardLogClusterState) savedStateMachine.getCurrentState()).getClusterLevel())
        .isEqualByComparingTo(LogClusterLevel.L2);
    assertThat(savedStateMachine.getCurrentState().getStatus()).isEqualByComparingTo(AnalysisStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testExecuteStateMachine_currentlyRetryTransitionedToRunning() {
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.CREATED, verificationTaskId, timeSeriesAnalysisState);
    stateMachineService.initiateStateMachine(verificationTaskId, stateMachine);
    LearningEngineTask task = hPersistence.createQuery(LearningEngineTask.class).get();
    task.setTaskStatus(FAILED);
    hPersistence.save(task);
    stateMachineService.executeStateMachine(cvConfigId);

    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedStateMachine.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testExecuteStateMachine_currentlyRetryIsRetriedMarkedIgnoredForServiceGuard() {
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.CREATED, verificationTaskId, timeSeriesAnalysisState);
    stateMachineService.initiateStateMachine(verificationTaskId, stateMachine);
    stateMachine.getCurrentState().setRetryCount(3);
    hPersistence.save(stateMachine);
    LearningEngineTask task = hPersistence.createQuery(LearningEngineTask.class).get();
    task.setTaskStatus(FAILED);
    hPersistence.save(task);
    stateMachine.getCurrentState().setStatus(AnalysisStatus.RETRY);
    hPersistence.save(stateMachine);

    stateMachineService.executeStateMachine(verificationTaskId);
    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedStateMachine.getStatus().name()).isEqualTo(AnalysisStatus.IGNORED.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testExecuteStateMachine_timedOUtTasksAreFailedAfterRetryForDeployment() {
    String deploymentVerificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, generateUuid(), DataSourceType.APP_DYNAMICS);
    AnalysisStateMachine stateMachine = dataGenerator.buildStateMachine(
        AnalysisStatus.CREATED, deploymentVerificationTaskId, deploymentTimeSeriesAnalysisState);
    stateMachineService.initiateStateMachine(deploymentVerificationTaskId, stateMachine);
    stateMachine.getCurrentState().setRetryCount(3);
    hPersistence.save(stateMachine);
    LearningEngineTask task = hPersistence.createQuery(LearningEngineTask.class).get();
    task.setTaskStatus(TIMEOUT);
    hPersistence.save(task);
    stateMachine.getCurrentState().setStatus(AnalysisStatus.RETRY);
    hPersistence.save(stateMachine);

    stateMachineService.executeStateMachine(deploymentVerificationTaskId);
    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedStateMachine.getStatus().name()).isEqualTo(AnalysisStatus.FAILED.name());
    assertThat(savedStateMachine.getCurrentState().getRetryCount()).isEqualTo(3);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testRetryStateMachineAfterFailure_notYetTimeToRetry() {
    Instant nextAttemptTime = Instant.now().plus(5, ChronoUnit.MINUTES);
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.RETRY, verificationTaskId, timeSeriesAnalysisState);
    stateMachine.setNextAttemptTime(nextAttemptTime.toEpochMilli());
    hPersistence.save(stateMachine);
    stateMachineService.retryStateMachineAfterFailure(stateMachine);

    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    stateMachine.setAnalysisStartTime(stateMachine.getAnalysisStartTime().truncatedTo(ChronoUnit.MILLIS));
    stateMachine.setAnalysisEndTime(stateMachine.getAnalysisEndTime().truncatedTo(ChronoUnit.MILLIS));
    assertThat(savedStateMachine).isEqualTo(stateMachine);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testRetryStateMachineAfterFailure_executeRetry() {
    Instant nextAttemptTime = Instant.now().minus(5, ChronoUnit.MINUTES);
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.RETRY, verificationTaskId, timeSeriesAnalysisState);
    stateMachine.setNextAttemptTime(nextAttemptTime.toEpochMilli());
    stateMachine.getCurrentState().setStatus(AnalysisStatus.FAILED);
    hPersistence.save(stateMachine);

    stateMachineService.retryStateMachineAfterFailure(stateMachine);

    AnalysisStateMachine stateMachineFromDB = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(stateMachineFromDB).isNotNull();
    assertThat(stateMachineFromDB.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testIgnoreOldStateMachine_ignoreTrue() {
    Instant currentTime = clock.instant();
    AnalysisStateMachine analysisStateMachine =
        AnalysisStateMachine.builder()
            .accountId(accountId)
            .verificationTaskId(verificationTaskId)
            .analysisStartTime(currentTime.minus(STATE_MACHINE_IGNORE_MINUTES + 10, ChronoUnit.MINUTES))
            .analysisEndTime(currentTime.minus(STATE_MACHINE_IGNORE_MINUTES + 5, ChronoUnit.MINUTES))
            .build();

    Optional<AnalysisStateMachine> ignoredStateMachine =
        stateMachineService.ignoreOldStateMachine(analysisStateMachine);
    assertThat(ignoredStateMachine).isPresent();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testIgnoreOldStateMachine_ignoreFalse() {
    AnalysisStateMachine analysisStateMachine =
        AnalysisStateMachine.builder().analysisStartTime(clock.instant()).analysisEndTime(clock.instant()).build();
    Optional<AnalysisStateMachine> ignoredStateMachine =
        stateMachineService.ignoreOldStateMachine(analysisStateMachine);
    assertThat(ignoredStateMachine).isNotPresent();
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testCreateStateMachine_forDeployment_withHostSampling() throws IllegalAccessException {
    FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
    FieldUtils.writeField(taskTypeAnalysisStateMachineServiceMap.get(VerificationTask.TaskType.DEPLOYMENT),
        "featureFlagService", featureFlagService, true);
    String verificationTaskId = generateUuid();
    String verificationJobInstanceId = generateUuid();
    VerificationTask verificationTask = VerificationTask.builder()
                                            .accountId(accountId)
                                            .taskInfo(DeploymentInfo.builder()
                                                          .verificationJobInstanceId(verificationJobInstanceId)
                                                          .cvConfigId(cvConfigId)
                                                          .build())
                                            .build();
    verificationTask.setUuid(verificationTaskId);
    hPersistence.save(verificationTask);
    VerificationJobInstance verificationJobInstance =
        VerificationJobInstance.builder()
            .deploymentStartTime(Instant.now())
            .startTime(Instant.now().plus(Duration.ofMinutes(2)))
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .build();
    verificationJobInstance.setUuid(verificationJobInstanceId);
    hPersistence.save(verificationJobInstance);
    AnalysisInput inputs = AnalysisInput.builder()
                               .verificationTaskId(verificationTaskId)
                               .startTime(Instant.now().minus(5, ChronoUnit.MINUTES))
                               .endTime(Instant.now())
                               .build();

    AnalysisStateMachine stateMachine =
        taskTypeAnalysisStateMachineServiceMap.get(VerificationTask.TaskType.DEPLOYMENT).createStateMachine(inputs);
    assertEquals(stateMachine.getCurrentState().getClass(), DeploymentMetricHostSamplingState.class);
    assertThat(stateMachine).isNotNull();
  }
}
