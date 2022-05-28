/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO.MODEL_NAME;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTOMapper.fromDTO;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTOMapper.fromDTOIncludingChildScopes;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTOMapper.toDTO;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromParams;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.toParams;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.toParentScopeParams;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.USER_NOT_AUTHORIZED;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.TransactionUtils.DEFAULT_TRANSACTION_RETRY_POLICY;

import static java.util.stream.Collectors.toList;
import static lombok.AccessLevel.PRIVATE;

import io.harness.accesscontrol.AccessControlPermissions;
import io.harness.accesscontrol.AccessControlResourceTypes;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.common.validation.ValidationResult;
import io.harness.accesscontrol.commons.validation.HarnessActionValidator;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.serviceaccounts.HarnessServiceAccountService;
import io.harness.accesscontrol.principals.serviceaccounts.ServiceAccountService;
import io.harness.accesscontrol.principals.usergroups.HarnessUserGroupService;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.principals.users.HarnessUserService;
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
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.HarnessScopeService;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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
import javax.validation.constraints.NotNull;
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
  HarnessUserGroupService harnessUserGroupService;
  HarnessUserService harnessUserService;
  HarnessServiceAccountService harnessServiceAccountService;
  HarnessScopeService harnessScopeService;
  ScopeService scopeService;
  RoleService roleService;
  ResourceGroupService resourceGroupService;
  UserGroupService userGroupService;
  UserService userService;
  ServiceAccountService serviceAccountService;
  RoleAssignmentDTOMapper roleAssignmentDTOMapper;
  RoleDTOMapper roleDTOMapper;
  TransactionTemplate transactionTemplate;
  HarnessActionValidator<RoleAssignment> actionValidator;
  OutboxService outboxService;
  AccessControlClient accessControlClient;

  RetryPolicy<Object> transactionRetryPolicy = DEFAULT_TRANSACTION_RETRY_POLICY;

  @Inject
  public RoleAssignmentResourceImpl(RoleAssignmentService roleAssignmentService,
      HarnessResourceGroupService harnessResourceGroupService, HarnessUserGroupService harnessUserGroupService,
      HarnessUserService harnessUserService, HarnessServiceAccountService harnessServiceAccountService,
      HarnessScopeService harnessScopeService, ScopeService scopeService, RoleService roleService,
      ResourceGroupService resourceGroupService, UserGroupService userGroupService, UserService userService,
      ServiceAccountService serviceAccountService, RoleAssignmentDTOMapper roleAssignmentDTOMapper,
      RoleDTOMapper roleDTOMapper, @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate,
      @Named(MODEL_NAME) HarnessActionValidator<RoleAssignment> actionValidator, OutboxService outboxService,
      AccessControlClient accessControlClient) {
    this.roleAssignmentService = roleAssignmentService;
    this.harnessResourceGroupService = harnessResourceGroupService;
    this.harnessUserGroupService = harnessUserGroupService;
    this.harnessUserService = harnessUserService;
    this.harnessServiceAccountService = harnessServiceAccountService;
    this.harnessScopeService = harnessScopeService;
    this.scopeService = scopeService;
    this.roleService = roleService;
    this.resourceGroupService = resourceGroupService;
    this.userGroupService = userGroupService;
    this.userService = userService;
    this.serviceAccountService = serviceAccountService;
    this.roleAssignmentDTOMapper = roleAssignmentDTOMapper;
    this.roleDTOMapper = roleDTOMapper;
    this.transactionTemplate = transactionTemplate;
    this.actionValidator = actionValidator;
    this.outboxService = outboxService;
    this.accessControlClient = accessControlClient;
  }

  @Override
  public ResponseDTO<PageResponse<RoleAssignmentResponseDTO>> get(
      PageRequest pageRequest, HarnessScopeParams harnessScopeParams) {
    String scopeIdentifier = fromParams(harnessScopeParams).toString();
    RoleAssignmentFilterBuilder roleAssignmentFilterBuilder =
        RoleAssignmentFilter.builder().scopeFilter(scopeIdentifier);
    Set<PrincipalType> principalTypes = Sets.newHashSet();

    if (checkViewPermission(harnessScopeParams, USER)) {
      principalTypes.add(USER);
    }

    if (checkViewPermission(harnessScopeParams, USER_GROUP)) {
      principalTypes.add(USER_GROUP);
    }

    if (checkViewPermission(harnessScopeParams, SERVICE_ACCOUNT)) {
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
  public ResponseDTO<List<RoleAssignmentResponseDTO>> getAllIncludingChildScopes(
      HarnessScopeParams harnessScopeParams, RoleAssignmentFilterDTO roleAssignmentFilterDTO) {
    Scope scope = fromParams(harnessScopeParams);
    RoleAssignmentFilter roleAssignmentFilter = fromDTOIncludingChildScopes(scope.toString(), roleAssignmentFilterDTO);

    PageRequest pageRequest = PageRequest.builder().pageSize(1000).build();
    List<RoleAssignment> roleAssignments = roleAssignmentService.list(pageRequest, roleAssignmentFilter).getContent();
    return ResponseDTO.newResponse(roleAssignments.stream()
                                       .filter(roleAssignment
                                           -> checkViewPermission(toParams(scopeService.buildScopeFromScopeIdentifier(
                                                                      roleAssignment.getScopeIdentifier())),
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
    List<RoleResponseDTO> roleResponseDTOs = roleService.list(pageRequest, roleFilter)
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
    validateDeprecatedResourceGroupNotUsed(roleAssignmentDTO.getResourceGroupIdentifier(), scope.getLevel().toString());
    validatePrincipalScopeLevelConditions(roleAssignmentDTO.getPrincipal(), scope.getLevel());
    RoleAssignment roleAssignment =
        buildRoleAssignmentWithPrincipalScopeLevel(fromDTO(scope, roleAssignmentDTO), scope);
    syncDependencies(roleAssignment, scope);
    checkUpdatePermission(harnessScopeParams, roleAssignment);
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      RoleAssignment createdRoleAssignment = roleAssignmentService.create(roleAssignment);
      RoleAssignmentResponseDTO response = roleAssignmentDTOMapper.toResponseDTO(createdRoleAssignment);
      outboxService.save(new RoleAssignmentCreateEvent(
          response.getScope().getAccountIdentifier(), response.getRoleAssignment(), response.getScope()));
      return ResponseDTO.newResponse(response);
    }));
  }

  private RoleAssignment buildRoleAssignmentWithPrincipalScopeLevel(RoleAssignment roleAssignment, Scope scope) {
    String principalScopeLevel = null;
    if (USER_GROUP.equals(roleAssignment.getPrincipalType()) && !isEmpty(roleAssignment.getPrincipalScopeLevel())) {
      principalScopeLevel = roleAssignment.getPrincipalScopeLevel();
    }
    if (USER_GROUP.equals(roleAssignment.getPrincipalType()) && isEmpty(roleAssignment.getPrincipalScopeLevel())) {
      principalScopeLevel = roleAssignment.getScopeLevel();
    }
    if (SERVICE_ACCOUNT.equals(roleAssignment.getPrincipalType()) && isEmpty(roleAssignment.getPrincipalScopeLevel())) {
      principalScopeLevel = getServiceAccountScopeLevel(roleAssignment.getPrincipalIdentifier(), scope);
    }
    return RoleAssignment.builder()
        .identifier(roleAssignment.getIdentifier())
        .scopeIdentifier(roleAssignment.getScopeIdentifier())
        .scopeLevel(roleAssignment.getScopeLevel())
        .resourceGroupIdentifier(roleAssignment.getResourceGroupIdentifier())
        .roleIdentifier(roleAssignment.getRoleIdentifier())
        .principalScopeLevel(principalScopeLevel)
        .principalIdentifier(roleAssignment.getPrincipalIdentifier())
        .principalType(roleAssignment.getPrincipalType())
        .managed(roleAssignment.isManaged())
        .disabled(roleAssignment.isDisabled())
        .createdAt(roleAssignment.getCreatedAt())
        .lastModifiedAt(roleAssignment.getLastModifiedAt())
        .build();
  }

  private String getServiceAccountScopeLevel(@NotNull String serviceAccountIdentifier, @NotNull Scope scope) {
    HarnessScopeParams scopeParams = ScopeMapper.toParams(scope);
    Scope serviceAccountScope = scope;
    while (serviceAccountScope != null) {
      harnessServiceAccountService.sync(serviceAccountIdentifier, scope);
      serviceAccountScope = serviceAccountScope.getParentScope();
    }

    Scope accountScope = ScopeMapper.fromParams(
        HarnessScopeParams.builder().accountIdentifier(scopeParams.getAccountIdentifier()).build());
    if (serviceAccountService.get(serviceAccountIdentifier, accountScope.toString()).isPresent()) {
      return accountScope.getLevel().toString();
    }
    Scope orgScope = ScopeMapper.fromParams(HarnessScopeParams.builder()
                                                .accountIdentifier(scopeParams.getAccountIdentifier())
                                                .orgIdentifier(scopeParams.getOrgIdentifier())
                                                .build());
    if (serviceAccountService.get(serviceAccountIdentifier, orgScope.toString()).isPresent()) {
      return orgScope.getLevel().toString();
    }
    return scope.getLevel().toString();
  }

  private static void validateDeprecatedResourceGroupNotUsed(String resourceGroupIdentifier, String scopeLevel) {
    if (HarnessResourceGroupConstants.DEPRECATED_ALL_RESOURCES_RESOURCE_GROUP_IDENTIFIER.equals(
            resourceGroupIdentifier)) {
      throw new InvalidRequestException(String.format("%s is deprecated, please use %s.",
          HarnessResourceGroupConstants.DEPRECATED_ALL_RESOURCES_RESOURCE_GROUP_IDENTIFIER,
          HarnessResourceGroupConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER));
    }
  }

  private void validatePrincipalScopeLevelConditions(PrincipalDTO principalDTO, ScopeLevel scopeLevel) {
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

  @Override
  public ResponseDTO<RoleAssignmentResponseDTO> update(
      String identifier, HarnessScopeParams harnessScopeParams, RoleAssignmentDTO roleAssignmentDTO) {
    Scope scope = fromParams(harnessScopeParams);
    if (!identifier.equals(roleAssignmentDTO.getIdentifier())) {
      throw new InvalidRequestException("Role assignment identifier in the request body and the url do not match.");
    }
    validateDeprecatedResourceGroupNotUsed(roleAssignmentDTO.getResourceGroupIdentifier(), scope.getLevel().toString());
    validatePrincipalScopeLevelConditions(roleAssignmentDTO.getPrincipal(), scope.getLevel());
    RoleAssignment roleAssignmentUpdate =
        buildRoleAssignmentWithPrincipalScopeLevel(fromDTO(scope, roleAssignmentDTO), scope);
    checkUpdatePermission(harnessScopeParams, roleAssignmentUpdate);
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
      validateDeprecatedResourceGroupNotUsed(
          roleAssignmentDTO.getResourceGroupIdentifier(), scope.getLevel().toString());
      validatePrincipalScopeLevelConditions(roleAssignmentDTO.getPrincipal(), scope.getLevel());
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
    checkUpdatePermission(harnessScopeParams, roleAssignment);
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

  private List<RoleAssignmentResponseDTO> createRoleAssignments(
      HarnessScopeParams harnessScopeParams, RoleAssignmentCreateRequestDTO requestDTO, boolean managed) {
    Scope scope = fromParams(harnessScopeParams);
    List<RoleAssignment> roleAssignmentsPayload =
        requestDTO.getRoleAssignments()
            .stream()
            .map(roleAssignmentDTO
                -> buildRoleAssignmentWithPrincipalScopeLevel(fromDTO(scope, roleAssignmentDTO, managed), scope))
            .collect(Collectors.toList());
    List<RoleAssignmentResponseDTO> createdRoleAssignments = new ArrayList<>();
    for (RoleAssignment roleAssignment : roleAssignmentsPayload) {
      try {
        syncDependencies(roleAssignment, scope);
        checkUpdatePermission(harnessScopeParams, roleAssignment);
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

  private void checkUpdatePermission(HarnessScopeParams harnessScopeParams, RoleAssignment roleAssignment) {
    int scopeRank = ScopeMapper.fromParams(harnessScopeParams).getLevel().getRank();
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

  private boolean checkViewPermission(HarnessScopeParams harnessScopeParams, PrincipalType principalType) {
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

  private Optional<RoleAssignmentFilter> buildRoleAssignmentFilterWithPermissionFilter(
      HarnessScopeParams harnessScopeParams, RoleAssignmentFilterDTO roleAssignmentFilterDTO) {
    boolean hasAccessToUserRoleAssignments = checkViewPermission(harnessScopeParams, USER);
    boolean hasAccessToUserGroupRoleAssignments = checkViewPermission(harnessScopeParams, USER_GROUP);
    boolean hasAccessToServiceAccountRoleAssignments = checkViewPermission(harnessScopeParams, SERVICE_ACCOUNT);
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
      if (checkViewPermission(harnessScopeParams, USER)) {
        principalTypes.add(USER);
      }

      if (checkViewPermission(harnessScopeParams, USER_GROUP)) {
        principalTypes.add(USER_GROUP);
      }

      if (checkViewPermission(harnessScopeParams, SERVICE_ACCOUNT)) {
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

  private void syncDependencies(RoleAssignment roleAssignment, Scope scope) {
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
}
