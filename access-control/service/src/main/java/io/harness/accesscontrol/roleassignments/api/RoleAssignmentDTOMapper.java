/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.accesscontrol.common.filter.ManagedFilter.buildFromSet;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.commons.validation.ValidationResultMapper;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.RoleAssignment.RoleAssignmentBuilder;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.ScopeFilter;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationRequest;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationResult;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.ScopeFilterType;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.utils.CryptoUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(PL)
@Singleton
public class RoleAssignmentDTOMapper {
  private final ScopeService scopeService;

  @Inject
  public RoleAssignmentDTOMapper(ScopeService scopeService) {
    this.scopeService = scopeService;
  }

  public RoleAssignmentResponseDTO toResponseDTO(RoleAssignment object) {
    Scope scope = scopeService.buildScopeFromScopeIdentifier(object.getScopeIdentifier());
    return RoleAssignmentResponseDTO.builder()
        .roleAssignment(RoleAssignmentDTO.builder()
                            .identifier(object.getIdentifier())
                            .principal(PrincipalDTO.builder()
                                           .scopeLevel(object.getPrincipalScopeLevel())
                                           .identifier(object.getPrincipalIdentifier())
                                           .type(object.getPrincipalType())
                                           .build())
                            .resourceGroupIdentifier(object.getResourceGroupIdentifier())
                            .roleIdentifier(object.getRoleIdentifier())
                            .disabled(object.isDisabled())
                            .managed(object.isManaged())
                            .build())
        .scope(ScopeMapper.toDTO(scope))
        .harnessManaged(object.isManaged())
        .createdAt(object.getCreatedAt())
        .lastModifiedAt(object.getLastModifiedAt())
        .build();
  }

  public static RoleAssignmentDTO toDTO(RoleAssignment object) {
    return RoleAssignmentDTO.builder()
        .identifier(object.getIdentifier())
        .principal(PrincipalDTO.builder()
                       .scopeLevel(object.getPrincipalScopeLevel())
                       .identifier(object.getPrincipalIdentifier())
                       .type(object.getPrincipalType())
                       .build())
        .resourceGroupIdentifier(object.getResourceGroupIdentifier())
        .roleIdentifier(object.getRoleIdentifier())
        .disabled(object.isDisabled())
        .managed(object.isManaged())
        .build();
  }

  public static RoleAssignment fromDTO(Scope scope, RoleAssignmentDTO object) {
    return fromDTO(scope, object, false);
  }

  public static RoleAssignment fromDTO(Scope scope, RoleAssignmentDTO object, boolean managed) {
    RoleAssignmentBuilder roleAssignmentBuilder =
        RoleAssignment.builder()
            .identifier(isEmpty(object.getIdentifier())
                    ? "role_assignment_".concat(CryptoUtils.secureRandAlphaNumString(20))
                    : object.getIdentifier())
            .principalIdentifier(object.getPrincipal().getIdentifier())
            .principalScopeLevel(object.getPrincipal().getScopeLevel())
            .principalType(object.getPrincipal().getType())
            .resourceGroupIdentifier(object.getResourceGroupIdentifier())
            .roleIdentifier(object.getRoleIdentifier())
            .disabled(object.isDisabled())
            .managed(managed)
            .internal(object.isInternal())
            .scopeIdentifier(scope.toString())
            .scopeLevel(scope.getLevel().toString());
    return roleAssignmentBuilder.build();
  }

  public static RoleAssignmentFilter fromV2(RoleAssignmentFilterV2 object) {
    RoleAssignmentFilter roleAssignmentFilter =
        RoleAssignmentFilter.builder()
            .scopeFilters(object.getScopeFilters() == null
                    ? new HashSet()
                    : object.getScopeFilters()
                          .stream()
                          .map(filter
                              -> ScopeFilter.builder()
                                     .scope(ScopeMapper
                                                .fromDTO(ScopeDTO.builder()
                                                             .accountIdentifier(filter.getAccountIdentifier())
                                                             .orgIdentifier(filter.getOrgIdentifier())
                                                             .projectIdentifier(filter.getProjectIdentifier())
                                                             .build())
                                                .toString()

                                             )
                                     .includeChildScopes(
                                         ScopeFilterType.INCLUDING_CHILD_SCOPES.equals(filter.getFilter()))
                                     .build())
                          .collect(Collectors.toSet()))
            .roleFilter(object.getRoleFilter() == null ? new HashSet<>() : object.getRoleFilter())
            .resourceGroupFilter(
                object.getResourceGroupFilter() == null ? new HashSet<>() : object.getResourceGroupFilter())
            .disabledFilter(
                object.getDisabledFilter() == null ? new HashSet<>() : Sets.newHashSet(object.getDisabledFilter()))
            .managedFilter(Objects.isNull(object.getHarnessManagedFilter())
                    ? ManagedFilter.NO_FILTER
                    : (object.getHarnessManagedFilter() == Boolean.TRUE ? ManagedFilter.ONLY_MANAGED
                                                                        : ManagedFilter.ONLY_CUSTOM))
            .principalTypeFilter(
                object.getPrincipalTypeFilter() == null ? new HashSet<>() : object.getPrincipalTypeFilter())
            .build();
    if (object.getPrincipalFilter() != null) {
      roleAssignmentFilter.setPrincipalFilter(
          new HashSet<>(List.of(Principal.builder()
                                    .principalType(object.getPrincipalFilter().getType())
                                    .principalIdentifier(object.getPrincipalFilter().getIdentifier())
                                    .principalScopeLevel(object.getPrincipalFilter().getScopeLevel())
                                    .build())));
    }
    return roleAssignmentFilter;
  }

