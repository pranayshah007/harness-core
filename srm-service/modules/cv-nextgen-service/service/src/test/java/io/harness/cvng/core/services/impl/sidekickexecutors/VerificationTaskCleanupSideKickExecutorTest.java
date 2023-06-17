/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.sidekickexecutors;

import static io.harness.cvng.CVNGTestConstants.FIXED_TIME_FOR_TESTS;
import static io.harness.cvng.core.services.impl.sidekickexecutors.VerificationTaskCleanupSideKickExecutor.RECORDS_TO_BE_DELETED_IN_SINGLE_BATCH;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.NO_DATA;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.VerificationApplication;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis.DeploymentTimeSeriesAnalysisKeys;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisResultKeys;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.sidekick.VerificationTaskCleanupSideKickData;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask.MonitoringSourcePerpetualTaskKeys;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.demo.CVNGDemoDataIndex;
import io.harness.cvng.core.entities.demo.CVNGDemoDataIndex.cvngDemoDataIndexKeys;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.demo.CVNGDemoDataIndexService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket.SLIRecordBucketKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.reflection.HarnessReflections;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VerificationTaskCleanupSideKickExecutorTest extends CvNextGenTestBase {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private VerificationTaskCleanupSideKickExecutor sideKickExecutor;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private CVConfigService cvConfigService;
  @Inject private CVNGDemoDataIndexService cvngDemoDataIndexService;

  @Inject private SLIRecordService sliRecordService;

  @Inject private Clock clock;
  private CVConfig cvConfig;
  private ServiceLevelIndicator sli;
  private String verificationTaskIdsForSli;
  private String verificationTaskId;

  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    this.builderFactory = BuilderFactory.getDefault();
    this.clock = FIXED_TIME_FOR_TESTS;
    this.cvConfig = createCVConfig();
    cvConfigService.save(cvConfig);
    this.sli = createSLI();
    this.verificationTaskIdsForSli = sli.getUuid();
    createSLIRecords(sli.getUuid());
    VerificationTask slitask =
        verificationTaskService.getSLITask(builderFactory.getContext().getAccountId(), sli.getUuid());
    slitask.setCreatedAt(clock.millis());
    hPersistence.save(slitask);

    VerificationTask serviceGuardtask =
        verificationTaskService.getLiveMonitoringTask(builderFactory.getContext().getAccountId(), cvConfig.getUuid());
    serviceGuardtask.setCreatedAt(clock.millis());
    hPersistence.save(serviceGuardtask);
    this.verificationTaskId = serviceGuardtask.getUuid();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_deleteVerificationTaskForMonitoredService() {
    VerificationTaskCleanupSideKickData sideKickData =
        VerificationTaskCleanupSideKickData.builder().verificationTaskId(verificationTaskId).cvConfig(cvConfig).build();
    sideKickExecutor.execute(sideKickData);
    assertThat(hPersistence.get(VerificationTask.class, verificationTaskId)).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_deleteVerificationTaskForServiceLevelIndicator() {
    VerificationTaskCleanupSideKickData sideKickData =
        VerificationTaskCleanupSideKickData.builder().verificationTaskId(verificationTaskIdsForSli).build();
    sideKickExecutor.execute(sideKickData);
    assertThat(hPersistence.get(VerificationTask.class, verificationTaskIdsForSli)).isNull();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecute_deleteSLIRecordAndSLIRecordBucketForServiceLevelIndicator() {
    assertThat(hPersistence.createQuery(SLIRecordBucket.class).filter(SLIRecordBucketKeys.sliId, sli.getUuid()).count())
        .isNotEqualTo(0);
    assertThat(hPersistence.createQuery(SLIRecord.class).filter(SLIRecordKeys.sliId, sli.getUuid()).count())
        .isNotEqualTo(0);
    VerificationTaskCleanupSideKickData sideKickData =
        VerificationTaskCleanupSideKickData.builder().verificationTaskId(verificationTaskIdsForSli).build();
    sideKickExecutor.execute(sideKickData);
    assertThat(hPersistence.createQuery(SLIRecordBucket.class).filter(SLIRecordBucketKeys.sliId, sli.getUuid()).count())
        .isEqualTo(0);
    assertThat(hPersistence.createQuery(SLIRecord.class).filter(SLIRecordKeys.sliId, sli.getUuid()).count())
        .isEqualTo(0);
    assertThat(hPersistence.get(VerificationTask.class, verificationTaskIdsForSli)).isNull();
  }
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_deleteMonitoringSourcePerpetualTasks() {
    monitoringSourcePerpetualTaskService.createTask(builderFactory.getContext().getAccountId(),
        cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier(), cvConfig.getConnectorIdentifier(),
        cvConfig.getFullyQualifiedIdentifier(), false);
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasksBeforeDeletion =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class)
            .filter(
                MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, cvConfig.getFullyQualifiedIdentifier())
            .asList();
    assertThat(monitoringSourcePerpetualTasksBeforeDeletion).hasSize(2);
    hPersistence.delete(CVConfig.class, cvConfig.getUuid());
    sideKickExecutor.execute(VerificationTaskCleanupSideKickData.builder()
                                 .verificationTaskId(verificationTaskId)
                                 .cvConfig(cvConfig)
                                 .build());
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasksAfterDeletion =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class)
            .filter(
                MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, cvConfig.getFullyQualifiedIdentifier())
            .asList();
    assertThat(monitoringSourcePerpetualTasksAfterDeletion).hasSize(0);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testExecute_deleteMonitoringSourcePerpetualTasksIfRecreatedWithDifferentConnectorId() {
    monitoringSourcePerpetualTaskService.createTask(builderFactory.getContext().getAccountId(),
        cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier(), cvConfig.getConnectorIdentifier(),
        cvConfig.getFullyQualifiedIdentifier(), false);
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasksBeforeDeletion =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class)
            .filter(
                MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, cvConfig.getFullyQualifiedIdentifier())
            .asList();
    assertThat(monitoringSourcePerpetualTasksBeforeDeletion).hasSize(2);
    hPersistence.delete(CVConfig.class, cvConfig.getUuid());
    CVConfig cvConfig2 = builderFactory.splunkCVConfigBuilder().connectorIdentifier("connector2").build();
    cvConfigService.save(cvConfig2);
    monitoringSourcePerpetualTaskService.createTask(builderFactory.getContext().getAccountId(),
        cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier(), "connector2",
        cvConfig.getFullyQualifiedIdentifier(), false);
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasksAfterDifferentConnectorCreated =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class)
            .filter(
                MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, cvConfig.getFullyQualifiedIdentifier())
            .asList();
    assertThat(monitoringSourcePerpetualTasksAfterDifferentConnectorCreated).hasSize(4);
    sideKickExecutor.execute(VerificationTaskCleanupSideKickData.builder()
                                 .verificationTaskId(verificationTaskId)
                                 .cvConfig(cvConfig)
                                 .build());
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasksAfterDeletion =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class)
            .filter(
                MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, cvConfig.getFullyQualifiedIdentifier())
            .asList();
    assertThat(monitoringSourcePerpetualTasksAfterDeletion).hasSize(2);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testExecute_NodeleteMonitoringSourcePerpetualTasksIfRecreated() {
    monitoringSourcePerpetualTaskService.createTask(builderFactory.getContext().getAccountId(),
        cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier(), cvConfig.getConnectorIdentifier(),
        cvConfig.getFullyQualifiedIdentifier(), false);
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasksBeforeDeletion =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class)
            .filter(
                MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, cvConfig.getFullyQualifiedIdentifier())
            .asList();
    assertThat(monitoringSourcePerpetualTasksBeforeDeletion).hasSize(2);
    sideKickExecutor.execute(VerificationTaskCleanupSideKickData.builder()
                                 .verificationTaskId(verificationTaskId)
                                 .cvConfig(cvConfig)
                                 .build());
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasksAfterDeletion =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class)
            .filter(
                MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, cvConfig.getFullyQualifiedIdentifier())
            .asList();
    assertThat(monitoringSourcePerpetualTasksAfterDeletion).hasSize(2);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_deleteMonitoringSourcePerpetualTasksFails() {
    monitoringSourcePerpetualTaskService.createTask(builderFactory.getContext().getAccountId(),
        cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier(), cvConfig.getConnectorIdentifier(),
        cvConfig.getFullyQualifiedIdentifier(), false);
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasksBeforeDeletion =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class)
            .filter(
                MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, cvConfig.getFullyQualifiedIdentifier())
            .asList();
    assertThat(monitoringSourcePerpetualTasksBeforeDeletion).hasSize(2);

    sideKickExecutor.execute(VerificationTaskCleanupSideKickData.builder()
                                 .verificationTaskId(verificationTaskId)
                                 .cvConfig(cvConfig)
                                 .build());
    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasksAfterDeletion =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class)
            .filter(
                MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier, cvConfig.getFullyQualifiedIdentifier())
            .asList();
    assertThat(monitoringSourcePerpetualTasksAfterDeletion).hasSize(2);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testTriggerCleanup_entitiesList() {
    Set<Class<? extends PersistentEntity>> entitiesWithVerificationTaskId = new HashSet<>();
    entitiesWithVerificationTaskId.addAll(
        VerificationTaskCleanupSideKickExecutor.ENTITIES_TO_DELETE_BY_VERIFICATION_ID);
    entitiesWithVerificationTaskId.addAll(
        VerificationTaskCleanupSideKickExecutor.ENTITIES_DELETE_BLACKLIST_BY_VERIFICATION_ID);
    Set<Class<? extends PersistentEntity>> reflections =
        HarnessReflections.get()
            .getSubTypesOf(PersistentEntity.class)
            .stream()
            .filter(klazz
                -> StringUtils.startsWithAny(
                    klazz.getPackage().getName(), VerificationApplication.class.getPackage().getName()))
            .collect(Collectors.toSet());
    Set<Class<? extends PersistentEntity>> withVerificationTaskId = new HashSet<>();
    reflections.forEach(entity -> {
      if (doesClassContainField(entity, VerificationTask.VERIFICATION_TASK_ID_KEY)) {
        withVerificationTaskId.add(entity);
      }
    });
    assertThat(entitiesWithVerificationTaskId)
        .isEqualTo(withVerificationTaskId)
        .withFailMessage(
            "Entities with verificationTaskId found which is not added to ENTITIES_TO_DELETE_BY_VERIFICATION_ID or ENTITIES_DELETE_BLACKLIST_BY_VERIFICATION_ID");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testTriggerCleanup_entitiesImplementUuidAware() {
    Set<String> entitiesWithoutUuidAwareImpl = new HashSet<>();
    VerificationTaskCleanupSideKickExecutor.ENTITIES_TO_DELETE_BY_VERIFICATION_ID.forEach(clazz -> {
      if (Arrays.stream(clazz.getInterfaces())
              .noneMatch(
                  implementedInterface -> implementedInterface.getTypeName().equals(UuidAware.class.getTypeName()))) {
        entitiesWithoutUuidAwareImpl.add(clazz.getName());
      }
    });
    assertThat(entitiesWithoutUuidAwareImpl)
        .withFailMessage(
            "The following entities do not implement the required interface UuidAware %s", entitiesWithoutUuidAwareImpl)
        .isEmpty();
  }

  private boolean doesClassContainField(Class<?> clazz, String fieldName) {
    return Arrays.stream(clazz.getDeclaredFields()).anyMatch(f -> f.getName().equals(fieldName));
  }

  private CVConfig createCVConfig() {
    return builderFactory.splunkCVConfigBuilder().build();
  }

  private ServiceLevelIndicator createSLI() {
    ServiceLevelObjectiveV2DTO sloDTO = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    createMonitoredService();
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(builderFactory.getContext().getAccountId())
                                      .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                      .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
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

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_cleanupLogAnalysisResultRecords() {
    VerificationTaskCleanupSideKickExecutor spiedSideKickExecutor = spy(sideKickExecutor);
    int numberOfRecords = new Random().nextInt(999) + 1;
    for (int i = 0; i < numberOfRecords; ++i) {
      LogAnalysisResult logAnalysisResult = LogAnalysisResult.builder().verificationTaskId(verificationTaskId).build();
      hPersistence.save(logAnalysisResult);
    }
    Query<LogAnalysisResult> query = hPersistence.createQuery(LogAnalysisResult.class)
                                         .filter(LogAnalysisResultKeys.verificationTaskId, verificationTaskId);
    assertThat(query.count()).isEqualTo(numberOfRecords);
    spiedSideKickExecutor.execute(VerificationTaskCleanupSideKickData.builder()
                                      .verificationTaskId(verificationTaskId)
                                      .cvConfig(cvConfig)
                                      .build());
    assertThat(query.count()).isZero();
    int expectedNumberOfDbDeleteCalls =
        (int) Math.ceil((double) numberOfRecords / RECORDS_TO_BE_DELETED_IN_SINGLE_BATCH);
    verify(spiedSideKickExecutor, times(expectedNumberOfDbDeleteCalls)).deleteRecords(any(Query.class));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_recordsAreNotDeleted() {
    VerificationTaskCleanupSideKickExecutor spiedSideKickExecutor = spy(sideKickExecutor);
    DeploymentTimeSeriesAnalysis deploymentTimeSeriesAnalysis =
        DeploymentTimeSeriesAnalysis.builder().verificationTaskId(verificationTaskId).build();
    hPersistence.save(deploymentTimeSeriesAnalysis);
    Query<DeploymentTimeSeriesAnalysis> query =
        hPersistence.createQuery(DeploymentTimeSeriesAnalysis.class)
            .filter(DeploymentTimeSeriesAnalysisKeys.verificationTaskId, verificationTaskId);
    assertThat(query.count()).isEqualTo(1);
    spiedSideKickExecutor.execute(VerificationTaskCleanupSideKickData.builder()
                                      .verificationTaskId(verificationTaskId)
                                      .cvConfig(cvConfig)
                                      .build());
    assertThat(query.count()).isEqualTo(1);
    verify(spiedSideKickExecutor, times(0)).deleteRecords(any(Query.class));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_cleanupRecordsWithMixedTypesOfUuids() {
    VerificationTaskCleanupSideKickExecutor spiedSideKickExecutor = spy(sideKickExecutor);
    saveRecordsWithMixedTypesOfUuids();
    Query<CVNGDemoDataIndex> cvngDemoDataIndicesQuery =
        hPersistence.createQuery(CVNGDemoDataIndex.class)
            .filter(cvngDemoDataIndexKeys.verificationTaskId, verificationTaskId);
    Query<LogAnalysisResult> logAnalysisResultsQuery =
        hPersistence.createQuery(LogAnalysisResult.class)
            .filter(LogAnalysisResultKeys.verificationTaskId, verificationTaskId);
    assertThat(cvngDemoDataIndicesQuery.count()).isEqualTo(2);
    assertThat(logAnalysisResultsQuery.count()).isEqualTo(1);
    List<CVNGDemoDataIndex> cvngDemoDataIndices = cvngDemoDataIndicesQuery.find().toList();
    assertThat(ObjectId.isValid(cvngDemoDataIndices.get(0).getUuid())).isFalse();
    assertThat(ObjectId.isValid(cvngDemoDataIndices.get(1).getUuid())).isTrue();
    assertThat(ObjectId.isValid(logAnalysisResultsQuery.get().getUuid())).isFalse();
    spiedSideKickExecutor.execute(VerificationTaskCleanupSideKickData.builder()
                                      .verificationTaskId(verificationTaskId)
                                      .cvConfig(cvConfig)
                                      .build());
    assertThat(cvngDemoDataIndicesQuery.count()).isZero();
    assertThat(logAnalysisResultsQuery.count()).isZero();
    int expectedNumberOfDbDeleteCalls = 2;
    verify(spiedSideKickExecutor, times(expectedNumberOfDbDeleteCalls)).deleteRecords(any(Query.class));
  }

  private void saveRecordsWithMixedTypesOfUuids() {
    hPersistence.save(LogAnalysisResult.builder().verificationTaskId(verificationTaskId).build());
    hPersistence.save(CVNGDemoDataIndex.builder()
                          .uuid("1234567890")
                          .lastIndex(2)
                          .dataCollectionWorkerId("234")
                          .verificationTaskId(verificationTaskId)
                          .accountId(builderFactory.getContext().getAccountId())
                          .build());
    cvngDemoDataIndexService.saveIndexForDemoData(
        builderFactory.getContext().getAccountId(), "123", verificationTaskId, 3);
  }

  private void createSLIRecords(String sliId) {
    Instant startTime = clock.instant().minus(Duration.ofMinutes(10));
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, verificationTaskIdsForSli, 0);
  }

  private List<SLIRecordParam> getSLIRecordParam(Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIState sliState = sliStates.get(i);
      long goodCount = 0;
      long badCount = 0;
      if (sliState == GOOD) {
        goodCount++;
      } else if (sliState == BAD) {
        badCount++;
      }
      sliRecordParams.add(SLIRecordParam.builder()
                              .sliState(sliState)
                              .timeStamp(startTime.plus(Duration.ofMinutes(i)))
                              .goodEventCount(goodCount)
                              .badEventCount(badCount)
                              .build());
    }
    return sliRecordParams;
  }
}
