/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.SANJA;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.MockableTestMixin;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.beans.DelegateTask.Status;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.category.element.UnitTests;
import io.harness.delegate.AccountId;
import io.harness.delegate.Capability;
import io.harness.delegate.DelegateServiceAgentClient;
import io.harness.delegate.DelegateServiceGrpc;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskExecutionStage;
import io.harness.delegate.TaskId;
import io.harness.delegate.TaskLogAbstractions;
import io.harness.delegate.TaskMode;
import io.harness.delegate.TaskSetupAbstractions;
import io.harness.delegate.TaskType;
import io.harness.delegate.beans.DelegateStringProgressData;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.beans.executioncapability.SystemEnvCheckerCapability;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskClientContextDetails;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.TaskClientParams;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.shell.ScriptType;
import io.harness.tasks.ProgressData;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.util.Timestamps;
import io.grpc.Channel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

@RunWith(MockitoJUnitRunner.class)
public class DelegateServiceGrpcImplTest extends WingsBaseTest implements MockableTestMixin {
  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  public static final String TASK_DESCRIPTION = "taskDescription";

  private DelegateServiceGrpcClient delegateServiceGrpcClient;
  private DelegateServiceAgentClient delegateServiceAgentClient;
  private DelegateServiceGrpcImpl delegateServiceGrpcImpl;

  private DelegateCallbackRegistry delegateCallbackRegistry;
  private PerpetualTaskService perpetualTaskService;
  private DelegateService delegateService;
  private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Inject private DelegateAsyncService delegateAsyncService;
  @Inject private KryoSerializer kryoSerializer;

  @Inject @Named("referenceFalseKryoSerializer") KryoSerializer referenceFalseKryoSerializer;
  private DelegateSyncService delegateSyncService;
  private DelegateTaskService delegateTaskService;
  private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  private Server server;
  private Logger mockClientLogger;
  private Logger mockServerLogger;

