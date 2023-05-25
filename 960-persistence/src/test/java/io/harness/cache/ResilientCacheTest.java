/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cache;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PersistenceTestBase;
import io.harness.cache.ResilientEntryProcessor.ResilientMutableEntryWrapper;
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
import javax.cache.configuration.Configuration;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class ResilientCacheTest extends PersistenceTestBase {
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
    String key = "abc";
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.put(key, cacheableEntity);
    verify(internalJCache, times(1)).put(key, cacheableEntity);
    CacheableEntity resultEntity = resilientCache.get(key);
    verify(internalJCache, times(1)).get(key);
    assertThat(resultEntity).isEqualTo(cacheableEntity);
    boolean containsKey = resilientCache.containsKey(key);
    assertThat(containsKey).isTrue();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAndPutAll() {
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    Map<String, CacheableEntity> data = new HashMap<>();
    data.put(key1, cacheableEntity1);
    data.put(key2, cacheableEntity2);
    resilientCache.putAll(data);
    Map<String, CacheableEntity> results = resilientCache.getAll(Sets.newHashSet(key1, key2));
    verify(internalJCache, times(1)).getAll(Sets.newHashSet(key1, key2));
    assertThat(results).isEqualTo(data);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAndPut() {
    String key = "abc";
    CacheableEntity oldEntity = mock(CacheableEntity.class);
    resilientCache.put(key, oldEntity);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    CacheableEntity resultEntity = resilientCache.getAndPut(key, cacheableEntity);
    verify(internalJCache, times(1)).getAndPut(key, cacheableEntity);
    assertThat(resultEntity).isEqualTo(oldEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testPutIfAbsent() {
    String key = "abc";
    CacheableEntity oldEntity = mock(CacheableEntity.class);
    resilientCache.put(key, oldEntity);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    CacheableEntity resultEntity = resilientCache.getAndPut(key, cacheableEntity);
    verify(internalJCache, times(1)).getAndPut(key, cacheableEntity);
    assertThat(resultEntity).isEqualTo(oldEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemove() {
    String key = "abc";
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.put(key, cacheableEntity);
    boolean removed = resilientCache.remove(key);
    verify(internalJCache, times(1)).remove(key);
    assertThat(removed).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveOldValue() {
    String key = "abc";
    CacheableEntity oldCacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.put(key, oldCacheableEntity);
    resilientCache.put(key, cacheableEntity);
    boolean removed = resilientCache.remove(key, oldCacheableEntity);
    verify(internalJCache, times(1)).remove(key, oldCacheableEntity);
    assertThat(removed).isEqualTo(false);
    removed = resilientCache.remove(key, cacheableEntity);
    verify(internalJCache, times(1)).remove(key, cacheableEntity);
    assertThat(removed).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAndRemove() {
    String key = "abc";
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.put(key, cacheableEntity);
    CacheableEntity resultCacheableEntity = resilientCache.getAndRemove(key);
    verify(internalJCache, times(1)).getAndRemove(key);
    assertThat(resultCacheableEntity).isEqualTo(cacheableEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testReplaceOldValue() {
    String key = "abc";
    CacheableEntity oldCacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.put(key, oldCacheableEntity);
    boolean replaced = resilientCache.replace(key, oldCacheableEntity, cacheableEntity);
    verify(internalJCache, times(1)).replace(key, oldCacheableEntity, cacheableEntity);
    assertThat(replaced).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testReplace() {
    String key = "abc";
    CacheableEntity oldCacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.put(key, oldCacheableEntity);
    boolean replaced = resilientCache.replace(key, cacheableEntity);
    verify(internalJCache, times(1)).replace(key, cacheableEntity);
    assertThat(replaced).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetAndReplace() {
    String key = "abc";
    CacheableEntity oldCacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.put(key, oldCacheableEntity);
    CacheableEntity resultCacheableEntity = resilientCache.getAndReplace(key, cacheableEntity);
    verify(internalJCache, times(1)).getAndReplace(key, cacheableEntity);
    assertThat(resultCacheableEntity).isEqualTo(oldCacheableEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveAllKeys() {
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(key1, cacheableEntity1);
    resilientCache.put(key2, cacheableEntity2);
    assertThat(resilientCache.containsKey(key1)).isTrue();
    assertThat(resilientCache.containsKey(key2)).isTrue();
    resilientCache.removeAll(Sets.newHashSet(key1, key2));
    verify(internalJCache, times(1)).removeAll(Sets.newHashSet(key1, key2));
    assertThat(resilientCache.containsKey(key1)).isFalse();
    assertThat(resilientCache.containsKey(key2)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testRemoveAll() {
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(key1, cacheableEntity1);
    resilientCache.put(key2, cacheableEntity2);
    assertThat(resilientCache.containsKey(key1)).isTrue();
    assertThat(resilientCache.containsKey(key2)).isTrue();
    resilientCache.removeAll();
    verify(internalJCache, times(1)).removeAll();
    assertThat(resilientCache.containsKey(key1)).isFalse();
    assertThat(resilientCache.containsKey(key2)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testClear() {
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(key1, cacheableEntity1);
    resilientCache.put(key2, cacheableEntity2);
    assertThat(resilientCache.containsKey(key1)).isTrue();
    assertThat(resilientCache.containsKey(key2)).isTrue();
    resilientCache.clear();
    verify(internalJCache, times(1)).clear();
    assertThat(resilientCache.containsKey(key1)).isFalse();
    assertThat(resilientCache.containsKey(key2)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testGetConfiguration() {
    ResilientCacheConfigurationWrapper cacheConfiguration =
        resilientCache.getConfiguration(ResilientCacheConfigurationWrapper.class);
    assertThat(cacheConfiguration.getInternalCacheConfig()).isNotNull();
    assertThat(cacheConfiguration.getKeyType()).isEqualTo(String.class);
    assertThat(cacheConfiguration.getValueType()).isEqualTo(CacheableEntity.class);
    assertThat(cacheConfiguration.isStoreByValue()).isEqualTo(false);
    verify(internalJCache, times(1)).getConfiguration(Configuration.class);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testInvoke() {
    String key = "abc";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(key, cacheableEntity1);
    EntryProcessor<String, CacheableEntity, Boolean> entryProcessor = (entry, arguments) -> {
      assertThat(entry.exists()).isTrue();
      assertThat(entry.getKey()).isEqualTo(key);
      assertThat(entry.getValue()).isEqualTo(cacheableEntity1);
      entry.setValue(cacheableEntity2);
      entry.remove();
      entry.unwrap(ResilientMutableEntryWrapper.class);
      return entry.exists();
    };
    assertThat(resilientCache.invoke(key, entryProcessor)).isFalse();
    verify(internalJCache, times(1)).invoke(eq(key), any(EntryProcessor.class));
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testInvokeAll() {
    String key = "abc";
    EntryProcessor<String, CacheableEntity, Boolean> entryProcessor = mock(EntryProcessor.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    resilientCache.invokeAll(Sets.newHashSet(key), entryProcessor);
    verify(internalJCache, times(1)).invokeAll(eq(Sets.newHashSet(key)), any(EntryProcessor.class));
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
  public void testRegisterCacheEntryListener() throws InterruptedException {
    String key = "abc";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    CacheEntryListenerConfiguration<String, CacheableEntity> cacheableEntityCacheEntryListenerConfiguration =
        mock(CacheEntryListenerConfiguration.class);
    when(cacheableEntityCacheEntryListenerConfiguration.isSynchronous()).thenReturn(true);
    Factory<CacheEntryListener<? super String, ? super CacheableEntity>> cacheFactory = mock(Factory.class);
    CacheEntryCreatedListener cacheListener1 = mock(CacheEntryCreatedListener.class);
    CacheEntryUpdatedListener cacheListener2 = mock(CacheEntryUpdatedListener.class);
    CacheEntryRemovedListener cacheListener3 = mock(CacheEntryRemovedListener.class);
    CacheEntryExpiredListener cacheListener4 = mock(CacheEntryExpiredListener.class);
    when(cacheFactory.create())
        .thenReturn(cacheListener1)
        .thenReturn(cacheListener2)
        .thenReturn(cacheListener3)
        .thenReturn(cacheListener4);
    Factory<CacheEntryEventFilter<? super String, ? super CacheableEntity>> cacheEntryEventFilterFactory =
        mock(Factory.class);
    CacheEntryEventFilter cacheEntryEventFilter = mock(CacheEntryEventFilter.class);
    when(cacheEntryEventFilter.evaluate(any())).thenReturn(true);
    when(cacheEntryEventFilterFactory.create()).thenReturn(cacheEntryEventFilter);
    when(cacheableEntityCacheEntryListenerConfiguration.getCacheEntryEventFilterFactory())
        .thenReturn(cacheEntryEventFilterFactory);
    when(cacheableEntityCacheEntryListenerConfiguration.getCacheEntryListenerFactory()).thenReturn(cacheFactory);
    resilientCache.registerCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration);
    resilientCache.registerCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration);
    resilientCache.registerCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration);
    resilientCache.registerCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration);
    verify(internalJCache, times(4)).registerCacheEntryListener(any());
    resilientCache.put(key, cacheableEntity1);
    resilientCache.replace(key, cacheableEntity2);
    resilientCache.remove(key);
    ArgumentCaptor<Iterable> eventsCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(cacheListener1, times(1)).onCreated(eventsCaptor.capture());
    verify(cacheListener2, times(1)).onUpdated(any());
    verify(cacheListener3, times(1)).onRemoved(any());
    Iterable<CacheEntryEvent<String, CacheableEntity>> iterable = eventsCaptor.getValue();
    assertThat(iterable.iterator().hasNext()).isTrue();
    CacheEntryEvent<String, CacheableEntity> cacheEntryEvent = iterable.iterator().next();
    assertThat(cacheEntryEvent.getSource()).isEqualTo(resilientCache);
    assertThat(cacheEntryEvent.getKey()).isEqualTo(key);
    assertThat(cacheEntryEvent.getValue()).isEqualTo(cacheableEntity1);
    assertThat(cacheEntryEvent.getOldValue()).isNull();
    assertThat(cacheEntryEvent.isOldValueAvailable()).isFalse();
    assertThat(cacheEntryEvent.unwrap(ResilientCacheEntryEventWrapper.class)).isNotNull();
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
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(key1, cacheableEntity1);
    resilientCache.put(key2, cacheableEntity2);
    CompletionListener completionListener = mock(CompletionListener.class);
    resilientCache.loadAll(Sets.newHashSet(key1, key2), true, completionListener);
    verify(internalJCache, times(1)).loadAll(Sets.newHashSet(key1, key2), true, completionListener);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testIterator() {
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    resilientCache.put(key1, cacheableEntity1);
    resilientCache.put(key2, cacheableEntity2);
    Iterator<Entry<String, CacheableEntity>> iterator = resilientCache.iterator();
    verify(internalJCache, times(1)).iterator();
    Entry<String, CacheableEntity> entityEntry = iterator.next();
    assertThat(entityEntry.getValue()).isIn(cacheableEntity1, cacheableEntity2);
    assertThat(entityEntry.getKey()).isIn(key1, key2);
    assertThat(iterator.hasNext()).isTrue();
  }
}
