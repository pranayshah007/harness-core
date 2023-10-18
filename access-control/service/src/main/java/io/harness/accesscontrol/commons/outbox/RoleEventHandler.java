/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.outbox;

import static io.harness.accesscontrol.roles.RoleMapper.toDTO;
import static io.harness.accesscontrol.roles.events.RoleCreateEvent.ROLE_CREATE_EVENT;
import static io.harness.accesscontrol.roles.events.RoleDeleteEvent.ROLE_DELETE_EVENT;
import static io.harness.accesscontrol.roles.events.RoleUpdateEvent.ROLE_UPDATE_EVENT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleDTOMapper;
import io.harness.accesscontrol.roles.api.RoleRequest;
import io.harness.accesscontrol.roles.events.RoleCreateEvent;
import io.harness.accesscontrol.roles.events.RoleCreateEventV2;
import io.harness.accesscontrol.roles.events.RoleDeleteEvent;
import io.harness.accesscontrol.roles.events.RoleDeleteEventV2;
import io.harness.accesscontrol.roles.events.RoleUpdateEvent;
import io.harness.accesscontrol.roles.events.RoleUpdateEventV2;
import io.harness.accesscontrol.scopes.AccessControlResourceScope;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.aggregator.consumers.AccessControlChangeConsumer;
import io.harness.aggregator.models.RoleChangeEventData;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.UnexpectedException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

