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
import static io.harness.aggregator.ACLEventProcessingConstants.CREATE_ACTION;
import static io.harness.aggregator.ACLEventProcessingConstants.DELETE_ACTION;
import static io.harness.aggregator.ACLEventProcessingConstants.UPDATE_ACTION;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTOMapper;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentRequest;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentCreateEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentCreateEventV2;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentDeleteEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentDeleteEventV2;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentUpdateEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentUpdateEventV2;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.aggregator.consumers.AccessControlChangeConsumer;
import io.harness.aggregator.models.RoleAssignmentChangeEventData;
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
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(PL)
@Slf4j
public class RoleAssignmentEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  private final UserGroupService userGroupService;
  private final UserMembershipClient userMembershipClient;
  private final ServiceAccountClient serviceAccountClient;
  private final OutboxEventHelper outboxEventHelper;
  private final RoleAssignmentDTOMapper roleAssignmentDTOMapper;
  private final boolean enableAclProcessingThroughOutbox;
  private final AccessControlChangeConsumer<RoleAssignmentChangeEventData> roleAssignmentChangeConsumer;

  @Inject
  public RoleAssignmentEventHandler(AuditClientService auditClientService, UserGroupService userGroupService,
      UserMembershipClient userMembershipClient, ServiceAccountClient serviceAccountClient,
      OutboxEventHelper outboxEventHelper, RoleAssignmentDTOMapper roleAssignmentDTOMapper,
      @Named("enableAclProcessingThroughOutbox") boolean enableAclProcessingThroughOutbox,
      AccessControlChangeConsumer<RoleAssignmentChangeEventData> roleAssignmentChangeConsumer) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
    this.userGroupService = userGroupService;
    this.userMembershipClient = userMembershipClient;
    this.serviceAccountClient = serviceAccountClient;
    this.outboxEventHelper = outboxEventHelper;
    this.roleAssignmentDTOMapper = roleAssignmentDTOMapper;
    this.enableAclProcessingThroughOutbox = enableAclProcessingThroughOutbox;
    this.roleAssignmentChangeConsumer = roleAssignmentChangeConsumer;
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
    RoleAssignmentDTO roleAssignmentDTO = null;
    ScopeDTO scopeDTO = null;
    RoleAssignment roleAssignment = null;
    Pair<Optional<ResourceScopeDTO>, Optional<Scope>> scopes = outboxEventHelper.getScopes(outboxEvent);
    if (outboxEventHelper.isEventV2(outboxEvent)) {
      RoleAssignmentCreateEventV2 roleAssignmentCreateEventV2 =
          objectMapper.readValue(outboxEvent.getEventData(), RoleAssignmentCreateEventV2.class);
      roleAssignment = roleAssignmentCreateEventV2.getRoleAssignment();
      scopeDTO = toDTO(scopes.getRight().get());
      roleAssignmentDTO = RoleAssignmentDTOMapper.toDTO(roleAssignment);
    } else {
      RoleAssignmentCreateEvent roleAssignmentCreateEvent =
          objectMapper.readValue(outboxEvent.getEventData(), RoleAssignmentCreateEvent.class);
      roleAssignmentDTO = roleAssignmentCreateEvent.getRoleAssignment();
      scopeDTO = roleAssignmentCreateEvent.getScope();
      Scope scope = fromDTO(scopeDTO);
      roleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, roleAssignmentDTO);
      roleAssignment.setId(roleAssignmentCreateEvent.getRoleAssignmentId());
    }
    if (enableAclProcessingThroughOutbox) {
      RoleAssignmentChangeEventData roleAssignmentChangeEventData =
          RoleAssignmentChangeEventData.builder().newRoleAssignment(roleAssignment).build();
      roleAssignmentChangeConsumer.consumeEvent(CREATE_ACTION, null, roleAssignmentChangeEventData);
    }
    ResourceDTO resourceDTO = getAuditResource(scopeDTO, roleAssignmentDTO);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.ROLE_ASSIGNMENT_CREATED)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(RoleAssignmentRequest.builder().roleAssignment(roleAssignmentDTO).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(resourceDTO)
            .resourceScope(scopes.getLeft().orElse(null))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleRoleAssignmentUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RoleAssignmentDTO newRoleAssignmentDTO = null;
    ScopeDTO scopeDTO = null;
    RoleAssignment newRoleAssignment = null;
    RoleAssignment oldRoleAssignment = null;
    RoleAssignmentDTO oldRoleAssignmentDTO = null;
    Pair<Optional<ResourceScopeDTO>, Optional<Scope>> scopes = outboxEventHelper.getScopes(outboxEvent);
    if (outboxEventHelper.isEventV2(outboxEvent)) {
      RoleAssignmentUpdateEventV2 roleAssignmentUpdateEventV2 =
          objectMapper.readValue(outboxEvent.getEventData(), RoleAssignmentUpdateEventV2.class);
      newRoleAssignment = roleAssignmentUpdateEventV2.getNewRoleAssignment();
      oldRoleAssignment = roleAssignmentUpdateEventV2.getOldRoleAssignment();
      scopeDTO = toDTO(scopes.getRight().get());
      newRoleAssignmentDTO = RoleAssignmentDTOMapper.toDTO(newRoleAssignment);
      oldRoleAssignmentDTO = RoleAssignmentDTOMapper.toDTO(roleAssignmentUpdateEventV2.getOldRoleAssignment());
    } else {
      RoleAssignmentUpdateEvent roleAssignmentUpdateEvent =
          objectMapper.readValue(outboxEvent.getEventData(), RoleAssignmentUpdateEvent.class);
      newRoleAssignmentDTO = roleAssignmentUpdateEvent.getNewRoleAssignment();
      scopeDTO = roleAssignmentUpdateEvent.getScope();
      Scope scope = scopes.getRight().get();
      newRoleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, newRoleAssignmentDTO);
      oldRoleAssignmentDTO = roleAssignmentUpdateEvent.getOldRoleAssignment();
      oldRoleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, oldRoleAssignmentDTO);
    }
    if (enableAclProcessingThroughOutbox) {
      RoleAssignmentChangeEventData roleAssignmentChangeEventData = RoleAssignmentChangeEventData.builder()
                                                                        .newRoleAssignment(newRoleAssignment)
                                                                        .updatedRoleAssignment(oldRoleAssignment)
                                                                        .build();
      roleAssignmentChangeConsumer.consumeEvent(UPDATE_ACTION, null, roleAssignmentChangeEventData);
    }
    ResourceDTO resourceDTO = getAuditResource(scopeDTO, newRoleAssignmentDTO);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.ROLE_ASSIGNMENT_UPDATED)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(RoleAssignmentRequest.builder().roleAssignment(newRoleAssignmentDTO).build()))
            .oldYaml(getYamlString(RoleAssignmentRequest.builder().roleAssignment(oldRoleAssignmentDTO).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(resourceDTO)
            .resourceScope(scopes.getLeft().get())
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleRoleAssignmentDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    boolean skipAudit = false;
    RoleAssignmentDTO roleAssignmentDTO = null;
    ScopeDTO scopeDTO = null;
    RoleAssignment roleAssignment = null;
    Pair<Optional<ResourceScopeDTO>, Optional<Scope>> scopes = outboxEventHelper.getScopes(outboxEvent);
    if (outboxEventHelper.isEventV2(outboxEvent)) {
      RoleAssignmentDeleteEventV2 roleAssignmentDeleteEventV2 =
          objectMapper.readValue(outboxEvent.getEventData(), RoleAssignmentDeleteEventV2.class);
      skipAudit = roleAssignmentDeleteEventV2.isSkipAudit();
      roleAssignment = roleAssignmentDeleteEventV2.getRoleAssignment();
      scopeDTO = toDTO(scopes.getRight().get());
      roleAssignmentDTO = RoleAssignmentDTOMapper.toDTO(roleAssignment);
    } else {
      RoleAssignmentDeleteEvent roleAssignmentDeleteEvent =
          objectMapper.readValue(outboxEvent.getEventData(), RoleAssignmentDeleteEvent.class);
      roleAssignmentDTO = roleAssignmentDeleteEvent.getRoleAssignment();
      scopeDTO = roleAssignmentDeleteEvent.getScope();
      skipAudit = roleAssignmentDeleteEvent.getSkipAudit() != null && roleAssignmentDeleteEvent.getSkipAudit();
      roleAssignment = RoleAssignmentDTOMapper.fromDTO(scopes.getRight().get(), roleAssignmentDTO);
      roleAssignment.setId(roleAssignmentDeleteEvent.getRoleAssignmentId());
    }
    if (enableAclProcessingThroughOutbox) {
      RoleAssignmentChangeEventData changeEventData =
          RoleAssignmentChangeEventData.builder().deletedRoleAssignment(roleAssignment).build();
      roleAssignmentChangeConsumer.consumeEvent(DELETE_ACTION, null, changeEventData);
    }
    if (!skipAudit) {
      ResourceDTO resourceDTO = getAuditResource(scopeDTO, roleAssignmentDTO);
      return shouldAuditDelete(outboxEvent, globalContext, roleAssignmentDTO, resourceDTO, scopes.getLeft());
    }
    return true;
  }

  private boolean shouldAuditDelete(OutboxEvent outboxEvent, GlobalContext globalContext,
      RoleAssignmentDTO roleAssignmentDTO, ResourceDTO resourceDTO,
      Optional<ResourceScopeDTO> resourceScopeDTOOptional) {
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.ROLE_ASSIGNMENT_DELETED)
            .module(ModuleType.CORE)
            .oldYaml(getYamlString(RoleAssignmentRequest.builder().roleAssignment(roleAssignmentDTO).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(resourceDTO)
            .resourceScope(resourceScopeDTOOptional.get())
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
