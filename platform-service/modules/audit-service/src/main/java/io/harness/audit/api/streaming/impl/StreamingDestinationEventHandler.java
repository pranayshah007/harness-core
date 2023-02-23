/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.api.streaming.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.events.StreamingDestinationCreateEvent.STREAMING_DESTINATION_CREATE_EVENT;
import static io.harness.audit.events.StreamingDestinationDeleteEvent.STREAMING_DESTINATION_DELETE_EVENT;
import static io.harness.audit.events.StreamingDestinationUpdateEvent.STREAMING_DESTINATION_UPDATE_EVENT;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.AuditEventDTO;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.audit.dto.StreamingDestinationRequest;
import io.harness.audit.events.StreamingDestinationCreateEvent;
import io.harness.audit.events.StreamingDestinationDeleteEvent;
import io.harness.audit.events.StreamingDestinationUpdateEvent;
import io.harness.audit.remote.AuditResource;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class StreamingDestinationEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final Producer eventProducer;
  private final AuditClientService auditClientService;
  private final AuditResource auditResource;

  @Inject
  public StreamingDestinationEventHandler(@Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer,
      AuditClientService auditClientService, AuditResource auditResource) {
    this.auditResource = auditResource;
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.eventProducer = eventProducer;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case STREAMING_DESTINATION_CREATE_EVENT:
          return handleStreamingDestinationCreateEvent(outboxEvent);
        case STREAMING_DESTINATION_UPDATE_EVENT:
          return handleStreamingDestinationUpdateEvent(outboxEvent);
        case STREAMING_DESTINATION_DELETE_EVENT:
          return handleStreamingDestinationDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Couldn't handle resource group outboxevent {}", outboxEvent, exception);
      return false;
    }
  }

  private boolean handleStreamingDestinationCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    StreamingDestinationCreateEvent streamingDestinationCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), StreamingDestinationCreateEvent.class);
    boolean publishedToRedis = publishEvent(streamingDestinationCreateEvent.getStreamingDestination(),
        EventsFrameworkMetadataConstants.CREATE_ACTION, streamingDestinationCreateEvent.getAccountIdentifier());
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(StreamingDestinationRequest.builder()
                                       .streamingDestination(streamingDestinationCreateEvent.getStreamingDestination())
                                       .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    AuditEventDTO auditEventDTO = auditClientService.getAuditEventDTO(auditEntry, globalContext, null);
    return publishedToRedis && auditResource.create(auditEventDTO).getData();
  }

  private boolean handleStreamingDestinationUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    StreamingDestinationUpdateEvent streamingDestinationUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), StreamingDestinationUpdateEvent.class);
    boolean publishedToRedis = publishEvent(streamingDestinationUpdateEvent.getNewStreamingDestination(),
        EventsFrameworkMetadataConstants.UPDATE_ACTION, streamingDestinationUpdateEvent.getAccountIdentifier());
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(
                getYamlString(StreamingDestinationRequest.builder()
                                  .streamingDestination(streamingDestinationUpdateEvent.getNewStreamingDestination())
                                  .build()))
            .oldYaml(
                getYamlString(StreamingDestinationRequest.builder()
                                  .streamingDestination(streamingDestinationUpdateEvent.getOldStreamingDestination())
                                  .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    AuditEventDTO auditEventDTO = auditClientService.getAuditEventDTO(auditEntry, globalContext, null);

    return publishedToRedis && auditResource.create(auditEventDTO).getData();
  }

  private boolean handleStreamingDestinationDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    StreamingDestinationDeleteEvent streamingDestinationDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), StreamingDestinationDeleteEvent.class);
    boolean publishedToRedis = publishEvent(streamingDestinationDeleteEvent.getStreamingDestination(),
        EventsFrameworkMetadataConstants.DELETE_ACTION, streamingDestinationDeleteEvent.getAccountIdentifier());
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .oldYaml(getYamlString(StreamingDestinationRequest.builder()
                                       .streamingDestination(streamingDestinationDeleteEvent.getStreamingDestination())
                                       .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    AuditEventDTO auditEventDTO = auditClientService.getAuditEventDTO(auditEntry, globalContext, null);
    return publishedToRedis && auditResource.create(auditEventDTO).getData();
  }

  private boolean publishEvent(StreamingDestinationDTO streamingDestination, String action, String accountIdentifier) {
    try {
      Map<String, String> metadataMap;
      metadataMap = ImmutableMap.of("accountId", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
          EventsFrameworkMetadataConstants.STREAMING_DESTINATION, EventsFrameworkMetadataConstants.ACTION, action);

      eventProducer.send(Message.newBuilder().putAllMetadata(metadataMap).setData(null).build());
      return true;
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework for action {} streaming destination {} with accountId {}",
          action, streamingDestination.getIdentifier(), accountIdentifier, e);
      return false;
    }
  }
}