/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cache;

import static java.util.Collections.emptyMap;

import io.harness.exception.InvalidArgumentsException;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryListener;
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
    if (aClass.isAssignableFrom(ResilientCacheConfigurationWrapper.class)) {
      return (C) new ResilientCacheConfigurationWrapper<K, V>(cache.getConfiguration(Configuration.class));
    }
    throw new InvalidArgumentsException("Casting to ResilientCacheConfiguration is only supported");
  }

  @Override
  public <T> T invoke(K k, EntryProcessor<K, V, T> entryProcessor, Object... objects) throws EntryProcessorException {
    try {
      EntryProcessor<K, V, T> cacheEntryProcessor = new ResilientEntryProcessor<>(entryProcessor);
      return cache.invoke(k, cacheEntryProcessor, objects);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }

  @Override
  public <T> Map<K, EntryProcessorResult<T>> invokeAll(
      Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor, Object... objects) {
    try {
      EntryProcessor<K, V, T> cacheEntryProcessor = new ResilientEntryProcessor<>(entryProcessor);
      return cache.invokeAll(keys, cacheEntryProcessor, objects);
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
      if (aClass.isAssignableFrom(getClass())) {
        return aClass.cast(this);
      }
      return cache.unwrap(aClass);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }

  private Factory<CacheEntryListener<? super K, ? super V>> getResilientCacheEntryListenerFactory(
      Factory<CacheEntryListener<? super K, ? super V>> cacheFactory, ResilientCache<K, V> resilientCache) {
    return () -> {
      CacheEntryListener<? super K, ? super V> cacheListener = cacheFactory.create();
      return new ResilientCacheEntryListener<>(cacheListener, resilientCache);
    };
  }

  private CacheEntryListenerConfiguration<K, V> getResilientCacheEntryListenerConfiguration(
      CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration, ResilientCache<K, V> resilientCache) {
    return new CacheEntryListenerConfiguration<K, V>() {
      @Override
      public Factory<CacheEntryListener<? super K, ? super V>> getCacheEntryListenerFactory() {
        Factory<CacheEntryListener<? super K, ? super V>> cacheFactory =
            cacheEntryListenerConfiguration.getCacheEntryListenerFactory();
        return getResilientCacheEntryListenerFactory(cacheFactory, resilientCache);
      }

      @Override
      public boolean isOldValueRequired() {
        return cacheEntryListenerConfiguration.isOldValueRequired();
      }

      @Override
      public Factory<CacheEntryEventFilter<? super K, ? super V>> getCacheEntryEventFilterFactory() {
        Factory<CacheEntryEventFilter<? super K, ? super V>> cacheEntryEventFilterFactory =
            cacheEntryListenerConfiguration.getCacheEntryEventFilterFactory();
        return () -> {
          CacheEntryEventFilter<? super K, ? super V> cacheEntryEventFilter = cacheEntryEventFilterFactory.create();
          return event -> cacheEntryEventFilter.evaluate(new ResilientCacheEntryEventWrapper<>(event, resilientCache));
        };
      }

      @Override
      public boolean isSynchronous() {
        return cacheEntryListenerConfiguration.isSynchronous();
      }
    };
  }

  @Override
  public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    try {
      CacheEntryListenerConfiguration<K, V> harnessCacheEntryListenerConfiguration =
          getResilientCacheEntryListenerConfiguration(cacheEntryListenerConfiguration, this);
      cache.registerCacheEntryListener(harnessCacheEntryListenerConfiguration);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
    }
  }

  @Override
  public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    try {
      CacheEntryListenerConfiguration<K, V> harnessCacheEntryListenerConfiguration =
          getResilientCacheEntryListenerConfiguration(cacheEntryListenerConfiguration, this);
      cache.deregisterCacheEntryListener(harnessCacheEntryListenerConfiguration);
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
    }
  }

  @Override
  public Iterator<Entry<K, V>> iterator() {
    try {
      Iterator<Entry<K, V>> cacheIterator = cache.iterator();
      return new Iterator<Entry<K, V>>() {
        @Override
        public boolean hasNext() {
          return cacheIterator.hasNext();
        }

        @Override
        public Entry<K, V> next() {
          Entry<K, V> cacheEntry = cacheIterator.next();
          return new Entry<K, V>() {
            @Override
            public K getKey() {
              return cacheEntry.getKey();
            }

            @Override
            public V getValue() {
              return cacheEntry.getValue();
            }

            @Override
            public <T> T unwrap(Class<T> clazz) {
              return cacheEntry.unwrap(clazz);
            }
          };
        }
      };
    } catch (Exception e) {
      log.error(HARNESS_CACHE_EXCEPTION_MESSAGE, e);
      return null;
    }
  }
}
