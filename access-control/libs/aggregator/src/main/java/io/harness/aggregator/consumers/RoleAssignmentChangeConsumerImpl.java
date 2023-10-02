/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.DelayLogContext;

import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Singleton
@Slf4j
public class RoleAssignmentChangeConsumerImpl implements ChangeConsumer<RoleAssignmentDBO> {
  private final ACLRepository aclRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final ACLGeneratorService aclGeneratorService;
  private final RoleAssignmentCRUDEventHandler roleAssignmentCRUDEventHandler;

  public RoleAssignmentChangeConsumerImpl(ACLRepository aclRepository,
      RoleAssignmentRepository roleAssignmentRepository, ACLGeneratorService aclGeneratorService,
      RoleAssignmentCRUDEventHandler roleAssignmentCRUDEventHandler) {
    this.aclRepository = aclRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.aclGeneratorService = aclGeneratorService;
    this.roleAssignmentCRUDEventHandler = roleAssignmentCRUDEventHandler;
  }

  @Override
  public boolean consumeUpdateEvent(String id, RoleAssignmentDBO updatedRoleAssignmentDBO) {
    if (!StringUtils.isEmpty(updatedRoleAssignmentDBO.getRoleIdentifier())
        || !StringUtils.isEmpty(updatedRoleAssignmentDBO.getResourceGroupIdentifier())
        || !StringUtils.isEmpty(updatedRoleAssignmentDBO.getPrincipalIdentifier())
        || updatedRoleAssignmentDBO.getDisabled() != null) {
      log.info("Number of ACLs deleted: {} for roleassignment: id: {}, identifier: {}, scope: {}", deleteACLs(id), id,
          updatedRoleAssignmentDBO.getIdentifier(), updatedRoleAssignmentDBO.getScopeIdentifier());
      Optional<RoleAssignmentDBO> roleAssignment = roleAssignmentRepository.findById(id);
      if (roleAssignment.isPresent()) {
        long createdCount = createACLs(roleAssignment.get());
        log.info("Number of ACLs created: {} for roleassignment: id: {}, identifier: {}, scope: {}", createdCount, id,
            updatedRoleAssignmentDBO.getIdentifier(), updatedRoleAssignmentDBO.getScopeIdentifier());
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean consumeDeleteEvent(String id) {
    long startTime = System.currentTimeMillis();
    roleAssignmentCRUDEventHandler.handleRoleAssignmentDelete(id);
    long numberOfACLsDeleted = deleteACLs(id);
    long permissionsChangeTime = System.currentTimeMillis() - startTime;
    try (DelayLogContext ignore = new DelayLogContext(permissionsChangeTime, OVERRIDE_ERROR)) {
      log.info("RoleAssignmentChangeConsumerImpl.consumeDeleteEvent: Number of ACLs deleted: {} for {} Time taken: {}",
          numberOfACLsDeleted, id, permissionsChangeTime);
    }
    return true;
  }

  private long deleteACLs(String id) {
    return aclRepository.deleteByRoleAssignmentId(id);
  }

  private long createACLs(RoleAssignmentDBO roleAssignment) {
    long numberOfACLsCreated = aclGeneratorService.createACLsForRoleAssignment(roleAssignment);
    numberOfACLsCreated +=
        aclGeneratorService.createImplicitACLsForRoleAssignment(roleAssignment, new HashSet<>(), new HashSet<>());
    return numberOfACLsCreated;
  }

  @Override
  public boolean consumeCreateEvent(String id, RoleAssignmentDBO newRoleAssignmentDBO) {
    long startTime = System.currentTimeMillis();
    Optional<RoleAssignmentDBO> roleAssignmentOptional = roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
        newRoleAssignmentDBO.getIdentifier(), newRoleAssignmentDBO.getScopeIdentifier());
    if (!roleAssignmentOptional.isPresent()) {
      log.info("Role assignment has been deleted, not processing role assignment create event for id: {}", id);
      return true;
    }
    roleAssignmentCRUDEventHandler.handleRoleAssignmentCreate(newRoleAssignmentDBO);
    long numberOfACLsCreated = createACLs(newRoleAssignmentDBO);
    long permissionsChangeTime = System.currentTimeMillis() - startTime;
    try (DelayLogContext ignore = new DelayLogContext(permissionsChangeTime, OVERRIDE_ERROR)) {
      log.info(
          "RoleAssignmentChangeConsumerImpl.consumeCreateEvent: Number of ACLs created: {} for id: {}, identifier: {}, scope: {} Time taken: {}",
          numberOfACLsCreated, id, newRoleAssignmentDBO.getIdentifier(), newRoleAssignmentDBO.getScopeIdentifier(),
          permissionsChangeTime);
    }
    return true;
  }
}
