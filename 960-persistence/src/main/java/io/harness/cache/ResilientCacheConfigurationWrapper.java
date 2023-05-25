/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cache;

import javax.cache.configuration.Configuration;

public class ResilientCacheConfigurationWrapper<K, V> implements Configuration<K, V> {
  private Configuration<K, V> jcacheConfig;

  public ResilientCacheConfigurationWrapper(Configuration<K, V> jcacheConfig) {
    this.jcacheConfig = jcacheConfig;
  }

  public Configuration<K, V> getInternalCacheConfig() {
    return jcacheConfig;
  }

  @Override
  public Class<K> getKeyType() {
    return jcacheConfig.getKeyType();
  }

  @Override
  public Class<V> getValueType() {
    return jcacheConfig.getValueType();
  }

  @Override
  public boolean isStoreByValue() {
    return jcacheConfig.isStoreByValue();
  }
}