@OwnedBy(PL)
@Slf4j
public class RoleEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  private final AccessControlChangeConsumer<RoleChangeEventData> roleChangeConsumer;
  private final boolean enableAclProcessingThroughOutbox;
  private final ScopeService scopeService;

  @Inject
  public RoleEventHandler(AuditClientService auditClientService,
      AccessControlChangeConsumer<RoleChangeEventData> roleChangeConsumer,
      @Named("enableAclProcessingThroughOutbox") boolean enableAclProcessingThroughOutbox, ScopeService scopeService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
    this.roleChangeConsumer = roleChangeConsumer;
    this.enableAclProcessingThroughOutbox = enableAclProcessingThroughOutbox;
    this.scopeService = scopeService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case ROLE_CREATE_EVENT:
          return handleRoleCreateEvent(outboxEvent);
        case ROLE_UPDATE_EVENT:
          return handleRoleUpdateEvent(outboxEvent);
        case ROLE_DELETE_EVENT:
          return handleRoleDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error(
          String.format("IOException occurred during handling outbox event of type %s", outboxEvent.getEventType()),
          exception);
      return false;
    }
  }

  private boolean handleRoleCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    if (isEventV2(outboxEvent)) {
      RoleCreateEventV2 roleCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), RoleCreateEventV2.class);
    } else {
      RoleCreateEvent roleCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), RoleCreateEvent.class);
    }
    Pair<Optional<ResourceScopeDTO>, Optional<Scope>> scopes = getScopes(outboxEvent);
    Optional<ResourceScopeDTO> resourceScopeDTO = scopes.getLeft();
    // Scope will be null for managed role and so setting default value of auditPublished as true because we don't want
    // to Audit for managed role.
    boolean auditPublished = true;
    if (resourceScopeDTO.isPresent()) {
      AuditEntry auditEntry = AuditEntry.builder()
                                  .action(Action.CREATE)
                                  .module(ModuleType.CORE)
                                  .timestamp(outboxEvent.getCreatedAt())
                                  .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                  .resourceScope(resourceScopeDTO.get())
                                  .insertId(outboxEvent.getId())
                                  .build();
      auditPublished = auditClientService.publishAudit(auditEntry, globalContext);
    }
    return auditPublished;
  }

  private boolean handleRoleUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    Pair<Optional<ResourceScopeDTO>, Optional<Scope>> scopes = getScopes(outboxEvent);
    Optional<ResourceScopeDTO> resourceScopeDTO = scopes.getLeft();
    Optional<Scope> scope = scopes.getRight();
    if (isEventV2(outboxEvent)) {
      return processRoleUpdateEventV2(outboxEvent, globalContext, resourceScopeDTO);
    } else {
      return processRoleUpdateEventV1(outboxEvent, globalContext, resourceScopeDTO, scope);
    }
  }

  private boolean processRoleUpdateEventV2(
      OutboxEvent outboxEvent, GlobalContext globalContext, Optional<ResourceScopeDTO> resourceScopeDTO) {
    try {
      RoleUpdateEventV2 roleUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), RoleUpdateEventV2.class);
      Role newRole = roleUpdateEvent.getNewRole();
      computeACLOnRoleUpdateV2(roleUpdateEvent, newRole);
      RoleDTO newRoleDTO = toDTO(newRole);
      RoleDTO oldRoleDTO = toDTO(roleUpdateEvent.getOldRole());
      return auditRoleUpdate(outboxEvent, globalContext, resourceScopeDTO, oldRoleDTO, newRoleDTO);
    } catch (Exception ex) {
      log.error("RoleEventHandler: Error occured during acl processing of role update", ex);
      throw new UnexpectedException("RoleEventHandler: Error occured during acl processing of role update", ex);
    }
  }

  private boolean auditRoleUpdate(OutboxEvent outboxEvent, GlobalContext globalContext,
      Optional<ResourceScopeDTO> resourceScopeDTO, RoleDTO oldRoleDTO, RoleDTO newRoleDTO) {
    boolean auditPublished = true;
    // Scope will be null for managed role and so setting default value of auditPublished as true because we don't want
    // to Audit for managed role.
    if (resourceScopeDTO.isPresent()) {
      AuditEntry auditEntry = AuditEntry.builder()
                                  .action(Action.UPDATE)
                                  .module(ModuleType.CORE)
                                  .timestamp(outboxEvent.getCreatedAt())
                                  .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                  .resourceScope(resourceScopeDTO.get())
                                  .oldYaml(getYamlString(RoleRequest.builder().role(oldRoleDTO).build()))
                                  .newYaml(getYamlString(RoleRequest.builder().role(newRoleDTO).build()))
                                  .insertId(outboxEvent.getId())
                                  .build();
      auditPublished = auditClientService.publishAudit(auditEntry, globalContext);
    }
    return auditPublished;
  }

  private void computeACLOnRoleUpdateV2(RoleUpdateEventV2 roleUpdateEvent, Role newRole) {
    if (enableAclProcessingThroughOutbox) {
      Set<String> permissionsAddedToRole = getDiffPermissions(
          roleUpdateEvent.getNewRole().getPermissions(), roleUpdateEvent.getOldRole().getPermissions());
      Set<String> permissionsRemovedFromRole = getDiffPermissions(
          roleUpdateEvent.getOldRole().getPermissions(), roleUpdateEvent.getNewRole().getPermissions());

      RoleChangeEventData roleChangeEventData = RoleChangeEventData.builder()
                                                    .updatedRole(newRole)
                                                    .permissionsAdded(permissionsAddedToRole)
                                                    .permissionsRemoved(permissionsRemovedFromRole)
                                                    .build();
      roleChangeConsumer.consumeUpdateEvent(null, roleChangeEventData);
    }
  }

  private boolean processRoleUpdateEventV1(OutboxEvent outboxEvent, GlobalContext globalContext,
      Optional<ResourceScopeDTO> resourceScopeDTO, Optional<Scope> scope) {
    try {
      RoleUpdateEvent roleUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), RoleUpdateEvent.class);
      computeACLOnRoleUpdateV1(scope, roleUpdateEvent);
      return auditRoleUpdate(
          outboxEvent, globalContext, resourceScopeDTO, roleUpdateEvent.getOldRole(), roleUpdateEvent.getNewRole());
    } catch (Exception ex) {
      log.error("RoleEventHandler: Error occured during acl processing of role update", ex);
      throw new UnexpectedException("RoleEventHandler: Error occured during acl processing of role update", ex);
    }
  }

  private void computeACLOnRoleUpdateV1(Optional<Scope> scope, RoleUpdateEvent roleUpdateEvent) {
    if (enableAclProcessingThroughOutbox) {
      Set<String> permissionsAddedToRole = getDiffPermissions(
          roleUpdateEvent.getNewRole().getPermissions(), roleUpdateEvent.getOldRole().getPermissions());
      Set<String> permissionsRemovedFromRole = getDiffPermissions(
          roleUpdateEvent.getOldRole().getPermissions(), roleUpdateEvent.getNewRole().getPermissions());
      Role newRole = RoleDTOMapper.fromDTO(scope.map(Scope::toString).orElse(null), roleUpdateEvent.getNewRole());
      RoleChangeEventData roleChangeEventData = RoleChangeEventData.builder()
                                                    .updatedRole(newRole)
                                                    .permissionsAdded(permissionsAddedToRole)
                                                    .permissionsRemoved(permissionsRemovedFromRole)
                                                    .build();
      roleChangeConsumer.consumeUpdateEvent(null, roleChangeEventData);
    }
  }

  @NotNull
  private Sets.SetView<String> getDiffPermissions(Set<String> permissionsSet1, Set<String> permissionsSet2) {
    return Sets.difference(getPermissions(permissionsSet1), getPermissions(permissionsSet2));
  }

  private Set<String> getPermissions(Set<String> permissions) {
    return isEmpty(permissions) ? Collections.emptySet() : permissions;
  }

  private boolean handleRoleDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    if (isEventV2(outboxEvent)) {
      RoleDeleteEventV2 roleDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), RoleDeleteEventV2.class);
    } else {
      RoleDeleteEvent roleDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), RoleDeleteEvent.class);
    }
    Pair<Optional<ResourceScopeDTO>, Optional<Scope>> scopes = getScopes(outboxEvent);
    Optional<ResourceScopeDTO> resourceScopeDTO = scopes.getLeft();
    boolean auditPublished = false;
    // Scope will be null for managed role and so setting default value of auditPublished as true because we don't want
    // to Audit for managed role.
    if (resourceScopeDTO.isPresent()) {
      AuditEntry auditEntry = AuditEntry.builder()
                                  .action(Action.DELETE)
                                  .module(ModuleType.CORE)
                                  .timestamp(outboxEvent.getCreatedAt())
                                  .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                  .resourceScope(resourceScopeDTO.get())
                                  .insertId(outboxEvent.getId())
                                  .build();
      auditPublished = auditClientService.publishAudit(auditEntry, globalContext);
    }
    return auditPublished;
  }

  private boolean isEventV2(OutboxEvent outboxEvent) {
    return outboxEvent.getResourceScope() instanceof AccessControlResourceScope;
  }

  private Pair<Optional<ResourceScopeDTO>, Optional<Scope>> getScopes(OutboxEvent outboxEvent) {
    Optional<ResourceScopeDTO> resourceScopeDTO = Optional.empty();
    Optional<Scope> scope = Optional.empty();
    if (Objects.isNull(outboxEvent.getResourceScope())) {
      return Pair.of(resourceScopeDTO, scope);
    }
    if (isEventV2(outboxEvent)) {
      scope = !isEmpty(outboxEvent.getResourceScope().getScope())
          ? Optional.of(scopeService.buildScopeFromScopeIdentifier(outboxEvent.getResourceScope().getScope()))
          : Optional.empty();
      resourceScopeDTO = scope.map(ScopeMapper::toResourceScopeDTO);
    } else {
      resourceScopeDTO = Optional.of(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()));
      scope = resourceScopeDTO.map(ScopeMapper::toScope);
    }
    return Pair.of(resourceScopeDTO, scope);
  }
}