/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SANJA;
import static io.harness.waiter.TestNotifyEventListener.TEST_PUBLISHER;

import static com.google.common.collect.ImmutableMap.of;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.WaitEngineTestBase;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.maintenance.MaintenanceGuard;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueConsumer.Filter;
import io.harness.queue.QueueListenerController;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ProgressData;
import io.harness.tasks.ResponseData;
import io.harness.threading.Concurrent;
import io.harness.threading.Poller;
import io.harness.timeout.TimeoutEngine;
import io.harness.waiter.ProgressUpdate.ProgressUpdateKeys;
import io.harness.waiter.WaitInstance.WaitInstanceKeys;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.query.Sort;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class WaitNotifyEngineTest extends WaitEngineTestBase {
  private static AtomicInteger callCount;
  private static AtomicInteger progressCallCount;
  private static AtomicInteger timeoutCallCount;
  private static Map<String, ResponseData> responseMap;
  private static List<ProgressData> progressDataList;
  private static CountDownLatch barrier;
  private static CountDownLatch[] barriers;

  @Inject private TimeoutEngine timeoutEngine;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private HPersistence persistence;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private QueueConsumer<NotifyEvent> notifyConsumer;
  @Inject private NotifyResponseCleanupHelper notifyResponseCleaner;
  @Inject private TestNotifyEventListener notifyEventListener;
  @Inject private QueueListenerController queueListenerController;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  /**
   * Setup response map.
   */
  @Before
  public void setupResponseMap() {
    timeoutCallCount = new AtomicInteger(0);
    callCount = new AtomicInteger(0);
    responseMap = new HashMap<>();
    progressCallCount = new AtomicInteger(0);
    progressDataList = new ArrayList<>();
    queueListenerController.register(notifyEventListener, 1);
  }

  /**
   * Should wait for correlation id.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldWaitForCorrelationId() throws IOException {
    String uuid = generateUuid();
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      String waitInstanceId = waitNotifyEngine.waitForAllOn(TEST_PUBLISHER, new TestNotifyCallback(), uuid);

      assertThat(persistence.get(WaitInstance.class, waitInstanceId)).isNotNull();

      ResponseData data = StringNotifyResponseData.builder().data("response-" + uuid).build();
      String id = waitNotifyEngine.doneWith(uuid, data);
      NotifyResponse notifyResponse = persistence.get(NotifyResponse.class, id);
      assertThat(notifyResponse).isNotNull();
      assertThat(notifyResponse.isUsingKryoWithoutReference()).isTrue();
      ResponseData responseDataResult =
          (ResponseData) referenceFalseKryoSerializer.asInflatedObject(notifyResponse.getResponseData());
      assertThat(responseDataResult).isEqualTo(data);

      Poller.pollFor(Duration.ofSeconds(10), ofMillis(100), () -> notifyConsumer.count(Filter.ALL) == 0);

      assertThat(responseMap).hasSize(1).isEqualTo(of(uuid, data));
      assertThat(callCount.get()).isEqualTo(1);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void stressWaitForCorrelationId() throws IOException {
    String uuid = generateUuid();
    try (MaintenanceGuard guard = new MaintenanceGuard(true)) {
      String waitInstanceId = waitNotifyEngine.waitForAllOn(TEST_PUBLISHER, new TestNotifyCallback(), uuid);

      assertThat(persistence.get(WaitInstance.class, waitInstanceId)).isNotNull();

      ResponseData data = StringNotifyResponseData.builder().data("response-" + uuid).build();
      String id = waitNotifyEngine.doneWith(uuid, data);

      NotifyResponse notifyResponse = persistence.get(NotifyResponse.class, id);
      assertThat(notifyResponse).isNotNull();
      assertThat(notifyResponse.isUsingKryoWithoutReference()).isTrue();
      ResponseData responseDataResult =
          (ResponseData) referenceFalseKryoSerializer.asInflatedObject(notifyResponse.getResponseData());
      assertThat(responseDataResult).isEqualTo(data);

      Concurrent.test(10, i -> { notifyEventListener.execute(); });

      assertThat(notifyConsumer.count(Filter.ALL)).isEqualTo(0);

      assertThat(responseMap).hasSize(1).isEqualTo(of(uuid, data));
      assertThat(callCount.get()).isEqualTo(1);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testNotifyBeforeWait() throws IOException {
    String uuid = generateUuid();
    try (MaintenanceGuard guard = new MaintenanceGuard(true)) {
      ResponseData data = StringNotifyResponseData.builder().data("response-" + uuid).build();
      String id = waitNotifyEngine.doneWith(uuid, data);

      String waitInstanceId = waitNotifyEngine.waitForAllOn(TEST_PUBLISHER, new TestNotifyCallback(), uuid);

      assertThat(persistence.get(WaitInstance.class, waitInstanceId)).isNotNull();

      NotifyResponse notifyResponse = persistence.get(NotifyResponse.class, id);
      assertThat(notifyResponse).isNotNull();
      assertThat(notifyResponse.isUsingKryoWithoutReference()).isTrue();
      ResponseData responseDataResult =
          (ResponseData) referenceFalseKryoSerializer.asInflatedObject(notifyResponse.getResponseData());
      assertThat(responseDataResult).isEqualTo(data);

      notifyEventListener.execute();

      assertThat(notifyConsumer.count(Filter.ALL)).isEqualTo(0);

      assertThat(responseMap).hasSize(1).isEqualTo(of(uuid, data));
      assertThat(callCount.get()).isEqualTo(1);
    }
  }

  /**
   * Should wait for correlation ids.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldWaitForCorrelationIds() throws IOException {
    String uuid1 = generateUuid();
    String uuid2 = generateUuid();
    String uuid3 = generateUuid();

    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      String waitInstanceId =
          waitNotifyEngine.waitForAllOn(TEST_PUBLISHER, new TestNotifyCallback(), uuid1, uuid2, uuid3);

      assertThat(persistence.get(WaitInstance.class, waitInstanceId)).isNotNull();

      ResponseData data1 = StringNotifyResponseData.builder().data("response-" + uuid1).build();

      String id = waitNotifyEngine.doneWith(uuid1, data1);

      NotifyResponse notifyResponse1 = persistence.get(NotifyResponse.class, id);
      assertThat(notifyResponse1).isNotNull();
      assertThat(notifyResponse1.isUsingKryoWithoutReference()).isTrue();
      ResponseData responseDataResult1 =
          (ResponseData) referenceFalseKryoSerializer.asInflatedObject(notifyResponse1.getResponseData());
      assertThat(responseDataResult1).isEqualTo(data1);

      Poller.pollFor(Duration.ofSeconds(10), ofMillis(100), () -> notifyConsumer.count(Filter.ALL) == 0);

      assertThat(responseMap).hasSize(0);
      ResponseData data2 = StringNotifyResponseData.builder().data("response-" + uuid2).build();

      id = waitNotifyEngine.doneWith(uuid2, data2);

      NotifyResponse notifyResponse2 = persistence.get(NotifyResponse.class, id);
      assertThat(notifyResponse2).isNotNull();
      assertThat(notifyResponse2.isUsingKryoWithoutReference()).isTrue();
      ResponseData responseDataResult2 =
          (ResponseData) referenceFalseKryoSerializer.asInflatedObject(notifyResponse2.getResponseData());
      assertThat(responseDataResult2).isEqualTo(data2);

      Poller.pollFor(Duration.ofSeconds(10), ofMillis(100), () -> notifyConsumer.count(Filter.ALL) == 0);

      assertThat(responseMap).hasSize(0);
      ResponseData data3 = StringNotifyResponseData.builder().data("response-" + uuid3).build();

      id = waitNotifyEngine.doneWith(uuid3, data3);

      NotifyResponse notifyResponse3 = persistence.get(NotifyResponse.class, id);
      assertThat(notifyResponse3).isNotNull();
      assertThat(notifyResponse3.isUsingKryoWithoutReference()).isTrue();
      ResponseData responseDataResult =
          (ResponseData) referenceFalseKryoSerializer.asInflatedObject(notifyResponse3.getResponseData());
      assertThat(responseDataResult).isEqualTo(data3);

      Poller.pollFor(Duration.ofSeconds(10), ofMillis(100), () -> notifyConsumer.count(Filter.ALL) == 0);

      assertThat(responseMap).hasSize(3).containsAllEntriesOf(of(uuid1, data1, uuid2, data2, uuid3, data3));
      assertThat(callCount.get()).isEqualTo(1);
    }
  }

  @Test
  @SpringWaiter
  @Owner(developers = PRASHANT, intermittent = true)
  @Category(UnitTests.class)
  public void shouldTestTimeoutWithResponse() {
    String uuid1 = generateUuid();
    String uuid2 = generateUuid();
    List<String> correlationIds = Arrays.asList(uuid1, uuid2);

    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      timeoutEngine.createAndStartIterator(
          PersistenceIteratorFactory.PumpExecutorOptions.builder().name("TimeoutEngine").poolSize(5).build(),
          ofSeconds(60));
      String waitInstanceId = waitNotifyEngine.waitForAllOnInList(
          TEST_PUBLISHER, new TestNotifyCallback(), correlationIds, Duration.ofSeconds(3));
      WaitInstance waitInstance =
          mongoTemplate.findOne(query(where(WaitInstanceKeys.uuid).is(waitInstanceId)), WaitInstance.class);
      assertThat(waitInstance).isNotNull();
      assertThat(waitInstance.getTimeout()).isEqualTo(Duration.ofSeconds(3));
      ResponseData data = StringNotifyResponseData.builder().data("response-" + uuid1).build();
      waitNotifyEngine.doneWith(uuid1, data);
      Poller.pollFor(Duration.ofSeconds(6), ofMillis(300), () -> timeoutCallCount.get() == 1);
      assertThat(responseMap).hasSize(1).containsAllEntriesOf(of(uuid1, data));
      assertThat(mongoTemplate.findOne(query(where(WaitInstanceKeys.uuid).is(waitInstanceId)), WaitInstance.class))
          .isNull();
    }
  }

  @Test
  @SpringWaiter
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldNoopDoneWith() {
    String uuid1 = generateUuid();
    List<String> correlationIds = Arrays.asList(uuid1);

    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      String waitInstanceId = waitNotifyEngine.waitForAllOnInList(
          TEST_PUBLISHER, new TestNotifyCallback(), correlationIds, Duration.ofSeconds(3));
      WaitInstance waitInstance =
          mongoTemplate.findOne(query(where(WaitInstanceKeys.uuid).is(waitInstanceId)), WaitInstance.class);
      assertThat(waitInstance).isNotNull();
      assertThat(waitInstance.getTimeout()).isEqualTo(Duration.ofSeconds(3));
      boolean result = waitNotifyEngine.doneWithWithoutCallback(uuid1);
      assertThat(result).isTrue();
      WaitInstance deleted =
          mongoTemplate.findOne(query(where(WaitInstanceKeys.uuid).is(waitInstanceId)), WaitInstance.class);
      assertThat(deleted).isNull();
    }
  }

  @Test
  @SpringWaiter
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldNoopDoneWithMultipleCorrelationIds() {
    String uuid1 = generateUuid();
    String uuid2 = generateUuid();
    List<String> correlationIds = Arrays.asList(uuid1, uuid2);

    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      String waitInstanceId = waitNotifyEngine.waitForAllOnInList(
          TEST_PUBLISHER, new TestNotifyCallback(), correlationIds, Duration.ofSeconds(3));
      WaitInstance waitInstance =
          mongoTemplate.findOne(query(where(WaitInstanceKeys.uuid).is(waitInstanceId)), WaitInstance.class);
      assertThat(waitInstance).isNotNull();
      assertThat(waitInstance.getTimeout()).isEqualTo(Duration.ofSeconds(3));

      boolean result1 = waitNotifyEngine.doneWithWithoutCallback(uuid1);
      assertThat(result1).isTrue();
      WaitInstance firstCallbackInstance =
          mongoTemplate.findOne(query(where(WaitInstanceKeys.uuid).is(waitInstanceId)), WaitInstance.class);
      assertThat(firstCallbackInstance).isNotNull();
      assertThat(firstCallbackInstance.getWaitingOnCorrelationIds()).hasSize(1);
      assertThat(firstCallbackInstance.getWaitingOnCorrelationIds()).containsExactly(uuid2);

      boolean result2 = waitNotifyEngine.doneWithWithoutCallback(uuid2);
      assertThat(result2).isTrue();
      WaitInstance firstCallbackInstance2 =
          mongoTemplate.findOne(query(where(WaitInstanceKeys.uuid).is(waitInstanceId)), WaitInstance.class);
      assertThat(firstCallbackInstance2).isNull();
    }
  }

  @Test
  @SpringWaiter
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestTimeoutValid() {
    String uuid1 = generateUuid();
    List<String> correlationIds = Arrays.asList(uuid1);
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      assertThatThrownBy(()
                             -> waitNotifyEngine.waitForAllOnInList(
                                 TEST_PUBLISHER, new TestNotifyCallback(), correlationIds, Duration.ofSeconds(2)))
          .isInstanceOf(InvalidArgumentsException.class)
          .hasMessageContaining("Timeout should be greater than");
    }
  }

  /**
   * Should wait forx correlation id for multiple wait instances.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldWaitForCorrelationIdForMultipleWaitInstances() throws IOException {
    String uuid = generateUuid();

    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      String waitInstanceId1 = waitNotifyEngine.waitForAllOn(TEST_PUBLISHER, new TestNotifyCallback(), uuid);
      String waitInstanceId2 = waitNotifyEngine.waitForAllOn(TEST_PUBLISHER, new TestNotifyCallback(), uuid);
      String waitInstanceId3 = waitNotifyEngine.waitForAllOn(TEST_PUBLISHER, new TestNotifyCallback(), uuid);

      assertThat(persistence.createQuery(WaitInstance.class, excludeAuthority).asList())
          .hasSize(3)
          .extracting(WaitInstance::getUuid)
          .containsExactly(waitInstanceId1, waitInstanceId2, waitInstanceId3);

      ResponseData data = StringNotifyResponseData.builder().data("response-" + uuid).build();
      String id = waitNotifyEngine.doneWith(uuid, data);

      NotifyResponse notifyResponse = persistence.get(NotifyResponse.class, id);
      assertThat(notifyResponse).isNotNull();
      assertThat(notifyResponse.isUsingKryoWithoutReference()).isTrue();
      ResponseData responseDataResult =
          (ResponseData) referenceFalseKryoSerializer.asInflatedObject(notifyResponse.getResponseData());
      assertThat(responseDataResult).isEqualTo(data);

      while (notifyConsumer.count(Filter.ALL) != 0) {
        Thread.yield();
      }

      assertThat(responseMap).hasSize(1).containsAllEntriesOf(of(uuid, data));
      assertThat(callCount.get()).isEqualTo(3);
    }
  }

  /**
   * Should wait for progress on correlation id.
   */
  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldWaitForProgressOnCorrelationId() throws InterruptedException {
    String uuid = generateUuid();
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      String waitInstanceId =
          waitNotifyEngine.waitForAllOn(TEST_PUBLISHER, new TestNotifyCallback(), new TestProgressCallback(), uuid);

      assertThat(persistence.get(WaitInstance.class, waitInstanceId)).isNotNull();
      StringNotifyProgressData data1 = StringNotifyProgressData.builder().data("progress1-" + uuid).build();
      waitNotifyEngine.progressOn(uuid, data1);

      ProgressUpdate progressUpdate = persistence.createQuery(ProgressUpdate.class, excludeAuthority)
                                          .filter(ProgressUpdateKeys.correlationId, uuid)
                                          .get();
      assertThat(progressUpdate).isNotNull();
      assertThat(progressUpdate.isUsingKryoWithoutReference()).isTrue();
      ProgressData progressDataResult =
          (ProgressData) referenceFalseKryoSerializer.asInflatedObject(progressUpdate.getProgressData());
      assertThat(progressDataResult).isEqualTo(data1);

      StringNotifyProgressData data2 = StringNotifyProgressData.builder().data("progress2-" + uuid).build();
      waitNotifyEngine.progressOn(uuid, data2);

      List<ProgressUpdate> progressUpdate2 = persistence.createQuery(ProgressUpdate.class, excludeAuthority)
                                                 .filter(ProgressUpdateKeys.correlationId, uuid)
                                                 .order(Sort.ascending(ProgressUpdateKeys.createdAt))
                                                 .asList();
      assertThat(progressUpdate2).hasSize(2);
      ProgressData progressDataResult2 =
          (ProgressData) referenceFalseKryoSerializer.asInflatedObject(progressUpdate2.get(1).getProgressData());
      assertThat(progressDataResult2).isEqualTo(data2);

      while (progressCallCount.get() < 2) {
        Thread.yield();
      }

      StringNotifyProgressData result1 = (StringNotifyProgressData) progressDataList.get(0);
      StringNotifyProgressData result2 = (StringNotifyProgressData) progressDataList.get(1);

      assertThat(progressDataList).hasSize(2);
      assertThat(Arrays.asList(result1.getData(), result2.getData()))
          .containsExactlyInAnyOrder(data1.getData(), data2.getData());
      assertThat(progressCallCount.get()).isEqualTo(2);
    }
  }

  /**
   * Should wait for progress on correlation id.
   */
  // CREATES A WAIT INSTANCE AND VERIFY THAT TWO PROGRESS EVENTS ARE TRIGGERED TO THE SAME CORRELATION. AS THE CODE
  // NATURE IS MULTI THREAD, WE NEED TO ENFORCE THE FLOW EXECUTION TO AVOID A CASE WHEN PROGRESS IS CONSUMED MORE FAST
  // THAN WE CAN READ THE PERSISTED INFORMATION.
  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldWaitForProgressOnCorrelationIdUsingKryoWithoutReference() throws InterruptedException {
    String uuid = generateUuid();
    try (MaintenanceGuard ignore = new MaintenanceGuard(false)) {
      // USED AT END TO VERIFY NOTIFIED PROGRESS EVENTS
      barrier = new CountDownLatch(2);
      // USED AFTER POST PROGRESS EVENTS
      barriers = new CountDownLatch[] {new CountDownLatch(1), new CountDownLatch(1)};

      String waitInstanceId =
          waitNotifyEngine.waitForAllOn(TEST_PUBLISHER, new TestNotifyCallback(), new BarrierProgressCallback(), uuid);
      assertThat(persistence.get(WaitInstance.class, waitInstanceId)).isNotNull();

      StringNotifyProgressData data1 = StringNotifyProgressData.builder().data("progress1-" + uuid).build();
      waitNotifyEngine.progressOn(uuid, data1, true);

      ProgressUpdate progressUpdate = persistence.createQuery(ProgressUpdate.class, excludeAuthority)
                                          .filter(ProgressUpdateKeys.correlationId, uuid)
                                          .get();
      assertThat(progressUpdate).isNotNull();
      assertThat(progressUpdate.isUsingKryoWithoutReference()).isTrue();
      ProgressData progressDataResult =
          (ProgressData) referenceFalseKryoSerializer.asInflatedObject(progressUpdate.getProgressData());
      assertThat(progressDataResult).isEqualTo(data1);
      barriers[0].countDown();

      StringNotifyProgressData data2 = StringNotifyProgressData.builder().data("progress2-" + uuid).build();
      waitNotifyEngine.progressOn(uuid, data2, true);

      List<ProgressUpdate> progressUpdate2 = persistence.createQuery(ProgressUpdate.class, excludeAuthority)
                                                 .filter(ProgressUpdateKeys.correlationId, uuid)
                                                 .order(Sort.ascending(ProgressUpdateKeys.createdAt))
                                                 .asList();
      assertThat(progressUpdate2).hasSize(2);
      ProgressData progressDataResult2 =
          (ProgressData) referenceFalseKryoSerializer.asInflatedObject(progressUpdate2.get(1).getProgressData());
      assertThat(progressDataResult2).isEqualTo(data2);
      barriers[1].countDown();

      barrier.await(7L, TimeUnit.SECONDS);

      StringNotifyProgressData result1 = (StringNotifyProgressData) progressDataList.get(0);
      StringNotifyProgressData result2 = (StringNotifyProgressData) progressDataList.get(1);

      assertThat(progressDataList).hasSize(2);
      assertThat(Arrays.asList(result1.getData(), result2.getData()))
          .containsExactlyInAnyOrder(data1.getData(), data2.getData());
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCleanZombieNotifyResponse() {
    final NotifyResponse notifyResponse = NotifyResponse.builder()
                                              .uuid(generateUuid())
                                              .createdAt(System.currentTimeMillis() - ofSeconds(20).toMillis())
                                              .error(false)
                                              .build();
    String notificationId = persistence.save(notifyResponse);

    notifyResponseCleaner.execute();

    assertThat(persistence.get(NotifyResponse.class, notificationId)).isNull();
  }

  public static class TestNotifyCallback implements OldNotifyCallback {
    @Override
    public void notify(Map<String, ResponseData> response) {
      callCount.incrementAndGet();
      responseMap.putAll(response);
    }

    @Override
    public void notifyError(Map<String, ResponseData> response) {
      // Do Nothing.
    }

    @Override
    public void notifyTimeout(Map<String, ResponseData> response) {
      timeoutCallCount.incrementAndGet();
      responseMap.putAll(response);
    }
  }

  public static class TestProgressCallback implements ProgressCallback {
    @Override
    public void notify(String correlationId, ProgressData progressData) {
      progressCallCount.incrementAndGet();
      progressDataList.add(progressData);
    }
  }

  // SAVE PROGRESS DATA AND WAIT FOR COUNT DOWN OF THE CURRENT BARRIER EXECUTION, THEN COUNT DOWN THE FINAL BARRIER THAT
  // HAVE THE SAME COUNT OF THE EXPECTED COLLECTION SIZE.
  private static class BarrierProgressCallback implements ProgressCallback {
    @Override
    public void notify(String correlationId, ProgressData progressData) {
      progressDataList.add(progressData);
      try {
        barriers[progressDataList.size()].await();
        barrier.countDown();

      } catch (InterruptedException e) {
        // IGNORE
      }
    }
  }
}
