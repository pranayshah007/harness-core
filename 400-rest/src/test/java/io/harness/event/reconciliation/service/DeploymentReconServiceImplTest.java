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
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.RUSHABH;

import static software.wings.beans.CountsByStatuses.Builder.aCountsByStatuses;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.event.reconciliation.DetectionStatus;
import io.harness.event.reconciliation.ReconcilationAction;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecord;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.WingsBaseTest;
import software.wings.beans.CountsByStatuses;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionBuilder;

import com.google.inject.Inject;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.mongodb.morphia.query.UpdateOperations;

public class DeploymentReconServiceImplTest extends WingsBaseTest {
  @Mock TimeScaleDBService timeScaleDBService;
  @Inject @InjectMocks DeploymentReconServiceImpl deploymentReconService;
  @Inject private HPersistence persistence;
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

      ReconciliationStatus status =
          deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs);
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
      deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs);
      DeploymentReconRecord latestRecord = deploymentReconService.getLatestDeploymentReconRecord(ACCOUNT_ID);
      assertThat(latestRecord.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(latestRecord.getDurationStartTs()).isEqualTo(durationStartTs);
      assertThat(latestRecord.getDurationEndTs()).isEqualTo(durationEndTs);
      assertThat(latestRecord.getDetectionStatus()).isEqualTo(DetectionStatus.SUCCESS);
      assertThat(latestRecord.getReconcilationAction()).isEqualTo(ReconcilationAction.NONE);
      assertThat(latestRecord.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SUCCESS);

      ReconciliationStatus status =
          deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs);
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
    deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs);
    DeploymentReconRecord latestRecord = deploymentReconService.getLatestDeploymentReconRecord(ACCOUNT_ID);
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
    deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs);
    DeploymentReconRecord latestRecord = deploymentReconService.getLatestDeploymentReconRecord(ACCOUNT_ID);
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
      deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs);
      DeploymentReconRecord latestRecord = deploymentReconService.getLatestDeploymentReconRecord(ACCOUNT_ID);
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

    assertThat(deploymentReconService.shouldPerformReconciliation(reconRecord, System.currentTimeMillis() - 1000))
        .isFalse();

    reconRecord = DeploymentReconRecord.builder()
                      .reconciliationStatus(ReconciliationStatus.IN_PROGRESS)
                      .durationEndTs(System.currentTimeMillis() - 2 * DeploymentReconServiceImpl.COOL_DOWN_INTERVAL)
                      .build();

    final UpdateOperations updateOperations = mock(UpdateOperations.class);
    when(persistence.createUpdateOperations(DeploymentReconRecord.class)).thenReturn(updateOperations);
    assertThat(deploymentReconService.shouldPerformReconciliation(
                   reconRecord, System.currentTimeMillis() - 2 * DeploymentReconServiceImpl.COOL_DOWN_INTERVAL))
        .isTrue();

    verify(persistence, times(1)).createUpdateOperations(DeploymentReconRecord.class);
    verify(persistence, times(1)).update(reconRecord, updateOperations);

    reconRecord = DeploymentReconRecord.builder()
                      .reconciliationStatus(ReconciliationStatus.SUCCESS)
                      .durationEndTs(System.currentTimeMillis() - 2 * DeploymentReconServiceImpl.COOL_DOWN_INTERVAL)
                      .build();

    assertThat(deploymentReconService.shouldPerformReconciliation(reconRecord, System.currentTimeMillis())).isTrue();

    reconRecord = DeploymentReconRecord.builder()
                      .reconciliationStatus(ReconciliationStatus.SUCCESS)
                      .reconEndTs(System.currentTimeMillis() - 1000)
                      .durationEndTs(System.currentTimeMillis() - 1000)
                      .build();
    assertThat(deploymentReconService.shouldPerformReconciliation(reconRecord, System.currentTimeMillis() - 1000))
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
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet3).getString(any());

      deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs);
      DeploymentReconRecord latestRecord = deploymentReconService.getLatestDeploymentReconRecord(ACCOUNT_ID);
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
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet4).getString(any());

      deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs);
      DeploymentReconRecord latestRecord = deploymentReconService.getLatestDeploymentReconRecord(ACCOUNT_ID);
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
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet3).getString(any());

      deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs);
      DeploymentReconRecord latestRecord = deploymentReconService.getLatestDeploymentReconRecord(ACCOUNT_ID);
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
      doReturn("DATA" + statusMismatchCount[0]).when(resultSet4).getString(any());

      deploymentReconService.performReconciliation(ACCOUNT_ID, durationStartTs, durationEndTs);
      DeploymentReconRecord latestRecord = deploymentReconService.getLatestDeploymentReconRecord(ACCOUNT_ID);
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
