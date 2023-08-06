/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.models.UserGroupUpdateEventData;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.logging.DelayLogContext;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Singleton
@Slf4j
public class UserGroupChangeConsumer implements AccessControlChangeConsumer<UserGroupUpdateEventData> {
  private final ExecutorService executorService;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final ACLGeneratorService aclGeneratorService;
  private final ScopeService scopeService;
  private final ACLRepository aclRepository;

  @Inject
  public UserGroupChangeConsumer(@Named(ACL.PRIMARY_COLLECTION) ACLRepository aclRepository,
      RoleAssignmentRepository roleAssignmentRepository, ACLGeneratorService aclGeneratorService,
      ScopeService scopeService) {
    this.aclRepository = aclRepository;
    this.aclGeneratorService = aclGeneratorService;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
        new ThreadFactoryBuilder().setNameFormat("user-group-change-consumer-%d").build());
    this.scopeService = scopeService;
  }

  @Override
  public boolean consumeUpdateEvent(String id, UserGroupUpdateEventData userGroupUpdateEventData) {
    UserGroup userGroup = userGroupUpdateEventData.getUpdatedUserGroup();
    Set<String> usersAdded = isEmpty(userGroupUpdateEventData.getUsersAdded())
        ? Collections.emptySet()
        : userGroupUpdateEventData.getUsersAdded();
    Set<String> usersRemoved = isEmpty(userGroupUpdateEventData.getUsersRemoved())
        ? Collections.emptySet()
        : userGroupUpdateEventData.getUsersRemoved();
    if (isEmpty(usersAdded) && isEmpty(usersRemoved)) {
      return true;
    }
    long startTime = System.currentTimeMillis();
    Pattern startsWithScope = Pattern.compile("^".concat(userGroup.getScopeIdentifier()));
    String principalScopeLevel =
        scopeService.buildScopeFromScopeIdentifier(userGroup.getScopeIdentifier()).getLevel().toString();

    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.principalType)
                            .is(USER_GROUP)
                            .and(RoleAssignmentDBOKeys.principalIdentifier)
                            .is(userGroup.getIdentifier())
                            .and(RoleAssignmentDBOKeys.principalScopeLevel)
                            .is(principalScopeLevel)
                            .and(RoleAssignmentDBOKeys.scopeIdentifier)
                            .regex(startsWithScope);
    List<ReProcessRoleAssignmentOnUserGroupUpdateTask> tasksToExecute =
        roleAssignmentRepository.findAll(criteria, Pageable.ofSize(10000))
            .stream()
            .map((RoleAssignmentDBO roleAssignment)
                     -> new UserGroupChangeConsumer.ReProcessRoleAssignmentOnUserGroupUpdateTask(
                         aclGeneratorService, aclRepository, roleAssignment, usersAdded, usersRemoved))
            .collect(Collectors.toList());

    long numberOfACLsCreated = 0;
    long numberOfACLsDeleted = 0;

    try {
      for (Future<Result> future : executorService.invokeAll(tasksToExecute)) {
        Result result = future.get();
        numberOfACLsCreated += result.getNumberOfACLsCreated();
        numberOfACLsDeleted += result.getNumberOfACLsDeleted();
      }
    } catch (ExecutionException ex) {
      throw new GeneralException("", ex.getCause());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new GeneralException("", ex);
    }

    long aclProcessingTime = System.currentTimeMillis() - startTime;
    try (DelayLogContext ignore = new DelayLogContext(aclProcessingTime, OVERRIDE_ERROR)) {
      log.info(
          "UserGroupChangeConsumer.consumeUpdateEvent: Number of ACLs created: {} for identifier: {}, scope: {} Time taken: {}",
          numberOfACLsCreated, userGroup.getIdentifier(), userGroup.getScopeIdentifier(), aclProcessingTime);
      log.info(
          "UserGroupChangeConsumer.consumeUpdateEvent: Number of ACLs deleted: {} for identifier: {}, scope: {} Time taken: {}",
          numberOfACLsDeleted, userGroup.getIdentifier(), userGroup.getScopeIdentifier(), aclProcessingTime);
    }
    return true;
  }

  @Override
  public boolean consumeDeleteEvent(String id) {
    return true;
  }

  @Override
  public boolean consumeCreateEvent(String id, UserGroupUpdateEventData changeEventData) {
    return true;
  }

  private static class ReProcessRoleAssignmentOnUserGroupUpdateTask implements Callable<Result> {
    private final ACLGeneratorService aclGeneratorService;
    private final RoleAssignmentDBO roleAssignmentDBO;
    private final ACLRepository aclRepository;
    private final Set<String> usersAdded;
    private final Set<String> usersRemoved;

    private ReProcessRoleAssignmentOnUserGroupUpdateTask(ACLGeneratorService aclGeneratorService,
        ACLRepository aclRepository, RoleAssignmentDBO roleAssignment, Set<String> usersAdded,
        Set<String> usersRemoved) {
      this.aclGeneratorService = aclGeneratorService;
      this.aclRepository = aclRepository;
      this.roleAssignmentDBO = roleAssignment;
      this.usersAdded = usersAdded;
      this.usersRemoved = usersRemoved;
    }

    @Override
    public Result call() {
      long offset = 0;
      long totalACLsDeleted = 0;
      while (offset < usersRemoved.size()) {
        Set<String> subSetPrincipalRemovedFromUserGroup =
            usersRemoved.stream().skip(offset).limit(1000).collect(Collectors.toSet());
        long numberOfACLsDeleted = aclRepository.deleteByRoleAssignmentIdAndPrincipals(
            roleAssignmentDBO.getId(), subSetPrincipalRemovedFromUserGroup);
        offset += 1000;
        totalACLsDeleted += numberOfACLsDeleted;
      }

      long numberOfACLsCreated = 0;
      numberOfACLsCreated += aclGeneratorService.createACLsForRoleAssignment(roleAssignmentDBO, usersAdded);
      numberOfACLsCreated += aclGeneratorService.createImplicitACLs(roleAssignmentDBO, usersAdded);

      return new Result(numberOfACLsCreated, totalACLsDeleted);
    }
  }
}
