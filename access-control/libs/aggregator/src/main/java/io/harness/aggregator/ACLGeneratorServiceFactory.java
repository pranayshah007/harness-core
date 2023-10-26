/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.consumers.ACLGeneratorService;
import io.harness.aggregator.consumers.ACLGeneratorServiceImpl;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.tuple.Pair;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@OwnedBy(PL)
public class ACLGeneratorServiceFactory implements Provider<ACLGeneratorService> {
  public static final String SECONDARY_ACL_GENERATOR_SERVICE = "secondary_acl_generator_service";

  private final RoleService roleService;
  private final UserGroupService userGroupService;
  private final ResourceGroupService resourceGroupService;
  private final ScopeService scopeService;
  private final Map<Pair<ScopeLevel, Boolean>, Set<String>> implicitPermissionsByScope;
  private final ACLRepository aclRepository;
  private final InMemoryPermissionRepository inMemoryPermissionRepository;
  private final int batchSizeForACLCreation;

  public ACLGeneratorServiceFactory(RoleService roleService, UserGroupService userGroupService,
      ResourceGroupService resourceGroupService, ScopeService scopeService,
      Map<Pair<ScopeLevel, Boolean>, Set<String>> implicitPermissionsByScope, ACLRepository aclRepository,
      InMemoryPermissionRepository inMemoryPermissionRepository, int batchSizeForACLCreation) {
    this.roleService = roleService;
    this.userGroupService = userGroupService;
    this.resourceGroupService = resourceGroupService;
    this.scopeService = scopeService;
    this.implicitPermissionsByScope = implicitPermissionsByScope;
    this.aclRepository = aclRepository;
    this.inMemoryPermissionRepository = inMemoryPermissionRepository;
    this.batchSizeForACLCreation = batchSizeForACLCreation;
  }

  @Override
  public ACLGeneratorService get() {
    return new ACLGeneratorServiceImpl(roleService, userGroupService, resourceGroupService, scopeService,
        implicitPermissionsByScope, aclRepository, inMemoryPermissionRepository, batchSizeForACLCreation);
  }
}
