/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.core.AliasRegistry;
import io.harness.utils.RecastReflectionUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class RecasterMap extends LinkedHashMap<String, Object> implements Map<String, Object> {
  public static final String RECAST_CLASS_KEY = "__recast";
  public static final String ENCODED_VALUE = "__encodedValue";

  public RecasterMap() {}

  public RecasterMap(Map<? extends String, ?> m) {
    super(m);
  }

  public static RecasterMap cast(Map<String, Object> map) {
    return new RecasterMap(map);
  }

  public boolean containsIdentifier() {
    return this.containsKey(RECAST_CLASS_KEY);
  }

  public Object getIdentifier() {
    return this.get(RECAST_CLASS_KEY);
  }

  public <T> void setIdentifier(Class<T> clazz) {
    String recasterAliasValue = RecastReflectionUtils.obtainRecasterAliasValueOrNull(clazz);
    if (recasterAliasValue != null) {
      this.put(RECAST_CLASS_KEY, recasterAliasValue);
    } else {
      if (AliasRegistry.getInstance().shouldContainAlias(clazz)) {
        log.warn("[RECAST_ALIAS]: Consider adding @RecasterAlias annotation to this class {}", clazz.getName());
      }
      this.put(RECAST_CLASS_KEY, clazz.getName());
    }
  }

  public Object removeIdentifier() {
    return this.remove(RECAST_CLASS_KEY);
  }

  public boolean containsEncodedValue() {
    return this.containsKey(ENCODED_VALUE);
  }

  public Object getEncodedValue() {
    return this.get(ENCODED_VALUE);
  }

  public void setEncodedValue(Object value) {
    this.put(ENCODED_VALUE, value);
  }

  public RecasterMap append(String key, Object value) {
    this.put(key, value);
    return this;
  }
}
