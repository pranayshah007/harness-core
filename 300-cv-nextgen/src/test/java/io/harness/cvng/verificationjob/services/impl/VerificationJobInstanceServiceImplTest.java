/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.services.impl;

import static io.harness.cvng.beans.DataSourceType.APP_DYNAMICS;
import static io.harness.cvng.beans.activity.ActivityVerificationStatus.VERIFICATION_FAILED;
import static io.harness.cvng.beans.activity.ActivityVerificationStatus.VERIFICATION_PASSED;
import static io.harness.cvng.beans.job.VerificationJobType.CANARY;
import static io.harness.cvng.beans.job.VerificationJobType.TEST;
import static io.harness.cvng.core.services.CVNextGenConstants.DATA_COLLECTION_DELAY;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.activity.beans.DeploymentActivityResultDTO;
import io.harness.cvng.analysis.beans.CanaryAdditionalInfo;
import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.services.api.DeploymentLogAnalysisService;
import io.harness.cvng.analysis.services.api.VerificationJobInstanceAnalysisService;
import io.harness.cvng.beans.AppDynamicsDataCollectionInfo;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;
import io.harness.cvng.beans.job.CanaryVerificationJobDTO;
import io.harness.cvng.beans.job.HealthVerificationJobDTO;
import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.beans.job.TestVerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.AnalysisInfo.DeploymentVerification;
import io.harness.cvng.core.entities.AnalysisInfo.SLI;
import io.harness.cvng.core.entities.AppDynamicsCVConfig.MetricInfo;
import io.harness.cvng.core.entities.AppDynamicsCVConfig.MetricInfo.MetricInfoBuilder;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.verificationjob.beans.TestVerificationBaselineExecutionDTO;
import io.harness.cvng.verificationjob.entities.HealthVerificationJob;
import io.harness.cvng.verificationjob.entities.TestVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJob.RuntimeParameter;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.AnalysisProgressLog;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ExecutionStatus;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.VerificationJobInstanceKeys;
import io.harness.cvng.verificationjob.services.api.VerificationJobService;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.persistence.HPersistence;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class VerificationJobInstanceServiceImplTest extends CvNextGenTestBase {
  @Inject private VerificationJobService verificationJobService;
  @Inject private VerificationJobInstanceServiceImpl verificationJobInstanceService;
  @Inject private CVConfigService cvConfigService;
  @Mock private VerificationManagerService verificationManagerService;
  @Inject private DataCollectionTaskService dataCollectionTaskService;
  @Inject private HPersistence hPersistence;
  @Mock private NextGenService nextGenService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private VerificationJobInstanceAnalysisService verificationJobInstanceAnalysisService;
  @Inject private DeploymentLogAnalysisService deploymentLogAnalysisService;

  @Mock private Clock clock;
  private Instant fakeNow;
  private String accountId;
  private String verificationJobIdentifier;
  private long deploymentStartTimeMs;
  private String connectorId;
  private String perpetualTaskId;
  private String projectIdentifier;
  private String orgIdentifier;
  private String cvConfigId;
  private String serviceIdentifier;
  private String monitoringSourceIdentifier;
  private int timeCounter;
  private CVConfig cvConfig;
  private BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    verificationJobIdentifier = generateUuid();
    accountId = generateUuid();
    accountId = generateUuid();
    projectIdentifier = generateUuid();
    orgIdentifier = generateUuid();
    cvConfigId = generateUuid();
    monitoringSourceIdentifier = "monitoringIdentifier/healthSourceIdentifier";
    serviceIdentifier = generateUuid();
    deploymentStartTimeMs = Instant.parse("2020-07-27T10:44:06.390Z").toEpochMilli();
    connectorId = generateUuid();
    perpetualTaskId = generateUuid();
    fakeNow = Instant.parse("2020-07-27T10:50:00.390Z");
    clock = Clock.fixed(fakeNow, ZoneOffset.UTC);
    timeCounter = 0;
    builderFactory = BuilderFactory.builder()
                         .context(BuilderFactory.Context.builder()
                                      .projectParams(ProjectParams.builder()
                                                         .accountIdentifier(accountId)
                                                         .orgIdentifier(orgIdentifier)
                                                         .projectIdentifier(projectIdentifier)
                                                         .build())
                                      .serviceIdentifier(serviceIdentifier)
                                      .envIdentifier(generateUuid())
                                      .build())
                         .build();
    cvConfig = newCVConfig();
    FieldUtils.writeField(verificationJobInstanceService, "clock", clock, true);
    FieldUtils.writeField(verificationJobInstanceService, "nextGenService", nextGenService, true);
    when(verificationManagerService.createDataCollectionTask(any(), any(), any(), any())).thenReturn(perpetualTaskId);

    when(nextGenService.getEnvironment(accountId, orgIdentifier, projectIdentifier, "dev"))
        .thenReturn(EnvironmentResponseDTO.builder()
                        .accountId(accountId)
                        .identifier("dev")
                        .name("Harness dev")
                        .type(EnvironmentType.PreProduction)
                        .projectIdentifier(projectIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .build());

    when(nextGenService.getEnvironment(accountId, orgIdentifier, projectIdentifier, "prod"))
        .thenReturn(EnvironmentResponseDTO.builder()
                        .accountId(accountId)
                        .identifier("prod")
                        .name("Harness prod")
                        .type(EnvironmentType.Production)
                        .projectIdentifier(projectIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .build());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testProcessVerificationJobInstance_getEmbaddedCVConfig() {
    VerificationJobInstance verificationJobInstance = newVerificationJobInstance();
    cvConfigService.save(cvConfig);
    verificationJobInstanceService.create(Arrays.asList(verificationJobInstance));

    verificationJobInstanceService.processVerificationJobInstance(verificationJobInstance);
    assertThat(
        verificationJobInstanceService.getEmbeddedCVConfig(cvConfig.getUuid(), verificationJobInstance.getUuid()))
        .isEqualTo(cvConfig);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void createDataCollectionTasks_validateDataCollectionTasksCreation() {
    VerificationJob job = builderFactory.canaryVerificationJobBuilder()
                              .monitoringSources(Collections.singletonList(monitoringSourceIdentifier))
                              .build();
    job.setAccountId(accountId);
    job.setIdentifier(verificationJobIdentifier);
    cvConfigService.save(newCVConfig());
    String verificationJobInstanceId = verificationJobInstanceService.create(newVerificationJobInstance());
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    verificationJobInstance.setResolvedJob(job);
    verificationJobInstanceService.createDataCollectionTasks(verificationJobInstance);
    String workerId = getDataCollectionWorkerId(connectorId);
    DataCollectionTask firstTask = dataCollectionTaskService.getNextTask(accountId, workerId).get();
    assertThat(firstTask).isNotNull();
    assertThat(firstTask.getStartTime()).isEqualTo(Instant.parse("2020-07-27T10:34:00Z"));
    assertThat(firstTask.getEndTime()).isEqualTo(Instant.parse("2020-07-27T10:44:00Z"));
    assertThat(firstTask.getValidAfter()).isEqualTo(Instant.parse("2020-07-27T10:44:00Z").plus(Duration.ofMinutes(5)));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void createDataCollectionTasks_validateDataCollectionInfo() {
    VerificationJob job = builderFactory.canaryVerificationJobBuilder()
                              .monitoringSources(Collections.singletonList(monitoringSourceIdentifier))
                              .build();
    job.setIdentifier(verificationJobIdentifier);
    CVConfig cvConfig = builderFactory.appDynamicsCVConfigBuilder()
                            .metricInfos(Arrays.asList(
                                getAppdMetricInfoBuilder("1")
                                    .deploymentVerification(DeploymentVerification.builder().enabled(false).build())
                                    .build(),
                                getAppdMetricInfoBuilder("2")
                                    .deploymentVerification(DeploymentVerification.builder().enabled(true).build())
                                    .build()))
                            .build();
    cvConfig.setAccountId(accountId);
    cvConfig.setConnectorIdentifier(connectorId);
    cvConfig.setProjectIdentifier(projectIdentifier);
    cvConfig.setOrgIdentifier(orgIdentifier);
    cvConfig.setIdentifier(monitoringSourceIdentifier);
    cvConfigService.save(cvConfig);
    String verificationJobInstanceId = verificationJobInstanceService.create(newVerificationJobInstance());
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    verificationJobInstance.setResolvedJob(job);
    verificationJobInstanceService.createDataCollectionTasks(verificationJobInstance);
    String workerId = getDataCollectionWorkerId(connectorId);
    DataCollectionTask firstTask = dataCollectionTaskService.getNextTask(accountId, workerId).get();
    assertThat(firstTask).isNotNull();
    assertThat(((AppDynamicsDataCollectionInfo) firstTask.getDataCollectionInfo()).getCustomMetrics()).hasSize(1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDataCollectionTasks_validateDataCollectionTasksCreationWithDefaultDataCollectionDelay() {
    VerificationJob job = builderFactory.canaryVerificationJobBuilder()
                              .monitoringSources(Collections.singletonList(monitoringSourceIdentifier))
                              .build();
    job.setIdentifier(verificationJobIdentifier);
    cvConfigService.save(newCVConfig());
    VerificationJobInstance jobInstance = builderFactory.verificationJobInstanceBuilder()
                                              .deploymentStartTime(Instant.ofEpochMilli(deploymentStartTimeMs))
                                              .startTime(Instant.ofEpochMilli(deploymentStartTimeMs))
                                              .build();
    String verificationJobInstanceId = verificationJobInstanceService.create(jobInstance);
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    verificationJobInstance.setResolvedJob(job);
    verificationJobInstanceService.createDataCollectionTasks(verificationJobInstance);
    VerificationJobInstance updated =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    String workerId = getDataCollectionWorkerId(connectorId);
    DataCollectionTask firstTask = dataCollectionTaskService.getNextTask(accountId, workerId).get();
    assertThat(firstTask).isNotNull();
    assertThat(firstTask.getEndTime()).isEqualTo(Instant.parse("2020-07-27T10:44:00Z"));
    assertThat(firstTask.getValidAfter()).isEqualTo(Instant.parse("2020-07-27T10:44:00Z").plus(DATA_COLLECTION_DELAY));
    assertThat(updated.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testLogProgress_multipleUpdates() {
    cvConfigService.save(newCVConfig());
    String verificationJobInstanceId =
        createVerificationJobInstance("identifier", "envIdentifier", ExecutionStatus.RUNNING, CANARY).getUuid();

    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    assertThat(verificationJobInstance.getProgressLogs()).isEmpty();
    AnalysisProgressLog progressLog = AnalysisProgressLog.builder()
                                          .startTime(verificationJobInstance.getStartTime())
                                          .endTime(verificationJobInstance.getStartTime().plus(Duration.ofMinutes(1)))
                                          .analysisStatus(AnalysisStatus.SUCCESS)
                                          .log("time series analysis done")
                                          .verificationTaskId(verificationTaskService.getVerificationTaskId(
                                              accountId, cvConfigId, verificationJobInstanceId))
                                          .build();
    verificationJobInstanceService.logProgress(progressLog);
    assertThat(progressLog.getCreatedAt()).isEqualTo(clock.instant());
    verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    assertThat(verificationJobInstance.getProgressLogs()).hasSize(1);
    assertThat(((AnalysisProgressLog) verificationJobInstance.getProgressLogs().get(0)).getAnalysisStatus())
        .isEqualTo(AnalysisStatus.SUCCESS);
    assertThat(verificationJobInstance.getProgressLogs().get(0).getLog()).isEqualTo("time series analysis done");

    assertThat(verificationJobInstance.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    progressLog = AnalysisProgressLog.builder()
                      .startTime(verificationJobInstance.getEndTime().minus(Duration.ofMinutes(1)))
                      .endTime(verificationJobInstance.getEndTime())
                      .analysisStatus(AnalysisStatus.SUCCESS)
                      .verificationTaskId(verificationTaskService.getVerificationTaskId(
                          accountId, cvConfigId, verificationJobInstanceId))
                      .isFinalState(false)
                      .log("log")
                      .build();
    verificationJobInstanceService.logProgress(progressLog);
    verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    assertThat(verificationJobInstance.getProgressLogs()).hasSize(2);
    assertThat(((AnalysisProgressLog) verificationJobInstance.getProgressLogs().get(1)).getAnalysisStatus())
        .isEqualTo(AnalysisStatus.SUCCESS);
    assertThat(verificationJobInstance.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);

    progressLog = AnalysisProgressLog.builder()
                      .startTime(verificationJobInstance.getEndTime().minus(Duration.ofMinutes(1)))
                      .endTime(verificationJobInstance.getEndTime())
                      .analysisStatus(AnalysisStatus.SUCCESS)
                      .verificationTaskId(verificationTaskService.getVerificationTaskId(
                          accountId, cvConfigId, verificationJobInstanceId))
                      .isFinalState(true)
                      .log("log")
                      .build();
    verificationJobInstanceService.logProgress(progressLog);
    verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    assertThat(verificationJobInstance.getProgressLogs()).hasSize(3);
    assertThat(((AnalysisProgressLog) verificationJobInstance.getProgressLogs().get(2)).getAnalysisStatus())
        .isEqualTo(AnalysisStatus.SUCCESS);
    assertThat(verificationJobInstance.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testLogProgress_onFailure() {
    cvConfigService.save(newCVConfig());
    String verificationJobInstanceId = createVerificationJobInstance().getUuid();
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    assertThat(verificationJobInstance.getProgressLogs()).isEmpty();
    AnalysisProgressLog progressLog = AnalysisProgressLog.builder()
                                          .startTime(verificationJobInstance.getStartTime())
                                          .endTime(verificationJobInstance.getStartTime().plus(Duration.ofMinutes(1)))
                                          .analysisStatus(AnalysisStatus.FAILED)
                                          .log("log")
                                          .verificationTaskId(verificationTaskService.getVerificationTaskId(
                                              accountId, cvConfigId, verificationJobInstanceId))
                                          .build();
    verificationJobInstanceService.logProgress(progressLog);
    verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    assertThat(verificationJobInstance.getProgressLogs()).hasSize(1);
    assertThat(((AnalysisProgressLog) verificationJobInstance.getProgressLogs().get(0)).getAnalysisStatus())
        .isEqualTo(AnalysisStatus.FAILED);
    assertThat(verificationJobInstance.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetDeploymentVerificationStatus() {
    cvConfigService.save(newCVConfig());
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    verificationJobInstance.setExecutionStatus(ExecutionStatus.SUCCESS);
    String verificationJobInstanceId = verificationJobInstance.getUuid();
    String verificationTaskId =
        verificationTaskService.getVerificationTaskId(accountId, cvConfigId, verificationJobInstanceId);
    DeploymentLogAnalysis deploymentLogAnalysis =
        DeploymentLogAnalysis.builder()
            .accountId(accountId)
            .verificationTaskId(verificationTaskId)
            .resultSummary(DeploymentLogAnalysisDTO.ResultSummary.builder().risk(1).build())
            .build();
    deploymentLogAnalysisService.save(deploymentLogAnalysis);
    ActivityVerificationStatus activityVerificationStatus =
        verificationJobInstanceService.getDeploymentVerificationStatus(verificationJobInstance);
    assertThat(activityVerificationStatus).isEqualTo(VERIFICATION_FAILED);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testLogProgress_multipleVerificationTasks() {
    CVConfig cvConfig1 = newCVConfig();
    cvConfigService.save(cvConfig1);
    String verificationJobInstanceId =
        createVerificationJobInstance("identifier", "env", ExecutionStatus.RUNNING, CANARY).getUuid();
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfig1.getUuid(), verificationJobInstanceId, cvConfig1.getType());
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    AnalysisProgressLog progressLog =
        AnalysisProgressLog.builder()
            .startTime(verificationJobInstance.getStartTime().minus(Duration.ofMinutes(1)))
            .endTime(verificationJobInstance.getEndTime())
            .verificationTaskId(
                verificationTaskService.getVerificationTaskId(accountId, cvConfigId, verificationJobInstanceId))
            .analysisStatus(AnalysisStatus.SUCCESS)
            .isFinalState(true)
            .log("log")
            .build();
    verificationJobInstanceService.logProgress(progressLog);
    verificationJobInstanceService.logProgress(progressLog); // Multiple updates for same call is handled.
    verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    assertThat(verificationJobInstance.getProgressLogs()).hasSize(1);
    assertThat(((AnalysisProgressLog) verificationJobInstance.getProgressLogs().get(0)).getAnalysisStatus())
        .isEqualTo(AnalysisStatus.SUCCESS);
    assertThat(verificationJobInstance.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    progressLog = AnalysisProgressLog.builder()
                      .startTime(verificationJobInstance.getStartTime().minus(Duration.ofMinutes(1)))
                      .endTime(verificationJobInstance.getEndTime())
                      .verificationTaskId(verificationTaskService.getVerificationTaskId(
                          accountId, cvConfig1.getUuid(), verificationJobInstanceId))
                      .analysisStatus(AnalysisStatus.SUCCESS)
                      .isFinalState(true)
                      .log("log")
                      .build();
    verificationJobInstanceService.logProgress(progressLog);
    verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);
    assertThat(verificationJobInstance.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testAbort() throws IllegalAccessException {
    DataCollectionTaskService dataCollectionTaskService = Mockito.mock(DataCollectionTaskService.class);
    FieldUtils.writeField(verificationJobInstanceService, "dataCollectionTaskService", dataCollectionTaskService, true);

    VerificationJobInstance runningVerificationJobInstance =
        createVerificationJobInstance(verificationJobIdentifier, "prod", ExecutionStatus.RUNNING, CANARY);
    VerificationJobInstance failedVerificationJobInstance =
        createVerificationJobInstance(verificationJobIdentifier, "prod", ExecutionStatus.FAILED, CANARY);
    hPersistence.save(Lists.newArrayList(runningVerificationJobInstance, failedVerificationJobInstance));
    List<String> verificationTaskIds = verificationTaskService.maybeGetVerificationTaskIds(
        Lists.newArrayList(runningVerificationJobInstance.getUuid(), failedVerificationJobInstance.getUuid()));

    verificationJobInstanceService.abort(
        Lists.newArrayList(runningVerificationJobInstance.getUuid(), failedVerificationJobInstance.getUuid()));

    VerificationJobInstance abortedRunningVJI =
        hPersistence.get(VerificationJobInstance.class, runningVerificationJobInstance.getUuid());
    assertThat(abortedRunningVJI.getExecutionStatus()).isEqualTo(ExecutionStatus.ABORTED);
    // Failed JobInstance will remain in failed status
    VerificationJobInstance abortedFailedVJI =
        hPersistence.get(VerificationJobInstance.class, failedVerificationJobInstance.getUuid());
    assertThat(abortedFailedVJI.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    Mockito.verify(dataCollectionTaskService).abortDeploymentDataCollectionTasks(verificationTaskIds);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetTestJobBaselineExecutions_noOldInstanceExist() {
    assertThat(verificationJobInstanceService.getTestJobBaselineExecutions(
                   accountId, orgIdentifier, projectIdentifier, generateUuid()))
        .isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetTestJobBaselineExecutions_multipleOldSuccessfulInstances() {
    String verificationJobIdentifierOne = generateUuid();
    verificationJobService.create(
        accountId, createVerificationJobDTO(verificationJobIdentifierOne, generateUuid(), TEST));

    List<ExecutionStatus> executionStatuses =
        Arrays.asList(ExecutionStatus.QUEUED, ExecutionStatus.SUCCESS, ExecutionStatus.FAILED, ExecutionStatus.SUCCESS);
    List<VerificationJobInstance> verificationJobInstances = new ArrayList<>();
    for (ExecutionStatus executionStatus : executionStatuses) {
      verificationJobInstances.add(createVerificationJobInstance(verificationJobIdentifierOne, executionStatus));
    }
    verificationJobInstances.add(
        createVerificationJobInstance(generateUuid(), generateUuid(), ExecutionStatus.SUCCESS, CANARY));
    verificationJobInstances.add(
        createVerificationJobInstance(generateUuid(), generateUuid(), ExecutionStatus.SUCCESS, TEST));
    List<TestVerificationBaselineExecutionDTO> testVerificationBaselineExecutionDTOS =
        verificationJobInstanceService.getTestJobBaselineExecutions(
            accountId, orgIdentifier, projectIdentifier, verificationJobIdentifierOne);
    assertThat(testVerificationBaselineExecutionDTOS).hasSize(2);
    assertThat(testVerificationBaselineExecutionDTOS.get(0))
        .isEqualTo(TestVerificationBaselineExecutionDTO.builder()
                       .verificationJobInstanceId(verificationJobInstances.get(1).getUuid())
                       .createdAt(verificationJobInstances.get(1).getCreatedAt())
                       .build());
    assertThat(testVerificationBaselineExecutionDTOS.get(1))
        .isEqualTo(TestVerificationBaselineExecutionDTO.builder()
                       .verificationJobInstanceId(verificationJobInstances.get(3).getUuid())
                       .createdAt(verificationJobInstances.get(3).getCreatedAt())
                       .build());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulTestVerificationJobExecutionId_doesNotExist() {
    assertThat(verificationJobInstanceService.getLastSuccessfulTestVerificationJobExecutionId(
                   accountId, orgIdentifier, projectIdentifier, generateUuid()))
        .isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulTestVerificationJobExecutionId_lastSuccessfulTestVerificationJobInstance() {
    String verificationJobIdentifier = generateUuid();
    List<ExecutionStatus> executionStatuses =
        Arrays.asList(ExecutionStatus.QUEUED, ExecutionStatus.SUCCESS, ExecutionStatus.FAILED);
    List<VerificationJobInstance> verificationJobInstances = new ArrayList<>();
    for (ExecutionStatus executionStatus : executionStatuses) {
      verificationJobInstances.add(
          createVerificationJobInstance(verificationJobIdentifier, generateUuid(), executionStatus, TEST));
    }
    verificationJobInstances.add(
        createVerificationJobInstance(verificationJobIdentifier, generateUuid(), ExecutionStatus.SUCCESS, CANARY));
    verificationJobInstances.add(
        createVerificationJobInstance(generateUuid(), generateUuid(), ExecutionStatus.SUCCESS, TEST));
    assertThat(verificationJobInstanceService
                   .getLastSuccessfulTestVerificationJobExecutionId(
                       accountId, orgIdentifier, projectIdentifier, verificationJobIdentifier)
                   .get())
        .isEqualTo(verificationJobInstances.get(1).getUuid());
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulTestVerificationJobExecutionId_WithVerificationStatusAsSuccess() {
    String verificationJobIdentifier = generateUuid();
    List<ExecutionStatus> executionStatuses =
        Arrays.asList(ExecutionStatus.QUEUED, ExecutionStatus.SUCCESS, ExecutionStatus.FAILED);
    List<VerificationJobInstance> verificationJobInstances = new ArrayList<>();
    for (ExecutionStatus executionStatus : executionStatuses) {
      verificationJobInstances.add(createVerificationJobInstance(
          verificationJobIdentifier, generateUuid(), executionStatus, TEST, VERIFICATION_PASSED));
    }
    verificationJobInstances.add(createVerificationJobInstance(verificationJobIdentifier, generateUuid(),
        ExecutionStatus.SUCCESS, TEST, ActivityVerificationStatus.VERIFICATION_FAILED));
    verificationJobInstances.add(createVerificationJobInstance(
        verificationJobIdentifier, generateUuid(), ExecutionStatus.SUCCESS, TEST, VERIFICATION_PASSED));
    assertThat(verificationJobInstanceService
                   .getLastSuccessfulTestVerificationJobExecutionId(
                       accountId, orgIdentifier, projectIdentifier, verificationJobIdentifier)
                   .get())
        .isEqualTo(verificationJobInstances.get(4).getUuid());
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulTestVerificationJobExecutionId_WithAllVerificationStatusAsFail() {
    String verificationJobIdentifier = generateUuid();
    List<ExecutionStatus> executionStatuses =
        Arrays.asList(ExecutionStatus.QUEUED, ExecutionStatus.SUCCESS, ExecutionStatus.FAILED);
    List<VerificationJobInstance> verificationJobInstances = new ArrayList<>();
    for (ExecutionStatus executionStatus : executionStatuses) {
      verificationJobInstances.add(createVerificationJobInstance(verificationJobIdentifier, generateUuid(),
          executionStatus, TEST, ActivityVerificationStatus.VERIFICATION_FAILED));
    }
    assertThat(verificationJobInstanceService.getLastSuccessfulTestVerificationJobExecutionId(
                   accountId, orgIdentifier, projectIdentifier, verificationJobIdentifier))
        .isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetDeploymentVerificationJobInstanceSummary() {
    VerificationJobInstance devVerificationJobInstance =
        createVerificationJobInstance("devVerificationJobInstance", "dev");
    VerificationJobInstance prodVerificationJobInstance =
        createVerificationJobInstance("prodVerificationJobInstance", "prod");
    DeploymentActivityResultDTO.DeploymentVerificationJobInstanceSummary deploymentVerificationJobInstanceSummary =
        verificationJobInstanceService.getDeploymentVerificationJobInstanceSummary(
            Lists.newArrayList(devVerificationJobInstance.getUuid(), prodVerificationJobInstance.getUuid()));
    assertThat(deploymentVerificationJobInstanceSummary.getEnvironmentName()).isEqualTo("Harness dev");
    assertThat(deploymentVerificationJobInstanceSummary.getVerificationJobInstanceId())
        .isEqualTo(devVerificationJobInstance.getUuid());
    assertThat(deploymentVerificationJobInstanceSummary.getActivityId()).isNull();
    assertThat(deploymentVerificationJobInstanceSummary.getActivityStartTime()).isZero();
    assertThat(deploymentVerificationJobInstanceSummary.getJobName())
        .isEqualTo(devVerificationJobInstance.getResolvedJob().getJobName());
    assertThat(deploymentVerificationJobInstanceSummary.getProgressPercentage()).isEqualTo(0);
    assertThat(deploymentVerificationJobInstanceSummary.getRisk()).isNull();
    assertThat(deploymentVerificationJobInstanceSummary.getStatus()).isEqualTo(ActivityVerificationStatus.NOT_STARTED);
    CanaryAdditionalInfo additionalInfo = new CanaryAdditionalInfo();
    additionalInfo.setCanary(new HashSet<>());
    additionalInfo.setPrimary(new HashSet<>());
    additionalInfo.setPrimaryInstancesLabel("before");
    additionalInfo.setPrimaryInstancesLabel("after");
  }

  private String getDataCollectionWorkerId(String connectorId) {
    return monitoringSourcePerpetualTaskService.getDeploymentWorkerId(
        accountId, orgIdentifier, projectIdentifier, connectorId, monitoringSourceIdentifier);
  }
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testDedupCreate_withHealthJob() {
    Instant now = Instant.parse("2021-02-26T09:03:48.439Z");
    int numOfJobInstances = 10;
    List<VerificationJobInstance> verificationJobInstances = new ArrayList<>();
    for (int i = 0; i < numOfJobInstances; i++) {
      verificationJobInstances.add(
          builderFactory.verificationJobInstanceBuilder()
              .accountId(accountId)
              .startTime(now.plus(Duration.ofMinutes(i)))
              .executionStatus(ExecutionStatus.QUEUED)
              .deploymentStartTime(now.minus(Duration.ofMinutes(2)))
              .resolvedJob(
                  HealthVerificationJob.builder()
                      .accountId(accountId)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .identifier("job-" + i)
                      .duration(RuntimeParameter.builder().value("5m").isRuntimeParam(false).build())
                      .envIdentifier(
                          RuntimeParameter.builder().isRuntimeParam(false).value(i % 2 == 0 ? "e0" : "e1").build())
                      .serviceIdentifier(
                          RuntimeParameter.builder().isRuntimeParam(false).value(i % 2 == 0 ? "s0" : "s1").build())
                      .build())
              .build());
    }

    List<String> jobIds = verificationJobInstanceService.dedupCreate(verificationJobInstances);
    assertThat(jobIds.size()).isEqualTo(2);
    verificationJobInstances = hPersistence.createQuery(VerificationJobInstance.class, excludeAuthority).asList();
    Collections.sort(verificationJobInstances, Comparator.comparing(v -> v.getResolvedJob().getIdentifier()));
    for (int i = 0; i < 2; i++) {
      VerificationJobInstance verificationJobInstance = verificationJobInstances.get(i);
      assertThat(verificationJobInstance.getAccountId()).isEqualTo(accountId);
      assertThat(verificationJobInstance.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
      assertThat(verificationJobInstance.getStartTime()).isEqualTo(Instant.parse("2021-02-26T09:00:00.000Z"));
      assertThat(((HealthVerificationJob) verificationJobInstance.getResolvedJob())
                     .getPreActivityVerificationStartTime(verificationJobInstance.getStartTime()))
          .isEqualTo(Instant.parse("2021-02-26T08:55:00.000Z"));
      assertThat(verificationJobInstance.getStartTime()).isEqualTo(Instant.parse("2021-02-26T09:00:00.000Z"));

      HealthVerificationJob resolvedJob = (HealthVerificationJob) verificationJobInstance.getResolvedJob();
      assertThat(resolvedJob.getIdentifier()).isEqualTo("job-" + i);
      assertThat(resolvedJob.getAccountId()).isEqualTo(accountId);
      assertThat(resolvedJob.getOrgIdentifier()).isEqualTo(orgIdentifier);
      assertThat(resolvedJob.getProjectIdentifier()).isEqualTo(projectIdentifier);
      assertThat(resolvedJob.getEnvIdentifier()).isEqualTo("e" + i);
      assertThat(resolvedJob.getServiceIdentifier()).isEqualTo("s" + i);
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCreate_whenNonHealthJob() {
    Instant now = Instant.parse("2021-02-26T09:03:00.000Z");
    int numOfJobInstances = 10;
    List<VerificationJobInstance> verificationJobInstances = new ArrayList<>();
    for (int i = 0; i < numOfJobInstances; i++) {
      verificationJobInstances.add(
          VerificationJobInstance.builder()
              .uuid("id-" + i)
              .accountId(accountId)
              .deploymentStartTime(now.minus(Duration.ofMinutes(2)))
              .startTime(now.plus(Duration.ofMinutes(i)))
              .executionStatus(ExecutionStatus.QUEUED)
              .resolvedJob(
                  TestVerificationJob.builder()
                      .accountId(accountId)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .identifier("job-" + i)
                      .envIdentifier(RuntimeParameter.builder().isRuntimeParam(false).value("e1").build())
                      .serviceIdentifier(RuntimeParameter.builder().isRuntimeParam(false).value("s1").build())
                      .duration(RuntimeParameter.builder().isRuntimeParam(false).value("10m").build())
                      .sensitivity(
                          RuntimeParameter.builder().isRuntimeParam(false).value(Sensitivity.LOW.name()).build())
                      .build())
              .build());
    }

    List<String> jobIds = verificationJobInstanceService.create(verificationJobInstances);
    assertThat(jobIds.size()).isEqualTo(numOfJobInstances);
    Collections.sort(jobIds);
    for (int i = 0; i < 2; i++) {
      VerificationJobInstance verificationJobInstance = hPersistence.get(VerificationJobInstance.class, jobIds.get(i));
      assertThat(verificationJobInstance.getAccountId()).isEqualTo(accountId);
      assertThat(verificationJobInstance.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
      assertThat(verificationJobInstance.getStartTime())
          .isEqualTo(Instant.parse("2021-02-26T09:03:00.000Z").plus(Duration.ofMinutes(i)));
      assertThat(verificationJobInstance.getStartTime()).isEqualTo(now.plusSeconds(i * 60));
      TestVerificationJob resolvedJob = (TestVerificationJob) verificationJobInstance.getResolvedJob();
      assertThat(resolvedJob.getIdentifier()).isEqualTo("job-" + i);
      assertThat(resolvedJob.getAccountId()).isEqualTo(accountId);
      assertThat(resolvedJob.getOrgIdentifier()).isEqualTo(orgIdentifier);
      assertThat(resolvedJob.getProjectIdentifier()).isEqualTo(projectIdentifier);
      assertThat(resolvedJob.getEnvIdentifier()).isEqualTo("e1");
      assertThat(resolvedJob.getServiceIdentifier()).isEqualTo("s1");
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCreateAddsAllFields() {
    Instant now = Instant.now();
    int numOfJobInstances = 10;
    List<VerificationJobInstance> verificationJobInstances = new ArrayList<>();
    for (int i = 0; i < numOfJobInstances; i++) {
      verificationJobInstances.add(
          VerificationJobInstance.builder()
              .uuid("id-" + i)
              .accountId(accountId)
              .deploymentStartTime(now.minus(Duration.ofMinutes(2)))
              .startTime(now.plusSeconds(i))
              .executionStatus(ExecutionStatus.QUEUED)
              .resolvedJob(
                  TestVerificationJob.builder()
                      .accountId(accountId)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .identifier("job-" + i)
                      .envIdentifier(RuntimeParameter.builder().isRuntimeParam(false).value("e1").build())
                      .serviceIdentifier(RuntimeParameter.builder().isRuntimeParam(false).value("s1").build())
                      .duration(RuntimeParameter.builder().isRuntimeParam(false).value("10m").build())
                      .sensitivity(
                          RuntimeParameter.builder().isRuntimeParam(false).value(Sensitivity.LOW.name()).build())
                      .build())
              .build());
    }
    verificationJobInstanceService.create(verificationJobInstances);
    verificationJobInstances = hPersistence.createQuery(VerificationJobInstance.class, excludeAuthority).asList();
    Set<String> nullableFields = Sets.newHashSet(VerificationJobInstanceKeys.deploymentStartTime,
        VerificationJobInstanceKeys.dataCollectionTaskIteration, VerificationJobInstanceKeys.timeoutTaskIteration,
        VerificationJobInstanceKeys.dataCollectionDelay, VerificationJobInstanceKeys.oldVersionHosts,
        VerificationJobInstanceKeys.newVersionHosts, VerificationJobInstanceKeys.newHostsTrafficSplitPercentage,
        VerificationJobInstanceKeys.progressLogs, VerificationJobInstanceKeys.cvConfigMap,
        VerificationJobInstanceKeys.verificationStatus, VerificationJobInstanceKeys.name);
    verificationJobInstances.forEach(verificationJobInstance -> {
      List<Field> fields = ReflectionUtils.getAllDeclaredAndInheritedFields(VerificationJobInstance.class);
      fields.stream().filter(field -> !nullableFields.contains(field.getName())).forEach(field -> {
        try {
          field.setAccessible(true);
          assertThat(field.get(verificationJobInstance))
              .withFailMessage("field %s is null", field.getName())
              .isNotNull();
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      });
    });
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testMarkTimedOutIfNoProgress_currentTimeAfterTimeOut() throws IllegalAccessException {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    clock = Clock.fixed(verificationJobInstance.getEndTime().plus(Duration.ofMinutes(31)), ZoneOffset.UTC);
    FieldUtils.writeField(verificationJobInstanceService, "clock", clock, true);
    verificationJobInstance.setCreatedAt(verificationJobInstance.getStartTime().toEpochMilli());
    verificationJobInstanceService.markTimedOutIfNoProgress(verificationJobInstance);
    VerificationJobInstance updated =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstance.getUuid());
    assertThat(updated.getExecutionStatus()).isEqualTo(ExecutionStatus.TIMEOUT);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testMarkTimedOutIfNoProgress_currentTimeBeforeTimeout() throws IllegalAccessException {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    clock = Clock.fixed(verificationJobInstance.getEndTime().plus(Duration.ofMinutes(29)), ZoneOffset.UTC);
    FieldUtils.writeField(verificationJobInstanceService, "clock", clock, true);
    verificationJobInstance.setCreatedAt(verificationJobInstance.getStartTime().toEpochMilli());
    verificationJobInstanceService.markTimedOutIfNoProgress(verificationJobInstance);
    VerificationJobInstance updated =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstance.getUuid());
    assertThat(updated.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testMarkTimedOutIfNoProgress_endTimeIsInThePastQueued() throws IllegalAccessException {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    clock = Clock.fixed(verificationJobInstance.getEndTime().plus(Duration.ofDays(1)), ZoneOffset.UTC);
    FieldUtils.writeField(verificationJobInstanceService, "clock", clock, true);
    verificationJobInstance.setCreatedAt(clock.instant().minus(20, ChronoUnit.MINUTES).toEpochMilli());
    verificationJobInstanceService.markTimedOutIfNoProgress(verificationJobInstance);
    VerificationJobInstance updated =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstance.getUuid());
    assertThat(updated.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testMarkTimedOutIfNoProgress_endTimeIsInThePastWithCreatedAtTimeout() throws IllegalAccessException {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    clock = Clock.fixed(verificationJobInstance.getEndTime().plus(Duration.ofDays(1)), ZoneOffset.UTC);
    FieldUtils.writeField(verificationJobInstanceService, "clock", clock, true);
    verificationJobInstance.setCreatedAt(clock.instant().minus(31, ChronoUnit.MINUTES).toEpochMilli());
    verificationJobInstanceService.markTimedOutIfNoProgress(verificationJobInstance);
    VerificationJobInstance updated =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstance.getUuid());
    assertThat(updated.getExecutionStatus()).isEqualTo(ExecutionStatus.TIMEOUT);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetCVConfigsForVerification() {
    String monSource = "monitoringSource1/healthSource";
    VerificationJob job = builderFactory.canaryVerificationJobBuilder().build();
    job.setMonitoringSources(Arrays.asList(monSource));
    job.setAccountId(accountId);
    job.setIdentifier(verificationJobIdentifier);

    CVConfig cvConfig = newCVConfig();
    cvConfig.setIdentifier(monSource);
    CVConfig updated = cvConfigService.save(cvConfig);

    List<CVConfig> cvConfigs = verificationJobInstanceService.getCVConfigsForVerificationJob(job);

    assertThat(cvConfigs.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateDemoInstances() {
    VerificationJobInstance verificationJobInstance = newVerificationJobInstance();
    verificationJobInstance.setVerificationStatus(ActivityVerificationStatus.VERIFICATION_FAILED);
    cvConfigService.save(cvConfig);
    List<String> verificationJobInstanceIds =
        verificationJobInstanceService.createDemoInstances(Arrays.asList(verificationJobInstance));
    assertThat(verificationJobInstanceIds).hasSize(1);
    VerificationJobInstance saved =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceIds.get(0));
    assertThat(saved.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(saved.getVerificationStatus()).isEqualTo(ActivityVerificationStatus.VERIFICATION_FAILED);
    assertThat(saved.getCvConfigMap()).hasSize(1);
    assertThat(saved.getCvConfigMap()).containsKey(cvConfig.getUuid());
    Optional<Risk> riskScore =
        verificationJobInstanceAnalysisService.getLatestRiskScore(accountId, verificationJobInstanceIds.get(0));
    assertThat(riskScore).isPresent();
    assertThat(riskScore.get()).isEqualTo(Risk.UNHEALTHY);
  }

  private VerificationJobDTO newCanaryVerificationJobDTO() {
    CanaryVerificationJobDTO canaryVerificationJobDTO = new CanaryVerificationJobDTO();
    canaryVerificationJobDTO.setIdentifier(verificationJobIdentifier);
    canaryVerificationJobDTO.setJobName(generateUuid());
    canaryVerificationJobDTO.setDataSources(Lists.newArrayList(DataSourceType.SPLUNK));
    canaryVerificationJobDTO.setMonitoringSources(Arrays.asList(monitoringSourceIdentifier));
    canaryVerificationJobDTO.setSensitivity(Sensitivity.MEDIUM.name());
    canaryVerificationJobDTO.setServiceIdentifier(serviceIdentifier);
    canaryVerificationJobDTO.setOrgIdentifier(orgIdentifier);
    canaryVerificationJobDTO.setProjectIdentifier(projectIdentifier);
    canaryVerificationJobDTO.setEnvIdentifier(builderFactory.getContext().getEnvIdentifier());
    canaryVerificationJobDTO.setSensitivity(Sensitivity.MEDIUM.name());
    canaryVerificationJobDTO.setDuration("15m");
    return canaryVerificationJobDTO;
  }

  private VerificationJobDTO newHealthVerificationJobDTO() {
    HealthVerificationJobDTO healthVerificationJobDTO = new HealthVerificationJobDTO();
    healthVerificationJobDTO.setIdentifier(verificationJobIdentifier);
    healthVerificationJobDTO.setJobName(generateUuid());
    healthVerificationJobDTO.setDataSources(Lists.newArrayList(DataSourceType.SPLUNK));
    healthVerificationJobDTO.setMonitoringSources(Arrays.asList(monitoringSourceIdentifier));
    healthVerificationJobDTO.setServiceIdentifier(serviceIdentifier);
    healthVerificationJobDTO.setOrgIdentifier(orgIdentifier);
    healthVerificationJobDTO.setProjectIdentifier(projectIdentifier);
    healthVerificationJobDTO.setEnvIdentifier(builderFactory.getContext().getEnvIdentifier());
    healthVerificationJobDTO.setDuration("15m");
    return healthVerificationJobDTO;
  }

  private CVConfig newCVConfig() {
    return builderFactory.splunkCVConfigBuilder()
        .connectorIdentifier(connectorId)
        .identifier(monitoringSourceIdentifier)
        .build();
  }

  private VerificationJobInstance createVerificationJobInstance() {
    verificationJobService.create(accountId, newCanaryVerificationJobDTO());
    VerificationJob verificationJob = verificationJobService.getVerificationJob(
        accountId, orgIdentifier, projectIdentifier, verificationJobIdentifier);
    VerificationJobInstance verificationJobInstance =
        VerificationJobInstance.builder()
            .accountId(accountId)
            .executionStatus(ExecutionStatus.QUEUED)
            .deploymentStartTime(Instant.ofEpochMilli(deploymentStartTimeMs))
            .resolvedJob(verificationJob)
            .startTime(Instant.ofEpochMilli(deploymentStartTimeMs + Duration.ofMinutes(2).toMillis()))
            .build();
    verificationJobInstanceService.create(verificationJobInstance);
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstance.getUuid(), APP_DYNAMICS);
    return verificationJobInstance;
  }

  private VerificationJobInstance newVerificationJobInstance() {
    return builderFactory.verificationJobInstanceBuilder()
        .deploymentStartTime(Instant.ofEpochMilli(deploymentStartTimeMs))
        .startTime(Instant.ofEpochMilli(deploymentStartTimeMs + Duration.ofMinutes(2).toMillis()))
        .dataCollectionDelay(Duration.ofMinutes(5))
        .build();
  }

  private VerificationJobInstance createVerificationJobInstance(String verificationJobIdentifier, String envIdentifier,
      ExecutionStatus executionStatus, VerificationJobType verificationJobType) {
    verificationJobService.create(
        accountId, createVerificationJobDTO(verificationJobIdentifier, envIdentifier, verificationJobType));
    VerificationJob verificationJob = verificationJobService.getVerificationJob(
        accountId, orgIdentifier, projectIdentifier, verificationJobIdentifier);
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .accountId(accountId)
            .executionStatus(executionStatus)
            .deploymentStartTime(Instant.ofEpochMilli(deploymentStartTimeMs))
            .resolvedJob(verificationJob)
            .createdAt(deploymentStartTimeMs + Duration.ofMinutes(2).toMillis())
            .startTime(Instant.ofEpochMilli(deploymentStartTimeMs + Duration.ofMinutes(2).toMillis()))
            .verificationStatus(VERIFICATION_PASSED)
            .cvConfigMap(Collections.singletonMap(cvConfigId, cvConfig))
            .build();
    verificationJobInstanceService.create(verificationJobInstance);
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstance.getUuid(), APP_DYNAMICS);
    return verificationJobInstance;
  }

  private VerificationJobInstance createVerificationJobInstance(
      String verificationJobIdentifier, ExecutionStatus executionStatus) {
    VerificationJob verificationJob = verificationJobService.getVerificationJob(
        accountId, orgIdentifier, projectIdentifier, verificationJobIdentifier);
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .accountId(accountId)
            .executionStatus(executionStatus)
            .deploymentStartTime(Instant.ofEpochMilli(deploymentStartTimeMs))
            .resolvedJob(verificationJob)
            .createdAt(deploymentStartTimeMs + Duration.ofMinutes(2).toMillis())
            .startTime(Instant.ofEpochMilli(deploymentStartTimeMs + Duration.ofMinutes(2).toMillis()))
            .verificationStatus(VERIFICATION_PASSED)
            .cvConfigMap(Collections.singletonMap(cvConfigId, cvConfig))
            .build();
    verificationJobInstanceService.create(verificationJobInstance);
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstance.getUuid(), APP_DYNAMICS);
    return verificationJobInstance;
  }

  private VerificationJobInstance createVerificationJobInstance(String verificationJobIdentifier, String envIdentifier,
      ExecutionStatus executionStatus, VerificationJobType verificationJobType,
      ActivityVerificationStatus verificationStatus) {
    verificationJobService.create(
        accountId, createVerificationJobDTO(verificationJobIdentifier, envIdentifier, verificationJobType));
    VerificationJob verificationJob = verificationJobService.getVerificationJob(
        accountId, orgIdentifier, projectIdentifier, verificationJobIdentifier);
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .accountId(accountId)
            .executionStatus(executionStatus)
            .deploymentStartTime(Instant.ofEpochMilli(deploymentStartTimeMs))
            .resolvedJob(verificationJob)
            .createdAt(deploymentStartTimeMs + Duration.ofMinutes(timeCounter++).toMillis())
            .startTime(Instant.ofEpochMilli(deploymentStartTimeMs + Duration.ofMinutes(2).toMillis()))
            .verificationStatus(verificationStatus)
            .build();
    verificationJobInstanceService.create(verificationJobInstance);
    verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstance.getUuid(), APP_DYNAMICS);
    return verificationJobInstance;
  }

  private VerificationJobInstance createVerificationJobInstance(
      String verificationJobIdentifier, String envIdentifier) {
    return createVerificationJobInstance(verificationJobIdentifier, envIdentifier, ExecutionStatus.QUEUED, CANARY);
  }
  private VerificationJobDTO newTestVerificationJobDTO(String verificationJobIdentifier, String envIdentifier) {
    TestVerificationJobDTO verificationJobDTO = new TestVerificationJobDTO();
    fillCommonFields(verificationJobIdentifier, envIdentifier, verificationJobDTO);
    verificationJobDTO.setBaselineVerificationJobInstanceId(generateUuid());
    verificationJobDTO.setSensitivity(Sensitivity.MEDIUM.name());

    return verificationJobDTO;
  }

  private void fillCommonFields(
      String verificationJobIdentifier, String envIdentifier, VerificationJobDTO verificationJobDTO) {
    verificationJobDTO.setIdentifier(verificationJobIdentifier);
    verificationJobDTO.setJobName(generateUuid());
    verificationJobDTO.setDataSources(Lists.newArrayList(DataSourceType.SPLUNK));
    verificationJobDTO.setMonitoringSources(Arrays.asList(monitoringSourceIdentifier));
    verificationJobDTO.setServiceIdentifier(serviceIdentifier);
    verificationJobDTO.setOrgIdentifier(orgIdentifier);
    verificationJobDTO.setProjectIdentifier(projectIdentifier);
    verificationJobDTO.setEnvIdentifier(envIdentifier);
    verificationJobDTO.setDuration("15m");
  }

  private VerificationJobDTO createVerificationJobDTO(
      String verificationJobIdentifier, String envIdentifier, VerificationJobType verificationJobType) {
    switch (verificationJobType) {
      case TEST:
        return newTestVerificationJobDTO(verificationJobIdentifier, envIdentifier);
      case CANARY:
        return newCanaryVerificationJobDTO(verificationJobIdentifier, envIdentifier);
      default:
        throw new IllegalStateException("Not implemented: " + verificationJobType);
    }
  }

  private VerificationJobDTO newCanaryVerificationJobDTO(String verificationJobIdentifier, String envIdentifier) {
    CanaryVerificationJobDTO verificationJobDTO = new CanaryVerificationJobDTO();
    fillCommonFields(verificationJobIdentifier, envIdentifier, verificationJobDTO);
    verificationJobDTO.setSensitivity(Sensitivity.MEDIUM.name());
    return verificationJobDTO;
  }

  private MetricInfoBuilder getAppdMetricInfoBuilder(String suffix) {
    return MetricInfo.builder()
        .metricName("metricName" + suffix)
        .identifier("metric" + suffix)
        .metricPath("metricPath" + suffix)
        .baseFolder("baseFolder" + suffix)
        .sli(SLI.builder().enabled(true).build());
  }
}
