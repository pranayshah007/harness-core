/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlContext;

@Value
@Slf4j
public class EngineJexlContext implements JexlContext {
  EngineExpressionEvaluator engineExpressionEvaluator;
  Map<String, Object> originalMap;
  Map<String, Object> updatesMap;

  public EngineJexlContext(
      @NotNull EngineExpressionEvaluator engineExpressionEvaluator, @NotNull Map<String, Object> originalMap) {
    this.engineExpressionEvaluator = engineExpressionEvaluator;
    this.originalMap = originalMap;
    this.updatesMap = new HashMap<>();
  }

  @Override
  public synchronized boolean has(String key) {
    return originalMap.containsKey(key) || updatesMap.containsKey(key);
  }

  @Override
  public synchronized Object get(String key) {
    Object object = null;
    if (updatesMap.containsKey(key)) {
      object = getInternal(updatesMap, key);
    }
    if (object == null && originalMap.containsKey(key)) {
      object = getInternal(originalMap, key);
    }
    return object;
  }

  private Object getInternal(Map<String, Object> map, String key) {
    Object object = map.get(key);
    if (object instanceof LateBindingValue) {
      originalMap.remove(key);
      object = ((LateBindingValue) object).bind();
      originalMap.put(key, object);
    }
    return object;
  }

  @Override
  public synchronized void set(String name, Object value) {
    updatesMap.put(name, value);
  }

  public synchronized void clear() {
    originalMap.clear();
    updatesMap.clear();
  }

  public synchronized void addToContext(Map<String, Object> newMap) {
    if (newMap != null) {
      originalMap.putAll(newMap);
    }
  }

  public boolean isFeatureFlagEnabled(String featureName) {
    return originalMap.containsKey(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY)
        && originalMap.get(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY).toString().contains(featureName);
  }
}
