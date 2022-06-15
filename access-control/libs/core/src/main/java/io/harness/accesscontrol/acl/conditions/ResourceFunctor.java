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
