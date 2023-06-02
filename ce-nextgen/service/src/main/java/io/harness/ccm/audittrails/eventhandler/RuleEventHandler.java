/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.eventhandler;

import static io.harness.ccm.audittrails.events.RuleCreateEvent.RULE_CREATED;
import static io.harness.ccm.audittrails.events.RuleDeleteEvent.RULE_DELETED;
import static io.harness.ccm.audittrails.events.RuleUpdateEvent.RULE_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.ccm.audittrails.events.RuleCreateEvent;
import io.harness.ccm.audittrails.events.RuleDeleteEvent;
import io.harness.ccm.audittrails.events.RuleUpdateEvent;
import io.harness.ccm.audittrails.yamlDTOs.RuleDTO;
import io.harness.ccm.views.entities.Rule;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j

public class RuleEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final Producer eventProducer;
  private final AuditClientService auditClientService;

  @Inject
  public RuleEventHandler(
      @Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer, AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.eventProducer = eventProducer;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case RULE_CREATED:
          return handlePolicyCreateEvent(outboxEvent);
        case RULE_UPDATED:
          return handleRuleUpdateEvent(outboxEvent);
        case RULE_DELETED:
          return handleRuleDeleteEvent(outboxEvent);

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
    RuleCreateEvent ruleCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), RuleCreateEvent.class);
    boolean publishedToRedis = publishEvent(ruleCreateEvent.getRule(), EventsFrameworkMetadataConstants.CREATE_ACTION);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.CE)
                                .newYaml(getYamlString(RuleDTO.builder().rule(ruleCreateEvent.getRule()).build()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handleRuleUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RuleUpdateEvent policyUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), RuleUpdateEvent.class);
    boolean publishedToRedis =
        publishEvent(policyUpdateEvent.getRule(), EventsFrameworkMetadataConstants.UPDATE_ACTION);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.CE)
                                .newYaml(getYamlString(RuleDTO.builder().rule(policyUpdateEvent.getRule()).build()))
                                .oldYaml(getYamlString(RuleDTO.builder().rule(policyUpdateEvent.getOldRule()).build()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handleRuleDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RuleDeleteEvent policyDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), RuleDeleteEvent.class);
    boolean publishedToRedis =
        publishEvent(policyDeleteEvent.getRule(), EventsFrameworkMetadataConstants.DELETE_ACTION);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.CE)
                                .oldYaml(getYamlString(RuleDTO.builder().rule(policyDeleteEvent.getRule()).build()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean publishEvent(Rule rule, String action) {
    try {
      String eventId = eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(
                  ImmutableMap.of("accountId", rule.getAccountId(), EventsFrameworkMetadataConstants.ENTITY_TYPE,
                      EventsFrameworkMetadataConstants.CCM_RULE, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(getRulePayload(rule))
              .build());
      log.info("Produced event id:[{}] for Rule :[{}], action:[{}]", eventId, rule, action);
      return true;
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework for Rule identifier {}", rule.getUuid(), e);
      return false;
    }
  }

  private ByteString getRulePayload(Rule rule) {
    EntityChangeDTO.Builder builder = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(rule.getAccountId()))
                                          .setIdentifier(StringValue.of(rule.getUuid()));
    return builder.build().toByteString();
  }
}
