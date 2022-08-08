/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.datastructures;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Collection;
import java.util.Iterator;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;

@OwnedBy(HarnessTeam.PIPELINE)
public class RedisHSet<V> implements HSet<V> {
  String setName;
  RedissonClient redissonClient;

  public RedisHSet(RedissonClient redissonClient, String setName) {
    this.redissonClient = redissonClient;
    this.setName = setName;
  }

  private RSet<V> getSet() {
    return redissonClient.getSet(setName);
  }

  @Override
  public int size() {
    return getSet().size();
  }

  @Override
  public boolean isEmpty() {
    return getSet().isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return getSet().contains(o);
  }

  @Override
  public Iterator<V> iterator() {
    return getSet().iterator();
  }

  @Override
  public Object[] toArray() {
    return getSet().toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return null;
  }

  @Override
  public boolean add(V v) {
    return getSet().add(v);
  }

  @Override
  public boolean remove(Object o) {
    return getSet().remove(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return getSet().containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends V> c) {
    return getSet().addAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return getSet().retainAll(c);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return getSet().removeAll(c);
  }

  @Override
  public void clear() {
    getSet().clear();
  }
}
