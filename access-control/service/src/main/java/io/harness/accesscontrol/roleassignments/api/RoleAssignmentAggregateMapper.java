package io.harness.accesscontrol.roleassignments.api;

import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromParams;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.principals.PrincipalDTOV2;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.api.ResourceGroupDTOMapper;
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

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(PL)
@Singleton
public class RoleAssignmentAggregateMapper {
  ScopeService scopeService;
  RoleService roleService;
  ResourceGroupService resourceGroupService;

  RoleDTOMapper roleDTOMapper;

  @Inject
  public RoleAssignmentAggregateMapper(ScopeService scopeService, RoleService roleService,
      ResourceGroupService resourceGroupService, RoleDTOMapper roleDTOMapper) {
    this.scopeService = scopeService;
    this.roleService = roleService;
    this.resourceGroupService = resourceGroupService;
    this.roleDTOMapper = roleDTOMapper;
  }

  public ScopeResponseDTO getScopeName(String scopeIdentifier) {
    Scope scope = scopeService.buildScopeFromScopeIdentifier(scopeIdentifier);
    ScopeDTO harnessScopeParams = ScopeMapper.toDTO(scope);
    String accountName =
        scopeService
            .get(fromParams(
                HarnessScopeParams.builder().accountIdentifier(harnessScopeParams.getAccountIdentifier()).build())
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
      scopeResponseDTO.setOrgName(scopeService
                                      .get(fromParams(HarnessScopeParams.builder()
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
              .get(fromParams(HarnessScopeParams.builder()
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

  public RoleAssignmentAggregate toDTO(RoleAssignment response, String principalName) {
    return RoleAssignmentAggregate.builder()
        .identifier(response.getIdentifier())
        .principal(PrincipalDTOV2.builder()
                       .scopeLevel(response.getPrincipalScopeLevel())
                       .identifier(response.getPrincipalIdentifier())
                       .name(principalName)
                       .type(response.getPrincipalType())
                       .build())
        .disabled(response.isDisabled())
        .role(roleDTOMapper.toResponseDTO(
            roleService.get(response.getRoleIdentifier(), response.getScopeIdentifier(), NO_FILTER).orElse(null)))
        .resourceGroup(ResourceGroupDTOMapper.toDTO(
            resourceGroupService.get(response.getResourceGroupIdentifier(), response.getScopeIdentifier(), NO_FILTER)
                .orElse(null)))
        .scope(getScopeName(response.getScopeIdentifier()))
        .harnessManaged(response.isManaged())
        .createdAt(response.getCreatedAt())
        .lastModifiedAt(response.getLastModifiedAt())
        .build();
  }
}
