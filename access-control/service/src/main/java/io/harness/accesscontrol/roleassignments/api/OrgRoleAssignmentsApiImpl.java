/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentApiUtils.ROLE_ASSIGNMENT_DOES_NOT_EXISTS;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO.MODEL_NAME;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTOMapper.fromDTO;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromParams;
import static io.harness.eraro.ErrorCode.USER_NOT_AUTHORIZED;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static lombok.AccessLevel.PRIVATE;

import io.harness.accesscontrol.common.validation.ValidationResult;
import io.harness.accesscontrol.commons.validation.HarnessActionValidator;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter.RoleAssignmentFilterBuilder;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentCreateEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentDeleteEvent;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.accesscontrol.v1.OrgRoleAssignmentsApi;
import io.harness.spec.server.accesscontrol.v1.model.RoleAssignment;
import io.harness.spec.server.accesscontrol.v1.model.RoleAssignmentResponse;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
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
public class OrgRoleAssignmentsApiImpl implements OrgRoleAssignmentsApi {
  RoleAssignmentApiUtils roleAssignmentApiUtils;
  RoleAssignmentService roleAssignmentService;
  RoleAssignmentDTOMapper roleAssignmentDTOMapper;
  TransactionTemplate transactionTemplate;
  OutboxService outboxService;
  HarnessActionValidator<io.harness.accesscontrol.roleassignments.RoleAssignment> actionValidator;

  RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Inject
  public OrgRoleAssignmentsApiImpl(RoleAssignmentApiUtils roleAssignmentApiUtils,
      RoleAssignmentService roleAssignmentService, RoleAssignmentDTOMapper roleAssignmentDTOMapper,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService,
      @Named(
          MODEL_NAME) HarnessActionValidator<io.harness.accesscontrol.roleassignments.RoleAssignment> actionValidator) {
    this.roleAssignmentApiUtils = roleAssignmentApiUtils;
    this.roleAssignmentService = roleAssignmentService;
    this.roleAssignmentDTOMapper = roleAssignmentDTOMapper;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.actionValidator = actionValidator;
  }

