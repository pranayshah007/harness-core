/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO.MODEL_NAME;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTOMapper.fromDTO;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTOMapper.fromDTOIncludingChildScopes;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTOMapper.toDTO;
import static io.harness.accesscontrol.roles.HarnessRoleConstants.ORGANIZATION_VIEWER_ROLE;
import static io.harness.accesscontrol.scopes.core.ScopeHelper.toParentScope;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromParams;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.toParams;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.toParentScopeParams;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.USER_NOT_AUTHORIZED;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.util.stream.Collectors.toList;
import static lombok.AccessLevel.PRIVATE;

import io.harness.accesscontrol.common.validation.ValidationResult;
import io.harness.accesscontrol.commons.validation.HarnessActionValidator;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.principals.users.User;
import io.harness.accesscontrol.principals.users.UserService;
import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.api.ResourceGroupDTOMapper;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter.RoleAssignmentFilterBuilder;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.roleassignments.RoleAssignmentUpdateResult;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentCreateEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentDeleteEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentUpdateEvent;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.roles.api.RoleDTOMapper;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.scopes.ScopeFilterType;
import io.harness.accesscontrol.scopes.ScopeSelector;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.security.annotations.InternalApi;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import javax.ws.rs.NotFoundException;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@ValidateOnExecution
@Singleton
@FieldDefaults(level = PRIVATE, makeFinal = true)
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class RoleAssignmentResourceImpl implements RoleAssignmentResource {
  RoleAssignmentService roleAssignmentService;
  HarnessResourceGroupService harnessResourceGroupService;
  ScopeService scopeService;
  RoleService roleService;
  ResourceGroupService resourceGroupService;
  UserGroupService userGroupService;
  UserService userService;
  RoleAssignmentDTOMapper roleAssignmentDTOMapper;

  RoleAssignmentAggregateMapper roleAssignmentAggregateMapper;
  RoleDTOMapper roleDTOMapper;
  TransactionTemplate transactionTemplate;
  HarnessActionValidator<RoleAssignment> actionValidator;
  OutboxService outboxService;
  RoleAssignmentApiUtils roleAssignmentApiUtils;

  RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Inject
  public RoleAssignmentResourceImpl(RoleAssignmentService roleAssignmentService,
      HarnessResourceGroupService harnessResourceGroupService, ScopeService scopeService, RoleService roleService,
      ResourceGroupService resourceGroupService, UserGroupService userGroupService, UserService userService,
      RoleAssignmentDTOMapper roleAssignmentDTOMapper, RoleAssignmentAggregateMapper roleAssignmentAggregateMapper,
      RoleDTOMapper roleDTOMapper, @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      @Named(MODEL_NAME) HarnessActionValidator<RoleAssignment> actionValidator, OutboxService outboxService,
      RoleAssignmentApiUtils roleAssignmentApiUtils) {
    this.roleAssignmentService = roleAssignmentService;
    this.harnessResourceGroupService = harnessResourceGroupService;
    this.scopeService = scopeService;
    this.roleService = roleService;
    this.resourceGroupService = resourceGroupService;
    this.userGroupService = userGroupService;
    this.userService = userService;
    this.roleAssignmentDTOMapper = roleAssignmentDTOMapper;
    this.roleAssignmentAggregateMapper = roleAssignmentAggregateMapper;
    this.roleDTOMapper = roleDTOMapper;
    this.transactionTemplate = transactionTemplate;
    this.actionValidator = actionValidator;
    this.outboxService = outboxService;
    this.roleAssignmentApiUtils = roleAssignmentApiUtils;
  }

  @Override
  public ResponseDTO<PageResponse<RoleAssignmentResponseDTO>> get(
      PageRequest pageRequest, HarnessScopeParams harnessScopeParams) {
    String scopeIdentifier = fromParams(harnessScopeParams).toString();
    RoleAssignmentFilterBuilder roleAssignmentFilterBuilder =
        RoleAssignmentFilter.builder().scopeFilter(scopeIdentifier);
    Set<PrincipalType> principalTypes = Sets.newHashSet();

    if (roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, USER)) {
      principalTypes.add(USER);
    }

    if (roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, USER_GROUP)) {
      principalTypes.add(USER_GROUP);
    }

    if (roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, SERVICE_ACCOUNT)) {
      principalTypes.add(SERVICE_ACCOUNT);
    }

    if (principalTypes.isEmpty()) {
      throw new UnauthorizedException("Current principal is not authorized to the view the role assignments",
          USER_NOT_AUTHORIZED, WingsException.USER);
    }

    PageResponse<RoleAssignment> pageResponse = roleAssignmentService.list(
        pageRequest, roleAssignmentFilterBuilder.principalTypeFilter(principalTypes).build());
    return ResponseDTO.newResponse(pageResponse.map(roleAssignmentDTOMapper::toResponseDTO));
  }

  @Override
  public ResponseDTO<PageResponse<RoleAssignmentResponseDTO>> get(
      PageRequest pageRequest, HarnessScopeParams harnessScopeParams, RoleAssignmentFilterDTO roleAssignmentFilter) {
    Optional<RoleAssignmentFilter> filter =
        buildRoleAssignmentFilterWithPermissionFilter(harnessScopeParams, roleAssignmentFilter);
    if (!filter.isPresent()) {
      throw new UnauthorizedException("Current principal is not authorized to the view the role assignments",
          USER_NOT_AUTHORIZED, WingsException.USER);
    }
    PageResponse<RoleAssignment> pageResponse = roleAssignmentService.list(pageRequest, filter.get());
    return ResponseDTO.newResponse(pageResponse.map(roleAssignmentDTOMapper::toResponseDTO));
  }

  @Override
  public ResponseDTO<PageResponse<RoleAssignmentResponseDTO>> getFilteredRoleAssignmentsWithInternalRoles(
      PageRequest pageRequest, HarnessScopeParams harnessScopeParams, RoleAssignmentFilterDTO roleAssignmentFilter) {
    Optional<RoleAssignmentFilter> filter =
        buildRoleAssignmentFilterWithPermissionFilter(harnessScopeParams, roleAssignmentFilter);
    if (!filter.isPresent()) {
      throw new UnauthorizedException("Current principal is not authorized to the view the role assignments",
          USER_NOT_AUTHORIZED, WingsException.USER);
    }
    PageResponse<RoleAssignment> pageResponse = roleAssignmentService.list(pageRequest, filter.get(), false);
    return ResponseDTO.newResponse(pageResponse.map(roleAssignmentDTOMapper::toResponseDTO));
  }

  @Override
  public ResponseDTO<PageResponse<RoleAssignmentAggregate>> getList(
      PageRequest pageRequest, HarnessScopeParams harnessScopeParams, RoleAssignmentFilterV2 roleAssignmentFilterV2) {
    Optional<RoleAssignmentFilterV2> roleAssignmentFilterV2WithPermittedFiltersOptional =
        sanitizeRoleAssignmentFilterV2ForPermitted(harnessScopeParams, roleAssignmentFilterV2);
    if (roleAssignmentFilterV2WithPermittedFiltersOptional.isEmpty()) {
      throw new UnauthorizedException("Current principal is not authorized to the view the role assignments",
          USER_NOT_AUTHORIZED, WingsException.USER);
    }
    RoleAssignmentFilterV2 roleAssignmentFilterV2WithPermittedFilters =
        roleAssignmentFilterV2WithPermittedFiltersOptional.get();
    Set<ScopeSelector> scopeFilter = new HashSet<>();
    if (isEmpty(roleAssignmentFilterV2WithPermittedFilters.getScopeFilters())) {
      scopeFilter.add(ScopeSelector.builder()
                          .accountIdentifier(harnessScopeParams.getAccountIdentifier())
                          .orgIdentifier(harnessScopeParams.getOrgIdentifier())
                          .projectIdentifier(harnessScopeParams.getProjectIdentifier())
                          .filter(ScopeFilterType.EXCLUDING_CHILD_SCOPES)
                          .build());
    } else {
      scopeFilter.addAll(roleAssignmentFilterV2WithPermittedFilters.getScopeFilters());
    }
    roleAssignmentFilterV2WithPermittedFilters.setScopeFilters(scopeFilter);

    PrincipalDTO principalFilter = roleAssignmentFilterV2WithPermittedFilters.getPrincipalFilter();
    RoleAssignmentFilter filter = null;
    List<UserGroup> userGroups = new ArrayList<>();
    if (principalFilter != null) {
      if (USER.equals(principalFilter.getType())) {
        userGroups.addAll(userGroupService.list(principalFilter.getIdentifier()));
        filter = roleAssignmentDTOMapper.fromDTO(
            principalFilter.getIdentifier(), userGroups, roleAssignmentFilterV2WithPermittedFilters);
      } else {
        filter = RoleAssignmentDTOMapper.fromV2(roleAssignmentFilterV2WithPermittedFilters);
      }
    } else {
      filter = RoleAssignmentDTOMapper.fromV2(roleAssignmentFilterV2WithPermittedFilters);
    }

    PageResponse<RoleAssignment> pageResponse = roleAssignmentService.list(pageRequest, filter);
    PageResponse<RoleAssignmentAggregate> roleAssignmentAggregateWithScope = pageResponse.map(response -> {
      String principalName = null;
      String principalEmail = null;
      if (USER.equals(response.getPrincipalType())) {
        Optional<User> userData = userService.get(response.getPrincipalIdentifier(), response.getScopeIdentifier());
        if (userData.isPresent()) {
          principalName = userData.get().getName();
          principalEmail = userData.get().getEmail();
        }
      } else if (USER_GROUP.equals(response.getPrincipalType())) {
        Scope scope = toParentScope(scopeService.buildScopeFromScopeIdentifier(response.getScopeIdentifier()),
            response.getPrincipalScopeLevel());
        Optional<UserGroup> principal = userGroupService.get(response.getPrincipalIdentifier(), scope.toString());
        if (principal.isPresent()) {
          principalName = principal.get().getName();
        }
      }

      return roleAssignmentAggregateMapper.toDTO(response, principalName, principalEmail);
    });

    return ResponseDTO.newResponse(roleAssignmentAggregateWithScope);
  }

  private Optional<RoleAssignmentFilterV2> sanitizeRoleAssignmentFilterV2ForPermitted(
      HarnessScopeParams harnessScopeParams, RoleAssignmentFilterV2 roleAssignmentFilter) {
    boolean hasAccessToUserRoleAssignments = roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, USER);
    boolean hasAccessToUserGroupRoleAssignments =
        roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, USER_GROUP);
    boolean hasAccessToServiceAccountRoleAssignments =
        roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, SERVICE_ACCOUNT);

    if (roleAssignmentFilter.getPrincipalFilter() != null) {
      if ((roleAssignmentFilter.getPrincipalFilter().getType().equals(USER) && !hasAccessToUserRoleAssignments)
          || (roleAssignmentFilter.getPrincipalFilter().getType().equals(USER_GROUP)
              && !hasAccessToUserGroupRoleAssignments)
          || (roleAssignmentFilter.getPrincipalFilter().getType().equals(SERVICE_ACCOUNT)
              && !hasAccessToServiceAccountRoleAssignments)) {
        return Optional.empty();
      }
    } else if (isNotEmpty(roleAssignmentFilter.getPrincipalTypeFilter())) {
      if (!hasAccessToUserRoleAssignments) {
        roleAssignmentFilter.getPrincipalTypeFilter().remove(USER);
      }
      if (!hasAccessToUserGroupRoleAssignments) {
        roleAssignmentFilter.getPrincipalTypeFilter().remove(USER_GROUP);
      }
      if (!hasAccessToServiceAccountRoleAssignments) {
        roleAssignmentFilter.getPrincipalTypeFilter().remove(SERVICE_ACCOUNT);
      }
      if (isEmpty(roleAssignmentFilter.getPrincipalTypeFilter())) {
        return Optional.empty();
      }
    } else {
      Set<PrincipalType> principalTypes = Sets.newHashSet();
      if (hasAccessToUserRoleAssignments) {
        principalTypes.add(USER);
      }

      if (hasAccessToUserGroupRoleAssignments) {
        principalTypes.add(USER_GROUP);
      }

      if (hasAccessToServiceAccountRoleAssignments) {
        principalTypes.add(SERVICE_ACCOUNT);
      }
      if (principalTypes.isEmpty()) {
        return Optional.empty();
      } else {
        roleAssignmentFilter.setPrincipalTypeFilter(principalTypes);
      }
    }
    return Optional.of(roleAssignmentFilter);
  }

  @Override
  public ResponseDTO<List<RoleAssignmentResponseDTO>> getAllIncludingChildScopes(
      HarnessScopeParams harnessScopeParams, RoleAssignmentFilterDTO roleAssignmentFilterDTO) {
    Scope scope = fromParams(harnessScopeParams);
    RoleAssignmentFilter roleAssignmentFilter = fromDTOIncludingChildScopes(scope.toString(), roleAssignmentFilterDTO);

    PageRequest pageRequest = PageRequest.builder().pageSize(1000).build();
    List<RoleAssignment> roleAssignments = roleAssignmentService.list(pageRequest, roleAssignmentFilter).getContent();
    return ResponseDTO.newResponse(
        roleAssignments.stream()
            .filter(roleAssignment
                -> roleAssignmentApiUtils.checkViewPermission(
                    toParams(scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier())),
                    roleAssignment.getPrincipalType()))
            .map(roleAssignmentDTOMapper::toResponseDTO)
            .collect(Collectors.toList()));
  }

  @Override
  public ResponseDTO<RoleAssignmentAggregateResponseDTO> getAggregated(
      HarnessScopeParams harnessScopeParams, RoleAssignmentFilterDTO roleAssignmentFilter) {
    Scope scope = fromParams(harnessScopeParams);
    Optional<RoleAssignmentFilter> filter =
        buildRoleAssignmentFilterWithPermissionFilter(harnessScopeParams, roleAssignmentFilter);
    if (!filter.isPresent()) {
      throw new UnauthorizedException("Current principal is not authorized to the view the role assignments",
          USER_NOT_AUTHORIZED, WingsException.USER);
    }
    PageRequest pageRequest = PageRequest.builder().pageSize(1000).build();
    List<RoleAssignment> roleAssignments = roleAssignmentService.list(pageRequest, filter.get()).getContent();
    List<String> roleIdentifiers =
        roleAssignments.stream().map(RoleAssignment::getRoleIdentifier).distinct().collect(toList());
    RoleFilter roleFilter = RoleFilter.builder()
                                .identifierFilter(new HashSet<>(roleIdentifiers))
                                .scopeIdentifier(scope.toString())
                                .managedFilter(NO_FILTER)
                                .build();
    List<RoleResponseDTO> roleResponseDTOs = roleService.list(pageRequest, roleFilter, true)
                                                 .getContent()
                                                 .stream()
                                                 .map(roleDTOMapper::toResponseDTO)
                                                 .collect(toList());
    List<String> resourceGroupIdentifiers =
        roleAssignments.stream().map(RoleAssignment::getResourceGroupIdentifier).distinct().collect(toList());
    List<ResourceGroupDTO> resourceGroupDTOs =
        resourceGroupService.list(resourceGroupIdentifiers, scope.toString(), NO_FILTER)
            .stream()
            .map(ResourceGroupDTOMapper::toDTO)
            .collect(toList());

    List<RoleAssignmentDTO> roleAssignmentDTOs =
        roleAssignments.stream().map(RoleAssignmentDTOMapper::toDTO).collect(toList());
    return ResponseDTO.newResponse(
        RoleAssignmentAggregateResponseDTOMapper.toDTO(roleAssignmentDTOs, scope, roleResponseDTOs, resourceGroupDTOs));
  }

  @Override
  public ResponseDTO<RoleAssignmentResponseDTO> create(
      HarnessScopeParams harnessScopeParams, RoleAssignmentDTO roleAssignmentDTO) {
    Scope scope = fromParams(harnessScopeParams);
    roleAssignmentApiUtils.validateDeprecatedResourceGroupNotUsed(
        roleAssignmentDTO.getResourceGroupIdentifier(), scope.getLevel().toString());
    roleAssignmentApiUtils.validatePrincipalScopeLevelConditions(roleAssignmentDTO.getPrincipal(), scope.getLevel());
    RoleAssignment roleAssignment =
        roleAssignmentApiUtils.buildRoleAssignmentWithPrincipalScopeLevel(fromDTO(scope, roleAssignmentDTO), scope);
    roleAssignmentApiUtils.syncDependencies(roleAssignment, scope);
    roleAssignmentApiUtils.checkUpdatePermission(harnessScopeParams, roleAssignment);
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      RoleAssignment createdRoleAssignment = roleAssignmentService.create(roleAssignment);
      RoleAssignmentResponseDTO response = roleAssignmentDTOMapper.toResponseDTO(createdRoleAssignment);
      outboxService.save(new RoleAssignmentCreateEvent(
          response.getScope().getAccountIdentifier(), response.getRoleAssignment(), response.getScope()));
      return ResponseDTO.newResponse(response);
    }));
  }

  @Override
  public ResponseDTO<RoleAssignmentResponseDTO> update(
      String identifier, HarnessScopeParams harnessScopeParams, RoleAssignmentDTO roleAssignmentDTO) {
    Scope scope = fromParams(harnessScopeParams);
    if (!identifier.equals(roleAssignmentDTO.getIdentifier())) {
      throw new InvalidRequestException("Role assignment identifier in the request body and the url do not match.");
    }
    roleAssignmentApiUtils.validateDeprecatedResourceGroupNotUsed(
        roleAssignmentDTO.getResourceGroupIdentifier(), scope.getLevel().toString());
    roleAssignmentApiUtils.validatePrincipalScopeLevelConditions(roleAssignmentDTO.getPrincipal(), scope.getLevel());
    RoleAssignment roleAssignmentUpdate =
        roleAssignmentApiUtils.buildRoleAssignmentWithPrincipalScopeLevel(fromDTO(scope, roleAssignmentDTO), scope);
    roleAssignmentApiUtils.checkUpdatePermission(harnessScopeParams, roleAssignmentUpdate);
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      RoleAssignmentUpdateResult roleAssignmentUpdateResult = roleAssignmentService.update(roleAssignmentUpdate);
      RoleAssignmentResponseDTO response =
          roleAssignmentDTOMapper.toResponseDTO(roleAssignmentUpdateResult.getUpdatedRoleAssignment());
      outboxService.save(
          new RoleAssignmentUpdateEvent(response.getScope().getAccountIdentifier(), response.getRoleAssignment(),
              roleAssignmentDTOMapper.toResponseDTO(roleAssignmentUpdateResult.getOriginalRoleAssignment())
                  .getRoleAssignment(),
              response.getScope()));
      return ResponseDTO.newResponse(response);
    }));
  }

  @Override
  public ResponseDTO<List<RoleAssignmentResponseDTO>> create(
      HarnessScopeParams harnessScopeParams, RoleAssignmentCreateRequestDTO roleAssignmentCreateRequestDTO) {
    Scope scope = fromParams(harnessScopeParams);
    if (roleAssignmentCreateRequestDTO == null) {
      throw new InvalidRequestException("Request body is empty");
    }
    if (isEmpty(roleAssignmentCreateRequestDTO.getRoleAssignments())) {
      return ResponseDTO.newResponse(new ArrayList<>());
    }
    roleAssignmentCreateRequestDTO.getRoleAssignments().forEach(roleAssignmentDTO -> {
      roleAssignmentApiUtils.validateDeprecatedResourceGroupNotUsed(
          roleAssignmentDTO.getResourceGroupIdentifier(), scope.getLevel().toString());
      roleAssignmentApiUtils.validatePrincipalScopeLevelConditions(roleAssignmentDTO.getPrincipal(), scope.getLevel());
    });
    return ResponseDTO.newResponse(createRoleAssignments(harnessScopeParams, roleAssignmentCreateRequestDTO, false));
  }

  @Override
  @InternalApi
  public ResponseDTO<List<RoleAssignmentResponseDTO>> create(HarnessScopeParams harnessScopeParams,
      RoleAssignmentCreateRequestDTO roleAssignmentCreateRequestDTO, Boolean managed) {
    // TODO: remove this deprecated resource group handling
    if (roleAssignmentCreateRequestDTO == null) {
      throw new InvalidRequestException("Request body is empty");
    }
    if (isEmpty(roleAssignmentCreateRequestDTO.getRoleAssignments())) {
      return ResponseDTO.newResponse(new ArrayList<>());
    }
    List<RoleAssignmentDTO> roleAssignmentDTOs = new ArrayList<>();
    roleAssignmentCreateRequestDTO.getRoleAssignments().forEach(roleAssignmentDTO -> {
      if (HarnessResourceGroupConstants.DEPRECATED_ALL_RESOURCES_RESOURCE_GROUP_IDENTIFIER.equals(
              roleAssignmentDTO.getResourceGroupIdentifier())) {
        roleAssignmentDTOs.add(RoleAssignmentDTO.builder()
                                   .disabled(roleAssignmentDTO.isDisabled())
                                   .identifier(roleAssignmentDTO.getIdentifier())
                                   .managed(roleAssignmentDTO.isManaged())
                                   .internal(roleAssignmentDTO.isInternal())
                                   .principal(roleAssignmentDTO.getPrincipal())
                                   .roleIdentifier(roleAssignmentDTO.getRoleIdentifier())
                                   .resourceGroupIdentifier(getDefaultResourceGroupIdentifier(harnessScopeParams))
                                   .build());
      } else {
        roleAssignmentDTOs.add(roleAssignmentDTO);
      }
    });
    roleAssignmentCreateRequestDTO =
        RoleAssignmentCreateRequestDTO.builder().roleAssignments(roleAssignmentDTOs).build();
    return ResponseDTO.newResponse(createRoleAssignments(harnessScopeParams, roleAssignmentCreateRequestDTO, managed));
  }

  private String getDefaultResourceGroupIdentifier(HarnessScopeParams harnessScopeParams) {
    if (isNotEmpty(harnessScopeParams.getProjectIdentifier())) {
      return DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    } else if (isNotEmpty(harnessScopeParams.getOrgIdentifier())) {
      return DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    } else {
      return DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    }
  }

  @Override
  public ResponseDTO<RoleAssignmentValidationResponseDTO> validate(
      HarnessScopeParams harnessScopeParams, RoleAssignmentValidationRequestDTO validationRequest) {
    Scope scope = fromParams(harnessScopeParams);
    harnessResourceGroupService.sync(validationRequest.getRoleAssignment().getResourceGroupIdentifier(), scope);
    return ResponseDTO.newResponse(toDTO(roleAssignmentService.validate(fromDTO(scope, validationRequest))));
  }

  @Override
  public ResponseDTO<RoleAssignmentResponseDTO> delete(HarnessScopeParams harnessScopeParams, String identifier) {
    String scopeIdentifier = fromParams(harnessScopeParams).toString();
    RoleAssignment roleAssignment =
        roleAssignmentService.get(identifier, scopeIdentifier).<InvalidRequestException>orElseThrow(() -> {
          throw new InvalidRequestException("Invalid Role Assignment");
        });
    roleAssignmentApiUtils.checkUpdatePermission(harnessScopeParams, roleAssignment);
    ValidationResult validationResult = actionValidator.canDelete(roleAssignment);
    if (!validationResult.isValid()) {
      throw new InvalidRequestException(validationResult.getErrorMessage());
    }
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      RoleAssignment deletedRoleAssignment =
          roleAssignmentService.delete(identifier, scopeIdentifier).<NotFoundException>orElseThrow(() -> {
            throw new NotFoundException("Role Assignment is already deleted");
          });
      RoleAssignmentResponseDTO response = roleAssignmentDTOMapper.toResponseDTO(deletedRoleAssignment);
      outboxService.save(new RoleAssignmentDeleteEvent(
          response.getScope().getAccountIdentifier(), response.getRoleAssignment(), response.getScope()));
      return ResponseDTO.newResponse(response);
    }));
  }

  @Override
  public ResponseDTO<RoleAssignmentResponseDTO> get(HarnessScopeParams harnessScopeParams, String identifier) {
    Scope scope = fromParams(harnessScopeParams);
    RoleAssignment roleAssignment =
        roleAssignmentService.get(identifier, scope.toString()).<NotFoundException>orElseThrow(() -> {
          throw new NotFoundException("Role Assignment with given identifier doesn't exists");
        });
    if (!roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, roleAssignment.getPrincipalType())) {
      throw new UnauthorizedException(
          String.format("Current principal is not authorized to the view the role assignments for Principal Type %s",
              roleAssignment.getPrincipalType().name()),
          USER_NOT_AUTHORIZED, WingsException.USER);
    }
    RoleAssignmentResponseDTO response = roleAssignmentDTOMapper.toResponseDTO(roleAssignment);
    return ResponseDTO.newResponse(response);
  }

  private List<RoleAssignmentResponseDTO> createRoleAssignments(
      HarnessScopeParams harnessScopeParams, RoleAssignmentCreateRequestDTO requestDTO, boolean managed) {
    Scope scope = fromParams(harnessScopeParams);
    List<RoleAssignment> roleAssignmentsPayload =
        requestDTO.getRoleAssignments()
            .stream()
            .map(roleAssignmentDTO
                -> roleAssignmentApiUtils.buildRoleAssignmentWithPrincipalScopeLevel(
                    fromDTO(scope, roleAssignmentDTO, managed), scope))
            .collect(Collectors.toList());

    requestDTO.getRoleAssignments().forEach(
        roleAssignmentDTO -> checkAndAddManagedRoleAssignmentForUserGroup(harnessScopeParams, roleAssignmentDTO));

    List<RoleAssignmentResponseDTO> createdRoleAssignments = new ArrayList<>();
    for (RoleAssignment roleAssignment : roleAssignmentsPayload) {
      try {
        roleAssignmentApiUtils.syncDependencies(roleAssignment, scope);
        roleAssignmentApiUtils.checkUpdatePermission(harnessScopeParams, roleAssignment);
        RoleAssignmentResponseDTO roleAssignmentResponseDTO =
            Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
              RoleAssignmentResponseDTO response =
                  roleAssignmentDTOMapper.toResponseDTO(roleAssignmentService.create(roleAssignment));
              outboxService.save(new RoleAssignmentCreateEvent(
                  response.getScope().getAccountIdentifier(), response.getRoleAssignment(), response.getScope()));
              return response;
            }));
        createdRoleAssignments.add(roleAssignmentResponseDTO);
      } catch (Exception e) {
        log.error(String.format("Could not create role assignment %s", roleAssignment), e);
      }
    }
    return createdRoleAssignments;
  }

  private void checkAndAddManagedRoleAssignmentForUserGroup(
      HarnessScopeParams harnessScopeParams, RoleAssignmentDTO roleAssignmentDTO) {
    Scope scope = fromParams(harnessScopeParams);
    if (USER_GROUP.equals(roleAssignmentDTO.getPrincipal().getType())
        && HarnessScopeLevel.ACCOUNT.getName().equalsIgnoreCase(roleAssignmentDTO.getPrincipal().getScopeLevel())
        && HarnessScopeLevel.PROJECT.getName().equalsIgnoreCase(scope.getLevel().toString())) {
      try {
        create(toParentScopeParams(harnessScopeParams, HarnessScopeLevel.ORGANIZATION.getName()),
            RoleAssignmentDTO.builder()
                .resourceGroupIdentifier(DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER)
                .principal(roleAssignmentDTO.getPrincipal())
                .roleIdentifier(ORGANIZATION_VIEWER_ROLE)
                .disabled(false)
                .managed(false)
                .build());
      } catch (DuplicateFieldException e) {
        /**
         *  It's expected that usergroup might already have this roleassignment.
         */
      }
    }
  }

  private Optional<RoleAssignmentFilter> buildRoleAssignmentFilterWithPermissionFilter(
      HarnessScopeParams harnessScopeParams, RoleAssignmentFilterDTO roleAssignmentFilterDTO) {
    boolean hasAccessToUserRoleAssignments = roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, USER);
    boolean hasAccessToUserGroupRoleAssignments =
        roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, USER_GROUP);
    boolean hasAccessToServiceAccountRoleAssignments =
        roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, SERVICE_ACCOUNT);
    Scope scope = fromParams(harnessScopeParams);
    RoleAssignmentFilter roleAssignmentFilter = fromDTO(scope.toString(), roleAssignmentFilterDTO);
    if (isNotEmpty(roleAssignmentFilter.getPrincipalFilter())) {
      Set<Principal> principals = roleAssignmentFilter.getPrincipalFilter();
      if (!hasAccessToUserGroupRoleAssignments) {
        principals = principals.stream()
                         .filter(principal -> !USER_GROUP.equals(principal.getPrincipalType()))
                         .collect(Collectors.toSet());
      }
      if (!hasAccessToUserRoleAssignments) {
        principals = principals.stream()
                         .filter(principal -> !USER.equals(principal.getPrincipalType()))
                         .collect(Collectors.toSet());
      }
      if (!hasAccessToServiceAccountRoleAssignments) {
        principals = principals.stream()
                         .filter(principal -> !SERVICE_ACCOUNT.equals(principal.getPrincipalType()))
                         .collect(Collectors.toSet());
      }
      if (isEmpty(principals)) {
        return Optional.empty();
      }
      roleAssignmentFilter.setPrincipalFilter(principals);
    } else if (isNotEmpty(roleAssignmentFilter.getPrincipalTypeFilter())) {
      if (!hasAccessToUserGroupRoleAssignments) {
        roleAssignmentFilter.getPrincipalTypeFilter().remove(USER_GROUP);
      }
      if (!hasAccessToUserRoleAssignments) {
        roleAssignmentFilter.getPrincipalTypeFilter().remove(USER);
      }
      if (!hasAccessToServiceAccountRoleAssignments) {
        roleAssignmentFilter.getPrincipalTypeFilter().remove(SERVICE_ACCOUNT);
      }
      if (isEmpty(roleAssignmentFilter.getPrincipalTypeFilter())) {
        return Optional.empty();
      }
    } else {
      Set<PrincipalType> principalTypes = Sets.newHashSet();
      if (roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, USER)) {
        principalTypes.add(USER);
      }

      if (roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, USER_GROUP)) {
        principalTypes.add(USER_GROUP);
      }

      if (roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, SERVICE_ACCOUNT)) {
        principalTypes.add(SERVICE_ACCOUNT);
      }

      if (principalTypes.isEmpty()) {
        return Optional.empty();
      } else {
        roleAssignmentFilter.setPrincipalTypeFilter(principalTypes);
      }
    }
    return Optional.of(roleAssignmentFilter);
  }
}
