package io.harness.accesscontrol.acl.api;

import static java.util.stream.Collectors.groupingBy;

import io.harness.accesscontrol.ResourceInfo;
import io.harness.accesscontrol.acl.ResourceAttributeProvider;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.connector.ConnectorResourceClient;
import io.harness.environment.remote.EnvironmentResourceClient;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResourceAttributeProviderImpl implements ResourceAttributeProvider {
  @Inject EnvironmentResourceClient environmentResourceClient;
  @Inject ConnectorResourceClient connectorResourceClient;

  @Override
  public Map<ResourceInfo, Map<String, String>> getAttributes(Set<ResourceInfo> resources) {
    Map<ResourceInfo, Map<String, String>> result = new HashMap<>();
    Map<Scope, List<ResourceInfo>> resourcesByScope =
        resources.stream().collect(groupingBy(ResourceInfo::getResourceScope));

    for (Scope resourceScope : resourcesByScope.keySet()) {
      List<ResourceInfo> resourcesInScope = resourcesByScope.get(resourceScope);
      Map<ResourceInfo, Map<String, String>> resourceAttributes =
          getAttributesOfResourcesInSameScope(resourceScope, resourcesInScope);
      result.putAll(resourceAttributes);
    }

    return result;
  }

  private Map<ResourceInfo, Map<String, String>> getAttributesOfResourcesInSameScope(
      Scope resourceScope, List<ResourceInfo> resources) {
    Map<String, List<ResourceInfo>> resourcesByTypes =
        resources.stream().collect(groupingBy(ResourceInfo::getResourceType));

    Map<ResourceInfo, Map<String, String>> result = new HashMap<>();
    for (String resourceType : resourcesByTypes.keySet()) {
      Map<ResourceInfo, Map<String, String>> resourceAttributes = getAttributesOfResourcesInSameScopeAndOfSameType(
          resourceScope, resourceType, resourcesByTypes.get(resourceType));
      result.putAll(resourceAttributes);
    }
    return result;
  }

  private Map<ResourceInfo, Map<String, String>> getAttributesOfResourcesInSameScopeAndOfSameType(
      Scope resourceScope, String resourceType, List<ResourceInfo> resources) {
    List<String> resourceIds = resources.stream().map(ResourceInfo::getResourceIdentifier).collect(Collectors.toList());
    HarnessScopeParams scope = ScopeMapper.toParams(resourceScope);

    List<Map<String, String>> resourceAttributes = new ArrayList<>();
    switch (resourceType) {
      case "ENVIRONMENT":
        resourceAttributes = NGRestUtils.getResponse(environmentResourceClient.getEnvironmentsAttributes(
            scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), resourceIds));
        break;
      case "CONNECTOR":
        resourceAttributes = NGRestUtils.getResponse(connectorResourceClient.getConnectorsAttributes(
            scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), resourceIds));
        break;
      default:
        log.warn("Unsupported resource type : " + resourceType);
    }

    Map<ResourceInfo, Map<String, String>> result = new HashMap<>();
    for (int i = 0; i < resources.size(); i++) {
      result.put(resources.get(i), resourceAttributes.get(i));
    }

    return result;
  }
}
