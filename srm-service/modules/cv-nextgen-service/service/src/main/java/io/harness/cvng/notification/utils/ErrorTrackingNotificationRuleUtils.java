/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils;

import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_HORIZONTAL_LINE_DIV;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_LINK_BEGIN;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_LINK_END;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_LINK_MIDDLE;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.ET_MONITORED_SERVICE_URL_FORMAT;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.SLACK_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.CET_MODULE_NAME;

import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.beans.errortracking.Scorecard;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

public class ErrorTrackingNotificationRuleUtils {
  public static final String EVENT_VERSION_LABEL = "Events appeared on the deployment version ";
  public static final String NEW_EVENT_LABEL = "New Events ";
  public static final String CRITICAL_EVENT_LABEL = "Critical Events ";
  public static final String RESURFACED_EVENT_LABEL = "Resurfaced Events ";

  private ErrorTrackingNotificationRuleUtils() {
    throw new IllegalStateException("Utility classes cannot be instantiated.");
  }

  public static Map<String, String> getCodeErrorTemplateData(List<ErrorTrackingEventStatus> errorTrackingEventStatus,
      ErrorTrackingNotificationData errorTrackingNotificationData, String baseLinkUrl) {
    Map<String, String> notificationDataMap = new HashMap<>();

    String from = String.valueOf(errorTrackingNotificationData.getFrom().getTime() / 1000);
    String to = String.valueOf(errorTrackingNotificationData.getTo().getTime() / 1000);

    final List<Scorecard> scorecards = errorTrackingNotificationData.getScorecards();
    if (scorecards != null) {
      List<ErrorTrackingEvent> errorTrackingEvents =
          getErrorTrackingEventsRecursive(errorTrackingEventStatus, scorecards, baseLinkUrl, from, to);

      final String slackVersionList =
          errorTrackingEvents.stream().map(ErrorTrackingEvent::toSlackString).collect(Collectors.joining("\n"));
      final String emailVersionList =
          errorTrackingEvents.stream().map(ErrorTrackingEvent::toEmailString).collect(Collectors.joining());

      notificationDataMap.put(SLACK_FORMATTED_VERSION_LIST, slackVersionList);
      notificationDataMap.put(EMAIL_FORMATTED_VERSION_LIST, emailVersionList);
    }
    return notificationDataMap;
  }

  public static List<ErrorTrackingEvent> getErrorTrackingEventsRecursive(
      List<ErrorTrackingEventStatus> errorTrackingEventStatus, List<Scorecard> scorecards, String baseLinkUrl,
      String from, String to) {
    List<ErrorTrackingEvent> urlsByVersion = new ArrayList<>();
    for (Scorecard scorecard : scorecards) {
      if (scorecard.getChildren() == null) {
        if (scorecard.getVersionIdentifier() != null
            && (scorecard.getNewHitCount() > 0 || scorecard.getCriticalHitCount() > 0
                || scorecard.getResurfacedHitCount() > 0)) {
          final String eventListUrlWithParameters = buildEventListUrlWithParameters(baseLinkUrl,
              scorecard.getAccountIdentifier(), scorecard.getOrganizationIdentifier(), scorecard.getProjectIdentifier(),
              scorecard.getEnvironmentIdentifier(), scorecard.getServiceIdentifier(), scorecard.getVersionIdentifier(),
              from, to);
          ErrorTrackingEvent errorTrackingEvent = ErrorTrackingEvent.builder()
                                                      .version(scorecard.getVersionIdentifier())
                                                      .url(eventListUrlWithParameters)
                                                      .newCount(scorecard.getNewHitCount())
                                                      .criticalCount(scorecard.getCriticalHitCount())
                                                      .resurfacedCount(scorecard.getResurfacedHitCount())
                                                      .errorTrackingEventStatus(errorTrackingEventStatus)
                                                      .build();
          urlsByVersion.add(errorTrackingEvent);
        }
      } else {
        urlsByVersion.addAll(
            getErrorTrackingEventsRecursive(errorTrackingEventStatus, scorecard.getChildren(), baseLinkUrl, from, to));
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
  public static class ErrorTrackingEvent {
    private static final String EVENT_STATUS_PARAM = "&eventStatus=";
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

    public String toSlackString() {
      StringBuilder slack = new StringBuilder(EVENT_VERSION_LABEL + "*" + version + "*\n");
      slack.append(errorTrackingEventStatus.stream().map(this::getSlackString).collect(Collectors.joining("   |   ")));
      return slack.toString();
    }

    public String toEmailString() {
      StringBuilder email = new StringBuilder("<div style=\"margin-bottom: 16px\">");
      email.append("<span>" + EVENT_VERSION_LABEL + "<span style=\"font-weight: bold;\">" + version + "</span></span>");
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

      // only provide the text if the event status count is 0
      if (count == 0) {
        return textOnly;
      }
      return "<" + getEventStatusParameterUrl(status) + "|" + textOnly + ">";
    }

    private String getEmailString(ErrorTrackingEventStatus status) {
      int count = getEventStatusCount(status);
      String textOnly = getEventStatusLabel(status) + "(" + count + ")";

      // only provide the text if the event status count is 0
      if (count == 0) {
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
}
