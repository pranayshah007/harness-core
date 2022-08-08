/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.datastructures;

import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class NoopEphemeralCacheServiceImpl implements EphemeralCacheService {
  @Override
  public <V> Set<V> getDistributedSet(String setName) {
    return new HashSet<>();
  }
}
