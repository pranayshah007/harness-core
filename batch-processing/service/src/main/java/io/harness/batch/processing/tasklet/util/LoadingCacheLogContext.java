/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.util;

import io.harness.logging.AutoLogContext;

import com.google.common.collect.ImmutableMap;

public class LoadingCacheLogContext extends AutoLogContext {
  public static final String NAME = "cache_name";
  public static final String SIZE_COUNT = "size_count";

  public LoadingCacheLogContext(String cacheName, String sizeCount, OverrideBehavior behavior) {
    super(ImmutableMap.of(NAME, cacheName, SIZE_COUNT, sizeCount), behavior);
  }
}
