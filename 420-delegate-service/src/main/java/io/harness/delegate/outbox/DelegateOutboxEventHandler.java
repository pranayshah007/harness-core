/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.outbox;

import static io.harness.delegate.utils.DelegateOutboxEventConstants.DELEGATE_DELETE_EVENT;
import static io.harness.delegate.utils.DelegateOutboxEventConstants.DELEGATE_REGISTER_EVENT;
import static io.harness.delegate.utils.DelegateOutboxEventConstants.DELEGATE_TOKEN_CREATE_EVENT;
import static io.harness.delegate.utils.DelegateOutboxEventConstants.DELEGATE_TOKEN_REVOKE_EVENT;
import static io.harness.delegate.utils.DelegateOutboxEventConstants.DELEGATE_UNREGISTER_EVENT;
import static io.harness.delegate.utils.DelegateOutboxEventConstants.DELEGATE_UPSERT_EVENT;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.delegate.events.DelegateDeleteEvent;
import io.harness.delegate.events.DelegateNgTokenCreateEvent;
import io.harness.delegate.events.DelegateNgTokenRevokeEvent;
import io.harness.delegate.events.DelegateRegisterEvent;
import io.harness.delegate.events.DelegateUnregisterEvent;
import io.harness.delegate.events.DelegateUpsertEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class DelegateOutboxEventHandler implements OutboxEventHandler {
  private ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  @Inject
  DelegateOutboxEventHandler(AuditClientService auditClientService) {
    this.objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case DELEGATE_UPSERT_EVENT:
          return handleDelegateUpsertEvent(outboxEvent);
        case DELEGATE_DELETE_EVENT:
          return handleDelegateDeleteEvent(outboxEvent);
        case DELEGATE_TOKEN_CREATE_EVENT:
          return handleDelegateNgTokenCreateEvent(outboxEvent);
        case DELEGATE_TOKEN_REVOKE_EVENT:
          return handleDelegateNgTokenRevokeEvent(outboxEvent);
        case DELEGATE_REGISTER_EVENT:
          return handleDelegateRegisterEvent(outboxEvent);
        case DELEGATE_UNREGISTER_EVENT:
          return handleDelegateUnRegisterEvent(outboxEvent);
        default:
          return false;
      }
    } catch (IOException exception) {
      return false;
    }
  }

  @VisibleForTesting
  protected boolean handleDelegateUpsertEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    DelegateUpsertEvent delegateUpsertEvent =
        objectMapper.readValue(outboxEvent.getEventData(), DelegateUpsertEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPSERT)
                                .module(ModuleType.CORE)
                                .newYaml(getYamlString(delegateUpsertEvent.getDelegateSetupDetails()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  @VisibleForTesting
  protected boolean handleDelegateDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    DelegateDeleteEvent delegateDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), DelegateDeleteEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.CORE)
                                .newYaml(getYamlString(delegateDeleteEvent.getDelegateSetupDetails()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleDelegateNgTokenCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    DelegateNgTokenCreateEvent delegateNgTokenCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), DelegateNgTokenCreateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE_TOKEN)
                                .module(ModuleType.CORE)
                                .newYaml(getYamlString(delegateNgTokenCreateEvent.getToken()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleDelegateNgTokenRevokeEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    DelegateNgTokenRevokeEvent delegateNgTokenRevokeEvent =
        objectMapper.readValue(outboxEvent.getEventData(), DelegateNgTokenRevokeEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.REVOKE_TOKEN)
                                .module(ModuleType.CORE)
                                .newYaml(getYamlString(delegateNgTokenRevokeEvent.getToken()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  @VisibleForTesting
  protected boolean handleDelegateRegisterEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    DelegateRegisterEvent delegateRegisterEvent =
        objectMapper.readValue(outboxEvent.getEventData(), DelegateRegisterEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.CORE)
                                .timestamp(outboxEvent.getCreatedAt())
                                .newYaml(getYamlString(delegateRegisterEvent.getDelegateSetupDetails()))
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  @VisibleForTesting
  protected boolean handleDelegateUnRegisterEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    DelegateUnregisterEvent delegateUnRegisterEvent =
        objectMapper.readValue(outboxEvent.getEventData(), DelegateUnregisterEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.CORE)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .newYaml(getYamlString(delegateUnRegisterEvent.getDelegateSetupDetails()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
