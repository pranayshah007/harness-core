/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils;

import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.ARC_SCREEN_URL;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_EVENT_DETAILS_BUTTON;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_EVENT_DETAILS_BUTTON_VALUE;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_HORIZONTAL_LINE_DIV;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_LINK_BEGIN;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_LINK_END;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_LINK_MIDDLE;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_SAVED_SEARCH_FILTER_SECTION;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_SAVED_SEARCH_FILTER_SECTION_VALUE;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.ET_MONITORED_SERVICE_URL_FORMAT;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EVENT_STATUS;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.NOTIFICATION_EVENT_TRIGGER_LIST;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.SLACK_EVENT_DETAILS_BUTTON;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.SLACK_EVENT_DETAILS_BUTTON_BLOCK_VALUE;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.SLACK_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.SLACK_SAVED_SEARCH_FILTER_SECTION;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.SLACK_SAVED_SEARCH_FILTER_SECTION_VALUE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.CET_MODULE_NAME;

import io.harness.cvng.beans.errortracking.CriticalEventType;
import io.harness.cvng.beans.errortracking.ErrorTrackingHitSummary;
import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.beans.errortracking.EventStatus;
import io.harness.cvng.beans.errortracking.SavedFilter;
import io.harness.cvng.beans.errortracking.Scorecard;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ErrorTrackingNotificationRuleUtils {
  public static final String EVENT_VERSION_LABEL = "Events appeared on the deployment version ";
  public static final String NEW_EVENT_LABEL = "New Events ";
  public static final String CRITICAL_EVENT_LABEL = "Critical Events ";
  public static final String RESURFACED_EVENT_LABEL = "Resurfaced Events ";
  public static final String SEARCH_TERM = ", and search term (";

  private ErrorTrackingNotificationRuleUtils() {
    throw new IllegalStateException("Utility classes cannot be instantiated.");
  }

  public static Map<String, String> getCodeErrorHitSummaryTemplateData(ErrorTrackingHitSummary errorTrackingHitSummary,
      MonitoredService monitoredService, String environmentId, String baseLinkUrl) {
    Map<String, String> notificationDataMap = new HashMap<>();

    long firstSeen = errorTrackingHitSummary.getFirstSeen().getTime() / 1000;

    // Subtract and add 30 seconds to simulate a 1 minute window with the firstSeen date in the middle for the arc
    // screen
    long fromTime = firstSeen - 30;
    long toTime = firstSeen + 30;

    String arcScreenUrl = buildArcScreenUrlWithParameters(baseLinkUrl, monitoredService.getAccountId(),
        monitoredService.getOrgIdentifier(), monitoredService.getProjectIdentifier(),
        errorTrackingHitSummary.getRequestId(), environmentId, monitoredService.getServiceIdentifier(),
        errorTrackingHitSummary.getVersionId(), String.valueOf(fromTime), String.valueOf(toTime));

    notificationDataMap.put(ARC_SCREEN_URL, arcScreenUrl);
    notificationDataMap.put(EMAIL_EVENT_DETAILS_BUTTON, EMAIL_EVENT_DETAILS_BUTTON_VALUE);
    notificationDataMap.put(SLACK_EVENT_DETAILS_BUTTON, SLACK_EVENT_DETAILS_BUTTON_BLOCK_VALUE);

    StackTraceEvent stackTraceEvent = StackTraceEvent.builder()
                                          .version(errorTrackingHitSummary.getVersionId())
                                          .stackTrace(String.join(",", errorTrackingHitSummary.getStackTrace()))
                                          .build();

    final String slackStackTraceEvent = stackTraceEvent.toSlackString();
    final String emailStackTraceEvent = stackTraceEvent.toEmailString();

    notificationDataMap.put(SLACK_FORMATTED_VERSION_LIST, slackStackTraceEvent);
    notificationDataMap.put(EMAIL_FORMATTED_VERSION_LIST, emailStackTraceEvent);

    notificationDataMap.put(EMAIL_SAVED_SEARCH_FILTER_SECTION, "");
    notificationDataMap.put(SLACK_SAVED_SEARCH_FILTER_SECTION, "");

    return notificationDataMap;
  }

  public static String buildArcScreenUrlWithParameters(String baseLinkUrl, String account, String org, String project,
      Integer request, String env, String service, String deployment, String from, String to) {
    return String.format(
        "%s/account/%s/%s/orgs/%s/projects/%s/eventsummary/events/arc?request=%s&environment=%s&harnessService=%s&dep=%s&fromTimestamp=%s&toTimestamp=%s",
        baseLinkUrl, account, CET_MODULE_NAME, org, project, request, env, service, deployment, from, to);
  }

  public static Map<String, String> getCodeErrorTemplateData(MonitoredServiceCodeErrorCondition codeErrorCondition,
      ErrorTrackingNotificationData errorTrackingNotificationData, String baseLinkUrl) {
    Map<String, String> notificationDataMap = new HashMap<>();

    String from = String.valueOf(errorTrackingNotificationData.getFrom().getTime() / 1000);
    String to = String.valueOf(errorTrackingNotificationData.getTo().getTime() / 1000);

    final List<Scorecard> scorecards = errorTrackingNotificationData.getScorecards();
    if (scorecards != null) {
      List<AggregatedEvents> aggregatedEvents = getErrorTrackingEventsRecursive(
          codeErrorCondition, scorecards, errorTrackingNotificationData.getFilter(), baseLinkUrl, from, to);

      notificationDataMap.put(EMAIL_EVENT_DETAILS_BUTTON, "");
      notificationDataMap.put(SLACK_EVENT_DETAILS_BUTTON, "");

      final String slackVersionList =
          aggregatedEvents.stream().map(AggregatedEvents::toSlackString).collect(Collectors.joining("\n"));
      final String emailVersionList =
          aggregatedEvents.stream().map(AggregatedEvents::toEmailString).collect(Collectors.joining());

      notificationDataMap.put(SLACK_FORMATTED_VERSION_LIST, slackVersionList);
      notificationDataMap.put(EMAIL_FORMATTED_VERSION_LIST, emailVersionList);
    }

    if(errorTrackingNotificationData.getFilter() != null) {
      notificationDataMap.put(EMAIL_SAVED_SEARCH_FILTER_SECTION, EMAIL_SAVED_SEARCH_FILTER_SECTION_VALUE.replace("${SAVED_SEARCH_FILTER_NAME}", errorTrackingNotificationData.getFilter().getFilterName()));
      notificationDataMap.put(SLACK_SAVED_SEARCH_FILTER_SECTION, SLACK_SAVED_SEARCH_FILTER_SECTION_VALUE.replace("${SAVED_SEARCH_FILTER_NAME}", errorTrackingNotificationData.getFilter().getFilterName()));
    }
    return notificationDataMap;
  }

  public static List<AggregatedEvents> getErrorTrackingEventsRecursive(
      MonitoredServiceCodeErrorCondition codeErrorCondition, List<Scorecard> scorecards, SavedFilter savedFilter,
      String baseLinkUrl, String from, String to) {
    List<AggregatedEvents> urlsByVersion = new ArrayList<>();
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
          if (codeErrorCondition.getSavedFilterId() != null) {
            eventStatus = savedFilter.getStatuses()
                              .stream()
                              .map(ErrorTrackingNotificationRuleUtils::toErrorTrackingEventStatus)
                              .collect(Collectors.toList());
          }
          AggregatedEvents aggregatedEvents = AggregatedEvents.builder()
                                                  .version(scorecard.getVersionIdentifier())
                                                  .url(eventListUrlWithParameters)
                                                  .newCount(scorecard.getNewHitCount())
                                                  .criticalCount(scorecard.getCriticalHitCount())
                                                  .resurfacedCount(scorecard.getResurfacedHitCount())
                                                  .errorTrackingEventStatus(eventStatus)
                                                  .savedFilterId(codeErrorCondition.getSavedFilterId())
                                                  .build();
          urlsByVersion.add(aggregatedEvents);
        }
      } else {
        urlsByVersion.addAll(getErrorTrackingEventsRecursive(
            codeErrorCondition, scorecard.getChildren(), savedFilter, baseLinkUrl, from, to));
      }
    }
    return urlsByVersion;
  }

  public static String buildEventListUrlWithParameters(String baseLinkUrl, String account, String org, String project,
      String env, String service, String deployment, String from, String to) {
    return String.format(
        "%s/account/%s/%s/orgs/%s/projects/%s/eventsummary/events?env=%s&service=%s&dep=%s&fromTimestamp=%s&toTimestamp=%s",
        baseLinkUrl, account, CET_MODULE_NAME, org, project, env, service, deployment, from, to);
  }

  public static String buildMonitoredServiceConfigurationTabUrl(String baseUrl, MonitoredServiceParams params) {
    return String.format(ET_MONITORED_SERVICE_URL_FORMAT, baseUrl, params.getAccountIdentifier(), CET_MODULE_NAME,
        params.getOrgIdentifier(), params.getProjectIdentifier(), params.getMonitoredServiceIdentifier());
  }

  @Builder
  @Slf4j
  public static class AggregatedEvents {
    private static final String EVENT_STATUS_PARAM = "&eventStatus=";
    private static final String FILTER_ID_PARAM = "&filterId=";
    private static final String NEW_EVENT_NAME;
    private static final String CRITICAL_EVENT_NAME;
    private static final String RESURFACED_EVENT_NAME;

    static {
      try {
        // Get the json property names from the ErrorTrackingEventStatus enums to use as the event status url parameters
        NEW_EVENT_NAME = ErrorTrackingEventStatus.class.getField(ErrorTrackingEventStatus.NEW_EVENTS.name())
                             .getAnnotation(JsonProperty.class)
                             .value();
        CRITICAL_EVENT_NAME = ErrorTrackingEventStatus.class.getField(ErrorTrackingEventStatus.CRITICAL_EVENTS.name())
                                  .getAnnotation(JsonProperty.class)
                                  .value();
        RESURFACED_EVENT_NAME =
            ErrorTrackingEventStatus.class.getField(ErrorTrackingEventStatus.RESURFACED_EVENTS.name())
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
        email.append(
            "<span>" + EVENT_VERSION_LABEL + "<span style=\"font-weight: bold;\">" + version + "</span></span>");
      } else {
        String savedFilterLink =
            EMAIL_LINK_BEGIN + url + FILTER_ID_PARAM + savedFilterId + EMAIL_LINK_MIDDLE + version + EMAIL_LINK_END;
        email.append("<span>" + EVENT_VERSION_LABEL + "<span style=\"font-weight: bold;\">" + savedFilterLink
            + "</span></span>");
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

  public static ErrorTrackingEventStatus toErrorTrackingEventStatus(EventStatus eventStatus) {
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

  @Builder
  @Slf4j
  public static class StackTraceEvent {
    private String version;
    private String stackTrace;
    private String arcScreenUrl;

    public String toSlackString() {
      StringBuilder slack = new StringBuilder(EVENT_VERSION_LABEL + "*" + version + "*\n");
      slack.append("```").append(stackTrace.replace(",", "\n")).append("```");
      return slack.toString();
    }

    public String toEmailString() {
      StringBuilder email = new StringBuilder("<div style=\"margin-bottom: 16px\">");
      email.append("<span>" + EVENT_VERSION_LABEL + "<span style=\"font-weight: bold;\">" + version + "</span></span>");
      email.append("<div style =\"margin-top: 4px; background-color: #383946; border-radius: 3px;\">");
      email.append("<p style=\"color:white; padding: 15px; padding-top: 18px; padding-bottom:18px;\">");
      email.append(stackTrace.replace(",", "</br>"));
      email.append("</p>").append("</div>").append("</div>");
      return email.toString();
    }
  }

  /**
   * Sets variables from the Monitored Service condition that triggered the notification
   */
  public static Map<String, String> getConditionTemplateVariables(
      MonitoredServiceCodeErrorCondition condition, ErrorTrackingNotificationData errorTrackingNotificationData) {

    // Event status example is "New Events" or "Critical Events"
    String changeEventStatusString = "";
    // Event type example is "Exception", "Log Errors", etc
    String changeEventTypeString = "";

    if (condition.getSavedFilterId() == null) {
      changeEventStatusString = condition.getErrorTrackingEventStatus()
                                    .stream()
                                    .map(ErrorTrackingEventStatus::getDisplayName)
                                    .collect(Collectors.joining(", "));

      changeEventTypeString = condition.getErrorTrackingEventTypes()
                                  .stream()
                                  .map(ErrorTrackingEventType::getDisplayName)
                                  .collect(Collectors.joining(", "));
    } else {
      if(errorTrackingNotificationData.getFilter() != null) {
      changeEventStatusString = errorTrackingNotificationData.getFilter()
                                    .getStatuses()
                                    .stream()
                                    .map(ErrorTrackingNotificationRuleUtils::toErrorTrackingEventStatus)
                                    .map(ErrorTrackingEventStatus::getDisplayName)
                                    .collect(Collectors.joining(", "));

      changeEventTypeString = errorTrackingNotificationData.getFilter()
                                  .getEventTypes()
                                  .stream()
                                  .map(CriticalEventType::getDisplayName)
                                  .collect(Collectors.joining(", "))
          + SEARCH_TERM + errorTrackingNotificationData.getFilter().getSearchTerm() + ")";
      } else {
        log.info("errorTrackingNotificationData.getSavedFilter() is null");
      }
    }

    return Map.of(EVENT_STATUS, changeEventStatusString, NOTIFICATION_EVENT_TRIGGER_LIST, changeEventTypeString);
  }
}
