/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking.interfaces;

import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;

import java.util.List;
import java.util.stream.Collectors;

public interface DefinedFilterNotification {
  static String getEventStatus(List<ErrorTrackingEventStatus> eventStatus) {
    return eventStatus.stream().map(ErrorTrackingEventStatus::getDisplayName).collect(Collectors.joining(", "));
  }

  static String getNotificationEventTriggerList(List<ErrorTrackingEventType> eventTypes) {
    return eventTypes.stream().map(ErrorTrackingEventType::getDisplayName).collect(Collectors.joining(", "));
  }
}
