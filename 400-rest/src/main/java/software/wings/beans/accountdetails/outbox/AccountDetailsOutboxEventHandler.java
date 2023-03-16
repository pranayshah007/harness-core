/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.accountdetails.outbox;

import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static software.wings.beans.accountdetails.AccountDetailsConstants.CROSS_GENERATION_ACCESS_UPDATED;
import static software.wings.beans.accountdetails.AccountDetailsConstants.DEFAULT_EXPERIENCE_UPDATED;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import software.wings.beans.accountdetails.events.AccountDetailsCrossGenerationAccessUpdateEvent;
import software.wings.beans.accountdetails.events.AccountDetailsDefaultExperienceUpdateEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountDetailsOutboxEventHandler implements OutboxEventHandler {
  private final AuditClientService auditClientService;
  private final ObjectMapper objectMapper;

  @Inject
  AccountDetailsOutboxEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
    this.objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    try {
      switch (outboxEvent.getEventType()) {
        case CROSS_GENERATION_ACCESS_UPDATED:
          return handleCrossGenerationAccessUpdateEvent(outboxEvent, globalContext);
        case DEFAULT_EXPERIENCE_UPDATED:
          return handleDefaultExperienceUpdateEvent(outboxEvent, globalContext);
        default:
          log.error(outboxEvent.getEventType() + " event is unidentified and not handled");
          return false;
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event with exception: ", exception);
      return false;
    }
  }

  private boolean handleCrossGenerationAccessUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    AccountDetailsCrossGenerationAccessUpdateEvent accountDetailsCrossGenerationAccessUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), AccountDetailsCrossGenerationAccessUpdateEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.UPDATE,
        getYamlString(accountDetailsCrossGenerationAccessUpdateEvent.getOldCrossGenerationAccessYamlDTO()),
        getYamlString(accountDetailsCrossGenerationAccessUpdateEvent.getNewCrossGenerationAccessYamlDTO()));
    log.info(
        "NG Account Details: for account {} and outboxEventId {} publishing the audit for AccountDetailsCrossGenerationAccessUpdateEvent",
        accountDetailsCrossGenerationAccessUpdateEvent.getAccountIdentifier(), outboxEvent.getId());
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleDefaultExperienceUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    AccountDetailsDefaultExperienceUpdateEvent accountDetailsDefaultExperienceUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), AccountDetailsDefaultExperienceUpdateEvent.class);
    AuditEntry auditEntry = getAuditEntry(outboxEvent, Action.UPDATE,
        getYamlString(accountDetailsDefaultExperienceUpdateEvent.getOldDefaultExperienceYamlDTO()),
        getYamlString(accountDetailsDefaultExperienceUpdateEvent.getNewDefaultExperienceYamlDTO()));
    log.info(
        "NG Account Details: for account {} and outboxEventId {} publishing the audit for AccountDetailsDefaultExperienceUpdateEvent",
        accountDetailsDefaultExperienceUpdateEvent.getAccountIdentifier(), outboxEvent.getId());
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private AuditEntry getAuditEntry(OutboxEvent outboxEvent, Action action, String oldYaml, String newYaml) {
    return AuditEntry.builder()
        .action(action)
        .module(ModuleType.CORE)
        .oldYaml(oldYaml)
        .newYaml(newYaml)
        .timestamp(outboxEvent.getCreatedAt())
        .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
        .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
        .insertId(outboxEvent.getId())
        .build();
  }
}
