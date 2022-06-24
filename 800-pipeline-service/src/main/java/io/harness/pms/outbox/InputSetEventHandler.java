/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.outbox.OutboxEvent;
import io.harness.pms.events.InputSetCreateEvent;
import io.harness.pms.events.InputSetDeleteEvent;
import io.harness.pms.events.InputSetUpdateEvent;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.io.IOException;

@OwnedBy(HarnessTeam.PIPELINE)
public class InputSetEventHandler {
  private ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  @Inject
  InputSetEventHandler(AuditClientService auditClientService) {
    this.objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  protected boolean handleInputSetCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    InputSetCreateEvent event = objectMapper.readValue(outboxEvent.getEventData(), InputSetCreateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .insertId(outboxEvent.getId())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .action(Action.CREATE)
                                .module(ModuleType.PMS)
                                .timestamp(outboxEvent.getCreatedAt())
                                .newYaml(event.getInputSet().getYaml())
                                .build();
    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(PIPELINE_SERVICE.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    if (event.getIsForOldGitSync()) {
      return true;
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  protected boolean handleInputSetUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    InputSetUpdateEvent event = objectMapper.readValue(outboxEvent.getEventData(), InputSetUpdateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .insertId(outboxEvent.getId())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .action(Action.UPDATE)
                                .module(ModuleType.PMS)
                                .timestamp(outboxEvent.getCreatedAt())
                                .oldYaml(event.getOldInputSet().getYaml())
                                .newYaml(event.getNewInputSet().getYaml())
                                .build();
    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(PIPELINE_SERVICE.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    if (event.getIsForOldGitSync()) {
      return true;
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  protected boolean handleInputSetDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    InputSetDeleteEvent event = objectMapper.readValue(outboxEvent.getEventData(), InputSetDeleteEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .insertId(outboxEvent.getId())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .action(Action.DELETE)
                                .module(ModuleType.PMS)
                                .timestamp(outboxEvent.getCreatedAt())
                                .oldYaml(event.getInputSet().getYaml())
                                .build();
    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(PIPELINE_SERVICE.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    if (event.getIsForOldGitSync()) {
      return true;
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }
}
