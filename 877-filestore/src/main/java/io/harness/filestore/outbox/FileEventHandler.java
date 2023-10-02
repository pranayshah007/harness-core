/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.outbox;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.FILE_ENTITY;
import static io.harness.filestore.events.FileCreateEvent.FILE_CREATED_EVENT;
import static io.harness.filestore.events.FileDeleteEvent.FILE_DELETED_EVENT;
import static io.harness.filestore.events.FileForceDeleteEvent.FILE_FORCE_DELETED_EVENT;
import static io.harness.filestore.events.FileUpdateEvent.FILE_UPDATED_EVENT;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import static java.lang.String.format;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidArgumentsException;
import io.harness.filestore.events.FileCreateEvent;
import io.harness.filestore.events.FileDeleteEvent;
import io.harness.filestore.events.FileUpdateEvent;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.filestore.dto.FileStoreRequest;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import io.serializer.HObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@Slf4j
@OwnedBy(CDP)
public class FileEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  private final Producer eventProducer;

  @Inject
  public FileEventHandler(
      AuditClientService auditClientService, @Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer) {
    this.objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
    this.eventProducer = eventProducer;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case FILE_CREATED_EVENT:
          return handleFileCreateEvent(outboxEvent);
        case FILE_UPDATED_EVENT:
          return handleFileUpdateEvent(outboxEvent);
        case FILE_DELETED_EVENT:
          return handleFileDeleteEvent(outboxEvent, false);
        case FILE_FORCE_DELETED_EVENT:
          return handleFileDeleteEvent(outboxEvent, true);
        default:
          throw new InvalidArgumentsException(format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error(format("Failed to handle %s event", outboxEvent.getEventType()), exception);
      return false;
    }
  }

  private boolean handleFileCreateEvent(OutboxEvent outboxEvent) throws IOException {
    FileCreateEvent fileCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), FileCreateEvent.class);

    boolean publishedToRedis = publishEvent(outboxEvent, EventsFrameworkMetadataConstants.CREATE_ACTION);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(NGYamlUtils.getYamlString(FileStoreRequest.builder().file(fileCreateEvent.getFile()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && publishAudit(auditEntry, outboxEvent);
  }

  private boolean handleFileUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    FileUpdateEvent fileUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), FileUpdateEvent.class);

    boolean publishedToRedis = publishEvent(outboxEvent, EventsFrameworkMetadataConstants.UPDATE_ACTION);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(NGYamlUtils.getYamlString(FileStoreRequest.builder().file(fileUpdateEvent.getNewFile()).build()))
            .oldYaml(NGYamlUtils.getYamlString(FileStoreRequest.builder().file(fileUpdateEvent.getOldFile()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && publishAudit(auditEntry, outboxEvent);
  }

  private boolean handleFileDeleteEvent(OutboxEvent outboxEvent, boolean forceDelete) throws IOException {
    FileDeleteEvent fileDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), FileDeleteEvent.class);
    boolean publishedToRedis = publishEvent(outboxEvent, EventsFrameworkMetadataConstants.DELETE_ACTION);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(forceDelete ? Action.FORCE_DELETE : Action.DELETE)
            .module(ModuleType.CORE)
            .oldYaml(NGYamlUtils.getYamlString(FileStoreRequest.builder().file(fileDeleteEvent.getFile()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && publishAudit(auditEntry, outboxEvent);
  }

  private boolean publishAudit(AuditEntry auditEntry, OutboxEvent outboxEvent) {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  boolean publishEvent(OutboxEvent outboxEvent, String action) {
    EntityChangeDTO.Builder entityBuilder =
        EntityChangeDTO.newBuilder().setIdentifier(StringValue.of(outboxEvent.getResource().getIdentifier()));

    String accountIdentifier;
    if (outboxEvent.getResourceScope() instanceof AccountScope) {
      accountIdentifier = ((AccountScope) outboxEvent.getResourceScope()).getAccountIdentifier();
      entityBuilder.setAccountIdentifier(StringValue.of(accountIdentifier));
    } else if (outboxEvent.getResourceScope() instanceof OrgScope) {
      OrgScope resourceScope = (OrgScope) outboxEvent.getResourceScope();
      accountIdentifier = resourceScope.getAccountIdentifier();
      entityBuilder.setAccountIdentifier(StringValue.of(accountIdentifier));
      entityBuilder.setOrgIdentifier(StringValue.of(resourceScope.getOrgIdentifier()));
    } else {
      ProjectScope resourceScope = (ProjectScope) outboxEvent.getResourceScope();
      accountIdentifier = resourceScope.getAccountIdentifier();
      entityBuilder.setAccountIdentifier(StringValue.of(accountIdentifier));
      entityBuilder.setOrgIdentifier(StringValue.of(resourceScope.getOrgIdentifier()));
      entityBuilder.setProjectIdentifier(StringValue.of(resourceScope.getProjectIdentifier()));
    }
    return publishEvent(action, accountIdentifier, outboxEvent.getResource().getIdentifier(), entityBuilder.build());
  }

  boolean publishEvent(String action, String accountIdentifier, String identifier, EntityChangeDTO entityChangeDTO) {
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier, ENTITY_TYPE, FILE_ENTITY, ACTION, action))
              .setData(entityChangeDTO.toByteString())
              .build());
      return true;
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework fileIdentifier: " + identifier, e);
      return false;
    }
  }
}
