package io.harness.accesscontrol.roleassignments.privileged;

import static io.harness.accesscontrol.roleassignments.privileged.AdminPrivilegedRoleAssignmentMapper.MANAGED_RESOURCE_GROUP_IDENTIFIERS;
import static io.harness.accesscontrol.roleassignments.privileged.AdminPrivilegedRoleAssignmentMapper.roleToPrivilegedRole;

import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.aggregator.consumers.RoleAssignmentCRUDEventHandler;
import io.harness.aggregator.consumers.UserGroupCRUDEventHandler;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PL)
@Singleton
@ValidateOnExecution
public class PrivilegedRoleAssignmentHandler implements RoleAssignmentCRUDEventHandler, UserGroupCRUDEventHandler {
  private final UserGroupService userGroupService;
  private final RoleAssignmentRepository roleAssignmentRepository;
  private final PrivilegedRoleAssignmentService privilegedRoleAssignmentService;

  @Inject
  public PrivilegedRoleAssignmentHandler(UserGroupService userGroupService,
      RoleAssignmentRepository roleAssignmentRepository,
      PrivilegedRoleAssignmentService privilegedRoleAssignmentService) {
    this.userGroupService = userGroupService;
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.privilegedRoleAssignmentService = privilegedRoleAssignmentService;
  }

  @Override
  public void handleRoleAssignmentCreate(RoleAssignmentDBO roleAssignment) {
    Optional<PrivilegedRoleAssignment> privilegedRoleAssignmentOpt =
        AdminPrivilegedRoleAssignmentMapper.buildAdminPrivilegedRoleAssignment(roleAssignment);
    Set<PrivilegedRoleAssignment> privilegedRoleAssignments = new HashSet<>();
    if (privilegedRoleAssignmentOpt.isPresent()) {
      PrivilegedRoleAssignment privileged = privilegedRoleAssignmentOpt.get();
      if (privileged.getPrincipalType().equals(PrincipalType.USER_GROUP)) {
        Optional<UserGroup> userGroup =
            userGroupService.get(privileged.getPrincipalIdentifier(), privileged.getScopeIdentifier());
        userGroup.ifPresent(group
            -> group.getUsers()
                   .stream()
                   .map(user
                       -> privileged.withPrincipalType(PrincipalType.USER)
                              .withPrincipalIdentifier(user)
                              .withUserGroupIdentifier(group.getIdentifier()))
                   .forEach(privilegedRoleAssignments::add));
      } else {
        privilegedRoleAssignments.add(privileged);
      }
    }
    privilegedRoleAssignmentService.saveAll(privilegedRoleAssignments);
  }

  @Override
  public void handleRoleAssignmentDelete(String id) {
    privilegedRoleAssignmentService.deleteByRoleAssignment(id);
  }

  @Override
  public void handleUserGroupCreate(UserGroupDBO userGroupDBO) {
    // Do nothing
  }

  @Override
  public void handleUserGroupUpdate(UserGroupDBO userGroupDBO) {
    Criteria criteria = new Criteria();
    criteria.and(RoleAssignmentDBOKeys.scopeIdentifier).is(userGroupDBO.getScopeIdentifier());
    criteria.and(RoleAssignmentDBOKeys.resourceGroupIdentifier).in(MANAGED_RESOURCE_GROUP_IDENTIFIERS);
    criteria.and(RoleAssignmentDBOKeys.roleIdentifier).in(roleToPrivilegedRole.keySet());
    criteria.and(RoleAssignmentDBOKeys.principalIdentifier).is(userGroupDBO.getIdentifier());
    criteria.and(RoleAssignmentDBOKeys.principalType).is(PrincipalType.USER_GROUP);
    Page<RoleAssignmentDBO> roleAssignments = roleAssignmentRepository.findAll(criteria, Pageable.unpaged());

    Set<PrivilegedRoleAssignment> privilegedRoleAssignments = new HashSet<>();
    for (RoleAssignmentDBO roleAssignmentDBO : roleAssignments) {
      List<PrivilegedRoleAssignment> privilegedRoleAssignmentList =
          userGroupDBO.getUsers()
              .stream()
              .map(userId
                  -> PrivilegedRoleAssignment.builder()
                         .scopeIdentifier(roleAssignmentDBO.getScopeIdentifier())
                         .principalType(PrincipalType.USER)
                         .principalIdentifier(userId)
                         .roleIdentifier(roleToPrivilegedRole.get(roleAssignmentDBO.getRoleIdentifier()))
                         .userGroupIdentifier(userGroupDBO.getIdentifier())
                         .linkedRoleAssignment(roleAssignmentDBO.getId())
                         .build())
              .collect(Collectors.toList());
      privilegedRoleAssignments.addAll(privilegedRoleAssignmentList);
    }
    privilegedRoleAssignmentService.deleteByUserGroup(userGroupDBO.getIdentifier(), userGroupDBO.getScopeIdentifier());
    privilegedRoleAssignmentService.saveAll(privilegedRoleAssignments);
  }

  @Override
  public void handleUserGroupDelete(String id) {
    // Do Nothing
  }
}
