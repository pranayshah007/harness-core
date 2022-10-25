/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.client.impl.appender;

import static io.harness.event.payloads.Lifecycle.EventType.EVENT_TYPE_START;
import static io.harness.event.payloads.Lifecycle.EventType.EVENT_TYPE_STOP;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.awaitility.Awaitility.await;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.event.EventPublisherGrpc;
import io.harness.event.EventPublisherGrpc.EventPublisherBlockingStub;
import io.harness.event.PublishMessage;
import io.harness.event.client.EventPublisher;
import io.harness.event.client.FakeService;
import io.harness.event.client.impl.tailer.ChronicleEventTailer;
import io.harness.event.client.impl.tailer.TailerModule;
import io.harness.event.payloads.Lifecycle;
import io.harness.event.payloads.Lifecycle.EventType;
import io.harness.flow.BackoffScheduler;
import io.harness.govern.ProviderModule;
import io.harness.grpc.utils.HTimestamps;
import io.harness.rule.Owner;
import io.harness.threading.Concurrent;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.util.Modules;
import com.google.protobuf.Any;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChronicleEventAppenderTest extends CategoryTest {
  private final String QUEUE_FILE_PATH = "../eventQueue"
      + "/" + UUID.randomUUID();
  private final String SERVER_NAME = InProcessServerBuilder.generateName();
  private final AtomicInteger messagesPublished = new AtomicInteger();

  private final Injector injector = Guice.createInjector(
      Modules
          .override(
              new AppenderModule(AppenderModule.Config.builder().queueFilePath(QUEUE_FILE_PATH).build(), () -> ""),
              new TailerModule(TailerModule.Config.builder().queueFilePath(QUEUE_FILE_PATH).build()))
          .with(new ProviderModule() {
            @Provides
            @Singleton
            ManagedChannel channel() {
              return InProcessChannelBuilder.forName(SERVER_NAME).build();
            }

            @Provides
            @Singleton
            EventPublisherBlockingStub eventPublisherBlockingStub(ManagedChannel channel) {
              return EventPublisherGrpc.newBlockingStub(channel);
            }

            @Provides
            @Singleton
            @Named("tailer")
            BackoffScheduler backoffScheduler() {
              Duration delay = Duration.ofMillis(50);
              return new BackoffScheduler(ChronicleEventTailer.class.getSimpleName(), delay, delay);
            }
          }));
  @Inject private EventPublisher eventPublisher;
  @Inject private ManagedChannel channel;
  private Server server;
  private FakeService fakeService;

  @Before
  public void setUp() throws Exception {
    File directory = new File(QUEUE_FILE_PATH);
    FileUtils.forceMkdir(directory);
    FileUtils.cleanDirectory(directory);
    injector.injectMembers(this);
    fakeService = new FakeService();
    server = InProcessServerBuilder.forName(SERVER_NAME).addService(fakeService).build();
    server.start();
    injector.getInstance(ChronicleEventTailer.class).startAsync().awaitRunning();
  }

  @After
  public void tearDown() throws Exception {
    eventPublisher.shutdown();
    final ChronicleEventTailer eventTailer = injector.getInstance(ChronicleEventTailer.class);
    eventTailer.stopAsync().awaitTerminated();
    server.shutdown();
    server.awaitTermination();
    channel.shutdownNow().awaitTermination(10, TimeUnit.SECONDS);
    FileUtils.cleanDirectory(new File(QUEUE_FILE_PATH));
    deleteDirectoryAndItsContentIfExists(QUEUE_FILE_PATH);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void shouldThrowIaeIfWrongMethodIsCalled() {
    assertThatIllegalArgumentException().isThrownBy(
        ()
            -> eventPublisher.publishMessage(
                PublishMessage.newBuilder().build(), HTimestamps.fromInstant(Instant.now())));
  }

  @Test
  @Owner(developers = AVMOHAN, intermittent = true)
  @Category(UnitTests.class)
  public void testNoMessageLoss() throws Exception {
    concurrentPublish(eventPublisher);
  }

  @Test
  @Owner(developers = AVMOHAN, intermittent = true)
  @Category(UnitTests.class)
  public void testNoMessageLossWithErrorProneServer() throws Exception {
    fakeService.setErrorProne(true);
    concurrentPublish(eventPublisher);
  }

  private void concurrentPublish(EventPublisher eventPublisher) throws Exception {
    int numThreads = 20;
    CountDownLatch latch = new CountDownLatch(numThreads);
    Concurrent.test(numThreads, x -> {
      try {
        int numMessages = 1000;
        for (int i = 0; i < numMessages; i++) {
          eventPublisher.publishMessage(Lifecycle.newBuilder()
                                            .setInstanceId("instanceId-123")
                                            .setType(EVENT_TYPE_START)
                                            .setTimestamp(HTimestamps.fromInstant(Instant.now()))
                                            .build(),
              HTimestamps.fromInstant(Instant.now()));
          messagesPublished.incrementAndGet();
        }
      } finally {
        latch.countDown();
      }
    });
    latch.await();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        // >= because "at-least"-once delivery semantics - i.e. duplicate delivery is allowed.
        .until(() -> assertThat(fakeService.getMessageCount()).isGreaterThanOrEqualTo(messagesPublished.get()));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testMessageReceived() {
    fakeService.setRecordMessages(true);
    Instant instant = Instant.now().minus(3, ChronoUnit.MINUTES);
    Lifecycle message = ecsLifecycleEvent("instance-123", instant, EVENT_TYPE_STOP);
    eventPublisher.publishMessage(message, HTimestamps.fromInstant(Instant.now()));
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(()
                   -> assertThat(fakeService.getReceivedMessages().stream().map(PublishMessage::getPayload))
                          .contains(Any.pack(message)));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  @Ignore(value = "TODO")
  public void testMessageReceivedEvenIfServerCallFails() {
    fakeService.setRecordMessages(true);
    fakeService.failNext();

    Instant instant = Instant.now().minus(13, ChronoUnit.MINUTES);
    Lifecycle message = ecsLifecycleEvent("instance-456", instant, EVENT_TYPE_START);
    eventPublisher.publishMessage(message, HTimestamps.fromInstant(Instant.now()));

    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(()
                   -> assertThat(fakeService.getReceivedMessages().stream().map(PublishMessage::getPayload))
                          .contains(Any.pack(message)));
  }

  private Lifecycle ecsLifecycleEvent(String instanceId, Instant eventTime, EventType eventType) {
    return Lifecycle.newBuilder()
        .setInstanceId(instanceId)
        .setTimestamp(HTimestamps.fromInstant(eventTime))
        .setType(eventType)
        .setCreatedTimestamp(HTimestamps.fromInstant(Instant.now()))
        .build();
  }
}
