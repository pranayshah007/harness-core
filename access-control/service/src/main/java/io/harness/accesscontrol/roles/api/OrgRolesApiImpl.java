/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.api;

import static io.harness.accesscontrol.AccessControlPermissions.DELETE_ROLE_PERMISSION;
import static io.harness.accesscontrol.AccessControlPermissions.EDIT_ROLE_PERMISSION;
import static io.harness.accesscontrol.AccessControlPermissions.VIEW_ROLE_PERMISSION;
import static io.harness.accesscontrol.AccessControlResourceTypes.ROLE;
import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.accesscontrol.roles.api.RoleDTO.ScopeLevel.fromString;
import static io.harness.accesscontrol.roles.api.RoleDTOMapper.fromDTO;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.roles.RoleUpdateResult;
import io.harness.accesscontrol.roles.events.RoleCreateEvent;
import io.harness.accesscontrol.roles.events.RoleDeleteEvent;
import io.harness.accesscontrol.roles.events.RoleUpdateEvent;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.accesscontrol.v1.OrganizationRolesApi;
import io.harness.spec.server.accesscontrol.v1.model.CreateRoleRequest;
import io.harness.spec.server.accesscontrol.v1.model.RolesResponse;
import io.harness.utils.ApiUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class OrgRolesApiImpl implements OrganizationRolesApi {
  private final RoleService roleService;
  private final ScopeService scopeService;
  private final RoleDTOMapper roleDTOMapper;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;
  private final AccessControlClient accessControlClient;
  private final RolesApiUtils rolesApiUtils;

  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @Inject
  public OrgRolesApiImpl(RoleService roleService, ScopeService scopeService, RoleDTOMapper roleDTOMapper,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService,
      AccessControlClient accessControlClient, RolesApiUtils rolesApiUtils) {
    this.roleService = roleService;
    this.scopeService = scopeService;
    this.roleDTOMapper = roleDTOMapper;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.accessControlClient = accessControlClient;
    this.rolesApiUtils = rolesApiUtils;
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.CUSTOM_ROLES)
  public Response createRoleOrg(CreateRoleRequest body, String org, @AccountIdentifier String account) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, null), Resource.of(ROLE, null), EDIT_ROLE_PERMISSION);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    Scope scope = scopeService.getOrCreate(ScopeMapper.fromParams(harnessScopeParams));
    RoleDTO roleDTO = rolesApiUtils.getRoleOrgDTO(body);
    roleDTO.setAllowedScopeLevels(Sets.newHashSet(fromString(scope.getLevel().toString())));

    RolesResponse response = Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      RoleResponseDTO responseDTO = roleDTOMapper.toResponseDTO(roleService.create(fromDTO(scope.toString(), roleDTO)));
      outboxService.save(new RoleCreateEvent(
          responseDTO.getScope().getAccountIdentifier(), responseDTO.getRole(), responseDTO.getScope()));
      return RolesApiUtils.getRolesResponse(responseDTO);
    }));
    return Response.status(201).entity(response).build();
  }

  @Override
  public Response deleteRoleOrg(String org, String role, String account) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, null), Resource.of(ROLE, role), DELETE_ROLE_PERMISSION);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    String scopeIdentifier = ScopeMapper.fromParams(harnessScopeParams).toString();
    RolesResponse response = Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      RoleResponseDTO responseDTO = roleDTOMapper.toResponseDTO(roleService.delete(role, scopeIdentifier));
      outboxService.save(new RoleDeleteEvent(
          responseDTO.getScope().getAccountIdentifier(), responseDTO.getRole(), responseDTO.getScope()));
      return RolesApiUtils.getRolesResponse(responseDTO);
    }));
    return Response.ok().entity(response).build();
  }

  @Override
  public Response getRoleOrg(String org, String role, String account) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, null), Resource.of(ROLE, role), VIEW_ROLE_PERMISSION);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    String scopeIdentifier = ScopeMapper.fromParams(harnessScopeParams).toString();
    RolesResponse response = RolesApiUtils.getRolesResponse(roleDTOMapper.toResponseDTO(
        roleService.get(role, scopeIdentifier, NO_FILTER).<InvalidRequestException>orElseThrow(() -> {
          throw new InvalidRequestException("Role not found in Organization scope for given identifier.");
        })));
    return Response.ok().entity(response).build();
  }

  @Override
  public Response listRolesOrg(
      String org, Integer page, Integer limit, String searchTerm, String account, String sort, String order) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, null), Resource.of(ROLE, null), VIEW_ROLE_PERMISSION);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    String scopeIdentifier = ScopeMapper.fromParams(harnessScopeParams).toString();
    RoleFilter roleFilter =
        RoleFilter.builder().searchTerm(searchTerm).scopeIdentifier(scopeIdentifier).managedFilter(NO_FILTER).build();
    PageRequest pageRequest = ApiUtils.getPageRequest(page, limit, sort, order);
    PageResponse<Role> pageResponse = roleService.list(pageRequest, roleFilter, true);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, pageResponse.getTotalItems(), page, limit);
    return responseBuilderWithLinks
        .entity(pageResponse.getContent()
                    .stream()
                    .map(role -> RolesApiUtils.getRolesResponse(roleDTOMapper.toResponseDTO(role)))
                    .collect(Collectors.toList()))
        .build();
  }

  @Override
  public Response updateRoleOrg(CreateRoleRequest body, String org, String role, String account) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, null), Resource.of(ROLE, role), EDIT_ROLE_PERMISSION);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    if (!role.equals(body.getIdentifier())) {
      throw new InvalidRequestException("Role identifier in the request body and the URL do not match.");
    }
    String scopeIdentifier = ScopeMapper.fromParams(harnessScopeParams).toString();
    RolesResponse response = Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      RoleUpdateResult roleUpdateResult =
          roleService.update(fromDTO(scopeIdentifier, rolesApiUtils.getRoleOrgDTO(body)));
      RoleResponseDTO responseDTO = roleDTOMapper.toResponseDTO(roleUpdateResult.getUpdatedRole());
      outboxService.save(new RoleUpdateEvent(responseDTO.getScope().getAccountIdentifier(), responseDTO.getRole(),
          roleDTOMapper.toResponseDTO(roleUpdateResult.getOriginalRole()).getRole(), responseDTO.getScope()));
      return RolesApiUtils.getRolesResponse(responseDTO);
    }));
    return Response.ok().entity(response).build();
  }
}
