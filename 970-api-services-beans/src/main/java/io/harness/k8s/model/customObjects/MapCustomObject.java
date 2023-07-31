/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.customObjects;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.google.inject.Inject;
import java.util.Map;

public class MapCustomObject implements CustomObject {
  private Object value;
  @Inject CustomObjectFactory customObjectFactory;
  public MapCustomObject(Object object) {
    this.value = object;
  }

  public Map<String, Object> getObject() {
    return (Map<String, Object>) this.value;
  }

  @Override
  public CustomObject getNode(String fieldName) {
    Map<String, Object> mapNode = getObject();
    if (isEmpty(mapNode)) {
      return null;
    }
    if (mapNode.containsKey(fieldName)) {
      return customObjectFactory.create(mapNode.get(fieldName));
    }
    for (Object node : mapNode.values()) {
      CustomObject customObject = customObjectFactory.create(node).getNode(fieldName);
      if (customObject != null) {
        return customObject;
      }
    }
    return null;
  }

  @Override
  public void setNode(String fieldName, Object object) {
    Map<String, Object> mapNode = getObject();
    mapNode.put(fieldName, object);
  }
}
