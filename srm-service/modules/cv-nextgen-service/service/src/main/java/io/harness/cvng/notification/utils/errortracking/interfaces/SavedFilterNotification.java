/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking.interfaces;

import io.harness.cvng.beans.errortracking.CriticalEventType;
import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.beans.errortracking.EventStatus;

import java.util.stream.Collectors;

public interface SavedFilterNotification {
  String SEARCH_TERM = ", and search term (";

  static String getEventStatus(ErrorTrackingNotificationData errorTrackingNotificationData) {
    String changeEventStatusString = "";
    if (errorTrackingNotificationData.getFilter() != null) {
      changeEventStatusString = errorTrackingNotificationData.getFilter()
                                    .getStatuses()
                                    .stream()
                                    .map(EventStatus::getDisplayName)
                                    .collect(Collectors.joining(", "));
    }
    return changeEventStatusString;
  }

  static String getNotificationEventTriggerList(ErrorTrackingNotificationData errorTrackingNotificationData) {
    String changeEventTypeString = "";
    if (errorTrackingNotificationData.getFilter() != null) {
      changeEventTypeString = errorTrackingNotificationData.getFilter()
                                  .getEventTypes()
                                  .stream()
                                  .map(CriticalEventType::getDisplayName)
                                  .collect(Collectors.joining(", "))
          + SEARCH_TERM + errorTrackingNotificationData.getFilter().getSearchTerm() + ")";
    }
    return changeEventTypeString;
  }
}
