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
import static io.harness.accesscontrol.roles.api.RoleDTOMapper.fromDTO;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.TransactionUtils.DEFAULT_TRANSACTION_RETRY_POLICY;

import static java.lang.String.format;

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
import io.harness.ng.beans.PageResponse;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.accesscontrol.OrganizationRolesApi;
import io.harness.spec.server.accesscontrol.model.CreateRoleRequest;
import io.harness.spec.server.accesscontrol.model.RolesResponse;

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

  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_TRANSACTION_RETRY_POLICY;

  @Inject
  public OrgRolesApiImpl(RoleService roleService, ScopeService scopeService, RoleDTOMapper roleDTOMapper,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService,
      AccessControlClient accessControlClient) {
    this.roleService = roleService;
    this.scopeService = scopeService;
    this.roleDTOMapper = roleDTOMapper;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.accessControlClient = accessControlClient;
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.CUSTOM_ROLES)
  public Response createRoleOrg(CreateRoleRequest body, String org, @AccountIdentifier String account) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, null), Resource.of(ROLE, null), EDIT_ROLE_PERMISSION);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    Scope scope = scopeService.getOrCreate(ScopeMapper.fromParams(harnessScopeParams));
    RoleDTO roleDTO = RoleApiUtils.getRoleOrgDTO(body);
    roleDTO.setAllowedScopeLevels(Sets.newHashSet(scope.getLevel().toString()));

    RolesResponse response = Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      RoleResponseDTO responseDTO = roleDTOMapper.toResponseDTO(roleService.create(fromDTO(scope.toString(), roleDTO)));
      outboxService.save(new RoleCreateEvent(
          responseDTO.getScope().getAccountIdentifier(), responseDTO.getRole(), responseDTO.getScope()));
      return RoleApiUtils.getRolesResponse(responseDTO);
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
      return RoleApiUtils.getRolesResponse(responseDTO);
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
    RolesResponse response = RoleApiUtils.getRolesResponse(roleDTOMapper.toResponseDTO(
        roleService.get(role, scopeIdentifier, NO_FILTER).<InvalidRequestException>orElseThrow(() -> {
          throw new InvalidRequestException("Role not found in Organization scope for given identifier.");
        })));
    return Response.ok().entity(response).build();
  }

  @Override
  public Response listRolesOrg(String org, String account, Integer page, Integer limit, String searchTerm) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, null), Resource.of(ROLE, null), VIEW_ROLE_PERMISSION);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    String scopeIdentifier = ScopeMapper.fromParams(harnessScopeParams).toString();
    RoleFilter roleFilter =
        RoleFilter.builder().searchTerm(searchTerm).scopeIdentifier(scopeIdentifier).managedFilter(NO_FILTER).build();
    PageResponse<Role> pageResponse = roleService.list(RoleApiUtils.getPageRequest(page, limit), roleFilter);
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks = RoleApiUtils.addLinksHeader(
        responseBuilder, format("/v1/orgs/%s/roles)", org), pageResponse.getContent().size(), page, limit);
    return responseBuilderWithLinks
        .entity(pageResponse.getContent()
                    .stream()
                    .map(role -> RoleApiUtils.getRolesResponse(roleDTOMapper.toResponseDTO(role)))
                    .collect(Collectors.toList()))
        .build();
  }

  @Override
  public Response updateRoleOrg(CreateRoleRequest body, String org, String role, String account) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, null), Resource.of(ROLE, role), EDIT_ROLE_PERMISSION);
    HarnessScopeParams harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).build();
    if (!role.equals(body.getSlug())) {
      throw new InvalidRequestException("Role identifier in the request body and the URL do not match.");
    }
    String scopeIdentifier = ScopeMapper.fromParams(harnessScopeParams).toString();
    RolesResponse response = Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      RoleUpdateResult roleUpdateResult =
          roleService.update(fromDTO(scopeIdentifier, RoleApiUtils.getRoleOrgDTO(body)));
      RoleResponseDTO responseDTO = roleDTOMapper.toResponseDTO(roleUpdateResult.getUpdatedRole());
      outboxService.save(new RoleUpdateEvent(responseDTO.getScope().getAccountIdentifier(), responseDTO.getRole(),
          roleDTOMapper.toResponseDTO(roleUpdateResult.getOriginalRole()).getRole(), responseDTO.getScope()));
      return RoleApiUtils.getRolesResponse(responseDTO);
    }));
    return Response.ok().entity(response).build();
  }
}
