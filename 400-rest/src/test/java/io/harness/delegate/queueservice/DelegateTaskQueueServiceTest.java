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
import io.harness.hsqs.client.HsqsClient;
import io.harness.hsqs.client.model.AckResponse;
import io.harness.persistence.HPersistence;
import io.harness.queueservice.DelegateTaskDequeue;
import io.harness.queueservice.ResourceBasedDelegateSelectionCheckForTask;
import io.harness.queueservice.impl.FilterByDelegateCapacity;
import io.harness.queueservice.impl.OrderByTotalNumberOfTaskAssignedCriteria;
import io.harness.queueservice.infc.DelegateCapacityManagementService;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateCache;

import software.wings.WingsBaseTest;
import software.wings.beans.TaskType;
import software.wings.service.impl.DelegateTaskServiceClassicImpl;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import okhttp3.Request;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DelegateTaskQueueServiceTest extends WingsBaseTest {
  @Inject @InjectMocks private DelegateTaskQueueService delegateTaskQueueService;
  @Inject private ResourceBasedDelegateSelectionCheckForTask resourceBasedDelegateSelectionCheckForTask;
  @Inject @InjectMocks private OrderByTotalNumberOfTaskAssignedCriteria orderByTotalNumberOfTaskAssignedCriteria;
  @Inject @InjectMocks private DelegateTaskServiceClassicImpl delegateTaskServiceClassicImpl;
  @Inject @InjectMocks private FilterByDelegateCapacity filterByDelegateCapacity;
  @Inject private DelegateCapacityManagementService delegateCapacityManagementService;
  @Mock private DelegateCache delegateCache;
  @Mock private HsqsClient hsqsServiceClient;
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testIsResourceAvailableToAssignTask() {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId, 2);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .status(DelegateTask.Status.STARTED)
                                    .stageId(generateUuid())
                                    .delegateId(delegate.getUuid())
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
    delegateTaskQueueService.acknowledgeAndProcessDelegateTask(
        DelegateTaskDequeue.builder().delegateTask(delegateTask).build());
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAcknowledgeAndProcessDelegateTaskV2() throws IOException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId, 1);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .stageId(generateUuid())
                                    .delegateId(delegate.getUuid())
                                    .data(TaskData.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
                                    .eligibleToExecuteDelegateIds(new LinkedList<>(List.of(delegate.getUuid())))
                                    .build();
    delegateTaskQueueService.acknowledgeAndProcessDelegateTaskV2(
        DelegateTaskDequeue.builder().delegateTask(delegateTask).build());
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
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
  public void setUp() throws IllegalAccessException, ExecutionException {
    when(hsqsServiceClient.ack(any())).thenReturn(new Call<>() {
      @Override
      public Response<AckResponse> execute() throws IOException {
        return Response.success(AckResponse.builder().itemId("itemId").build());
      }

      @Override
      public void enqueue(Callback<AckResponse> callback) {}

      @Override
      public boolean isExecuted() {
        return false;
      }

      @Override
      public void cancel() {}

      @Override
      public boolean isCanceled() {
        return false;
      }

      @Override
      public Call<AckResponse> clone() {
        return null;
      }

      @Override
      public Request request() {
        return null;
      }
    });
  }
}
