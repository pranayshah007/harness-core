/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.senders;

import io.harness.delegate.beans.NotificationProcessingResponse;

import com.github.dikhan.pagerduty.client.events.PagerDutyEventsClient;
import com.github.dikhan.pagerduty.client.events.domain.EventResult;
import com.github.dikhan.pagerduty.client.events.domain.LinkContext;
import com.github.dikhan.pagerduty.client.events.domain.Payload;
import com.github.dikhan.pagerduty.client.events.domain.TriggerIncident;
import com.github.dikhan.pagerduty.client.events.domain.TriggerIncident.TriggerIncidentBuilder;
import com.github.dikhan.pagerduty.client.events.exceptions.NotifyEventException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PagerDutySenderImpl {
  public NotificationProcessingResponse send(
      List<String> pagerDutyKeys, Payload payload, List<LinkContext> links, String notificationId) {
    List<Boolean> results = new ArrayList<>();
    PagerDutyEventsClient pagerDutyEventsClient = PagerDutyEventsClient.create();
    for (String pagerDutyKey : pagerDutyKeys) {
      TriggerIncident incident = TriggerIncidentBuilder.newBuilder(pagerDutyKey, payload).setLinks(links).build();
      try {
        EventResult result = pagerDutyEventsClient.trigger(incident);
        results.add(result != null && result.getErrors() == null);
      } catch (NotifyEventException e) {
        log.error("Unable to send PagerDuty incident for notification reference id {}", notificationId);
      }
    }
    return NotificationProcessingResponse.builder().result(results).build();
  }
}
