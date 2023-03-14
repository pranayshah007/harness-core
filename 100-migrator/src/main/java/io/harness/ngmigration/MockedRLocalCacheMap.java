/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.redisson.api.ObjectListener;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RFuture;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RLock;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.mapreduce.RMapReduce;
import org.redisson.client.codec.Codec;

public class MockedRLocalCacheMap<T> implements RLocalCachedMap<String, T> {
  @Override
  public void preloadCache() {}

  @Override
  public void preloadCache(int i) {}

  @Override
  public RFuture<Void> clearLocalCacheAsync() {
    return null;
  }

  @Override
  public void clearLocalCache() {}

  @Override
  public Set<String> cachedKeySet() {
    return null;
  }

  @Override
  public Collection<T> cachedValues() {
    return null;
  }

  @Override
  public Set<Entry<String, T>> cachedEntrySet() {
    return null;
  }

  @Override
  public Map<String, T> getCachedMap() {
    return null;
  }

  @Override
  public void loadAll(boolean b, int i) {}

  @Override
  public void loadAll(Set<? extends String> set, boolean b, int i) {}

  @Override
  public T get(Object o) {
    return null;
  }

  @Override
  public T put(String s, T t) {
    return null;
  }

  @Override
  public T putIfAbsent(String s, T t) {
    return null;
  }

  @Override
  public T putIfExists(String s, T t) {
    return null;
  }

  @Override
  public Set<String> randomKeys(int i) {
    return null;
  }

  @Override
  public Map<String, T> randomEntries(int i) {
    return null;
  }

  @Override
  public <KOut, VOut> RMapReduce<String, T, KOut, VOut> mapReduce() {
    return null;
  }

  @Override
  public RCountDownLatch getCountDownLatch(String s) {
    return null;
  }

  @Override
  public RPermitExpirableSemaphore getPermitExpirableSemaphore(String s) {
    return null;
  }

  @Override
  public RSemaphore getSemaphore(String s) {
    return null;
  }

  @Override
  public RLock getFairLock(String s) {
    return null;
  }

  @Override
  public RReadWriteLock getReadWriteLock(String s) {
    return null;
  }

  @Override
  public RLock getLock(String s) {
    return null;
  }

  @Override
  public int valueSize(String s) {
    return 0;
  }

