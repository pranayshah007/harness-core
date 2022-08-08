/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.migration;

import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.accesscontrol.principals.serviceaccounts.HarnessServiceAccountService;
import io.harness.accesscontrol.principals.serviceaccounts.ServiceAccountService;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(PL)
public class RoleAssignmentPrincipalScopeLevelMigration implements NGMigration {
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final ScopeService scopeService;
  private final HarnessServiceAccountService harnessServiceAccountService;
  private final ServiceAccountService serviceAccountService;

  @Inject
  public RoleAssignmentPrincipalScopeLevelMigration(RoleAssignmentRepository roleAssignmentRepository,
      ScopeService scopeService, HarnessServiceAccountService harnessServiceAccountService,
      ServiceAccountService serviceAccountService) {
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.scopeService = scopeService;
    this.harnessServiceAccountService = harnessServiceAccountService;
    this.serviceAccountService = serviceAccountService;
  }

  @Override
  public void migrate() {
    log.info("RoleAssignmentPrincipalScopeLevelMigration starts ...");
    int pageSize = 1000;
    int pageIndex = 0;
    Pageable pageable = PageRequest.of(pageIndex, pageSize);
    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.principalType)
                            .in(Arrays.asList(USER_GROUP, SERVICE_ACCOUNT))
                            .and(RoleAssignmentDBOKeys.principalScopeLevel)
                            .is(null);
    do {
      List<RoleAssignmentDBO> roleAssignmentList = roleAssignmentRepository.findAll(criteria, pageable).getContent();
      if (isEmpty(roleAssignmentList)) {
        break;
      }
      for (RoleAssignmentDBO roleAssignment : roleAssignmentList) {
        try {
          roleAssignmentRepository.save(buildRoleAssignmentDBO(roleAssignment));
        } catch (DuplicateKeyException exception) {
          log.warn("[RoleAssignmentPrincipalScopeLevelMigration] RoleAssignment already exists.", exception);
        } catch (Exception exception) {
          log.error("[RoleAssignmentPrincipalScopeLevelMigration] Unexpected error occurred.", exception);
        }
      }
    } while (true);
    log.info("RoleAssignmentPrincipalScopeLevelMigration completed.");
  }

  private RoleAssignmentDBO buildRoleAssignmentDBO(RoleAssignmentDBO roleAssignmentDBO) {
    String principalScopeLevel;
    if (USER_GROUP.equals(roleAssignmentDBO.getPrincipalType())) {
      principalScopeLevel = roleAssignmentDBO.getScopeLevel();
    } else {
      principalScopeLevel = getServiceAccountScopeLevel(roleAssignmentDBO.getPrincipalIdentifier(),
          scopeService.buildScopeFromScopeIdentifier(roleAssignmentDBO.getScopeIdentifier()));
    }
    return RoleAssignmentDBO.builder()
        .id(roleAssignmentDBO.getId())
        .identifier(roleAssignmentDBO.getIdentifier())
        .scopeIdentifier(roleAssignmentDBO.getScopeIdentifier())
        .scopeLevel(roleAssignmentDBO.getScopeLevel())
        .disabled(roleAssignmentDBO.isDisabled())
        .managed(roleAssignmentDBO.isManaged())
        .roleIdentifier(roleAssignmentDBO.getRoleIdentifier())
        .resourceGroupIdentifier(roleAssignmentDBO.getResourceGroupIdentifier())
        .principalScopeLevel(principalScopeLevel)
        .principalIdentifier(roleAssignmentDBO.getPrincipalIdentifier())
        .principalType(roleAssignmentDBO.getPrincipalType())
        .createdAt(roleAssignmentDBO.getCreatedAt())
        .createdBy(roleAssignmentDBO.getCreatedBy())
        .lastModifiedAt(roleAssignmentDBO.getLastModifiedAt())
        .lastUpdatedBy(roleAssignmentDBO.getLastUpdatedBy())
        .version(roleAssignmentDBO.getVersion())
        .build();
  }

  private String getServiceAccountScopeLevel(@NotNull String serviceAccountIdentifier, @NotNull Scope scope) {
    HarnessScopeParams scopeParams = ScopeMapper.toParams(scope);
    Scope serviceAccountScope = scope;
    while (serviceAccountScope != null) {
      harnessServiceAccountService.sync(serviceAccountIdentifier, scope);
      serviceAccountScope = serviceAccountScope.getParentScope();
    }
    Scope accountScope = ScopeMapper.fromParams(
        HarnessScopeParams.builder().accountIdentifier(scopeParams.getAccountIdentifier()).build());
    if (serviceAccountService.get(serviceAccountIdentifier, accountScope.toString()).isPresent()) {
      return accountScope.getLevel().toString();
    }
    Scope orgScope = ScopeMapper.fromParams(HarnessScopeParams.builder()
                                                .accountIdentifier(scopeParams.getAccountIdentifier())
                                                .orgIdentifier(scopeParams.getOrgIdentifier())
                                                .build());
    if (serviceAccountService.get(serviceAccountIdentifier, orgScope.toString()).isPresent()) {
      return orgScope.getLevel().toString();
    }
    return scope.getLevel().toString();
  }
}
