/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.outbox;

import static io.harness.accesscontrol.roleassignments.events.RoleAssignmentCreateEvent.ROLE_ASSIGNMENT_CREATE_EVENT;
import static io.harness.accesscontrol.roleassignments.events.RoleAssignmentDeleteEvent.ROLE_ASSIGNMENT_DELETE_EVENT;
import static io.harness.accesscontrol.roleassignments.events.RoleAssignmentUpdateEvent.ROLE_ASSIGNMENT_UPDATE_EVENT;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromDTO;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.toDTO;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.toParams;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.toParentScopeParams;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentRequest;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentCreateEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentDeleteEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentUpdateEvent;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.serviceaccount.remote.ServiceAccountClient;
import io.harness.usermembership.remote.UserMembershipClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class RoleAssignmentEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  private final UserGroupService userGroupService;
  private final UserMembershipClient userMembershipClient;
  private final ServiceAccountClient serviceAccountClient;

  @Inject
  public RoleAssignmentEventHandler(AuditClientService auditClientService, UserGroupService userGroupService,
      UserMembershipClient userMembershipClient, ServiceAccountClient serviceAccountClient) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
    this.userGroupService = userGroupService;
    this.userMembershipClient = userMembershipClient;
    this.serviceAccountClient = serviceAccountClient;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(ACCESS_CONTROL_SERVICE.getServiceId()));
      switch (outboxEvent.getEventType()) {
        case ROLE_ASSIGNMENT_CREATE_EVENT:
          return handleRoleAssignmentCreateEvent(outboxEvent);
        case ROLE_ASSIGNMENT_UPDATE_EVENT:
          return handleRoleAssignmentUpdateEvent(outboxEvent);
        case ROLE_ASSIGNMENT_DELETE_EVENT:
          return handleRoleAssignmentDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      SecurityContextBuilder.unsetCompleteContext();
      log.error(
          String.format("IOException occurred during handling outbox event of type %s", outboxEvent.getEventType()),
          exception);
      return false;
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private boolean handleRoleAssignmentCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RoleAssignmentCreateEvent roleAssignmentCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), RoleAssignmentCreateEvent.class);
    ResourceDTO resourceDTO =
        getAuditResource(roleAssignmentCreateEvent.getScope(), roleAssignmentCreateEvent.getRoleAssignment());
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.ROLE_ASSIGNMENT_CREATED)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(
                RoleAssignmentRequest.builder().roleAssignment(roleAssignmentCreateEvent.getRoleAssignment()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(resourceDTO)
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleRoleAssignmentUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RoleAssignmentUpdateEvent roleAssignmentUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), RoleAssignmentUpdateEvent.class);
    ResourceDTO resourceDTO =
        getAuditResource(roleAssignmentUpdateEvent.getScope(), roleAssignmentUpdateEvent.getNewRoleAssignment());
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.ROLE_ASSIGNMENT_UPDATED)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(RoleAssignmentRequest.builder()
                                       .roleAssignment(roleAssignmentUpdateEvent.getNewRoleAssignment())
                                       .build()))
            .oldYaml(getYamlString(RoleAssignmentRequest.builder()
                                       .roleAssignment(roleAssignmentUpdateEvent.getOldRoleAssignment())
                                       .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(resourceDTO)
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleRoleAssignmentDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RoleAssignmentDeleteEvent roleAssignmentDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), RoleAssignmentDeleteEvent.class);
    ResourceDTO resourceDTO =
        getAuditResource(roleAssignmentDeleteEvent.getScope(), roleAssignmentDeleteEvent.getRoleAssignment());
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.ROLE_ASSIGNMENT_DELETED)
            .module(ModuleType.CORE)
            .oldYaml(getYamlString(
                RoleAssignmentRequest.builder().roleAssignment(roleAssignmentDeleteEvent.getRoleAssignment()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(resourceDTO)
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private ResourceDTO getAuditResource(ScopeDTO scopeDTO, RoleAssignmentDTO roleAssignmentDTO) {
    switch (roleAssignmentDTO.getPrincipal().getType()) {
      case USER:
        return getUserResourceDTO(scopeDTO, roleAssignmentDTO.getPrincipal().getIdentifier());
      case USER_GROUP:
        return getUserGroupResourceDTO(
            toDTO(toParentScopeParams(toParams(scopeDTO), roleAssignmentDTO.getPrincipal().getScopeLevel())),
            roleAssignmentDTO.getPrincipal().getIdentifier());
      case SERVICE_ACCOUNT:
        return getServiceAccountResourceDTO(
            toDTO(toParentScopeParams(toParams(scopeDTO), roleAssignmentDTO.getPrincipal().getScopeLevel())),
            roleAssignmentDTO.getPrincipal().getIdentifier());
      default:
        throw new InvalidArgumentsException(String.format(
            "Not supported principal type %s in role assignment audits.", roleAssignmentDTO.getPrincipal().getType()));
    }
  }

  private ResourceDTO getUserResourceDTO(ScopeDTO scopeDTO, String identifier) {
    UserMetadataDTO user = getResponse(userMembershipClient.getUser(identifier, scopeDTO.getAccountIdentifier()));
    ResourceDTO userResourceDTO = ResourceDTO.builder().type(ResourceTypeConstants.USER).identifier(identifier).build();
    if (user != null) {
      Map<String, String> labels = new HashMap<>();
      userResourceDTO.setIdentifier(user.getEmail());
      labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, user.getName());
      labels.put(ResourceConstants.LABEL_KEY_USER_ID, user.getUuid());
      userResourceDTO.setLabels(labels);
    }
    return userResourceDTO;
  }

  private ResourceDTO getUserGroupResourceDTO(ScopeDTO scopeDTO, String identifier) {
    Optional<UserGroup> userGroupOptional = userGroupService.get(identifier, fromDTO(scopeDTO).toString());
    ResourceDTO userGroupResourceDTO =
        ResourceDTO.builder().type(ResourceTypeConstants.USER_GROUP).identifier(identifier).build();
    if (userGroupOptional.isPresent()) {
      Map<String, String> labels = new HashMap<>();
      labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, userGroupOptional.get().getName());
      userGroupResourceDTO.setLabels(labels);
    }
    return userGroupResourceDTO;
  }

  private ResourceDTO getServiceAccountResourceDTO(ScopeDTO scopeDTO, String identifier) {
    List<String> resourceIds = Lists.newArrayList(identifier);
    List<ServiceAccountDTO> serviceAccountDTOs = new ArrayList<>();

    ResourceDTO serviceAccountResourceDTO =
        ResourceDTO.builder().type(ResourceTypeConstants.SERVICE_ACCOUNT).identifier(identifier).build();

    serviceAccountDTOs.addAll(getResponse(
        serviceAccountClient.listServiceAccounts(scopeDTO.getAccountIdentifier(), null, null, resourceIds)));
    serviceAccountDTOs.addAll(getResponse(serviceAccountClient.listServiceAccounts(
        scopeDTO.getAccountIdentifier(), scopeDTO.getOrgIdentifier(), null, resourceIds)));
    serviceAccountDTOs.addAll(getResponse(serviceAccountClient.listServiceAccounts(
        scopeDTO.getAccountIdentifier(), scopeDTO.getOrgIdentifier(), scopeDTO.getProjectIdentifier(), resourceIds)));

    if (isNotEmpty(serviceAccountDTOs)) {
      Map<String, String> labels = new HashMap<>();
      labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, serviceAccountDTOs.get(0).getName());
      serviceAccountResourceDTO.setLabels(labels);
    }
    return serviceAccountResourceDTO;
  }
}