  @Override
  public T addAndGet(String s, Number number) {
    return null;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean containsKey(Object o) {
    return false;
  }

  @Override
  public boolean containsValue(Object o) {
    return false;
  }

  @Override
  public T remove(Object o) {
    return null;
  }

  @Override
  public T replace(String s, T t) {
    return null;
  }

  @Override
  public boolean replace(String s, T t, T v1) {
    return false;
  }

  @Override
  public boolean remove(Object o, Object o1) {
    return false;
  }

  @Override
  public void putAll(Map<? extends String, ? extends T> map) {}

  @Override
  public void clear() {}

  @Override
  public void putAll(Map<? extends String, ? extends T> map, int i) {}

  @Override
  public Map<String, T> getAll(Set<String> set) {
    return null;
  }

  @Override
  public long fastRemove(String... strings) {
    return 0;
  }

  @Override
  public boolean fastPut(String s, T t) {
    return false;
  }

  @Override
  public boolean fastReplace(String s, T t) {
    return false;
  }

  @Override
  public boolean fastPutIfAbsent(String s, T t) {
    return false;
  }

  @Override
  public boolean fastPutIfExists(String s, T t) {
    return false;
  }

  @Override
  public Set<String> readAllKeySet() {
    return null;
  }

  @Override
  public Collection<T> readAllValues() {
    return null;
  }

  @Override
  public Set<Entry<String, T>> readAllEntrySet() {
    return null;
  }

  @Override
  public Map<String, T> readAllMap() {
    return null;
  }

  @Override
  public Set<String> keySet() {
    return null;
  }

  @Override
  public Set<String> keySet(int i) {
    return null;
  }

  @Override
  public Set<String> keySet(String s, int i) {
    return null;
  }

  @Override
  public Set<String> keySet(String s) {
    return null;
  }

  @Override
  public Collection<T> values() {
    return null;
  }

  @Override
  public Collection<T> values(String s) {
    return null;
  }

  @Override
  public Collection<T> values(String s, int i) {
    return null;
  }

  @Override
  public Collection<T> values(int i) {
    return null;
  }

  @Override
  public Set<Entry<String, T>> entrySet() {
    return null;
  }

  @Override
  public Set<Entry<String, T>> entrySet(String s) {
    return null;
  }

  @Override
  public Set<Entry<String, T>> entrySet(String s, int i) {
    return null;
  }

  @Override
  public Set<Entry<String, T>> entrySet(int i) {
    return null;
  }

  @Override
  public void destroy() {}

  @Override
  public boolean expire(long l, TimeUnit timeUnit) {
    return false;
  }

  @Override
  public boolean expireAt(long l) {
    return false;
  }

  @Override
  public boolean expireAt(Date date) {
    return false;
  }

  @Override
  public boolean expire(Instant instant) {
    return false;
  }

  @Override
  public boolean expireIfSet(Instant instant) {
    return false;
  }

  @Override
  public boolean expireIfNotSet(Instant instant) {
    return false;
  }

  @Override
  public boolean expireIfGreater(Instant instant) {
    return false;
  }

  @Override
  public boolean expireIfLess(Instant instant) {
    return false;
  }

  @Override
  public boolean expire(Duration duration) {
    return false;
  }

  @Override
  public boolean expireIfSet(Duration duration) {
    return false;
  }

  @Override
  public boolean expireIfNotSet(Duration duration) {
    return false;
  }

  @Override
  public boolean expireIfGreater(Duration duration) {
    return false;
  }

  @Override
  public boolean expireIfLess(Duration duration) {
    return false;
  }

  @Override
  public boolean clearExpire() {
    return false;
  }

  @Override
  public long remainTimeToLive() {
    return 0;
  }

  @Override
  public long getExpireTime() {
    return 0;
  }

  @Override
  public RFuture<T> mergeAsync(String s, T t, BiFunction<? super T, ? super T, ? extends T> biFunction) {
    return null;
  }

  @Override
  public RFuture<T> computeAsync(String s, BiFunction<? super String, ? super T, ? extends T> biFunction) {
    return null;
  }

  @Override
  public RFuture<T> computeIfAbsentAsync(String s, Function<? super String, ? extends T> function) {
    return null;
  }

  @Override
  public RFuture<T> computeIfPresentAsync(String s, BiFunction<? super String, ? super T, ? extends T> biFunction) {
    return null;
  }

  @Override
  public RFuture<Void> loadAllAsync(boolean b, int i) {
    return null;
  }

  @Override
  public RFuture<Void> loadAllAsync(Set<? extends String> set, boolean b, int i) {
    return null;
  }

  @Override
  public RFuture<Integer> valueSizeAsync(String s) {
    return null;
  }

  @Override
  public RFuture<Map<String, T>> getAllAsync(Set<String> set) {
    return null;
  }

  @Override
  public RFuture<Void> putAllAsync(Map<? extends String, ? extends T> map) {
    return null;
  }

  @Override
  public RFuture<Void> putAllAsync(Map<? extends String, ? extends T> map, int i) {
    return null;
  }

  @Override
  public RFuture<Set<String>> randomKeysAsync(int i) {
    return null;
  }

  @Override
  public RFuture<Map<String, T>> randomEntriesAsync(int i) {
    return null;
  }

  @Override
  public RFuture<T> addAndGetAsync(String s, Number number) {
    return null;
  }

  @Override
  public RFuture<Boolean> containsValueAsync(Object o) {
    return null;
  }

  @Override
  public RFuture<Boolean> containsKeyAsync(Object o) {
    return null;
  }

  @Override
  public RFuture<Integer> sizeAsync() {
    return null;
  }

  @Override
  public RFuture<Long> fastRemoveAsync(String... strings) {
    return null;
  }

  @Override
  public RFuture<Boolean> fastPutAsync(String s, T t) {
    return null;
  }

  @Override
  public RFuture<Boolean> fastReplaceAsync(String s, T t) {
    return null;
  }

  @Override
  public RFuture<Boolean> fastPutIfAbsentAsync(String s, T t) {
    return null;
  }

  @Override
  public RFuture<Boolean> fastPutIfExistsAsync(String s, T t) {
    return null;
  }

  @Override
  public RFuture<Set<String>> readAllKeySetAsync() {
    return null;
  }

  @Override
  public RFuture<Collection<T>> readAllValuesAsync() {
    return null;
  }

  @Override
  public RFuture<Set<Entry<String, T>>> readAllEntrySetAsync() {
    return null;
  }

  @Override
  public RFuture<Map<String, T>> readAllMapAsync() {
    return null;
  }

  @Override
  public RFuture<T> getAsync(String s) {
    return null;
  }

  @Override
  public RFuture<T> putAsync(String s, T t) {
    return null;
  }

  @Override
  public RFuture<T> removeAsync(String s) {
    return null;
  }

  @Override
  public RFuture<T> replaceAsync(String s, T t) {
    return null;
  }

  @Override
  public RFuture<Boolean> replaceAsync(String s, T t, T v1) {
    return null;
  }

  @Override
  public RFuture<Boolean> removeAsync(Object o, Object o1) {
    return null;
  }

  @Override
  public RFuture<T> putIfAbsentAsync(String s, T t) {
    return null;
  }

  @Override
  public RFuture<T> putIfExistsAsync(String s, T t) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireAsync(long l, TimeUnit timeUnit) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireAtAsync(Date date) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireAtAsync(long l) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireAsync(Instant instant) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireIfSetAsync(Instant instant) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireIfNotSetAsync(Instant instant) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireIfGreaterAsync(Instant instant) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireIfLessAsync(Instant instant) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireAsync(Duration duration) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireIfSetAsync(Duration duration) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireIfNotSetAsync(Duration duration) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireIfGreaterAsync(Duration duration) {
    return null;
  }

  @Override
  public RFuture<Boolean> expireIfLessAsync(Duration duration) {
    return null;
  }

  @Override
  public RFuture<Boolean> clearExpireAsync() {
    return null;
  }

  @Override
  public RFuture<Long> remainTimeToLiveAsync() {
    return null;
  }

  @Override
  public RFuture<Long> getExpireTimeAsync() {
    return null;
  }

  @Override
  public Long getIdleTime() {
    return null;
  }

  @Override
  public long sizeInMemory() {
    return 0;
  }

  @Override
  public void restore(byte[] bytes) {}

  @Override
  public void restore(byte[] bytes, long l, TimeUnit timeUnit) {}

  @Override
  public void restoreAndReplace(byte[] bytes) {}

  @Override
  public void restoreAndReplace(byte[] bytes, long l, TimeUnit timeUnit) {}

  @Override
  public byte[] dump() {
    return new byte[0];
  }

  @Override
  public boolean touch() {
    return false;
  }

  @Override
  public void migrate(String s, int i, int i1, long l) {}

  @Override
  public void copy(String s, int i, int i1, long l) {}

  @Override
  public boolean move(int i) {
    return false;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public boolean delete() {
    return false;
  }

  @Override
  public boolean unlink() {
    return false;
  }

  @Override
  public void rename(String s) {}

  @Override
  public boolean renamenx(String s) {
    return false;
  }

  @Override
  public boolean isExists() {
    return false;
  }

  @Override
  public Codec getCodec() {
    return null;
  }

  @Override
  public int addListener(ObjectListener objectListener) {
    return 0;
  }

  @Override
  public void removeListener(int i) {}

  @Override
  public RFuture<Long> getIdleTimeAsync() {
    return null;
  }

  @Override
  public RFuture<Long> sizeInMemoryAsync() {
    return null;
  }

  @Override
  public RFuture<Void> restoreAsync(byte[] bytes) {
    return null;
  }

  @Override
  public RFuture<Void> restoreAsync(byte[] bytes, long l, TimeUnit timeUnit) {
    return null;
  }

  @Override
  public RFuture<Void> restoreAndReplaceAsync(byte[] bytes) {
    return null;
  }

  @Override
  public RFuture<Void> restoreAndReplaceAsync(byte[] bytes, long l, TimeUnit timeUnit) {
    return null;
  }

  @Override
  public RFuture<byte[]> dumpAsync() {
    return null;
  }

  @Override
  public RFuture<Boolean> touchAsync() {
    return null;
  }

  @Override
  public RFuture<Void> migrateAsync(String s, int i, int i1, long l) {
    return null;
  }

  @Override
  public RFuture<Void> copyAsync(String s, int i, int i1, long l) {
    return null;
  }

  @Override
  public RFuture<Boolean> moveAsync(int i) {
    return null;
  }

  @Override
  public RFuture<Boolean> deleteAsync() {
    return null;
  }

  @Override
  public RFuture<Boolean> unlinkAsync() {
    return null;
  }

  @Override
  public RFuture<Void> renameAsync(String s) {
    return null;
  }

  @Override
  public RFuture<Boolean> renamenxAsync(String s) {
    return null;
  }

  @Override
  public RFuture<Boolean> isExistsAsync() {
    return null;
  }

  @Override
  public RFuture<Integer> addListenerAsync(ObjectListener objectListener) {
    return null;
  }

  @Override
  public RFuture<Void> removeListenerAsync(int i) {
    return null;
  }
}
