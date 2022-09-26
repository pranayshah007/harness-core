/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.data.structure;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
// Note that the name of the class is chosen to allow for a simple replacement of the original ImmutableMap
// ImmutableMap.builder()... simply becomes NullSafeImmutableMap.builder()...
// Technically it can inherit the ImmutableMap and became a an object class, but this will serve no purpose
// since it does not extend the functionality of the class.
public class NullSafeImmutableMap {
  public static class NullSafeBuilder<K, V> extends com.google.common.collect.ImmutableMap.Builder<K, V> {
    @Override
    public NullSafeBuilder<K, V> put(@NonNull K key, @NonNull V value) {
      super.put(key, value);
      return this;
    }

    public NullSafeBuilder<K, V> putIfNotNull(K key, V value) {
      if (key != null && value != null) {
        put(key, value);
      }
      return this;
    }
  }

  public static <K, V> NullSafeBuilder<K, V> builder() {
    return new NullSafeBuilder<>();
  }
}
