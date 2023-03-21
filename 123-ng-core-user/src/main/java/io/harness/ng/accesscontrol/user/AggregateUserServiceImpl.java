/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.accesscontrol.user;

import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.role.dto.RoleAssignmentMetadataDTO;
import io.harness.ng.core.user.remote.dto.UserAggregateDTO;
import io.harness.ng.core.user.remote.dto.UserFilter;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@OwnedBy(PL)
public class AggregateUserServiceImpl implements AggregateUserService {
  AccessControlAdminClient accessControlAdminClient;
  NgUserService ngUserService;

  @Override
  public PageResponse<UserAggregateDTO> getAggregatedUsers(Scope scope, String searchTerm, PageRequest pageRequest) {
    PageResponse<UserMetadataDTO> userPage =
        ngUserService.listUsers(scope, pageRequest, UserFilter.builder().searchTerm(searchTerm).build());
    Set<PrincipalDTO> principalDTOs =
        userPage.getContent()
            .stream()
            .map(user -> PrincipalDTO.builder().identifier(user.getUuid()).type(USER).build())
            .collect(Collectors.toSet());

    RoleAssignmentFilterDTO roleAssignmentFilter =
        RoleAssignmentFilterDTO.builder().principalFilter(principalDTOs).build();

    Map<String, List<RoleAssignmentMetadataDTO>> userRoleAssignmentsMap = getPrincipalRoleAssignmentMap(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), roleAssignmentFilter);
    List<UserAggregateDTO> userAggregateList =
        userPage.getContent()
            .stream()
            .map(user
                -> UserAggregateDTO.builder()
                       .roleAssignmentMetadata(
                           userRoleAssignmentsMap.getOrDefault(user.getUuid(), Collections.emptyList()))
                       .user(user)
                       .build())
            .collect(toList());
    return PageUtils.getNGPageResponse(userPage, userAggregateList);
  }

  @Override
  public PageResponse<UserAggregateDTO> getAggregatedUsers(
      Scope scope, ACLAggregateFilter aclAggregateFilter, PageRequest pageRequest) {
    RoleAssignmentFilterDTO roleAssignmentFilterDTO =
        RoleAssignmentFilterDTO.builder()
            .roleFilter(aclAggregateFilter.getRoleIdentifiers())
            .resourceGroupFilter(aclAggregateFilter.getResourceGroupIdentifiers())
            .principalTypeFilter(Collections.singleton(USER))
            .build();
    Map<String, List<RoleAssignmentMetadataDTO>> userRoleAssignmentsMap = getPrincipalRoleAssignmentMap(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), roleAssignmentFilterDTO);

    Set<String> userIds = userRoleAssignmentsMap.keySet();
    PageResponse<UserMetadataDTO> userPage =
        ngUserService.listUsers(scope, pageRequest, UserFilter.builder().identifiers(userIds).build());

    List<UserAggregateDTO> userAggregateList =
        userPage.getContent()
            .stream()
            .map(user
                -> UserAggregateDTO.builder()
                       .roleAssignmentMetadata(
                           userRoleAssignmentsMap.getOrDefault(user.getUuid(), Collections.emptyList()))
                       .user(user)
                       .build())
            .collect(toList());
    return PageUtils.getNGPageResponse(userPage, userAggregateList);
  }

  @Override
  public Optional<UserAggregateDTO> getAggregatedUser(Scope scope, String userId) {
    Optional<UserMetadataDTO> user =
        ngUserService.isUserAtScope(userId, scope) ? ngUserService.getUserMetadata(userId) : Optional.empty();
    if (!user.isPresent()) {
      return Optional.empty();
    }

    RoleAssignmentFilterDTO roleAssignmentFilterDTO =
        RoleAssignmentFilterDTO.builder()
            .principalFilter(Collections.singleton(PrincipalDTO.builder().identifier(userId).type(USER).build()))
            .build();

    List<RoleAssignmentMetadataDTO> roleAssignments = getPrincipalRoleAssignmentMap(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), roleAssignmentFilterDTO)
                                                          .getOrDefault(userId, Collections.emptyList());

    return Optional.of(UserAggregateDTO.builder().roleAssignmentMetadata(roleAssignments).user(user.get()).build());
  }

  private Map<String, List<RoleAssignmentMetadataDTO>> getPrincipalRoleAssignmentMap(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, RoleAssignmentFilterDTO roleAssignmentFilterDTO) {
    RoleAssignmentAggregateResponseDTO roleAssignmentAggregateResponseDTO =
        getResponse(accessControlAdminClient.getAggregatedFilteredRoleAssignments(
            accountIdentifier, orgIdentifier, projectIdentifier, roleAssignmentFilterDTO));

    Map<String, RoleResponseDTO> roleMap = roleAssignmentAggregateResponseDTO.getRoles().stream().collect(
        toMap(e -> e.getRole().getIdentifier(), Function.identity()));

    Map<String, ResourceGroupDTO> resourceGroupMap =
        roleAssignmentAggregateResponseDTO.getResourceGroups().stream().collect(
            toMap(ResourceGroupDTO::getIdentifier, Function.identity()));

    return roleAssignmentAggregateResponseDTO.getRoleAssignments()
        .stream()
        .filter(roleAssignmentDTO
            -> roleMap.containsKey(roleAssignmentDTO.getRoleIdentifier())
                && resourceGroupMap.containsKey(roleAssignmentDTO.getResourceGroupIdentifier()))
        .collect(Collectors.groupingBy(roleAssignment
            -> roleAssignment.getPrincipal().getIdentifier(),
            Collectors.mapping(roleAssignment
                -> RoleAssignmentMetadataDTO.builder()
                       .identifier(roleAssignment.getIdentifier())
                       .roleIdentifier(roleAssignment.getRoleIdentifier())
                       .resourceGroupIdentifier(roleAssignment.getResourceGroupIdentifier())
                       .roleName(roleMap.get(roleAssignment.getRoleIdentifier()).getRole().getName())
                       .resourceGroupName(resourceGroupMap.get(roleAssignment.getResourceGroupIdentifier()).getName())
                       .managedRole(roleMap.get(roleAssignment.getRoleIdentifier()).isHarnessManaged())
                       .managedRoleAssignment(roleAssignment.isManaged())
                       .build(),
                toList())));
  }
}
