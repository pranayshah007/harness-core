/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.beans.ExecutionStatus.PAUSED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.ExecutionStatus.WAITING;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.shouldPerformReconciliation;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.RUSHABH;

import static software.wings.beans.CountsByStatuses.Builder.aCountsByStatuses;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.event.reconciliation.DetectionStatus;
import io.harness.event.reconciliation.ReconcilationAction;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecord;
import io.harness.event.reconciliation.deployment.DeploymentReconRecordRepository;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.WingsBaseTest;
import software.wings.beans.CountsByStatuses;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionBuilder;
import software.wings.search.entities.deployment.DeploymentExecutionEntity;
import software.wings.search.entities.deployment.DeploymentStepExecutionEntity;
import software.wings.search.entities.executionInterrupt.ExecutionInterruptEntity;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder;
import software.wings.sm.StateExecutionInstance;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

public class DeploymentReconServiceImplTest extends WingsBaseTest {
  @Mock TimeScaleDBService timeScaleDBService;
  @Inject @InjectMocks DeploymentReconServiceImpl deploymentReconService;
  @Inject @InjectMocks DeploymentStepReconServiceImpl deploymentStepReconService;
  @Inject @InjectMocks ExecutionInterruptReconServiceImpl executionInterruptReconService;
  @Inject DeploymentReconRecordRepository deploymentReconRecordRepository;
  @Inject private HPersistence persistence;
  private Cache<String, DeploymentReconRecord> deploymentReconRecordCache;
  @Inject DeploymentExecutionEntity deploymentExecutionEntity;
  @Inject DeploymentStepExecutionEntity deploymentStepExecutionEntity;
  @Inject ExecutionInterruptEntity executionInterruptEntity;

  String sourceEntityClass = WorkflowExecution.class.getCanonicalName();
  String stateExecutionInstanceSourceEntityClass = StateExecutionInstance.class.getCanonicalName();
  String executionInterruptSourceEntityClass = ExecutionInterrupt.class.getCanonicalName();

  final Connection mockConnection = mock(Connection.class);
  final Statement mockStatement = mock(Statement.class);
  final ResultSet resultSet1 = mock(ResultSet.class);
  final ResultSet resultSet2 = mock(ResultSet.class);
  final ResultSet resultSet3 = mock(ResultSet.class);
  final ResultSet resultSet4 = mock(ResultSet.class);
  final ResultSet statementResultSet = mock(ResultSet.class);
  final DeploymentReconServiceImpl deploymentReconServiceImpl = mock(DeploymentReconServiceImpl.class);
  final PreparedStatement preparedStatement = mock(PreparedStatement.class);
  final Array array = mock(Array.class);
  int[] duplicatesCount = {0};
  int[] statusMismatchCount = {0};

