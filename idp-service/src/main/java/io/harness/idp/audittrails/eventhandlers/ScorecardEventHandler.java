/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.scorecard.scorecardchecks.events.scorecards.ScorecardCreateEvent.SCORECARD_CREATED;
import static io.harness.idp.scorecard.scorecardchecks.events.scorecards.ScorecardDeleteEvent.SCORECARD_DELETED;
import static io.harness.idp.scorecard.scorecardchecks.events.scorecards.ScorecardUpdateEvent.SCORECARD_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrails.eventhandlers.dtos.ScorecardsDTO;
import io.harness.idp.scorecard.scorecardchecks.events.scorecards.ScorecardCreateEvent;
import io.harness.idp.scorecard.scorecardchecks.events.scorecards.ScorecardDeleteEvent;
import io.harness.idp.scorecard.scorecardchecks.events.scorecards.ScorecardUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScorecardEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public ScorecardEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case SCORECARD_CREATED:
          return handleScorecardCreateEvent(outboxEvent);
        case SCORECARD_UPDATED:
          return handleScorecardUpdateEvent(outboxEvent);
        case SCORECARD_DELETED:
          return handleScorecardDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleScorecardCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    ScorecardCreateEvent scorecardCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ScorecardCreateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.IDP)
            .newYaml(NGYamlUtils.getYamlString(
                ScorecardsDTO.builder().scorecardDetailsResponse(scorecardCreateEvent.getNewScorecardDetails()).build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleScorecardUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    ScorecardUpdateEvent scorecardUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ScorecardUpdateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.IDP)
                                .newYaml(NGYamlUtils.getYamlString(
                                    ScorecardsDTO.builder()
                                        .scorecardDetailsResponse(scorecardUpdateEvent.getNewScorecardDetailsResponse())
                                        .build(),
                                    objectMapper))
                                .oldYaml(NGYamlUtils.getYamlString(
                                    ScorecardsDTO.builder()
                                        .scorecardDetailsResponse(scorecardUpdateEvent.getOldScorecardDetailsResponse())
                                        .build(),
                                    objectMapper))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleScorecardDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    ScorecardDeleteEvent scorecardDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ScorecardDeleteEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.IDP)
                                .oldYaml(NGYamlUtils.getYamlString(
                                    ScorecardsDTO.builder()
                                        .scorecardDetailsResponse(scorecardDeleteEvent.getOldScorecardDetailsResponse())
                                        .build(),
                                    objectMapper))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
