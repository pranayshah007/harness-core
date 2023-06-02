/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.MOHIT_GARG;
import static io.harness.rule.OwnerRule.VUK;
import static io.harness.rule.OwnerRule.XIN;

import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.NO_ELIGIBLE_DELEGATES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.DelegateTaskValidationFailedException;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.beans.NoAvailableDelegatesException;
import io.harness.delegate.beans.NoInstalledDelegatesException;
import io.harness.exception.DelegateTaskExpiredException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.observer.Subject;
import io.harness.perpetualtask.ecs.EcsPerpetualTaskServiceClient;
import io.harness.perpetualtask.instancesync.AwsSshInstanceSyncPerpetualTaskParams;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecordDao;
import io.harness.perpetualtask.internal.PerpetualTaskRecordHandler;
import io.harness.rule.Owner;
import io.harness.service.intfc.PerpetualTaskStateObserver;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import com.google.protobuf.Any;
import com.google.protobuf.util.Durations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class PerpetualTaskServiceImplTest extends WingsBaseTest {
  @Mock private PerpetualTaskServiceClient client;

  @Inject private PerpetualTaskRecordDao perpetualTaskRecordDao;
  @Mock private Subject<PerpetualTaskStateObserver> perpetualTaskStateObserverSubject;
  @Inject private PerpetualTaskScheduleService perpetualTaskScheduleService;

  @InjectMocks @Inject private PerpetualTaskServiceImpl perpetualTaskService;

  @Mock DelegateService delegateService;
  @Mock private PerpetualTaskServiceClientRegistry clientRegistry;
  @InjectMocks @Inject private PerpetualTaskRecordHandler perpetualTaskRecordHandler;

  @Inject private BroadcasterFactory broadcasterFactory;
  @Mock private Broadcaster broadcaster;

  public static final String TASK_DESCRIPTION = "taskDescription";
  private final String ACCOUNT_ID = "test-account-id";
  private final String REGION = "region";
  private final String DELEGATE_ID = "test-delegate-id";
  private final String SETTING_ID = "settingId";
  private final String CLUSTER_NAME = "clusterName";

  private final String TASK_FAILURE_EXCEPTION_MSG = "exception occurred";
  private final long HEARTBEAT_MILLIS = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
  private final long OLD_HEARTBEAT_MILLIS = Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli();
  private Set<Pair<String, String>> testBroadcastAggregateSet = new ConcurrentHashSet<>();

  @Before
  public void setup() throws IllegalAccessException {
    PerpetualTaskServiceClientRegistry clientRegistry = new PerpetualTaskServiceClientRegistry();
    clientRegistry.registerClient(PerpetualTaskType.ECS_CLUSTER, new EcsPerpetualTaskServiceClient());
    clientRegistry.registerClient(PerpetualTaskType.K8S_WATCH, client);
    FieldUtils.writeField(perpetualTaskService, "broadcastAggregateSet", testBroadcastAggregateSet, true);
    FieldUtils.writeField(perpetualTaskService, "clientRegistry", clientRegistry, true);
    FieldUtils.writeField(
        perpetualTaskService, "perpetualTaskStateObserverSubject", perpetualTaskStateObserverSubject, true);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testCreateTask() {
    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);

    String taskId = perpetualTaskService.createTask(
        PerpetualTaskType.ECS_CLUSTER, ACCOUNT_ID, clientContext, perpetualTaskSchedule(), false, TASK_DESCRIPTION);
    assertThat(taskId).isNotNull();

    String taskIdDuplicate = perpetualTaskService.createTask(
        PerpetualTaskType.ECS_CLUSTER, ACCOUNT_ID, clientContext, perpetualTaskSchedule(), false, TASK_DESCRIPTION);
    assertThat(taskIdDuplicate).isEqualTo(taskId);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCustomTimeIntervalIfNotPresent() {
    long finalTaskTimeInterval =
        perpetualTaskService.getTaskTimeInterval(perpetualTaskSchedule(), ACCOUNT_ID, PerpetualTaskType.ECS_CLUSTER);

    assertThat(finalTaskTimeInterval).isEqualTo(perpetualTaskSchedule().getInterval().getSeconds());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCustomTimeIntervalIfPresent() {
    long customTimeIntervalInMs = 100000;
    perpetualTaskScheduleService.save(ACCOUNT_ID, PerpetualTaskType.ECS_CLUSTER, customTimeIntervalInMs);
    long finalTaskTimeInterval =
        perpetualTaskService.getTaskTimeInterval(perpetualTaskSchedule(), ACCOUNT_ID, PerpetualTaskType.ECS_CLUSTER);

    assertThat(finalTaskTimeInterval).isEqualTo(customTimeIntervalInMs / 1000);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testResetTaskWithoutTaskParams() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    String taskId = generateUuid();

    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(accountId);
    perpetualTaskRecord.setDelegateId(delegateId);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    boolean resetTask = perpetualTaskService.resetTask(accountId, taskId, null);

    PerpetualTaskRecord record = perpetualTaskService.getTaskRecord(taskId);

    assertThat(record).isNotNull();
    assertThat(record.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(resetTask).isTrue();
    assertThat(record.getClientContext().getClientParams()).isEqualTo(clientContext.getClientParams());
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testResetTaskWithTaskParams() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    String taskId = generateUuid();

    PerpetualTaskClientContext clientContext = clientContextWithTaskParams();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(accountId);
    perpetualTaskRecord.setDelegateId(delegateId);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    PerpetualTaskExecutionBundle taskExecutionBundle =
        PerpetualTaskExecutionBundle.newBuilder()
            .setTaskParams(Any.pack(AwsSshInstanceSyncPerpetualTaskParams.getDefaultInstance()))
            .build();

    boolean resetTask = perpetualTaskService.resetTask(accountId, taskId, taskExecutionBundle);

    PerpetualTaskRecord record = perpetualTaskService.getTaskRecord(taskId);

    assertThat(record).isNotNull();
    assertThat(record.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(resetTask).isTrue();
    assertThat(record.getClientContext().getExecutionBundle()).isEqualTo(taskExecutionBundle.toByteArray());
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testResetTaskWithClientContextNull() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    String taskId = generateUuid();

    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setAccountId(accountId);
    perpetualTaskRecord.setDelegateId(delegateId);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    boolean resetTask = perpetualTaskService.resetTask(accountId, taskId, null);

    PerpetualTaskRecord record = perpetualTaskService.getTaskRecord(taskId);

    assertThat(record).isNotNull();
    assertThat(record.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(resetTask).isTrue();
    assertThat(record.getClientContext()).isNotNull();
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testSingleUpdateTaskSchedule() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    String taskId = generateUuid();
    long intervalInMillis = 1000;

    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setAccountId(accountId);
    perpetualTaskRecord.setDelegateId(delegateId);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
    perpetualTaskRecord.setIntervalSeconds(intervalInMillis * 10);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    long updatedRecords =
        perpetualTaskService.updateTasksSchedule(accountId, PerpetualTaskType.K8S_WATCH, intervalInMillis);

    PerpetualTaskRecord record = perpetualTaskService.getTaskRecord(taskId);

    assertThat(updatedRecords).isEqualTo(1L);
    assertThat(record).isNotNull();
    assertThat(record.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(record.getIntervalSeconds()).isEqualTo(intervalInMillis / 1000);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testMultiUpdateTaskSchedule() {
    long intervalInMillis = 1000;

    for (int i = 0; i < 10; i++) {
      String delegateId = generateUuid();
      String taskId = generateUuid();
      PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
      perpetualTaskRecord.setDelegateId(delegateId);
      perpetualTaskRecord.setUuid(taskId);
      perpetualTaskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
      perpetualTaskRecord.setIntervalSeconds(intervalInMillis * 10);
      perpetualTaskRecordDao.save(perpetualTaskRecord);
    }

    long updatedRecords =
        perpetualTaskService.updateTasksSchedule(ACCOUNT_ID, PerpetualTaskType.K8S_WATCH, intervalInMillis);

    assertThat(updatedRecords).isEqualTo(10L);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testMultiUpdateTaskScheduleInCaseOfMultipleAccounts() {
    long intervalInMillis = 1000;

    for (int i = 0; i < 10; i++) {
      String delegateId = generateUuid();
      String taskId = generateUuid();
      PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
      perpetualTaskRecord.setDelegateId(delegateId);
      perpetualTaskRecord.setUuid(taskId);
      perpetualTaskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
      perpetualTaskRecord.setIntervalSeconds(intervalInMillis * 10);
      perpetualTaskRecordDao.save(perpetualTaskRecord);
    }

    for (int i = 0; i < 10; i++) {
      String delegateId = generateUuid();
      String taskId = generateUuid();
      PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
      perpetualTaskRecord.setDelegateId(delegateId);
      perpetualTaskRecord.setAccountId(generateUuid());
      perpetualTaskRecord.setUuid(taskId);
      perpetualTaskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
      perpetualTaskRecord.setIntervalSeconds(intervalInMillis * 10);
      perpetualTaskRecordDao.save(perpetualTaskRecord);
    }

    long updatedRecords =
        perpetualTaskService.updateTasksSchedule(ACCOUNT_ID, PerpetualTaskType.K8S_WATCH, intervalInMillis);

    assertThat(updatedRecords).isEqualTo(10L);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testMultiUpdateTaskScheduleInCaseOfMultipleTaskTypes() {
    long intervalInMillis = 1000;

    for (int i = 0; i < 5; i++) {
      String delegateId = generateUuid();
      String taskId = generateUuid();
      PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
      perpetualTaskRecord.setDelegateId(delegateId);
      perpetualTaskRecord.setUuid(taskId);
      perpetualTaskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
      perpetualTaskRecord.setIntervalSeconds(intervalInMillis * 10);
      perpetualTaskRecordDao.save(perpetualTaskRecord);
    }

    for (int i = 0; i < 5; i++) {
      String delegateId = generateUuid();
      String taskId = generateUuid();
      PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
      perpetualTaskRecord.setDelegateId(delegateId);
      perpetualTaskRecord.setPerpetualTaskType("TASK_TYPE");
      perpetualTaskRecord.setAccountId(generateUuid());
      perpetualTaskRecord.setUuid(taskId);
      perpetualTaskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
      perpetualTaskRecord.setIntervalSeconds(intervalInMillis * 10);
      perpetualTaskRecordDao.save(perpetualTaskRecord);
    }

    long updatedRecords =
        perpetualTaskService.updateTasksSchedule(ACCOUNT_ID, PerpetualTaskType.K8S_WATCH, intervalInMillis);

    assertThat(updatedRecords).isEqualTo(5L);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testDeleteTask() {
    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);

    String taskId = perpetualTaskService.createTask(
        PerpetualTaskType.ECS_CLUSTER, ACCOUNT_ID, clientContext, perpetualTaskSchedule(), false, TASK_DESCRIPTION);

    boolean deletedTask = perpetualTaskService.deleteTask(ACCOUNT_ID, taskId);

    assertThat(taskId).isNotNull();
    assertThat(deletedTask).isTrue();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testGetTaskRecord() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    String taskId = generateUuid();

    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(accountId);
    perpetualTaskRecord.setDelegateId(delegateId);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    PerpetualTaskRecord record = perpetualTaskService.getTaskRecord(taskId);

    assertThat(record).isNotNull();
    assertThat(record.getAccountId()).isEqualTo(accountId);
    assertThat(record.getDelegateId()).isEqualTo(delegateId);
    assertThat(record.getClientContext()).isEqualTo(clientContext);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testPerpetualTaskType() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    String taskId = generateUuid();
    String perpetualTaskType = generateUuid();

    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(accountId);
    perpetualTaskRecord.setDelegateId(delegateId);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecord.setPerpetualTaskType(perpetualTaskType);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    String perpetualTaskTypeResult = perpetualTaskService.getPerpetualTaskType(taskId);

    assertThat(perpetualTaskTypeResult).isNotNull();
    assertThat(perpetualTaskTypeResult).isEqualTo(perpetualTaskType);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testUpdateHeartbeatUnassigned() {
    String taskId = perpetualTaskService.createTask(
        PerpetualTaskType.ECS_CLUSTER, ACCOUNT_ID, clientContext(), perpetualTaskSchedule(), false, TASK_DESCRIPTION);
    boolean updateHeartbeat = perpetualTaskService.triggerCallback(taskId, HEARTBEAT_MILLIS, perpetualTaskResponse());
    assertThat(updateHeartbeat).isFalse();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testUpdateHeartbeatAssigned() {
    String taskId = perpetualTaskService.createTask(
        PerpetualTaskType.ECS_CLUSTER, ACCOUNT_ID, clientContext(), perpetualTaskSchedule(), false, TASK_DESCRIPTION);
    perpetualTaskService.appointDelegate(ACCOUNT_ID, taskId, DELEGATE_ID, 0);
    boolean updateHeartbeat = perpetualTaskService.triggerCallback(taskId, HEARTBEAT_MILLIS, perpetualTaskResponse());
    assertThat(updateHeartbeat).isTrue();
  }

  private PerpetualTaskResponse perpetualTaskResponse() {
    return PerpetualTaskResponse.builder().responseCode(200).build();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldNotUpdateHeartbeat() {
    String taskId = perpetualTaskService.createTask(
        PerpetualTaskType.ECS_CLUSTER, ACCOUNT_ID, clientContext(), perpetualTaskSchedule(), false, TASK_DESCRIPTION);
    perpetualTaskService.triggerCallback(taskId, HEARTBEAT_MILLIS, perpetualTaskResponse());
    boolean updateHeartbeat =
        perpetualTaskService.triggerCallback(taskId, OLD_HEARTBEAT_MILLIS, perpetualTaskResponse());
    assertThat(updateHeartbeat).isFalse();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testAppointDelegate() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    String taskId = generateUuid();

    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(accountId);
    perpetualTaskRecord.setDelegateId(null);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    perpetualTaskService.appointDelegate(accountId, taskId, delegateId, 1L);

    PerpetualTaskRecord record = perpetualTaskRecordDao.getTask(taskId);
    assertThat(record).isNotNull();
    assertThat(record.getDelegateId()).isEqualTo(delegateId);
    assertThat(record.getClientContext().getLastContextUpdated()).isEqualTo(1L);
    verify(perpetualTaskStateObserverSubject).fireInform(any(), eq(accountId), eq(taskId), eq(delegateId));

    String delegateId2 = generateUuid();

    perpetualTaskService.appointDelegate(accountId, taskId, delegateId, 1L);
    perpetualTaskService.appointDelegate(accountId, taskId, delegateId2, 1L);
    assertThat(testBroadcastAggregateSet).isNotNull();
    assertThat(testBroadcastAggregateSet).size().isEqualTo(2);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testBroadcastToDelegate() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    String taskId = generateUuid();

    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(accountId);
    perpetualTaskRecord.setDelegateId(delegateId);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    perpetualTaskService.appointDelegate(accountId, taskId, delegateId, 1L);

    assertThat(testBroadcastAggregateSet).isNotNull();
    assertThat(testBroadcastAggregateSet).size().isEqualTo(1);
    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
    perpetualTaskService.broadcastToDelegate();
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testPerpetualTaskErrorHandlingBackoff() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    String taskId = generateUuid();

    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(accountId);
    perpetualTaskRecord.setDelegateId(null);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecord.setFailedExecutionCount(4);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    perpetualTaskService.appointDelegate(accountId, taskId, delegateId, 1L);

    boolean result = perpetualTaskService.triggerCallback(taskId, HEARTBEAT_MILLIS, perpetualTaskFailedResponse());
    PerpetualTaskRecord resultPerpetualTaskRecord = perpetualTaskRecordDao.getTask(taskId);

    assertThat(result).isEqualTo(true);
    assertThat(resultPerpetualTaskRecord.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    assertThat(resultPerpetualTaskRecord.getUnassignedReason())
        .isEqualTo(PerpetualTaskUnassignedReason.MULTIPLE_FAILED_PERPETUAL_TASK);
    assertThat(resultPerpetualTaskRecord.getFailedExecutionCount()).isEqualTo(5);
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testPerpetualTaskErrorHandlingNonBackoff() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    String taskId = generateUuid();

    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(accountId);
    perpetualTaskRecord.setDelegateId(null);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecord.setFailedExecutionCount(3);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    perpetualTaskService.appointDelegate(accountId, taskId, delegateId, 1L);

    boolean result = perpetualTaskService.triggerCallback(taskId, HEARTBEAT_MILLIS, perpetualTaskFailedResponse());
    PerpetualTaskRecord resultPerpetualTaskRecord = perpetualTaskRecordDao.getTask(taskId);

    assertThat(result).isEqualTo(true);
    assertThat(resultPerpetualTaskRecord.getFailedExecutionCount()).isEqualTo(4);
    assertThat(resultPerpetualTaskRecord.getState()).isEqualTo(PerpetualTaskState.TASK_ASSIGNED);
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testPerpetualTaskSuccess() {
    String accountId = generateUuid();
    String delegateId = generateUuid();
    String taskId = generateUuid();

    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(accountId);
    perpetualTaskRecord.setDelegateId(null);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecord.setFailedExecutionCount(5);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    perpetualTaskService.appointDelegate(accountId, taskId, delegateId, 1L);

    boolean result = perpetualTaskService.triggerCallback(taskId, HEARTBEAT_MILLIS, perpetualTaskResponse());
    PerpetualTaskRecord resultPerpetualTaskRecord = perpetualTaskRecordDao.getTask(taskId);

    assertThat(result).isEqualTo(true);
    assertThat(resultPerpetualTaskRecord.getFailedExecutionCount()).isEqualTo(0);
    assertThat(resultPerpetualTaskRecord.getState()).isEqualTo(PerpetualTaskState.TASK_ASSIGNED);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskAssignedList() {
    String taskId = generateUuid();

    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(ACCOUNT_ID);
    perpetualTaskRecord.setDelegateId(DELEGATE_ID);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
    perpetualTaskRecordDao.save(perpetualTaskRecord);
    List<PerpetualTaskAssignDetails> perpetualTaskAssignDetailsList =
        perpetualTaskService.listAssignedTasks(DELEGATE_ID, ACCOUNT_ID);
    assertThat(perpetualTaskAssignDetailsList).hasSize(1);
    assertThat(perpetualTaskAssignDetailsList.get(0).getTaskId().getId()).isEqualTo(taskId);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskState_FromUnAssigned_ToNonAssignable() throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecord();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new NoEligibleDelegatesInAccountException(NO_ELIGIBLE_DELEGATES);
    });
    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(updatedPtRecord.getAssignTryCount()).isEqualTo(1);
    assertThat(updatedPtRecord.getAssignAfterMs()).isGreaterThanOrEqualTo(System.currentTimeMillis());

    updatedPtRecord.setAssignTryCount(10);
    perpetualTaskRecordDao.save(updatedPtRecord);
    perpetualTaskRecordHandler.assign(updatedPtRecord);
    PerpetualTaskRecord updatedPtRecord1 = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord1.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnNonEligibleDelegate() throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecord();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new NoEligibleDelegatesInAccountException(NO_ELIGIBLE_DELEGATES);
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.NO_ELIGIBLE_DELEGATES);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnNonEligibleDelegate_AfterMaxAssignmentTry()
      throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecordAndAssignTryCountAsMax();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new NoEligibleDelegatesInAccountException(NO_ELIGIBLE_DELEGATES);
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.NO_ELIGIBLE_DELEGATES);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnNoAvailableDelegate() throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecord();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new NoAvailableDelegatesException();
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.NO_DELEGATE_AVAILABLE);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnNoAvailableDelegate_AfterMaxAssignmentTry()
      throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecordAndAssignTryCountAsMax();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new NoAvailableDelegatesException();
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.NO_DELEGATE_AVAILABLE);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnNoInstalledDelegate() throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecord();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new NoInstalledDelegatesException();
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.NO_DELEGATE_INSTALLED);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnTaskExpired() throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecord();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new DelegateTaskExpiredException("");
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.TASK_EXPIRED);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnTaskExpired_AfterMaxAssignmentTry() throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecordAndAssignTryCountAsMax();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new DelegateTaskExpiredException("");
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.TASK_EXPIRED);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnTaskValidationFailed() throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecord();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new DelegateTaskValidationFailedException("");
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.TASK_VALIDATION_FAILED);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnTaskValidationFailed_AfterMaxAssignmentTry()
      throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecordAndAssignTryCountAsMax();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new DelegateTaskValidationFailedException("");
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.TASK_VALIDATION_FAILED);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnInvalidArgumentException() throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecord();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new InvalidArgumentsException("");
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.TASK_EXPIRED);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnInvalidArgumentException_AfterMaxAssignmentTry()
      throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecordAndAssignTryCountAsMax();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new InvalidArgumentsException("");
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.TASK_EXPIRED);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnUnExpectedException() throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecord();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new InvalidArgumentsException("");
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.TASK_EXPIRED);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskNonAssignableState_OnUnExpectedException_AfterMaxAssignmentTry()
      throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecord();
    when(clientRegistry.getClient(perpetualTaskRecord.getPerpetualTaskType())).thenReturn(client);
    when(client.getValidationTask(clientContext(), perpetualTaskRecord.getAccountId()))
        .thenReturn(DelegateTask.builder().build());
    when(delegateService.executeTaskV2(nullable(DelegateTask.class))).thenAnswer(invocation -> {
      throw new InvalidArgumentsException("");
    });

    perpetualTaskRecordHandler.assign(perpetualTaskRecord);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(updatedPtRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.TASK_EXPIRED);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testUpdatePerpetualTaskAssignable() {
    PerpetualTaskRecord perpetualTaskRecord1 = createPerpetualTaskRecord();
    PerpetualTaskRecord perpetualTaskRecord2 = createPerpetualTaskRecord();
    PerpetualTaskRecord perpetualTaskRecord3 = createPerpetualTaskRecord();
    perpetualTaskRecordDao.updateTaskNonAssignableToAssignable(ACCOUNT_ID);
    PerpetualTaskRecord updatedPtRecord1 = perpetualTaskRecordDao.getTask(perpetualTaskRecord1.getUuid());
    assertThat(updatedPtRecord1.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(updatedPtRecord1.getUnassignedReason()).isNull();
    PerpetualTaskRecord updatedPtRecord2 = perpetualTaskRecordDao.getTask(perpetualTaskRecord2.getUuid());
    assertThat(updatedPtRecord2.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(updatedPtRecord2.getUnassignedReason()).isNull();
    PerpetualTaskRecord updatedPtRecord3 = perpetualTaskRecordDao.getTask(perpetualTaskRecord3.getUuid());
    assertThat(updatedPtRecord3.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(updatedPtRecord3.getUnassignedReason()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskContext_withNullTaskParams() {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecord();
    perpetualTaskRecordDao.save(perpetualTaskRecord);
    perpetualTaskService.perpetualTaskContext(perpetualTaskRecord.getUuid());
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_INVALID);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskContext_withNullTaskParamsAndAfterMaxAssignTry() {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecordAndAssignTryCountAsMax();
    perpetualTaskService.perpetualTaskContext(perpetualTaskRecord.getUuid());
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_INVALID);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testPerpetualTaskState_FromNonAssignable_ToUnAssigned() throws InterruptedException {
    PerpetualTaskRecord perpetualTaskRecord = createPerpetualTaskRecord();
    perpetualTaskRecord.setState(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    perpetualTaskRecord.setAssignTryCount(3);
    perpetualTaskRecord.setUnassignedReason(PerpetualTaskUnassignedReason.PT_TASK_FAILED);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    perpetualTaskRecordDao.updateTaskNonAssignableToAssignable(ACCOUNT_ID);
    PerpetualTaskRecord updatedPtRecord = perpetualTaskRecordDao.getTask(perpetualTaskRecord.getUuid());
    assertThat(updatedPtRecord.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldRecordTaskFailureMessage() {
    String taskId = generateUuid();

    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(ACCOUNT_ID);
    perpetualTaskRecord.setDelegateId(null);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecord.setFailedExecutionCount(4);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    perpetualTaskService.appointDelegate(ACCOUNT_ID, taskId, DELEGATE_ID, 1L);

    perpetualTaskService.recordTaskFailure(taskId, TASK_FAILURE_EXCEPTION_MSG);

    PerpetualTaskRecord updatedPTRecord = perpetualTaskRecordDao.getTask(taskId);

    assertThat(updatedPTRecord.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    assertThat(updatedPTRecord.getUnassignedReason())
        .isEqualTo(PerpetualTaskUnassignedReason.MULTIPLE_FAILED_PERPETUAL_TASK);
    assertThat(updatedPTRecord.getFailedExecutionCount()).isEqualTo(0);
    assertThat(updatedPTRecord.getException()).isEqualTo(TASK_FAILURE_EXCEPTION_MSG);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void shouldRecordTaskFailureMessage_AssignedTask() {
    String taskId = generateUuid();

    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setAccountId(ACCOUNT_ID);
    perpetualTaskRecord.setDelegateId(null);
    perpetualTaskRecord.setUuid(taskId);
    perpetualTaskRecord.setFailedExecutionCount(2);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    perpetualTaskService.appointDelegate(ACCOUNT_ID, taskId, DELEGATE_ID, 1L);

    perpetualTaskService.recordTaskFailure(taskId, TASK_FAILURE_EXCEPTION_MSG);

    PerpetualTaskRecord updatedPTRecord = perpetualTaskRecordDao.getTask(taskId);

    assertThat(updatedPTRecord.getState()).isEqualTo(PerpetualTaskState.TASK_ASSIGNED);
    assertThat(updatedPTRecord.getFailedExecutionCount()).isEqualTo(3);
  }

  private PerpetualTaskRecord createPerpetualTaskRecord() {
    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setState(PerpetualTaskState.TASK_UNASSIGNED);

    String taskId = perpetualTaskService.createTask(
        PerpetualTaskType.ECS_CLUSTER, ACCOUNT_ID, clientContext, perpetualTaskSchedule(), false, TASK_DESCRIPTION);
    assertThat(taskId).isNotNull();

    return perpetualTaskRecordDao.getTask(taskId);
  }

  private PerpetualTaskRecord createPerpetualTaskRecordAndAssignTryCountAsMax() {
    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskRecord();
    perpetualTaskRecord.setClientContext(clientContext);
    perpetualTaskRecord.setState(PerpetualTaskState.TASK_UNASSIGNED);
    perpetualTaskRecord.setAssignTryCount(11);

    String taskId = perpetualTaskService.createTask(
        PerpetualTaskType.ECS_CLUSTER, ACCOUNT_ID, clientContext, perpetualTaskSchedule(), false, TASK_DESCRIPTION);
    assertThat(taskId).isNotNull();
    PerpetualTaskRecord perpetualTaskRecordAfterCreate = perpetualTaskRecordDao.getTask(taskId);
    perpetualTaskRecordAfterCreate.setAssignTryCount(11);
    perpetualTaskRecordDao.save(perpetualTaskRecordAfterCreate);
    return perpetualTaskRecordDao.getTask(taskId);
  }

  private PerpetualTaskResponse perpetualTaskFailedResponse() {
    return PerpetualTaskResponse.builder().responseCode(500).build();
  }

  public PerpetualTaskClientContext clientContext() {
    Map<String, String> clientParamMap = new HashMap<>();
    clientParamMap.put(REGION, REGION);
    clientParamMap.put(SETTING_ID, SETTING_ID);
    clientParamMap.put(CLUSTER_NAME, CLUSTER_NAME);
    return PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();
  }

  public PerpetualTaskClientContext clientContextWithTaskParams() {
    byte[] taskParameters = new byte[] {1, 2, 3, 4};
    return PerpetualTaskClientContext.builder().executionBundle(taskParameters).build();
  }

  public PerpetualTaskSchedule perpetualTaskSchedule() {
    return PerpetualTaskSchedule.newBuilder()
        .setInterval(Durations.fromSeconds(600))
        .setTimeout(Durations.fromMillis(180000))
        .build();
  }

  public PerpetualTaskRecord perpetualTaskRecord() {
    return PerpetualTaskRecord.builder()
        .accountId(ACCOUNT_ID)
        .perpetualTaskType(PerpetualTaskType.K8S_WATCH)
        .clientContext(clientContext())
        .delegateId(DELEGATE_ID)
        .build();
  }
}
