/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.queue;

import static io.harness.queue.QueueConsumer.Filter.ALL;
import static io.harness.rule.OwnerRule.GEORGE;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.PersistenceTestBase;
import io.harness.category.element.StressTests;
import io.harness.maintenance.MaintenanceGuard;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.threading.Poller;

import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class StressTest extends PersistenceTestBase {
  private static final int COUNT = 1000000;
  @Inject private HPersistence persistence;
  @Inject private QueueConsumer<TestTopicQueuableObject> topicConsumer;
  @Inject private QueuePublisher<TestTopicQueuableObject> topicPublisher;
  @Inject private QueueConsumer<TestNoTopicQueuableObject> noTopicConsumer;
  @Inject private QueuePublisher<TestNoTopicQueuableObject> noToipcPublisher;

  @Test
  @Owner(developers = GEORGE)
  @Category(StressTests.class)

  @Ignore("This is stress test we should ignore it to allow for simple run of unit tests")
  public void topicPerformance() throws IOException {
    assertThatCode(() -> {
      persistence.ensureIndexForTesting(TestTopicQueuableObject.class);

      try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
        for (int i = 1; i <= COUNT; ++i) {
          final TestTopicQueuableObject queuableObject = new TestTopicQueuableObject(i);
          queuableObject.setTopic("dummy");

          persistence.save(queuableObject);

          if (i % 10000 == 0) {
            log.info("Previous topic records added: {}, still in queue {}", i, topicConsumer.count(ALL));
          }
        }
        for (int i = 1; i <= COUNT; ++i) {
          topicPublisher.send(new TestTopicQueuableObject(i));
          if (i % 10000 == 0) {
            log.info("Correct topic records added: {} , still in the queue {}", i, topicConsumer.count(ALL));
          }
        }

        final long start = topicConsumer.count(ALL);
        try {
          Poller.pollFor(Duration.ofSeconds(10), ofMillis(100), () -> {
            final long count = topicConsumer.count(ALL);
            // log.info("Intermittent queue count: {}", count);
            return count == 0;
          });
        } catch (Exception ignore) {
          // do nothing
        }

        final long diff = start - topicConsumer.count(ALL);
        log.info("Items handled for 10s: {}", diff);
      }
    }).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(StressTests.class)

  @Ignore("This is stress test we should ignore it to allow for simple run of unit tests")
  public void noTopicPerformance() throws IOException {
    assertThatCode(() -> {
      persistence.ensureIndexForTesting(TestNoTopicQueuableObject.class);

      try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
        for (int i = 1; i <= COUNT; ++i) {
          final TestNoTopicQueuableObject queuableObject = new TestNoTopicQueuableObject(i);
          queuableObject.setTopic("dummy");
          noToipcPublisher.send(queuableObject);

          if (i % 10000 == 0) {
            log.info("Previous records added: {}, still in queue {}", i, noTopicConsumer.count(ALL));
          }
        }
        for (int i = 1; i <= COUNT; ++i) {
          noToipcPublisher.send(new TestNoTopicQueuableObject(i));
          if (i % 10000 == 0) {
            log.info("Correct records added: {} , still in the queue {}", i, noTopicConsumer.count(ALL));
          }
        }

        final long start = noTopicConsumer.count(ALL);
        try {
          Poller.pollFor(Duration.ofSeconds(10), ofMillis(100), () -> {
            final long count = noTopicConsumer.count(ALL);
            // log.info("Intermittent queue count: {}", count);
            return count == 0;
          });
        } catch (Exception ignore) {
          // do nothing
        }

        final long diff = start - noTopicConsumer.count(ALL);
        log.info("Items handled for 10s: {}", diff);
      }
    }).doesNotThrowAnyException();
  }
}
