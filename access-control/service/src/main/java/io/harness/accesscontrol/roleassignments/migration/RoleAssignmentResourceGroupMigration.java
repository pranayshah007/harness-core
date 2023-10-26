/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.migration;

import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

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
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(PL)
public class RoleAssignmentResourceGroupMigration implements NGMigration {
  private final RoleAssignmentRepository roleAssignmentRepository;
  private static final String ALL_RESOURCES_RESOURCE_GROUP_IDENTIFIER = "_all_resources";

  @Inject
  public RoleAssignmentResourceGroupMigration(RoleAssignmentRepository roleAssignmentRepository) {
    this.roleAssignmentRepository = roleAssignmentRepository;
  }

  @Override
  public void migrate() {
    migrateInternal(HarnessScopeLevel.ACCOUNT);
    migrateInternal(HarnessScopeLevel.ORGANIZATION);
    migrateInternal(HarnessScopeLevel.PROJECT);
  }

  private void migrateInternal(ScopeLevel scopeLevel) {
    int pageSize = 1000;
    int pageIndex = 0;
    Pageable pageable = PageRequest.of(pageIndex, pageSize);
    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.scopeLevel)
                            .is(scopeLevel.toString())
                            .and(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                            .is(ALL_RESOURCES_RESOURCE_GROUP_IDENTIFIER);
    do {
      List<RoleAssignmentDBO> roleAssignmentList = roleAssignmentRepository.findAll(criteria, pageable).getContent();
      if (isEmpty(roleAssignmentList)) {
        return;
      }
      for (RoleAssignmentDBO roleAssignment : roleAssignmentList) {
        try {
          roleAssignmentRepository.save(buildRoleAssignmentDBO(scopeLevel, roleAssignment));
        } catch (DuplicateKeyException exception) {
          log.info("[RoleAssignmentResourceGroupMigration] RoleAssignment already exists.", exception);
        }
        roleAssignmentRepository.deleteById(roleAssignment.getId());
      }
    } while (true);
  }

  private RoleAssignmentDBO buildRoleAssignmentDBO(ScopeLevel scopeLevel, RoleAssignmentDBO roleAssignmentDBO) {
    return RoleAssignmentDBO.builder()
        .identifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
        .scopeIdentifier(roleAssignmentDBO.getScopeIdentifier())
        .scopeLevel(roleAssignmentDBO.getScopeLevel())
        .disabled(roleAssignmentDBO.isDisabled())
        .managed(roleAssignmentDBO.isManaged())
        .roleIdentifier(roleAssignmentDBO.getRoleIdentifier())
        .resourceGroupIdentifier(getResourceGroupIdentifier(scopeLevel))
        .principalIdentifier(roleAssignmentDBO.getPrincipalIdentifier())
        .principalType(roleAssignmentDBO.getPrincipalType())
        .createdAt(roleAssignmentDBO.getCreatedAt())
        .createdBy(roleAssignmentDBO.getCreatedBy())
        .build();
  }

  private String getResourceGroupIdentifier(ScopeLevel scopeLevel) {
    if (HarnessScopeLevel.PROJECT.equals(scopeLevel)) {
      return DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    } else if (HarnessScopeLevel.ORGANIZATION.equals(scopeLevel)) {
      return DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    } else {
      return DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    }
  }
}
