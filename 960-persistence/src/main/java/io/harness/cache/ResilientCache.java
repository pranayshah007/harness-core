/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cache;

import static java.util.Collections.emptyMap;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResilientCache<K, V> implements Cache<K, V> {
  public static final String HARNESS_CACHE_EXCEPTION_MESSAGE = "HarnessCache Exception occurred ";

  private Cache<K, V> cache;

  public ResilientCache(Cache<K, V> cache) {
    this.cache = cache;
  }

  @Override
  public V get(K k) {
    try {
      return cache.get(k);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }

  @Override
  public Map<K, V> getAll(Set<? extends K> set) {
    try {
      return cache.getAll(set);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return emptyMap();
    }
  }

  @Override
  public boolean containsKey(K k) {
    try {
      return cache.containsKey(k);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return false;
    }
  }

  @Override
  public void loadAll(Set<? extends K> set, boolean b, CompletionListener completionListener) {
    try {
      cache.loadAll(set, b, completionListener);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
    }
  }

  @Override
  public void put(K k, V v) {
    try {
      cache.put(k, v);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
    }
  }

  @Override
  public V getAndPut(K k, V v) {
    try {
      return cache.getAndPut(k, v);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    try {
      cache.putAll(map);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
    }
  }

  @Override
  public boolean putIfAbsent(K k, V v) {
    try {
      return cache.putIfAbsent(k, v);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return false;
    }
  }

  @Override
  public boolean remove(K k) {
    try {
      return cache.remove(k);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return false;
    }
  }

  @Override
  public boolean remove(K k, V v) {
    try {
      return cache.remove(k, v);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return false;
    }
  }

  @Override
  public V getAndRemove(K k) {
    try {
      return cache.getAndRemove(k);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }

  @Override
  public boolean replace(K k, V v, V v1) {
    try {
      return cache.replace(k, v, v1);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return false;
    }
  }

  @Override
  public boolean replace(K k, V v) {
    try {
      return cache.replace(k, v);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return false;
    }
  }

  @Override
  public V getAndReplace(K k, V v) {
    try {
      return cache.getAndReplace(k, v);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }

  @Override
  public void removeAll(Set<? extends K> set) {
    try {
      cache.removeAll(set);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
    }
  }

  @Override
  public void removeAll() {
    try {
      cache.removeAll();
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
    }
  }

  @Override
  public void clear() {
    try {
      cache.clear();
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
    }
  }

  @Override
  public <C extends Configuration<K, V>> C getConfiguration(Class<C> aClass) {
    try {
      return cache.getConfiguration(aClass);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }

  @Override
  public <T> T invoke(K k, EntryProcessor<K, V, T> entryProcessor, Object... objects) throws EntryProcessorException {
    try {
      return cache.invoke(k, entryProcessor, objects);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }

  @Override
  public <T> Map<K, EntryProcessorResult<T>> invokeAll(
      Set<? extends K> set, EntryProcessor<K, V, T> entryProcessor, Object... objects) {
    try {
      return cache.invokeAll(set, entryProcessor, objects);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return emptyMap();
    }
  }

  @Override
  public String getName() {
    try {
      return cache.getName();
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }

  @Override
  public CacheManager getCacheManager() {
    return cache.getCacheManager();
  }

  @Override
  public void close() {
    try {
      cache.close();
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
    }
  }

  @Override
  public boolean isClosed() {
    return cache.isClosed();
  }

  @Override
  public <T> T unwrap(Class<T> aClass) {
    try {
      return cache.unwrap(aClass);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }

  @Override
  public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    try {
      cache.registerCacheEntryListener(cacheEntryListenerConfiguration);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
    }
  }

  @Override
  public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    try {
      cache.deregisterCacheEntryListener(cacheEntryListenerConfiguration);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
    }
  }

  @Override
  public Iterator<Entry<K, V>> iterator() {
    try {
      return cache.iterator();
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }
}
