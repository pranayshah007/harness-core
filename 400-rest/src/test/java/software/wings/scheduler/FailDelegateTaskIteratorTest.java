/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.beans.DelegateTask.Status.ABORTED;
import static io.harness.beans.DelegateTask.Status.PARKED;
import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.DelegateTask.Status.STARTED;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.JENNY;

import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.task.TaskFailureReason;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.iterator.FailDelegateTaskIterator;
import io.harness.iterator.FailDelegateTaskIteratorHelper;
import io.harness.iterator.FailDelegateTaskIteratorOnDMS;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator.MongoPersistenceIteratorBuilder;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.TaskType;
import software.wings.service.impl.DelegateTaskServiceClassicImpl;
import software.wings.service.intfc.AssignDelegateService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FailDelegateTaskIteratorTest extends WingsBaseTest {
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @InjectMocks @Inject private FailDelegateTaskIterator failDelegateTaskIterator;

  @InjectMocks @Inject private FailDelegateTaskIteratorOnDMS failDelegateTaskIteratorOnDMS;
  @InjectMocks @Inject private FailDelegateTaskIteratorHelper failDelegateTaskIteratorHelper;
  @Mock private AssignDelegateService assignDelegateService;
  @InjectMocks @Inject private DelegateTaskServiceClassicImpl delegateTaskServiceClassic;
  @Inject private HPersistence persistence;
  @Inject private Clock clock;

  private static final String DELEGATE_TASK_UUID_NEW = "delegateTask-NEW";

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    ArgumentCaptor<MongoPersistenceIteratorBuilder> captor =
        ArgumentCaptor.forClass(MongoPersistenceIteratorBuilder.class);
    failDelegateTaskIterator.createAndStartIterator(PersistenceIteratorFactory.PumpExecutorOptions.builder()
                                                        .name("DelegateTaskFail")
                                                        .poolSize(2)
                                                        .interval(Duration.ofSeconds(30))
                                                        .build(),
        Duration.ofSeconds(30));
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(FailDelegateTaskIterator.class), captor.capture());
    MongoPersistenceIteratorBuilder mongoPersistenceIteratorBuilder = captor.getValue();
    assertThat(mongoPersistenceIteratorBuilder).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRegisterIterators_newDB() {
    ArgumentCaptor<MongoPersistenceIteratorBuilder> captor =
        ArgumentCaptor.forClass(MongoPersistenceIteratorBuilder.class);
    failDelegateTaskIteratorOnDMS.createAndStartIterator(PersistenceIteratorFactory.PumpExecutorOptions.builder()
                                                             .name("DelegateTaskFailOnDMS")
                                                             .poolSize(2)
                                                             .interval(Duration.ofSeconds(30))
                                                             .build(),
        Duration.ofSeconds(30));
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(FailDelegateTaskIteratorOnDMS.class), captor.capture());
    MongoPersistenceIteratorBuilder mongoPersistenceIteratorBuilder = captor.getValue();
    assertThat(mongoPersistenceIteratorBuilder).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithQueuedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(QUEUED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.markLongQueuedTasksAsFailed(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).count()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithTaskDataV2AndQueuedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(QUEUED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .taskDataV2(
                TaskDataV2.builder()
                    .async(true)
                    .taskType(TaskType.HTTP.name())
                    .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                    .timeout(1)
                    .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.markLongQueuedTasksAsFailed(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).count()).isEqualTo(0);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithAbortedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(ABORTED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.markLongQueuedTasksAsFailed(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithTaskDataV2AndAbortedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(ABORTED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .taskDataV2(
                TaskDataV2.builder()
                    .async(true)
                    .taskType(TaskType.HTTP.name())
                    .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                    .timeout(1)
                    .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.markLongQueuedTasksAsFailed(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithParkedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(PARKED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.markLongQueuedTasksAsFailed(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithTaskDataV2AndParkedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(PARKED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .taskDataV2(
                TaskDataV2.builder()
                    .async(true)
                    .taskType(TaskType.HTTP.name())
                    .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                    .timeout(1)
                    .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.markLongQueuedTasksAsFailed(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testMarkTimedOutStartedTasksAsFailed() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(STARTED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.markTimedOutTasksAsFailed(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).count()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMarkTimedOutStartedTasksWithAtaskDataV2AsFailed() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(STARTED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .taskDataV2(
                TaskDataV2.builder()
                    .async(true)
                    .taskType(TaskType.HTTP.name())
                    .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                    .timeout(1)
                    .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.markTimedOutTasksAsFailed(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).count()).isEqualTo(0);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_testEndTasksWithCorruptedRecord() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .expiry(System.currentTimeMillis() - 10)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.endTasks(asList(delegateTask.getUuid()), false, TaskFailureReason.NOT_ASSIGNED);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withAllDelegatesValidationCompleted() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .status(STARTED)
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.failValidationCompletedQueuedTask(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withDelegatesPendingValidation() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .status(QUEUED)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.failValidationCompletedQueuedTask(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withAllDelegatesValidationCompleted_ButFoundConnectedWhitelistedOnes() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(QUEUED)
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    when(assignDelegateService.connectedWhitelistedDelegates(delegateTask)).thenReturn(Arrays.asList("del1"));
    failDelegateTaskIteratorHelper.failValidationCompletedQueuedTask(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withAllDelegateValidationCompleted() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.failValidationCompletedQueuedTask(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testHandleWithAllDelegateValidationCompletedV2() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .taskDataV2(
                TaskDataV2.builder()
                    .async(true)
                    .taskType(TaskType.HTTP.name())
                    .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                    .timeout(1)
                    .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.failValidationCompletedQueuedTask(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withAllDelegateValidationCompleted_ButFoundConnectedWhitelistedOnes() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .validationStartedAt(validationStarted)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    when(assignDelegateService.connectedWhitelistedDelegates(delegateTask)).thenReturn(Arrays.asList("del1"));
    failDelegateTaskIteratorHelper.failValidationCompletedQueuedTask(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withFewDelegatesCompletedValidation() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .status(QUEUED)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .validationStartedAt(validationStarted)
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.failValidationCompletedQueuedTask(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_verifyValidationTimeOut() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2) + TimeUnit.SECONDS.toMillis(5);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.failValidationCompletedQueuedTask(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_verifyNoRaceBetweenAcquireTaskAndFailIterator() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .build();
    persistence.save(delegateTask);
    final Thread iteratorThread =
        new Thread(() -> { failDelegateTaskIteratorHelper.failValidationCompletedQueuedTask(delegateTask, false); });
    final Thread acquireThread = new Thread(() -> {
      delegateTask.setStatus(STARTED);
      persistence.save(delegateTask);
    });
    acquireThread.start();
    iteratorThread.start();
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withAllDelegateValidationCompleted_ButNotTimedOut() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    long taskExpiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
    HttpConnectionExecutionCapability matchingExecutionCapability =
        buildHttpConnectionExecutionCapability("https//aws.amazon.com", null);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .expiry(taskExpiry)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .executionCapabilities(Arrays.asList(matchingExecutionCapability))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.amazon.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.handle(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withAllDelegateValidationCompleted_ButNotTimedOut_newDB() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    long taskExpiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
    HttpConnectionExecutionCapability matchingExecutionCapability =
        buildHttpConnectionExecutionCapability("https//aws.amazon.com", null);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .uuid(DELEGATE_TASK_UUID_NEW)
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .expiry(taskExpiry)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .executionCapabilities(Arrays.asList(matchingExecutionCapability))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.amazon.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .build();
    persistence.save(delegateTask, true);
    failDelegateTaskIteratorOnDMS.handle(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class, true).get()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Ignore("Platform Team will fix later")
  public void testMarkNotAcquiredAfterMultipleBroadcastAsFailed() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long now = clock.millis();
    long nextInterval = now - TimeUnit.MINUTES.toMillis(2);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(QUEUED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .broadcastRound(3)
            .nextBroadcast(nextInterval)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIteratorHelper.markNotAcquiredAfterMultipleBroadcastAsFailed(delegateTask, false);
    assertThat(persistence.createQuery(DelegateTask.class).count()).isEqualTo(0);
  }
}
