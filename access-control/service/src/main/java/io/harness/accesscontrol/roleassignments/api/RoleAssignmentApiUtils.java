/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.accesscontrol.AccessControlPermissions.EDIT_SERVICEACCOUNT_PERMISSION;
import static io.harness.accesscontrol.AccessControlPermissions.MANAGE_USERGROUP_PERMISSION;
import static io.harness.accesscontrol.AccessControlPermissions.MANAGE_USER_PERMISSION;
import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromParams;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.toParams;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.toParentScopeParams;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.SortOrder.Builder.aSortOrder;
import static io.harness.beans.SortOrder.OrderType.DESC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.AccessControlPermissions;
import io.harness.accesscontrol.AccessControlResourceTypes;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.serviceaccounts.HarnessServiceAccountService;
import io.harness.accesscontrol.principals.serviceaccounts.ServiceAccountService;
import io.harness.accesscontrol.principals.usergroups.HarnessUserGroupService;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.principals.users.HarnessUserService;
import io.harness.accesscontrol.principals.users.UserService;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.HarnessScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.spec.server.accesscontrol.v1.model.Principal;
import io.harness.spec.server.accesscontrol.v1.model.RoleAssignment;
import io.harness.spec.server.accesscontrol.v1.model.RoleAssignmentResponse;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;

@OwnedBy(PL)
@Singleton
public class RoleAssignmentApiUtils {
  public static final String ROLE_ASSIGNMENT_DOES_NOT_EXISTS = "Role Assignment with given identifier doesn't exists";

  Validator validator;
  HarnessResourceGroupService harnessResourceGroupService;
  HarnessUserGroupService harnessUserGroupService;
  HarnessUserService harnessUserService;
  HarnessServiceAccountService harnessServiceAccountService;
  HarnessScopeService harnessScopeService;
  ScopeService scopeService;
  ResourceGroupService resourceGroupService;
  UserGroupService userGroupService;
  UserService userService;
  ServiceAccountService serviceAccountService;
  RoleAssignmentDTOMapper roleAssignmentDTOMapper;
  AccessControlClient accessControlClient;

  @Inject
  public RoleAssignmentApiUtils(Validator validator, HarnessResourceGroupService harnessResourceGroupService,
      HarnessUserGroupService harnessUserGroupService, HarnessUserService harnessUserService,
      HarnessServiceAccountService harnessServiceAccountService, HarnessScopeService harnessScopeService,
      ScopeService scopeService, ResourceGroupService resourceGroupService, UserGroupService userGroupService,
      UserService userService, ServiceAccountService serviceAccountService,
      RoleAssignmentDTOMapper roleAssignmentDTOMapper, AccessControlClient accessControlClient) {
    this.validator = validator;
    this.harnessResourceGroupService = harnessResourceGroupService;
    this.harnessUserGroupService = harnessUserGroupService;
    this.harnessUserService = harnessUserService;
    this.harnessServiceAccountService = harnessServiceAccountService;
    this.harnessScopeService = harnessScopeService;
    this.scopeService = scopeService;
    this.resourceGroupService = resourceGroupService;
    this.userGroupService = userGroupService;
    this.userService = userService;
    this.serviceAccountService = serviceAccountService;
    this.roleAssignmentDTOMapper = roleAssignmentDTOMapper;
    this.accessControlClient = accessControlClient;
  }

  public PrincipalDTO getPrincipalDto(Principal principal) {
    PrincipalDTO principalDTO = PrincipalDTO.builder()
                                    .identifier(principal.getIdentifier())
                                    .scopeLevel(principal.getScopeLevel())
                                    .type(getPrincipalType(principal.getType()))
                                    .build();

    Set<ConstraintViolation<PrincipalDTO>> violations = validator.validate(principalDTO);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }

