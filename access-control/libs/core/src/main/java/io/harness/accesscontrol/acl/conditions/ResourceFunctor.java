/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.conditions;

import io.harness.expression.LateBindingValue;

import java.util.HashMap;

public class ResourceFunctor implements LateBindingValue {
  private final HashMap<String, Object> resourceMap;

  public ResourceFunctor(HashMap<String, Object> resourceMap) {
    this.resourceMap = resourceMap;
  }

  @Override
  public Object bind() {
    return resourceMap;
  }
}
