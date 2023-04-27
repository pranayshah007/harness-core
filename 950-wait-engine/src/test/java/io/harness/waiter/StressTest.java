/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.waiter.TestNotifyEventListener.TEST_PUBLISHER;

import io.harness.WaitEngineTestBase;
import io.harness.category.element.StressTests;
import io.harness.maintenance.MaintenanceGuard;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.threading.Concurrent;
import io.harness.threading.Morpheus;

import com.google.inject.Inject;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class StressTest extends WaitEngineTestBase {
  private static final SecureRandom random = new SecureRandom();

  @Inject private HPersistence persistence;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private NotifyResponseCleanupHelper notifyResponseCleaner;

  @Test
  @Owner(developers = GEORGE)
  @Category(StressTests.class)

  @Ignore("Ignore this stress test to make it easy to run only unit tests")
  public void stress() throws IOException {
    persistence.ensureIndexForTesting(NotifyEvent.class);
    persistence.ensureIndexForTesting(WaitInstance.class);
    persistence.ensureIndexForTesting(NotifyResponse.class);

    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      Concurrent.test(1, n -> {
        int i = 1;

        List<String> vector = new ArrayList<>();

        long time = 0;
        while (i < 10000) {
          final int ids = random.nextInt(5) + 1;
          if (i / 100 != (i + ids) / 100) {
            final long waits = persistence.createQuery(WaitInstance.class).count();
            long notifyQueues = persistence.createQuery(NotifyEvent.class).count();
            log.info(
                "{}: i = {}, avg: {}, waits: {}, events: {}", n, (i / 100 + 1) * 100, time / i, waits, notifyQueues);
          }
          i += ids;
          final String[] correlationIds = new String[ids];
          for (int id = 0; id < ids; id++) {
            final String uuid = generateUuid();
            correlationIds[id] = uuid;
            vector.add(uuid);
          }
          waitNotifyEngine.waitForAllOn(TEST_PUBLISHER, null, correlationIds);

          while (vector.size() > 0) {
            int index = random.nextInt(vector.size());
            time -= System.currentTimeMillis();
            waitNotifyEngine.doneWith(vector.get(index), null);
            time += System.currentTimeMillis();

            final int last = vector.size() - 1;
            vector.set(index, vector.get(last));
            vector.remove(last);
          }
        }
      });

      while (true) {
        notifyResponseCleaner.execute();
        final long waits = persistence.createQuery(WaitInstance.class).count();
        long notifyQueues = persistence.createQuery(NotifyEvent.class).count();
        log.info("waits: {}, events: {}", waits, notifyQueues);

        if (notifyQueues == 0) {
          break;
        }
        Morpheus.sleep(Duration.ofSeconds(1));
      }
    }
  }
}
