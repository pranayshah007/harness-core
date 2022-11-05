/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import dev.morphia.Key;
import dev.morphia.mapping.cache.EntityCache;
import dev.morphia.mapping.cache.EntityCacheStatistics;

/**
 * Implementation of entity cache required by
 * morphia mapper for conversion of DBObject to POJO.
 *
 * @author utkarsh
 */
@OwnedBy(PL)
public class NoopEntityCache implements EntityCache {
  @Override
  public Boolean exists(Key<?> key) {
    return false;
  }

  @Override
  public void flush() {
    // Do nothing because it is a noop cache.
  }

  @Override
  public <T> T getEntity(Key<T> key) {
    return null;
  }

  @Override
  public <T> T getProxy(Key<T> key) {
    return null;
  }

  @Override
  public void notifyExists(Key<?> key, boolean b) {
    // Do nothing because it is a noop cache.
  }

  @Override
  public <T> void putEntity(Key<T> key, T t) {
    // Do nothing because it is a noop cache.
  }

  @Override
  public <T> void putProxy(Key<T> key, T t) {
    // Do nothing because it is a noop cache.
  }

  @Override
  public EntityCacheStatistics stats() {
    return null;
  }
}
