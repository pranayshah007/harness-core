/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals.users;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.users.persistence.UserDao;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.annotations.dev.OwnedBy;
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
public class UserServiceImpl implements UserService {
  private final UserDao userDao;
  private final RoleAssignmentService roleAssignmentService;
  private final TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> deleteUserTransactionPolicy = PersistenceUtils.getRetryPolicy(
      "[Retrying]: Failed to delete user and corresponding role assignments; attempt: {}",
      "[Failed]: Failed to delete user and corresponding role assignments; attempt: {}");

  @Inject
  public UserServiceImpl(
      UserDao userDao, RoleAssignmentService roleAssignmentService, TransactionTemplate transactionTemplate) {
    this.userDao = userDao;
    this.roleAssignmentService = roleAssignmentService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public long saveAll(List<User> users) {
    return userDao.saveAll(users);
  }

  @Override
  public User createIfNotPresent(User user) {
    return userDao.createIfNotPresent(user);
  }

  @Override
  public PageResponse<User> list(PageRequest pageRequest, String scopeIdentifier) {
    return userDao.list(pageRequest, scopeIdentifier);
  }

  @Override
  public Optional<User> get(String identifier, String scopeIdentifier) {
    return userDao.get(identifier, scopeIdentifier);
  }

  @Override
  public Optional<User> deleteIfPresent(String identifier, String scopeIdentifier) {
    Optional<User> currentUserOptional = get(identifier, scopeIdentifier);
    if (currentUserOptional.isPresent()) {
      return Optional.of(deleteInternal(identifier, scopeIdentifier));
    }
    deleteUserRoleAssignments(identifier, scopeIdentifier);
    return Optional.empty();
  }

  private User deleteInternal(String identifier, String scopeIdentifier) {
    return Failsafe.with(deleteUserTransactionPolicy).get(() -> transactionTemplate.execute(status -> {
      long deleteCount = deleteUserRoleAssignments(identifier, scopeIdentifier);
      return userDao.delete(identifier, scopeIdentifier)
          .orElseThrow(()
                           -> new UnexpectedException(String.format(
                               "Failed to delete the user %s in the scope %s", identifier, scopeIdentifier)));
    }));
  }

  private long deleteUserRoleAssignments(String identifier, String scopeIdentifier) {
    return roleAssignmentService.deleteMulti(
        RoleAssignmentFilter.builder()
            .scopeFilter(scopeIdentifier)
            .principalFilter(Sets.newHashSet(
                Principal.builder().principalType(PrincipalType.USER).principalIdentifier(identifier).build()))
            .build());
  }
}
