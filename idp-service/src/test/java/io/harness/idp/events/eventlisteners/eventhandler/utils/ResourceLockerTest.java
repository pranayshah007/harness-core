/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.eventhandler.utils;

import static io.harness.rule.OwnerRule.DEVESH;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnexpectedException;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.lock.redis.RedisPersistentLocker;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;

@OwnedBy(HarnessTeam.IDP)
public class ResourceLockerTest extends CategoryTest {
  @Mock RedisPersistentLocker redisLocker;

  @InjectMocks ResourceLocker resourceLocker;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }
  public static final String TEST_LOCK_NAME = "test-lock-name";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testAcquireLock() throws InterruptedException {
    doReturn(RedisAcquiredLock.builder().build()).when(redisLocker).tryToAcquireLock(any(), any());
    assertNotNull(resourceLocker.acquireLock(TEST_LOCK_NAME));

    assertNotNull(resourceLocker.acquireLock(TEST_LOCK_NAME, 1));

    doReturn(null).when(redisLocker).tryToAcquireLock(any(), any());
    Exception exception = null;
    try {
      resourceLocker.acquireLock(TEST_LOCK_NAME, 1);
    } catch (UnexpectedException e) {
      exception = e;
    }
    assertNotNull(exception);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReleaseLock() throws InterruptedException {
    doNothing().when(redisLocker).destroy(any());
    RLock rlock = mock(RLock.class);
    RedisAcquiredLock redisAcquiredLock = RedisAcquiredLock.builder().lock(rlock).build();
    resourceLocker.releaseLock(redisAcquiredLock);
    verify(redisLocker).destroy(any());
  }
}
