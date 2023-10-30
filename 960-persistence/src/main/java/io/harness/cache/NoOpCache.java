/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cache;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorResult;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PL)
public class NoOpCache<K, V> implements Cache<K, V> {
  @Override
  public V get(K key) {
    return null;
  }

  @Override
  public Map<K, V> getAll(Set<? extends K> keys) {
    return null;
  }

  @Override
  public boolean containsKey(K key) {
    return false;
  }

  @Override
  public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, CompletionListener completionListener) {
    // NoOp cache
  }

  @Override
  public void put(K key, V value) {
    // Noop cache
  }

  @Override
  public V getAndPut(K key, V value) {
    return null;
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    // NoOp cache
  }

  @Override
  public boolean putIfAbsent(K key, V value) {
    return true;
  }

  @Override
  public boolean remove(K key) {
    return false;
  }

  @Override
  public boolean remove(K key, V oldValue) {
    return false;
  }

  @Override
  public void removeAll() {
    // NoOp cache
  }

  @Override
  public void removeAll(Set<? extends K> keys) {
    // NoOp cache
  }

  @Override
  public V getAndRemove(K key) {
    return null;
  }

  @Override
  public boolean replace(K key, V value) {
    return false;
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    return false;
  }

  @Override
  public V getAndReplace(K key, V value) {
    return null;
  }

  @Override
  public void clear() {
    // Noop cache
  }

  @Override
  public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz) {
    return null;
  }

  @Override
  public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
    return null;
  }

  @Override
  public <T> Map<K, EntryProcessorResult<T>> invokeAll(
      Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor, Object... arguments) {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public CacheManager getCacheManager() {
    return null;
  }

  @Override
  public void close() {
    // NoOp cache
  }

  @Override
  public boolean isClosed() {
    return false;
  }

  @Override
  public <T> T unwrap(Class<T> clazz) {
    return null;
  }

  @Override
  public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    // NoOp cache
  }

  @Override
  public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> cacheEntryListenerConfiguration) {
    // NoOp cache
  }

  @Override
  public Iterator<Entry<K, V>> iterator() {
    return Collections.emptyIterator();
  }

  @Override
  public void forEach(Consumer<? super Entry<K, V>> action) {
    // NoOp cache
  }

  @Override
  public Spliterator<Entry<K, V>> spliterator() {
    return null;
  }
}
