/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cache;

import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PersistenceTestBase;
import io.harness.cache.VersionedEntryProcessor.VersionedMutableEntryWrapper;
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
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class VersionedCacheTest extends PersistenceTestBase {
  private static class CacheableEntity {
    private String id;
    private String name;
  }

  @Inject HarnessCacheManager harnessCacheManager;
  private static final String VERSION = "1997";
  private static final String CACHE_NAME = "testVersioningCache";
  private Cache<VersionedKey<String>, CacheableEntity> internalJCache;
  private VersionedCache<String, CacheableEntity> versionedCache;

  @Before
  public void setup() {
    this.internalJCache = spy(harnessCacheManager.getCache(
        CACHE_NAME, (Class) VersionedKey.class, CacheableEntity.class, EternalExpiryPolicy.factoryOf()));
    this.versionedCache = new VersionedCache<>(this.internalJCache, VERSION);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetPutAndContains() {
    String key = "abc";
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    versionedCache.put(key, cacheableEntity);
    verify(internalJCache, times(1)).put(new VersionedKey<>(key, VERSION), cacheableEntity);
    CacheableEntity resultEntity = versionedCache.get(key);
    verify(internalJCache, times(1)).get(new VersionedKey<>(key, VERSION));
    assertThat(resultEntity).isEqualTo(cacheableEntity);
    boolean containsKey = versionedCache.containsKey(key);
    assertThat(containsKey).isTrue();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetAndPutAll() {
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    Map<String, CacheableEntity> data = new HashMap<>();
    data.put(key1, cacheableEntity1);
    data.put(key2, cacheableEntity2);
    versionedCache.putAll(data);
    Map<String, CacheableEntity> results = versionedCache.getAll(Sets.newHashSet(key1, key2));
    verify(internalJCache, times(1))
        .getAll(Sets.newHashSet(new VersionedKey<>(key1, VERSION), new VersionedKey<>(key2, VERSION)));
    assertThat(results).isEqualTo(data);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetAndPut() {
    String key = "abc";
    CacheableEntity oldEntity = mock(CacheableEntity.class);
    versionedCache.put(key, oldEntity);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    CacheableEntity resultEntity = versionedCache.getAndPut(key, cacheableEntity);
    verify(internalJCache, times(1)).getAndPut(new VersionedKey<>(key, VERSION), cacheableEntity);
    assertThat(resultEntity).isEqualTo(oldEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testPutIfAbsent() {
    String key = "abc";
    CacheableEntity oldEntity = mock(CacheableEntity.class);
    versionedCache.put(key, oldEntity);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    CacheableEntity resultEntity = versionedCache.getAndPut(key, cacheableEntity);
    verify(internalJCache, times(1)).getAndPut(new VersionedKey<>(key, VERSION), cacheableEntity);
    assertThat(resultEntity).isEqualTo(oldEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRemove() {
    String key = "abc";
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    versionedCache.put(key, cacheableEntity);
    boolean removed = versionedCache.remove(key);
    verify(internalJCache, times(1)).remove(new VersionedKey<>(key, VERSION));
    assertThat(removed).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRemoveOldValue() {
    String key = "abc";
    CacheableEntity oldcacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    versionedCache.put(key, oldcacheableEntity);
    versionedCache.put(key, cacheableEntity);
    boolean removed = versionedCache.remove(key, oldcacheableEntity);
    verify(internalJCache, times(1)).remove(new VersionedKey<>(key, VERSION), oldcacheableEntity);
    assertThat(removed).isEqualTo(false);
    removed = versionedCache.remove(key, cacheableEntity);
    verify(internalJCache, times(1)).remove(new VersionedKey<>(key, VERSION), cacheableEntity);
    assertThat(removed).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetAndRemove() {
    String key = "abc";
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    versionedCache.put(key, cacheableEntity);
    CacheableEntity resultCacheableEntity = versionedCache.getAndRemove(key);
    verify(internalJCache, times(1)).getAndRemove(new VersionedKey<>(key, VERSION));
    assertThat(resultCacheableEntity).isEqualTo(cacheableEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testReplaceOldValue() {
    String key = "abc";
    CacheableEntity oldCacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    versionedCache.put(key, oldCacheableEntity);
    boolean replaced = versionedCache.replace(key, oldCacheableEntity, cacheableEntity);
    verify(internalJCache, times(1)).replace(new VersionedKey<>(key, VERSION), oldCacheableEntity, cacheableEntity);
    assertThat(replaced).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testReplace() {
    String key = "abc";
    CacheableEntity oldCacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    versionedCache.put(key, oldCacheableEntity);
    boolean replaced = versionedCache.replace(key, cacheableEntity);
    verify(internalJCache, times(1)).replace(new VersionedKey<>(key, VERSION), cacheableEntity);
    assertThat(replaced).isEqualTo(true);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetAndReplace() {
    String key = "abc";
    CacheableEntity oldCacheableEntity = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    versionedCache.put(key, oldCacheableEntity);
    CacheableEntity resultCacheableEntity = versionedCache.getAndReplace(key, cacheableEntity);
    verify(internalJCache, times(1)).getAndReplace(new VersionedKey<>(key, VERSION), cacheableEntity);
    assertThat(resultCacheableEntity).isEqualTo(oldCacheableEntity);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRemoveAllKeys() {
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    versionedCache.put(key1, cacheableEntity1);
    versionedCache.put(key2, cacheableEntity2);
    assertThat(versionedCache.containsKey(key1)).isTrue();
    assertThat(versionedCache.containsKey(key2)).isTrue();
    versionedCache.removeAll(Sets.newHashSet(key1, key2));
    verify(internalJCache, times(1))
        .removeAll(Sets.newHashSet(new VersionedKey<>(key1, VERSION), new VersionedKey<>(key2, VERSION)));
    assertThat(versionedCache.containsKey(key1)).isFalse();
    assertThat(versionedCache.containsKey(key2)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRemoveAll() {
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    versionedCache.put(key1, cacheableEntity1);
    versionedCache.put(key2, cacheableEntity2);
    assertThat(versionedCache.containsKey(key1)).isTrue();
    assertThat(versionedCache.containsKey(key2)).isTrue();
    versionedCache.removeAll();
    verify(internalJCache, times(1)).removeAll();
    assertThat(versionedCache.containsKey(key1)).isFalse();
    assertThat(versionedCache.containsKey(key2)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testClear() {
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    versionedCache.put(key1, cacheableEntity1);
    versionedCache.put(key2, cacheableEntity2);
    assertThat(versionedCache.containsKey(key1)).isTrue();
    assertThat(versionedCache.containsKey(key2)).isTrue();
    versionedCache.clear();
    verify(internalJCache, times(1)).clear();
    assertThat(versionedCache.containsKey(key1)).isFalse();
    assertThat(versionedCache.containsKey(key2)).isFalse();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testGetConfiguration() {
    VersionedCacheConfigurationWrapper cacheConfiguration =
        versionedCache.getConfiguration(VersionedCacheConfigurationWrapper.class);
    assertThat(cacheConfiguration.getInternalCacheConfig()).isNotNull();
    assertThat(cacheConfiguration.getVersion()).isEqualTo(VERSION);
    assertThat(cacheConfiguration.getKeyType()).isEqualTo(Object.class);
    assertThat(cacheConfiguration.getValueType()).isEqualTo(CacheableEntity.class);
    assertThat(cacheConfiguration.isStoreByValue()).isEqualTo(false);
    verify(internalJCache, times(1)).getConfiguration(Configuration.class);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testInvoke() {
    String key = "abc";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    versionedCache.put(key, cacheableEntity1);
    EntryProcessor<String, CacheableEntity, Boolean> entryProcessor =
        new EntryProcessor<String, CacheableEntity, Boolean>() {
          @Override
          public Boolean process(MutableEntry<String, CacheableEntity> entry, Object... arguments)
              throws EntryProcessorException {
            assertThat(entry.exists()).isTrue();
            assertThat(entry.getKey()).isEqualTo(key);
            assertThat(entry.getValue()).isEqualTo(cacheableEntity1);
            entry.setValue(cacheableEntity2);
            entry.remove();
            entry.unwrap(VersionedMutableEntryWrapper.class);
            return entry.exists();
          }
        };
    assertThat(versionedCache.invoke(key, entryProcessor)).isFalse();
    verify(internalJCache, times(1)).invoke(eq(new VersionedKey<>(key, VERSION)), any(EntryProcessor.class));
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testInvokeAll() {
    String key = "abc";
    EntryProcessor<String, CacheableEntity, Boolean> entryProcessor = mock(EntryProcessor.class);
    CacheableEntity cacheableEntity = mock(CacheableEntity.class);
    versionedCache.invokeAll(Sets.newHashSet(key), entryProcessor);
    verify(internalJCache, times(1))
        .invokeAll(eq(Sets.newHashSet(new VersionedKey<>(key, VERSION))), any(EntryProcessor.class));
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetName() {
    String cacheName = versionedCache.getName();
    verify(internalJCache, times(1)).getName();
    assertThat(cacheName).contains(CACHE_NAME);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetCacheManager() {
    versionedCache.getCacheManager();
    verify(internalJCache, times(1)).getCacheManager();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testClose() {
    versionedCache.close();
    verify(internalJCache, times(1)).close();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testIsClosed() {
    versionedCache.isClosed();
    verify(internalJCache, times(1)).isClosed();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUnwrap() {
    versionedCache.unwrap(CacheProxy.class);
    verify(internalJCache, times(1)).unwrap(CacheProxy.class);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
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
    versionedCache.registerCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration);
    versionedCache.registerCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration);
    versionedCache.registerCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration);
    versionedCache.registerCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration);
    verify(internalJCache, times(4)).registerCacheEntryListener(any());
    versionedCache.put(key, cacheableEntity1);
    versionedCache.replace(key, cacheableEntity2);
    versionedCache.remove(key);
    ArgumentCaptor<Iterable> eventsCaptor = ArgumentCaptor.forClass(Iterable.class);
    verify(cacheListener1, times(1)).onCreated(eventsCaptor.capture());
    verify(cacheListener2, times(1)).onUpdated(any());
    verify(cacheListener3, times(1)).onRemoved(any());
    Iterable<CacheEntryEvent<String, CacheableEntity>> iterable = eventsCaptor.getValue();
    assertThat(iterable.iterator().hasNext()).isTrue();
    CacheEntryEvent<String, CacheableEntity> cacheEntryEvent = iterable.iterator().next();
    assertThat(cacheEntryEvent.getSource()).isEqualTo(versionedCache);
    assertThat(cacheEntryEvent.getKey()).isEqualTo(key);
    assertThat(cacheEntryEvent.getValue()).isEqualTo(cacheableEntity1);
    assertThat(cacheEntryEvent.getOldValue()).isNull();
    assertThat(cacheEntryEvent.isOldValueAvailable()).isFalse();
    assertThat(cacheEntryEvent.unwrap(VersionedCacheEntryEventWrapper.class)).isNotNull();
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testDeregisterCacheEntryListener() {
    CacheEntryListenerConfiguration<String, CacheableEntity> cacheableEntityCacheEntryListenerConfiguration =
        mock(CacheEntryListenerConfiguration.class);
    versionedCache.deregisterCacheEntryListener(cacheableEntityCacheEntryListenerConfiguration);
    verify(internalJCache, times(1)).deregisterCacheEntryListener(any());
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testLoadAll() {
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    versionedCache.put(key1, cacheableEntity1);
    versionedCache.put(key2, cacheableEntity2);
    CompletionListener completionListener = mock(CompletionListener.class);
    versionedCache.loadAll(Sets.newHashSet(key1, key2), true, completionListener);
    verify(internalJCache, times(1))
        .loadAll(Sets.newHashSet(new VersionedKey<>(key1, VERSION), new VersionedKey<>(key2, VERSION)), true,
            completionListener);
  }

  @Test
  @io.harness.rule.Cache
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testIterator() {
    String key1 = "key1";
    String key2 = "key2";
    CacheableEntity cacheableEntity1 = mock(CacheableEntity.class);
    CacheableEntity cacheableEntity2 = mock(CacheableEntity.class);
    versionedCache.put(key1, cacheableEntity1);
    versionedCache.put(key2, cacheableEntity2);
    Iterator<Entry<String, CacheableEntity>> iterator = versionedCache.iterator();
    verify(internalJCache, times(1)).iterator();
    Entry<String, CacheableEntity> entityEntry = iterator.next();
    assertThat(entityEntry.getValue()).isIn(cacheableEntity1, cacheableEntity2);
    assertThat(entityEntry.getKey()).isIn(key1, key2);
    assertThat(iterator.hasNext()).isTrue();
  }
}
