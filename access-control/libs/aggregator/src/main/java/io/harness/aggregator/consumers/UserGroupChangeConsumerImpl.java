/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBO;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.logging.DelayLogContext;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
public class UserGroupChangeConsumerImpl implements ChangeConsumer<UserGroupDBO> {
  private final ACLRepository aclRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final UserGroupRepository userGroupRepository;
  private final ExecutorService executorService;
  private final ACLGeneratorService aclGeneratorService;
  private final ScopeService scopeService;
  private final UserGroupCRUDEventHandler userGroupCRUDEventHandler;

  public UserGroupChangeConsumerImpl(ACLRepository aclRepository, RoleAssignmentRepository roleAssignmentRepository,
      UserGroupRepository userGroupRepository, String executorServiceSuffix, ACLGeneratorService aclGeneratorService,
      ScopeService scopeService, UserGroupCRUDEventHandler userGroupCRUDEventHandler) {
    this.aclRepository = aclRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.userGroupRepository = userGroupRepository;
    this.scopeService = scopeService;
    this.userGroupCRUDEventHandler = userGroupCRUDEventHandler;
    String changeConsumerThreadFactory = String.format("%s-user-group-change-consumer", executorServiceSuffix) + "-%d";
    // Number of threads = Number of Available Cores * (1 + (Wait time / Service time) )
    this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
        new ThreadFactoryBuilder().setNameFormat(changeConsumerThreadFactory).build());
    this.aclGeneratorService = aclGeneratorService;
  }

  @Override
  public boolean consumeUpdateEvent(String id, UserGroupDBO updatedUserGroup) {
    long startTime = System.currentTimeMillis();
    if (updatedUserGroup.getUsers() == null) {
      return false;
    }

    Optional<UserGroupDBO> userGroup = userGroupRepository.findById(id);
    if (!userGroup.isPresent()) {
      return true;
    }

    Pattern startsWithScope = Pattern.compile("^".concat(userGroup.get().getScopeIdentifier()));
    String principalScopeLevel =
        scopeService.buildScopeFromScopeIdentifier(userGroup.get().getScopeIdentifier()).getLevel().toString();

    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.principalType)
                            .is(USER_GROUP)
                            .and(RoleAssignmentDBOKeys.principalIdentifier)
                            .is(userGroup.get().getIdentifier())
                            .and(RoleAssignmentDBOKeys.principalScopeLevel)
                            .is(principalScopeLevel)
                            .and(RoleAssignmentDBOKeys.scopeIdentifier)
                            .regex(startsWithScope);
    List<ReProcessRoleAssignmentOnUserGroupUpdateTask> tasksToExecute =
        roleAssignmentRepository.findAll(criteria, Pageable.unpaged())
            .stream()
            .map((RoleAssignmentDBO roleAssignment)
                     -> new ReProcessRoleAssignmentOnUserGroupUpdateTask(
                         aclRepository, aclGeneratorService, roleAssignment, userGroup.get()))
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

    userGroupCRUDEventHandler.handleUserGroupUpdate(userGroup.get());

    long permissionsChangeTime = System.currentTimeMillis() - startTime;
    try (DelayLogContext ignore = new DelayLogContext(permissionsChangeTime, OVERRIDE_ERROR)) {
      log.info("UserGroupChangeConsumerImpl.consumeUpdateEvent: Number of ACLs created: {} for {} Time taken: {}",
          numberOfACLsCreated, id, permissionsChangeTime);
      log.info("UserGroupChangeConsumerImpl.consumeUpdateEvent: Number of ACLs deleted: {} for {} Time taken: {}",
          numberOfACLsDeleted, id, permissionsChangeTime);
    }
    return true;
  }

  @Override
  public boolean consumeDeleteEvent(String id) {
    // No need to process separately. Would be processed indirectly when associated role bindings will be deleted
    return true;
  }

  @Override
  public boolean consumeCreateEvent(String id, UserGroupDBO createdEntity) {
    // we do not consume create event
    return true;
  }

  private static class ReProcessRoleAssignmentOnUserGroupUpdateTask implements Callable<Result> {
    private final ACLRepository aclRepository;
    private final ACLGeneratorService changeConsumerService;
    private final RoleAssignmentDBO roleAssignmentDBO;
    private final UserGroupDBO updatedUserGroup;

    private ReProcessRoleAssignmentOnUserGroupUpdateTask(ACLRepository aclRepository,
        ACLGeneratorService changeConsumerService, RoleAssignmentDBO roleAssignment, UserGroupDBO updatedUserGroup) {
      this.aclRepository = aclRepository;
      this.changeConsumerService = changeConsumerService;
      this.roleAssignmentDBO = roleAssignment;
      this.updatedUserGroup = updatedUserGroup;
    }

    @Override
    public Result call() {
      Set<String> existingPrincipals = Sets.newHashSet(
          Sets.newHashSet(aclRepository.getDistinctPrincipalsInACLsForRoleAssignment(roleAssignmentDBO.getId())));
      Set<String> principalsAddedToUserGroup =
          Sets.difference(updatedUserGroup.getUsers() == null ? Collections.emptySet() : updatedUserGroup.getUsers(),
              existingPrincipals);
      Set<String> principalRemovedFromUserGroup = Sets.difference(existingPrincipals,
          updatedUserGroup.getUsers() == null ? Collections.emptySet() : updatedUserGroup.getUsers());

      long offset = 0;
      long totalACLsDeleted = 0;
      while (offset < principalRemovedFromUserGroup.size()) {
        Set<String> subSetPrincipalRemovedFromUserGroup =
            principalRemovedFromUserGroup.stream().skip(offset).limit(1000).collect(Collectors.toSet());
        long numberOfACLsDeleted = aclRepository.deleteByRoleAssignmentIdAndPrincipals(
            roleAssignmentDBO.getId(), subSetPrincipalRemovedFromUserGroup);
        offset += 1000;
        totalACLsDeleted += numberOfACLsDeleted;
      }

      long numberOfACLsCreated = 0;

      numberOfACLsCreated +=
          changeConsumerService.createACLsForRoleAssignment(roleAssignmentDBO, principalsAddedToUserGroup);
      numberOfACLsCreated += changeConsumerService.createImplicitACLsForRoleAssignment(
          roleAssignmentDBO, principalsAddedToUserGroup, new HashSet<>());

      return new Result(numberOfACLsCreated, totalACLsDeleted);
    }
  }
}
