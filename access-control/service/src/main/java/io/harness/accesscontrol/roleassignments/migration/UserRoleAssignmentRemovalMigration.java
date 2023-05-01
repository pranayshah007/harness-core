/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.migration;

import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.ORGANIZATION_VIEWER_ROLE;
import static io.harness.NGConstants.PROJECT_VIEWER_ROLE;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.authorization.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;
import static io.harness.beans.FeatureName.ACCOUNT_BASIC_ROLE_ONLY;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGConstants;
import io.harness.accesscontrol.commons.helpers.FeatureFlagHelperService;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class UserRoleAssignmentRemovalMigration implements NGMigration {
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final FeatureFlagHelperService featureFlagHelperService;
  private final AccountUtils accountUtils;
  private final ScopeService scopeService;
  private static final String DEBUG_MESSAGE = "UserRoleAssignmentRemovalMigration: ";

  @Inject
  public UserRoleAssignmentRemovalMigration(RoleAssignmentRepository roleAssignmentRepository,
      FeatureFlagHelperService featureFlagHelperService, AccountUtils accountUtils, ScopeService scopeService) {
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.featureFlagHelperService = featureFlagHelperService;
    this.accountUtils = accountUtils;
    this.scopeService = scopeService;
  }

  @Override
  public void migrate() {
    log.info(DEBUG_MESSAGE + "started...");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(ACCESS_CONTROL_SERVICE.getServiceId()));
      log.info(DEBUG_MESSAGE + "Setting SecurityContext completed.");
      doMigration();
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
      log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
    }
    log.info(DEBUG_MESSAGE + " completed...");
  }

  private void doMigration() {
    List<String> ngEnabledAccountIds = accountUtils.getAllNGAccountIds();
    log.info(DEBUG_MESSAGE + String.format("%s accounts fetched", ngEnabledAccountIds.size()));
    HashSet<String> targetAccounts = new HashSet<>();
    HashSet<String> targetAccountsWithOrganizationAndProject = new HashSet<>();
    for (String accountId : ngEnabledAccountIds) {
      boolean isAccountBasicRoleOnlyEnabled = featureFlagHelperService.isEnabled(ACCOUNT_BASIC_ROLE_ONLY, accountId);
      if (!isAccountBasicRoleOnlyEnabled) {
        targetAccounts.add(accountId);
      }
      targetAccountsWithOrganizationAndProject.add(accountId);
    }
    List<String> filteredAccounts = filterAccounts(targetAccounts);
    if (isNotEmpty(filteredAccounts)) {
      deleteAccountScopeRoleAssignmentsInBatch(filteredAccounts);
    }
    if (isNotEmpty(targetAccountsWithOrganizationAndProject)) {
      deleteOrganizationScopeRoleAssignments(targetAccountsWithOrganizationAndProject);
      deleteProjectScopeRoleAssignments(targetAccountsWithOrganizationAndProject);
    }
  }

  private void deleteAccountScopeRoleAssignmentsInBatch(List<String> accountIds) {
    Streams.stream(Iterables.partition(accountIds, 1000)).forEach(list -> {
      try {
        deleteAccountScopeRoleAssignments(list);
        Thread.sleep(10000);
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE
            + String.format("Error while waking up. Failed to delete Role assignments for accounts %s", list));
      }
    });
  }

  private void deleteAccountScopeRoleAssignments(List<String> accountIds) {
    try {
      List<String> scopeIdentifiers =
          accountIds.stream().map(accId -> "/ACCOUNT/" + accId).collect(Collectors.toList());
      Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier)
                              .in(scopeIdentifiers)
                              .and(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                              .is(DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                              .and(RoleAssignmentDBOKeys.roleIdentifier)
                              .in(NGConstants.ACCOUNT_BASIC_ROLE, NGConstants.ACCOUNT_VIEWER_ROLE)
                              .and(RoleAssignmentDBOKeys.scopeLevel)
                              .is(HarnessScopeLevel.ACCOUNT.getName())
                              .and(RoleAssignmentDBOKeys.principalType)
                              .is(USER);

      long count = roleAssignmentRepository.deleteMulti(criteria);
      log.info(DEBUG_MESSAGE
          + String.format("removed Account scope %s Role Assignments for accounts: %s", count, accountIds));
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + String.format("Failed to delete Role assignments for accounts %s", accountIds));
    }
  }

  private void deleteOrganizationScopeRoleAssignments(HashSet<String> accountIds) {
    for (String accountId : accountIds) {
      try {
        Pattern startsWithScope = Pattern.compile("^".concat("/ACCOUNT/" + accountId));
        Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier)
                                .regex(startsWithScope)
                                .and(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                                .is(DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                                .and(RoleAssignmentDBOKeys.roleIdentifier)
                                .is(ORGANIZATION_VIEWER_ROLE)
                                .and(RoleAssignmentDBOKeys.principalType)
                                .is(USER)
                                .and(RoleAssignmentDBOKeys.scopeLevel)
                                .is(HarnessScopeLevel.ORGANIZATION.getName())
                                .and(RoleAssignmentDBOKeys.managed)
                                .is(true);

        long count = roleAssignmentRepository.deleteMulti(criteria);
        log.info(DEBUG_MESSAGE
            + String.format("removed Organization scope %s Role Assignment in account %s", count, accountId));
        Thread.sleep(100);
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE
            + String.format("Failed to delete role assignments for organization of account %s", accountId));
      }
    }
  }

  private void deleteProjectScopeRoleAssignments(HashSet<String> accountIds) {
    for (String accountId : accountIds) {
      try {
        Pattern startsWithScope = Pattern.compile("^".concat("/ACCOUNT/" + accountId));
        Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier)
                                .regex(startsWithScope)
                                .and(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                                .is(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                                .and(RoleAssignmentDBOKeys.roleIdentifier)
                                .is(PROJECT_VIEWER_ROLE)
                                .and(RoleAssignmentDBOKeys.principalType)
                                .is(USER)
                                .and(RoleAssignmentDBOKeys.scopeLevel)
                                .is(HarnessScopeLevel.PROJECT.getName())
                                .and(RoleAssignmentDBOKeys.managed)
                                .is(true);

        long count = roleAssignmentRepository.deleteMulti(criteria);
        log.info(
            DEBUG_MESSAGE + String.format("Removed Project scope %s role assignments in account %s", count, accountId));
        Thread.sleep(100);
      } catch (Exception ex) {
        log.error(
            DEBUG_MESSAGE + String.format("Failed to delete role assignments for project of account %s", accountId));
      }
    }
  }

  private List<String> filterAccounts(HashSet<String> accountIds) {
    return Streams.stream(Iterables.partition(accountIds, 100))
        .flatMap(list -> filterAccountsPaginated(list).stream())
        .collect(Collectors.toList());
  }

  private List<String> filterAccountsPaginated(List<String> accountIds) {
    List<String> filteredAccounts = new ArrayList<>();
    List<String> scopeIdentifiers = new ArrayList<>();
    for (String accountId : accountIds) {
      String scopeIdentifier = "/ACCOUNT/" + accountId;
      scopeIdentifiers.add(scopeIdentifier);
    }
    try {
      Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier)
                              .in(scopeIdentifiers)
                              .and(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                              .is(DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                              .and(RoleAssignmentDBOKeys.roleIdentifier)
                              .in(NGConstants.ACCOUNT_VIEWER_ROLE)
                              .and(RoleAssignmentDBOKeys.principalIdentifier)
                              .is(DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER)
                              .and(RoleAssignmentDBOKeys.principalScopeLevel)
                              .is(HarnessScopeLevel.ACCOUNT.getName())
                              .and(RoleAssignmentDBOKeys.principalType)
                              .is(USER_GROUP)
                              .and(RoleAssignmentDBOKeys.scopeLevel)
                              .is(HarnessScopeLevel.ACCOUNT.getName());

      Pageable pageable = Pageable.unpaged();
      List<RoleAssignmentDBO> roleAssignmentDBOList = roleAssignmentRepository.findAll(criteria, pageable).getContent();
      // If role assignment doesn't exist on Default User Group at account then skip removing User assigned role
      // assignment. So this list will contain AccountIds only having Default User Group.
      for (RoleAssignmentDBO roleAssignmentDBO : roleAssignmentDBOList) {
        Scope accountScope = scopeService.buildScopeFromScopeIdentifier(roleAssignmentDBO.getScopeIdentifier());
        filteredAccounts.add(accountScope.getInstanceId());
      }
      log.info(DEBUG_MESSAGE + String.format("Account Ids for which to remove role assignments %s", filteredAccounts));
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE
          + String.format("Failed to query role assignments of default user group for accounts %s", accountIds));
    }
    return filteredAccounts;
  }
}
