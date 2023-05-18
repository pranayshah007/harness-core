/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.JENNY;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.DELEGATE_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.Status;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.version.VersionInfoManager;

import software.wings.WingsBaseTest;
import software.wings.beans.DelegateTaskBroadcast;
import software.wings.beans.TaskType;
import software.wings.service.impl.DelegateTaskBroadcastHelper;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DelegateQueueTaskTest extends WingsBaseTest {
  @InjectMocks @Inject private DelegateTaskBroadcastHelper broadcastHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Mock private DelegateService delegateService;
  @InjectMocks @Inject DelegateQueueTask delegateQueueTask;
  @Inject private BroadcasterFactory broadcasterFactory;

  @Inject HPersistence persistence;
  @Inject private VersionInfoManager versionInfoManager;
  private static final String DELEGATE_TASK_UUID_NEW = "delegateTask-NEW";

  // TODO: ARPIT remove all tests with name "*_newDB" once delegate task migration is complete
  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastUnassignedTasks_sync() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(false).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(DELEGATE_ID)));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    delegateQueueTask.rebroadcastUnassignedTasks(false);

    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());

    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastUnassignedTasks_sync_newDB() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(DELEGATE_TASK_UUID_NEW)
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(false).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(DELEGATE_ID)));
    persistence.save(delegateTask, true);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);

    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());

    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastUnassignedTasks_async() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(DELEGATE_ID)));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    delegateQueueTask.rebroadcastUnassignedTasks(false);

    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());

    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastUnassignedTasks_async_newDB() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(DELEGATE_TASK_UUID_NEW)
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(DELEGATE_ID)));
    persistence.save(delegateTask, true);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);

    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());

    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastUnassignedTaskWhenNoEligibleDelegates() {
    String accountId = generateUuid();
    long nextBroadcastTime = System.currentTimeMillis() + 120000;

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().async(false).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    persistence.save(delegateTask);

    delegateQueueTask.rebroadcastUnassignedTasks(false);
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(0)).broadcast(argumentCaptor.capture());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastUnassignedTaskWhenNoEligibleDelegates_newDB() {
    String accountId = generateUuid();
    long nextBroadcastTime = System.currentTimeMillis() + 120000;

    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(DELEGATE_TASK_UUID_NEW)
                                    .accountId(accountId)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().async(false).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    persistence.save(delegateTask, true);

    delegateQueueTask.rebroadcastUnassignedTasks(true);
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(0)).broadcast(argumentCaptor.capture());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastUnassignedTasksToNonActiveDelegates() {
    String accountId = generateUuid();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().async(false).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    persistence.save(delegateTask);

    delegateQueueTask.rebroadcastUnassignedTasks(false);
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(0)).broadcast(argumentCaptor.capture());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastUnassignedTasksToNonActiveDelegates_newDB() {
    String accountId = generateUuid();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(DELEGATE_TASK_UUID_NEW)
                                    .accountId(accountId)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().async(false).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    persistence.save(delegateTask, true);

    delegateQueueTask.rebroadcastUnassignedTasks(true);
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(0)).broadcast(argumentCaptor.capture());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcast_updateInterval() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(
        new LinkedList<>(Arrays.asList(DELEGATE_ID, "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9")));
    delegateTask.setAlreadyTriedDelegates(Collections.singleton(DELEGATE_ID));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    delegateQueueTask.rebroadcastUnassignedTasks(false);

    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());

    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    assertThat(task.getAlreadyTriedDelegates().size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcast_updateInterval_newDB() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(DELEGATE_TASK_UUID_NEW)
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(
        new LinkedList<>(Arrays.asList(DELEGATE_ID, "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9")));
    delegateTask.setAlreadyTriedDelegates(Collections.singleton(DELEGATE_ID));
    persistence.save(delegateTask, true);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);

    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());

    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    assertThat(task.getAlreadyTriedDelegates().size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastRound_withOneDelegateEligibleDelegate() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Collections.singletonList(DELEGATE_ID)));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);

    delegateQueueTask.rebroadcastUnassignedTasks(false);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    // verify broadcast count and broadcast round count got updated
    assertThat(task.getBroadcastCount()).isEqualTo(1);
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    // verify broadcast
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());
    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    task.setNextBroadcast(System.currentTimeMillis());
    persistence.save(task);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // after first round of broadcasting
    DelegateTask taskAfterBroadcast1 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast1.getBroadcastCount()).isEqualTo(2);
    assertThat(taskAfterBroadcast1.getBroadcastRound()).isEqualTo(1);

    taskAfterBroadcast1.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast1);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // after second round of broadcasting
    DelegateTask taskAfterBroadcast2 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast2.getBroadcastCount()).isEqualTo(3);
    assertThat(taskAfterBroadcast2.getBroadcastRound()).isEqualTo(2);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast2 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast2).isNotNull();
    assertThat(delegateTaskBroadcast2.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast2.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast2.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast2);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // after third round of broadcasting
    DelegateTask taskAfterBroadcast3 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast3 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast3).isNotNull();
    assertThat(delegateTaskBroadcast3.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast3.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast3.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast3);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast4 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastRound_withOneDelegateEligibleDelegate_newDB() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(DELEGATE_TASK_UUID_NEW)
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Collections.singletonList(DELEGATE_ID)));
    persistence.save(delegateTask, true);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);

    delegateQueueTask.rebroadcastUnassignedTasks(true);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    // verify broadcast count and broadcast round count got updated
    assertThat(task.getBroadcastCount()).isEqualTo(1);
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    // verify broadcast
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());
    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    task.setNextBroadcast(System.currentTimeMillis());
    persistence.save(task, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // after first round of broadcasting
    DelegateTask taskAfterBroadcast1 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast1.getBroadcastCount()).isEqualTo(2);
    assertThat(taskAfterBroadcast1.getBroadcastRound()).isEqualTo(1);

    taskAfterBroadcast1.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast1, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // after second round of broadcasting
    DelegateTask taskAfterBroadcast2 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast2.getBroadcastCount()).isEqualTo(3);
    assertThat(taskAfterBroadcast2.getBroadcastRound()).isEqualTo(2);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast2 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast2).isNotNull();
    assertThat(delegateTaskBroadcast2.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast2.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast2.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast2, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // after third round of broadcasting
    DelegateTask taskAfterBroadcast3 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast3 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast3).isNotNull();
    assertThat(delegateTaskBroadcast3.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast3.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast3.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast3, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast4 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastRound_withLessThanTenDelegateEligibleDelegate() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(
        new LinkedList<>(Arrays.asList("del1", "del2", "del3", "del4", "del5", "del6")));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);

    delegateQueueTask.rebroadcastUnassignedTasks(false);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    // verify broadcast count and broadcast round count got updated
    assertThat(task.getBroadcastCount()).isEqualTo(1);
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    // verify broadcast
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());
    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    task.setNextBroadcast(System.currentTimeMillis());
    persistence.save(task);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // after first round of broadcasting
    DelegateTask taskAfterBroadcast1 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast1.getBroadcastCount()).isEqualTo(2);
    assertThat(taskAfterBroadcast1.getBroadcastRound()).isEqualTo(1);

    taskAfterBroadcast1.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast1);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // after second round of broadcasting
    DelegateTask taskAfterBroadcast2 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast2.getBroadcastCount()).isEqualTo(3);
    assertThat(taskAfterBroadcast2.getBroadcastRound()).isEqualTo(2);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast2 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast2).isNotNull();
    assertThat(delegateTaskBroadcast2.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast2.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast2.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast2);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // after third round of broadcasting
    DelegateTask taskAfterBroadcast3 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast3 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast3).isNotNull();
    assertThat(delegateTaskBroadcast3.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast3.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast3.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast3);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast4 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastRound_withLessThanTenDelegateEligibleDelegate_newDB() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(DELEGATE_TASK_UUID_NEW)
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(
        new LinkedList<>(Arrays.asList("del1", "del2", "del3", "del4", "del5", "del6")));
    persistence.save(delegateTask, true);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);

    delegateQueueTask.rebroadcastUnassignedTasks(true);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    // verify broadcast count and broadcast round count got updated
    assertThat(task.getBroadcastCount()).isEqualTo(1);
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    // verify broadcast
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());
    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    task.setNextBroadcast(System.currentTimeMillis());
    persistence.save(task, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // after first round of broadcasting
    DelegateTask taskAfterBroadcast1 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast1.getBroadcastCount()).isEqualTo(2);
    assertThat(taskAfterBroadcast1.getBroadcastRound()).isEqualTo(1);

    taskAfterBroadcast1.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast1, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // after second round of broadcasting
    DelegateTask taskAfterBroadcast2 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast2.getBroadcastCount()).isEqualTo(3);
    assertThat(taskAfterBroadcast2.getBroadcastRound()).isEqualTo(2);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast2 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast2).isNotNull();
    assertThat(delegateTaskBroadcast2.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast2.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast2.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast2, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // after third round of broadcasting
    DelegateTask taskAfterBroadcast3 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast3 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast3).isNotNull();
    assertThat(delegateTaskBroadcast3.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast3.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast3.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast3, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast4 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastRound_withMoreThanTenDelegateEligibleDelegate() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(
        "del1", "del2", "del3", "del4", "del5", "del6", "del7", "del8", "del9", "del10", "del11", "del12")));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);

    delegateQueueTask.rebroadcastUnassignedTasks(false);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    // verify broadcast count and broadcast round count got updated
    assertThat(task.getBroadcastCount()).isEqualTo(1);
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    // verify broadcast
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());
    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    task.setNextBroadcast(System.currentTimeMillis());
    persistence.save(task);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // after first round of broadcasting
    DelegateTask taskAfterBroadcast1 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast1.getBroadcastCount()).isEqualTo(2);
    assertThat(taskAfterBroadcast1.getBroadcastRound()).isEqualTo(0);

    taskAfterBroadcast1.setNextBroadcast(System.currentTimeMillis());
    taskAfterBroadcast1.setExpiry(System.currentTimeMillis() + 60000);
    persistence.save(taskAfterBroadcast1);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // after second round of broadcasting
    DelegateTask taskAfterBroadcast2 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast2.getBroadcastCount()).isEqualTo(3);
    assertThat(taskAfterBroadcast2.getBroadcastRound()).isEqualTo(1);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast2 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast2).isNotNull();
    assertThat(delegateTaskBroadcast2.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast2.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast2.setNextBroadcast(System.currentTimeMillis());
    taskAfterBroadcast2.setExpiry(System.currentTimeMillis() + 60000);
    persistence.save(taskAfterBroadcast2);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // after third round of broadcasting
    DelegateTask taskAfterBroadcast3 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(1);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast3 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast3).isNotNull();
    assertThat(delegateTaskBroadcast3.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast3.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast3.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast3);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast4 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast4.getBroadcastCount()).isEqualTo(5);
    assertThat(taskAfterBroadcast4.getBroadcastRound()).isEqualTo(2);

    taskAfterBroadcast4.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast4);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast5 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast5.getBroadcastCount()).isEqualTo(6);
    assertThat(taskAfterBroadcast5.getBroadcastRound()).isEqualTo(2);

    taskAfterBroadcast5.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast5);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast6 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast6.getBroadcastCount()).isEqualTo(7);
    assertThat(taskAfterBroadcast6.getBroadcastRound()).isEqualTo(3);

    taskAfterBroadcast6.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast6);
    delegateQueueTask.rebroadcastUnassignedTasks(false);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast7 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast7.getBroadcastCount()).isEqualTo(7);
    assertThat(taskAfterBroadcast7.getBroadcastRound()).isEqualTo(3);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRebroadcastRound_withMoreThanTenDelegateEligibleDelegate_newDB() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(DELEGATE_TASK_UUID_NEW)
                                    .accountId(ACCOUNT_ID)
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(
        "del1", "del2", "del3", "del4", "del5", "del6", "del7", "del8", "del9", "del10", "del11", "del12")));
    persistence.save(delegateTask, true);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);

    delegateQueueTask.rebroadcastUnassignedTasks(true);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    // verify broadcast count and broadcast round count got updated
    assertThat(task.getBroadcastCount()).isEqualTo(1);
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    // verify broadcast
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());
    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    task.setNextBroadcast(System.currentTimeMillis());
    persistence.save(task, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // after first round of broadcasting
    DelegateTask taskAfterBroadcast1 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast1.getBroadcastCount()).isEqualTo(2);
    assertThat(taskAfterBroadcast1.getBroadcastRound()).isEqualTo(0);

    taskAfterBroadcast1.setNextBroadcast(System.currentTimeMillis());
    taskAfterBroadcast1.setExpiry(System.currentTimeMillis() + 60000);
    persistence.save(taskAfterBroadcast1, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // after second round of broadcasting
    DelegateTask taskAfterBroadcast2 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast2.getBroadcastCount()).isEqualTo(3);
    assertThat(taskAfterBroadcast2.getBroadcastRound()).isEqualTo(1);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast2 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast2).isNotNull();
    assertThat(delegateTaskBroadcast2.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast2.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast2.setNextBroadcast(System.currentTimeMillis());
    taskAfterBroadcast2.setExpiry(System.currentTimeMillis() + 60000);
    persistence.save(taskAfterBroadcast2, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // after third round of broadcasting
    DelegateTask taskAfterBroadcast3 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(1);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast3 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast3).isNotNull();
    assertThat(delegateTaskBroadcast3.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast3.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast3.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast3, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast4 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast4.getBroadcastCount()).isEqualTo(5);
    assertThat(taskAfterBroadcast4.getBroadcastRound()).isEqualTo(2);

    taskAfterBroadcast4.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast4, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast5 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast5.getBroadcastCount()).isEqualTo(6);
    assertThat(taskAfterBroadcast5.getBroadcastRound()).isEqualTo(2);

    taskAfterBroadcast5.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast5, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast6 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast6.getBroadcastCount()).isEqualTo(7);
    assertThat(taskAfterBroadcast6.getBroadcastRound()).isEqualTo(3);

    taskAfterBroadcast6.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast6, true);
    delegateQueueTask.rebroadcastUnassignedTasks(true);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast7 = persistence.get(DelegateTask.class, delegateTask.getUuid(), true);
    assertThat(taskAfterBroadcast7.getBroadcastCount()).isEqualTo(7);
    assertThat(taskAfterBroadcast7.getBroadcastRound()).isEqualTo(3);
  }
}