    return principalDTO;
  }

  private PrincipalType getPrincipalType(Principal.TypeEnum type) {
    return PrincipalType.valueOf(type.value());
  }

  public RoleAssignmentDTO getRoleAssignmentDto(RoleAssignment request) {
    RoleAssignmentDTO roleAssignmentDTO = RoleAssignmentDTO.builder()
                                              .identifier(request.getIdentifier())
                                              .resourceGroupIdentifier(request.getResourceGroup())
                                              .roleIdentifier(request.getRole())
                                              .principal(getPrincipalDto(request.getPrincipal()))
                                              .disabled(request.isDisabled())
                                              .managed(request.isManaged())
                                              .build();

    Set<ConstraintViolation<RoleAssignmentDTO>> violations = validator.validate(roleAssignmentDTO);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return roleAssignmentDTO;
  }

  public List<RoleAssignmentResponse> getRoleAssignmentResponses(List<RoleAssignmentResponseDTO> responseDTOs) {
    if (CollectionUtils.isEmpty(responseDTOs)) {
      return Collections.emptyList();
    }

    return responseDTOs.stream().map(this::getRoleAssignmentResponse).collect(Collectors.toList());
  }

  public RoleAssignmentResponse getRoleAssignmentResponse(RoleAssignmentResponseDTO responseDTO) {
    RoleAssignmentResponse roleAssignmentResponse = new RoleAssignmentResponse();
    roleAssignmentResponse.setRoleAssignment(getRoleAssignment(responseDTO.getRoleAssignment()));
    roleAssignmentResponse.setHarnessManaged(responseDTO.isHarnessManaged());
    roleAssignmentResponse.setCreated(responseDTO.getCreatedAt());
    roleAssignmentResponse.setUpdated(responseDTO.getLastModifiedAt());

    return roleAssignmentResponse;
  }

  private RoleAssignment getRoleAssignment(RoleAssignmentDTO roleAssignmentDto) {
    RoleAssignment roleAssignment = new RoleAssignment();
    roleAssignment.setIdentifier(roleAssignmentDto.getIdentifier());
    roleAssignment.setResourceGroup(roleAssignmentDto.getResourceGroupIdentifier());
    roleAssignment.setRole(roleAssignmentDto.getRoleIdentifier());
    roleAssignment.setDisabled(roleAssignmentDto.isDisabled());
    roleAssignment.setManaged(roleAssignmentDto.isManaged());
    roleAssignment.setPrincipal(getPrincipal(roleAssignmentDto.getPrincipal()));

    return roleAssignment;
  }

  private Principal getPrincipal(PrincipalDTO principalDto) {
    Principal principal = new Principal();
    principal.setIdentifier(principalDto.getIdentifier());
    principal.setScopeLevel(principalDto.getScopeLevel());
    principal.setType(Principal.TypeEnum.valueOf(principalDto.getType().name()));

    return principal;
  }

  public io.harness.accesscontrol.roleassignments.RoleAssignment buildRoleAssignmentWithPrincipalScopeLevel(
      io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignment, Scope scope) {
    // For principalType USER, principalScopeLevel should be always null.
    String principalScopeLevel = null;

    if (USER_GROUP.equals(roleAssignment.getPrincipalType()) && !isEmpty(roleAssignment.getPrincipalScopeLevel())) {
      principalScopeLevel = roleAssignment.getPrincipalScopeLevel();
    }
    if (USER_GROUP.equals(roleAssignment.getPrincipalType()) && isEmpty(roleAssignment.getPrincipalScopeLevel())) {
      principalScopeLevel = roleAssignment.getScopeLevel();
    }

    if (SERVICE_ACCOUNT.equals(roleAssignment.getPrincipalType())) {
      if (isNotEmpty(roleAssignment.getPrincipalScopeLevel())
          && !roleAssignment.getPrincipalScopeLevel().equals(scope.getLevel().toString())) {
        throw new InvalidRequestException(
            "Cannot create role assignment for given Service Account. Principal should be of same scope as of role assignment.");
      }
      principalScopeLevel = getServiceAccountScopeLevel(roleAssignment.getPrincipalIdentifier(), scope);
    }
    return io.harness.accesscontrol.roleassignments.RoleAssignment.builder()
        .identifier(roleAssignment.getIdentifier())
        .scopeIdentifier(roleAssignment.getScopeIdentifier())
        .scopeLevel(roleAssignment.getScopeLevel())
        .resourceGroupIdentifier(roleAssignment.getResourceGroupIdentifier())
        .roleIdentifier(roleAssignment.getRoleIdentifier())
        .principalScopeLevel(principalScopeLevel)
        .principalIdentifier(roleAssignment.getPrincipalIdentifier())
        .principalType(roleAssignment.getPrincipalType())
        .managed(roleAssignment.isManaged())
        .internal(roleAssignment.isInternal())
        .disabled(roleAssignment.isDisabled())
        .createdAt(roleAssignment.getCreatedAt())
        .lastModifiedAt(roleAssignment.getLastModifiedAt())
        .build();
  }

  private String getServiceAccountScopeLevel(@NotNull String serviceAccountIdentifier, @NotNull Scope scope) {
    harnessServiceAccountService.sync(serviceAccountIdentifier, scope);
    return scope.getLevel().toString();
  }

  public static void validateDeprecatedResourceGroupNotUsed(String resourceGroupIdentifier, String scopeLevel) {
    if (HarnessResourceGroupConstants.DEPRECATED_ALL_RESOURCES_RESOURCE_GROUP_IDENTIFIER.equals(
            resourceGroupIdentifier)) {
      throw new InvalidRequestException(String.format("%s is deprecated, please use %s.",
          HarnessResourceGroupConstants.DEPRECATED_ALL_RESOURCES_RESOURCE_GROUP_IDENTIFIER,
          HarnessResourceGroupConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER));
    }
  }

  public void validatePrincipalScopeLevelConditions(PrincipalDTO principalDTO, ScopeLevel scopeLevel) {
    if (principalDTO.getScopeLevel() == null) {
      return;
    }
    if (!isValidParentScopeLevel(HarnessScopeLevel.valueOf(principalDTO.getScopeLevel().toUpperCase()), scopeLevel)) {
      throw new InvalidRequestException(
          String.format("Principal scope level cannot be %s for %s scoped role assignment.",
              principalDTO.getScopeLevel(), scopeLevel.toString()));
    }
  }

  private static boolean isValidParentScopeLevel(ScopeLevel parentScopeLevel, ScopeLevel scopeLevel) {
    return parentScopeLevel.getRank() <= scopeLevel.getRank();
  }

  public void checkUpdatePermission(
      HarnessScopeParams harnessScopeParams, io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignment) {
    int scopeRank = fromParams(harnessScopeParams).getLevel().getRank();
    int principalScopeRank = roleAssignment.getPrincipalScopeLevel() == null
        ? scopeRank
        : HarnessScopeLevel.valueOf(roleAssignment.getPrincipalScopeLevel().toUpperCase()).getRank();
    boolean allPrincipalUpdateCheck = roleAssignment.getPrincipalScopeLevel() != null && scopeRank > principalScopeRank;

    if (USER_GROUP.equals(roleAssignment.getPrincipalType())) {
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(harnessScopeParams.getAccountIdentifier(), harnessScopeParams.getOrgIdentifier(),
              harnessScopeParams.getProjectIdentifier()),
          Resource.of(AccessControlResourceTypes.USER_GROUP,
              allPrincipalUpdateCheck ? null : roleAssignment.getPrincipalIdentifier()),
          MANAGE_USERGROUP_PERMISSION);
    } else if (USER.equals(roleAssignment.getPrincipalType())) {
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(harnessScopeParams.getAccountIdentifier(), harnessScopeParams.getOrgIdentifier(),
              harnessScopeParams.getProjectIdentifier()),
          Resource.of(AccessControlResourceTypes.USER, roleAssignment.getPrincipalIdentifier()),
          MANAGE_USER_PERMISSION);
    } else if (SERVICE_ACCOUNT.equals(roleAssignment.getPrincipalType())) {
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(harnessScopeParams.getAccountIdentifier(), harnessScopeParams.getOrgIdentifier(),
              harnessScopeParams.getProjectIdentifier()),
          Resource.of(AccessControlResourceTypes.SERVICEACCOUNT,
              allPrincipalUpdateCheck ? null : roleAssignment.getPrincipalIdentifier()),
          EDIT_SERVICEACCOUNT_PERMISSION);
    } else {
      throw new InvalidRequestException(String.format(
          "Role assignments for principalType %s cannot be changed", roleAssignment.getPrincipalType().toString()));
    }
  }

  public boolean checkViewPermission(HarnessScopeParams harnessScopeParams, PrincipalType principalType) {
    String resourceType = null;
    String permissionIdentifier = null;
    if (USER.equals(principalType)) {
      resourceType = AccessControlResourceTypes.USER;
      permissionIdentifier = AccessControlPermissions.VIEW_USER_PERMISSION;
    } else if (USER_GROUP.equals(principalType)) {
      resourceType = AccessControlResourceTypes.USER_GROUP;
      permissionIdentifier = AccessControlPermissions.VIEW_USERGROUP_PERMISSION;
    } else if (SERVICE_ACCOUNT.equals(principalType)) {
      resourceType = AccessControlResourceTypes.SERVICEACCOUNT;
      permissionIdentifier = AccessControlPermissions.VIEW_SERVICEACCOUNT_PERMISSION;
    } else {
      throw new InvalidRequestException("Invalid Principal type: " + principalType.toString());
    }
    return accessControlClient.hasAccess(ResourceScope.builder()
                                             .projectIdentifier(harnessScopeParams.getProjectIdentifier())
                                             .orgIdentifier(harnessScopeParams.getOrgIdentifier())
                                             .accountIdentifier(harnessScopeParams.getAccountIdentifier())
                                             .build(),
        Resource.of(resourceType, null), permissionIdentifier);
  }

  public void syncDependencies(io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignment, Scope scope) {
    if (!scopeService.isPresent(scope.toString())) {
      harnessScopeService.sync(scope);
    }
    if (!resourceGroupService.get(roleAssignment.getResourceGroupIdentifier(), scope.toString(), NO_FILTER)
             .isPresent()) {
      harnessResourceGroupService.sync(roleAssignment.getResourceGroupIdentifier(), scope);
    }
    if (roleAssignment.getPrincipalType().equals(USER_GROUP)) {
      Scope principalScope = fromParams(toParentScopeParams(toParams(scope), roleAssignment.getPrincipalScopeLevel()));
      if (!userGroupService.get(roleAssignment.getPrincipalIdentifier(), principalScope.toString()).isPresent()) {
        harnessUserGroupService.sync(roleAssignment.getPrincipalIdentifier(), principalScope);
      }
    }
    if (roleAssignment.getPrincipalType().equals(USER)
        && !userService.get(roleAssignment.getPrincipalIdentifier(), scope.toString()).isPresent()) {
      harnessUserService.sync(roleAssignment.getPrincipalIdentifier(), scope);
    }
    if (roleAssignment.getPrincipalType().equals(SERVICE_ACCOUNT)) {
      Scope principalScope = fromParams(toParentScopeParams(toParams(scope), roleAssignment.getPrincipalScopeLevel()));
      if (!serviceAccountService.get(roleAssignment.getPrincipalIdentifier(), principalScope.toString()).isPresent()) {
        harnessServiceAccountService.sync(roleAssignment.getPrincipalIdentifier(), principalScope);
      }
    }
  }

  public PageRequest getPageRequest(int page, int limit, String sort, String order) {
    List<SortOrder> sortOrders;
    String mappedFieldName = getFieldName(sort);
    if (mappedFieldName != null) {
      SortOrder.OrderType fieldOrder = EnumUtils.getEnum(SortOrder.OrderType.class, order, DESC);
      sortOrders = ImmutableList.of(aSortOrder().withField(mappedFieldName, fieldOrder).build());
    } else {
      sortOrders = ImmutableList.of(aSortOrder().withField(RoleAssignmentDBOKeys.lastModifiedAt, DESC).build());
    }
    return new PageRequest(page, limit, sortOrders);
  }

  private String getFieldName(String sort) {
    SortFields sortField = SortFields.fromValue(sort);
    if (sortField == null) {
      sortField = SortFields.UNSUPPORTED;
    }
    switch (sortField) {
      case IDENTIFIER:
        return RoleAssignmentDBOKeys.identifier;
      case CREATED:
        return RoleAssignmentDBOKeys.createdAt;
      case UPDATED:
        return RoleAssignmentDBOKeys.lastModifiedAt;
      case UNSUPPORTED:
      default:
        return null;
    }
  }

  public enum SortFields {
    IDENTIFIER("identifier"),
    CREATED("created"),
    UPDATED("updated"),
    UNSUPPORTED(null);

    private String field;

    SortFields(String field) {
      this.field = field;
    }

    public String value() {
      return field;
    }

    @Override
    public String toString() {
      return String.valueOf(field);
    }

    public static SortFields fromValue(String value) {
      for (SortFields sortField : SortFields.values()) {
        if (String.valueOf(sortField.field).equals(value)) {
          return sortField;
        }
      }
      return null;
    }
  }
}
