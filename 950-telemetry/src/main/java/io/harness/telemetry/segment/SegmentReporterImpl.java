/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.telemetry.segment;

import static io.harness.TelemetryConstants.SEGMENT_DUMMY_ACCOUNT_PREFIX;
import static io.harness.TelemetryConstants.SYSTEM_USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.telemetry.Category;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;
import io.harness.telemetry.utils.TelemetryDataUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.segment.analytics.messages.GroupMessage;
import com.segment.analytics.messages.IdentifyMessage;
import com.segment.analytics.messages.TrackMessage;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.GTM)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class SegmentReporterImpl implements TelemetryReporter {
  private SegmentSender segmentSender;
  private static final String USER_ID_KEY = "userId";
  private static final String GROUP_ID_KEY = "groupId";
  private static final String CATEGORY_KEY = "category";
  private static final String TRACK = "track";
  private static final String IDENTITY = "identity";
  private static final String GROUP = "group";
  @Override
  public void sendTrackEvent(String eventName, HashMap<String, Object> properties,
      Map<Destination, Boolean> destinations, String category, TelemetryOption... telemetryOption) {
    sendTrackEvent(eventName, null, null, properties, destinations, category, telemetryOption);
  }

  @Override
  public void sendTrackEvent(String eventName, String identity, String accountId, HashMap<String, Object> properties,
      Map<Destination, Boolean> destinations, String category, TelemetryOption... telemetryOption) {
    if (!segmentSender.isEnabled()) {
      return;
    }
    if (identity == null) {
      identity = TelemetryDataUtils.readIdentityFromPrincipal();
    }
    if (accountId == null) {
      accountId = TelemetryDataUtils.readAccountIdFromPrincipal();
    }
    if (category == null) {
      category = Category.GLOBAL;
    }
    if (properties == null) {
      properties = new HashMap<>();
    }

    // check if analytics user can be used instead of system user
    if (identity == SYSTEM_USER && accountId != null) {
      identity = SEGMENT_DUMMY_ACCOUNT_PREFIX + accountId;
    }

    try {
      TrackMessage.Builder trackMessageBuilder = TrackMessage.builder(eventName).userId(identity);

      properties.put(USER_ID_KEY, identity);
      properties.put(GROUP_ID_KEY, accountId);
      properties.put(CATEGORY_KEY, category);
      verifyProperties(properties, TRACK, eventName);
      trackMessageBuilder.properties(properties);

      if (destinations != null) {
        destinations.forEach((k, v) -> trackMessageBuilder.enableIntegration(k.getDestinationName(), v));
      }
      segmentSender.enqueue(trackMessageBuilder);
    } catch (Exception e) {
      // protection from invalid data set in Builder causing runtime exception
      log.error("Build Track Event Failed", e);
    }
  }

  @Override
  public void sendIdentifyEvent(String identity, HashMap<String, Object> properties,
      Map<Destination, Boolean> destinations, TelemetryOption... telemetryOption) {
    if (!segmentSender.isEnabled()) {
      return;
    }
    try {
      IdentifyMessage.Builder identifyMessageBuilder = IdentifyMessage.builder().userId(identity);

      if (properties != null) {
        verifyProperties(properties, IDENTITY, null);
        identifyMessageBuilder.traits(properties);
      }
      if (destinations != null) {
        destinations.forEach((k, v) -> identifyMessageBuilder.enableIntegration(k.getDestinationName(), v));
      }
      segmentSender.enqueue(identifyMessageBuilder);
    } catch (Exception e) {
      // protection from invalid data set in Builder causing runtime exception
      log.error("Build Identify Event Failed", e);
    }
  }

  @Override
  public void sendGroupEvent(String accountId, HashMap<String, Object> properties,
      Map<Destination, Boolean> destinations, TelemetryOption... telemetryOption) {
    sendGroupEvent(accountId, null, properties, destinations, telemetryOption);
  }

  @Override
  public void sendGroupEvent(String accountId, String identity, HashMap<String, Object> properties,
      Map<Destination, Boolean> destinations, TelemetryOption... telemetryOption) {
    sendGroupEvent(accountId, identity, properties, destinations, null, telemetryOption);
  }

  @Override
  public void sendGroupEvent(String accountId, String identity, HashMap<String, Object> properties,
      Map<Destination, Boolean> destinations, Date timestamp, TelemetryOption... telemetryOption) {
    if (!segmentSender.isEnabled()) {
      return;
    }
    if (identity == null) {
      identity = TelemetryDataUtils.readIdentityFromPrincipal();
    }
    try {
      GroupMessage.Builder groupMessageBuilder = GroupMessage.builder(accountId).userId(identity);

      if (properties != null) {
        verifyProperties(properties, GROUP, null);
        groupMessageBuilder.traits(properties);
      }
      if (destinations != null) {
        destinations.forEach((k, v) -> groupMessageBuilder.enableIntegration(k.getDestinationName(), v));
      }
      if (timestamp != null) {
        groupMessageBuilder.timestamp(timestamp);
      }
      segmentSender.enqueue(groupMessageBuilder);
    } catch (Exception e) {
      // protection from invalid data set in Builder causing runtime exception
      log.error("Build Group Event Failed", e);
    }
  }

  @Override
  public void flush() {
    segmentSender.flushDataInQueue();
  }

  private void verifyProperties(HashMap<String, Object> properties, String eventType, String eventName) {
    ArrayList<String> nullProperties = new ArrayList<>();
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      if (entry.getValue() == null) {
        properties.put(entry.getKey(), "null");
        nullProperties.add(entry.getKey());
      }
    }

    if (!nullProperties.isEmpty()) {
      if (eventType == TRACK) {
        log.warn("Event type {}, event name {}:  detect null values in event properties with keys: {}", eventType,
            eventName, nullProperties);
      } else {
        log.warn("Event type {}: detect null values in event properties with keys: {}", eventType, nullProperties);
      }
    }
  }
}
