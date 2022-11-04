package io.harness.ccm.audittrails.eventhandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.ccm.audittrails.events.PolicyEnforcementCreateEvent;
import static io.harness.ccm.audittrails.events.PolicyEnforcementCreateEvent.POLICY_ENFORCEMENT_CREATED;
import io.harness.ccm.audittrails.events.PolicyEnforcementDeleteEvent;
import static io.harness.ccm.audittrails.events.PolicyEnforcementDeleteEvent.POLICY_ENFORCEMENT_DELETED;
import io.harness.ccm.audittrails.events.PolicyEnforcementUpdateEvent;
import static io.harness.ccm.audittrails.events.PolicyEnforcementUpdateEvent.POLICY_ENFORCEMENT_UPDATED;
import io.harness.ccm.views.dto.CreatePolicyEnforcementDTO;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j

public class PolicyEnforcementEventHandler implements OutboxEventHandler {
    private final ObjectMapper objectMapper;
    private final AuditClientService auditClientService;

    @Inject
    public PolicyEnforcementEventHandler(AuditClientService auditClientService) {
        this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
        this.auditClientService = auditClientService;
    }

    @Override
    public boolean handle(OutboxEvent outboxEvent) {
        try {
            switch (outboxEvent.getEventType()) {
                case POLICY_ENFORCEMENT_CREATED:
                    return handlePolicyEnforcementCreateEvent(outboxEvent);
                case POLICY_ENFORCEMENT_UPDATED:
                    return handlePolicyEnforcementUpdateEvent(outboxEvent);
                case POLICY_ENFORCEMENT_DELETED:
                    return handlePolicyEnforcementDeleteEvent(outboxEvent);

                default:
                    throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
            }
        } catch (IOException exception) {
            log.error(exception.toString());
            return false;
        }
    }

    private boolean handlePolicyEnforcementCreateEvent(OutboxEvent outboxEvent) throws IOException {
        GlobalContext globalContext = outboxEvent.getGlobalContext();
        PolicyEnforcementCreateEvent policyEnforcementCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), PolicyEnforcementCreateEvent.class);
        AuditEntry auditEntry =
                AuditEntry.builder()
                        .action(Action.CREATE)
                        .module(ModuleType.CE)
                        .newYaml(getYamlString(CreatePolicyEnforcementDTO.builder().policyEnforcement(policyEnforcementCreateEvent.getPolicyEnforcement()).build()))
                        .timestamp(outboxEvent.getCreatedAt())
                        .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                        .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                        .insertId(outboxEvent.getId())
                        .build();
        return auditClientService.publishAudit(auditEntry, globalContext);
    }
    private boolean handlePolicyEnforcementUpdateEvent(OutboxEvent outboxEvent) throws IOException {
        GlobalContext globalContext = outboxEvent.getGlobalContext();
        PolicyEnforcementUpdateEvent policyEnforcementUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), PolicyEnforcementUpdateEvent.class);
        AuditEntry auditEntry =
                AuditEntry.builder()
                        .action(Action.UPDATE)
                        .module(ModuleType.CE)
                        .newYaml(getYamlString(CreatePolicyEnforcementDTO.builder().policyEnforcement(policyEnforcementUpdateEvent.getPolicyEnforcement()).build()))
                        .timestamp(outboxEvent.getCreatedAt())
                        .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                        .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                        .insertId(outboxEvent.getId())
                        .build();
        return auditClientService.publishAudit(auditEntry, globalContext);
    }
    private boolean handlePolicyEnforcementDeleteEvent(OutboxEvent outboxEvent) throws IOException {
        GlobalContext globalContext = outboxEvent.getGlobalContext();
        PolicyEnforcementDeleteEvent policyEnforcementDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), PolicyEnforcementDeleteEvent.class);
        AuditEntry auditEntry =
                AuditEntry.builder()
                        .action(Action.DELETE)
                        .module(ModuleType.CE)
                        .newYaml(getYamlString(CreatePolicyEnforcementDTO.builder().policyEnforcement(policyEnforcementDeleteEvent.getPolicyEnforcement()).build()))
                        .timestamp(outboxEvent.getCreatedAt())
                        .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                        .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                        .insertId(outboxEvent.getId())
                        .build();
        return auditClientService.publishAudit(auditEntry, globalContext);
    }
}
