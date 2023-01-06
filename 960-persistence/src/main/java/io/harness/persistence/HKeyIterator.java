/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.persistence;

import dev.morphia.Key;
import dev.morphia.query.MorphiaKeyIterator;
import java.util.Iterator;

// This is a simple wrapper around MorphiaKeyIterator to provide AutoCloseable implementation
public class HKeyIterator<T> implements AutoCloseable, Iterator<Key<T>>, Iterable<Key<T>> {
  private MorphiaKeyIterator<T> iterator;

  public HKeyIterator(MorphiaKeyIterator<T> iterator) {
    this.iterator = iterator;
  }

  @Override
  public void close() {
    iterator.close();
  }

  @Override
  public boolean hasNext() {
    return HPersistence.retry(() -> iterator.hasNext());
  }

  @Override
  public Key<T> next() {
    return HPersistence.retry(() -> iterator.next());
  }

  @Override
  // This is just wrapper around the morphia iterator, it cannot be reused anyways
  @SuppressWarnings("squid:S4348")
  public Iterator<Key<T>> iterator() {
    return this;
  }
}
