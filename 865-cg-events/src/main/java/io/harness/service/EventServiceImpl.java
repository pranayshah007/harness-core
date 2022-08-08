/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CgEventConfig;
import io.harness.beans.Event;
import io.harness.beans.Event.EventCreatorSource;
import io.harness.beans.Event.EventsKeys;
import io.harness.beans.EventConfig;
import io.harness.beans.EventDetail;
import io.harness.beans.EventPayload;
import io.harness.beans.EventStatus;
import io.harness.beans.EventType;
import io.harness.beans.event.TestEventPayload;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@OwnedBy(CDC)
@Singleton
public class EventServiceImpl implements EventService {
  private static final int MAX_RETRY_ALLOWED = 1;
  @Inject EventHelper eventHelper;
  @Inject private HPersistence hPersistence;

  @Inject private EventConfigService eventConfigService;

  @Override
  public void deliverEvent(String accountId, String appId, EventPayload payload) {
    List<CgEventConfig> eventConfigList = eventConfigService.listAllEventsConfig(accountId, appId);
    if (!isEmpty(eventConfigList)) {
      eventConfigList.stream()
          .filter(CgEventConfig::isEnabled)
          .forEach(config -> deliverEvent(accountId, appId, config, payload));
    }
  }

  @Override
  public void deliverEvent(String accountId, String appId, EventConfig config, EventPayload payload) {
    if (config == null) {
      log.warn("Event config does not exist hence ignoring! Details accountId - {}; appId - {}", accountId, appId);
      return;
    }
    CgEventConfig eventConfig = (CgEventConfig) config;
    String eventConfigId = eventConfig.getUuid();
    if (payload == null) {
      log.warn("Event payload is null hence ignoring! Details accountId - {}; appId - {}; configId - {}", accountId,
          appId, eventConfigId);
      return;
    }
    if (payload.getData() == null || StringUtils.isBlank(payload.getEventType())) {
      log.warn(
          "Event payload has no data or no event type hence ignoring! Details accountId - {}; appId - {}; configId - {}",
          accountId, appId, eventConfigId);
      return;
    }

    // Set the eventId in payload so that the customer can de-dup events on their end if we redeliver them in the
    // future.
    payload.setId(UUID.randomUUID().toString());
    Event event = Event.builder()
                      .eventConfigId(eventConfigId)
                      .status(EventStatus.QUEUED)
                      .maxRetryAllowed(MAX_RETRY_ALLOWED)
                      .accountId(accountId)
                      .appId(appId)
                      .payload(payload)
                      .method("WEBHOOK")
                      .source(EventCreatorSource.CD)
                      .build();

    // We will skip sending all events if the event type is disabled or not test event
    // Customer should be able to send test event when it is disabled as well. For example they are building the server
    // to receive web hooks. They want to make sure that we do not send all events till they are ready. This will ensure
    // that they can manually request us to send test event
    if (!EventType.TEST.name().equals(payload.getEventType())) {
      if (!eventConfig.isEnabled()) {
        log.warn("Event config is not enabled hence ignoring! Details accountId - {}; appId - {}; configId - {}",
            accountId, appId, eventConfigId);
        return;
      }
      if (!eventHelper.canSendEvent(eventConfig, event, appId)) {
        log.warn(
            "Event cannot be sent for this event config as rules not applicable! Details accountId - {}; appId - {}; configId - {}",
            accountId, appId, eventConfigId);
        return;
      }
    }
    // Put this in the database and mark as queued
    hPersistence.save(event);
  }

  @Override
  public void deliverEvent(String accountId, String appId, String eventConfigId, EventPayload payload) {
    CgEventConfig eventConfig = eventConfigService.getEventsConfig(accountId, appId, eventConfigId);
    deliverEvent(accountId, appId, eventConfig, payload);
  }

  @Override
  public void sendTestEvent(String accountId, String appId, String eventConfigId) {
    EventPayload eventPayload =
        EventPayload.builder().version("v1").eventType(EventType.TEST.name()).data(new TestEventPayload()).build();
    deliverEvent(accountId, appId, eventConfigId, eventPayload);
  }

  @Override
  public void updateEventStatus(String eventId, EventStatus eventStatus, EventDetail detail) {
    UpdateOperations<Event> updateOperations = hPersistence.createUpdateOperations(Event.class)
                                                   .set(EventsKeys.status, eventStatus)
                                                   .set(EventsKeys.details, detail);

    Query<Event> query = hPersistence.createQuery(Event.class).filter(EventsKeys.uuid, eventId);

    hPersistence.update(query, updateOperations);
  }

  @Override
  public void pruneByApplication(String appId) {
    hPersistence.deleteOnServer(hPersistence.createQuery(Event.class).filter(EventsKeys.appId, appId));
  }
}
