/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.migration;

import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.roles.HarnessRoleConstants.ACCOUNT_ADMIN_ROLE;
import static io.harness.accesscontrol.roles.HarnessRoleConstants.ORGANIZATION_ADMIN_ROLE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.threading.Morpheus.sleep;

import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.utils.CryptoUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(PL)
public class SuperAdminViaAllResourcesMigration implements NGMigration {
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final HarnessResourceGroupService harnessResourceGroupService;
  private final ResourceGroupService resourceGroupService;

  @Inject
  public SuperAdminViaAllResourcesMigration(RoleAssignmentRepository roleAssignmentRepository,
      HarnessResourceGroupService harnessResourceGroupService, ResourceGroupService resourceGroupService) {
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.harnessResourceGroupService = harnessResourceGroupService;
    this.resourceGroupService = resourceGroupService;
  }

  @Override
  public void migrate() {
    while (!isAllResourcesResourceGroupUpdated()) {
      harnessResourceGroupService.sync(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER, null);
      harnessResourceGroupService.sync(DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER, null);
      harnessResourceGroupService.sync(DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER, null);
      harnessResourceGroupService.sync(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER, null);
      sleep(Duration.ofSeconds(30));
    }
    migrateInternal(HarnessScopeLevel.ACCOUNT);
    migrateInternal(HarnessScopeLevel.ORGANIZATION);
  }

  private boolean isAllResourcesResourceGroupUpdated() {
    Optional<ResourceGroup> resourceGroupOptional = resourceGroupService.get(
        ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER, null, ManagedFilter.ONLY_MANAGED);
    if (resourceGroupOptional.isPresent()) {
      ResourceGroup resourceGroup = resourceGroupOptional.get();
      return resourceGroup.getResourceSelectors() != null && resourceGroup.getResourceSelectors().contains("/**/*/*");
    }
    return false;
  }

  private void migrateInternal(ScopeLevel scopeLevel) {
    int pageSize = 1000;
    int pageIndex = 0;
    Pageable pageable = PageRequest.of(pageIndex, pageSize);
    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.scopeLevel)
                            .is(scopeLevel.toString())
                            .and(RoleAssignmentDBOKeys.roleIdentifier)
                            .is(getRoleIdentifier(scopeLevel))
                            .and(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                            .is(getResourceGroupIdentifier(scopeLevel));
    do {
      List<RoleAssignmentDBO> roleAssignmentList = roleAssignmentRepository.findAll(criteria, pageable).getContent();
      if (isEmpty(roleAssignmentList)) {
        return;
      }
      for (RoleAssignmentDBO roleAssignment : roleAssignmentList) {
        try {
          roleAssignmentRepository.save(buildRoleAssignmentDBO(roleAssignment));
        } catch (DuplicateKeyException exception) {
          log.info("[SuperAdminViaAllResourcesMigration] RoleAssignment already exists.", exception);
        }
        roleAssignmentRepository.deleteById(roleAssignment.getId());
      }
    } while (true);
  }

  private RoleAssignmentDBO buildRoleAssignmentDBO(RoleAssignmentDBO roleAssignmentDBO) {
    return RoleAssignmentDBO.builder()
        .identifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
        .scopeIdentifier(roleAssignmentDBO.getScopeIdentifier())
        .scopeLevel(roleAssignmentDBO.getScopeLevel())
        .disabled(roleAssignmentDBO.isDisabled())
        .managed(roleAssignmentDBO.isManaged())
        .roleIdentifier(roleAssignmentDBO.getRoleIdentifier())
        .resourceGroupIdentifier(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER)
        .principalIdentifier(roleAssignmentDBO.getPrincipalIdentifier())
        .principalType(roleAssignmentDBO.getPrincipalType())
        .createdAt(roleAssignmentDBO.getCreatedAt())
        .createdBy(roleAssignmentDBO.getCreatedBy())
        .build();
  }

  private String getResourceGroupIdentifier(ScopeLevel scopeLevel) {
    if (HarnessScopeLevel.ORGANIZATION.equals(scopeLevel)) {
      return DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    } else {
      return DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    }
  }

  private String getRoleIdentifier(ScopeLevel scopeLevel) {
    if (HarnessScopeLevel.ORGANIZATION.equals(scopeLevel)) {
      return ORGANIZATION_ADMIN_ROLE;
    } else {
      return ACCOUNT_ADMIN_ROLE;
    }
  }
}
