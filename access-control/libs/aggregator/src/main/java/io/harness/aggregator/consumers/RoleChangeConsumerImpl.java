/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.aggregator.ACLUtils.buildACL;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.accesscontrol.roles.persistence.RoleDBO;
import io.harness.accesscontrol.roles.persistence.repositories.RoleRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Slf4j
@Singleton
public class RoleChangeConsumerImpl implements ChangeConsumer<RoleDBO> {
  private final ACLRepository aclRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final RoleRepository roleRepository;
  private final ExecutorService executorService;
  private final ChangeConsumerService changeConsumerService;

  public RoleChangeConsumerImpl(ACLRepository aclRepository, RoleAssignmentRepository roleAssignmentRepository,
      RoleRepository roleRepository, String executorServiceSuffix, ChangeConsumerService changeConsumerService) {
    this.aclRepository = aclRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.roleRepository = roleRepository;
    String changeConsumerThreadFactory = String.format("%s-role-change-consumer", executorServiceSuffix) + "-%d";
    // Number of threads = Number of Available Cores * (1 + (Wait time / Service time) )
    this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
        new ThreadFactoryBuilder().setNameFormat(changeConsumerThreadFactory).build());
    this.changeConsumerService = changeConsumerService;
  }

  @Override
  public void consumeUpdateEvent(String id, RoleDBO updatedRole) {
    if (updatedRole.getPermissions() == null) {
      return;
    }

    Optional<RoleDBO> role = roleRepository.findById(id);
    if (!role.isPresent()) {
      return;
    }

    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.roleIdentifier).is(role.get().getIdentifier());
    if (!StringUtils.isEmpty(role.get().getScopeIdentifier())) {
      criteria.and(RoleAssignmentDBOKeys.scopeIdentifier).is(role.get().getScopeIdentifier());
    }

    List<ReProcessRoleAssignmentOnRoleUpdateTask> tasksToExecute =
        roleAssignmentRepository.findAll(criteria, Pageable.unpaged())
            .stream()
            .map((RoleAssignmentDBO roleAssignment)
                     -> new ReProcessRoleAssignmentOnRoleUpdateTask(
                         aclRepository, changeConsumerService, roleAssignment, role.get()))
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

    log.info("Number of ACLs created: {}", numberOfACLsCreated);
    log.info("Number of ACLs deleted: {}", numberOfACLsDeleted);
  }

  @Override
  public void consumeDeleteEvent(String id) {
    // No need to process separately. Would be processed indirectly when associated role bindings will be deleted
  }

  @Override
  public void consumeCreateEvent(String id, RoleDBO createdEntity) {
    // we do not consume create event
  }

  private static class ReProcessRoleAssignmentOnRoleUpdateTask implements Callable<Result> {
    private final ACLRepository aclRepository;
    private final RoleAssignmentDBO roleAssignmentDBO;
    private final RoleDBO updatedRole;
    private final ChangeConsumerService changeConsumerService;

    private ReProcessRoleAssignmentOnRoleUpdateTask(ACLRepository aclRepository,
        ChangeConsumerService changeConsumerService, RoleAssignmentDBO roleAssignment, RoleDBO updatedRole) {
      this.aclRepository = aclRepository;
      this.changeConsumerService = changeConsumerService;
      this.roleAssignmentDBO = roleAssignment;
      this.updatedRole = updatedRole;
    }

    @Override
    public Result call() {
      Set<String> existingPermissions =
          Sets.newHashSet(aclRepository.getDistinctPermissionsInACLsForRoleAssignment(roleAssignmentDBO.getId()));
      Set<String> permissionsAddedToRole =
          Sets.difference(updatedRole.getPermissions() == null ? Collections.emptySet() : updatedRole.getPermissions(),
              existingPermissions);
      Set<String> permissionsRemovedFromRole = Sets.difference(existingPermissions,
          updatedRole.getPermissions() == null ? Collections.emptySet() : updatedRole.getPermissions());

      long numberOfACLsDeleted =
          aclRepository.deleteByRoleAssignmentIdAndPermissions(roleAssignmentDBO.getId(), permissionsRemovedFromRole);

      Set<ResourceSelector> existingResourceSelectors =
          aclRepository.getDistinctResourceSelectorsInACLs(roleAssignmentDBO.getId());
      Set<String> existingPrincipals =
          Sets.newHashSet(aclRepository.getDistinctPrincipalsInACLsForRoleAssignment(roleAssignmentDBO.getId()));
      PrincipalType principalType =
          USER_GROUP.equals(roleAssignmentDBO.getPrincipalType()) ? USER : roleAssignmentDBO.getPrincipalType();

      long numberOfACLsCreated = 0;
      List<ACL> aclsToCreate = new ArrayList<>();

      if (existingResourceSelectors.isEmpty() || existingPrincipals.isEmpty()) {
        aclsToCreate.addAll(changeConsumerService.getAClsForRoleAssignment(roleAssignmentDBO));
      } else {
        permissionsAddedToRole.forEach(permissionIdentifier
            -> existingPrincipals.forEach(principalIdentifier
                -> existingResourceSelectors.forEach(resourceSelector
                    -> aclsToCreate.add(buildACL(permissionIdentifier, Principal.of(principalType, principalIdentifier),
                        roleAssignmentDBO, resourceSelector)))));
      }
      numberOfACLsCreated += aclRepository.insertAllIgnoringDuplicates(aclsToCreate);

      return new Result(numberOfACLsCreated, numberOfACLsDeleted);
    }
  }
}
