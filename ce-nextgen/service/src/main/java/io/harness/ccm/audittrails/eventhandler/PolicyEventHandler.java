package io.harness.ccm.audittrails.eventhandler;

import io.harness.ccm.audittrails.events.PolicyCreateEvent;
import static io.harness.ccm.audittrails.events.PolicyCreateEvent.POLICY_CREATED;
import io.harness.ccm.audittrails.events.PolicyDeleteEvent;
import static io.harness.ccm.audittrails.events.PolicyDeleteEvent.POLICY_DELETED;
import io.harness.ccm.audittrails.events.PolicyUpdateEvent;
import static io.harness.ccm.audittrails.events.PolicyUpdateEvent.POLICY_UPDATED;
import io.harness.ccm.views.dto.CreatePolicyDTO;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j

public class PolicyEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  public PolicyEventHandler(AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case POLICY_CREATED:
          return handlePolicyCreateEvent(outboxEvent);
        case POLICY_UPDATED:
          return handlePolicyUpdateEvent(outboxEvent);
        case POLICY_DELETED:
          return handlePolicyDeleteEvent(outboxEvent);

        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error(exception.toString());
      return false;
    }
  }

  private boolean handlePolicyCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
      PolicyCreateEvent policyCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), PolicyCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CE)
            .newYaml(getYamlString(CreatePolicyDTO.builder().policy(policyCreateEvent.getPolicy()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handlePolicyUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
      PolicyUpdateEvent policyUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), PolicyUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CE)
                .newYaml(getYamlString(CreatePolicyDTO.builder().policy(policyUpdateEvent.getPolicy()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handlePolicyDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
      PolicyDeleteEvent policyDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), PolicyDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CE)
                .newYaml(getYamlString(CreatePolicyDTO.builder().policy(policyDeleteEvent.getPolicy()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
