/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.usergroups;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDao;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.springdata.PersistenceUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Slf4j
@Singleton
@ValidateOnExecution
public class UserGroupServiceImpl implements UserGroupService {
  private final UserGroupDao userGroupDao;
  private final RoleAssignmentService roleAssignmentService;
  private final ScopeService scopeService;
  private final TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> deleteUserGroupTransactionPolicy = PersistenceUtils.getRetryPolicy(
      "[Retrying]: Failed to delete user group and corresponding role assignments; attempt: {}",
      "[Failed]: Failed to delete user group and corresponding role assignments; attempt: {}");

  @Inject
  public UserGroupServiceImpl(UserGroupDao userGroupDao, RoleAssignmentService roleAssignmentService,
      ScopeService scopeService, TransactionTemplate transactionTemplate) {
    this.userGroupDao = userGroupDao;
    this.roleAssignmentService = roleAssignmentService;
    this.scopeService = scopeService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public UserGroup upsert(UserGroup userGroup) {
    return userGroupDao.upsert(userGroup);
  }

  @Override
  public PageResponse<UserGroup> list(PageRequest pageRequest, String scopeIdentifier) {
    return userGroupDao.list(pageRequest, scopeIdentifier);
  }

  @Override
  public List<UserGroup> list(String scopeIdentifier, String userIdentifier) {
    return userGroupDao.list(scopeIdentifier, userIdentifier);
  }

  @Override
  public Optional<UserGroup> get(String identifier, String scopeIdentifier) {
    return userGroupDao.get(identifier, scopeIdentifier);
  }

  @Override
  public UserGroup delete(String identifier, String scopeIdentifier) {
    Optional<UserGroup> currentUserGroupOptional = get(identifier, scopeIdentifier);
    if (!currentUserGroupOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Could not find the user group in the scope %s", scopeIdentifier));
    }
    return deleteInternal(identifier, scopeIdentifier);
  }

  @Override
  public void deleteIfPresent(String identifier, String scopeIdentifier) {
    Optional<UserGroup> currentUserGroupOptional = get(identifier, scopeIdentifier);
    if (currentUserGroupOptional.isPresent()) {
      deleteInternal(identifier, scopeIdentifier);
    }
  }

  private UserGroup deleteInternal(String identifier, String scopeIdentifier) {
    return Failsafe.with(deleteUserGroupTransactionPolicy).get(() -> transactionTemplate.execute(status -> {
      long deleteCount = roleAssignmentService.deleteMulti(
          RoleAssignmentFilter.builder()
              .scopeFilter(scopeIdentifier)
              .includeChildScopes(true)
              .principalFilter(Sets.newHashSet(
                  Principal.builder()
                      .principalType(PrincipalType.USER_GROUP)
                      .principalIdentifier(identifier)
                      .principalScopeLevel(
                          scopeService.buildScopeFromScopeIdentifier(scopeIdentifier).getLevel().toString())
                      .build()))
              .build());
      return userGroupDao.delete(identifier, scopeIdentifier)
          .orElseThrow(()
                           -> new UnexpectedException(String.format(
                               "Failed to delete the user group %s in the scope %s", identifier, scopeIdentifier)));
    }));
  }
}
