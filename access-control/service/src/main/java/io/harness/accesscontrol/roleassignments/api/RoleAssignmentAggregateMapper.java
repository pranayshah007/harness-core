/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.principals.PrincipalDTOV2;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.roles.api.RoleDTOMapper;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.ScopeResponseDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.NGRestUtils;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.resourcegroupclient.remote.ResourceGroupClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;

@OwnedBy(PL)
@Singleton
public class RoleAssignmentAggregateMapper {
  ScopeService scopeService;
  RoleService roleService;
  ResourceGroupService resourceGroupService;
  private final ResourceGroupClient resourceGroupClient;

  RoleDTOMapper roleDTOMapper;

  @Inject
  public RoleAssignmentAggregateMapper(ScopeService scopeService, RoleService roleService,
      ResourceGroupService resourceGroupService, @Named("PRIVILEGED") ResourceGroupClient resourceGroupClient,
      RoleDTOMapper roleDTOMapper) {
    this.scopeService = scopeService;
    this.roleService = roleService;
    this.resourceGroupService = resourceGroupService;
    this.resourceGroupClient = resourceGroupClient;
    this.roleDTOMapper = roleDTOMapper;
  }

  public ScopeResponseDTO getScopeName(String scopeIdentifier) {
    Scope scope = scopeService.buildScopeFromScopeIdentifier(scopeIdentifier);
    ScopeDTO harnessScopeParams = ScopeMapper.toDTO(scope);
    String accountName = scopeService
                             .get(ScopeMapper
                                      .fromParams(HarnessScopeParams.builder()
                                                      .accountIdentifier(harnessScopeParams.getAccountIdentifier())
                                                      .build())
                                      .toString())
                             .orElse(null)
                             .getInstanceName();
    ScopeResponseDTO scopeResponseDTO = ScopeResponseDTO.builder()
                                            .accountIdentifier(harnessScopeParams.getAccountIdentifier())
                                            .orgIdentifier(harnessScopeParams.getOrgIdentifier())
                                            .projectIdentifier(harnessScopeParams.getProjectIdentifier())
                                            .accountName(accountName)
                                            .build();
    if (harnessScopeParams.getOrgIdentifier() != null) {
      scopeResponseDTO.setOrgName(
          scopeService
              .get(ScopeMapper
                       .fromParams(HarnessScopeParams.builder()
                                       .accountIdentifier(harnessScopeParams.getAccountIdentifier())
                                       .orgIdentifier(harnessScopeParams.getOrgIdentifier())
                                       .build())
                       .toString())
              .orElse(null)
              .getInstanceName());
    }
    if (harnessScopeParams.getProjectIdentifier() != null) {
      scopeResponseDTO.setProjectName(
          scopeService
              .get(ScopeMapper
                       .fromParams(HarnessScopeParams.builder()
                                       .accountIdentifier(harnessScopeParams.getAccountIdentifier())
                                       .orgIdentifier(harnessScopeParams.getOrgIdentifier())
                                       .projectIdentifier(harnessScopeParams.getProjectIdentifier())
                                       .build())
                       .toString())
              .orElse(null)
              .getInstanceName());
    }
    return scopeResponseDTO;
  }

  public RoleAssignmentAggregate toDTO(RoleAssignment response, String principalName, String principalEmail) {
    Scope scope = scopeService.buildScopeFromScopeIdentifier(response.getScopeIdentifier());
    ScopeDTO scopeParams = ScopeMapper.toDTO(scope);
    Optional<ResourceGroupResponse> resourceGroupResponse = Optional.ofNullable(
        NGRestUtils.getResponse(resourceGroupClient.getResourceGroup(response.getResourceGroupIdentifier(),
            scopeParams.getAccountIdentifier(), scopeParams.getOrgIdentifier(), scopeParams.getProjectIdentifier())));
    return RoleAssignmentAggregate.builder()
        .identifier(response.getIdentifier())
        .principal(PrincipalDTOV2.builder()
                       .scopeLevel(response.getPrincipalScopeLevel())
                       .identifier(response.getPrincipalIdentifier())
                       .name(principalName)
                       .email(principalEmail)
                       .type(response.getPrincipalType())
                       .build())
        .disabled(response.isDisabled())
        .role(roleDTOMapper.toResponseDTO(
            roleService.get(response.getRoleIdentifier(), response.getScopeIdentifier(), NO_FILTER).orElse(null)))
        .resourceGroup(resourceGroupResponse.map(ResourceGroupResponse::getResourceGroup).orElse(null))
        .scope(getScopeName(response.getScopeIdentifier()))
        .harnessManaged(response.isManaged())
        .createdAt(response.getCreatedAt())
        .lastModifiedAt(response.getLastModifiedAt())
        .build();
  }
}
