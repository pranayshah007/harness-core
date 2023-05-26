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

import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupDBO;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.logging.DelayLogContext;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
public class ResourceGroupChangeConsumerImpl implements ChangeConsumer<ResourceGroupDBO> {
  private final ACLRepository aclRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final ResourceGroupRepository resourceGroupRepository;
  private final ExecutorService executorService;
  private final ACLGeneratorService aclGeneratorService;

  public ResourceGroupChangeConsumerImpl(ACLRepository aclRepository, RoleAssignmentRepository roleAssignmentRepository,
      ResourceGroupRepository resourceGroupRepository, String executorServiceSuffix,
      ACLGeneratorService aclGeneratorService) {
    this.aclRepository = aclRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.resourceGroupRepository = resourceGroupRepository;
    this.aclGeneratorService = aclGeneratorService;
    String changeConsumerThreadFactory =
        String.format("%s-resource-group-change-consumer", executorServiceSuffix) + "-%d";
    // Number of threads = Number of Available Cores * (1 + (Wait time / Service time) )
    this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
        new ThreadFactoryBuilder().setNameFormat(changeConsumerThreadFactory).build());
  }

  @Override
  public boolean consumeUpdateEvent(String id, ResourceGroupDBO updatedResourceGroup) {
    long startTime = System.currentTimeMillis();
    if (updatedResourceGroup.getResourceSelectors() == null && updatedResourceGroup.getResourceSelectorsV2() == null
        && updatedResourceGroup.getScopeSelectors() == null) {
      return false;
    }

    Optional<ResourceGroupDBO> resourceGroup = resourceGroupRepository.findById(id);
    if (!resourceGroup.isPresent()) {
      return true;
    }

    Criteria criteria =
        Criteria.where(RoleAssignmentDBOKeys.resourceGroupIdentifier).is(resourceGroup.get().getIdentifier());
    if (isNotEmpty(resourceGroup.get().getScopeIdentifier())) {
      criteria.and(RoleAssignmentDBOKeys.scopeIdentifier).is(resourceGroup.get().getScopeIdentifier());
    }

    List<ReProcessRoleAssignmentOnResourceGroupUpdateTask> tasksToExecute =
        roleAssignmentRepository.findAll(criteria, Pageable.unpaged())
            .stream()
            .map((RoleAssignmentDBO roleAssignment)
                     -> new ReProcessRoleAssignmentOnResourceGroupUpdateTask(
                         aclRepository, aclGeneratorService, roleAssignment, resourceGroup.get()))
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
      log.info("ResourceGroupChangeConsumerImpl.consumeUpdateEvent: Number of ACLs created: {} for {} Time taken: {}",
          numberOfACLsCreated, id, permissionsChangeTime);
      log.info("ResourceGroupChangeConsumerImpl.consumeUpdateEvent: Number of ACLs deleted: {} for {} Time taken: {}",
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
  public boolean consumeCreateEvent(String id, ResourceGroupDBO createdEntity) {
    // we do not consume create event
    return true;
  }

  private static class ReProcessRoleAssignmentOnResourceGroupUpdateTask implements Callable<Result> {
    private final ACLRepository aclRepository;
    private final RoleAssignmentDBO roleAssignmentDBO;
    private final ResourceGroupDBO updatedResourceGroup;
    private final ACLGeneratorService changeConsumerService;

    private ReProcessRoleAssignmentOnResourceGroupUpdateTask(ACLRepository aclRepository,
        ACLGeneratorService changeConsumerService, RoleAssignmentDBO roleAssignment,
        ResourceGroupDBO updatedResourceGroup) {
      this.aclRepository = aclRepository;
      this.changeConsumerService = changeConsumerService;
      this.roleAssignmentDBO = roleAssignment;
      this.updatedResourceGroup = updatedResourceGroup;
    }

    @Override
    public Result call() {
      long numberOfACLsCreated = 0;
      long numberOfACLsDeleted = 0;

      if (updatedResourceGroup.getResourceSelectors() != null
          || updatedResourceGroup.getResourceSelectorsV2() != null) {
        Set<ResourceSelector> existingResourceSelectors =
            aclRepository.getDistinctResourceSelectorsInACLs(roleAssignmentDBO.getId());
        Set<ResourceSelector> newResourceSelectors = new HashSet<>();
        if (updatedResourceGroup.getResourceSelectors() != null) {
          newResourceSelectors.addAll(updatedResourceGroup.getResourceSelectors()
                                          .stream()
                                          .map(selector -> ResourceSelector.builder().selector(selector).build())
                                          .collect(Collectors.toList()));
        }
        if (updatedResourceGroup.getResourceSelectorsV2() != null) {
          newResourceSelectors.addAll(updatedResourceGroup.getResourceSelectorsV2());
        }

        Set<ResourceSelector> resourceSelectorsRemovedFromResourceGroup =
            Sets.difference(existingResourceSelectors, newResourceSelectors);
        Set<ResourceSelector> resourceSelectorsAddedToResourceGroup =
            Sets.difference(newResourceSelectors, existingResourceSelectors);

        Set<String> existingPermissions =
            Sets.newHashSet(aclRepository.getDistinctPermissionsInACLsForRoleAssignment(roleAssignmentDBO.getId()));
        Set<String> existingPrincipals =
            Sets.newHashSet(aclRepository.getDistinctPrincipalsInACLsForRoleAssignment(roleAssignmentDBO.getId()));

        if (newResourceSelectors.size() == 0) {
          numberOfACLsDeleted += aclRepository.deleteByRoleAssignmentId(roleAssignmentDBO.getId());
        } else {
          numberOfACLsDeleted += aclRepository.deleteByRoleAssignmentIdAndResourceSelectors(
              roleAssignmentDBO.getId(), resourceSelectorsRemovedFromResourceGroup);
        }

        if (existingPermissions.isEmpty() || existingPrincipals.isEmpty()) {
          numberOfACLsCreated += changeConsumerService.createACLsForRoleAssignment(roleAssignmentDBO);
        } else {
          numberOfACLsCreated += changeConsumerService.createACLs(
              roleAssignmentDBO, existingPrincipals, existingPermissions, resourceSelectorsAddedToResourceGroup);
        }

        if (updatedResourceGroup.getScopeSelectors() != null) {
          numberOfACLsDeleted += aclRepository.deleteByRoleAssignmentIdAndImplicitForScope(roleAssignmentDBO.getId());
          numberOfACLsCreated += changeConsumerService.createImplicitACLsForRoleAssignment(
              roleAssignmentDBO, new HashSet<>(), new HashSet<>());
        }
      }

      return new Result(numberOfACLsCreated, numberOfACLsDeleted);
    }
  }
}
