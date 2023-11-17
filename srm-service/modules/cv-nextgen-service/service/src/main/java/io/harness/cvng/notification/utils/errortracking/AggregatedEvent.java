/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.CET_MODULE_NAME;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_LINK_BEGIN;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_LINK_END;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_LINK_MIDDLE;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.EVENT_VERSION_LABEL;

import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.beans.errortracking.EventStatus;
import io.harness.cvng.beans.errortracking.SavedFilter;
import io.harness.cvng.beans.errortracking.Scorecard;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Builder
@ToString
@Slf4j
public class AggregatedEvent {
  public static final String NEW_EVENT_LABEL = "New Events ";
  public static final String CRITICAL_EVENT_LABEL = "Critical Events ";
  public static final String RESURFACED_EVENT_LABEL = "Resurfaced Events ";
  private static final String EVENT_STATUS_PARAM = "&eventStatus=";
  private static final String FILTER_ID_PARAM = "&filterId=";
  private static final String NEW_EVENT_NAME;
  private static final String CRITICAL_EVENT_NAME;
  private static final String RESURFACED_EVENT_NAME;
  private static final String EMAIL_HORIZONTAL_LINE_DIV =
      "<div style=\"display: inline; border-right: 1px solid #b0b1c3; height: 20px; margin: 0px 16px 0px 16px\"></div>";

