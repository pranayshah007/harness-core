/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_LIMIT;
import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_MINUTES;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.SOWMYA;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.DataGenerator;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskKeys;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.TimeSeriesCanaryLearningEngineTask;
import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.jobs.StateMachineEventPublisherService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLOService;
import io.harness.cvng.servicelevelobjective.services.api.SLIConsecutiveMinutesProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.SLIDataUnavailabilityInstancesHandlerService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLIMetricAnalysisTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorEntityAndDTOTransformer;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisOrchestratorStatus;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator.AnalysisOrchestratorKeys;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine.AnalysisStateMachineKeys;
import io.harness.cvng.statemachine.entities.CanaryTimeSeriesAnalysisState;
import io.harness.cvng.statemachine.entities.ServiceGuardTimeSeriesAnalysisState;
import io.harness.cvng.statemachine.entities.TimeSeriesAnalysisState;
import io.harness.cvng.statemachine.services.api.AnalysisStateExecutor;
import io.harness.cvng.statemachine.services.api.AnalysisStateMachineService;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.cvng.statemachine.services.api.SLIMetricAnalysisStateExecutor;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.metrics.service.api.MetricService;
import io.harness.persistence.HPersistence;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class OrchestrationServiceImplTest extends CvNextGenTestBase {
  @Inject HPersistence hPersistence;

  @Inject private MetricPackService metricPackService;
  @Inject AnalysisStateMachineService analysisStateMachineService;
  @Inject OrchestrationService orchestrationService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private DeploymentTimeSeriesAnalysisService deploymentTimeSeriesAnalysisService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;

  @Spy private StateMachineEventPublisherService stateMachineEventPublisherService;
  private BuilderFactory builderFactory;
  private String cvConfigId;
  private String verificationTaskId;
  private String accountId;
  private DataGenerator dataGenerator;
  private TimeSeriesAnalysisState timeSeriesAnalysisState;
  @Spy @Inject private SLIDataUnavailabilityInstancesHandlerService sliDataUnavailabilityInstancesHandlerService;
  @Spy @Inject private SLIDataProcessorService sliDataProcessorService;
  @Spy @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Spy @Inject private VerificationTaskService verificationTaskService;
  @Spy @Inject private SLIRecordService sliRecordService;
  @Spy @Inject private ServiceLevelIndicatorEntityAndDTOTransformer serviceLevelIndicatorEntityAndDTOTransformer;
  @Spy @Inject private TimeSeriesRecordService timeSeriesRecordService;
  @Spy @Inject private SLIMetricAnalysisTransformer sliMetricAnalysisTransformer;
  @Spy @Inject private SLOHealthIndicatorService sloHealthIndicatorService;
  @Spy @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Spy @Inject private SLIConsecutiveMinutesProcessorService sliConsecutiveMinutesProcessorService;
  @Spy @Inject private MetricService metricService;
  @Spy @Inject private EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;
  @Spy @Inject private CompositeSLOService compositeSLOService;

  @Spy @Inject private Clock clock;

  @Spy @InjectMocks private SLIMetricAnalysisStateExecutor analysisStateExecutorMock;

  // Inject the mock object into the map
  @InjectMocks
  private Map<AnalysisState.StateType, AnalysisStateExecutor> stateTypeAnalysisStateExecutorMap = new HashMap<>();

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    builderFactory = BuilderFactory.getDefault();
    cvConfigId = generateUuid();
    accountId = generateUuid();
    CVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setVerificationType(VerificationType.TIME_SERIES);
    cvConfig.setUuid(cvConfigId);
    cvConfig.setAccountId(accountId);
    hPersistence.save(cvConfig);
    dataGenerator = DataGenerator.builder().accountId(accountId).build();
    verificationTaskId =
        verificationTaskService.createLiveMonitoringVerificationTask(accountId, cvConfigId, cvConfig.getType());
    timeSeriesAnalysisState = ServiceGuardTimeSeriesAnalysisState.builder().build();
    timeSeriesAnalysisState.setStatus(AnalysisStatus.CREATED);
    timeSeriesAnalysisState.setInputs(AnalysisInput.builder()
                                          .verificationTaskId(verificationTaskId)
                                          .startTime(clock.instant().minus(Duration.ofMinutes(5)))
                                          .endTime(clock.instant())
                                          .build());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testQueueAnalysis_firstEverOrchestration() {
    AnalysisOrchestrator orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                                            .filter(AnalysisOrchestratorKeys.verificationTaskId, cvConfigId)
                                            .get();

    assertThat(orchestrator).isNull();
    orchestrationService.queueAnalysis(AnalysisInput.builder()
                                           .verificationTaskId(cvConfigId)
                                           .startTime(clock.instant())
                                           .endTime(clock.instant().minus(5, ChronoUnit.MINUTES))
                                           .build());

    orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                       .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
                       .get();

    assertThat(orchestrator).isNotNull();
    assertThat(orchestrator.getUuid()).isNotNull();
    assertThat(orchestrator.getStatus().name()).isEqualTo(AnalysisOrchestratorStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testQueueAnalysis_setStatusBackToRunnning() {
    Instant startTime = clock.instant();
    AnalysisOrchestrator orchestrator = orchestrationService.getAnalysisOrchestrator(verificationTaskId);
    assertThat(orchestrator).isNull();
    orchestrationService.queueAnalysis(AnalysisInput.builder()
                                           .verificationTaskId(verificationTaskId)
                                           .endTime(startTime)
                                           .startTime(startTime.minus(5, ChronoUnit.MINUTES))
                                           .build());

    for (int i = 0; i < 9; i++) {
      updateLearningEngineTask();
      orchestrator = orchestrationService.getAnalysisOrchestrator(verificationTaskId);
      orchestrationService.orchestrate(orchestrator);
      incrementAnalysisStateMachineClockBy5Hours();
    }
    assertThat(orchestrationService.getAnalysisOrchestrator(verificationTaskId).getStatus())
        .isEqualTo(AnalysisOrchestratorStatus.WAITING);
    orchestrationService.queueAnalysis(AnalysisInput.builder()
                                           .verificationTaskId(verificationTaskId)
                                           .startTime(startTime)
                                           .endTime(startTime.minus(5, ChronoUnit.MINUTES))
                                           .build());
    assertThat(orchestrationService.getAnalysisOrchestrator(verificationTaskId).getStatus())
        .isEqualTo(AnalysisOrchestratorStatus.RUNNING);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testQueueAnalysis_multiple() {
    for (Instant startTime = clock.instant(); startTime.isBefore(clock.instant().plus(Duration.ofMinutes(30)));
         startTime = startTime.plus(Duration.ofMinutes(5))) {
      orchestrationService.queueAnalysis(AnalysisInput.builder()
                                             .verificationTaskId(verificationTaskId)
                                             .startTime(startTime)
                                             .endTime(startTime.plus(5, ChronoUnit.MINUTES))
                                             .build());
    }
    List<AnalysisOrchestrator> orchestrator =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
            .asList();
    assertThat(orchestrator).hasSize(1);
    assertThat(orchestrator.get(0).getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(orchestrator.get(0).getAnalysisStateMachineQueue()).hasSize(6);
    orchestrationService.orchestrate(orchestrator.get(0));
    for (Instant startTime = clock.instant(); startTime.isBefore(clock.instant().plus(Duration.ofMinutes(15)));
         startTime = startTime.plus(Duration.ofMinutes(5))) {
      orchestrationService.queueAnalysis(AnalysisInput.builder()
                                             .verificationTaskId(verificationTaskId)
                                             .startTime(startTime)
                                             .endTime(startTime.plus(5, ChronoUnit.MINUTES))
                                             .build());
    }
    orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                       .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
                       .asList();
    assertThat(orchestrator).hasSize(1);
    assertThat(orchestrator.get(0).getStatus().name()).isEqualTo(AnalysisOrchestratorStatus.RUNNING.name());
    assertThat(orchestrator.get(0).getAnalysisStateMachineQueue()).hasSize(8);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testRetryLogic() {
    Instant startTime = clock.instant();
    orchestrationService.queueAnalysis(AnalysisInput.builder()
                                           .verificationTaskId(verificationTaskId)
                                           .startTime(startTime.minus(5, ChronoUnit.MINUTES))
                                           .endTime(startTime)
                                           .build());
    orchestrationService.queueAnalysis(AnalysisInput.builder()
                                           .verificationTaskId(verificationTaskId)
                                           .startTime(startTime)
                                           .endTime(startTime.plus(5, ChronoUnit.MINUTES))
                                           .build());

    List<AnalysisOrchestrator> orchestrator =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
            .asList();
    assertThat(orchestrator).hasSize(1);
    assertThat(orchestrator.get(0).getStatus().name()).isEqualTo(AnalysisOrchestratorStatus.RUNNING.name());
    assertThat(orchestrator.get(0).getAnalysisStateMachineQueue()).hasSize(2);
    orchestrationService.orchestrate(orchestrator.get(0));

    for (int i = 0; i < 8; i++) {
      updateLearningEngineTask();
      orchestrationService.orchestrate(orchestrator.get(0));
      incrementAnalysisStateMachineClockBy5Hours();
    }
    List<AnalysisStateMachine> analysisStateMachines =
        hPersistence.createQuery(AnalysisStateMachine.class)
            .filter(AnalysisStateMachineKeys.verificationTaskId, verificationTaskId)
            .asList();
    assertThat(analysisStateMachines).hasSize(2);
    assertThat(analysisStateMachines.stream()
                   .filter(analysisStateMachine -> analysisStateMachine.getStatus().equals(AnalysisStatus.IGNORED))
                   .filter(analysisStateMachine -> analysisStateMachine.getCurrentState().getRetryCount() == 2))
        .isNotEmpty();
    orchestrationService.queueAnalysis(AnalysisInput.builder()
                                           .verificationTaskId(verificationTaskId)
                                           .startTime(startTime.plus(1, ChronoUnit.DAYS))
                                           .endTime(startTime.plus(2, ChronoUnit.DAYS))
                                           .build());
    orchestrationService.orchestrate(orchestrationService.getAnalysisOrchestrator(verificationTaskId));
    analysisStateMachines = hPersistence.createQuery(AnalysisStateMachine.class)
                                .filter(AnalysisStateMachineKeys.verificationTaskId, verificationTaskId)
                                .asList();
    assertThat(analysisStateMachines).hasSize(3);
    // validate retry count is propogated
    analysisStateMachines.forEach(analysisStateMachine -> assertThat(analysisStateMachine.getTotalRetryCount() == 2));
  }

  private void updateLearningEngineTask() {
    UpdateOperations<LearningEngineTask> updateOperations =
        hPersistence.createUpdateOperations(LearningEngineTask.class)
            .set(LearningEngineTaskKeys.taskStatus, LearningEngineTask.ExecutionStatus.TIMEOUT);
    Query<LearningEngineTask> timeoutQuery = hPersistence.createQuery(LearningEngineTask.class);
    hPersistence.update(timeoutQuery, updateOperations);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testQueueAnalysis_firstEverOrchestrationInvalidInputs() {
    AnalysisOrchestrator orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                                            .filter(AnalysisOrchestratorKeys.verificationTaskId, cvConfigId)
                                            .get();

    assertThat(orchestrator).isNull();

    assertThatThrownBy(()
                           -> orchestrationService.queueAnalysis(AnalysisInput.builder()
                                                                     .verificationTaskId(verificationTaskId)
                                                                     .startTime(clock.instant())
                                                                     .endTime(null)
                                                                     .build()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testOrchestrate_currentStateMachineDoneNothingNewToExecute() {
    AnalysisOrchestrator orchestrator = AnalysisOrchestrator.builder()
                                            .verificationTaskId(cvConfigId)
                                            .status(AnalysisOrchestratorStatus.RUNNING)
                                            .build();
    hPersistence.save(orchestrator);
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.SUCCESS, verificationTaskId, timeSeriesAnalysisState);
    hPersistence.save(stateMachine);
    // createDeploymentTimeSeriesAnalysisRecords();
    orchestrate(verificationTaskId);

    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedStateMachine).isNotNull();
    assertThat(savedStateMachine.getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
    AnalysisOrchestrator savedOrchestrator = hPersistence.createQuery(AnalysisOrchestrator.class).get();
    assertThat(savedOrchestrator).isNotNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testOrchestrate_currentStateMachineDoneExecuteNext() {
    AnalysisOrchestrator orchestrator = AnalysisOrchestrator.builder()
                                            .verificationTaskId(cvConfigId)
                                            .status(AnalysisOrchestratorStatus.RUNNING)
                                            .analysisStateMachineQueue(new ArrayList<>())
                                            .build();

    hPersistence.save(orchestrator);
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.SUCCESS, verificationTaskId, timeSeriesAnalysisState);
    hPersistence.save(stateMachine);

    AnalysisStateMachine nextStateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.CREATED, verificationTaskId, timeSeriesAnalysisState);
    orchestrator.getAnalysisStateMachineQueue().add(nextStateMachine);
    hPersistence.save(orchestrator);

    orchestrate(verificationTaskId);

    List<AnalysisStateMachine> savedStateMachines = hPersistence.createQuery(AnalysisStateMachine.class).asList();
    assertThat(savedStateMachines.size()).isEqualTo(2);
    assertThat(savedStateMachines.get(0).getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
    assertThat(savedStateMachines.get(1).getStatus()).isEqualTo(AnalysisStatus.RUNNING);
    AnalysisOrchestrator savedOrchestrator = hPersistence.createQuery(AnalysisOrchestrator.class).get();
    assertThat(savedOrchestrator).isNotNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testOrchestrate_currentStateMachineRunningWithCompletedTasks() {
    AnalysisOrchestrator orchestrator = AnalysisOrchestrator.builder()
                                            .verificationTaskId(cvConfigId)
                                            .status(AnalysisOrchestratorStatus.RUNNING)
                                            .analysisStateMachineQueue(new ArrayList<>())
                                            .build();

    hPersistence.save(orchestrator);
    timeSeriesAnalysisState.setStatus(AnalysisStatus.SUCCESS);
    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.RUNNING, verificationTaskId, timeSeriesAnalysisState);
    hPersistence.save(stateMachine);

    AnalysisStateMachine nextStateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.CREATED, verificationTaskId, timeSeriesAnalysisState);
    orchestrator.getAnalysisStateMachineQueue().add(nextStateMachine);
    hPersistence.save(orchestrator);

    orchestrate(verificationTaskId);

    List<AnalysisStateMachine> savedStateMachines = hPersistence.createQuery(AnalysisStateMachine.class).asList();
    assertThat(savedStateMachines.size()).isEqualTo(2);
    assertThat(savedStateMachines.get(0).getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
    assertThat(savedStateMachines.get(1).getStatus()).isEqualTo(AnalysisStatus.RUNNING);
    AnalysisOrchestrator savedOrchestrator = hPersistence.createQuery(AnalysisOrchestrator.class).get();
    assertThat(savedOrchestrator).isNotNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testOrchestrate_currentlyRunning() {
    AnalysisOrchestrator orchestrator = AnalysisOrchestrator.builder()
                                            .verificationTaskId(cvConfigId)
                                            .status(AnalysisOrchestratorStatus.RUNNING)
                                            .build();
    hPersistence.save(orchestrator);

    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.RUNNING, verificationTaskId, timeSeriesAnalysisState);
    analysisStateMachineService.initiateStateMachine(verificationTaskId, stateMachine);
    orchestrate(verificationTaskId);

    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedStateMachine).isNotNull();
    assertThat(savedStateMachine.getStatus()).isEqualByComparingTo(AnalysisStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testOrchestrate_failed() {
    AnalysisOrchestrator orchestrator = AnalysisOrchestrator.builder()
                                            .verificationTaskId(verificationTaskId)
                                            .status(AnalysisOrchestratorStatus.RUNNING)
                                            .build();
    hPersistence.save(orchestrator);

    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.FAILED, verificationTaskId, timeSeriesAnalysisState);
    analysisStateMachineService.initiateStateMachine(verificationTaskId, stateMachine);
    stateMachine.setStatus(AnalysisStatus.FAILED);
    stateMachine.getCurrentState().setStatus(AnalysisStatus.FAILED);
    hPersistence.save(stateMachine);
    orchestrate(verificationTaskId);
    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedStateMachine).isNotNull();
    assertThat(savedStateMachine.getStatus()).isEqualByComparingTo(AnalysisStatus.FAILED);
    assertThat(orchestrationService.getAnalysisOrchestrator(verificationTaskId).getStatus())
        .isEqualTo(AnalysisOrchestratorStatus.COMPLETED);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testOrchestrate_timeout() {
    AnalysisOrchestrator orchestrator = AnalysisOrchestrator.builder()
                                            .verificationTaskId(cvConfigId)
                                            .status(AnalysisOrchestratorStatus.RUNNING)
                                            .build();
    hPersistence.save(orchestrator);

    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.CREATED, verificationTaskId, timeSeriesAnalysisState);
    analysisStateMachineService.initiateStateMachine(verificationTaskId, stateMachine);
    stateMachine.setStatus(AnalysisStatus.TIMEOUT);
    stateMachine.getCurrentState().setStatus(AnalysisStatus.TIMEOUT);
    hPersistence.save(stateMachine);

    orchestrate(verificationTaskId);

    AnalysisStateMachine savedStateMachine = hPersistence.createQuery(AnalysisStateMachine.class).get();
    assertThat(savedStateMachine).isNotNull();
    assertThat(savedStateMachine.getStatus()).isEqualByComparingTo(AnalysisStatus.RUNNING);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testOrchestrate_ignoreStateMachine() {
    AnalysisOrchestrator orchestrator = AnalysisOrchestrator.builder()
                                            .verificationTaskId(cvConfigId)
                                            .status(AnalysisOrchestratorStatus.RUNNING)
                                            .analysisStateMachineQueue(new ArrayList<>())
                                            .build();

    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.SUCCESS, verificationTaskId, timeSeriesAnalysisState);
    hPersistence.save(stateMachine);

    AnalysisStateMachine firstSM =
        dataGenerator.buildStateMachine(AnalysisStatus.CREATED, verificationTaskId, timeSeriesAnalysisState);
    firstSM.setAnalysisStartTime(clock.instant().minus(STATE_MACHINE_IGNORE_MINUTES + 20, ChronoUnit.MINUTES));
    firstSM.setAnalysisEndTime(clock.instant().minus(STATE_MACHINE_IGNORE_MINUTES + 10, ChronoUnit.MINUTES));
    orchestrator.getAnalysisStateMachineQueue().add(firstSM);

    AnalysisStateMachine nextSM =
        dataGenerator.buildStateMachine(AnalysisStatus.CREATED, verificationTaskId, timeSeriesAnalysisState);
    orchestrator.getAnalysisStateMachineQueue().add(nextSM);
    hPersistence.save(orchestrator);

    orchestrate(verificationTaskId);

    List<AnalysisStateMachine> savedStateMachines = hPersistence.createQuery(AnalysisStateMachine.class).asList();
    assertThat(savedStateMachines.size()).isEqualTo(3);
    assertThat(savedStateMachines.get(0).getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
    assertThat(savedStateMachines.get(1).getStatus()).isEqualTo(AnalysisStatus.IGNORED);
    assertThat(savedStateMachines.get(2).getStatus()).isEqualTo(AnalysisStatus.RUNNING);
    AnalysisOrchestrator savedOrchestrator = hPersistence.createQuery(AnalysisOrchestrator.class).get();
    assertThat(savedOrchestrator).isNotNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testOrchestrate_ignoreStateMachine100OldOnes() {
    AnalysisOrchestrator orchestrator = AnalysisOrchestrator.builder()
                                            .verificationTaskId(cvConfigId)
                                            .status(AnalysisOrchestratorStatus.RUNNING)
                                            .analysisStateMachineQueue(new ArrayList<>())
                                            .build();

    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.SUCCESS, verificationTaskId, timeSeriesAnalysisState);
    hPersistence.save(stateMachine);

    List<AnalysisStateMachine> ignoreList = new ArrayList<>();

    for (int i = 0; i < STATE_MACHINE_IGNORE_LIMIT + 10; i++) {
      AnalysisStateMachine firstSM =
          dataGenerator.buildStateMachine(AnalysisStatus.CREATED, verificationTaskId, timeSeriesAnalysisState);
      firstSM.setAnalysisStartTime(clock.instant().minus(STATE_MACHINE_IGNORE_MINUTES + 20, ChronoUnit.MINUTES));
      firstSM.setAnalysisEndTime(clock.instant().minus(STATE_MACHINE_IGNORE_MINUTES + 10, ChronoUnit.MINUTES));
      orchestrator.getAnalysisStateMachineQueue().add(firstSM);
      if (i < STATE_MACHINE_IGNORE_LIMIT) {
        ignoreList.add(firstSM);
      }
    }
    hPersistence.save(orchestrator);

    orchestrate(verificationTaskId);
    AnalysisOrchestrator orchestratorFromDB = hPersistence.createQuery(AnalysisOrchestrator.class).get();
    assertThat(orchestratorFromDB.getAnalysisStateMachineQueue().size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testOrchestrate_ignoreStateMachinesAtCreatedState() {
    AnalysisOrchestrator orchestrator = AnalysisOrchestrator.builder()
                                            .verificationTaskId(cvConfigId)
                                            .status(AnalysisOrchestratorStatus.CREATED)
                                            .analysisStateMachineQueue(new ArrayList<>())
                                            .build();

    AnalysisStateMachine stateMachine =
        dataGenerator.buildStateMachine(AnalysisStatus.SUCCESS, verificationTaskId, timeSeriesAnalysisState);
    hPersistence.save(stateMachine);

    List<AnalysisStateMachine> ignoreList = new ArrayList<>();

    for (int i = 0; i < STATE_MACHINE_IGNORE_LIMIT - 10; i++) {
      AnalysisStateMachine firstSM =
          dataGenerator.buildStateMachine(AnalysisStatus.CREATED, verificationTaskId, timeSeriesAnalysisState);
      firstSM.setAnalysisStartTime(clock.instant().minus(STATE_MACHINE_IGNORE_MINUTES + 20, ChronoUnit.MINUTES));
      firstSM.setAnalysisEndTime(clock.instant().minus(STATE_MACHINE_IGNORE_MINUTES + 10, ChronoUnit.MINUTES));
      orchestrator.getAnalysisStateMachineQueue().add(firstSM);
      if (i < STATE_MACHINE_IGNORE_LIMIT) {
        ignoreList.add(firstSM);
      }
    }
    hPersistence.save(orchestrator);

    orchestrate(cvConfigId);
    AnalysisOrchestrator orchestratorFromDB = hPersistence.createQuery(AnalysisOrchestrator.class).get();
    assertThat(orchestratorFromDB.getAnalysisStateMachineQueue()).isEmpty();
    assertThat(orchestratorFromDB.getStatus()).isEqualTo(AnalysisOrchestratorStatus.WAITING);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateAllFieldsOnUpsert() {
    AnalysisOrchestrator orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                                            .filter(AnalysisOrchestratorKeys.verificationTaskId, cvConfigId)
                                            .get();

    assertThat(orchestrator).isNull();

    orchestrationService.queueAnalysis(AnalysisInput.builder()
                                           .verificationTaskId(verificationTaskId)
                                           .startTime(clock.instant())
                                           .endTime(clock.instant().minus(5, ChronoUnit.MINUTES))
                                           .build());
    AnalysisOrchestrator dbOrchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                                              .filter(AnalysisOrchestratorKeys.verificationTaskId, cvConfigId)
                                              .get();

    Set<String> nullableFields = Sets.newHashSet();
    nullableFields.add(AnalysisOrchestratorKeys.analysisOrchestrationIteration);

    List<Field> fields = ReflectionUtils.getAllDeclaredAndInheritedFields(AnalysisOrchestrator.class);
    fields.stream().filter(field -> !nullableFields.contains(field.getName())).forEach(field -> {
      try {
        field.setAccessible(true);
        assertThat(field.get(dbOrchestrator)).withFailMessage("field %s is null", field.getName()).isNotNull();
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testQueueAnalysis_forSLIVerificationTask() {
    createMonitoredService();
    List<String> serviceLevelIndicatorIdentifiers =
        serviceLevelIndicatorService.create(builderFactory.getProjectParams(),
            Collections.singletonList(builderFactory.getServiceLevelIndicatorDTOBuilder()), generateUuid(),
            builderFactory.getContext().getMonitoredServiceIdentifier(), generateUuid());
    ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
        builderFactory.getProjectParams(), serviceLevelIndicatorIdentifiers.get(0));
    String sliId = serviceLevelIndicator.getUuid();

    AnalysisOrchestrator orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                                            .filter(AnalysisOrchestratorKeys.verificationTaskId, sliId)
                                            .get();

    assertThat(orchestrator).isNull();
    orchestrationService.queueAnalysis(AnalysisInput.builder()
                                           .verificationTaskId(sliId)
                                           .startTime(clock.instant())
                                           .endTime(clock.instant().minus(5, ChronoUnit.MINUTES))
                                           .build());

    orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                       .filter(AnalysisOrchestratorKeys.verificationTaskId, sliId)
                       .get();

    assertThat(orchestrator).isNotNull();
    assertThat(orchestrator.getUuid()).isNotNull();
    assertThat(orchestrator.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(orchestrator.getAnalysisStateMachineQueue().get(0).getCurrentState().getType())
        .isEqualTo(AnalysisState.StateType.SLI_METRIC_ANALYSIS);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testQueueAnalysisWithRestore_forSLIVerificationTask() throws IllegalAccessException {
    createMonitoredService();
    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    SimpleServiceLevelObjective serviceLevelObjective =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());
    ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(
        builderFactory.getProjectParams(), serviceLevelObjective.getServiceLevelIndicators().get(0));
    String sliId = serviceLevelIndicator.getUuid();

    AnalysisOrchestrator orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                                            .filter(AnalysisOrchestratorKeys.verificationTaskId, sliId)
                                            .get();

    assertThat(orchestrator).isNull();
    orchestrationService.queueAnalysis(AnalysisInput.builder()
                                           .verificationTaskId(sliId)
                                           .startTime(clock.instant())
                                           .endTime(clock.instant().minus(5, ChronoUnit.MINUTES))
                                           .isSLORestoreTask(true)
                                           .build());

    orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                       .filter(AnalysisOrchestratorKeys.verificationTaskId, sliId)
                       .get();

    assertThat(orchestrator).isNotNull();
    assertThat(orchestrator.getUuid()).isNotNull();
    assertThat(orchestrator.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(orchestrator.getAnalysisStateMachineQueue().get(0).getCurrentState().getType())
        .isEqualTo(AnalysisState.StateType.SLI_METRIC_ANALYSIS);
    orchestrationService.orchestrate(orchestrator);
    stateTypeAnalysisStateExecutorMap.put(AnalysisState.StateType.SLI_METRIC_ANALYSIS, analysisStateExecutorMock);
    doCallRealMethod().when(analysisStateExecutorMock).getExecutionStatus(any());
    doCallRealMethod().when(analysisStateExecutorMock).execute(any());
    FieldUtils.writeField(
        analysisStateMachineService, "stateTypeAnalysisStateExecutorMap", stateTypeAnalysisStateExecutorMap, true);
    FieldUtils.writeField(orchestrationService, "stateMachineService", analysisStateMachineService, true);
    orchestrationService.orchestrate(orchestrator);
    orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                       .filter(AnalysisOrchestratorKeys.verificationTaskId, sliId)
                       .get();
    assertThat(orchestrator.getStatus().name()).isEqualTo(AnalysisOrchestratorStatus.WAITING.name());
    AnalysisStateMachine stateMachine = hPersistence.createQuery(AnalysisStateMachine.class)
                                            .filter(AnalysisStateMachineKeys.verificationTaskId, sliId)
                                            .get();
    assertThat(stateMachine.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
    verify(analysisStateExecutorMock).handleFinalStatuses(any());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testQueueAnalysis_forCompositeSLOVerificationTask() throws IllegalAccessException {
    FieldUtils.writeField(
        orchestrationService, "stateMachineEventPublisherService", stateMachineEventPublisherService, true);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), getCompositeSLODTO());
    String sloId =
        serviceLevelObjectiveV2Service.getEntity(builderFactory.getProjectParams(), "compositeSloIdentifier").getUuid();
    AnalysisOrchestrator orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                                            .filter(AnalysisOrchestratorKeys.verificationTaskId, sloId)
                                            .get();

    assertThat(orchestrator).isNotNull();
    assertThat(orchestrator.getUuid()).isNotNull();
    assertThat(orchestrator.getStatus().name()).isEqualTo(AnalysisStatus.RUNNING.name());
    assertThat(orchestrator.getAnalysisStateMachineQueue().get(0).getCurrentState().getType())
        .isEqualTo(AnalysisState.StateType.COMPOSOITE_SLO_METRIC_ANALYSIS);
    orchestrationService.orchestrate(orchestrator);
    orchestrationService.orchestrate(orchestrator);

    verify(stateMachineEventPublisherService, times(0)).registerTaskComplete(any(), any());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testQueueAnalysis_withFailFast() {
    createDeploymentTimeSeriesAnalysisRecords(verificationTaskId);
    orchestrationService.queueAnalysis(AnalysisInput.builder()
                                           .verificationTaskId(verificationTaskId)
                                           .startTime(Instant.now())
                                           .endTime(Instant.now().plus(2, ChronoUnit.MINUTES))
                                           .build());

    AnalysisOrchestrator orchestrator = hPersistence.createQuery(AnalysisOrchestrator.class)
                                            .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
                                            .get();
    assertThat(orchestrator).isNull();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testOrchestrate_withFailFast() {
    CanaryTimeSeriesAnalysisState canaryTimeSeriesAnalysisState = CanaryTimeSeriesAnalysisState.builder().build();
    canaryTimeSeriesAnalysisState.setStatus(AnalysisStatus.CREATED);
    String verificationJobInstanceId =
        verificationJobInstanceService.create(builderFactory.verificationJobInstanceBuilder()
                                                  .executionStatus(VerificationJobInstance.ExecutionStatus.RUNNING)
                                                  .resolvedJob(builderFactory.getDeploymentVerificationJob())
                                                  .build());
    String deploymentVerificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, DataSourceType.APP_DYNAMICS);
    TimeSeriesCanaryLearningEngineTask timeSeriesCanaryLearningEngineTask =
        TimeSeriesCanaryLearningEngineTask.builder().build();
    timeSeriesCanaryLearningEngineTask.setVerificationTaskId(deploymentVerificationTaskId);
    timeSeriesCanaryLearningEngineTask.setAnalysisType(LearningEngineTaskType.TIME_SERIES_CANARY);
    timeSeriesCanaryLearningEngineTask.setAnalysisStartTime(clock.instant().minus(Duration.ofMinutes(5)));
    timeSeriesCanaryLearningEngineTask.setAnalysisEndTime(clock.instant());
    timeSeriesCanaryLearningEngineTask.setTaskStatus(LearningEngineTask.ExecutionStatus.SUCCESS);
    timeSeriesCanaryLearningEngineTask.setUuid(generateUuid());
    hPersistence.save(timeSeriesCanaryLearningEngineTask);

    canaryTimeSeriesAnalysisState.setInputs(AnalysisInput.builder()
                                                .verificationTaskId(deploymentVerificationTaskId)
                                                .startTime(clock.instant().minus(Duration.ofMinutes(5)))
                                                .endTime(clock.instant())
                                                .build());
    canaryTimeSeriesAnalysisState.setWorkerTaskId(timeSeriesCanaryLearningEngineTask.getUuid());

    AnalysisOrchestrator orchestrator = AnalysisOrchestrator.builder()
                                            .verificationTaskId(deploymentVerificationTaskId)
                                            .status(AnalysisOrchestratorStatus.RUNNING)
                                            .analysisStateMachineQueue(new ArrayList<>())
                                            .build();

    hPersistence.save(orchestrator);
    AnalysisStateMachine stateMachine = dataGenerator.buildStateMachine(
        AnalysisStatus.RUNNING, deploymentVerificationTaskId, canaryTimeSeriesAnalysisState);
    hPersistence.save(stateMachine);

    AnalysisStateMachine nextStateMachine = dataGenerator.buildStateMachine(
        AnalysisStatus.CREATED, deploymentVerificationTaskId, canaryTimeSeriesAnalysisState);
    orchestrator.getAnalysisStateMachineQueue().add(nextStateMachine);
    hPersistence.save(orchestrator);

    createDeploymentTimeSeriesAnalysisRecords(deploymentVerificationTaskId);
    orchestrate(deploymentVerificationTaskId);
    List<AnalysisStateMachine> savedStateMachines = hPersistence.createQuery(AnalysisStateMachine.class).asList();
    assertThat(savedStateMachines.size()).isEqualTo(2);
    assertThat(savedStateMachines.get(0).getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
    assertThat(savedStateMachines.get(1).getStatus()).isEqualTo(AnalysisStatus.TERMINATED);
    AnalysisOrchestrator savedOrchestrator = hPersistence.createQuery(AnalysisOrchestrator.class).get();
    assertThat(savedOrchestrator).isNotNull();
    assertThat(savedOrchestrator.getStatus()).isEqualTo(AnalysisOrchestratorStatus.TERMINATED);

    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    assertThat(verificationJobInstance.getExecutionStatus()).isEqualTo(VerificationJobInstance.ExecutionStatus.SUCCESS);
    assertThat(verificationJobInstance.getVerificationStatus())
        .isEqualTo(ActivityVerificationStatus.VERIFICATION_FAILED);
  }

  private void createDeploymentTimeSeriesAnalysisRecords(String verificationTaskId) {
    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysisOne =
        DeploymentTimeSeriesAnalysis.builder()
            .accountId(accountId)
            .verificationTaskId(verificationTaskId)
            .startTime(Instant.now())
            .endTime(Instant.now().plus(1, ChronoUnit.MINUTES))
            .build();
    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysisTwo =
        DeploymentTimeSeriesAnalysis.builder()
            .accountId(accountId)
            .verificationTaskId(verificationTaskId)
            .startTime(Instant.now().plus(1, ChronoUnit.MINUTES))
            .endTime(Instant.now().plus(2, ChronoUnit.MINUTES))
            .build();
    deploymentTimeSeriesAnalysisTwo.setFailFast(false);
    deploymentTimeSeriesAnalysisTwo.setRisk(Risk.HEALTHY);
    deploymentTimeSeriesAnalysisTwo.setFailFast(true);
    deploymentTimeSeriesAnalysisTwo.setRisk(Risk.UNHEALTHY);
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysisOne);
    deploymentTimeSeriesAnalysisService.save(deploymentTimeSeriesAnalysisTwo);
  }

  private AnalysisOrchestrator getOrchestrator(String verificationTaskId) {
    return hPersistence.createQuery(AnalysisOrchestrator.class)
        .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
        .get();
  }
  public void orchestrate(String verificationTaskId) {
    AnalysisOrchestrator orchestrator = getOrchestrator(verificationTaskId);
    orchestrationService.orchestrate(orchestrator);
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder()
            .identifier(builderFactory.getContext().getMonitoredServiceIdentifier())
            .build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  @SneakyThrows
  private void incrementAnalysisStateMachineClockBy5Hours() {
    Clock oldClock = (Clock) FieldUtils.readField(analysisStateMachineService, "clock", true);
    Clock newClock = Clock.fixed(oldClock.instant().plus(Duration.ofHours(5)), ZoneOffset.UTC);
    FieldUtils.writeField(analysisStateMachineService, "clock", newClock, true);
  }

  private ServiceLevelObjectiveV2DTO getCompositeSLODTO() {
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1);
    SimpleServiceLevelObjective simpleServiceLevelObjective1 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1.getIdentifier());

    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier2").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2);
    SimpleServiceLevelObjective simpleServiceLevelObjective2 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2.getIdentifier());

    return builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
        .spec(CompositeServiceLevelObjectiveSpec.builder()
                  .serviceLevelObjectivesDetails(
                      Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                        .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                        .weightagePercentage(75.0)
                                        .accountId(simpleServiceLevelObjective1.getAccountId())
                                        .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                        .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                        .build(),
                          ServiceLevelObjectiveDetailsDTO.builder()
                              .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                              .weightagePercentage(25.0)
                              .accountId(simpleServiceLevelObjective2.getAccountId())
                              .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                              .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                              .build()))
                  .build())
        .build();
  }
}
