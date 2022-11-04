package io.harness.ccm.audittrails.eventhandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.ccm.audittrails.events.PolicyCreateEvent;
import static io.harness.ccm.audittrails.events.PolicyCreateEvent.POLICY_CREATED;
import io.harness.ccm.audittrails.events.PolicyDeleteEvent;
import static io.harness.ccm.audittrails.events.PolicyDeleteEvent.POLICY_DELETED;
import io.harness.ccm.audittrails.events.PolicyPackCreateEvent;
import static io.harness.ccm.audittrails.events.PolicyPackCreateEvent.POLICY_PACK_CREATED;
import io.harness.ccm.audittrails.events.PolicyPackDeleteEvent;
import static io.harness.ccm.audittrails.events.PolicyPackDeleteEvent.POLICY_PACK_DELETED;
import io.harness.ccm.audittrails.events.PolicyPackEvent;
import io.harness.ccm.audittrails.events.PolicyPackUpdateEvent;
import static io.harness.ccm.audittrails.events.PolicyPackUpdateEvent.POLICY_PACK_UPDATED;
import io.harness.ccm.audittrails.events.PolicyUpdateEvent;
import static io.harness.ccm.audittrails.events.PolicyUpdateEvent.POLICY_UPDATED;
import io.harness.ccm.views.dto.CreatePolicyDTO;
import io.harness.ccm.views.dto.CreatePolicyPackDTO;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j

public class PolicyPackEventHandler implements OutboxEventHandler {
    private final ObjectMapper objectMapper;
    private final AuditClientService auditClientService;

    @Inject
    public PolicyPackEventHandler(AuditClientService auditClientService) {
        this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
        this.auditClientService = auditClientService;
    }

    @Override
    public boolean handle(OutboxEvent outboxEvent) {
        try {
            switch (outboxEvent.getEventType()) {
                case POLICY_PACK_CREATED:
                    return handlePolicyPackCreateEvent(outboxEvent);
                case POLICY_PACK_UPDATED:
                    return handlePolicyPackUpdateEvent(outboxEvent);
                case POLICY_PACK_DELETED:
                    return handlePolicyPackDeleteEvent(outboxEvent);

                default:
                    throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
            }
        } catch (IOException exception) {
            log.error(exception.toString());
            return false;
        }
    }

    private boolean handlePolicyPackCreateEvent(OutboxEvent outboxEvent) throws IOException {
        GlobalContext globalContext = outboxEvent.getGlobalContext();
        PolicyPackCreateEvent policyPackCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), PolicyPackCreateEvent.class);
        AuditEntry auditEntry =
                AuditEntry.builder()
                        .action(Action.CREATE)
                        .module(ModuleType.CE)
                        .newYaml(getYamlString(CreatePolicyPackDTO.builder().policyPack(policyPackCreateEvent.getPolicyPack()).build()))
                        .timestamp(outboxEvent.getCreatedAt())
                        .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                        .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                        .insertId(outboxEvent.getId())
                        .build();
        return auditClientService.publishAudit(auditEntry, globalContext);
    }
    private boolean handlePolicyPackUpdateEvent(OutboxEvent outboxEvent) throws IOException {
        GlobalContext globalContext = outboxEvent.getGlobalContext();
        PolicyPackUpdateEvent policyPackUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), PolicyPackUpdateEvent.class);
        AuditEntry auditEntry =
                AuditEntry.builder()
                        .action(Action.UPDATE)
                        .module(ModuleType.CE)
                        .newYaml(getYamlString(CreatePolicyPackDTO.builder().policyPack(policyPackUpdateEvent.getPolicyPack()).build()))
                        .timestamp(outboxEvent.getCreatedAt())
                        .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                        .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                        .insertId(outboxEvent.getId())
                        .build();
        return auditClientService.publishAudit(auditEntry, globalContext);
    }
    private boolean handlePolicyPackDeleteEvent(OutboxEvent outboxEvent) throws IOException {
        GlobalContext globalContext = outboxEvent.getGlobalContext();
        PolicyPackDeleteEvent policyPackDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), PolicyPackDeleteEvent.class);
        AuditEntry auditEntry =
                AuditEntry.builder()
                        .action(Action.DELETE)
                        .module(ModuleType.CE)
                        .newYaml(getYamlString(CreatePolicyPackDTO.builder().policyPack(policyPackDeleteEvent.getPolicyPack()).build()))
                        .timestamp(outboxEvent.getCreatedAt())
                        .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                        .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                        .insertId(outboxEvent.getId())
                        .build();
        return auditClientService.publishAudit(auditEntry, globalContext);
    }
}
