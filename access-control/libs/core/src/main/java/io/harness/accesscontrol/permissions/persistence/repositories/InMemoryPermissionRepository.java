/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.permissions.persistence.repositories;

import static io.harness.accesscontrol.resources.resourcegroups.ResourceGroup.ALL_RESOURCES_IDENTIFIER;
import static io.harness.accesscontrol.scopes.core.Scope.PATH_DELIMITER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import io.harness.accesscontrol.permissions.Permission;
import io.harness.accesscontrol.permissions.persistence.PermissionDBO;
import io.harness.accesscontrol.permissions.persistence.PermissionDBOMapper;
import io.harness.accesscontrol.resources.resourcetypes.persistence.ResourceTypeDBO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class InMemoryPermissionRepository {
  private static final String RESOURCE_PERMISSIONS_YAML_PATH =
      "io/harness/accesscontrol/resources/resource-permissions-exception-mapping.yml";

  private final MongoTemplate mongoTemplate;
  private final Map<String, Set<String>> permissionToResourceTypeMapping;

  @Inject
  public InMemoryPermissionRepository(@Named("mongoTemplate") MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
    this.permissionToResourceTypeMapping = loadPermissionToResourceMapping();
  }

  private Map<String, Set<String>> loadPermissionToResourceMapping() {
    return loadPermissionToResourceMapping(loadExplicitPermissionToResourceMapping());
  }

  @VisibleForTesting
  public InMemoryPermissionRepository(
      MongoTemplate mongoTemplate, Map<String, Set<String>> explicitPermissionToResourceTypeMapping) {
    this.mongoTemplate = mongoTemplate;
    this.permissionToResourceTypeMapping = loadPermissionToResourceMapping(explicitPermissionToResourceTypeMapping);
  }

  @VisibleForTesting
  private Map<String, Set<String>> loadPermissionToResourceMapping(
      Map<String, Set<String>> explicitPermissionToResourceTypeMapping) {
    Map<String, Set<String>> mapping = new HashMap<>(explicitPermissionToResourceTypeMapping);

    loadImplicitPermissionToResourceMapping().forEach((key, value) -> {
      if (nonNull(mapping.get(key))) {
        mapping.get(key).add(value);
      } else {
        Set<String> resources = new HashSet<>();
        resources.add(value);
        mapping.put(key, resources);
      }
    });
    return mapping;
  }

  private Map<String, Set<String>> loadExplicitPermissionToResourceMapping() {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());
    ResourcePermissionsMapping resourcePermissionsMapping;
    try {
      URL url = getClass().getClassLoader().getResource(RESOURCE_PERMISSIONS_YAML_PATH);
      byte[] bytes = Resources.toByteArray(url);
      resourcePermissionsMapping = om.readValue(bytes, new TypeReference<>() {});
    } catch (IOException e) {
      throw new InvalidRequestException("Permissions file path is invalid or the syntax is incorrect", e);
    }
    // TODO: We can validate resource identifiers are valid or not

    Map<String, Set<String>> explicitPermissionResourceMapping = new HashMap<>();
    resourcePermissionsMapping.getResourceToPermissionsExceptionMapping().forEach(resourcePermissions -> {
      resourcePermissions.getPermissions().forEach(permission -> {
        if (explicitPermissionResourceMapping.get(permission) != null) {
          explicitPermissionResourceMapping.get(permission).add(resourcePermissions.getResourceIdentifier());
        } else {
          Set<String> resources = new HashSet<>();
          resources.add(resourcePermissions.getResourceIdentifier());
          explicitPermissionResourceMapping.put(permission, resources);
        }
      });
    });
    return explicitPermissionResourceMapping;
  }

  private Map<String, String> loadImplicitPermissionToResourceMapping() {
    List<PermissionDBO> permissionDBOS = mongoTemplate.findAll(PermissionDBO.class);
    if (isEmpty(permissionDBOS)) {
      return new HashMap<>();
    }
    List<ResourceTypeDBO> resourceTypeDBOs = mongoTemplate.findAll(ResourceTypeDBO.class);
    if (isEmpty(resourceTypeDBOs)) {
      return new HashMap<>();
    }
    Map<String, String> resourceTypeMapping =
        resourceTypeDBOs.stream().collect(toMap(ResourceTypeDBO::getPermissionKey, ResourceTypeDBO::getIdentifier));
    return permissionDBOS.stream()
        .map(PermissionDBOMapper::fromDBO)
        .collect(toMap(
            Permission::getIdentifier, permission -> resourceTypeMapping.get(permission.getPermissionMetadata(1))));
  }

  public boolean isPermissionCompatibleWithResourceSelector(String permission, String resourceSelector) {
    String resourceTypeFromSelector = getResourceTypeFromSelector(resourceSelector);

    Set<String> matchedResourceTypes = getResourceTypesApplicableToPermission(permission)
                                           .stream()
                                           .filter(resourceTypeFromSelector::equalsIgnoreCase)
                                           .collect(toSet());

    return ALL_RESOURCES_IDENTIFIER.equals(resourceTypeFromSelector) || isNotEmpty(matchedResourceTypes);
  }

  private static String getResourceTypeFromSelector(String resourceSelector) {
    String[] split = resourceSelector.split(PATH_DELIMITER);
    String resourceTypeFromSelector = split[split.length - 2];
    return resourceTypeFromSelector;
  }

  public Set<String> getResourceTypesApplicableToPermission(String permission) {
    return permissionToResourceTypeMapping.get(permission);
  }

  @OwnedBy(HarnessTeam.PL)
  @Value
  @Builder
  public static class ResourcePermissions {
    String resourceIdentifier;
    List<String> permissions;
  }

  @OwnedBy(HarnessTeam.PL)
  @Value
  @Builder
  public static class ResourcePermissionsMapping {
    List<ResourcePermissions> resourceToPermissionsExceptionMapping;
  }
}
