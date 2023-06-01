/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cache;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static java.util.Set.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PersistenceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.github.benmanes.caffeine.jcache.CacheProxy;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.cache.Cache;
import javax.cache.Cache.Entry;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.MutableEntry;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.redisson.client.RedisOutOfMemoryException;

public class ResilientCacheTest extends PersistenceTestBase {
  public static final String KEY = "key1";
  public static CacheableEntity VALUE = mock(CacheableEntity.class);
  private RedisOutOfMemoryException exception = new RedisOutOfMemoryException("Redis OOM");
  ;

  private static class CacheableEntity {
    private String id;
    private String name;
  }

  @Inject HarnessCacheManager harnessCacheManager;
  private static final String CACHE_NAME = "testResilientCache";

  private Cache<String, CacheableEntity> internalJCache;
  private ResilientCache<String, CacheableEntity> resilientCache;

  @Before
  public void setup() {
    this.internalJCache = spy(
        harnessCacheManager.getCache(CACHE_NAME, String.class, CacheableEntity.class, EternalExpiryPolicy.factoryOf()));
    this.resilientCache = new ResilientCache<>(this.internalJCache);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetPutAndContains() {
    resilientCache.put(KEY, VALUE);
    verify(internalJCache, times(1)).put(KEY, VALUE);
    CacheableEntity resultEntity = resilientCache.get(KEY);
    verify(internalJCache, times(1)).get(KEY);
    assertThat(resultEntity).isEqualTo(VALUE);
    boolean containsKey = resilientCache.containsKey(KEY);
    assertThat(containsKey).isTrue();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAndPutAll() {
    String key2 = "key2";
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    Map<String, CacheableEntity> data = new HashMap<>();
    data.put(KEY, VALUE);
    data.put(key2, cacheableEntity2);
    resilientCache.putAll(data);
    Map<String, CacheableEntity> results = resilientCache.getAll(Sets.newHashSet(KEY, key2));
    verify(internalJCache, times(1)).getAll(Sets.newHashSet(KEY, key2));
    assertThat(results).isEqualTo(data);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAndPut() {
    CacheableEntity oldEntity = mock(CacheableEntity.class);
    resilientCache.put(KEY, oldEntity);
    CacheableEntity resultEntity = resilientCache.getAndPut(KEY, VALUE);
    verify(internalJCache, times(1)).getAndPut(KEY, VALUE);
    assertThat(resultEntity).isEqualTo(oldEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPutIfAbsent() {
    CacheableEntity oldEntity = mock(CacheableEntity.class);
    resilientCache.put(KEY, oldEntity);
    CacheableEntity resultEntity = resilientCache.getAndPut(KEY, VALUE);
    verify(internalJCache, times(1)).getAndPut(KEY, VALUE);
    assertThat(resultEntity).isEqualTo(oldEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemove() {
    resilientCache.put(KEY, VALUE);
    boolean removed = resilientCache.remove(KEY);
    verify(internalJCache, times(1)).remove(KEY);
    assertThat(removed).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveOldValue() {
    CacheableEntity oldCacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.put(KEY, oldCacheableEntity);
    resilientCache.put(KEY, cacheableEntity);
    boolean removed = resilientCache.remove(KEY, oldCacheableEntity);
    verify(internalJCache, times(1)).remove(KEY, oldCacheableEntity);
    assertThat(removed).isEqualTo(false);
    removed = resilientCache.remove(KEY, cacheableEntity);
    verify(internalJCache, times(1)).remove(KEY, cacheableEntity);
    assertThat(removed).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAndRemove() {
    resilientCache.put(KEY, VALUE);
    CacheableEntity resultCacheableEntity = resilientCache.getAndRemove(KEY);
    verify(internalJCache, times(1)).getAndRemove(KEY);
    assertThat(resultCacheableEntity).isEqualTo(VALUE);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testReplaceOldValue() {
    CacheableEntity oldCacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.put(KEY, oldCacheableEntity);
    boolean replaced = resilientCache.replace(KEY, oldCacheableEntity, cacheableEntity);
    verify(internalJCache, times(1)).replace(KEY, oldCacheableEntity, cacheableEntity);
    assertThat(replaced).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testReplace() {
    CacheableEntity oldCacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.put(KEY, oldCacheableEntity);
    boolean replaced = resilientCache.replace(KEY, cacheableEntity);
    verify(internalJCache, times(1)).replace(KEY, cacheableEntity);
    assertThat(replaced).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAndReplace() {
    CacheableEntity oldCacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.put(KEY, oldCacheableEntity);
    CacheableEntity resultCacheableEntity = resilientCache.getAndReplace(KEY, cacheableEntity);
    verify(internalJCache, times(1)).getAndReplace(KEY, cacheableEntity);
    assertThat(resultCacheableEntity).isEqualTo(oldCacheableEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveAllKeys() {
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(KEY, cacheableEntity1);
    resilientCache.put(key2, cacheableEntity2);
    assertThat(resilientCache.containsKey(KEY)).isTrue();
    assertThat(resilientCache.containsKey(key2)).isTrue();
    resilientCache.removeAll(Sets.newHashSet(KEY, key2));
    verify(internalJCache, times(1)).removeAll(Sets.newHashSet(KEY, key2));
    assertThat(resilientCache.containsKey(KEY)).isFalse();
    assertThat(resilientCache.containsKey(key2)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveAll() {
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(KEY, cacheableEntity1);
    resilientCache.put(key2, cacheableEntity2);
    assertThat(resilientCache.containsKey(KEY)).isTrue();
    assertThat(resilientCache.containsKey(key2)).isTrue();
    resilientCache.removeAll();
    verify(internalJCache, times(1)).removeAll();
    assertThat(resilientCache.containsKey(KEY)).isFalse();
    assertThat(resilientCache.containsKey(key2)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testClear() {
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(KEY, cacheableEntity1);
    resilientCache.put(key2, cacheableEntity2);
    assertThat(resilientCache.containsKey(KEY)).isTrue();
    assertThat(resilientCache.containsKey(key2)).isTrue();
    resilientCache.clear();
    verify(internalJCache, times(1)).clear();
    assertThat(resilientCache.containsKey(KEY)).isFalse();
    assertThat(resilientCache.containsKey(key2)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testGetConfiguration() {
    resilientCache.getConfiguration(CompleteConfiguration.class);
    verify(internalJCache, times(1)).getConfiguration(CompleteConfiguration.class);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testInvoke() {
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(KEY, cacheableEntity1);
    EntryProcessor<String, CacheableEntity, Boolean> entryProcessor = (entry, arguments) -> {
      assertThat(entry.exists()).isTrue();
      assertThat(entry.getKey()).isEqualTo(KEY);
      assertThat(entry.getValue()).isEqualTo(cacheableEntity1);
      entry.setValue(cacheableEntity2);
      entry.remove();
      entry.unwrap(MutableEntry.class);
      return entry.exists();
    };
    resilientCache.invoke(KEY, entryProcessor);
    verify(internalJCache, times(1)).invoke(KEY, entryProcessor);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testInvokeAll() {
    EntryProcessor<String, CacheableEntity, String> entryProcessor = (mutableEntry, objects) -> mutableEntry.getKey();
    resilientCache.invokeAll(Sets.newHashSet(KEY), entryProcessor);
    verify(internalJCache, times(1)).invokeAll(eq(Sets.newHashSet(KEY)), eq(entryProcessor));
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetName() {
    String cacheName = resilientCache.getName();
    verify(internalJCache, times(1)).getName();
    assertThat(cacheName).contains(CACHE_NAME);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetCacheManager() {
    resilientCache.getCacheManager();
    verify(internalJCache, times(1)).getCacheManager();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testClose() {
    resilientCache.close();
    verify(internalJCache, times(1)).close();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testIsClosed() {
    resilientCache.isClosed();
    verify(internalJCache, times(1)).isClosed();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUnwrap() {
    resilientCache.unwrap(CacheProxy.class);
    verify(internalJCache, times(1)).unwrap(CacheProxy.class);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void testRegisterCacheEntryListener() {
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    CacheEntryListenerConfiguration<String, CacheableEntity> cacheableEntityCacheEntryListenerConfiguration1 =
        mock(CacheEntryListenerConfiguration.class);
    CacheEntryListenerConfiguration<String, CacheableEntity> cacheableEntityCacheEntryListenerConfiguration2 =
        mock(CacheEntryListenerConfiguration.class);
    CacheEntryListenerConfiguration<String, CacheableEntity> cacheableEntityCacheEntryListenerConfiguration3 =
        mock(CacheEntryListenerConfiguration.class);
    CacheEntryListenerConfiguration<String, CacheableEntity> cacheableEntityCacheEntryListenerConfiguration4 =
        mock(CacheEntryListenerConfiguration.class);
    when(cacheableEntityCacheEntryListenerConfiguration1.isSynchronous()).thenReturn(true);
    when(cacheableEntityCacheEntryListenerConfiguration2.isSynchronous()).thenReturn(true);
    when(cacheableEntityCacheEntryListenerConfiguration3.isSynchronous()).thenReturn(true);
    when(cacheableEntityCacheEntryListenerConfiguration4.isSynchronous()).thenReturn(true);
    Factory<CacheEntryListener<? super String, ? super CacheableEntity>> cacheFactory = mock(Factory.class);
    CacheEntryCreatedListener cacheListener1 = mock(CacheEntryCreatedListener.class);
    CacheEntryUpdatedListener cacheListener2 = mock(CacheEntryUpdatedListener.class);
    CacheEntryRemovedListener cacheListener3 = mock(CacheEntryRemovedListener.class);
    when(cacheFactory.create()).thenReturn(cacheListener1).thenReturn(cacheListener2).thenReturn(cacheListener3);
    Factory<CacheEntryEventFilter<? super String, ? super CacheableEntity>> cacheEntryEventFilterFactory =
        mock(Factory.class);
    CacheEntryEventFilter cacheEntryEventFilter = mock(CacheEntryEventFilter.class);
    when(cacheEntryEventFilter.evaluate(any())).thenReturn(true);
    when(cacheEntryEventFilterFactory.create()).thenReturn(cacheEntryEventFilter);
    when(cacheableEntityCacheEntryListenerConfiguration1.getCacheEntryEventFilterFactory())
        .thenReturn(cacheEntryEventFilterFactory);
    when(cacheableEntityCacheEntryListenerConfiguration1.getCacheEntryListenerFactory()).thenReturn(cacheFactory);
    when(cacheableEntityCacheEntryListenerConfiguration2.getCacheEntryListenerFactory()).thenReturn(cacheFactory);
    when(cacheableEntityCacheEntryListenerConfiguration3.getCacheEntryListenerFactory()).thenReturn(cacheFactory);
    resilientCache.registerCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration1);
    resilientCache.registerCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration2);
    resilientCache.registerCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration3);
    verify(internalJCache, times(3)).registerCacheEntryListener(any());
    resilientCache.put(KEY, cacheableEntity1);
    resilientCache.replace(KEY, cacheableEntity2);
    resilientCache.remove(KEY);
    ArgumentCaptor<Iterable> eventsCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(cacheListener1, times(1)).onCreated(eventsCaptor.capture());
    verify(cacheListener2, times(1)).onUpdated(any());
    verify(cacheListener3, times(1)).onRemoved(any());
    Iterable<CacheEntryEvent<String, CacheableEntity>> iterable = eventsCaptor.getValue();
    assertThat(iterable.iterator().hasNext()).isTrue();
    CacheEntryEvent<String, CacheableEntity> cacheEntryEvent = iterable.iterator().next();
    assertThat(cacheEntryEvent.getSource()).isNotNull();
    assertThat(cacheEntryEvent.getKey()).isEqualTo(KEY);
    assertThat(cacheEntryEvent.getValue()).isEqualTo(cacheableEntity1);
    assertThat(cacheEntryEvent.getOldValue()).isNull();
    assertThat(cacheEntryEvent.isOldValueAvailable()).isFalse();
    assertThat(cacheEntryEvent.unwrap(CacheEntryEvent.class)).isNotNull();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testDeregisterCacheEntryListener() {
    CacheEntryListenerConfiguration<String, CacheableEntity> cacheableEntityCacheEntryListenerConfiguration =
        mock(CacheEntryListenerConfiguration.class);
    resilientCache.deregisterCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration);
    verify(internalJCache, times(1)).deregisterCacheEntryListener(any());
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testLoadAll() {
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(KEY, cacheableEntity1);
    resilientCache.put(key2, cacheableEntity2);
    CompletionListener completionListener = mock(CompletionListener.class);
    resilientCache.loadAll(Sets.newHashSet(KEY, key2), true, completionListener);
    verify(internalJCache, times(1)).loadAll(Sets.newHashSet(KEY, key2), true, completionListener);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testIterator() {
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(KEY, cacheableEntity1);
    resilientCache.put(key2, cacheableEntity2);
    Iterator<Entry<String, CacheableEntity>> iterator = resilientCache.iterator();
    verify(internalJCache, times(1)).iterator();
    Entry<String, CacheableEntity> entityEntry = iterator.next();
    assertThat(entityEntry.getValue()).isIn(cacheableEntity1, cacheableEntity2);
    assertThat(entityEntry.getKey()).isIn(KEY, key2);
    assertThat(iterator.hasNext()).isTrue();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPutShouldHandleExceptionGracefully() {
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    doThrow(exception).when(internalJCache).put(any(), any());
    resilientCache.put(KEY, cacheableEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).get(any());
    assertThat(resilientCache.get(KEY)).isNull();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAllShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).getAll(anySet());
    assertThat(resilientCache.getAll(of(KEY))).isEmpty();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testLoadAllShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).loadAll(anySet(), anyBoolean(), any());
    CompletionListener completionListener = mock(CompletionListener.class);
    resilientCache.loadAll(of(KEY), true, completionListener);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAndPutShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).getAndPut(any(), any());
    assertThat(resilientCache.getAndPut(KEY, VALUE)).isNull();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPutAllShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).putAll(any());
    resilientCache.putAll(new HashMap<>());
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPutIfAbsentShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).putIfAbsent(any(), any());
    assertThat(resilientCache.putIfAbsent(KEY, VALUE)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).remove(any());
    assertThat(resilientCache.remove(KEY)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveWithKeyValueShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).remove(any(), any());
    assertThat(resilientCache.remove(KEY, VALUE)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAndRemoveShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).getAndRemove(any());
    assertThat(resilientCache.getAndRemove(KEY)).isNull();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testReplaceShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).replace(any(), any());
    assertThat(resilientCache.replace(KEY, VALUE)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testReplaceWithValueShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).replace(any(), any(), any());
    assertThat(resilientCache.replace(KEY, VALUE, VALUE)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveAllByKeysShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).removeAll(anySet());
    resilientCache.removeAll(of(KEY));
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveAllShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).removeAll();
    resilientCache.removeAll();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testClearShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).clear();
    resilientCache.clear();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testInvokeShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).invoke(any(), any());
    EntryProcessor<String, CacheableEntity, String> entryProcessor = (mutableEntry, objects) -> mutableEntry.getKey();
    assertThat(resilientCache.invoke(KEY, entryProcessor)).isNull();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testInvokeAllShouldHandleExceptionGracefully() {
    EntryProcessor<String, CacheableEntity, String> entryProcessor = (mutableEntry, objects) -> mutableEntry.getKey();
    doThrow(exception).when(internalJCache).invokeAll(anySet(), any());
    assertThat(resilientCache.invokeAll(of(KEY), entryProcessor)).isEmpty();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCloseShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).close();
    resilientCache.close();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testIsClosedShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).isClosed();
    assertThat(resilientCache.isClosed()).isTrue();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testUnwrapShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).unwrap(any());
    assertThat(resilientCache.unwrap(CacheProxy.class)).isNull();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRegisterCacheEntryListenerShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).registerCacheEntryListener(any());
    resilientCache.unwrap(CacheProxy.class);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDeregisterCacheEntryListenerShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).deregisterCacheEntryListener(any());
    CacheEntryListenerConfiguration<String, CacheableEntity> cacheEntryListenerConfiguration =
        mock(CacheEntryListenerConfiguration.class);
    resilientCache.deregisterCacheEntryListener(cacheEntryListenerConfiguration);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testIteratorShouldHandleExceptionGracefully() {
    doThrow(exception).when(internalJCache).iterator();
    assertThat(resilientCache.iterator()).isNull();
  }
}
