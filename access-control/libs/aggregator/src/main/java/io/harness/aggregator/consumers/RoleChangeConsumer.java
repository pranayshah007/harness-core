/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.aggregator.consumers;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.roles.Role;
import io.harness.aggregator.models.RoleChangeEventData;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.logging.DelayLogContext;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Singleton
@Slf4j
public class RoleChangeConsumer implements AccessControlChangeConsumer<RoleChangeEventData> {
  private final ACLRepository aclRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final ExecutorService executorService;
  private final ACLGeneratorService aclGeneratorService;

  @Inject
  public RoleChangeConsumer(@Named(ACL.PRIMARY_COLLECTION) ACLRepository aclRepository,
      RoleAssignmentRepository roleAssignmentRepository, ACLGeneratorService aclGeneratorService) {
    this.aclRepository = aclRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.aclGeneratorService = aclGeneratorService;
    // Number of threads = Number of Available Cores * (1 + (Wait time / Service time) )
    this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
        new ThreadFactoryBuilder().setNameFormat("role-group-change-consumer-%d").build());
  }

  @Override
  public boolean consumeUpdateEvent(String id, RoleChangeEventData roleChangeEventData) {
    long startTime = System.currentTimeMillis();
    Role updatedRole = roleChangeEventData.getUpdatedRole();

    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.roleIdentifier).is(updatedRole.getIdentifier());
    if (!StringUtils.isEmpty(updatedRole.getScopeIdentifier())) {
      criteria.and(RoleAssignmentDBOKeys.scopeIdentifier).is(updatedRole.getScopeIdentifier());
    }

    List<RoleChangeConsumer.ReProcessRoleAssignmentOnRoleUpdateTask> tasksToExecute =
        roleAssignmentRepository.findAll(criteria, Pageable.ofSize(10000))
            .stream()
            .map((RoleAssignmentDBO roleAssignment)
                     -> new RoleChangeConsumer.ReProcessRoleAssignmentOnRoleUpdateTask(
                         aclRepository, aclGeneratorService, roleAssignment, roleChangeEventData))
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
    long permissionsChangeTime = System.currentTimeMillis() - startTime;
    try (DelayLogContext ignore = new DelayLogContext(permissionsChangeTime, OVERRIDE_ERROR)) {
      log.info(
          "RoleChangeConsumer.consumeUpdateEvent: Number of ACLs created: {} for identifier: {}, scope: {} Time taken: {}",
          numberOfACLsCreated, updatedRole.getIdentifier(), updatedRole.getScopeIdentifier(), permissionsChangeTime);
      log.info(
          "RoleChangeConsumer.consumeUpdateEvent: Number of ACLs deleted: {} for identifier: {}, scope: {} Time taken: {}",
          numberOfACLsDeleted, updatedRole.getIdentifier(), updatedRole.getScopeIdentifier(), permissionsChangeTime);
    }
    return true;
  }

  @Override
  public boolean consumeDeleteEvent(String id) {
    return true;
  }

  @Override
  public boolean consumeCreateEvent(String id, RoleChangeEventData changeEventData) {
    return true;
  }

  private static class ReProcessRoleAssignmentOnRoleUpdateTask implements Callable<Result> {
    private final ACLRepository aclRepository;
    private final RoleAssignmentDBO roleAssignmentDBO;
    private final RoleChangeEventData roleChangeEventData;
    private final ACLGeneratorService aclGeneratorService;

    private ReProcessRoleAssignmentOnRoleUpdateTask(ACLRepository aclRepository,
        ACLGeneratorService aclGeneratorService, RoleAssignmentDBO roleAssignment,
        RoleChangeEventData roleChangeEventData) {
      this.aclRepository = aclRepository;
      this.aclGeneratorService = aclGeneratorService;
      this.roleAssignmentDBO = roleAssignment;
      this.roleChangeEventData = roleChangeEventData;
    }

    @Override
    public Result call() {
      Set<String> permissionsAdded = roleChangeEventData.getPermissionsAdded();
      Set<String> permissionsRemoved = roleChangeEventData.getPermissionsRemoved();

      long numberOfACLsDeleted =
          aclRepository.deleteByRoleAssignmentIdAndPermissions(roleAssignmentDBO.getId(), permissionsRemoved);
      long numberOfACLsCreated = 0;
      numberOfACLsCreated += aclGeneratorService.createACLsFromPermissions(roleAssignmentDBO, permissionsAdded);
      numberOfACLsCreated += aclGeneratorService.createImplicitACLsFromPermissions(roleAssignmentDBO, permissionsAdded);
      return new Result(numberOfACLsCreated, numberOfACLsDeleted);
    }
  }
}
