/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.lock;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.GEORGE;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.harness.PersistenceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.MessageManager;
import io.harness.exception.WingsException;
import io.harness.lock.mongo.AcquiredDistributedLock;
import io.harness.lock.mongo.MongoPersistentLocker;
import io.harness.rule.Owner;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.deftlabs.lock.mongo.DistributedLock;
import com.deftlabs.lock.mongo.DistributedLockOptions;
import com.deftlabs.lock.mongo.DistributedLockSvc;
import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

/**
 * The Class PersistentLockerTest.
 */
@OwnedBy(PL)
public class MongoPersistentLockerTest extends PersistenceTestBase {
  @Mock private DistributedLockSvc distributedLockSvc;

  @Inject @InjectMocks private MongoPersistentLocker mongoPersistentLocker;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testAcquireLockDoLock() {
    Duration timeout = ofMillis(1000);

    DistributedLockOptions options = new DistributedLockOptions();
    options.setInactiveLockTimeout((int) timeout.toMillis());

    DistributedLock distributedLock = mock(DistributedLock.class);
    when(distributedLock.getOptions()).thenReturn(options);

    when(distributedLock.tryLock()).thenReturn(true);
    when(distributedLock.isLocked()).thenReturn(true);
    when(distributedLockSvc.create(matches(AcquiredLock.class.getName() + "-cba"), any())).thenReturn(distributedLock);

    try (AcquiredLock lock = mongoPersistentLocker.acquireLock(AcquiredLock.class, "cba", Duration.ofMinutes(1))) {
    }

    InOrder inOrder = inOrder(distributedLock);
    inOrder.verify(distributedLock, times(1)).tryLock();
    inOrder.verify(distributedLock, times(1)).unlock();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testAcquireLockDoNotRunTheBody() {
    DistributedLock distributedLock = mock(DistributedLock.class);
    when(distributedLock.tryLock()).thenReturn(false);
    when(distributedLockSvc.create(matches(AcquiredLock.class.getName() + "-cba"), any())).thenReturn(distributedLock);

    boolean body = false;
    try (AcquiredLock lock = mongoPersistentLocker.acquireLock(AcquiredLock.class, "cba", Duration.ofMinutes(1))) {
      body = true;
    } catch (RuntimeException ex) {
      assertThat(ex.getMessage()).isEqualTo("Failed to acquire distributed lock for io.harness.lock.AcquiredLock-cba");
    }

    assertThat(body).isFalse();

    InOrder inOrder = inOrder(distributedLock);
    inOrder.verify(distributedLock, times(1)).tryLock();
    inOrder.verify(distributedLock, times(0)).unlock();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testTryAcquireLockDoNotThrowException() {
    DistributedLock distributedLock = mock(DistributedLock.class);
    when(distributedLock.tryLock()).thenReturn(false);
    when(distributedLockSvc.create(matches(AcquiredLock.class.getName() + "-cba"), any())).thenReturn(distributedLock);

    boolean body = false;
    try (AcquiredLock lock = mongoPersistentLocker.tryToAcquireLock(AcquiredLock.class, "cba", Duration.ofMinutes(1))) {
      assertThat(lock).isNull();
      body = true;
    }

    assertThat(body).isTrue();

    InOrder inOrder = inOrder(distributedLock);
    inOrder.verify(distributedLock, times(1)).tryLock();
    inOrder.verify(distributedLock, times(0)).unlock();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testAcquireLockNonLockedAtRelease() throws IllegalAccessException {
    Duration timeout = ofMillis(1000);

    DistributedLockOptions options = new DistributedLockOptions();
    options.setInactiveLockTimeout((int) timeout.toMillis());

    DistributedLock distributedLock = mock(DistributedLock.class);
    when(distributedLock.getOptions()).thenReturn(options);

    when(distributedLock.tryLock()).thenReturn(true);
    when(distributedLock.isLocked()).thenReturn(false);
    when(distributedLockSvc.create(matches(AcquiredLock.class.getName() + "-cba"), any())).thenReturn(distributedLock);

    Logger logger = (Logger) LoggerFactory.getLogger(AcquiredDistributedLock.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(listAppender);

    try (AcquiredLock lock = mongoPersistentLocker.acquireLock(AcquiredLock.class, "cba", timeout)) {
    }
    assertThat(listAppender.list).hasSize(1);
    assertThat(listAppender.list.get(0).getFormattedMessage())
        .isEqualTo("attempt to release lock that is not currently locked");
    assertThat(listAppender.list.get(0).getThrowableProxy()).isNotNull();
    assertThat(listAppender.list.get(0).getThrowableProxy().getClassName())
        .isEqualTo(Exception.class.getCanonicalName());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testAcquireLockLogging() throws IllegalAccessException {
    DistributedLock distributedLock = mock(DistributedLock.class);
    when(distributedLock.tryLock()).thenReturn(false);
    when(distributedLockSvc.create(matches(AcquiredLock.class.getName() + "-cba"), any())).thenReturn(distributedLock);

    Logger logger = (Logger) LoggerFactory.getLogger(MessageManager.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    try (AcquiredLock lock = mongoPersistentLocker.acquireLock(AcquiredLock.class, "cba", Duration.ofMinutes(1))) {
    } catch (WingsException exception) {
      logger.error("", exception);
    }
    assertThat(listAppender.list).hasSize(1);
    assertThat(listAppender.list.get(0).getFormattedMessage()).isEqualTo("");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testAcquireTimeout() throws InterruptedException {
    assumeThat("We can have timeout logic").isEqualTo("true");
    Duration timeout = ofMillis(1);

    DistributedLockOptions options = new DistributedLockOptions();
    options.setInactiveLockTimeout((int) timeout.toMillis());

    DistributedLock distributedLock = mock(DistributedLock.class);
    when(distributedLock.getOptions()).thenReturn(options);
    when(distributedLock.getName()).thenReturn(AcquiredLock.class.getName() + "-cba");
    when(distributedLock.tryLock()).thenReturn(true);
    when(distributedLockSvc.create(matches(AcquiredLock.class.getName() + "-cba"), any())).thenReturn(distributedLock);

    Logger logger = (Logger) LoggerFactory.getLogger(AcquiredDistributedLock.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    try (AcquiredLock lock = mongoPersistentLocker.acquireLock(AcquiredLock.class, "cba", timeout)) {
      Thread.sleep(10);
    } catch (WingsException exception) {
      logger.error("", exception);
    }

    assertThat(listAppender.list).hasSizeGreaterThan(0);
    assertThat(listAppender.list.get(0).getFormattedMessage())
        .matches("The distributed lock " + AcquiredLock.class.getName()
            + "-cba was not released on time. THIS IS VERY BAD!!!, elapsed: \\d+, timeout 1");
  }
}
