/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.beans.CVNGPerpetualTaskState.TASK_UNASSIGNED;
import static io.harness.cvng.beans.DataCollectionExecutionStatus.ABORTED;
import static io.harness.cvng.beans.DataCollectionExecutionStatus.FAILED;
import static io.harness.cvng.beans.DataCollectionExecutionStatus.QUEUED;
import static io.harness.cvng.beans.DataCollectionExecutionStatus.RUNNING;
import static io.harness.cvng.beans.DataCollectionExecutionStatus.SUCCESS;
import static io.harness.cvng.beans.DataCollectionExecutionStatus.WAITING;
import static io.harness.cvng.beans.DataSourceType.APP_DYNAMICS;
import static io.harness.cvng.core.entities.DeploymentDataCollectionTask.MAX_RETRY_COUNT;
import static io.harness.cvng.core.services.CVNextGenConstants.DATA_COLLECTION_DELAY;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.NEMANJA;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;
import static io.harness.rule.OwnerRule.VUK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.analysis.entities.VerificationTaskBase.VerificationTaskBaseKeys;
import io.harness.cvng.beans.AppDynamicsDataCollectionInfo;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.cvng.beans.DataCollectionTaskDTO;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.SplunkDataCollectionInfo;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO.LogLevel;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.DataCollectionTask.DataCollectionTaskKeys;
import io.harness.cvng.core.entities.DataCollectionTask.Type;
import io.harness.cvng.core.entities.DeploymentDataCollectionTask;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.SLIDataCollectionTask;
import io.harness.cvng.core.entities.ServiceGuardDataCollectionTask;
import io.harness.cvng.core.entities.SplunkCVConfig;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.DataCollectionSLIInfoMapper;
import io.harness.cvng.core.services.api.DataCollectionTaskManagementService;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.services.impl.ServiceLevelIndicatorServiceImpl;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ExecutionStatus;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Spy;

public class DataCollectionTaskServiceImplTest extends CvNextGenTestBase {
  @Inject private DataCollectionTaskService dataCollectionTaskService;
  @Inject @Spy private DataCollectionTaskServiceImpl dataCollectionTaskServiceImpl;
  @Inject private ServiceGuardDataCollectionTaskServiceImpl serviceGuardDataCollectionTaskService;

  @Inject private SLIDataCollectionTaskServiceImpl sliDataCollectionTaskService;
  @Inject private HPersistence hPersistence;

  @Inject private HealthSourceService healthSourceService;
  @Inject private CVConfigService cvConfigService;
  @Inject private MetricPackService metricPackService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private Clock clock;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private CVNGLogService cvngLogService;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Inject private MonitoredServiceService monitoredServiceService;

  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  private String cvConfigId;
  private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  private Instant fakeNow;
  private String dataCollectionWorkerId;
  private String verificationTaskId;

  private String sliVerificationTaskId;
  private CVConfig cvConfig;
  private BuilderFactory builderFactory;

  private String monitoredServiceIdentifier;

  private ServiceLevelIndicator serviceLevelIndicator;
  @Inject private Map<Type, DataCollectionTaskManagementService> dataCollectionTaskManagementServiceMapBinder;

  @Inject private Map<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;

