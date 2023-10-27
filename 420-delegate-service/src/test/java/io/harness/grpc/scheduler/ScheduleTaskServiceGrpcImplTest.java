/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.scheduler;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.delegate.beans.RunnerType.RUNNER_TYPE_K8S;
import static io.harness.grpc.scheduler.datagen.InfraRequestTestFactory.createRequest;
import static io.harness.grpc.scheduler.datagen.InfraRequestTestFactory.createStep;
import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.IntegrationTests;
import io.harness.delegate.Execution;
import io.harness.delegate.ExecutionInput;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.ScheduleTaskRequest;
import io.harness.delegate.ScheduleTaskServiceGrpc;
import io.harness.delegate.SchedulingConfig;
import io.harness.delegate.SetupExecutionInfrastructureRequest;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.executionInfra.ExecutionInfraLocation;
import io.harness.executionInfra.ExecutionInfrastructureService;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.rule.Owner;
import io.harness.taskclient.TaskClient;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Response;

@OwnedBy(DEL)
@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class ScheduleTaskServiceGrpcImplTest {
  private static final String LOG_SERVICE_SECRET = "ls secret";
  private static final String ACCOUNT_ID = "accountId";
  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private ScheduleTaskServiceGrpc.ScheduleTaskServiceBlockingStub underTest;
  @Mock private DelegateTaskMigrationHelper migrationHelper;
  @Mock private TaskClient taskClient;
  @Mock private ExecutionInfrastructureService infraService;
  @Mock(answer = RETURNS_DEEP_STUBS) private LogStreamingServiceRestClient logServiceClient;

  @Before
  public void setUp() throws Exception {
    final var underTest = new ScheduleTaskServiceGrpcImpl(
        migrationHelper, taskClient, infraService, logServiceClient, LOG_SERVICE_SECRET);

    final var serverName = InProcessServerBuilder.generateName();
    final Server server =
        InProcessServerBuilder.forName(serverName).directExecutor().addService(underTest).build().start();
    grpcCleanup.register(server);

    final var channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());
    this.underTest = ScheduleTaskServiceGrpc.newBlockingStub(channel);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(IntegrationTests.class)
  public void testInitTask() throws IOException {
    final var taskId = "newTaskId";
    final var step1 = createStep("step1", "alpine:latest");
    final var step2 = createStep("step2", "alpine:latest");
    final var request = createRequest("logKey", step1, step2);

    when(logServiceClient.retrieveAccountToken(LOG_SERVICE_SECRET, ACCOUNT_ID).execute())
        .thenReturn(Response.success("token"));
    when(migrationHelper.generateDelegateTaskUUID()).thenReturn(taskId);

    final var actual = underTest.initTask(request);

    verify(infraService).createExecutionInfra(eq(taskId), any(), eq(RUNNER_TYPE_K8S));
    verify(taskClient).sendTask(any());
    assertThat(actual.getTaskId().getId()).isEqualTo(taskId);
    assertThat(actual.getInfraRefId()).isEqualTo(taskId);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(IntegrationTests.class)
  public void testInitTaskFailNoSchedulingConfig() {
    final var request = SetupExecutionInfrastructureRequest.newBuilder().build();

    assertThatThrownBy(() -> underTest.initTask(request))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("Scheduling config is mandatory")
        .matches(e -> ((StatusRuntimeException) e).getStatus().getCode() == Status.INVALID_ARGUMENT.getCode());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(IntegrationTests.class)
  public void testInitTaskUnsupportedRunner() {
    final var request = SetupExecutionInfrastructureRequest.newBuilder()
                            .setConfig(SchedulingConfig.newBuilder().setRunnerType("UnsupportedRunner").build())
                            .build();

    assertThatThrownBy(() -> underTest.initTask(request))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("Unsupported runner type")
        .matches(e -> ((StatusRuntimeException) e).getStatus().getCode() == Status.INVALID_ARGUMENT.getCode());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(IntegrationTests.class)
  public void testInitTaskFailLoggingToken() throws IOException {
    final var request =
        SetupExecutionInfrastructureRequest.newBuilder()
            .setConfig(SchedulingConfig.newBuilder().setRunnerType(RUNNER_TYPE_K8S).setAccountId(ACCOUNT_ID).build())
            .build();

    when(logServiceClient.retrieveAccountToken(LOG_SERVICE_SECRET, ACCOUNT_ID).execute())
        .thenThrow(new IOException("fail"));

    assertThatThrownBy(() -> underTest.initTask(request))
        .isInstanceOf(StatusRuntimeException.class)
        .matches(e -> ((StatusRuntimeException) e).getStatus().getCode() == Status.INTERNAL.getCode());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(IntegrationTests.class)
  public void testInitTaskFailNoEligibleDelegates() throws IOException {
    final var request =
        SetupExecutionInfrastructureRequest.newBuilder()
            .setConfig(SchedulingConfig.newBuilder().setRunnerType(RUNNER_TYPE_K8S).setAccountId(ACCOUNT_ID).build())
            .build();

    when(logServiceClient.retrieveAccountToken(LOG_SERVICE_SECRET, ACCOUNT_ID).execute())
        .thenReturn(Response.success("token"));
    when(migrationHelper.generateDelegateTaskUUID()).thenReturn("taskId");
    doThrow(new NoEligibleDelegatesInAccountException("fail")).when(taskClient).sendTask(any());

    assertThatThrownBy(() -> underTest.initTask(request))
        .isInstanceOf(StatusRuntimeException.class)
        .matches(e -> ((StatusRuntimeException) e).getStatus().getCode() == Status.FAILED_PRECONDITION.getCode());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(IntegrationTests.class)
  public void testInitTaskFailGenericException() throws IOException {
    final var request =
        SetupExecutionInfrastructureRequest.newBuilder()
            .setConfig(SchedulingConfig.newBuilder().setRunnerType(RUNNER_TYPE_K8S).setAccountId(ACCOUNT_ID).build())
            .build();

    when(logServiceClient.retrieveAccountToken(LOG_SERVICE_SECRET, ACCOUNT_ID).execute())
        .thenReturn(Response.success("token"));
    when(migrationHelper.generateDelegateTaskUUID()).thenReturn("taskId");
    doThrow(new NullPointerException("fail")).when(taskClient).sendTask(any());

    assertThatThrownBy(() -> underTest.initTask(request))
        .isInstanceOf(StatusRuntimeException.class)
        .matches(e -> ((StatusRuntimeException) e).getStatus().getCode() == Status.INTERNAL.getCode());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(IntegrationTests.class)
  public void testExecuteTask() throws IOException {
    final var taskId = "taskId";
    final var infraRefId = "infraRefId";
    final var executionInput = ExecutionInput.newBuilder().setData(ByteString.EMPTY).build();
    final var request =
        ScheduleTaskRequest.newBuilder()
            .setExecution(Execution.newBuilder().setInfraRefId(infraRefId).setInput(executionInput).build())
            .setConfig(SchedulingConfig.newBuilder().setRunnerType(RUNNER_TYPE_K8S).setAccountId(ACCOUNT_ID).build())
            .build();

    when(logServiceClient.retrieveAccountToken(LOG_SERVICE_SECRET, ACCOUNT_ID).execute())
        .thenReturn(Response.success("token"));
    when(migrationHelper.generateDelegateTaskUUID()).thenReturn(taskId);
    when(infraService.getExecutionInfra(infraRefId))
        .thenReturn(ExecutionInfraLocation.builder().delegateGroupName("delegate").build());

    final var actual = underTest.executeTask(request);

    verify(taskClient).sendTask(any());
    assertThat(actual.getTaskId().getId()).isEqualTo(taskId);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(IntegrationTests.class)
  public void testExecuteTaskFailNoExecution() {
    final var request =
        ScheduleTaskRequest.newBuilder()
            .setConfig(SchedulingConfig.newBuilder().setRunnerType(RUNNER_TYPE_K8S).setAccountId(ACCOUNT_ID).build())
            .build();

    assertThatThrownBy(() -> underTest.executeTask(request))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("infra_ref_id is mandatory")
        .matches(e -> ((StatusRuntimeException) e).getStatus().getCode() == Status.INVALID_ARGUMENT.getCode());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(IntegrationTests.class)
  public void testExecuteTaskFailNoEligibleDelegates() throws IOException {
    final var infraRefId = "infraRefId";
    final var request =
        ScheduleTaskRequest.newBuilder()
            .setExecution(Execution.newBuilder().setInfraRefId(infraRefId).build())
            .setConfig(SchedulingConfig.newBuilder().setRunnerType(RUNNER_TYPE_K8S).setAccountId(ACCOUNT_ID).build())
            .build();

    when(logServiceClient.retrieveAccountToken(LOG_SERVICE_SECRET, ACCOUNT_ID).execute())
        .thenReturn(Response.success("token"));
    when(migrationHelper.generateDelegateTaskUUID()).thenReturn("taskId");
    when(infraService.getExecutionInfra(infraRefId))
        .thenReturn(ExecutionInfraLocation.builder().delegateGroupName("delegate").build());
    doThrow(new NoEligibleDelegatesInAccountException("fail")).when(taskClient).sendTask(any());

    assertThatThrownBy(() -> underTest.executeTask(request))
        .isInstanceOf(StatusRuntimeException.class)
        .matches(e -> ((StatusRuntimeException) e).getStatus().getCode() == Status.FAILED_PRECONDITION.getCode());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(IntegrationTests.class)
  public void testExecuteTaskFailGenericException() throws IOException {
    final var infraRefId = "infraRefId";
    final var request =
        ScheduleTaskRequest.newBuilder()
            .setExecution(Execution.newBuilder().setInfraRefId(infraRefId).build())
            .setConfig(SchedulingConfig.newBuilder().setRunnerType(RUNNER_TYPE_K8S).setAccountId(ACCOUNT_ID).build())
            .build();

    when(logServiceClient.retrieveAccountToken(LOG_SERVICE_SECRET, ACCOUNT_ID).execute())
        .thenReturn(Response.success("token"));
    when(migrationHelper.generateDelegateTaskUUID()).thenReturn("taskId");
    when(infraService.getExecutionInfra(infraRefId))
        .thenReturn(ExecutionInfraLocation.builder().delegateGroupName("delegate").build());
    doThrow(new NullPointerException("fail")).when(taskClient).sendTask(any());

    assertThatThrownBy(() -> underTest.executeTask(request))
        .isInstanceOf(StatusRuntimeException.class)
        .matches(e -> ((StatusRuntimeException) e).getStatus().getCode() == Status.INTERNAL.getCode());
  }
}
