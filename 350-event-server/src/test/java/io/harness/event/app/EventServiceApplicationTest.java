/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.app;

import static io.harness.event.payloads.Lifecycle.EventType.EVENT_TYPE_START;
import static io.harness.grpc.IdentifierKeys.DELEGATE_ID;
import static io.harness.rule.EventServiceRule.DEFAULT_ACCOUNT_ID;
import static io.harness.rule.EventServiceRule.DEFAULT_ACCOUNT_SECRET;
import static io.harness.rule.EventServiceRule.DEFAULT_DELEGATE_ID;
import static io.harness.rule.EventServiceRule.QUEUE_FILE_PATH;
import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.EventServerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.event.client.EventPublisher;
import io.harness.event.client.impl.tailer.ChronicleEventTailer;
import io.harness.event.payloads.Lifecycle;
import io.harness.grpc.utils.HTimestamps;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.beans.Account;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.Inject;
import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class EventServiceApplicationTest extends EventServerTestBase {
  @Inject private HPersistence hPersistence;

  @Inject private EventPublisher eventPublisher;

  @Inject private ServiceManager serviceManager;

  @Inject ChronicleEventTailer chronicleEventTailer;

  @Before
  public void setUp() throws Exception {
    serviceManager.startAsync().awaitHealthy();
    apiServiceRule.getClosingFactory().addServer(() -> serviceManager.stopAsync());
    chronicleEventTailer.startAsync().awaitRunning();
    apiServiceRule.getClosingFactory().addServer(() -> FileUtils.deleteQuietly(new File(QUEUE_FILE_PATH)));
    apiServiceRule.getClosingFactory().addServer(() -> chronicleEventTailer.stopAsync().awaitTerminated());
    apiServiceRule.getClosingFactory().addServer(() -> eventPublisher.shutdown());
  }

  @Test
  @Owner(developers = AVMOHAN, intermittent = true)
  @Category(UnitTests.class)

  public void shouldEventuallyPersistPublishedEvent() throws Exception {
    hPersistence.save(
        Account.Builder.anAccount().withUuid(DEFAULT_ACCOUNT_ID).withAccountKey(DEFAULT_ACCOUNT_SECRET).build());
    Lifecycle message = Lifecycle.newBuilder()
                            .setInstanceId("instanceId-123")
                            .setType(EVENT_TYPE_START)
                            .setTimestamp(HTimestamps.fromInstant(Instant.now()))
                            .setCreatedTimestamp(HTimestamps.fromInstant(Instant.now().minus(10, ChronoUnit.HOURS)))
                            .build();
    Map<String, String> attributes = ImmutableMap.of("k1", "v1", "k2", "v2");
    eventPublisher.publishMessage(message, message.getTimestamp(), attributes);
    Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(10, TimeUnit.MILLISECONDS).until(() -> {
      PublishedMessage publishedMessage = hPersistence.createQuery(PublishedMessage.class).get();
      assertThat(publishedMessage).isNotNull();
      assertThat(publishedMessage.getAccountId()).isEqualTo(DEFAULT_ACCOUNT_ID);
      assertThat(publishedMessage.getAttributes())
          .isEqualTo(ImmutableMap.builder().putAll(attributes).put(DELEGATE_ID, DEFAULT_DELEGATE_ID).build());
      assertThat(publishedMessage.getMessage()).isEqualTo(message);
    });
  }
}
