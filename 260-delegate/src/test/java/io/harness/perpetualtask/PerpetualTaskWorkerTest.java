/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.VUK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.DelegateTestBase;
import io.harness.category.element.UnitTests;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.perpetualtask.k8s.watch.K8sWatchTaskParams;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.threading.Concurrent;

import software.wings.beans.KubernetesClusterConfig;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Slf4j
public class PerpetualTaskWorkerTest extends DelegateTestBase {
  String accountId = "ACCOUNT_ID";
  String cloudProviderId = "CLOUD_PROVIDER_ID";

  String taskIdString1 = "TASK_ID_1";
  PerpetualTaskId taskId1 = PerpetualTaskId.newBuilder().setId(taskIdString1).build();
  Timestamp lastContextUpdate = Timestamp.newBuilder().setSeconds(1111).build();
  PerpetualTaskAssignDetails task1 =
      PerpetualTaskAssignDetails.newBuilder().setTaskId(taskId1).setLastContextUpdated(lastContextUpdate).build();

  String taskIdString2 = "TASK_ID_2";
  PerpetualTaskId taskId2 = PerpetualTaskId.newBuilder().setId(taskIdString2).build();
  PerpetualTaskAssignDetails task2 = PerpetualTaskAssignDetails.newBuilder().setTaskId(taskId2).build();

  KubernetesClusterConfig kubernetesClusterConfig;
  PerpetualTaskExecutionParams params;
  PerpetualTaskExecutionContext context;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private PerpetualTaskServiceAgentClient perpetualTaskServiceAgentClient;
  @Mock private Map<String, PerpetualTaskExecutor> factoryMap;
  @Mock @Named("taskExecutor") ThreadPoolExecutor perpetualTaskExecutor;
  @Spy
  @Named("perpetualTaskTimeoutExecutor")
  ScheduledExecutorService perpetualTaskTimeoutExecutor =
      new ManagedScheduledExecutorService("perpetualTaskTimeoutExecutor");
  @InjectMocks private PerpetualTaskWorker worker;

  @Inject KryoSerializer kryoSerializer;

  @Before
  public void setUp() throws ExecutionException, InterruptedException {
    kubernetesClusterConfig = KubernetesClusterConfig.builder().accountId(accountId).build();
    ByteString bytes = ByteString.copyFrom(kryoSerializer.asBytes(kubernetesClusterConfig));
    K8sWatchTaskParams k8sWatchTaskParams =
        K8sWatchTaskParams.newBuilder().setCloudProviderId(cloudProviderId).setK8SClusterConfig(bytes).build();

    params = PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(k8sWatchTaskParams)).build();

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder().setInterval(Durations.fromSeconds(1)).build();
    context = PerpetualTaskExecutionContext.newBuilder().setTaskParams(params).setTaskSchedule(schedule).build();

    doReturn(
        CompletableFuture.completedFuture(Collections.singletonList(PerpetualTaskAssignDetails.newBuilder().build()))
            .get())
        .when(perpetualTaskServiceAgentClient)
        .perpetualTaskList(anyString(), anyObject());

    doReturn(CompletableFuture
                 .completedFuture(PerpetualTaskContextResponse.newBuilder().setPerpetualTaskContext(context).build())
                 .get()
                 .getPerpetualTaskContext())
        .when(perpetualTaskServiceAgentClient)
        .perpetualTaskContext(isA(PerpetualTaskId.class), anyObject());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testStartTask() {
    Concurrent.test(10, n -> {
      worker.startTask(task1);
      worker.startTask(task2);
    });
    assertThat(worker.getRunningTaskMap()).hasSize(2);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testStopTasks() {
    worker.startTask(task1);
    worker.startTask(task2);
    Concurrent.test(10, n -> {
      worker.stopTask(task1.getTaskId());
      worker.stopTask(task2.getTaskId());
    });
    assertThat(worker.getRunningTaskMap()).isEmpty();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testFetchAssignedTask() {
    worker.fetchAssignedTask();
    verify(perpetualTaskServiceAgentClient).perpetualTaskList(anyString(), anyObject());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSplitTasks() {
    Map<PerpetualTaskId, PerpetualTaskAssignRecord> runningTaskMap = new ConcurrentHashMap<>();
    List<PerpetualTaskAssignDetails> assignedTasks = new ArrayList<>();
    Set<PerpetualTaskId> stopTasks = new HashSet<>();
    List<PerpetualTaskAssignDetails> startTasks = new ArrayList<>();
    List<PerpetualTaskAssignDetails> updatedTasks = new ArrayList<>();

    worker.splitTasks(runningTaskMap, assignedTasks, stopTasks, startTasks, updatedTasks);
    assertThat(stopTasks).isEmpty();
    assertThat(startTasks).isEmpty();
    assertThat(updatedTasks).isEmpty();

    runningTaskMap.put(
        task1.getTaskId(), PerpetualTaskAssignRecord.builder().perpetualTaskAssignDetails(task1).build());
    worker.splitTasks(runningTaskMap, assignedTasks, stopTasks, startTasks, updatedTasks);
    assertThat(stopTasks).containsExactly(task1.getTaskId());
    assertThat(startTasks).isEmpty();
    assertThat(updatedTasks).isEmpty();
    stopTasks.clear();

    assignedTasks.add(PerpetualTaskAssignDetails.newBuilder().setTaskId(task1.getTaskId()).build());
    worker.splitTasks(runningTaskMap, assignedTasks, stopTasks, startTasks, updatedTasks);
    assertThat(stopTasks).isEmpty();
    assertThat(startTasks).isEmpty();
    assertThat(updatedTasks).isEmpty();
    assignedTasks.clear();

    Timestamp newLastContextUpdate = Timestamp.newBuilder().setSeconds(2222).build();

    PerpetualTaskAssignDetails updatedTask = PerpetualTaskAssignDetails.newBuilder()
                                                 .setTaskId(task1.getTaskId())
                                                 .setLastContextUpdated(newLastContextUpdate)
                                                 .build();
    assignedTasks.add(updatedTask);
    worker.splitTasks(runningTaskMap, assignedTasks, stopTasks, startTasks, updatedTasks);
    assertThat(stopTasks).isEmpty();
    assertThat(startTasks).isEmpty();
    assertThat(updatedTasks).containsExactly(updatedTask);

    stopTasks.clear();
    runningTaskMap.clear();

    worker.splitTasks(runningTaskMap, assignedTasks, stopTasks, startTasks, updatedTasks);
    assertThat(stopTasks).isEmpty();
    assertThat(startTasks).containsExactly(updatedTask);
    assertThat(updatedTasks).isNotEmpty();
  }
}
