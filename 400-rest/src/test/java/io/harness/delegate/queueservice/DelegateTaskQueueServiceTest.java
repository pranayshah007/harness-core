/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.delegate.queueservice;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.JENNY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateCapacity;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.AckResponse;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.persistence.HPersistence;
import io.harness.queueservice.DelegateTaskDequeue;
import io.harness.queueservice.ResourceBasedDelegateSelectionCheckForTask;
import io.harness.queueservice.impl.FilterByDelegateCapacity;
import io.harness.queueservice.impl.OrderByTotalNumberOfTaskAssignedCriteria;
import io.harness.queueservice.infc.DelegateCapacityManagementService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateCache;

import software.wings.WingsBaseTest;
import software.wings.beans.TaskType;
import software.wings.service.impl.DelegateTaskServiceClassicImpl;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DelegateTaskQueueServiceTest extends WingsBaseTest {
  @Inject @InjectMocks private DelegateTaskQueueService delegateTaskQueueService;
  @Inject private ResourceBasedDelegateSelectionCheckForTask resourceBasedDelegateSelectionCheckForTask;
  @Inject @InjectMocks private OrderByTotalNumberOfTaskAssignedCriteria orderByTotalNumberOfTaskAssignedCriteria;
  @Inject @InjectMocks private DelegateTaskServiceClassicImpl delegateTaskServiceClassicImpl;
  @Inject @InjectMocks private FilterByDelegateCapacity filterByDelegateCapacity;
  @Inject private DelegateCapacityManagementService delegateCapacityManagementService;
  @Mock private DelegateCache delegateCache;
  @Mock private HsqsClientService hsqsClientService;
  @Inject private HPersistence persistence;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testIsResourceAvailableToAssignTask() {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId, 2);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .status(DelegateTask.Status.STARTED)
            .stageId(generateUuid())
            .delegateId(delegate.getUuid())
            .taskDataV2(TaskDataV2.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
            .data(TaskData.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
            .eligibleToExecuteDelegateIds(new LinkedList<>(List.of(delegate.getUuid())))
            .build();
    persistence.save(delegateTask);
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    assertThat(delegateTaskQueueService.isResourceAvailableToAssignTask(delegateTask)).isTrue();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAcknowledgeAndProcessDelegateTask() throws IOException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId, 1);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .stageId(generateUuid())
                                    .delegateId(delegate.getUuid())
                                    .data(TaskData.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
                                    .eligibleToExecuteDelegateIds(new LinkedList<>(List.of(delegate.getUuid())))
                                    .build();
    when(hsqsClientService.ack(any())).thenReturn(AckResponse.builder().itemId("itemid").build());
    delegateTaskQueueService.acknowledgeAndProcessDelegateTask(
        DelegateTaskDequeue.builder().delegateTask(delegateTask).itemId("itemid").build());
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
  }
  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAbortQueuedDelegateTask() throws IOException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId, 1);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(generateUuid())
                                    .accountId(accountId)
                                    .stageId(generateUuid())
                                    .delegateId(delegate.getUuid())
                                    .data(TaskData.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
                                    .eligibleToExecuteDelegateIds(new LinkedList<>(List.of(delegate.getUuid())))
                                    .build();
    when(hsqsClientService.enqueue(any())).thenReturn(EnqueueResponse.builder().itemId("itemid").build());
    when(delegateCache.getAbortedTaskList(accountId))
        .thenReturn(new HashSet<String>(Collections.singleton(delegateTask.getUuid())));
    delegateTaskQueueService.enqueue(delegateTask);
    String task = referenceFalseKryoSerializer.asString(delegateTask);
    when(hsqsClientService.dequeue(any()))
        .thenReturn(List.of(DequeueResponse.builder().itemId("itemid").payload(task).build()));
    delegateTaskQueueService.dequeue();
    DelegateTask delegateTaskSaved = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(delegateTaskSaved).isNull();
  }

  private Delegate createDelegate(String accountId, int maxBuild) {
    Delegate delegate = createDelegateBuilder(accountId, maxBuild).build();
    persistence.save(delegate);
    return delegate;
  }
  private DelegateBuilder createDelegateBuilder(String accountId, int maxBuild) {
    return Delegate.builder()
        .accountId(accountId)
        .ip("127.0.0.1")
        .delegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(maxBuild).build())
        .hostName("localhost")
        .delegateName("testDelegateName")
        .tags(ImmutableList.of("aws-delegate", "sel1", "sel2"))
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }
  @Before
  public void setUp() throws IllegalAccessException, ExecutionException {}
}
