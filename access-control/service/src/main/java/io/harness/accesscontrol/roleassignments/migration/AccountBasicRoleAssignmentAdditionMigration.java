/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.migration;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.accesscontrol.commons.helpers.FeatureFlagHelperService;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.migration.NGMigration;
import io.harness.utils.CryptoUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class AccountBasicRoleAssignmentAdditionMigration implements NGMigration {
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final FeatureFlagHelperService featureFlagHelperService;
  private final HashMap<String, Boolean> featureFlagForAccounts;
  private final ScopeService scopeService;
  private static final String ACCOUNT_VIEWER = "_account_viewer";
  private static final String ACCOUNT_BASIC = "_account_basic";

  @Inject
  public AccountBasicRoleAssignmentAdditionMigration(RoleAssignmentRepository roleAssignmentRepository,
      FeatureFlagHelperService featureFlagHelperService, ScopeService scopeService) {
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.featureFlagHelperService = featureFlagHelperService;
    this.featureFlagForAccounts = new HashMap<>();
    this.scopeService = scopeService;
  }

  @Override
  public void migrate() {
    log.info("AccountBasicRoleAssignmentAdditionMigration starts ...");
    int pageSize = 1000;
    int pageIndex = 0;

    do {
      Pageable pageable = PageRequest.of(pageIndex, pageSize);
      Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.roleIdentifier)
                              .is(ACCOUNT_VIEWER)
                              .and(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                              .is(DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                              .and(RoleAssignmentDBOKeys.scopeLevel)
                              .is(HarnessScopeLevel.ACCOUNT.getName())
                              .and(RoleAssignmentDBOKeys.principalType)
                              .is(USER);

      List<RoleAssignmentDBO> roleAssignmentList =
          roleAssignmentRepository
              .findAll(criteria, pageable, Sort.by(Sort.Direction.ASC, RoleAssignmentDBOKeys.createdAt))
              .getContent();
      if (isEmpty(roleAssignmentList)) {
        break;
      }
      for (RoleAssignmentDBO roleAssignment : roleAssignmentList) {
        if (roleAssignment.isManaged()) {
          String accountId =
              scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier()).getInstanceId();
          try {
            featureFlagForAccounts.computeIfAbsent(
                accountId, accId -> featureFlagHelperService.isEnabled(FeatureName.ACCOUNT_BASIC_ROLE, accId));
            if (Boolean.TRUE.equals(featureFlagForAccounts.get(accountId))) {
              RoleAssignmentDBO newRoleAssignmentDBO = buildRoleAssignmentDBO(roleAssignment);
              try {
                roleAssignmentRepository.save(newRoleAssignmentDBO);
              } catch (DuplicateKeyException e) {
                log.error("Corresponding account basic was already created {}", newRoleAssignmentDBO.toString(), e);
              }
              roleAssignmentRepository.updateById(roleAssignment.getId(), update(RoleAssignmentDBOKeys.managed, false));
            }
          } catch (Exception exception) {
            log.error("[AccountBasicRoleAssignmentAdditionMigration] Unexpected error occurred.", exception);
          }
        }
      }
      pageIndex++;
    } while (true);
    log.info("AccountBasicRoleAssignmentAdditionMigration completed.");
  }

  private RoleAssignmentDBO buildRoleAssignmentDBO(RoleAssignmentDBO roleAssignmentDBO) {
    return RoleAssignmentDBO.builder()
        .identifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
        .scopeIdentifier(roleAssignmentDBO.getScopeIdentifier())
        .scopeLevel(roleAssignmentDBO.getScopeLevel())
        .disabled(roleAssignmentDBO.isDisabled())
        .managed(true)
        .roleIdentifier(ACCOUNT_BASIC)
        .resourceGroupIdentifier(roleAssignmentDBO.getResourceGroupIdentifier())
        .principalScopeLevel(roleAssignmentDBO.getPrincipalScopeLevel())
        .principalIdentifier(roleAssignmentDBO.getPrincipalIdentifier())
        .principalType(roleAssignmentDBO.getPrincipalType())
        .build();
  }
}
