/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlException;

@Builder
public class LateBindingContext implements JexlContext {
  private ExpressionEvaluator expressionEvaluator;
  private List<String> prefixes;
  private Map<String, Object> map;

  private boolean recursive;

  @Override
  public synchronized boolean has(String name) {
    return map.containsKey(name);
  }

  @Override
  public synchronized Object get(String key) {
    Object object;
    object = map.get(key);
    if (object == null && !recursive) {
      for (String prefix : prefixes) {
        if (prefix == null) {
          continue;
        }
        recursive = true;
        try {
          object = expressionEvaluator.evaluate(prefix + "." + key, this);
        } catch (JexlException ignore) {
          // Ignore any expression evaluation exception
        } finally {
          recursive = false;
        }
        if (object != null) {
          break;
        }
      }
    }

    if (object instanceof LateBindingValue) {
      map.remove((String) key);
      object = ((LateBindingValue) object).bind();
      map.put((String) key, object);
    }

    return object;
  }

  @Override
  public synchronized void set(String name, Object value) {
    map.put(name, value);
  }

  public synchronized void clear() {
    map.clear();
  }

  Map<String, Object> getMap() {
    return map;
  }
}
