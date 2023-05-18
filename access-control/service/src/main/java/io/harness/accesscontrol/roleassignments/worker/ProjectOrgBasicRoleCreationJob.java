/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.worker;

import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.authorization.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;
import static io.harness.beans.FeatureName.PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.utils.CryptoUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class ProjectOrgBasicRoleCreationJob implements Runnable {
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final FeatureFlagService featureFlagService;
  private final AccountUtils accountUtils;
  private final ScopeService scopeService;
  private final PersistentLocker persistentLocker;
  private final String DEBUG_MESSAGE = "ProjectOrgBasicRoleCreationJob: ";
  private static final String LOCK_NAME = "ProjectOrgBasicRoleCreationJobLock";

  private static final String ORGANIZATION_VIEWER = "_organization_viewer";
  private static final String PROJECT_VIEWER = "_project_viewer";

  private static final String ORGANIZATION_BASIC = "_organization_basic";
  private static final String PROJECT_BASIC = "_project_basic";

  @Inject
  public ProjectOrgBasicRoleCreationJob(RoleAssignmentRepository roleAssignmentRepository,
      FeatureFlagService featureFlagService, AccountUtils accountUtils, ScopeService scopeService,
      PersistentLocker persistentLocker) {
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.featureFlagService = featureFlagService;
    this.accountUtils = accountUtils;
    this.scopeService = scopeService;
    this.persistentLocker = persistentLocker;
  }

  @Override
  public void run() {
    log.info(DEBUG_MESSAGE + "started...");
    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(DEBUG_MESSAGE + "failed to acquire lock");
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(ACCESS_CONTROL_SERVICE.getServiceId()));
        log.info(DEBUG_MESSAGE + "Setting SecurityContext completed.");
        execute();
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
        log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
    log.info(DEBUG_MESSAGE + " completed...");
  }

  private void execute() {
    List<String> targetAccounts = getFFEnabledAccounts();
    if (isEmpty(targetAccounts)) {
      return;
    }
    try {
      for (String accountId : targetAccounts) {
        Pattern startsWithScope = Pattern.compile("^".concat("/ACCOUNT/" + accountId));
        Criteria projectCriteria = Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier)
                                       .regex(startsWithScope)
                                       .and(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                                       .is(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                                       .and(RoleAssignmentDBOKeys.roleIdentifier)
                                       .is(PROJECT_VIEWER)
                                       .and(RoleAssignmentDBOKeys.principalIdentifier)
                                       .is(DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER)

                                       .and(RoleAssignmentDBOKeys.principalScopeLevel)
                                       .is(HarnessScopeLevel.PROJECT.getName())
                                       .and(RoleAssignmentDBOKeys.principalType)
                                       .is(USER_GROUP);
        Criteria orgCriteria = Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier)
                                   .regex(startsWithScope)
                                   .and(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                                   .is(DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                                   .and(RoleAssignmentDBOKeys.roleIdentifier)
                                   .is(ORGANIZATION_VIEWER)

                                   .and(RoleAssignmentDBOKeys.principalIdentifier)
                                   .is(DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER)

                                   .and(RoleAssignmentDBOKeys.principalScopeLevel)
                                   .is(HarnessScopeLevel.ORGANIZATION.getName())
                                   .and(RoleAssignmentDBOKeys.principalType)
                                   .is(USER_GROUP);
        addBasicRoleToDefaultUserGroup(projectCriteria, PROJECT_BASIC);
        addBasicRoleToDefaultUserGroup(orgCriteria, ORGANIZATION_BASIC);
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + "Failed to create basic role for org/project", ex);
    }
  }

  private List<String> getFFEnabledAccounts() {
    List<String> accountIds = accountUtils.getAllAccountIds();
    List<String> targetAccounts = new ArrayList<>();
    try {
      for (String accountId : accountIds) {
        boolean isBasicRoleCreationEnabled =
            featureFlagService.isEnabled(PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS, accountId);
        if (isBasicRoleCreationEnabled) {
          targetAccounts.add(accountId);
        }
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + "Failed to filter accounts for FF PL_ENABLE_BASIC_ROLE_FOR_PROJECTS_ORGS");
    }
    return targetAccounts;
  }

  private void addBasicRoleToDefaultUserGroup(Criteria criteria, String roleIdentifier) {
    int pageSize = 1000;
    int pageIndex = 0;

    do {
      Pageable pageable = PageRequest.of(pageIndex, pageSize);
      List<RoleAssignmentDBO> roleAssignmentList =
          roleAssignmentRepository
              .findAll(criteria, pageable, Sort.by(Sort.Direction.ASC, RoleAssignmentDBOKeys.createdAt))
              .getContent();
      if (isEmpty(roleAssignmentList)) {
        break;
      }
      for (RoleAssignmentDBO roleAssignment : roleAssignmentList) {
        if (roleAssignment.isManaged()) {
          RoleAssignmentDBO newRoleAssignmentDBO = buildRoleAssignmentDBO(roleAssignment, roleIdentifier);
          try {
            try {
              roleAssignmentRepository.save(newRoleAssignmentDBO);
            } catch (DuplicateKeyException e) {
              log.warn("[ProjectOrgBasicRoleCreationJob]: Corresponding basic role assigment was already created {}",
                  newRoleAssignmentDBO.toString(), e);
            }
            roleAssignmentRepository.updateById(roleAssignment.getId(), update(RoleAssignmentDBOKeys.managed, false));
          } catch (Exception exception) {
            log.error("[ProjectOrgBasicRoleCreationJob] Unexpected error occurred.", exception);
          }
        }
      }
      log.info("[ProjectOrgBasicRoleCreationJob] Migration for page {} with  completed successfully.", pageIndex,
          roleAssignmentList.size());
      pageIndex++;
    } while (true);
  }

  private RoleAssignmentDBO buildRoleAssignmentDBO(RoleAssignmentDBO roleAssignmentDBO, String roleIdentifier) {
    return RoleAssignmentDBO.builder()
        .identifier("role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20)))
        .scopeIdentifier(roleAssignmentDBO.getScopeIdentifier())
        .scopeLevel(roleAssignmentDBO.getScopeLevel())
        .disabled(roleAssignmentDBO.isDisabled())
        .managed(true)
        .internal(true)
        .roleIdentifier(roleIdentifier)
        .resourceGroupIdentifier(roleAssignmentDBO.getResourceGroupIdentifier())
        .principalScopeLevel(roleAssignmentDBO.getPrincipalScopeLevel())
        .principalIdentifier(roleAssignmentDBO.getPrincipalIdentifier())
        .principalType(roleAssignmentDBO.getPrincipalType())
        .build();
  }
}
