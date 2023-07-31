/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model.customObjects;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import com.google.inject.Inject;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class ListCustomObject implements CustomObject {
  private Object value;
  @Inject CustomObjectFactory customObjectFactory;

  public ListCustomObject(Object object) {
    this.value = object;
  }

  public List<Object> getObject() {
    return (List<Object>) this.value;
  }

  @Override
  public CustomObject getNode(String fieldName) {
    List<Object> listNode = getObject();
    if (isEmpty(listNode)) {
      return null;
    }
    for (Object node : listNode) {
      CustomObject customObject = customObjectFactory.create(node).getNode(fieldName);
      if (customObject != null) {
        return customObject;
      }
    }
    return null;
  }

  @Override
  public void setNode(String fieldName, Object object) {
    List<Object> listNode = getObject();
    listNode.add(object);
  }
}
