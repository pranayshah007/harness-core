/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.redis.intfc;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.redis.impl.DelegateRedissonCacheManagerImpl;

import com.google.inject.ImplementedBy;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;

@OwnedBy(DEL)
@ImplementedBy(DelegateRedissonCacheManagerImpl.class)
public interface DelegateRedissonCacheManager {
  <K, V> RLocalCachedMap<K, V> getCache(
      String cacheName, Class<K> keyType, Class<V> valueType, LocalCachedMapOptions<K, V> localCachedMapOptions);
}
