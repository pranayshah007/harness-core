/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.events;

import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.authorization.AuthorizationServiceHeader.TEMPLATE_SERVICE;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.beans.custom.template.TemplateEventData;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateReferenceHelper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import io.serializer.HObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class TemplateOutboxEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  private final Producer eventProducer;
  private final TemplateReferenceHelper templateReferenceHelper;

  @Inject
  public TemplateOutboxEventHandler(AuditClientService auditClientService,
      @Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer,
      TemplateReferenceHelper templateReferenceHelper) {
    this.templateReferenceHelper = templateReferenceHelper;
    this.objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
    this.eventProducer = eventProducer;
  }

  private boolean handleTemplateCreateEvent(OutboxEvent outboxEvent) throws IOException {
    TemplateCreateEvent templateCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), TemplateCreateEvent.class);

    boolean publishedToRedis = publishEvent(
        outboxEvent, EventsFrameworkMetadataConstants.CREATE_ACTION, templateCreateEvent.getTemplateEntity());
    TemplateEventData templateEventData = new TemplateEventData(templateCreateEvent.getComments(), null);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.TEMPLATESERVICE)
                                .newYaml(templateCreateEvent.getTemplateEntity().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .auditEventData(templateEventData)
                                .insertId(outboxEvent.getId())
                                .build();
    return publishedToRedis && publishAudit(auditEntry, outboxEvent);
  }

  private boolean handleTemplateUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    TemplateUpdateEvent templateUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), TemplateUpdateEvent.class);

    boolean publishedToRedis = false;
    if (templateUpdateEvent.getTemplateUpdateEventType() == TemplateUpdateEventType.TEMPLATE_CHANGE_SCOPE_EVENT) {
      TemplateEntity oldTemplate = templateUpdateEvent.getOldTemplateEntity();
      EntityChangeDTO.Builder entityBuilder =
          EntityChangeDTO.newBuilder()
              .setIdentifier(StringValue.of(outboxEvent.getResource().getIdentifier()))
              .setAccountIdentifier(StringValue.of(oldTemplate.getAccountIdentifier()));

      if (oldTemplate.getOrgIdentifier() != null) {
        entityBuilder.setOrgIdentifier(StringValue.of(oldTemplate.getOrgIdentifier()));
      }

      if (oldTemplate.getProjectIdentifier() != null) {
        entityBuilder.setOrgIdentifier(StringValue.of(oldTemplate.getProjectIdentifier()));
      }

      publishedToRedis = publishEvent(EventsFrameworkMetadataConstants.DELETE_ACTION,
          oldTemplate.getAccountIdentifier(), outboxEvent.getResource().getIdentifier(), entityBuilder.build());

      publishedToRedis =
          publishedToRedis && publishEvent(outboxEvent, EventsFrameworkMetadataConstants.CREATE_ACTION, oldTemplate);
    } else {
      publishedToRedis = publishEvent(
          outboxEvent, EventsFrameworkMetadataConstants.UPDATE_ACTION, templateUpdateEvent.getNewTemplateEntity());
    }

    TemplateEventData templateEventData = new TemplateEventData(
        templateUpdateEvent.getComments(), templateUpdateEvent.getTemplateUpdateEventType().fetchYamlType());

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.TEMPLATESERVICE)
                                .oldYaml(templateUpdateEvent.getOldTemplateEntity().getYaml())
                                .newYaml(templateUpdateEvent.getNewTemplateEntity().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .auditEventData(templateEventData)
                                .insertId(outboxEvent.getId())
                                .build();
    return publishedToRedis && publishAudit(auditEntry, outboxEvent);
  }

  private boolean handleTemplateDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    TemplateDeleteEvent templateDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), TemplateDeleteEvent.class);
    boolean publishedToRedis = publishEvent(
        outboxEvent, EventsFrameworkMetadataConstants.DELETE_ACTION, templateDeleteEvent.getTemplateEntity());
    templateReferenceHelper.deleteTemplateReferences(templateDeleteEvent.getTemplateEntity());
    TemplateEventData templateEventData = new TemplateEventData(templateDeleteEvent.getComments(), null);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.TEMPLATESERVICE)
                                .oldYaml(templateDeleteEvent.getTemplateEntity().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .auditEventData(templateEventData)
                                .insertId(outboxEvent.getId())
                                .build();
    return publishedToRedis && publishAudit(auditEntry, outboxEvent);
  }
  private boolean handleTemplateForceDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    TemplateForceDeleteEvent templateForceDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), TemplateForceDeleteEvent.class);
    boolean publishedToRedis = publishEvent(
        outboxEvent, EventsFrameworkMetadataConstants.DELETE_ACTION, templateForceDeleteEvent.getTemplateEntity());
    templateReferenceHelper.deleteTemplateReferences(templateForceDeleteEvent.getTemplateEntity());
    TemplateEventData templateEventData = new TemplateEventData(templateForceDeleteEvent.getComments(), null);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.FORCE_DELETE)
                                .module(ModuleType.TEMPLATESERVICE)
                                .oldYaml(templateForceDeleteEvent.getTemplateEntity().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .auditEventData(templateEventData)
                                .insertId(outboxEvent.getId())
                                .build();
    return publishedToRedis && publishAudit(auditEntry, outboxEvent);
  }

  private boolean publishAudit(AuditEntry auditEntry, OutboxEvent outboxEvent) {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(TEMPLATE_SERVICE.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case TemplateOutboxEvents.TEMPLATE_VERSION_CREATED:
          return handleTemplateCreateEvent(outboxEvent);
        case TemplateOutboxEvents.TEMPLATE_VERSION_UPDATED:
          return handleTemplateUpdateEvent(outboxEvent);
        case TemplateOutboxEvents.TEMPLATE_VERSION_DELETED:
          return handleTemplateDeleteEvent(outboxEvent);
        case TemplateOutboxEvents.TEMPLATE_VERSION_FORCE_DELETED:
          return handleTemplateForceDeleteEvent(outboxEvent);
        default:
          return false;
      }
    } catch (IOException ex) {
      return false;
    }
  }

  boolean publishEvent(OutboxEvent outboxEvent, String action, TemplateEntity templateEntity) {
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

    entityBuilder.putMetadata("versionLabel", templateEntity.getVersionLabel());
    entityBuilder.putMetadata("templateType", templateEntity.getTemplateEntityType().toString());
    if (templateEntity.isStableTemplate()) {
      entityBuilder.putMetadata("isStable", "true");
    }
    return publishEvent(action, accountIdentifier, outboxEvent.getResource().getIdentifier(), entityBuilder.build());
  }

  boolean publishEvent(String action, String accountIdentifier, String identifier, EntityChangeDTO entityChangeDTO) {
    try {
      eventProducer.send(Message.newBuilder()
                             .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                                 EventsFrameworkMetadataConstants.ENTITY_TYPE, "TEMPLATE",
                                 EventsFrameworkMetadataConstants.ACTION, action))
                             .setData(entityChangeDTO.toByteString())
                             .build());
      return true;
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework templateIdentifier: " + identifier, e);
      return false;
    }
  }
}