  @Before
  public void setupTests() throws IllegalAccessException {
    initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    cvConfig = cvConfigService.save(createCVConfig());
    cvConfigId = cvConfig.getUuid();
    verificationTaskId = verificationTaskService.getServiceGuardVerificationTaskId(accountId, cvConfigId);
    serviceLevelIndicator = createSLI();
    sliVerificationTaskId =
        verificationTaskService.getSLIVerificationTaskId(accountId, serviceLevelIndicator.getUuid());
    dataCollectionTaskService = spy(dataCollectionTaskService);
    FieldUtils.writeField(dataCollectionTaskService, "clock", clock, true);
    FieldUtils.writeField(verificationJobInstanceService, "clock", clock, true);
    FieldUtils.writeField(
        dataCollectionTaskService, "verificationJobInstanceService", verificationJobInstanceService, true);
    FieldUtils.writeField(
        dataCollectionTaskService, "monitoringSourcePerpetualTaskService", monitoringSourcePerpetualTaskService, true);
    fakeNow = clock.instant();
    dataCollectionWorkerId = monitoringSourcePerpetualTaskService.getLiveMonitoringWorkerId(cvConfig.getAccountId(),
        cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier(), cvConfig.getConnectorIdentifier(),
        cvConfig.getIdentifier());
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testSave_handleRecoverNextTask() throws IllegalAccessException {
    DataCollectionTask dataCollectionTask = create(SUCCESS);
    clock = Clock.fixed(Instant.now().plus(Duration.ofMinutes(20)), ZoneOffset.UTC);

    FieldUtils.writeField(dataCollectionTaskServiceImpl, "clock", clock, true);
    FieldUtils.writeField(serviceGuardDataCollectionTaskService, "clock", clock, true);

    dataCollectionTaskServiceImpl.save(dataCollectionTask);
    serviceGuardDataCollectionTaskService.handleCreateNextTask(cvConfig);

    DataCollectionTask retrievedDataCollectionTask = getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(retrievedDataCollectionTask).isNotNull();
    assertThat(retrievedDataCollectionTask.getStatus()).isEqualTo(SUCCESS);

    DataCollectionTask dataCollectionTaskQuery = hPersistence.createQuery(DataCollectionTask.class)
                                                     .filter(DataCollectionTaskKeys.status, QUEUED)
                                                     .filter(DataCollectionTaskKeys.dataCollectionWorkerId,
                                                         retrievedDataCollectionTask.getDataCollectionWorkerId())
                                                     .get();

    assertThat(dataCollectionTaskQuery).isNotNull();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testSave_handleRecoverNextLastUpdatedStatusQueued() {
    DataCollectionTask dataCollectionTask = create(QUEUED);

    dataCollectionTaskServiceImpl.save(dataCollectionTask);
    serviceGuardDataCollectionTaskService.handleCreateNextTask(cvConfig);

    DataCollectionTask retrievedDataCollectionTask = getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(retrievedDataCollectionTask).isNotNull();
    assertThat(retrievedDataCollectionTask.getStatus()).isEqualTo(QUEUED);

    List<DataCollectionTask> dataCollectionTaskQuery = hPersistence.createQuery(DataCollectionTask.class)
                                                           .filter(DataCollectionTaskKeys.status, QUEUED)
                                                           .filter(DataCollectionTaskKeys.dataCollectionWorkerId,
                                                               retrievedDataCollectionTask.getDataCollectionWorkerId())
                                                           .asList();

    assertThat(dataCollectionTaskQuery).hasSize(1);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testSave_handleRecoverNextTaskIsAfterTwoMinutes() throws IllegalAccessException {
    Instant currentTime = clock.instant();
    Instant instant = currentTime.minusMillis(Duration.ofMinutes(10).toMillis());
    DataCollectionTask dataCollectionTask = create();
    clock = Clock.fixed(instant, ZoneOffset.UTC);

    FieldUtils.writeField(dataCollectionTaskServiceImpl, "clock", clock, true);

    dataCollectionTaskServiceImpl.save(dataCollectionTask);
    serviceGuardDataCollectionTaskService.handleCreateNextTask(cvConfig);

    DataCollectionTask retrievedDataCollectionTask = getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(retrievedDataCollectionTask).isNotNull();

    List<DataCollectionTask> dataCollectionTaskQuery = hPersistence.createQuery(DataCollectionTask.class)
                                                           .filter(DataCollectionTaskKeys.status, QUEUED)
                                                           .filter(DataCollectionTaskKeys.dataCollectionWorkerId,
                                                               retrievedDataCollectionTask.getDataCollectionWorkerId())
                                                           .asList();

    assertThat(dataCollectionTaskQuery).hasSize(1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testSave_dataCollectionTask() {
    DataCollectionTask dataCollectionTask = create();
    dataCollectionTaskService.save(dataCollectionTask);
    assertThat(dataCollectionTask.getStatus()).isEqualTo(QUEUED);
    DataCollectionTask updatedDataCollectionTask = getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(updatedDataCollectionTask.getStatus()).isEqualTo(QUEUED);
    assertThat(updatedDataCollectionTask.getVerificationTaskId()).isEqualTo(dataCollectionTask.getVerificationTaskId());
    assertThat(updatedDataCollectionTask.shouldQueueAnalysis()).isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextTask_validAfterWhenNotSetOnGettingNextTask() {
    DataCollectionTask dataCollectionTask = create();
    dataCollectionTaskService.save(dataCollectionTask);
    Optional<DataCollectionTask> nextTask =
        dataCollectionTaskService.getNextTask(dataCollectionTask.getAccountId(), dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isTrue();
    assertThat(nextTask.get().getUuid()).isEqualTo(dataCollectionTask.getUuid());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextTaskDTO_dtoCreation() {
    DataCollectionTask dataCollectionTask = create();
    dataCollectionTaskService.save(dataCollectionTask);
    Optional<DataCollectionTaskDTO> nextTask =
        dataCollectionTaskService.getNextTaskDTO(dataCollectionTask.getAccountId(), dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isTrue();
    assertThat(nextTask.get().getUuid()).isEqualTo(dataCollectionTask.getUuid());
    assertThat(nextTask.get().getAccountId()).isEqualTo(dataCollectionTask.getAccountId());
    assertThat(nextTask.get().getDataCollectionInfo()).isEqualTo(dataCollectionTask.getDataCollectionInfo());
    assertThat(nextTask.get().getStartTime()).isEqualTo(dataCollectionTask.getStartTime());
    assertThat(nextTask.get().getEndTime()).isEqualTo(dataCollectionTask.getEndTime());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextTaskDTO_notPresent() {
    DataCollectionTask dataCollectionTask = create();
    dataCollectionTaskService.save(dataCollectionTask);
    Optional<DataCollectionTaskDTO> nextTask =
        dataCollectionTaskService.getNextTaskDTO(dataCollectionTask.getAccountId(), "invalid-worker-id");
    assertThat(nextTask.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextTask_validAfterWhenSetToFutureOnGettingNextTask() {
    DataCollectionTask dataCollectionTask = create();
    dataCollectionTask.setValidAfter(Instant.now().plus(10, ChronoUnit.MINUTES));
    dataCollectionTaskService.save(dataCollectionTask);
    Optional<DataCollectionTask> nextTask =
        dataCollectionTaskService.getNextTask(dataCollectionTask.getAccountId(), dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextTask_validAfterWhenSetToPastOnGettingNextTask() {
    DataCollectionTask dataCollectionTask = create();
    dataCollectionTask.setValidAfter(clock.instant().minus(10, ChronoUnit.MINUTES));
    dataCollectionTaskService.save(dataCollectionTask);
    Optional<DataCollectionTask> nextTask =
        dataCollectionTaskService.getNextTask(dataCollectionTask.getAccountId(), dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isTrue();
    assertThat(nextTask.get().getUuid()).isEqualTo(dataCollectionTask.getUuid());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextTask_ExecutionStatusFilterOnGettingNextTask() {
    DataCollectionTask dataCollectionTask = create();
    dataCollectionTask.setStatus(DataCollectionExecutionStatus.RUNNING);
    hPersistence.save(dataCollectionTask);
    Optional<DataCollectionTask> nextTask =
        dataCollectionTaskService.getNextTask(dataCollectionTask.getAccountId(), dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isFalse();
    dataCollectionTask.setStatus(QUEUED);
    hPersistence.save(dataCollectionTask);
    nextTask = dataCollectionTaskService.getNextTask(dataCollectionTask.getAccountId(), dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isTrue();
    assertThat(nextTask.get().getUuid()).isEqualTo(dataCollectionTask.getUuid());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextTask_executionStatusUpdateOnGettingNextTask() {
    DataCollectionTask dataCollectionTask = createAndSave(QUEUED);
    Optional<DataCollectionTask> nextTask =
        dataCollectionTaskService.getNextTask(dataCollectionTask.getAccountId(), dataCollectionWorkerId);
    assertThat(nextTask.get().getStatus()).isEqualTo(DataCollectionExecutionStatus.RUNNING);
    DataCollectionTask reloadedDataCollectionTask = getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(reloadedDataCollectionTask.getStatus()).isEqualTo(DataCollectionExecutionStatus.RUNNING);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextTask_orderOnGettingNextTask() throws InterruptedException {
    List<DataCollectionTask> dataCollectionTasks = new ArrayList<>();
    long time = clock.millis();
    for (int i = 0; i < 5; i++) {
      DataCollectionTask dataCollectionTask = create();
      // dataCollectionTask.setLastUpdatedAt(time + i);
      // TODO: find a way to set lastUpdatedAt.
      Thread.sleep(1);
      dataCollectionTasks.add(dataCollectionTask);
      hPersistence.save(dataCollectionTask);
    }
    Thread.sleep(1);
    // dataCollectionTasks.get(0).setLastUpdatedAt(time + 6);
    hPersistence.save(dataCollectionTasks.get(0));
    for (int i = 1; i < 5; i++) {
      Optional<DataCollectionTask> nextTask = dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId);
      assertThat(nextTask.isPresent()).isTrue();
      assertThat(nextTask.get().getUuid()).isEqualTo(dataCollectionTasks.get(i).getUuid());
    }
    Optional<DataCollectionTask> nextTask = dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isTrue();
    assertThat(nextTask.get().getUuid()).isEqualTo(dataCollectionTasks.get(0).getUuid());
    assertThat(dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId).isPresent()).isFalse();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetNextTask_taskPickedUpAgainAfterNotCompletedForMoreThanFiveMinutes() throws IllegalAccessException {
    DataCollectionTask dataCollectionTask = create(DataCollectionExecutionStatus.RUNNING);
    clock = Clock.fixed(Instant.now().plus(15, ChronoUnit.MINUTES), ZoneOffset.UTC);
    FieldUtils.writeField(dataCollectionTaskService, "clock", clock, true);
    hPersistence.save(dataCollectionTask);
    Optional<DataCollectionTask> nextTask = dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isTrue();
    assertThat(nextTask.get().getRetryCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetNextTask_withBothQueuedAndRunningTask() throws IllegalAccessException {
    clock = Clock.fixed(Instant.now().plus(6, ChronoUnit.MINUTES), ZoneOffset.UTC);
    FieldUtils.writeField(dataCollectionTaskService, "clock", clock, true);

    DataCollectionTask queuedDataCollectionTask = create(QUEUED);
    DataCollectionTask runningDataCollectionTask = create(DataCollectionExecutionStatus.RUNNING);
    hPersistence.save(queuedDataCollectionTask);
    hPersistence.save(runningDataCollectionTask);

    Optional<DataCollectionTask> nextTask = dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isTrue();
    nextTask = dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isTrue();
    clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    FieldUtils.writeField(dataCollectionTaskService, "clock", clock, true);
    nextTask = dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetNextTask_withAbortedTask() throws IllegalAccessException {
    DataCollectionTask abortedDataCollectionTask = create(ABORTED);
    hPersistence.save(abortedDataCollectionTask);

    Optional<DataCollectionTask> nextTask = dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void testGetNextTask_withExceededRetryCountDeployment() {
    DataCollectionTask dataCollectionTask = create(QUEUED, Type.DEPLOYMENT);
    dataCollectionTask.setRetryCount(MAX_RETRY_COUNT + 1);
    hPersistence.save(dataCollectionTask);
    Optional<DataCollectionTask> nextTask = dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetNextTask_withExceededRetryCountSLI() {
    DataCollectionTask dataCollectionTask = create(QUEUED, Type.SLI);
    dataCollectionTask.setRetryCount(SLIDataCollectionTask.MAX_RETRY_COUNT + 1);
    hPersistence.save(dataCollectionTask);
    Optional<DataCollectionTask> nextTask = dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetNextTask_forSLI() {
    DataCollectionTask dataCollectionTask = create(QUEUED, Type.SLI);
    dataCollectionTask.setRetryCount(SLIDataCollectionTask.MAX_RETRY_COUNT);
    hPersistence.save(dataCollectionTask);
    Optional<DataCollectionTask> nextTask = dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetNextTask_withExceededRetryCountServiceGuard() {
    DataCollectionTask dataCollectionTask = create(QUEUED, Type.SERVICE_GUARD);
    dataCollectionTask.setRetryCount(MAX_RETRY_COUNT + 1);
    hPersistence.save(dataCollectionTask);
    Optional<DataCollectionTask> nextTask = dataCollectionTaskService.getNextTask(accountId, dataCollectionWorkerId);
    assertThat(nextTask.isPresent()).isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetValidUntil_dataCollectionTaskValidUntilIsBeingSetToOneMonth() {
    DataCollectionTask dataCollectionTask = create(DataCollectionExecutionStatus.SUCCESS);
    assertThat(dataCollectionTask.getValidUntil().getTime() > Instant.now().toEpochMilli()).isTrue();
    assertThat(dataCollectionTask.getValidUntil().getTime() >= Instant.now().plus(29, ChronoUnit.DAYS).toEpochMilli())
        .isTrue();
    assertThat(dataCollectionTask.getValidUntil().getTime() <= Instant.now().plus(31, ChronoUnit.DAYS).toEpochMilli())
        .isTrue();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetDataCollectionTask_byId() {
    DataCollectionTask dataCollectionTask = createAndSave(DataCollectionExecutionStatus.SUCCESS);
    assertThat(dataCollectionTask.getUuid())
        .isEqualTo(dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid()).getUuid());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_taskStatusToSuccess() {
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING);
    DataCollectionTaskResult result = DataCollectionTaskDTO.DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.SUCCESS)
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .build();
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask updated = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(updated.getStatus()).isEqualTo(DataCollectionExecutionStatus.SUCCESS);
    assertThat(updated.getException()).isNull();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_withExecutionLogService() {
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING);
    DataCollectionTaskResult result = DataCollectionTaskDTO.DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.SUCCESS)
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .build();
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask updated = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(updated.getStatus()).isEqualTo(DataCollectionExecutionStatus.SUCCESS);
    assertThat(updated.getException()).isNull();
    List<ExecutionLogDTO> cvngLogs =
        cvngLogService.getExecutionLogDTOs(accountId, dataCollectionTask.getVerificationTaskId());
    assertThat(cvngLogs).hasSize(2);
    assertThat(cvngLogs.get(0).getLog()).isEqualTo("Data collection task status: SUCCESS");
    assertThat(cvngLogs.get(0).getLogLevel()).isEqualTo(LogLevel.INFO);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_multipleCallsWithSuccessAndThenFailure() {
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING);
    DataCollectionTaskResult result = DataCollectionTaskDTO.DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.SUCCESS)
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .build();
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTaskResult failure = DataCollectionTaskDTO.DataCollectionTaskResult.builder()
                                           .status(DataCollectionExecutionStatus.FAILED)
                                           .dataCollectionTaskId(dataCollectionTask.getUuid())
                                           .exception("socket timeout")
                                           .build();
    DataCollectionTask updatedTask = hPersistence.get(DataCollectionTask.class, dataCollectionTask.getUuid());
    assertThat(updatedTask.getStatus()).isEqualTo(SUCCESS);
    assertThat(updatedTask.getException()).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_taskStatusSuccessShouldCreateNextTask() {
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING);
    DataCollectionTaskResult result = DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.SUCCESS)
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .build();
    CVConfig cvConfig = cvConfigService.get(cvConfigId);
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask nextTask = hPersistence.createQuery(DataCollectionTask.class)
                                      .filter(DataCollectionTaskKeys.accountId, accountId)
                                      .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                      .filter(DataCollectionTaskKeys.status, QUEUED)
                                      .order(VerificationTaskBaseKeys.lastUpdatedAt)
                                      .get();

    assertThat(nextTask.getStatus()).isEqualTo(QUEUED);
    assertThat(nextTask.getStartTime()).isEqualTo(dataCollectionTask.getEndTime());
    assertThat(nextTask.getEndTime()).isEqualTo(dataCollectionTask.getEndTime().plus(5, ChronoUnit.MINUTES));
    assertThat(nextTask.getDataCollectionWorkerId())
        .isEqualTo(monitoringSourcePerpetualTaskService.getLiveMonitoringWorkerId(
            accountId, orgIdentifier, projectIdentifier, cvConfig.getConnectorIdentifier(), cvConfig.getIdentifier()));
    assertThat(nextTask.getValidAfter())
        .isEqualTo(dataCollectionTask.getEndTime().plus(5, ChronoUnit.MINUTES).plus(DATA_COLLECTION_DELAY));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_disabledCVConfigShouldNotCreateNextTask() {
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING);
    DataCollectionTaskResult result = DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.SUCCESS)
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .build();
    CVConfig cvConfig = cvConfigService.get(cvConfigId);
    cvConfigService.setHealthMonitoringFlag(
        accountId, orgIdentifier, projectIdentifier, Collections.singletonList(cvConfig.getIdentifier()), false);
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask nextTask = hPersistence.createQuery(DataCollectionTask.class)
                                      .filter(DataCollectionTaskKeys.accountId, accountId)
                                      .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                      .filter(DataCollectionTaskKeys.status, QUEUED)
                                      .order(VerificationTaskBaseKeys.lastUpdatedAt)
                                      .get();

    assertThat(nextTask).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_ifLastSuccessIsBefore2Hour() throws IllegalAccessException {
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING);
    DataCollectionTaskResult result = DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.SUCCESS)
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .build();
    CVConfig cvConfig = cvConfigService.get(cvConfigId);

    Clock clock = Clock.fixed(this.clock.instant().plus(Duration.ofHours(3)), ZoneOffset.UTC);
    FieldUtils.writeField(dataCollectionTaskService, "clock", clock, true);
    serviceGuardDataCollectionTaskService =
        (ServiceGuardDataCollectionTaskServiceImpl) dataCollectionTaskManagementServiceMapBinder.get(
            Type.SERVICE_GUARD);
    FieldUtils.writeField(serviceGuardDataCollectionTaskService, "clock", clock, true);
    FieldUtils.writeField(dataCollectionTaskService, "dataCollectionTaskManagementServiceMapBinder",
        dataCollectionTaskManagementServiceMapBinder, true);
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask nextTask = hPersistence.createQuery(DataCollectionTask.class)
                                      .filter(DataCollectionTaskKeys.accountId, accountId)
                                      .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                      .filter(DataCollectionTaskKeys.status, QUEUED)
                                      .order(VerificationTaskBaseKeys.lastUpdatedAt)
                                      .get();

    assertThat(nextTask.getStatus()).isEqualTo(QUEUED);
    Instant expectedStartTime = CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().plus(1, ChronoUnit.HOURS);
    assertThat(nextTask.getStartTime()).isEqualTo(expectedStartTime);
    assertThat(nextTask.getEndTime()).isEqualTo(expectedStartTime.plus(5, ChronoUnit.MINUTES));
    assertThat(nextTask.getDataCollectionWorkerId())
        .isEqualTo(monitoringSourcePerpetualTaskService.getLiveMonitoringWorkerId(
            accountId, orgIdentifier, projectIdentifier, cvConfig.getConnectorIdentifier(), cvConfig.getIdentifier()));
    assertThat(nextTask.getValidAfter())
        .isEqualTo(expectedStartTime.plus(5, ChronoUnit.MINUTES).plus(DATA_COLLECTION_DELAY));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_retryFailedTask() {
    Exception exception = new RuntimeException("exception msg");
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING);
    DataCollectionTaskResult result = DataCollectionTaskDTO.DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.FAILED)
                                          .stacktrace(ExceptionUtils.getStackTrace(exception))
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .exception(exception.getMessage())
                                          .build();
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask updated = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(updated.getStatus()).isEqualTo(QUEUED);
    assertThat(updated.getRetryCount()).isEqualTo(1);
    assertThat(updated.getException()).isEqualTo(exception.getMessage());
    assertThat(updated.getStacktrace()).isEqualTo(ExceptionUtils.getStackTrace(exception));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  @Ignore("TODO: Fix with SRM-11813")
  public void testUpdateTaskStatus_executionLogForFailedTask() {
    Exception exception = new RuntimeException("exception msg");
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING);
    DataCollectionTaskResult result =
        DataCollectionTaskResult.builder()
            .status(DataCollectionExecutionStatus.FAILED)
            .stacktrace(ExceptionUtils.getStackTrace(exception))
            .dataCollectionTaskId(dataCollectionTask.getUuid())
            .exception(exception.getMessage())
            .executionLogs(Collections.singletonList(
                DataCollectionTaskResult.ExecutionLog.builder().log("log message").logLevel(LogLevel.INFO).build()))
            .build();
    dataCollectionTaskService.updateTaskStatus(result);
    List<ExecutionLogDTO> cvngLogs =
        cvngLogService.getExecutionLogDTOs(accountId, dataCollectionTask.getVerificationTaskId());
    assertThat(cvngLogs).hasSize(4);
    assertThat(cvngLogs.stream().map(cvngLogDTO -> cvngLogDTO.getLog()).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("Data collection task status: FAILED",
            "Data collection task failed with exception: exception msg", "log message",
            "Data collection task status: QUEUED");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_deploymentDataCollectionDontRetryIfRetryCountExceeds() {
    Exception exception = new RuntimeException("exception msg");
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING, Type.DEPLOYMENT);
    DataCollectionTaskResult result = DataCollectionTaskDTO.DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.FAILED)
                                          .stacktrace(ExceptionUtils.getStackTrace(exception))
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .exception(exception.getMessage())
                                          .build();
    MAX_RETRY_COUNT = 2;
    int maxRetry = MAX_RETRY_COUNT;
    IntStream.range(0, maxRetry).forEach(index -> {
      dataCollectionTaskService.updateTaskStatus(result);
      DataCollectionTask updated = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
      assertThat(updated.getStatus()).isEqualTo(QUEUED);
      assertThat(updated.getRetryCount()).isEqualTo(index + 1);
      assertThat(updated.getException()).isEqualTo(exception.getMessage());
      assertThat(updated.getStacktrace()).isEqualTo(ExceptionUtils.getStackTrace(exception));
      markRunning(updated.getUuid());
    });
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask updated = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(updated.getStatus()).isEqualTo(DataCollectionExecutionStatus.FAILED);
    assertThat(updated.getRetryCount()).isEqualTo(maxRetry);
    assertThat(updated.getException()).isEqualTo(exception.getMessage());
    assertThat(updated.getStacktrace()).isEqualTo(ExceptionUtils.getStackTrace(exception));

    VerificationJobInstance jobInstanceWithProgressLog =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstance.getUuid());
    assertThat(jobInstanceWithProgressLog.getProgressLogs()).hasSize(1);
    assertThat(jobInstanceWithProgressLog.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(jobInstanceWithProgressLog.getProgressLogs().get(0))
        .isEqualTo(VerificationJobInstance.DataCollectionProgressLog.builder()
                       .executionStatus(DataCollectionExecutionStatus.FAILED)
                       .verificationTaskId(verificationTaskId)
                       .startTime(dataCollectionTask.getStartTime())
                       .createdAt(clock.instant())
                       .endTime(dataCollectionTask.getEndTime())
                       .log("Data collection failed with exception: exception msg")
                       .isFinalState(false)
                       .build());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_sliDataCollectionDontRetryIfRetryCountExceeds() {
    Exception exception = new RuntimeException("exception msg");
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING, Type.SLI);
    DataCollectionTaskResult result = DataCollectionTaskDTO.DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.FAILED)
                                          .stacktrace(ExceptionUtils.getStackTrace(exception))
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .exception(exception.getMessage())
                                          .build();
    int maxRetry = SLIDataCollectionTask.MAX_RETRY_COUNT;
    IntStream.range(0, maxRetry).forEach(index -> {
      dataCollectionTaskService.updateTaskStatus(result);
      DataCollectionTask updated = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
      assertThat(updated.getStatus()).isEqualTo(QUEUED);
      assertThat(updated.getRetryCount()).isEqualTo(index + 1);
      assertThat(updated.getException()).isEqualTo(exception.getMessage());
      assertThat(updated.getStacktrace()).isEqualTo(ExceptionUtils.getStackTrace(exception));
      markRunning(updated.getUuid());
    });
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask updated = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(updated.getStatus()).isEqualTo(FAILED);
    assertThat(updated.getRetryCount()).isEqualTo(10);
    assertThat(updated.getException()).isEqualTo(exception.getMessage());
    assertThat(updated.getStacktrace()).isEqualTo(ExceptionUtils.getStackTrace(exception));
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_deploymentSuccessful() {
    VerificationJobInstance verificationJobInstance = createVerificationJobInstance();
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING, Type.DEPLOYMENT);
    DataCollectionTaskResult result = DataCollectionTaskDTO.DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.SUCCESS)
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .build();
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask updated = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(updated.getStatus()).isEqualTo(SUCCESS);
    assertThat(updated.getRetryCount()).isEqualTo(0);
    assertThat(updated.getException()).isNull();
    assertThat(updated.getStacktrace()).isNull();
    VerificationJobInstance jobInstanceWithProgressLog =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstance.getUuid());
    assertThat(jobInstanceWithProgressLog.getProgressLogs()).hasSize(1);
    assertThat(jobInstanceWithProgressLog.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    assertThat(jobInstanceWithProgressLog.getProgressLogs().get(0))
        .isEqualTo(VerificationJobInstance.DataCollectionProgressLog.builder()
                       .executionStatus(SUCCESS)
                       .verificationTaskId(verificationTaskId)
                       .startTime(dataCollectionTask.getStartTime())
                       .createdAt(clock.instant())
                       .endTime(dataCollectionTask.getEndTime())
                       .log("Data collection task successful")
                       .isFinalState(false)
                       .build());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_serviceGuardCreateNewTaskIfRetryTaskIsTooOld() throws IllegalAccessException {
    Exception exception = new RuntimeException("exception msg");
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING, Type.SERVICE_GUARD);
    DataCollectionTaskResult result = DataCollectionTaskDTO.DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.FAILED)
                                          .stacktrace(ExceptionUtils.getStackTrace(exception))
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .exception(exception.getMessage())
                                          .build();
    dataCollectionTaskService.updateTaskStatus(result);
    markRunning(dataCollectionTask.getUuid());
    Clock clock = Clock.fixed(this.clock.instant().plus(Duration.ofHours(3)), ZoneOffset.UTC);
    FieldUtils.writeField(dataCollectionTaskService, "clock", clock, true);
    FieldUtils.writeField(serviceGuardDataCollectionTaskService, "clock", clock, true);
    serviceGuardDataCollectionTaskService =
        (ServiceGuardDataCollectionTaskServiceImpl) dataCollectionTaskManagementServiceMapBinder.get(
            Type.SERVICE_GUARD);
    FieldUtils.writeField(serviceGuardDataCollectionTaskService, "clock", clock, true);
    FieldUtils.writeField(dataCollectionTaskService, "dataCollectionTaskManagementServiceMapBinder",
        dataCollectionTaskManagementServiceMapBinder, true);
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask updated = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(updated.getStatus()).isEqualTo(DataCollectionExecutionStatus.FAILED);
    assertThat(updated.getRetryCount()).isEqualTo(1);
    assertThat(updated.getException()).isEqualTo("exception msg");
    DataCollectionTask newTask =
        hPersistence.createQuery(DataCollectionTask.class).filter(DataCollectionTaskKeys.status, QUEUED).get();
    assertThat(newTask.getStatus()).isEqualTo(QUEUED);
    assertThat(newTask.getRetryCount()).isEqualTo(1);
    assertThat(newTask.getValidAfter())
        .isEqualTo(
            CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().plus(3, ChronoUnit.HOURS).plus(10, ChronoUnit.SECONDS));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_sliCreateNewTaskIfRetryTaskIsTooOld() throws IllegalAccessException {
    ServiceLevelIndicatorService serviceLevelIndicatorServiceMock = mock(ServiceLevelIndicatorServiceImpl.class);
    Exception exception = new RuntimeException("exception msg");
    DataCollectionTask dataCollectionTask = createAndSave(RUNNING, Type.SLI);
    DataCollectionTaskResult result = DataCollectionTaskDTO.DataCollectionTaskResult.builder()
                                          .status(DataCollectionExecutionStatus.FAILED)
                                          .stacktrace(ExceptionUtils.getStackTrace(exception))
                                          .dataCollectionTaskId(dataCollectionTask.getUuid())
                                          .exception(exception.getMessage())
                                          .build();
    dataCollectionTaskService.updateTaskStatus(result);
    markRunning(dataCollectionTask.getUuid());
    Clock clock = Clock.fixed(this.clock.instant().plus(Duration.ofHours(25)), ZoneOffset.UTC);
    FieldUtils.writeField(dataCollectionTaskService, "clock", clock, true);
    FieldUtils.writeField(sliDataCollectionTaskService, "clock", clock, true);
    sliDataCollectionTaskService =
        (SLIDataCollectionTaskServiceImpl) dataCollectionTaskManagementServiceMapBinder.get(Type.SLI);
    doCallRealMethod().when(serviceLevelIndicatorServiceMock).get(any());
    doCallRealMethod().when(serviceLevelIndicatorServiceMock).fetchCVConfigForSLI((ServiceLevelIndicator) any());
    FieldUtils.writeField(serviceLevelIndicatorServiceMock, "healthSourceService", healthSourceService, true);
    FieldUtils.writeField(serviceLevelIndicatorServiceMock, "hPersistence", hPersistence, true);
    FieldUtils.writeField(sliDataCollectionTaskService, "clock", clock, true);
    FieldUtils.writeField(sliDataCollectionTaskService, "dataSourceTypeDataCollectionInfoMapperMap",
        dataSourceTypeDataCollectionInfoMapperMap, true);
    FieldUtils.writeField(
        dataCollectionTaskService, "serviceLevelIndicatorService", serviceLevelIndicatorServiceMock, true);
    FieldUtils.writeField(
        sliDataCollectionTaskService, "serviceLevelIndicatorService", serviceLevelIndicatorServiceMock, true);
    FieldUtils.writeField(dataCollectionTaskService, "dataCollectionTaskManagementServiceMapBinder",
        dataCollectionTaskManagementServiceMapBinder, true);
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask updated = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    verify(serviceLevelIndicatorServiceMock)
        .enqueueDataCollectionFailureInstanceAndTriggerAnalysis(sliVerificationTaskId,
            fakeNow.minus(Duration.ofMinutes(7)), fakeNow.minus(Duration.ofMinutes(2)), serviceLevelIndicator);
    verify(serviceLevelIndicatorServiceMock)
        .enqueueDataCollectionFailureInstanceAndTriggerAnalysis(sliVerificationTaskId,
            fakeNow.minus(Duration.ofMinutes(1)), fakeNow.plus(Duration.ofMinutes(59)), serviceLevelIndicator);
    assertThat(updated.getStatus()).isEqualTo(DataCollectionExecutionStatus.FAILED);
    assertThat(updated.getRetryCount()).isEqualTo(1);
    assertThat(updated.getException()).isEqualTo("exception msg");
    DataCollectionTask newTask =
        hPersistence.createQuery(DataCollectionTask.class).filter(DataCollectionTaskKeys.status, QUEUED).get();
    assertThat(newTask.getStatus()).isEqualTo(QUEUED);
    assertThat(newTask.getRetryCount()).isEqualTo(0);
    assertThat(newTask.getValidAfter())
        .isEqualTo(
            CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().plus(25, ChronoUnit.HOURS).plus(5, ChronoUnit.SECONDS));
  }
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testUpdateTaskStatus_DeploymentWithAbortedNextTask() throws IllegalAccessException {
    createVerificationJobInstance();
    DataCollectionTask dataCollectionTask2 = createAndSave(ABORTED, Type.DEPLOYMENT);
    DataCollectionTask dataCollectionTask1 = create(RUNNING, Type.DEPLOYMENT);
    dataCollectionTask1.setNextTaskId(dataCollectionTask2.getUuid());
    hPersistence.save(dataCollectionTask1);
    DataCollectionTaskResult result = DataCollectionTaskDTO.DataCollectionTaskResult.builder()
                                          .status(SUCCESS)
                                          .dataCollectionTaskId(dataCollectionTask1.getUuid())
                                          .build();
    dataCollectionTaskService.updateTaskStatus(result);
    DataCollectionTask dataCollectionTask2FromDb =
        dataCollectionTaskService.getDataCollectionTask(dataCollectionTask2.getUuid());
    // Assert that aborted task is not queued
    assertThat(dataCollectionTask2FromDb.getStatus()).isEqualTo(ABORTED);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpdateRetry() throws IllegalAccessException {
    DataCollectionTask dataCollectionTask = createAndSave(QUEUED);
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();
    Clock clock = Clock.fixed(this.clock.instant().plus(Duration.ofHours(3)), ZoneOffset.UTC);
    FieldUtils.writeField(dataCollectionTaskService, "clock", clock, true);

    dataCollectionTaskService.updateRetry(projectParams, dataCollectionTask.getUuid());
    DataCollectionTask newTask = hPersistence.get(DataCollectionTask.class, dataCollectionTask.getUuid());

    assertThat(newTask.getRetryCount()).isEqualTo(0);
    assertThat(newTask.getValidAfter())
        .isEqualTo(
            CVNGTestConstants.FIXED_TIME_FOR_TESTS.instant().plus(3, ChronoUnit.HOURS).plus(300, ChronoUnit.SECONDS));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void handleCreateNextTask_forFirstTaskAndMetricsConfig() {
    AppDynamicsCVConfig cvConfig = getCVConfig();
    cvConfig.setCreatedAt(1);
    serviceGuardDataCollectionTaskService.handleCreateNextTask(cvConfig);
    DataCollectionTask savedTask = hPersistence.createQuery(DataCollectionTask.class)
                                       .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                       .get();
    assertThat(savedTask.getStatus()).isEqualTo(QUEUED);
    assertThat(savedTask.getDataCollectionInfo()).isInstanceOf(AppDynamicsDataCollectionInfo.class);
    assertThat(savedTask.getEndTime()).isEqualTo(cvConfig.getFirstTimeDataCollectionTimeRange().getEndTime());
    assertThat(savedTask.getStartTime()).isEqualTo(cvConfig.getFirstTimeDataCollectionTimeRange().getStartTime());
    assertThat(savedTask.getVerificationTaskId())
        .isEqualTo(verificationTaskService.getServiceGuardVerificationTaskId(accountId, cvConfigId));
    assertThat(savedTask.getDataCollectionWorkerId())
        .isEqualTo(monitoringSourcePerpetualTaskService.getLiveMonitoringWorkerId(
            accountId, orgIdentifier, projectIdentifier, cvConfig.getConnectorIdentifier(), cvConfig.getIdentifier()));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testAbortDataCollectionTasks() {
    List<DataCollectionTask> dataCollectionTasks =
        Arrays.stream(DataCollectionExecutionStatus.values())
            .map(status -> create(status, Type.DEPLOYMENT))
            .peek(dataCollectionTask -> dataCollectionTask.setVerificationTaskId(verificationTaskId))
            .collect(Collectors.toList());
    hPersistence.save(dataCollectionTasks);
    dataCollectionTaskService.abortDeploymentDataCollectionTasks(Lists.newArrayList(verificationTaskId));
    // WAITING and QUEUED status needs to changed to ABORTED
    dataCollectionTasks.stream()
        .filter(dataCollectionTask
            -> dataCollectionTask.getStatus().equals(WAITING) || dataCollectionTask.getStatus().equals(QUEUED))
        .map(dataCollectionTask -> dataCollectionTask.getUuid())
        .forEach(uuid -> {
          DataCollectionTask dataCollectionTask = hPersistence.get(DataCollectionTask.class, uuid);
          assertThat(dataCollectionTask.getStatus()).isEqualTo(ABORTED);
        });
    // status other than WAITING and QUEUED should remain the same
    dataCollectionTasks.stream()
        .filter(dataCollectionTask
            -> !(dataCollectionTask.getStatus().equals(WAITING) || dataCollectionTask.getStatus().equals(QUEUED)))
        .forEach(dataCollectionTask -> {
          DataCollectionTask updatedDataCollectionTask =
              hPersistence.get(DataCollectionTask.class, dataCollectionTask.getUuid());
          assertThat(updatedDataCollectionTask.getStatus()).isEqualTo(dataCollectionTask.getStatus());
        });
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdatePerpetualTaskStatusForDemoMonitoringSourcePerpetualTask() {
    DataCollectionTask dataCollectionTask = create();
    hPersistence.save(dataCollectionTask);
    hPersistence.save(MonitoringSourcePerpetualTask.builder()
                          .accountId(dataCollectionTask.getAccountId())
                          .dataCollectionWorkerId(dataCollectionTask.getDataCollectionWorkerId())
                          .isDemo(true)
                          .build());
    dataCollectionTaskService.updatePerpetualTaskStatus(dataCollectionTask);
    dataCollectionTask = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(dataCollectionTask.getStatus()).isEqualTo(QUEUED);
    assertThat(dataCollectionTask.getStacktrace()).isNull();
    assertThat(dataCollectionTask.getException()).isNull();
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdatePerpetualTaskStatusWhenTaskIsUnassigned() {
    DataCollectionTask dataCollectionTask = create(QUEUED);
    hPersistence.save(dataCollectionTask);
    String perpetualTaskId = "some-perpetual-task-id";
    hPersistence.save(MonitoringSourcePerpetualTask.builder()
                          .accountId(dataCollectionTask.getAccountId())
                          .dataCollectionWorkerId(dataCollectionTask.getDataCollectionWorkerId())
                          .perpetualTaskId(perpetualTaskId)
                          .build());
    dataCollectionTaskService.updatePerpetualTaskStatus(dataCollectionTask);
    dataCollectionTask = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    // checking for queued and not failed status as the status changes in retry logic.
    assertThat(dataCollectionTask.getStatus()).isEqualTo(QUEUED);
    assertThat(dataCollectionTask.getException())
        .isEqualTo("Perpetual task assigned but not in a valid state:" + TASK_UNASSIGNED
            + " and is assigned to delegate:"
            + "some-delegate-id");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdatePerpetualTaskStatusWhenNoDelegateFound() {
    DataCollectionTask dataCollectionTask = create();
    hPersistence.save(dataCollectionTask);
    String perpetualTaskId = "some-perpetual-task-id";
    hPersistence.save(MonitoringSourcePerpetualTask.builder()
                          .accountId(dataCollectionTask.getAccountId())
                          .dataCollectionWorkerId(dataCollectionTask.getDataCollectionWorkerId())
                          .perpetualTaskId(perpetualTaskId)
                          .build());
    dataCollectionTaskService.updatePerpetualTaskStatus(dataCollectionTask);
    dataCollectionTask = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    assertThat(dataCollectionTask.getException())
        .isEqualTo(
            "Perpetual task assigned but not in a valid state:TASK_UNASSIGNED and is assigned to delegate:some-delegate-id");
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testUpdatePerpetualTaskStatusWhenTaskIsAssigned() {
    DataCollectionTask dataCollectionTask = create();
    hPersistence.save(dataCollectionTask);
    String perpetualTaskId = "some-perpetual-task-id";
    hPersistence.save(MonitoringSourcePerpetualTask.builder()
                          .accountId(dataCollectionTask.getAccountId())
                          .dataCollectionWorkerId(dataCollectionTask.getDataCollectionWorkerId())
                          .perpetualTaskId(perpetualTaskId)
                          .build());
    dataCollectionTask = dataCollectionTaskService.getDataCollectionTask(dataCollectionTask.getUuid());
    dataCollectionTaskService.updatePerpetualTaskStatus(dataCollectionTask);
    assertThat(dataCollectionTask.getException()).isNull();
    assertThat(dataCollectionTask.getStatus()).isEqualTo(QUEUED);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandleCreateNextTask_forLogConfig() {
    SplunkCVConfig cvConfig = getSplunkCVConfig();
    serviceGuardDataCollectionTaskService.handleCreateNextTask(cvConfig);
    DataCollectionTask savedTask = hPersistence.createQuery(DataCollectionTask.class)
                                       .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
                                       .get();
    assertThat(savedTask.getStatus()).isEqualTo(QUEUED);
    assertThat(savedTask.getDataCollectionInfo()).isInstanceOf(SplunkDataCollectionInfo.class);
    assertThat(savedTask.getEndTime()).isEqualTo(cvConfig.getFirstTimeDataCollectionTimeRange().getEndTime());
    assertThat(savedTask.getStartTime()).isEqualTo(cvConfig.getFirstTimeDataCollectionTimeRange().getStartTime());
    assertThat(savedTask.getDataCollectionWorkerId())
        .isEqualTo(monitoringSourcePerpetualTaskService.getLiveMonitoringWorkerId(
            accountId, orgIdentifier, projectIdentifier, cvConfig.getConnectorIdentifier(), cvConfig.getIdentifier()));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreateSeqTasks() {
    List<DataCollectionTask> dataCollectionTasks = new ArrayList<>();
    for (int minute = 0; minute < 10; minute++) {
      DataCollectionTask dataCollectionTask = create();
      dataCollectionTasks.add(dataCollectionTask);
    }
    dataCollectionTaskService.createSeqTasks(dataCollectionTasks);
    for (int i = 0; i < dataCollectionTasks.size(); i++) {
      dataCollectionTasks.set(i, dataCollectionTaskService.getDataCollectionTask(dataCollectionTasks.get(i).getUuid()));
    }
    assertThat(dataCollectionTasks.get(0).getStatus()).isEqualTo(QUEUED);
    for (int i = 0; i < 9; i++) {
      assertThat(dataCollectionTasks.get(i + 1).getUuid()).isEqualTo(dataCollectionTasks.get(i).getNextTaskId());
    }
    assertThat(dataCollectionTasks.get(dataCollectionTasks.size() - 1).getNextTaskId()).isNull();
    for (int i = 1; i < 10; i++) {
      assertThat(dataCollectionTasks.get(i).getStatus()).isEqualTo(DataCollectionExecutionStatus.WAITING);
    }
    assertThat(fakeNow).isEqualTo(dataCollectionTasks.get(0).getValidAfter());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetNextTaskDTOs() {
    hPersistence.save(create());
    List<DataCollectionTaskDTO> nextTaskDTOs =
        dataCollectionTaskService.getNextTaskDTOs(accountId, dataCollectionWorkerId);
    assertThat(nextTaskDTOs.size()).isEqualTo(1);
    int numOfTasks = 100;
    List<DataCollectionTask> dataCollectionTasks = new ArrayList<>();
    for (int i = 0; i < numOfTasks; i++) {
      dataCollectionTasks.add(create());
    }
    hPersistence.save(dataCollectionTasks);
    nextTaskDTOs = dataCollectionTaskService.getNextTaskDTOs(accountId, dataCollectionWorkerId);
    assertThat(nextTaskDTOs.size()).isEqualTo(CVNextGenConstants.CVNG_MAX_PARALLEL_THREADS);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetAllDataCollection() {
    int numOfTasks = 10;
    createAndSave(RUNNING);
    for (int i = 0; i < numOfTasks - 1; i++) {
      createAndSave(QUEUED);
    }

    List<DataCollectionTask> dataCollectionTaskList =
        dataCollectionTaskService.getAllDataCollectionTasks(accountId, verificationTaskId);

    assertThat(dataCollectionTaskList.size()).isEqualTo(numOfTasks);
    assertThat(dataCollectionTaskList.get(0).getStatus()).isEqualTo(RUNNING);
    long createdAt = dataCollectionTaskList.get(0).getCreatedAt();
    for (int i = 1; i < numOfTasks; i++) {
      assertThat(dataCollectionTaskList.get(i).getStatus()).isEqualTo(QUEUED);
      long createdAtNext = dataCollectionTaskList.get(i).getCreatedAt();
      assertThat(createdAtNext).isGreaterThanOrEqualTo(createdAt);
      createdAt = createdAtNext;
    }
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetAllNonFinalDataCollectionTasks() {
    int numOfTasks = 5;
    for (int i = 0; i < numOfTasks; i++) {
      createAndSave(RUNNING);
    }
    for (int i = 0; i < numOfTasks; i++) {
      createAndSave(SUCCESS);
    }

    List<DataCollectionTask> dataCollectionTaskList =
        dataCollectionTaskService.getAllNonFinalDataCollectionTasks(accountId, verificationTaskId);
    assertThat(dataCollectionTaskList.size()).isEqualTo(numOfTasks);
    for (DataCollectionTask dataCollectionTask : dataCollectionTaskList) {
      assertThat(dataCollectionTask.getStatus()).isIn(DataCollectionExecutionStatus.getNonFinalStatuses());
    }
  }

  private AppDynamicsCVConfig getCVConfig() {
    AppDynamicsCVConfig cvConfig = builderFactory.appDynamicsCVConfigBuilder().build();
    cvConfig.setUuid(cvConfigId);
    cvConfig.setApplicationName("cv-app");
    cvConfig.setTierName("docker-tier");
    cvConfig.setApplicationName("applicationName");
    cvConfig.setTierName("tierName");
    cvConfig.setMetricPack(
        metricPackService.getMetricPacks(accountId, orgIdentifier, projectIdentifier, DataSourceType.APP_DYNAMICS)
            .get(0));
    return cvConfig;
  }

  private SplunkCVConfig getSplunkCVConfig() {
    SplunkCVConfig cvConfig = builderFactory.splunkCVConfigBuilder().build();
    cvConfig.setUuid(cvConfigId);
    return cvConfig;
  }

  private DataCollectionTask getDataCollectionTask(String dataCollectionTaskId) {
    return hPersistence.get(DataCollectionTask.class, dataCollectionTaskId);
  }

  private DataCollectionTask create() {
    return create(QUEUED);
  }

  private DataCollectionInfo createDataCollectionInfo() {
    return AppDynamicsDataCollectionInfo.builder().build();
  }

  private DataCollectionTask create(DataCollectionExecutionStatus executionStatus) {
    return create(executionStatus, Type.SERVICE_GUARD);
  }
  private DataCollectionTask create(DataCollectionExecutionStatus executionStatus, Type type) {
    if (type == Type.SERVICE_GUARD) {
      return ServiceGuardDataCollectionTask.builder()
          .verificationTaskId(verificationTaskId)
          .type(Type.SERVICE_GUARD)
          .dataCollectionWorkerId(dataCollectionWorkerId)
          .accountId(accountId)
          .startTime(fakeNow.minus(Duration.ofMinutes(7)))
          .endTime(fakeNow.minus(Duration.ofMinutes(2)))
          .status(executionStatus)
          .dataCollectionInfo(createDataCollectionInfo())
          .lastPickedAt(executionStatus == RUNNING ? fakeNow.minus(Duration.ofMinutes(5)) : null)
          .build();
    } else if (type == Type.SLI) {
      return SLIDataCollectionTask.builder()
          .verificationTaskId(sliVerificationTaskId)
          .type(Type.SLI)
          .dataCollectionWorkerId(dataCollectionWorkerId)
          .accountId(accountId)
          .startTime(fakeNow.minus(Duration.ofMinutes(7)))
          .endTime(fakeNow.minus(Duration.ofMinutes(2)))
          .status(executionStatus)
          .dataCollectionInfo(createDataCollectionInfo())
          .lastPickedAt(executionStatus == RUNNING ? fakeNow.minus(Duration.ofMinutes(5)) : null)
          .build();
    } else {
      return DeploymentDataCollectionTask.builder()
          .verificationTaskId(verificationTaskId)
          .dataCollectionWorkerId(dataCollectionWorkerId)
          .type(Type.DEPLOYMENT)
          .accountId(accountId)
          .startTime(fakeNow.minus(Duration.ofMinutes(7)))
          .endTime(fakeNow.minus(Duration.ofMinutes(2)))
          .status(executionStatus)
          .dataCollectionInfo(createDataCollectionInfo())
          .lastPickedAt(executionStatus == RUNNING ? fakeNow.minus(Duration.ofMinutes(5)) : null)
          .build();
    }
  }

  private DataCollectionTask createAndSave(DataCollectionExecutionStatus executionStatus) {
    DataCollectionTask dataCollectionTask = create(executionStatus);
    hPersistence.save(dataCollectionTask);
    return dataCollectionTask;
  }

  private DataCollectionTask createAndSave(DataCollectionExecutionStatus executionStatus, Type type) {
    DataCollectionTask dataCollectionTask = create(executionStatus, type);
    hPersistence.save(dataCollectionTask);
    return dataCollectionTask;
  }

  private CVConfig createCVConfig() {
    PrometheusCVConfig cvConfig = builderFactory.prometheusCVConfigBuilder().build();
    String identifier = HealthSourceService.getNameSpacedIdentifier(
        builderFactory.getContext().getServiceIdentifier() + "_" + builderFactory.getContext().getEnvIdentifier(),
        "healthSourceIdentifier");
    cvConfig.setVerificationType(VerificationType.TIME_SERIES);
    cvConfig.setAccountId(accountId);
    cvConfig.setEnabled(true);
    cvConfig.setConnectorIdentifier(generateUuid());
    cvConfig.setProjectIdentifier(projectIdentifier);
    cvConfig.setOrgIdentifier(orgIdentifier);
    cvConfig.setIdentifier(identifier);
    cvConfig.setMonitoringSourceName(identifier);
    cvConfig.setCategory(CVMonitoringCategory.PERFORMANCE);
    cvConfig.setProductName(generateUuid());
    cvConfig.setGroupName("myGroupName");
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    cvConfig.setMetricPack(metricPack);
    PrometheusCVConfig.MetricInfo metricInfo = PrometheusCVConfig.MetricInfo.builder()
                                                   .metricName("myMetric")
                                                   .metricType(TimeSeriesMetricType.RESP_TIME)
                                                   .prometheusMetricName("cpu_usage_total")
                                                   .build();

    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    return cvConfig;
  }

  private VerificationJobInstance createVerificationJobInstance() {
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .executionStatus(ExecutionStatus.QUEUED)
            .deploymentStartTime(Instant.ofEpochMilli(clock.millis()))
            .startTime(Instant.ofEpochMilli(clock.millis() + Duration.ofMinutes(2).toMillis()))
            .build();
    verificationJobInstanceService.create(verificationJobInstance);
    verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstance.getUuid(), APP_DYNAMICS);
    return verificationJobInstance;
  }

  private void markRunning(String uuid) {
    UpdateOperations<DataCollectionTask> updateOperations =
        hPersistence.createUpdateOperations(DataCollectionTask.class).set(DataCollectionTaskKeys.status, RUNNING);
    Query<DataCollectionTask> query =
        hPersistence.createQuery(DataCollectionTask.class).filter(DataCollectionTaskKeys.uuid, uuid);
    hPersistence.update(query, updateOperations);
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  private ServiceLevelIndicator createSLI() {
    ServiceLevelObjectiveV2DTO sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    createMonitoredService();
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .projectIdentifier(projectIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .build();
    ServiceLevelObjectiveV2Response serviceLevelObjectiveResponse =
        serviceLevelObjectiveV2Service.create(projectParams, sloDTO);
    String identifier =
        ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveResponse.getServiceLevelObjectiveV2DTO().getSpec())
            .getServiceLevelIndicators()
            .get(0)
            .getIdentifier();
    return hPersistence.createQuery(ServiceLevelIndicator.class)
        .filter(ServiceLevelIndicatorKeys.accountId, projectParams.getAccountIdentifier())
        .filter(ServiceLevelIndicatorKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(ServiceLevelIndicatorKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(ServiceLevelIndicatorKeys.identifier, identifier)
        .get();
  }
}
