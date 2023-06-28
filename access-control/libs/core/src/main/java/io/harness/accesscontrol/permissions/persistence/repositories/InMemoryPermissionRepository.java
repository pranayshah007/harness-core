/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.permissions.persistence.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.stream.Collectors.toMap;

import io.harness.accesscontrol.permissions.Permission;
import io.harness.accesscontrol.permissions.persistence.PermissionDBO;
import io.harness.accesscontrol.permissions.persistence.PermissionDBOMapper;
import io.harness.accesscontrol.resources.resourcetypes.persistence.ResourceTypeDBO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class InMemoryPermissionRepository {
  private MongoTemplate mongoTemplate;
  private Map<String, String> permissionToResourceTypeMapping;

  @Inject
  public InMemoryPermissionRepository(@Named("mongoTemplate") MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
    this.permissionToResourceTypeMapping = new HashMap<>();
  }

  private Map<String, String> loadPermissionToResourceTypeMapping() {
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

  public String getResourceTypeBy(String permission) {
    if (isEmpty(permissionToResourceTypeMapping)) {
      permissionToResourceTypeMapping = loadPermissionToResourceTypeMapping();
    }
    return permissionToResourceTypeMapping.get(permission);
  }
}