  @Before
  public void setUp() throws Exception {
    deploymentReconService = spy(deploymentReconService);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(mockConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
    when(mockConnection.createArrayOf(any(), any())).thenReturn(array);
    when(preparedStatement.executeQuery()).thenReturn(resultSet1, resultSet2, resultSet3, resultSet4);
    when(mockStatement.executeQuery(anyString())).thenReturn(statementResultSet);
    when(timeScaleDBService.isValid()).thenReturn(true);
  }

  public void activateDuplicatesFound(ResultSet resultSet) throws Exception {
    doAnswer((Answer<Boolean>) invocation -> getAnswerDuplicates()).when(resultSet).next();
  }

  public void deactivateDuplicatesFound(ResultSet resultSet) throws Exception {
    doAnswer((Answer<Boolean>) invocation -> false).when(resultSet).next();
  }

  private boolean getAnswerDuplicates() {
    while (duplicatesCount[0] < 2) {
      duplicatesCount[0]++;
      return true;
    }
    return false;
  }

  private boolean getAnswerStatusMismatch() {
    while (statusMismatchCount[0] < 2) {
      statusMismatchCount[0]++;
      return true;
    }
    return false;
  }

  private void activateStatusMismatch(ResultSet resultSet, long timeStamp) throws Exception {
    doAnswer((Answer<Boolean>) invocation -> getAnswerStatusMismatch()).when(resultSet).next();

    final WorkflowExecutionBuilder workflowExecutionBuilder = WorkflowExecution.builder()
                                                                  .accountId(ACCOUNT_ID)
                                                                  .appId(APP_ID)
                                                                  .envId(ENV_ID)
                                                                  .startTs(2L)
                                                                  .endTs(timeStamp)
                                                                  .createdAt(timeStamp);

    persistence.save(workflowExecutionBuilder.uuid("DATA0").status(SUCCESS).build());
  }

  private void activateStatusMismatchStateExecutionInstance(ResultSet resultSet, long timeStamp) throws Exception {
    doAnswer((Answer<Boolean>) invocation -> getAnswerStatusMismatch()).when(resultSet).next();

    final StateExecutionInstance.Builder stateExecutionInstanceBuilder =
        StateExecutionInstance.Builder.aStateExecutionInstance()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .startTs(2L)
            .endTs(timeStamp)
            .createdAt(timeStamp);

    persistence.save(stateExecutionInstanceBuilder.uuid("DATA0").status(SUCCESS).build());
  }

  private void activateMissingRecords(long timeStamp) {
    CountsByStatuses countsByStatuses = aCountsByStatuses().build();

    final WorkflowExecutionBuilder workflowExecutionBuilder = WorkflowExecution.builder()
                                                                  .accountId(ACCOUNT_ID)
                                                                  .appId(APP_ID)
                                                                  .envId(ENV_ID)
                                                                  .startTs(2L)
                                                                  .endTs(timeStamp)
                                                                  .createdAt(timeStamp);

    persistence.save(workflowExecutionBuilder.uuid(generateUuid()).status(SUCCESS).build());
    persistence.save(workflowExecutionBuilder.uuid(generateUuid())
                         .status(ExecutionStatus.ERROR)
                         .breakdown(countsByStatuses)
                         .build());
    persistence.save(workflowExecutionBuilder.uuid(generateUuid())
                         .status(ExecutionStatus.FAILED)
                         .breakdown(countsByStatuses)
                         .build());
    persistence.save(workflowExecutionBuilder.uuid(generateUuid()).status(ExecutionStatus.ABORTED).build());

    persistence.save(workflowExecutionBuilder.uuid(generateUuid()).status(RUNNING).build());

    persistence.save(workflowExecutionBuilder.uuid(generateUuid()).status(PAUSED).build());
    persistence.save(workflowExecutionBuilder.uuid(generateUuid()).status(WAITING).build());
  }

  private void activateMissingRecordsStateExecutionInstance(long timeStamp) {
    final StateExecutionInstance.Builder stateExecutionInstanceBuilder =
        StateExecutionInstance.Builder.aStateExecutionInstance()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .startTs(2L)
            .endTs(timeStamp)
            .createdAt(timeStamp);

    persistence.save(stateExecutionInstanceBuilder.uuid(generateUuid()).status(SUCCESS).build());
    persistence.save(stateExecutionInstanceBuilder.uuid(generateUuid()).status(ExecutionStatus.ERROR).build());
    persistence.save(stateExecutionInstanceBuilder.uuid(generateUuid()).status(ExecutionStatus.FAILED).build());
    persistence.save(stateExecutionInstanceBuilder.uuid(generateUuid()).status(ExecutionStatus.ABORTED).build());

    persistence.save(stateExecutionInstanceBuilder.uuid(generateUuid()).status(RUNNING).build());

    persistence.save(stateExecutionInstanceBuilder.uuid(generateUuid()).status(PAUSED).build());
    persistence.save(stateExecutionInstanceBuilder.uuid(generateUuid()).status(WAITING).build());
  }

  private void activateMissingRecordsExecutionInterrupt() {
    final ExecutionInterruptBuilder executionInterruptBuilder =
        ExecutionInterruptBuilder.anExecutionInterrupt()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .createdAt(2l)
            .executionUuid("abc")
            .executionInterruptType(ExecutionInterruptType.IGNORE);

    persistence.save(executionInterruptBuilder.uuid(generateUuid()).build());
    persistence.save(executionInterruptBuilder.uuid(generateUuid()).build());
    persistence.save(executionInterruptBuilder.uuid(generateUuid()).build());
    persistence.save(executionInterruptBuilder.uuid(generateUuid()).build());

    persistence.save(executionInterruptBuilder.uuid(generateUuid()).build());

    persistence.save(executionInterruptBuilder.uuid(generateUuid()).build());
    persistence.save(executionInterruptBuilder.uuid(generateUuid()).build());
  }

  public void deactivateMissingRecords(ResultSet resultSet) throws Exception {
    doAnswer((Answer<Boolean>) invocation -> true).when(resultSet).next();
    doReturn(1L).when(resultSet).getLong(anyInt());
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testInvalidTSDB() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();

      when(timeScaleDBService.isValid()).thenReturn(false);

      ReconciliationStatus status = deploymentReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentExecutionEntity);
      assertThat(status).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testAllOK() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();
      deploymentReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentExecutionEntity);
      DeploymentReconRecord latestRecord =
          deploymentReconRecordRepository.getLatestDeploymentReconRecord(ACCOUNT_ID, sourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.SUCCESS);
      assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.NONE);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

      ReconciliationStatus status = deploymentReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentExecutionEntity);
      assertThat(status).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testAllOKStateExecutionInstance() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();
      deploymentStepReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentStepExecutionEntity);
      DeploymentReconRecord latestRecord = deploymentReconRecordRepository.getLatestDeploymentReconRecord(
          ACCOUNT_ID, stateExecutionInstanceSourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.SUCCESS);
      assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.NONE);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

      ReconciliationStatus status = deploymentStepReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentStepExecutionEntity);
      assertThat(status).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testAllOKExecutionInterrupt() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();
      executionInterruptReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, executionInterruptEntity);
      DeploymentReconRecord latestRecord = deploymentReconRecordRepository.getLatestDeploymentReconRecord(
          ACCOUNT_ID, executionInterruptSourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.SUCCESS);
      assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.NONE);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

      ReconciliationStatus status = executionInterruptReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, executionInterruptEntity);
      assertThat(status).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testMissingRecords() throws Exception {
    final long durationStartTs = System.currentTimeMillis() - 2000000;
    final long durationEndTs = System.currentTimeMillis();
    activateMissingRecords(durationEndTs - 1000L);
    deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs, deploymentExecutionEntity);
    DeploymentReconRecord latestRecord =
        deploymentReconRecordRepository.getLatestDeploymentReconRecord(ACCOUNT_ID, sourceEntityClass);
    assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
    assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
    assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.MISSING_RECORDS_DETECTED);
    assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.ADD_MISSING_RECORDS);
    assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testMissingRecordsStateExecutionInstance() throws Exception {
    final long durationStartTs = System.currentTimeMillis() - 2000000;
    final long durationEndTs = System.currentTimeMillis();
    activateMissingRecordsStateExecutionInstance(durationEndTs - 1000L);
    deploymentStepReconService.performReconciliation(
        ACCOUNT_ID, durationStartTs, durationEndTs, deploymentStepExecutionEntity);
    DeploymentReconRecord latestRecord = deploymentReconRecordRepository.getLatestDeploymentReconRecord(
        ACCOUNT_ID, stateExecutionInstanceSourceEntityClass);
    assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
    assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
    assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.MISSING_RECORDS_DETECTED);
    assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.ADD_MISSING_RECORDS);
    assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testMissingRecordsExecutionInterrupt() throws Exception {
    activateMissingRecordsExecutionInterrupt();
    final long durationEndTs = System.currentTimeMillis();
    final long durationStartTs = durationEndTs - 2000000l;
    executionInterruptReconService.performReconciliation(
        ACCOUNT_ID, durationStartTs, durationEndTs, executionInterruptEntity);
    DeploymentReconRecord latestRecord =
        deploymentReconRecordRepository.getLatestDeploymentReconRecord(ACCOUNT_ID, executionInterruptSourceEntityClass);
    assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
    assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
    assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.MISSING_RECORDS_DETECTED);
    assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.ADD_MISSING_RECORDS);
    assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testMissingRecordsDuplicatesFound() throws Exception {
    activateDuplicatesFound(resultSet1);
    final long durationStartTs = System.currentTimeMillis() - 2000000;
    final long durationEndTs = System.currentTimeMillis();
    activateMissingRecords(durationEndTs - 1300);
    deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs, deploymentExecutionEntity);
    DeploymentReconRecord latestRecord =
        deploymentReconRecordRepository.getLatestDeploymentReconRecord(ACCOUNT_ID, sourceEntityClass);
    assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
    assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
    assertThat(latestRecord.getDetectionStatus())
        .isEqualTo(DetectionStatus.DUPLICATE_DETECTED_MISSING_RECORDS_DETECTED);
    assertThat(latestRecord.getReconcilationAction())
        .isEqualTo(ReconcilationAction.DUPLICATE_REMOVAL_ADD_MISSING_RECORDS);
    assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testMissingRecordsDuplicatesFoundStateExecutionInstance() throws Exception {
    activateDuplicatesFound(resultSet1);
    final long durationStartTs = System.currentTimeMillis() - 2000000;
    final long durationEndTs = System.currentTimeMillis();
    activateMissingRecordsStateExecutionInstance(durationEndTs - 1300);
    deploymentStepReconService.performReconciliation(
        ACCOUNT_ID, durationStartTs, durationEndTs, deploymentStepExecutionEntity);
    DeploymentReconRecord latestRecord = deploymentReconRecordRepository.getLatestDeploymentReconRecord(
        ACCOUNT_ID, stateExecutionInstanceSourceEntityClass);
    assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
    assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
    assertThat(latestRecord.getDetectionStatus())
        .isEqualTo(DetectionStatus.DUPLICATE_DETECTED_MISSING_RECORDS_DETECTED);
    assertThat(latestRecord.getReconcilationAction())
        .isEqualTo(ReconcilationAction.DUPLICATE_REMOVAL_ADD_MISSING_RECORDS);
    assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testMissingRecordsDuplicatesFoundExecutionInterrupt() throws Exception {
    activateDuplicatesFound(resultSet1);
    activateMissingRecordsExecutionInterrupt();
    final long durationStartTs = System.currentTimeMillis() - 2000000;
    final long durationEndTs = System.currentTimeMillis();
    executionInterruptReconService.performReconciliation(
        ACCOUNT_ID, durationStartTs, durationEndTs, executionInterruptEntity);
    DeploymentReconRecord latestRecord =
        deploymentReconRecordRepository.getLatestDeploymentReconRecord(ACCOUNT_ID, executionInterruptSourceEntityClass);
    assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
    assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
    assertThat(latestRecord.getDetectionStatus())
        .isEqualTo(DetectionStatus.DUPLICATE_DETECTED_MISSING_RECORDS_DETECTED);
    assertThat(latestRecord.getReconcilationAction())
        .isEqualTo(ReconcilationAction.DUPLICATE_REMOVAL_ADD_MISSING_RECORDS);
    assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testDuplicatesDetected() {
    try {
      activateDuplicatesFound(resultSet1);
      when(resultSet1.getString(anyInt())).thenReturn("DATA" + duplicatesCount[0]);

      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();
      deploymentReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentExecutionEntity);
      DeploymentReconRecord latestRecord =
          deploymentReconRecordRepository.getLatestDeploymentReconRecord(ACCOUNT_ID, sourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.DUPLICATE_DETECTED);
      assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.DUPLICATE_REMOVAL);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testDuplicatesDetectedStateExecutionInstance() {
    try {
      activateDuplicatesFound(resultSet1);
      when(resultSet1.getString(anyInt())).thenReturn("DATA" + duplicatesCount[0]);

      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();
      deploymentStepReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentStepExecutionEntity);
      DeploymentReconRecord latestRecord = deploymentReconRecordRepository.getLatestDeploymentReconRecord(
          ACCOUNT_ID, stateExecutionInstanceSourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.DUPLICATE_DETECTED);
      assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.DUPLICATE_REMOVAL);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testDuplicatesDetectedExecutionInterrupt() {
    try {
      activateDuplicatesFound(resultSet1);
      when(resultSet1.getString(anyInt())).thenReturn("DATA" + duplicatesCount[0]);

      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();
      executionInterruptReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, executionInterruptEntity);
      DeploymentReconRecord latestRecord = deploymentReconRecordRepository.getLatestDeploymentReconRecord(
          ACCOUNT_ID, executionInterruptSourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.DUPLICATE_DETECTED);
      assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.DUPLICATE_REMOVAL);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testShouldPerformReconciliation() {
    DeploymentReconRecord reconRecord = DeploymentReconRecord.builder()
                                            .reconciliationStatus(ReconciliationStatus.IN_PROGRESS)
                                            .durationEndTs(System.currentTimeMillis() - 1000)
                                            .build();

    persistence = mock(HPersistence.class);
    on(deploymentReconService).set("persistence", persistence);
    on(deploymentReconRecordRepository).set("persistence", persistence);

    assertThat(shouldPerformReconciliation(
                   reconRecord, System.currentTimeMillis() - 1000, persistence, deploymentReconRecordRepository))
        .isFalse();

    reconRecord = DeploymentReconRecord.builder()
                      .reconciliationStatus(ReconciliationStatus.IN_PROGRESS)
                      .durationEndTs(System.currentTimeMillis() - 2 * DeploymentReconServiceHelper.COOL_DOWN_INTERVAL)
                      .build();

    final UpdateOperations updateOperations = mock(UpdateOperations.class);
    when(persistence.createUpdateOperations(DeploymentReconRecord.class)).thenReturn(updateOperations);
    assertThat(shouldPerformReconciliation(reconRecord,
                   System.currentTimeMillis() - 2 * DeploymentReconServiceHelper.COOL_DOWN_INTERVAL, persistence,
                   deploymentReconRecordRepository))
        .isTrue();

    verify(persistence, times(1)).createUpdateOperations(DeploymentReconRecord.class);
    verify(persistence, times(1)).update(reconRecord, updateOperations);

    reconRecord = DeploymentReconRecord.builder()
                      .reconciliationStatus(ReconciliationStatus.SUCCESS)
                      .durationEndTs(System.currentTimeMillis() - 2 * DeploymentReconServiceHelper.COOL_DOWN_INTERVAL)
                      .build();

    assertThat(shouldPerformReconciliation(
                   reconRecord, System.currentTimeMillis(), persistence, deploymentReconRecordRepository))
        .isTrue();

    reconRecord = DeploymentReconRecord.builder()
                      .reconciliationStatus(ReconciliationStatus.SUCCESS)
                      .reconEndTs(System.currentTimeMillis() - 1000)
                      .durationEndTs(System.currentTimeMillis() - 1000)
                      .build();
    assertThat(shouldPerformReconciliation(
                   reconRecord, System.currentTimeMillis() - 1000, persistence, deploymentReconRecordRepository))
        .isFalse();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testStatusMismatchDetected() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();

      deactivateDuplicatesFound(resultSet1);
      deactivateMissingRecords(resultSet2);
      activateStatusMismatch(resultSet3, durationEndTs - 1300);
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet3).getString(anyInt());

      deploymentReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentExecutionEntity);
      DeploymentReconRecord latestRecord =
          deploymentReconRecordRepository.getLatestDeploymentReconRecord(ACCOUNT_ID, sourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.STATUS_MISMATCH_DETECTED);
      assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.STATUS_RECONCILIATION);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testStatusMismatchDetectedStateExecutionInstance() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();

      deactivateDuplicatesFound(resultSet1);
      deactivateMissingRecords(resultSet2);
      activateStatusMismatchStateExecutionInstance(resultSet3, durationEndTs - 1300);
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet3).getString(anyInt());

      deploymentStepReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentStepExecutionEntity);
      DeploymentReconRecord latestRecord = deploymentReconRecordRepository.getLatestDeploymentReconRecord(
          ACCOUNT_ID, stateExecutionInstanceSourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.STATUS_MISMATCH_DETECTED);
      assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.STATUS_RECONCILIATION);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testMissingRecordsStatusMismatchDetected() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();

      deactivateDuplicatesFound(resultSet1);
      activateStatusMismatch(resultSet4, durationEndTs - 1300);
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet4).getString(anyInt());

      deploymentReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentExecutionEntity);
      DeploymentReconRecord latestRecord =
          deploymentReconRecordRepository.getLatestDeploymentReconRecord(ACCOUNT_ID, sourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus())
          .isEqualTo(DetectionStatus.MISSING_RECORDS_DETECTED_STATUS_MISMATCH_DETECTED);
      assertThat(latestRecord.getReconcilationAction())
          .isEqualTo(ReconcilationAction.ADD_MISSING_RECORDS_STATUS_RECONCILIATION);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testMissingRecordsStatusMismatchDetectedStateExecutionInstance() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();

      deactivateDuplicatesFound(resultSet1);
      activateStatusMismatchStateExecutionInstance(resultSet4, durationEndTs - 1300);
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet4).getString(anyInt());

      deploymentStepReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentStepExecutionEntity);
      DeploymentReconRecord latestRecord = deploymentReconRecordRepository.getLatestDeploymentReconRecord(
          ACCOUNT_ID, stateExecutionInstanceSourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus())
          .isEqualTo(DetectionStatus.MISSING_RECORDS_DETECTED_STATUS_MISMATCH_DETECTED);
      assertThat(latestRecord.getReconcilationAction())
          .isEqualTo(ReconcilationAction.ADD_MISSING_RECORDS_STATUS_RECONCILIATION);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testDuplicatesStatusMismatchDetected() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();

      activateDuplicatesFound(resultSet1);
      deactivateMissingRecords(resultSet2);
      activateStatusMismatch(resultSet3, durationEndTs - 1300);
      doReturn("DATA" + duplicatesCount[0]).when(resultSet1).getString(any());
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet3).getString(anyInt());

      deploymentReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentExecutionEntity);
      DeploymentReconRecord latestRecord =
          deploymentReconRecordRepository.getLatestDeploymentReconRecord(ACCOUNT_ID, sourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus())
          .isEqualTo(DetectionStatus.DUPLICATE_DETECTED_STATUS_MISMATCH_DETECTED);
      assertThat(latestRecord.getReconcilationAction())
          .isEqualTo(ReconcilationAction.DUPLICATE_REMOVAL_STATUS_RECONCILIATION);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testDuplicatesStatusMismatchDetectedStateExecutionInstance() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();

      activateDuplicatesFound(resultSet1);
      deactivateMissingRecords(resultSet2);
      activateStatusMismatchStateExecutionInstance(resultSet3, durationEndTs - 1300);
      doReturn("DATA" + duplicatesCount[0]).when(resultSet1).getString(any());
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet3).getString(anyInt());

      deploymentStepReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentStepExecutionEntity);
      DeploymentReconRecord latestRecord = deploymentReconRecordRepository.getLatestDeploymentReconRecord(
          ACCOUNT_ID, stateExecutionInstanceSourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus())
          .isEqualTo(DetectionStatus.DUPLICATE_DETECTED_STATUS_MISMATCH_DETECTED);
      assertThat(latestRecord.getReconcilationAction())
          .isEqualTo(ReconcilationAction.DUPLICATE_REMOVAL_STATUS_RECONCILIATION);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testAllDetected() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();

      activateDuplicatesFound(resultSet1);
      activateStatusMismatch(resultSet4, durationEndTs - 1300);
      doReturn("DATA" + duplicatesCount[0]).when(resultSet1).getString(any());
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet4).getString(anyInt());

      deploymentReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentExecutionEntity);
      DeploymentReconRecord latestRecord =
          deploymentReconRecordRepository.getLatestDeploymentReconRecord(ACCOUNT_ID, sourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus())
          .isEqualTo(DetectionStatus.DUPLICATE_DETECTED_MISSING_RECORDS_DETECTED_STATUS_MISMATCH_DETECTED);
      assertThat(latestRecord.getReconcilationAction())
          .isEqualTo(ReconcilationAction.DUPLICATE_REMOVAL_ADD_MISSING_RECORDS_STATUS_RECONCILIATION);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testAllDetectedStateExecutionInstance() {
    try {
      final long durationStartTs = System.currentTimeMillis() - 2000000;
      final long durationEndTs = System.currentTimeMillis();

      activateDuplicatesFound(resultSet1);
      activateStatusMismatchStateExecutionInstance(resultSet4, durationEndTs - 1300);
      doReturn("DATA" + duplicatesCount[0]).when(resultSet1).getString(any());
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet4).getString(anyInt());

      deploymentStepReconService.performReconciliation(
          ACCOUNT_ID, durationStartTs, durationEndTs, deploymentStepExecutionEntity);
      DeploymentReconRecord latestRecord = deploymentReconRecordRepository.getLatestDeploymentReconRecord(
          ACCOUNT_ID, stateExecutionInstanceSourceEntityClass);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus())
          .isEqualTo(DetectionStatus.DUPLICATE_DETECTED_MISSING_RECORDS_DETECTED_STATUS_MISMATCH_DETECTED);
      assertThat(latestRecord.getReconcilationAction())
          .isEqualTo(ReconcilationAction.DUPLICATE_REMOVAL_ADD_MISSING_RECORDS_STATUS_RECONCILIATION);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

    } catch (Exception e) {
      fail(e.getMessage());
    }
  }
}
