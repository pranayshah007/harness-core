package io.harness.accesscontrol.acl;

import io.harness.accesscontrol.ResourceInfo;

import java.util.Map;
import java.util.Set;

public interface ResourceAttributeProvider {
  Map<ResourceInfo, Map<String, String>> getAttributes(Set<ResourceInfo> resources);
}
