/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.redis;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.JENNY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateCache;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DelegateHeartBeatSyncFromRedisTest extends WingsBaseTest {
  @Inject @InjectMocks private DelegateHeartBeatSyncFromRedis delegateHeartBeatSyncFromRedis;
  @Inject private HPersistence persistence;
  @Mock DelegateCache delegateCache;

  private static final long NEW_HEARTBEAT_VALUE = 1691116575000L;
  private static final long HEARTBEAT_VALUE = 1691124412000L;

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testMongoHeartBeatSync() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegateBuilder(accountId);
    persistence.save(delegate1);
    Delegate delegate2 = createDelegateBuilder(accountId);
    persistence.save(delegate2);
    Delegate delegate3 = createDelegateBuilder(accountId);
    persistence.save(delegate3);
    Delegate delegate4 = createDelegateBuilder(accountId);
    persistence.save(delegate4);

    // update cache with new HB
    delegate1.setLastHeartBeat(NEW_HEARTBEAT_VALUE);
    delegate2.setLastHeartBeat(NEW_HEARTBEAT_VALUE);
    delegate3.setLastHeartBeat(NEW_HEARTBEAT_VALUE);
    delegate4.setLastHeartBeat(NEW_HEARTBEAT_VALUE);
    when(delegateCache.get(accountId, delegate1.getUuid())).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid())).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid())).thenReturn(delegate3);
    when(delegateCache.get(accountId, delegate4.getUuid())).thenReturn(delegate4);

    delegateHeartBeatSyncFromRedis.startSync();

    Delegate delegate1Updated = persistence.get(Delegate.class, delegate1.getUuid());
    assertThat(delegate1Updated.getLastHeartBeat()).isEqualTo(NEW_HEARTBEAT_VALUE);
    Delegate delegate2Updated = persistence.get(Delegate.class, delegate2.getUuid());
    assertThat(delegate2Updated.getLastHeartBeat()).isEqualTo(NEW_HEARTBEAT_VALUE);
    Delegate delegate3Updated = persistence.get(Delegate.class, delegate3.getUuid());
    assertThat(delegate3Updated.getLastHeartBeat()).isEqualTo(NEW_HEARTBEAT_VALUE);
    Delegate delegate4Updated = persistence.get(Delegate.class, delegate4.getUuid());
    assertThat(delegate4Updated.getLastHeartBeat()).isEqualTo(NEW_HEARTBEAT_VALUE);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testMongoHeartBeatSyncWhenNotAvailableInCache() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateBuilder(accountId);
    delegate.setLastHeartBeat(HEARTBEAT_VALUE);
    persistence.save(delegate);
    delegateHeartBeatSyncFromRedis.startSync();
    Delegate delegateUpdated = persistence.get(Delegate.class, delegate.getUuid());
    assertThat(delegateUpdated.getLastHeartBeat()).isEqualTo(HEARTBEAT_VALUE);
  }

  private Delegate createDelegateBuilder(String accountId) {
    return Delegate.builder()
        .accountId(accountId)
        .hostName("localhost")
        .delegateName("testDelegateName")
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis())
        .build();
  }
}
