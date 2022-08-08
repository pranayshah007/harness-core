/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.serviceaccounts;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.serviceaccounts.persistence.ServiceAccountDao;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.utils.RetryUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Slf4j
@Singleton
@ValidateOnExecution
public class ServiceAccountServiceImpl implements ServiceAccountService {
  private final ServiceAccountDao serviceAccountDao;
  private final RoleAssignmentService roleAssignmentService;
  private final ScopeService scopeService;
  private final TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> deleteServiceAccountTransactionPolicy = RetryUtils.getRetryPolicy(
      "[Retrying]: Failed to delete service account and corresponding role assignments; attempt: {}",
      "[Failed]: Failed to delete service account and corresponding role assignments; attempt: {}",
      ImmutableList.of(TransactionException.class), Duration.ofSeconds(5), 3, log);

  @Inject
  public ServiceAccountServiceImpl(ServiceAccountDao serviceAccountDao, TransactionTemplate transactionTemplate,
      RoleAssignmentService roleAssignmentService, ScopeService scopeService) {
    this.serviceAccountDao = serviceAccountDao;
    this.transactionTemplate = transactionTemplate;
    this.roleAssignmentService = roleAssignmentService;
    this.scopeService = scopeService;
  }

  @Override
  public ServiceAccount createIfNotPresent(ServiceAccount serviceAccount) {
    return serviceAccountDao.createIfNotPresent(serviceAccount);
  }

  @Override
  public PageResponse<ServiceAccount> list(PageRequest pageRequest, String scopeIdentifier) {
    return serviceAccountDao.list(pageRequest, scopeIdentifier);
  }

  @Override
  public Optional<ServiceAccount> get(String identifier, String scopeIdentifier) {
    return serviceAccountDao.get(identifier, scopeIdentifier);
  }

  @Override
  public Optional<ServiceAccount> deleteIfPresent(String identifier, String scopeIdentifier) {
    Optional<ServiceAccount> optionalServiceAccount = get(identifier, scopeIdentifier);
    if (optionalServiceAccount.isPresent()) {
      deleteInternal(identifier, scopeIdentifier);
      return optionalServiceAccount;
    }
    return Optional.empty();
  }

  private Optional<ServiceAccount> deleteInternal(String identifier, String scopeIdentifier) {
    return Failsafe.with(deleteServiceAccountTransactionPolicy).get(() -> transactionTemplate.execute(status -> {
      deleteServiceAccountRoleAssignments(identifier, scopeIdentifier);
      return serviceAccountDao.delete(identifier, scopeIdentifier);
    }));
  }

  private long deleteServiceAccountRoleAssignments(String identifier, String scopeIdentifier) {
    return roleAssignmentService.deleteMulti(
        RoleAssignmentFilter.builder()
            .scopeFilter(scopeIdentifier)
            .includeChildScopes(true)
            .principalFilter(Sets.newHashSet(
                Principal.builder()
                    .principalType(PrincipalType.SERVICE_ACCOUNT)
                    .principalIdentifier(identifier)
                    .principalScopeLevel(
                        scopeService.buildScopeFromScopeIdentifier(scopeIdentifier).getLevel().toString())
                    .build()))
            .build());
  }
}
