/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.delegateservice;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.DelegateServiceGrpc.DelegateServiceBlockingStub;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.SANJA;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.category.element.FunctionalTests;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.AccountId;
import io.harness.delegate.DelegateServiceAgentClient;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskId;
import io.harness.delegate.TaskLogAbstractions;
import io.harness.delegate.TaskMode;
import io.harness.delegate.TaskSetupAbstractions;
import io.harness.delegate.TaskType;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateStringProgressData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.service.intfc.DelegateProgressService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.tasks.ProgressData;
import io.harness.tasks.ResponseData;
import io.harness.threading.Poller;
import io.harness.waiter.NotifyResponse;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.ProgressCallback;
import io.harness.waiter.ProgressUpdateService;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.app.MainConfiguration;
import software.wings.beans.HttpStateExecutionResponse;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateServiceTaskApiFunctionalTest extends AbstractFunctionalTest {
  private static final String NON_EXISTING_SELECTOR = "nonExistingSelector";

  @Inject private DelegateServiceBlockingStub delegateServiceBlockingStub;

  @Inject @Named("referenceFalseKryoSerializer") KryoSerializer referenceFalseKryoSerializer;
  @Inject private DelegateSyncService delegateSyncService;
  @Inject private DelegateAsyncService delegateAsyncService;
  @Inject private DelegateProgressService delegateProgressService;
  @Inject private MainConfiguration configuration;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private ProgressUpdateService progressUpdateService;

  private static AtomicInteger progressCallCount = new AtomicInteger(0);
  private static List<ProgressData> progressDataList = new ArrayList<>();

  @Test
  @Owner(developers = MARKO)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testSyncTaskExecution() {
    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    scheduledExecutorService.scheduleWithFixedDelay(delegateSyncService, 0L, 2L, TimeUnit.SECONDS);

    DelegateServiceGrpcClient delegateServiceGrpcClient = new DelegateServiceGrpcClient(delegateServiceBlockingStub,
        delegateAsyncService, referenceFalseKryoSerializer, delegateSyncService, () -> false);

    DelegateCallbackToken callbackToken = delegateServiceGrpcClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("!!!custom")
                                  .setConnection(configuration.getMongoConnectionFactory().getUri())
                                  .build())
            .build());

    assertThat(callbackToken).isNotNull();
    assertThat(callbackToken.getToken()).isNotBlank();

    HttpTaskParameters httpTaskParameters = HttpTaskParameters.builder()
                                                .header("")
                                                .method("GET")
                                                .body("")
                                                .url("https://app.harness.io/api/version")
                                                .socketTimeoutMillis(9000)
                                                .useProxy(false)
                                                .build();

    TaskId taskId =
        delegateServiceGrpcClient
            .submitTaskV2(callbackToken, AccountId.newBuilder().setId(getAccount().getUuid()).build(),
                TaskSetupAbstractions.newBuilder().build(), TaskLogAbstractions.newBuilder().build(),
                TaskDetails.newBuilder()
                    .setMode(TaskMode.SYNC)
                    .setType(TaskType.newBuilder().setType("HTTP").build())
                    .setKryoParameters(
                        ByteString.copyFrom(referenceFalseKryoSerializer.asDeflatedBytes(httpTaskParameters)))
                    .setExecutionTimeout(com.google.protobuf.Duration.newBuilder().setSeconds(600).setNanos(0).build())
                    .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                    .build(),
                httpTaskParameters.fetchRequiredExecutionCapabilities(null), null, Duration.ZERO, false, false,
                Collections.emptyList(), false, null, false, List.of())
            .getTaskId();

    DelegateResponseData responseData =
        delegateSyncService.waitForTask(taskId.getId(), "Http Execution", Duration.ofSeconds(60), null);

    assertThat(responseData).isNotNull();
    HttpStateExecutionResponse executionData = (HttpStateExecutionResponse) responseData;
    assertThat(executionData).isNotNull();
    assertThat(executionData.getHttpResponseCode()).isEqualTo(200);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testSyncTaskExecutionWithErrorResponse() {
    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    scheduledExecutorService.scheduleWithFixedDelay(delegateSyncService, 0L, 2L, TimeUnit.SECONDS);

    DelegateServiceGrpcClient delegateServiceGrpcClient = new DelegateServiceGrpcClient(delegateServiceBlockingStub,
        delegateAsyncService, referenceFalseKryoSerializer, delegateSyncService, () -> false);

    DelegateCallbackToken callbackToken = delegateServiceGrpcClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("!!!custom")
                                  .setConnection(configuration.getMongoConnectionFactory().getUri())
                                  .build())
            .build());

    assertThat(callbackToken).isNotNull();
    assertThat(callbackToken.getToken()).isNotBlank();

    HttpTaskParameters httpTaskParameters = HttpTaskParameters.builder()
                                                .header("")
                                                .method("GET")
                                                .body("")
                                                .url("https://non-existing.com")
                                                .socketTimeoutMillis(9000)
                                                .useProxy(false)
                                                .build();

    TaskId taskId =
        delegateServiceGrpcClient
            .submitTaskV2(callbackToken, AccountId.newBuilder().setId(getAccount().getUuid()).build(),
                TaskSetupAbstractions.newBuilder().build(), TaskLogAbstractions.newBuilder().build(),
                TaskDetails.newBuilder()
                    .setMode(TaskMode.SYNC)
                    .setType(TaskType.newBuilder().setType("HTTP").build())
                    .setKryoParameters(
                        ByteString.copyFrom(referenceFalseKryoSerializer.asDeflatedBytes(httpTaskParameters)))
                    .setExecutionTimeout(com.google.protobuf.Duration.newBuilder().setSeconds(600).setNanos(0).build())
                    .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                    .build(),
                httpTaskParameters.fetchRequiredExecutionCapabilities(null), null, Duration.ZERO, false, false,
                Collections.emptyList(), false, null, false, List.of())
            .getTaskId();

    DelegateResponseData responseData =
        delegateSyncService.waitForTask(taskId.getId(), "Http Execution", Duration.ofSeconds(60), null);

    assertThat(responseData).isNotNull();
    RemoteMethodReturnValueData returnValueData = (RemoteMethodReturnValueData) responseData;
    assertThat(returnValueData).isNotNull();
    assertThat(returnValueData.getException()).isNotNull();
    assertThat(returnValueData.getException()).isInstanceOf(InvalidRequestException.class);
    assertThat(returnValueData.getException().getMessage()).isNotBlank();
    assertThat(returnValueData.getException().getMessage()).contains("https://non-existing.com");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testSyncTaskExecutionWithExceptionThrown() {
    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    scheduledExecutorService.scheduleWithFixedDelay(delegateSyncService, 0L, 2L, TimeUnit.SECONDS);

    DelegateServiceGrpcClient delegateServiceGrpcClient = new DelegateServiceGrpcClient(delegateServiceBlockingStub,
        delegateAsyncService, referenceFalseKryoSerializer, delegateSyncService, () -> false);

    DelegateCallbackToken callbackToken = delegateServiceGrpcClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("!!!custom")
                                  .setConnection(configuration.getMongoConnectionFactory().getUri())
                                  .build())
            .build());

    assertThat(callbackToken).isNotNull();
    assertThat(callbackToken.getToken()).isNotBlank();

    HttpTaskParameters httpTaskParameters = HttpTaskParameters.builder()
                                                .header("")
                                                .method("GET")
                                                .body("")
                                                .url("https://app.harness.io/api/version")
                                                .socketTimeoutMillis(9000)
                                                .useProxy(false)
                                                .build();

    assertThatThrownBy(
        ()
            -> delegateServiceGrpcClient.submitTaskV2(callbackToken,
                AccountId.newBuilder().setId(getAccount().getUuid()).build(),
                TaskSetupAbstractions.newBuilder().build(), TaskLogAbstractions.newBuilder().build(),
                TaskDetails.newBuilder()
                    .setMode(TaskMode.SYNC)
                    .setType(TaskType.newBuilder().setType("HTTP").build())
                    .setKryoParameters(
                        ByteString.copyFrom(referenceFalseKryoSerializer.asDeflatedBytes(httpTaskParameters)))
                    .setExecutionTimeout(com.google.protobuf.Duration.newBuilder().setSeconds(600).setNanos(0).build())
                    .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                    .build(),
                httpTaskParameters.fetchRequiredExecutionCapabilities(null), Arrays.asList(NON_EXISTING_SELECTOR),
                Duration.ZERO, false, false, Collections.emptyList(), false, null, false, List.of()))
        .isInstanceOf(DelegateServiceDriverException.class)
        .hasMessage("Unexpected error occurred while submitting task.")
        .hasRootCauseMessage("INTERNAL: Delegates are not available");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testAsyncTaskExecution() {
    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    scheduledExecutorService.scheduleWithFixedDelay(delegateAsyncService, 0L, 2L, TimeUnit.SECONDS);

    DelegateServiceGrpcClient delegateServiceGrpcClient = new DelegateServiceGrpcClient(delegateServiceBlockingStub,
        delegateAsyncService, referenceFalseKryoSerializer, delegateSyncService, () -> false);

    DelegateCallbackToken callbackToken = delegateServiceGrpcClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("!!!custom")
                                  .setConnection(configuration.getMongoConnectionFactory().getUri())
                                  .build())
            .build());

    assertThat(callbackToken).isNotNull();
    assertThat(callbackToken.getToken()).isNotBlank();

    HttpTaskParameters httpTaskParameters = HttpTaskParameters.builder()
                                                .header("")
                                                .method("GET")
                                                .body("")
                                                .url("https://app.harness.io/api/version")
                                                .socketTimeoutMillis(9000)
                                                .useProxy(false)
                                                .build();

    TaskId taskId =
        delegateServiceGrpcClient
            .submitTaskV2(callbackToken, AccountId.newBuilder().setId(getAccount().getUuid()).build(),
                TaskSetupAbstractions.newBuilder().build(), TaskLogAbstractions.newBuilder().build(),
                TaskDetails.newBuilder()
                    .setMode(TaskMode.ASYNC)
                    .setType(TaskType.newBuilder().setType("HTTP").build())
                    .setKryoParameters(
                        ByteString.copyFrom(referenceFalseKryoSerializer.asDeflatedBytes(httpTaskParameters)))
                    .setExecutionTimeout(com.google.protobuf.Duration.newBuilder().setSeconds(600).setNanos(0).build())
                    .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                    .build(),
                httpTaskParameters.fetchRequiredExecutionCapabilities(null), null, Duration.ZERO, false, false,
                Collections.emptyList(), false, null, false, List.of())
            .getTaskId();

    Poller.pollFor(Duration.ofMinutes(5), Duration.ofSeconds(5), () -> {
      NotifyResponse notifyResponse = wingsPersistence.get(NotifyResponse.class, taskId.getId());
      return notifyResponse != null;
    });

    NotifyResponse notifyResponse = wingsPersistence.get(NotifyResponse.class, taskId.getId());
    assertThat(notifyResponse).isNotNull();
    HttpStateExecutionResponse executionData =
        (HttpStateExecutionResponse) referenceFalseKryoSerializer.asInflatedObject(notifyResponse.getResponseData());
    assertThat(executionData).isNotNull();
    assertThat(executionData.getHttpResponseCode()).isEqualTo(200);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testAsyncTaskExecutionWithErrorResponse() {
    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    scheduledExecutorService.scheduleWithFixedDelay(delegateAsyncService, 0L, 2L, TimeUnit.SECONDS);

    DelegateServiceGrpcClient delegateServiceGrpcClient = new DelegateServiceGrpcClient(delegateServiceBlockingStub,
        delegateAsyncService, referenceFalseKryoSerializer, delegateSyncService, () -> false);

    DelegateCallbackToken callbackToken = delegateServiceGrpcClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("!!!custom")
                                  .setConnection(configuration.getMongoConnectionFactory().getUri())
                                  .build())
            .build());

    assertThat(callbackToken).isNotNull();
    assertThat(callbackToken.getToken()).isNotBlank();

    HttpTaskParameters httpTaskParameters = HttpTaskParameters.builder()
                                                .header("")
                                                .method("GET")
                                                .body("")
                                                .url("https://app.harness.io/api/version")
                                                .socketTimeoutMillis(9000)
                                                .useProxy(false)
                                                .build();

    TaskId taskId =
        delegateServiceGrpcClient
            .submitTaskV2(callbackToken, AccountId.newBuilder().setId(getAccount().getUuid()).build(),
                TaskSetupAbstractions.newBuilder().build(), TaskLogAbstractions.newBuilder().build(),
                TaskDetails.newBuilder()
                    .setMode(TaskMode.ASYNC)
                    .setType(TaskType.newBuilder().setType("HTTP").build())
                    .setKryoParameters(
                        ByteString.copyFrom(referenceFalseKryoSerializer.asDeflatedBytes(httpTaskParameters)))
                    .setExecutionTimeout(com.google.protobuf.Duration.newBuilder().setSeconds(30).setNanos(0).build())
                    .setExpressionFunctorToken(HashGenerator.generateIntegerHash())
                    .build(),
                emptyList(), Arrays.asList(NON_EXISTING_SELECTOR), Duration.ZERO, false, false, Collections.emptyList(),
                false, null, false, List.of())
            .getTaskId();

    Poller.pollFor(Duration.ofMinutes(5), Duration.ofSeconds(5), () -> {
      NotifyResponse notifyResponse = wingsPersistence.get(NotifyResponse.class, taskId.getId());
      return notifyResponse != null;
    });

    NotifyResponse notifyResponse = wingsPersistence.get(NotifyResponse.class, taskId.getId());
    assertThat(notifyResponse).isNotNull();

    ErrorNotifyResponseData executionData =
        (ErrorNotifyResponseData) referenceFalseKryoSerializer.asInflatedObject(notifyResponse.getResponseData());
    assertThat(executionData).isNotNull();
    assertThat(executionData.getErrorMessage()).isNotNull();
    assertThat(executionData.getErrorMessage().contains(NON_EXISTING_SELECTOR)).isTrue();
  }

  @Test
  @Owner(developers = SANJA)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void testTaskProgressUpdate() {
    progressCallCount.set(0);
    progressDataList.clear();

    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    scheduledExecutorService.scheduleWithFixedDelay(delegateProgressService, 0L, 2L, TimeUnit.SECONDS);
    scheduledExecutorService.scheduleWithFixedDelay(progressUpdateService, 0L, 2L, TimeUnit.SECONDS);

    DelegateServiceGrpcClient delegateServiceGrpcClient = new DelegateServiceGrpcClient(delegateServiceBlockingStub,
        delegateAsyncService, referenceFalseKryoSerializer, delegateSyncService, () -> false);
    DelegateServiceAgentClient delegateServiceAgentClient = new DelegateServiceAgentClient();

    DelegateCallbackToken callbackToken = delegateServiceGrpcClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("!!!custom")
                                  .setConnection(configuration.getMongoConnectionFactory().getUri())
                                  .build())
            .build());

    assertThat(callbackToken).isNotNull();
    assertThat(callbackToken.getToken()).isNotBlank();

    String taskUuid = generateUuid();
    TaskId taskId = TaskId.newBuilder().setId(taskUuid).build();
    ProgressData testData = DelegateStringProgressData.builder().data("Progress1").build();
    byte[] testDataBytes = referenceFalseKryoSerializer.asDeflatedBytes(testData);
    ProgressData testData2 = DelegateStringProgressData.builder().data("Progress2").build();
    byte[] testDataBytes2 = referenceFalseKryoSerializer.asDeflatedBytes(testData2);

    waitNotifyEngine.waitForAllOn("general", new TestNotifyCallback(), new TestProgressCallback(), taskUuid);

    delegateServiceAgentClient.sendTaskProgressUpdate(
        AccountId.newBuilder().setId(getAccount().getUuid()).build(), taskId, callbackToken, testDataBytes);
    delegateServiceAgentClient.sendTaskProgressUpdate(
        AccountId.newBuilder().setId(getAccount().getUuid()).build(), taskId, callbackToken, testDataBytes2);

    Poller.pollFor(Duration.ofMinutes(5), Duration.ofSeconds(5), () -> { return progressCallCount.get() == 2; });

    assertThat(progressCallCount.get()).isEqualTo(2);
    assertThat(progressDataList.size()).isEqualTo(2);
    DelegateStringProgressData result1 = (DelegateStringProgressData) progressDataList.get(0);
    DelegateStringProgressData result2 = (DelegateStringProgressData) progressDataList.get(1);
    assertThat(Arrays.asList(result1.getData(), result2.getData())).containsExactlyInAnyOrder("Progress1", "Progress2");
  }

  public static class TestNotifyCallback implements OldNotifyCallback {
    @Override
    public void notify(Map<String, ResponseData> response) {}

    @Override
    public void notifyError(Map<String, ResponseData> response) {
      // Do Nothing.
    }
  }

  public static class TestProgressCallback implements ProgressCallback {
    @Override
    public void notify(String correlationId, ProgressData progressData) {
      progressCallCount.incrementAndGet();
      progressDataList.add(progressData);
    }
  }
}
