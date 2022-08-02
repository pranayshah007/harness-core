/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cache;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import javax.cache.Cache;
import javax.cache.configuration.Factory;
import javax.cache.expiry.ExpiryPolicy;

@OwnedBy(PL)
public class NoOpHarnessCacheManager implements HarnessCacheManager {
  @Override
  public <K, V> Cache<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy) {
    return new NoOpCache<>();
  }

  @Override
  public <K, V> Cache<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType,
      Factory<ExpiryPolicy> expiryPolicy, boolean enterpriseRedis) {
    return new NoOpCache<>();
  }

  @Override
  public <K, V> Cache<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, Factory<ExpiryPolicy> expiryPolicy, String keyPrefix) {
    return new NoOpCache<>();
  }

  @Override
  public <K, V> Cache<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType,
      Factory<ExpiryPolicy> expiryPolicy, String keyPrefix, boolean enterpriseRedis) {
    return new NoOpCache<>();
  }
}