  static {
    try {
      // Get the json property names from the ErrorTrackingEventStatus enums to use as the event status url parameters
      NEW_EVENT_NAME = ErrorTrackingEventStatus.class.getField(ErrorTrackingEventStatus.NEW_EVENTS.name())
                           .getAnnotation(JsonProperty.class)
                           .value();
      CRITICAL_EVENT_NAME = ErrorTrackingEventStatus.class.getField(ErrorTrackingEventStatus.CRITICAL_EVENTS.name())
                                .getAnnotation(JsonProperty.class)
                                .value();
      RESURFACED_EVENT_NAME = ErrorTrackingEventStatus.class.getField(ErrorTrackingEventStatus.RESURFACED_EVENTS.name())
                                  .getAnnotation(JsonProperty.class)
                                  .value();
    } catch (NoSuchFieldException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private String version;
  private String url;
  private int newCount;
  private int criticalCount;
  private int resurfacedCount;
  private List<ErrorTrackingEventStatus> errorTrackingEventStatus;
  private Long savedFilterId;

  public static List<AggregatedEvent> getAggregatedEvents(
      MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition codeErrorCondition,
      ErrorTrackingNotificationData errorTrackingNotificationData, String baseLinkUrl) {
    List<AggregatedEvent> aggregatedEvents = new ArrayList<>();

    String from = String.valueOf(errorTrackingNotificationData.getFrom().getTime() / 1000);
    String to = String.valueOf(errorTrackingNotificationData.getTo().getTime() / 1000);

    final List<Scorecard> scorecards = errorTrackingNotificationData.getScorecards();
    if (scorecards != null) {
      aggregatedEvents = getAggregatedEventsRecursive(
          codeErrorCondition, scorecards, errorTrackingNotificationData.getFilter(), baseLinkUrl, from, to);
    }
    return aggregatedEvents;
  }

  private static List<AggregatedEvent> getAggregatedEventsRecursive(
      MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition codeErrorCondition,
      List<Scorecard> scorecards, SavedFilter savedFilter, String baseLinkUrl, String from, String to) {
    List<AggregatedEvent> aggregatedEvents = new ArrayList<>();
    for (Scorecard scorecard : scorecards) {
      if (scorecard.getChildren() == null) {
        if (scorecard.getVersionIdentifier() != null
            && (scorecard.getNewHitCount() > 0 || scorecard.getCriticalHitCount() > 0
                || scorecard.getResurfacedHitCount() > 0)) {
          final String eventListUrlWithParameters = buildEventListUrlWithParameters(baseLinkUrl,
              scorecard.getAccountIdentifier(), scorecard.getOrganizationIdentifier(), scorecard.getProjectIdentifier(),
              scorecard.getEnvironmentIdentifier(), scorecard.getServiceIdentifier(), scorecard.getVersionIdentifier(),
              from, to);
          List<ErrorTrackingEventStatus> eventStatus = codeErrorCondition.getErrorTrackingEventStatus();
          if (savedFilter != null) {
            eventStatus = savedFilter.getStatuses()
                              .stream()
                              .filter(status -> status != EventStatus.ANY_EVENTS)
                              .map(AggregatedEvent::toErrorTrackingEventStatus)
                              .collect(Collectors.toList());
          }
          AggregatedEvent aggregatedEvent =
              AggregatedEvent.builder()
                  .version(scorecard.getVersionIdentifier())
                  .url(eventListUrlWithParameters)
                  .newCount(Optional.ofNullable(scorecard.getNewHitCount()).orElse(0))
                  .criticalCount(Optional.ofNullable(scorecard.getCriticalHitCount()).orElse(0))
                  .resurfacedCount(Optional.ofNullable(scorecard.getResurfacedHitCount()).orElse(0))
                  .errorTrackingEventStatus(eventStatus)
                  .savedFilterId(codeErrorCondition.getSavedFilterId())
                  .build();
          aggregatedEvents.add(aggregatedEvent);
        }
      } else {
        aggregatedEvents.addAll(getAggregatedEventsRecursive(
            codeErrorCondition, scorecard.getChildren(), savedFilter, baseLinkUrl, from, to));
      }
    }
    return aggregatedEvents;
  }

  private static ErrorTrackingEventStatus toErrorTrackingEventStatus(EventStatus eventStatus) {
    switch (eventStatus) {
      case NEW_EVENTS:
        return ErrorTrackingEventStatus.NEW_EVENTS;
      case CRITICAL_EVENTS:
        return ErrorTrackingEventStatus.CRITICAL_EVENTS;
      case RESURFACED_EVENTS:
        return ErrorTrackingEventStatus.RESURFACED_EVENTS;
      default:
        throw new IllegalArgumentException("Unsupported EventStatus: " + eventStatus);
    }
  }

  private static String buildEventListUrlWithParameters(String baseLinkUrl, String account, String org, String project,
      String env, String service, String deployment, String from, String to) {
    return String.format(
        "%s/account/%s/%s/orgs/%s/projects/%s/eventsummary/events?env=%s&service=%s&dep=%s&fromTimestamp=%s&toTimestamp=%s",
        baseLinkUrl, account, CET_MODULE_NAME, org, project, env, service, deployment, from, to);
  }

  public String toSlackString() {
    StringBuilder slack;
    if (savedFilterId == null) {
      slack = new StringBuilder(EVENT_VERSION_LABEL + "*" + version + "*\n");
    } else {
      String savedFilterLink = "<" + url + FILTER_ID_PARAM + savedFilterId + "|" + version + ">";
      slack = new StringBuilder(EVENT_VERSION_LABEL + "*" + savedFilterLink + "*\n");
    }
    slack.append(errorTrackingEventStatus.stream().map(this::getSlackString).collect(Collectors.joining("   |   ")));
    return slack.toString();
  }

  public String toEmailString() {
    StringBuilder email = new StringBuilder("<div style=\"margin-bottom: 16px\">");
    if (savedFilterId == null) {
      email.append("<span>" + EVENT_VERSION_LABEL + "<span style=\"font-weight: bold;\">" + version + "</span></span>");
    } else {
      String savedFilterLink =
          EMAIL_LINK_BEGIN + url + FILTER_ID_PARAM + savedFilterId + EMAIL_LINK_MIDDLE + version + EMAIL_LINK_END;
      email.append(
          "<span>" + EVENT_VERSION_LABEL + "<span style=\"font-weight: bold;\">" + savedFilterLink + "</span></span>");
    }
    email.append("<div style =\"margin-top: 4px;\">");
    email.append("<span>");
    email.append(errorTrackingEventStatus.stream()
                     .map(this::getEmailString)
                     .collect(Collectors.joining(EMAIL_HORIZONTAL_LINE_DIV)));
    email.append("</span>").append("</div>").append("</div>");
    return email.toString();
  }

  private String getSlackString(ErrorTrackingEventStatus status) {
    int count = getEventStatusCount(status);
    String textOnly = getEventStatusLabel(status) + "(" + count + ")";

    // if the event status count is 0 OR the savedFilterId is not null provide textOnly
    if (count == 0 || savedFilterId != null) {
      return textOnly;
    }
    return "<" + getEventStatusParameterUrl(status) + "|" + textOnly + ">";
  }

  private String getEmailString(ErrorTrackingEventStatus status) {
    int count = getEventStatusCount(status);
    String textOnly = getEventStatusLabel(status) + "(" + count + ")";

    // if the event status count is 0 OR the savedFilterId is not null provide textOnly
    if (count == 0 || savedFilterId != null) {
      return textOnly;
    }
    return EMAIL_LINK_BEGIN + getEventStatusParameterUrl(status) + EMAIL_LINK_MIDDLE + textOnly + EMAIL_LINK_END;
  }

  private String getEventStatusParameterUrl(ErrorTrackingEventStatus status) {
    switch (status) {
      case NEW_EVENTS:
        return url + EVENT_STATUS_PARAM + NEW_EVENT_NAME;
      case CRITICAL_EVENTS:
        return url + EVENT_STATUS_PARAM + CRITICAL_EVENT_NAME;
      case RESURFACED_EVENTS:
        return url + EVENT_STATUS_PARAM + RESURFACED_EVENT_NAME;
      default:
        log.warn("The Error Tracking Event Status type is not a status type which is handled for the url");
        return "";
    }
  }

  private String getEventStatusLabel(ErrorTrackingEventStatus status) {
    switch (status) {
      case NEW_EVENTS:
        return NEW_EVENT_LABEL;
      case CRITICAL_EVENTS:
        return CRITICAL_EVENT_LABEL;
      case RESURFACED_EVENTS:
        return RESURFACED_EVENT_LABEL;
      default:
        log.warn("The Error Tracking Event Status type is not a status type which is handled for the label");
        return "";
    }
  }

  private int getEventStatusCount(ErrorTrackingEventStatus status) {
    switch (status) {
      case NEW_EVENTS:
        return newCount;
      case CRITICAL_EVENTS:
        return criticalCount;
      case RESURFACED_EVENTS:
        return resurfacedCount;
      default:
        log.warn("The Error Tracking Event Status type is not a status type which is handled for the count");
        return 0;
    }
  }
}