  @Before
  public void setUp() throws Exception {
    mockClientLogger = mock(Logger.class);
    mockServerLogger = mock(Logger.class);

    String serverName = InProcessServerBuilder.generateName();
    Channel channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).build());

    DelegateServiceGrpc.DelegateServiceBlockingStub delegateServiceBlockingStub =
        DelegateServiceGrpc.newBlockingStub(channel);
    delegateServiceGrpcClient = new DelegateServiceGrpcClient(delegateServiceBlockingStub, delegateAsyncService,
        kryoSerializer, referenceFalseKryoSerializer, delegateSyncService, () -> false);
    delegateServiceAgentClient = mock(DelegateServiceAgentClient.class);
    delegateSyncService = mock(DelegateSyncService.class);

    delegateCallbackRegistry = mock(DelegateCallbackRegistry.class);
    perpetualTaskService = mock(PerpetualTaskService.class);
    delegateService = mock(DelegateService.class);
    delegateTaskServiceClassic = mock(DelegateTaskServiceClassic.class);
    delegateTaskService = mock(DelegateTaskService.class);
    delegateTaskMigrationHelper = mock(DelegateTaskMigrationHelper.class);
    delegateServiceGrpcImpl = new DelegateServiceGrpcImpl(delegateCallbackRegistry, perpetualTaskService,
        delegateService, delegateTaskService, kryoSerializer, referenceFalseKryoSerializer, delegateTaskServiceClassic,
        delegateTaskMigrationHelper, null, null);

    server =
        InProcessServerBuilder.forName(serverName).directExecutor().addService(delegateServiceGrpcImpl).build().start();
    grpcCleanup.register(server);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testSubmitTask() {
    ByteString kryoParams = ByteString.copyFrom(kryoSerializer.asDeflatedBytes(ScriptType.BASH));
    when(delegateTaskMigrationHelper.generateDelegateTaskUUID()).thenReturn(generateUuid());

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(Cd1SetupFields.APP_ID_FIELD, "appId");
    setupAbstractions.put(Cd1SetupFields.ENV_ID_FIELD, "envId");
    setupAbstractions.put(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, "infrastructureMappingId");
    setupAbstractions.put(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD, "serviceTemplateId");
    setupAbstractions.put(Cd1SetupFields.ARTIFACT_STREAM_ID_FIELD, "artifactStreamId");
    setupAbstractions.put(DelegateTaskKeys.workflowExecutionId, "workflowExecutionId");

    LinkedHashMap<String, String> logAbstractions = new LinkedHashMap<>();
    logAbstractions.put(Cd1SetupFields.APP_ID_FIELD, "appId");
    logAbstractions.put(Cd1SetupFields.ENV_ID_FIELD, "envId");

    Map<String, String> expressions = new HashMap<>();
    expressions.put("expression1", "exp1");
    expressions.put("expression1", "exp1");

    TaskDetails.Builder builder = TaskDetails.newBuilder()
                                      .setType(TaskType.newBuilder().setType("TYPE").build())
                                      .setKryoParameters(kryoParams)
                                      .setExecutionTimeout(Duration.newBuilder().setSeconds(3).setNanos(100).build())
                                      .setExpressionFunctorToken(200);

    List<String> taskSelectors = Arrays.asList("testSelector");

    TaskId taskId1 = delegateServiceGrpcClient
                         .submitTask(DelegateCallbackToken.newBuilder().setToken("token").build(),
                             AccountId.newBuilder().setId(generateUuid()).build(),
                             TaskSetupAbstractions.newBuilder().putAllValues(setupAbstractions).build(),
                             TaskLogAbstractions.newBuilder().putAllValues(logAbstractions).build(),
                             builder.setMode(TaskMode.SYNC).setParked(false).build(),
                             asList(SystemEnvCheckerCapability.builder().build()), taskSelectors,
                             java.time.Duration.ZERO, false, false, Collections.emptyList(), false, null, false)
                         .getTaskId();
    assertThat(taskId1).isNotNull();
    assertThat(taskId1.getId()).isNotBlank();
    verify(delegateService).scheduleSyncTask(any(DelegateTask.class));

    setupAbstractions.put("ng", "true");
    TaskId taskId1Ng = delegateServiceGrpcClient
                           .submitTask(DelegateCallbackToken.newBuilder().setToken("token").build(),
                               AccountId.newBuilder().setId(generateUuid()).build(),
                               TaskSetupAbstractions.newBuilder().putAllValues(setupAbstractions).build(),
                               TaskLogAbstractions.newBuilder().putAllValues(logAbstractions).build(),
                               builder.setMode(TaskMode.SYNC).setParked(false).build(),
                               asList(SystemEnvCheckerCapability.builder().build()), taskSelectors,
                               java.time.Duration.ZERO, false, false, Collections.emptyList(), false, null, false)
                           .getTaskId();
    assertThat(taskId1Ng).isNotNull();
    assertThat(taskId1Ng.getId()).isNotBlank();
    verify(delegateService, times(2)).scheduleSyncTask(any(DelegateTask.class));

    TaskId taskId2 = delegateServiceGrpcClient
                         .submitTask(DelegateCallbackToken.newBuilder().setToken("token").build(),
                             AccountId.newBuilder().setId(generateUuid()).build(),
                             TaskSetupAbstractions.newBuilder().putAllValues(new HashMap<>()).build(),
                             TaskLogAbstractions.newBuilder().putAllValues(new LinkedHashMap<>()).build(),
                             builder.setMode(TaskMode.ASYNC).setParked(false).build(),
                             asList(SystemEnvCheckerCapability.builder().build()), taskSelectors,
                             java.time.Duration.ZERO, false, false, Collections.emptyList(), false, null, false)
                         .getTaskId();
    assertThat(taskId2).isNotNull();
    assertThat(taskId2.getId()).isNotBlank();
    verify(delegateService).queueTask(any(DelegateTask.class));

    TaskId taskId3 =
        delegateServiceGrpcClient
            .submitTask(DelegateCallbackToken.newBuilder().setToken("token").build(),
                AccountId.newBuilder().setId(generateUuid()).build(), TaskSetupAbstractions.newBuilder().build(),
                TaskLogAbstractions.newBuilder().putAllValues(new LinkedHashMap<>()).build(),
                builder.setMode(TaskMode.ASYNC).setParked(true).build(),
                asList(SystemEnvCheckerCapability.builder().build()), taskSelectors, java.time.Duration.ZERO, false,
                false, Collections.emptyList(), false, null, false)
            .getTaskId();
    assertThat(taskId3).isNotNull();
    assertThat(taskId3.getId()).isNotBlank();
    verify(delegateTaskServiceClassic).processDelegateTask(any(DelegateTask.class), eq(Status.PARKED));

    doThrow(InvalidRequestException.class).when(delegateService).scheduleSyncTask(any(DelegateTask.class));
    assertThatThrownBy(
        ()
            -> delegateServiceGrpcClient.submitTask(DelegateCallbackToken.newBuilder().setToken("token").build(),
                AccountId.newBuilder().setId(generateUuid()).build(),
                TaskSetupAbstractions.newBuilder().putAllValues(setupAbstractions).build(),
                TaskLogAbstractions.newBuilder().putAllValues(new LinkedHashMap<>()).build(),
                builder.setMode(TaskMode.SYNC).setParked(false).build(),
                asList(SystemEnvCheckerCapability.builder().build()), taskSelectors, java.time.Duration.ZERO, false,
                false, Collections.emptyList(), false, null, false))
        .isInstanceOf(DelegateServiceDriverException.class)
        .hasMessage("Unexpected error occurred while submitting task.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCancelTask() {
    String accountId = generateUuid();
    String taskId = generateUuid();
    when(delegateTaskServiceClassic.abortTask(accountId, taskId))
        .thenReturn(null)
        .thenReturn(DelegateTask.builder().status(Status.STARTED).build());

    TaskExecutionStage taskExecutionStage = delegateServiceGrpcClient.cancelTask(
        AccountId.newBuilder().setId(accountId).build(), TaskId.newBuilder().setId(taskId).build());
    assertThat(taskExecutionStage).isNotNull();
    assertThat(taskExecutionStage).isEqualTo(TaskExecutionStage.TYPE_UNSPECIFIED);

    taskExecutionStage = delegateServiceGrpcClient.cancelTask(
        AccountId.newBuilder().setId(accountId).build(), TaskId.newBuilder().setId(taskId).build());
    assertThat(taskExecutionStage).isNotNull();
    assertThat(taskExecutionStage).isEqualTo(TaskExecutionStage.EXECUTING);

    doThrow(InvalidRequestException.class).when(delegateTaskServiceClassic).abortTask(accountId, taskId);
    assertThatThrownBy(()
                           -> delegateServiceGrpcClient.cancelTask(AccountId.newBuilder().setId(accountId).build(),
                               TaskId.newBuilder().setId(taskId).build()))
        .isInstanceOf(DelegateServiceDriverException.class)
        .hasMessage("Unexpected error occurred while cancelling task.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCancelTaskV2() {
    String accountId = generateUuid();
    String taskId = generateUuid();
    when(delegateTaskServiceClassic.abortTaskV2(accountId, taskId))
        .thenReturn(null)
        .thenReturn(DelegateTask.builder().status(Status.STARTED).build());

    TaskExecutionStage taskExecutionStage = delegateServiceGrpcClient.cancelTaskV2(
        AccountId.newBuilder().setId(accountId).build(), TaskId.newBuilder().setId(taskId).build());
    assertThat(taskExecutionStage).isNotNull();
    assertThat(taskExecutionStage).isEqualTo(TaskExecutionStage.TYPE_UNSPECIFIED);

    taskExecutionStage = delegateServiceGrpcClient.cancelTaskV2(
        AccountId.newBuilder().setId(accountId).build(), TaskId.newBuilder().setId(taskId).build());
    assertThat(taskExecutionStage).isNotNull();
    assertThat(taskExecutionStage).isEqualTo(TaskExecutionStage.EXECUTING);

    doThrow(InvalidRequestException.class).when(delegateTaskServiceClassic).abortTaskV2(accountId, taskId);
    assertThatThrownBy(()
                           -> delegateServiceGrpcClient.cancelTaskV2(AccountId.newBuilder().setId(accountId).build(),
                               TaskId.newBuilder().setId(taskId).build()))
        .isInstanceOf(DelegateServiceDriverException.class)
        .hasMessage("Unexpected error occurred while cancelling task.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testTaskProgress() {
    String accountId = generateUuid();
    String taskId = generateUuid();
    when(delegateTaskServiceClassic.fetchDelegateTask(accountId, taskId))
        .thenReturn(Optional.ofNullable(null))
        .thenReturn(Optional.ofNullable(DelegateTask.builder().status(Status.ERROR).build()));

    TaskExecutionStage taskExecutionStage = delegateServiceGrpcClient.taskProgress(
        AccountId.newBuilder().setId(accountId).build(), TaskId.newBuilder().setId(taskId).build());
    assertThat(taskExecutionStage).isNotNull();
    assertThat(taskExecutionStage).isEqualTo(TaskExecutionStage.TYPE_UNSPECIFIED);

    taskExecutionStage = delegateServiceGrpcClient.taskProgress(
        AccountId.newBuilder().setId(accountId).build(), TaskId.newBuilder().setId(taskId).build());
    assertThat(taskExecutionStage).isNotNull();
    assertThat(taskExecutionStage).isEqualTo(TaskExecutionStage.FAILED);

    doThrow(InvalidRequestException.class).when(delegateTaskServiceClassic).fetchDelegateTask(accountId, taskId);
    assertThatThrownBy(()
                           -> delegateServiceGrpcClient.taskProgress(AccountId.newBuilder().setId(accountId).build(),
                               TaskId.newBuilder().setId(taskId).build()))
        .isInstanceOf(DelegateServiceDriverException.class)
        .hasMessage("Unexpected error occurred while checking task progress.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testTaskProgressUpdates() {
    String accountId = generateUuid();
    String taskId = generateUuid();
    when(delegateTaskServiceClassic.fetchDelegateTask(accountId, taskId)).thenReturn(Optional.ofNullable(null));
    Consumer<TaskExecutionStage> taskExecutionStageConsumer = mock(Consumer.class);

    assertThatThrownBy(
        ()
            -> delegateServiceGrpcClient.taskProgressUpdate(AccountId.newBuilder().setId(accountId).build(),
                TaskId.newBuilder().setId(taskId).build(), taskExecutionStageConsumer))
        .isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void testSendTaskProgress() throws ExecutionException, InterruptedException {
    String accountId = generateUuid();
    String taskId = generateUuid();
    when(delegateTaskService.fetchDelegateTask(accountId, taskId)).thenReturn(Optional.ofNullable(null));
    doReturn(CompletableFuture.completedFuture(true).get())
        .when(delegateServiceAgentClient)
        .sendTaskProgressUpdate(any(), any(), any(), any());
    DelegateCallback delegateCallback =
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder().setConnection("test").setCollectionNamePrefix("test").build())
            .build();

    when(delegateCallbackRegistry.ensureCallback(delegateCallback)).thenReturn("token");
    DelegateCallbackToken token = delegateServiceGrpcClient.registerCallback(delegateCallback);

    ProgressData testData = DelegateStringProgressData.builder().data("Example").build();
    byte[] testDataBytes = kryoSerializer.asDeflatedBytes(testData);
    assertThat(delegateServiceAgentClient.sendTaskProgressUpdate(AccountId.newBuilder().setId(accountId).build(),
                   TaskId.newBuilder().setId(taskId).build(), token, testDataBytes))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void testRegisterCallback() {
    DelegateCallback delegateCallback =
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder().setConnection("test").setCollectionNamePrefix("test").build())
            .build();
    when(delegateCallbackRegistry.ensureCallback(delegateCallback)).thenReturn("token");

    DelegateCallbackToken token = delegateServiceGrpcClient.registerCallback(delegateCallback);

    assertThat(token).isNotNull();

    doThrow(InvalidRequestException.class).when(delegateCallbackRegistry).ensureCallback(delegateCallback);
    assertThatThrownBy(() -> delegateServiceGrpcClient.registerCallback(delegateCallback))
        .isInstanceOf(DelegateServiceDriverException.class)
        .hasMessage("Unexpected error occurred while registering callback.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreatePerpetualTask() {
    String accountId = generateUuid();
    String type = PerpetualTaskType.SAMPLE;
    long lastContextUpdated = 1000L;
    String taskId = generateUuid();

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Duration.newBuilder().setSeconds(5).build())
                                         .setTimeout(Duration.newBuilder().setSeconds(2).build())
                                         .build();

    PerpetualTaskClientContextDetails contextDetails =
        PerpetualTaskClientContextDetails.newBuilder()
            .setTaskClientParams(TaskClientParams.newBuilder().putAllParams(new HashMap<>()).build())
            .setLastContextUpdated(Timestamps.fromMillis(lastContextUpdated))
            .build();

    PerpetualTaskClientContext context = PerpetualTaskClientContext.builder()
                                             .clientParams(new HashMap<>())
                                             .lastContextUpdated(lastContextUpdated)
                                             .build();

    when(perpetualTaskService.createTask(
             eq(type), eq(accountId), eq(context), eq(schedule), eq(false), eq(TASK_DESCRIPTION)))
        .thenReturn(taskId);

    PerpetualTaskId perpetualTaskId = delegateServiceGrpcClient.createPerpetualTask(
        AccountId.newBuilder().setId(accountId).build(), type, schedule, contextDetails, false, TASK_DESCRIPTION);

    assertThat(perpetualTaskId).isNotNull();
    assertThat(perpetualTaskId.getId()).isEqualTo(taskId);

    doThrow(InvalidRequestException.class)
        .when(perpetualTaskService)
        .createTask(eq(type), eq(accountId), eq(context), eq(schedule), eq(false), eq(TASK_DESCRIPTION));
    assertThatThrownBy(
        ()
            -> delegateServiceGrpcClient.createPerpetualTask(AccountId.newBuilder().setId(accountId).build(), type,
                schedule, contextDetails, false, TASK_DESCRIPTION))
        .isInstanceOf(DelegateServiceDriverException.class)
        .hasMessage("Unexpected error occurred while creating perpetual task.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreatePerpetualTaskWithExecutionBundle() {
    String accountId = generateUuid();
    String type = PerpetualTaskType.SAMPLE;
    String taskId = generateUuid();

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Duration.newBuilder().setSeconds(5).build())
                                         .setTimeout(Duration.newBuilder().setSeconds(2).build())
                                         .build();

    Set<String> selectors = new HashSet<>();
    selectors.add("test");
    SelectorCapability selectorCapability =
        SelectorCapability.builder().selectors(selectors).selectorOrigin("TASK_SELECTOR").build();

    PerpetualTaskClientContextDetails contextDetails =
        PerpetualTaskClientContextDetails.newBuilder()
            .setExecutionBundle(PerpetualTaskExecutionBundle.newBuilder()
                                    .addCapabilities(Capability.newBuilder()
                                                         .setKryoCapability(ByteString.copyFrom(
                                                             kryoSerializer.asDeflatedBytes(selectorCapability)))
                                                         .build())
                                    .putSetupAbstractions("ng", "true")
                                    .build())
            .build();

    when(perpetualTaskService.createTask(eq(type), eq(accountId), any(PerpetualTaskClientContext.class), eq(schedule),
             eq(false), eq(TASK_DESCRIPTION)))
        .thenReturn(taskId);

    PerpetualTaskId perpetualTaskId = delegateServiceGrpcClient.createPerpetualTask(
        AccountId.newBuilder().setId(accountId).build(), type, schedule, contextDetails, false, TASK_DESCRIPTION);

    assertThat(perpetualTaskId).isNotNull();
    assertThat(perpetualTaskId.getId()).isEqualTo(taskId);

    doThrow(InvalidRequestException.class)
        .when(perpetualTaskService)
        .createTask(eq(type), eq(accountId), any(PerpetualTaskClientContext.class), eq(schedule), eq(false),
            eq(TASK_DESCRIPTION));
    assertThatThrownBy(
        ()
            -> delegateServiceGrpcClient.createPerpetualTask(AccountId.newBuilder().setId(accountId).build(), type,
                schedule, contextDetails, false, TASK_DESCRIPTION))
        .isInstanceOf(DelegateServiceDriverException.class)
        .hasMessage("Unexpected error occurred while creating perpetual task.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeletePerpetualTask() {
    String accountId = generateUuid();
    String taskId = generateUuid();
    try {
      delegateServiceGrpcClient.deletePerpetualTask(
          AccountId.newBuilder().setId(accountId).build(), PerpetualTaskId.newBuilder().setId(taskId).build());
    } catch (Exception e) {
      fail("Should not have thrown any exception");
    }

    doThrow(InvalidRequestException.class).when(perpetualTaskService).deleteTask(accountId, taskId);
    assertThatThrownBy(
        ()
            -> delegateServiceGrpcClient.deletePerpetualTask(
                AccountId.newBuilder().setId(accountId).build(), PerpetualTaskId.newBuilder().setId(taskId).build()))
        .isInstanceOf(DelegateServiceDriverException.class)
        .hasMessage("Unexpected error occurred while deleting perpetual task.");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testResetPerpetualTask() {
    String accountId = generateUuid();
    String taskId = generateUuid();
    PerpetualTaskExecutionBundle perpetualTaskExecutionBundle = PerpetualTaskExecutionBundle.getDefaultInstance();
    try {
      delegateServiceGrpcClient.resetPerpetualTask(AccountId.newBuilder().setId(accountId).build(),
          PerpetualTaskId.newBuilder().setId(taskId).build(), perpetualTaskExecutionBundle);
    } catch (Exception e) {
      fail("Should not have thrown any exception");
    }

    doThrow(InvalidRequestException.class)
        .when(perpetualTaskService)
        .resetTask(accountId, taskId, perpetualTaskExecutionBundle);
    assertThatThrownBy(
        ()
            -> delegateServiceGrpcClient.resetPerpetualTask(AccountId.newBuilder().setId(accountId).build(),
                PerpetualTaskId.newBuilder().setId(taskId).build(), perpetualTaskExecutionBundle))
        .isInstanceOf(DelegateServiceDriverException.class)
        .hasMessage("Unexpected error occurred while resetting perpetual task.");
  }
}
