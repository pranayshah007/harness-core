/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Singleton
@Slf4j
public class RoleAssignmentChangeConsumerImpl implements ChangeConsumer<RoleAssignmentDBO> {
  private final ACLRepository aclRepository;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final ChangeConsumerService changeConsumerService;
  private final RoleAssignmentCRUDEventHandler roleAssignmentCRUDEventHandler;

  public RoleAssignmentChangeConsumerImpl(ACLRepository aclRepository,
      RoleAssignmentRepository roleAssignmentRepository, ChangeConsumerService changeConsumerService,
      RoleAssignmentCRUDEventHandler roleAssignmentCRUDEventHandler) {
    this.aclRepository = aclRepository;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.changeConsumerService = changeConsumerService;
    this.roleAssignmentCRUDEventHandler = roleAssignmentCRUDEventHandler;
  }

  @Override
  public void consumeUpdateEvent(String id, RoleAssignmentDBO updatedRoleAssignmentDBO) {
    if (!StringUtils.isEmpty(updatedRoleAssignmentDBO.getRoleIdentifier())
        || !StringUtils.isEmpty(updatedRoleAssignmentDBO.getResourceGroupIdentifier())
        || !StringUtils.isEmpty(updatedRoleAssignmentDBO.getPrincipalIdentifier())
        || updatedRoleAssignmentDBO.getDisabled() != null) {
      log.info("Number of ACLs deleted: {}", deleteACLs(id));
      Optional<RoleAssignmentDBO> roleAssignment = roleAssignmentRepository.findById(id);
      if (roleAssignment.isPresent()) {
        long createdCount = createACLs(roleAssignment.get());
        log.info("Number of ACLs created: {}", createdCount);
      }
    }
  }

  @Override
  public void consumeDeleteEvent(String id) {
    roleAssignmentCRUDEventHandler.handleRoleAssignmentDelete(id);
    log.info("Number of ACLs deleted: {}", deleteACLs(id));
  }

  private long deleteACLs(String id) {
    return aclRepository.deleteByRoleAssignmentId(id);
  }

  private long createACLs(RoleAssignmentDBO roleAssignment) {
    List<ACL> aclsToCreate = changeConsumerService.getAClsForRoleAssignment(roleAssignment);
    aclsToCreate.addAll(
        changeConsumerService.getImplicitACLsForRoleAssignment(roleAssignment, new HashSet<>(), new HashSet<>()));
    return aclRepository.insertAllIgnoringDuplicates(aclsToCreate);
  }

  @Override
  public void consumeCreateEvent(String id, RoleAssignmentDBO newRoleAssignmentDBO) {
    Optional<RoleAssignmentDBO> roleAssignmentOptional = roleAssignmentRepository.findByIdentifierAndScopeIdentifier(
        newRoleAssignmentDBO.getIdentifier(), newRoleAssignmentDBO.getScopeIdentifier());
    if (!roleAssignmentOptional.isPresent()) {
      log.info("Role assignment has been deleted, not processing role assignment create event for id: {}", id);
      return;
    }
    roleAssignmentCRUDEventHandler.handleRoleAssignmentCreate(newRoleAssignmentDBO);
    log.info("Number of ACLs created: {}", createACLs(newRoleAssignmentDBO));
  }
}
