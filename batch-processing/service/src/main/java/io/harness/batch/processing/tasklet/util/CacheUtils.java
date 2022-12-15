/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.util;

import io.harness.logging.AutoLogContext;

import com.github.benmanes.caffeine.cache.Cache;
import java.lang.reflect.Field;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class CacheUtils {
  public void logCacheStats() throws IllegalAccessException {
    for (Field field : this.getClass().getDeclaredFields()) {
      if (Cache.class.isAssignableFrom(field.getType())) {
        field.setAccessible(true);
        Cache loadingCache = (Cache) field.get(this);
        try (AutoLogContext ignore1 = new LoadingCacheLogContext(field.getName(),
                 String.valueOf(loadingCache.estimatedSize()), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
          log.info(loadingCache.stats().toString());
        }
      }
    }
  }
}
