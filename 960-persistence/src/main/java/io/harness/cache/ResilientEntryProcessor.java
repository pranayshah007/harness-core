/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cache;

import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;

public class ResilientEntryProcessor<K, V, T> implements EntryProcessor<K, V, T> {
  private EntryProcessor<K, V, T> entryProcessor;

  public ResilientEntryProcessor(EntryProcessor<K, V, T> entryProcessor) {
    this.entryProcessor = entryProcessor;
  }

  public static class ResilientMutableEntryWrapper<K, V> implements MutableEntry<K, V> {
    private MutableEntry<K, V> resilientMutableEntry;

    public ResilientMutableEntryWrapper(MutableEntry<K, V> resilientMutableEntry) {
      this.resilientMutableEntry = resilientMutableEntry;
    }

    @Override
    public boolean exists() {
      return resilientMutableEntry.exists();
    }

    @Override
    public void remove() {
      resilientMutableEntry.remove();
    }

    @Override
    public void setValue(V value) {
      resilientMutableEntry.setValue(value);
    }

    @Override
    public K getKey() {
      return resilientMutableEntry.getKey();
    }

    @Override
    public V getValue() {
      return resilientMutableEntry.getValue();
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
      if (clazz.isAssignableFrom(getClass())) {
        return clazz.cast(this);
      }
      return resilientMutableEntry.unwrap(clazz);
    }
  }

  @Override
  public T process(MutableEntry<K, V> entry, Object... arguments) throws EntryProcessorException {
    MutableEntry<K, V> wrappedEntry = new ResilientMutableEntryWrapper<>(entry);
    return entryProcessor.process(wrappedEntry, arguments);
  }
}