  public RoleAssignmentFilter fromDTO(String identifier, List<UserGroup> userGroups, RoleAssignmentFilterV2 object) {
    Set<Principal> principalFilter = new HashSet<>();
    principalFilter.add(Principal.builder().principalType(PrincipalType.USER).principalIdentifier(identifier).build());
    userGroups.stream().forEach(userGroup -> {
      principalFilter.add(
          Principal.builder()
              .principalScopeLevel(
                  scopeService.buildScopeFromScopeIdentifier(userGroup.getScopeIdentifier()).getLevel().toString())
              .principalType(PrincipalType.USER_GROUP)
              .principalIdentifier(userGroup.getIdentifier())
              .build());
    });
    RoleAssignmentFilter roleAssignmentFilter = fromV2(object);
    roleAssignmentFilter.setPrincipalFilter(principalFilter);
    return roleAssignmentFilter;
  }

  public static RoleAssignmentFilter fromDTO(String scopeIdentifier, RoleAssignmentFilterDTO object) {
    return RoleAssignmentFilter.builder()
        .scopeFilter(scopeIdentifier)
        .includeChildScopes(false)
        .roleFilter(object.getRoleFilter() == null ? new HashSet<>() : object.getRoleFilter())
        .resourceGroupFilter(
            object.getResourceGroupFilter() == null ? new HashSet<>() : object.getResourceGroupFilter())
        .principalFilter(object.getPrincipalFilter() == null
                ? new HashSet<>()
                : object.getPrincipalFilter()
                      .stream()
                      .map(principalDTO
                          -> Principal.builder()
                                 .principalScopeLevel(principalDTO.getScopeLevel())
                                 .principalType(principalDTO.getType())
                                 .principalIdentifier(principalDTO.getIdentifier())
                                 .build())
                      .collect(Collectors.toSet()))
        .principalTypeFilter(
            object.getPrincipalTypeFilter() == null ? new HashSet<>() : object.getPrincipalTypeFilter())
        .principalScopeLevelFilter(
            object.getPrincipalScopeLevelFilter() == null ? new HashSet<>() : object.getPrincipalScopeLevelFilter())
        .managedFilter(Objects.isNull(object.getHarnessManagedFilter())
                ? ManagedFilter.NO_FILTER
                : buildFromSet(object.getHarnessManagedFilter()))
        .disabledFilter(object.getDisabledFilter() == null ? new HashSet<>() : object.getDisabledFilter())
        .build();
  }

  public static RoleAssignmentFilter fromDTOIncludingChildScopes(
      String scopeIdentifier, RoleAssignmentFilterDTO object) {
    return RoleAssignmentFilter.builder()
        .scopeFilter(scopeIdentifier)
        .includeChildScopes(true)
        .roleFilter(object.getRoleFilter() == null ? new HashSet<>() : object.getRoleFilter())
        .resourceGroupFilter(
            object.getResourceGroupFilter() == null ? new HashSet<>() : object.getResourceGroupFilter())
        .principalFilter(object.getPrincipalFilter() == null
                ? new HashSet<>()
                : object.getPrincipalFilter()
                      .stream()
                      .map(principalDTO
                          -> Principal.builder()
                                 .principalScopeLevel(principalDTO.getScopeLevel())
                                 .principalType(principalDTO.getType())
                                 .principalIdentifier(principalDTO.getIdentifier())
                                 .build())
                      .collect(Collectors.toSet()))
        .principalTypeFilter(
            object.getPrincipalTypeFilter() == null ? new HashSet<>() : object.getPrincipalTypeFilter())
        .principalScopeLevelFilter(
            object.getPrincipalScopeLevelFilter() == null ? new HashSet<>() : object.getPrincipalScopeLevelFilter())
        .managedFilter(Objects.isNull(object.getHarnessManagedFilter())
                ? ManagedFilter.NO_FILTER
                : buildFromSet(object.getHarnessManagedFilter()))
        .disabledFilter(object.getDisabledFilter() == null ? new HashSet<>() : object.getDisabledFilter())
        .build();
  }

  public static RoleAssignmentValidationRequest fromDTO(Scope scope, RoleAssignmentValidationRequestDTO object) {
    return RoleAssignmentValidationRequest.builder()
        .roleAssignment(fromDTO(scope, object.getRoleAssignment()))
        .validatePrincipal(object.isValidatePrincipal())
        .validateResourceGroup(object.isValidateResourceGroup())
        .validateRole(object.isValidateRole())
        .build();
  }

  public static RoleAssignmentValidationResponseDTO toDTO(RoleAssignmentValidationResult object) {
    return RoleAssignmentValidationResponseDTO.builder()
        .principalValidationResult(ValidationResultMapper.toDTO(object.getPrincipalValidationResult()))
        .resourceGroupValidationResult(ValidationResultMapper.toDTO(object.getResourceGroupValidationResult()))
        .roleValidationResult(ValidationResultMapper.toDTO(object.getRoleValidationResult()))
        .build();
  }
}
