/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils;

import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_LINK_BEGIN;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_LINK_END;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_LINK_MIDDLE;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.SLACK_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MODULE_NAME;

import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.beans.errortracking.Scorecard;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;

public class ErrorTrackingNotificationRuleUtils {
  public static final String EVENT_VERSION_LABEL = "Events appeared on the deployment version ";
  public static final String NEW_EVENT_LABEL = "New Events ";

  private ErrorTrackingNotificationRuleUtils() {
    throw new IllegalStateException("Utility classes cannot be instantiated.");
  }

  public static Map<String, String> getCodeErrorTemplateData(
      ErrorTrackingNotificationData errorTrackingNotificationData, String baseLinkUrl) {
    Map<String, String> notificationDataMap = new HashMap<>();

    String from = String.valueOf(errorTrackingNotificationData.getFrom().getTime() / 1000);
    String to = String.valueOf(errorTrackingNotificationData.getTo().getTime() / 1000);

    final List<Scorecard> scorecards = errorTrackingNotificationData.getScorecards();
    if (scorecards != null) {
      List<ErrorTrackingEvent> errorTrackingEvents = getErrorTrackingEventsRecursive(scorecards, baseLinkUrl, from, to);

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
      List<Scorecard> scorecards, String baseLinkUrl, String from, String to) {
    List<ErrorTrackingEvent> urlsByVersion = new ArrayList<>();
    for (Scorecard scorecard : scorecards) {
      if (scorecard.getChildren() == null) {
        if (scorecard.getNewHitCount() > 0 && scorecard.getVersionIdentifier() != null) {
          final String eventListUrlWithParameters = buildEventListUrlWithParameters(baseLinkUrl,
              scorecard.getAccountIdentifier(), scorecard.getOrganizationIdentifier(), scorecard.getProjectIdentifier(),
              scorecard.getEnvironmentIdentifier(), scorecard.getServiceIdentifier(), scorecard.getVersionIdentifier(),
              from, to);
          ErrorTrackingEvent errorTrackingEvent = ErrorTrackingEvent.builder()
                                                      .version(scorecard.getVersionIdentifier())
                                                      .url(eventListUrlWithParameters)
                                                      .newCount(scorecard.getNewHitCount().toString())
                                                      .build();
          urlsByVersion.add(errorTrackingEvent);
        }
      } else {
        urlsByVersion.addAll(getErrorTrackingEventsRecursive(scorecard.getChildren(), baseLinkUrl, from, to));
      }
    }
    return urlsByVersion;
  }

  public static String buildEventListUrlWithParameters(String baseLinkUrl, String account, String org, String project,
      String env, String service, String deployment, String from, String to) {
    return String.format(
        "%s/account/%s/%s/orgs/%s/projects/%s/et/eventsummary/events?env=%s&service=%s&dep=%s&fromTimestamp=%s&toTimestamp=%s",
        baseLinkUrl, account, MODULE_NAME, org, project, env, service, deployment, from, to);
  }

  public static String buildMonitoredServiceConfigurationTabUrl(String baseUrl, MonitoredServiceParams params) {
    return String.format("%s/account/%s/%s/orgs/%s/projects/%s/monitoringservices/edit/%s?tab=Configurations", baseUrl,
        params.getAccountIdentifier(), MODULE_NAME, params.getOrgIdentifier(), params.getProjectIdentifier(),
        params.getMonitoredServiceIdentifier());
  }

  @Builder
  public static class ErrorTrackingEvent {
    private String version;
    private String url;
    private String newCount;

    public String toSlackString() {
      return EVENT_VERSION_LABEL + "*" + version + "*\n<" + url + "|" + NEW_EVENT_LABEL + "(" + newCount + ")>";
    }
    public String toEmailString() {
      return "<div style=\"margin-bottom: 16px\">"
          + "<span>" + EVENT_VERSION_LABEL + "<span style=\"font-weight: bold;\">" + version + "</span></span>"
          + "<div style =\"margin-top: 4px;\">"
          + "<span>" + EMAIL_LINK_BEGIN + url + EMAIL_LINK_MIDDLE + NEW_EVENT_LABEL + "(" + newCount + ")"
          + EMAIL_LINK_END + "</span>"
          + "</div>"
          + "</div>";
    }
  }
}