  @Override
  public Response createOrgScopedRoleAssignments(RoleAssignment request, String org, String harnessAccount) {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(harnessAccount).orgIdentifier(org).build();
    Scope scope = fromParams(harnessScopeParams);

    roleAssignmentApiUtils.validateDeprecatedResourceGroupNotUsed(
        request.getResourceGroup(), scope.getLevel().toString());
    PrincipalDTO principal = roleAssignmentApiUtils.getPrincipalDto(request.getPrincipal());

    roleAssignmentApiUtils.validatePrincipalScopeLevelConditions(principal, scope.getLevel());

    RoleAssignmentDTO roleAssignmentDTO = roleAssignmentApiUtils.getRoleAssignmentDto(request);
    io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignment =
        roleAssignmentApiUtils.buildRoleAssignmentWithPrincipalScopeLevel(fromDTO(scope, roleAssignmentDTO), scope);
    roleAssignmentApiUtils.syncDependencies(roleAssignment, scope);
    roleAssignmentApiUtils.checkUpdatePermission(harnessScopeParams, roleAssignment);

    RoleAssignmentResponseDTO responseDTO =
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          io.harness.accesscontrol.roleassignments.RoleAssignment createdRoleAssignment =
              roleAssignmentService.create(roleAssignment);
          RoleAssignmentResponseDTO response = roleAssignmentDTOMapper.toResponseDTO(createdRoleAssignment);
          outboxService.save(new RoleAssignmentCreateEvent(
              response.getScope().getAccountIdentifier(), response.getRoleAssignment(), response.getScope()));
          return response;
        }));

    RoleAssignmentResponse response = roleAssignmentApiUtils.getRoleAssignmentResponse(responseDTO);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  @Override
  public Response deleteOrgScopedRoleAssignment(String roleassignment, String org, String harnessAccount) {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(harnessAccount).orgIdentifier(org).build();
    String scopeIdentifier = fromParams(harnessScopeParams).toString();

    io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignment =
        roleAssignmentService.get(roleassignment, scopeIdentifier).<NotFoundException>orElseThrow(() -> {
          throw new NotFoundException(ROLE_ASSIGNMENT_DOES_NOT_EXISTS);
        });
    roleAssignmentApiUtils.checkUpdatePermission(harnessScopeParams, roleAssignment);
    ValidationResult validationResult = actionValidator.canDelete(roleAssignment);
    if (!validationResult.isValid()) {
      throw new InvalidRequestException(validationResult.getErrorMessage());
    }
    RoleAssignmentResponseDTO roleAssignmentResponse =
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          io.harness.accesscontrol.roleassignments.RoleAssignment deletedRoleAssignment =
              roleAssignmentService.delete(roleassignment, scopeIdentifier).<NotFoundException>orElseThrow(() -> {
                throw new NotFoundException("Role Assignment is already deleted");
              });
          RoleAssignmentResponseDTO response = roleAssignmentDTOMapper.toResponseDTO(deletedRoleAssignment);
          outboxService.save(new RoleAssignmentDeleteEvent(
              response.getScope().getAccountIdentifier(), response.getRoleAssignment(), response.getScope()));
          return response;
        }));

    RoleAssignmentResponse response = roleAssignmentApiUtils.getRoleAssignmentResponse(roleAssignmentResponse);
    return Response.ok().entity(response).build();
  }

  @Override
  public Response getOrgScopedRoleAssignment(String roleassignment, String org, String harnessAccount) {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(harnessAccount).orgIdentifier(org).build();
    Scope scope = fromParams(harnessScopeParams);
    io.harness.accesscontrol.roleassignments.RoleAssignment roleAssignment =
        roleAssignmentService.get(roleassignment, scope.toString()).<NotFoundException>orElseThrow(() -> {
          throw new NotFoundException(ROLE_ASSIGNMENT_DOES_NOT_EXISTS);
        });
    if (!roleAssignmentApiUtils.checkViewPermission(harnessScopeParams, roleAssignment.getPrincipalType())) {
      throw new UnauthorizedException(
          String.format("Current principal is not authorized to the view the role assignments for Principal Type %s",
              roleAssignment.getPrincipalType().name()),
          USER_NOT_AUTHORIZED, WingsException.USER);
    }
    RoleAssignmentResponseDTO roleAssignmentResponseDto = roleAssignmentDTOMapper.toResponseDTO(roleAssignment);
    RoleAssignmentResponse roleAssignmentResponse =
        roleAssignmentApiUtils.getRoleAssignmentResponse(roleAssignmentResponseDto);

    return Response.ok().entity(roleAssignmentResponse).build();
  }

  @Override
  public Response getOrgScopedRoleAssignments(
      String org, String harnessAccount, Integer page, Integer limit, String sort, String order) {
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(harnessAccount).orgIdentifier(org).build();
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

    PageRequest pageRequest = roleAssignmentApiUtils.getPageRequest(page, limit, sort, order);
    PageResponse<io.harness.accesscontrol.roleassignments.RoleAssignment> pageResponse = roleAssignmentService.list(
        pageRequest, roleAssignmentFilterBuilder.principalTypeFilter(principalTypes).build());

    List<io.harness.accesscontrol.roleassignments.RoleAssignment> content = pageResponse.getContent();
    List<RoleAssignmentResponseDTO> roleAssignmentResponseDTOS =
        content.stream()
            .map(roleAssignment -> roleAssignmentDTOMapper.toResponseDTO(roleAssignment))
            .collect(Collectors.toList());

    List<RoleAssignmentResponse> roleAssignmentResponses =
        roleAssignmentApiUtils.getRoleAssignmentResponses(roleAssignmentResponseDTOS);

    ResponseBuilder responseBuilder = Response.ok();

    ResponseBuilder responseBuilderWithLinks = roleAssignmentApiUtils.addLinksHeader(responseBuilder,
        String.format("/v1/orgs/%s/roleassignments", org), roleAssignmentResponses.size(), page, limit);

    return responseBuilderWithLinks.entity(roleAssignmentResponses).build();
  }
}
