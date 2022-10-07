/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import javax.cache.Cache;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.EventType;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.redisson.jcache.JCacheEntryEvent;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationLogCacheListenerTest extends OrchestrationTestBase {
  @Mock Producer producer;
  @Mock Cache cache;
  @InjectMocks OrchestrationLogCacheListener orchestrationLogCacheListener;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testEvaluate() {
    boolean b = orchestrationLogCacheListener.evaluate(new JCacheEntryEvent<>(cache, EventType.CREATED, 0L, 0L));
    assertFalse(b);
    b = orchestrationLogCacheListener.evaluate(new JCacheEntryEvent<>(cache, EventType.EXPIRED, 0L, 0L));
    assertTrue(b);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testOnExpired() {
    List<CacheEntryEvent<? extends String, ? extends Long>> iterable = new ArrayList<>();
    iterable.add(new JCacheEntryEvent<>(cache, EventType.CREATED, "key1", 0L));
    orchestrationLogCacheListener.onExpired(iterable);
    verify(producer, times(0)).send(any());
    iterable.add(new JCacheEntryEvent<>(cache, EventType.CREATED, "key2", 10L));
    ArgumentCaptor<Message> argumentCaptor = ArgumentCaptor.forClass(Message.class);
    orchestrationLogCacheListener.onExpired(iterable);
    verify(producer, times(1)).send(argumentCaptor.capture());
    OrchestrationLogEvent orchestrationLogEvent = OrchestrationLogEvent.newBuilder().setPlanExecutionId("key2").build();
    Message message = Message.newBuilder()
                          .putAllMetadata(ImmutableMap.of("planExecutionId", "key2"))
                          .setData(orchestrationLogEvent.toByteString())
                          .build();
    assertThat(argumentCaptor.getValue()).isEqualTo(message);
  }
}
