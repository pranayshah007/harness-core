/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.tasks;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.delegate.AccountId;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.SubmitTaskResponse;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskId;
import io.harness.delegate.TaskMode;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.execution.tasks.DelegateTaskRequest;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.protobuf.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.core.classloader.annotations.PowerMockIgnore;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
@PowerMockIgnore({"javax.security.*", "javax.net.*"})
public class NgDelegate2TaskExecutorTest extends CategoryTest {
  @Mock private DelegateServiceGrpc.DelegateServiceBlockingStub delegateServiceBlockingStub;
  @Mock private DelegateSyncService delegateSyncService;
  @Mock private DelegateAsyncService delegateAsyncService;
  @Mock private Supplier<DelegateCallbackToken> tokenSupplier;

  @Inject @InjectMocks private NgDelegate2TaskExecutor ngDelegate2TaskExecutor;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenQueueTask() {
    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(SubmitTaskRequest.newBuilder()
                                    .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                    .setDetails(TaskDetails.newBuilder().setMode(TaskMode.SYNC).build())
                                    .build())
                    .build())
            .build();

    assertThatThrownBy(() -> ngDelegate2TaskExecutor.queueTask(new HashMap<>(), taskRequest, Duration.ZERO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.format("DelegateTaskRequest Mode %s Not Supported", TaskMode.SYNC));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenQueueTaskWithWrongTaskMode() {
    TaskRequest taskRequest = TaskRequest.newBuilder().setSkipTaskRequest(SkipTaskRequest.newBuilder().build()).build();

    assertThatThrownBy(() -> ngDelegate2TaskExecutor.queueTask(new HashMap<>(), taskRequest, Duration.ZERO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Task Request doesnt contain delegate Task Request");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldQueueTask() {
    String taskId = generateUuid();
    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(SubmitTaskRequest.newBuilder()
                                    .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                    .setDetails(TaskDetails.newBuilder().setMode(TaskMode.ASYNC).build())
                                    .build())
                    .build())
            .build();

    when(delegateServiceBlockingStub.submitTask(any()))
        .thenReturn(SubmitTaskResponse.newBuilder()
                        .setTotalExpiry(Timestamp.newBuilder().setSeconds(30).build())
                        .setTaskId(TaskId.newBuilder().setId(taskId).build())
                        .build());
    doNothing().when(delegateAsyncService).setupTimeoutForTask(anyString(), anyLong(), anyLong());
    when(tokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(generateUuid()).build());

    String actualTaskId = ngDelegate2TaskExecutor.queueTask(new HashMap<>(), taskRequest, Duration.ZERO);

    assertThat(actualTaskId).isEqualTo(taskId);

    verify(delegateServiceBlockingStub).submitTask(any());
    verify(delegateAsyncService).setupTimeoutForTask(anyString(), anyLong(), anyLong());
    verify(tokenSupplier).get();

    verifyNoMoreInteractions(delegateSyncService);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenExecuteTask() {
    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(SubmitTaskRequest.newBuilder()
                                    .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                    .setDetails(TaskDetails.newBuilder().setMode(TaskMode.ASYNC).build())
                                    .build())
                    .build())
            .build();

    assertThatThrownBy(() -> ngDelegate2TaskExecutor.executeTask(new HashMap<>(), taskRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(String.format("DelegateTaskRequest Mode %s Not Supported", TaskMode.ASYNC));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenExecuteTaskWithWrongTaskMode() {
    TaskRequest taskRequest = TaskRequest.newBuilder().setSkipTaskRequest(SkipTaskRequest.newBuilder().build()).build();

    assertThatThrownBy(() -> ngDelegate2TaskExecutor.executeTask(new HashMap<>(), taskRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Task Request doesnt contain delegate Task Request");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldExecuteTask() {
    TaskRequest taskRequest = TaskRequest.newBuilder().setSkipTaskRequest(SkipTaskRequest.newBuilder().build()).build();

    assertThatThrownBy(() -> ngDelegate2TaskExecutor.executeTask(new HashMap<>(), taskRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Task Request doesnt contain delegate Task Request");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestExpireTask() {
    String taskId = generateUuid();

    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(SubmitTaskRequest.newBuilder()
                                    .setAccountId(AccountId.newBuilder().setId(generateUuid()).build())
                                    .setDetails(TaskDetails.newBuilder().setMode(TaskMode.SYNC).build())
                                    .build())
                    .build())
            .build();

    when(delegateServiceBlockingStub.submitTask(any()))
        .thenReturn(SubmitTaskResponse.newBuilder()
                        .setTotalExpiry(Timestamp.newBuilder().setSeconds(30).build())
                        .setTaskId(TaskId.newBuilder().setId(taskId).build())
                        .build());
    when(delegateSyncService.waitForTask(anyString(), anyString(), any(), any())).thenReturn(new ResponseData() {});
    when(tokenSupplier.get()).thenReturn(DelegateCallbackToken.newBuilder().setToken(generateUuid()).build());

    ResponseData responseData = ngDelegate2TaskExecutor.executeTask(new HashMap<>(), taskRequest);
    assertThat(responseData).isNotNull();

    verify(delegateServiceBlockingStub).submitTask(any());
    verify(delegateSyncService).waitForTask(anyString(), anyString(), any(), any());
    verify(tokenSupplier).get();
    verifyNoMoreInteractions(delegateAsyncService);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void abortTask() {}
}
