/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.lock;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.lock.mongo.MongoPersistentLocker.LOCKS_STORE;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.threading.Morpheus.sleep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.harness.PersistenceTestBase;
import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.lock.mongo.MongoPersistentLocker;
import io.harness.rule.Owner;
import io.harness.testlib.RealMongo;
import io.harness.threading.Concurrent;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * The Class PersistentLockerTest.
 */
@OwnedBy(PL)
@Slf4j
public class MongoPersistentLockerDBTest extends PersistenceTestBase {
  @Inject private MongoPersistentLocker mongoPersistentLocker;

  private DBObject getDbLock(String uuid) {
    final DBCollection locks = mongoPersistentLocker.getPersistence().getCollection(LOCKS_STORE, "locks");
    return locks.findOne(new BasicDBObject().append("_id", uuid));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  @RealMongo
  public void testAcquireLockDoLock() {
    String uuid = generateUuid();
    try (AcquiredLock lock = mongoPersistentLocker.acquireLock(uuid, Duration.ofSeconds(1))) {
    }

    DBObject dbLock = getDbLock(uuid);
    assertThat(dbLock).isNotNull();

    boolean damage = false;
    try (AcquiredLock lock = mongoPersistentLocker.acquireLock(uuid, Duration.ofSeconds(1))) {
      mongoPersistentLocker.destroy(lock);
      damage = true;
    } catch (WingsException exception) {
      // Do nothing. This is just to suppress the exception
    }

    assertThat(damage).isFalse();

    dbLock = getDbLock(uuid);
    assertThat(dbLock).isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  @RealMongo
  public void testAcquireEphemeralLock() {
    String uuid = generateUuid();
    try (AcquiredLock lock = mongoPersistentLocker.acquireEphemeralLock(uuid, Duration.ofSeconds(1))) {
    } catch (WingsException exception) {
      // Do nothing. This is just to suppress the exception
    }

    DBObject dbLock = getDbLock(uuid);
    assertThat(dbLock).isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  @RealMongo
  public void testConcurrentAcquireEphemeralLock() {
    String uuid = generateUuid();

    Concurrent.test(10, i -> {
      try (AcquiredLock lock = mongoPersistentLocker.acquireEphemeralLock(uuid, Duration.ofSeconds(1))) {
      } catch (WingsException exception) {
        // Do nothing. This is just to suppress the exception
      }
    });
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  @RealMongo
  public void testAcquireLockAfterDestroy() {
    assertThatCode(() -> {
      String uuid = generateUuid();
      try (AcquiredLock lock = mongoPersistentLocker.acquireLock(uuid, Duration.ofSeconds(1))) {
        mongoPersistentLocker.destroy(lock);
      } catch (WingsException exception) {
        // Do nothing. This is just to suppress the exception
      }

      try (AcquiredLock lock = mongoPersistentLocker.acquireLock(uuid, Duration.ofSeconds(1))) {
      }
    }).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  @RealMongo
  public void testTryToAcquireEphemeralLock() {
    assertThatCode(() -> {
      String uuid = generateUuid();
      try (AcquiredLock outer =
               mongoPersistentLocker.tryToAcquireEphemeralLock(AcquiredLock.class, "foo", Duration.ofSeconds(1))) {
      }
    }).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  @RealMongo
  public void testTryToAcquireLock() {
    String uuid = generateUuid();
    try (AcquiredLock outer = mongoPersistentLocker.tryToAcquireLock(AcquiredLock.class, uuid, Duration.ofSeconds(1))) {
      assertThat(outer).isNotNull();
      try (AcquiredLock inner =
               mongoPersistentLocker.tryToAcquireLock(AcquiredLock.class, uuid, Duration.ofSeconds(1))) {
        assertThat(inner).isNull();
      }
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  @RealMongo
  @HarnessAlwaysRun
  public void testAcquireAfterTimeout() throws InterruptedException {
    assumeThat("The underlining code respects lock after timeout.").isEqualTo("true");
    class AnotherLock implements Runnable {
      public boolean locked;
      public boolean tested;

      @Override
      public void run() {
        try (AcquiredLock lock = mongoPersistentLocker.acquireLock(AcquiredLock.class, "cba", Duration.ofMillis(1))) {
          sleep(Duration.ofMillis(5));
          synchronized (this) {
            locked = true;
            this.notify();
          }

          synchronized (this) {
            while (!tested) {
              try {
                this.wait();
              } catch (InterruptedException e) {
                log.error("", e);
              }
            }
          }
        }
      }
    }

    AnotherLock run = new AnotherLock();
    Thread thread = new Thread(run);
    thread.start();

    synchronized (run) {
      while (!run.locked) {
        run.wait();
      }
    }

    boolean great = false;
    try (AcquiredLock lock = mongoPersistentLocker.acquireLock(AcquiredLock.class, "cba", Duration.ofMillis(100))) {
      great = true;
    }
    sleep(Duration.ofMillis(5));

    synchronized (run) {
      run.tested = true;
      run.notify();
    }

    thread.join();
    assertThat(great).isTrue();
  }
}
