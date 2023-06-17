/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.queue;

import static io.harness.rule.OwnerRule.GEORGE;

import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.PersistenceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.maintenance.MaintenanceGuard;
import io.harness.mongo.queue.MongoQueueConsumer;
import io.harness.mongo.queue.MongoQueuePublisher;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueueConsumer.Filter;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.internal.stubbing.answers.ThrowsException;
import org.mockito.invocation.InvocationOnMock;

@Slf4j
public class QueueListenerTest extends PersistenceTestBase {
  private MongoQueuePublisher<TestTopicQueuableObject> producer;
  private MongoQueueConsumer<TestTopicQueuableObject> consumer;
  private TestTopicQueuableObjectListener listener;

  @Inject private HPersistence persistence;

  @Inject QueueListenerController queueListenerController;
  @Inject private TimerScheduledExecutorService timer;
  @Inject private QueueController queueController;

  @Before
  public void setup() throws Exception {
    queueListenerController.stop();

    producer = spy(new MongoQueuePublisher<>(TestTopicQueuableObject.class.getSimpleName(), asList("topic")));
    on(producer).set("persistence", persistence);

    consumer = spy(new MongoQueueConsumer<>(TestTopicQueuableObject.class, ofSeconds(5), asList(asList("topic"))));
    on(consumer).set("persistence", persistence);

    listener = new TestTopicQueuableObjectListener(consumer);
    listener.setRunOnce(true);
    on(listener).set("timer", timer);
    on(listener).set("queueController", queueController);
    listener = spy(listener);
  }

  @After
  public void tearDown() throws Exception {
    listener.shutDown();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldProcessWhenReceivedMessageFromQueue() throws IOException {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      TestTopicQueuableObject message = new TestTopicQueuableObject(1);
      producer.send(message);
      assertThat(consumer.count(Filter.ALL)).isEqualTo(1);
      listener.run();
      assertThat(consumer.count(Filter.ALL)).isEqualTo(0);
      verify(listener).onMessage(message);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldStopOnInterruptedException() {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      listener.setRunOnce(false);

      TestTopicQueuableObject message = new TestTopicQueuableObject(1);
      producer.send(message);
      assertThat(consumer.count(Filter.ALL)).isEqualTo(1);

      doThrow(new RuntimeException(new InterruptedException())).when(consumer).get(any(), any());

      listener.run();

      assertThat(consumer.count(Filter.ALL)).isEqualTo(1);
      verify(listener, times(0)).onMessage(any(TestTopicQueuableObject.class));
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldExtendHeartbeat() throws Exception {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      TestTopicQueuableObject message = new TestTopicQueuableObject(1);
      producer.send(message);
      assertThat(consumer.count(Filter.ALL)).isEqualTo(1);

      listener.setRunOnce(true);
      consumer.setHeartbeat(ofSeconds(1));

      doAnswer(invocation -> {
        log.info("In mock executor");
        Thread.sleep(1500);
        log.info("Done with mock");
        return invocation.callRealMethod();
      })
          .when(listener)
          .onMessage(message);

      Thread runThread = new Thread(listener);
      runThread.start();
      runThread.join();

      assertThat(consumer.count(Filter.ALL)).isEqualTo(0);
      verify(listener).onMessage(message);
      verify(consumer, atLeast(1)).updateHeartbeat(any(TestTopicQueuableObject.class));
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldContinueProcessingOnAnyOtherException() throws Exception {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      listener.setRunOnce(false);

      CountDownLatch countDownLatch = new CountDownLatch(2);

      doAnswer(new ThrowsException(new RuntimeException()) {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          countDownLatch.countDown();
          return super.answer(invocation);
        }
      })
          .when(consumer)
          .get(ofSeconds(3), ofSeconds(1));

      Thread listenerThread = new Thread(listener);
      listenerThread.start();
      assertThat(countDownLatch.await(3000, TimeUnit.MILLISECONDS)).isTrue();
      listener.shutDown();
      listenerThread.join();

      verify(listener, times(0)).onMessage(any(TestTopicQueuableObject.class));
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldRequeueMessageWhenRetriesAreSet() throws Exception {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      TestTopicQueuableObject message = new TestTopicQueuableObject(1);
      message.setRetries(1);
      listener.setThrowException(true);
      producer.send(message);
      assertThat(consumer.count(Filter.ALL)).isEqualTo(1);

      listener.run();

      assertThat(consumer.count(Filter.ALL)).isEqualTo(1);
      verify(listener).onMessage(message);
      verify(listener).onException(any(Exception.class), eq(message));
      verify(consumer).requeue(message.getId(), 0);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotRequeueMessageWhenRetriesAreZero() throws Exception {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      TestTopicQueuableObject message = new TestTopicQueuableObject(1);
      listener.setThrowException(true);
      producer.send(message);
      assertThat(consumer.count(Filter.ALL)).isEqualTo(1);

      listener.run();

      assertThat(consumer.count(Filter.ALL)).isEqualTo(0);
      verify(listener).onMessage(message);
      verify(listener).onException(any(Exception.class), eq(message));
    }
  }
}
