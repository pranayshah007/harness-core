/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.aggregator.AccessControlAdminService;
import io.harness.aggregator.models.RoleAssignmentChangeEventData;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.DelayLogContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Singleton
@Slf4j
public class RoleAssignmentChangeConsumer extends AbstractAccessControlChangeConsumer<RoleAssignmentChangeEventData> {
  private final ACLRepository aclRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final ACLGeneratorService aclGeneratorService;

  @Inject
  public RoleAssignmentChangeConsumer(@Named(ACL.PRIMARY_COLLECTION) ACLRepository aclRepository,
      RoleAssignmentRepository roleAssignmentRepository, ACLGeneratorService aclGeneratorService,
      AccessControlAdminService accessControlAdminService) {
    super(accessControlAdminService);
    this.aclRepository = aclRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.aclGeneratorService = aclGeneratorService;
  }

  @Override
  public boolean consumeUpdateEvent(String id, RoleAssignmentChangeEventData changeEventData) {
    RoleAssignment updatedRoleAssignment = changeEventData.getUpdatedRoleAssignment();
    if (!StringUtils.isEmpty(updatedRoleAssignment.getRoleIdentifier())
        || !StringUtils.isEmpty(updatedRoleAssignment.getResourceGroupIdentifier())
        || !StringUtils.isEmpty(updatedRoleAssignment.getPrincipalIdentifier())) {
      Optional<RoleAssignmentDBO> roleAssignmentDBO = roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
          updatedRoleAssignment.getIdentifier(), updatedRoleAssignment.getScopeIdentifier());
      long deletedACLs =
          roleAssignmentDBO.map(assignmentDBO -> getNumberOfACLsDeleted(updatedRoleAssignment)).orElse(0L);
      log.info(
          "RoleAssignmentChangeConsumer.consumeUpdateEvent: Number of ACLs deleted: {} for roleassignment: identifier: {}, scope: {}",
          deletedACLs, updatedRoleAssignment.getIdentifier(), updatedRoleAssignment.getScopeIdentifier());
      if (roleAssignmentDBO.isPresent()) {
        long createdCount = createACLs(roleAssignmentDBO.get());
        log.info(
            "RoleAssignmentChangeConsumer.consumeUpdateEvent: Number of ACLs created: {} for roleassignment: identifier: {}, scope: {}",
            createdCount, updatedRoleAssignment.getIdentifier(), updatedRoleAssignment.getScopeIdentifier());
      }
      return true;
    }
    return false;
  }

  private long createACLs(RoleAssignmentDBO roleAssignment) {
    long numberOfACLsCreated = aclGeneratorService.createACLsForRoleAssignment(roleAssignment);
    numberOfACLsCreated += aclGeneratorService.createImplicitACLsForRoleAssignment(roleAssignment);
    return numberOfACLsCreated;
  }

  @Override
  public boolean consumeDeleteEvent(String id, RoleAssignmentChangeEventData roleAssignmentChangeEventData) {
    long startTime = System.currentTimeMillis();
    RoleAssignment deletedRoleAssignment = roleAssignmentChangeEventData.getDeletedRoleAssignment();
    long numberOfACLsDeleted = getNumberOfACLsDeleted(deletedRoleAssignment);
    long permissionsChangeTime = System.currentTimeMillis() - startTime;
    try (DelayLogContext ignore = new DelayLogContext(permissionsChangeTime, OVERRIDE_ERROR)) {
      log.info(
          "RoleAssignmentChangeConsumer.consumeDeleteEvent: Number of ACLs deleted: {} for id: {}, identifier: {}, scope: {} Time taken: {}",
          numberOfACLsDeleted, deletedRoleAssignment.getId(), deletedRoleAssignment.getIdentifier(),
          deletedRoleAssignment.getScopeIdentifier(), permissionsChangeTime);
    }
    return true;
  }

  private long getNumberOfACLsDeleted(RoleAssignment roleAssignment) {
    long numberOfACLsDeleted;
    if (isNotEmpty(roleAssignment.getId())) {
      numberOfACLsDeleted = aclRepository.deleteByRoleAssignmentId(roleAssignment.getId());
    } else {
      numberOfACLsDeleted = aclRepository.deleteByScopeIdentifierAndRoleAssignmentIdentifier(
          roleAssignment.getScopeIdentifier(), roleAssignment.getIdentifier());
    }
    return numberOfACLsDeleted;
  }

  @Override
  public boolean consumeCreateEvent(String id, RoleAssignmentChangeEventData changeEventData) {
    long startTime = System.currentTimeMillis();
    RoleAssignment newRoleAssignment = changeEventData.getNewRoleAssignment();
    Optional<RoleAssignmentDBO> roleAssignmentOptional = roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
        newRoleAssignment.getIdentifier(), newRoleAssignment.getScopeIdentifier());
    if (roleAssignmentOptional.isEmpty()) {
      log.info(
          "RoleAssignmentChangeConsumer.consumeCreateEvent: Role assignment has been deleted, not processing role assignment create event for identifier: {}, scope: {}",
          newRoleAssignment.getIdentifier(), newRoleAssignment.getScopeIdentifier());
      return true;
    }
    RoleAssignmentDBO roleAssignmentDBO = roleAssignmentOptional.get();
    long numberOfACLsCreated = createACLs(roleAssignmentDBO);
    long permissionsChangeTime = System.currentTimeMillis() - startTime;
    try (DelayLogContext ignore = new DelayLogContext(permissionsChangeTime, OVERRIDE_ERROR)) {
      log.info(
          "RoleAssignmentChangeConsumer.consumeCreateEvent: Number of ACLs created: {} for id: {}, identifier: {}, scope: {} Time taken: {}",
          numberOfACLsCreated, roleAssignmentDBO.getId(), roleAssignmentDBO.getIdentifier(),
          roleAssignmentDBO.getScopeIdentifier(), permissionsChangeTime);
    }
    return true;
  }
}
