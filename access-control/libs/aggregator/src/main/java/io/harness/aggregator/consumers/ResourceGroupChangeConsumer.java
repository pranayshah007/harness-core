/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.aggregator.consumers;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.aggregator.models.ResourceGroupChangeEventData;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.logging.DelayLogContext;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Singleton
@Slf4j
public class ResourceGroupChangeConsumer implements AccessControlChangeConsumer<ResourceGroupChangeEventData> {
  private final ACLRepository aclRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final ExecutorService executorService;
  private final ACLGeneratorService aclGeneratorService;

  @Inject
  public ResourceGroupChangeConsumer(@Named(ACL.PRIMARY_COLLECTION) ACLRepository aclRepository,
      RoleAssignmentRepository roleAssignmentRepository, ACLGeneratorService aclGeneratorService) {
    this.aclRepository = aclRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.aclGeneratorService = aclGeneratorService;
    this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
        new ThreadFactoryBuilder().setNameFormat("resource-group-change-consumer-%d").build());
  }

  @Override
  public boolean consumeUpdateEvent(String id, ResourceGroupChangeEventData resourceGroupChangeEventData) {
    long startTime = System.currentTimeMillis();
    ResourceGroup updatedResourceGroup = resourceGroupChangeEventData.getUpdatedResourceGroup();
    Criteria criteria =
        Criteria.where(RoleAssignmentDBOKeys.resourceGroupIdentifier).is(updatedResourceGroup.getIdentifier());
    if (isNotEmpty(updatedResourceGroup.getScopeIdentifier())) {
      criteria.and(RoleAssignmentDBOKeys.scopeIdentifier).is(updatedResourceGroup.getScopeIdentifier());
    }

    List<ResourceGroupChangeConsumer.ReProcessRoleAssignmentOnResourceGroupUpdateTask> tasksToExecute =
        roleAssignmentRepository.findAll(criteria, Pageable.ofSize(100000))
            .stream()
            .map((RoleAssignmentDBO roleAssignment)
                     -> new ResourceGroupChangeConsumer.ReProcessRoleAssignmentOnResourceGroupUpdateTask(
                         aclRepository, aclGeneratorService, roleAssignment, resourceGroupChangeEventData))
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
          "ResourceGroupChangeConsumer.consumeUpdateEvent: Number of ACLs created: {} for identifier: {}, scope: {} Time taken: {}",
          numberOfACLsCreated, updatedResourceGroup.getIdentifier(), updatedResourceGroup.getScopeIdentifier(),
          permissionsChangeTime);
      log.info(
          "ResourceGroupChangeConsumer.consumeUpdateEvent: Number of ACLs deleted: {} for identifier: {}, scope: {} Time taken: {}",
          numberOfACLsDeleted, updatedResourceGroup.getIdentifier(), updatedResourceGroup.getScopeIdentifier(),
          permissionsChangeTime);
    }
    return true;
  }

  @Override
  public boolean consumeDeleteEvent(String id) {
    return true;
  }

  @Override
  public boolean consumeCreateEvent(String id, ResourceGroupChangeEventData changeEventData) {
    return true;
  }

  private static class ReProcessRoleAssignmentOnResourceGroupUpdateTask implements Callable<Result> {
    private final ACLRepository aclRepository;
    private final RoleAssignmentDBO roleAssignmentDBO;
    private final ResourceGroupChangeEventData resourceGroupChangeEventData;
    private final ACLGeneratorService aclGeneratorService;

    private ReProcessRoleAssignmentOnResourceGroupUpdateTask(ACLRepository aclRepository,
        ACLGeneratorService aclGeneratorService, RoleAssignmentDBO roleAssignment,
        ResourceGroupChangeEventData resourceGroupChangeEventData) {
      this.aclRepository = aclRepository;
      this.aclGeneratorService = aclGeneratorService;
      this.roleAssignmentDBO = roleAssignment;
      this.resourceGroupChangeEventData = resourceGroupChangeEventData;
    }

    @Override
    public Result call() throws Exception {
      long numberOfACLsCreated = 0;
      long numberOfACLsDeleted = aclRepository.deleteByRoleAssignmentIdAndResourceSelectors(
          roleAssignmentDBO.getId(), resourceGroupChangeEventData.getRemovedResourceSelectors());

      numberOfACLsCreated += aclGeneratorService.createACLsFromResourceSelectors(
          roleAssignmentDBO, resourceGroupChangeEventData.getAddedResourceSelectors());
      // compute implicit ACLs.
      numberOfACLsDeleted += aclRepository.deleteByRoleAssignmentIdAndImplicitForScope(roleAssignmentDBO.getId());
      numberOfACLsCreated += aclGeneratorService.createImplicitACLsForRoleAssignment(roleAssignmentDBO);
      return new Result(numberOfACLsCreated, numberOfACLsDeleted);
    }
  }
}