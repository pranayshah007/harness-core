/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import io.harness.serializer.JsonUtils;

import java.util.HashMap;
import java.util.LinkedList;

public class JsonFunctor implements ExpressionFunctor {
  public Object object(String json) {
    return JsonUtils.asObject(json, HashMap.class);
  }

  public Object select(String path, String json) {
    final Object object = JsonUtils.jsonPath(json, path);
    if (object instanceof String) {
      return object;
    } else if (object instanceof LinkedList) {
      LinkedList list = (LinkedList) object;
      if (list.size() == 1) {
        return list.get(0);
      }
    }
    return null;
  }

  public Object list(String path, String json) {
    return JsonUtils.jsonPath(json, path);
  }

  public String format(Object object) {
    return JsonUtils.asJson(object);
  }
}
