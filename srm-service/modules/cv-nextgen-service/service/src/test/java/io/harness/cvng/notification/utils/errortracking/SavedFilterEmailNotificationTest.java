/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking;

import static io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition;
import static io.harness.cvng.notification.utils.errortracking.AggregatedEventTest.buildSavedFilter;
import static io.harness.cvng.notification.utils.errortracking.SavedFilterEmailNotification.EMAIL_SAVED_SEARCH_FILTER_SECTION;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_EVENT_DETAILS_BUTTON;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_MONITORED_SERVICE_NAME_HYPERLINK;
import static io.harness.cvng.notification.utils.errortracking.interfaces.EmailNotification.EMAIL_NOTIFICATION_NAME_HYPERLINK;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.ENVIRONMENT_NAME;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.EVENT_STATUS;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.NOTIFICATION_EVENT_TRIGGER_LIST;
import static io.harness.rule.OwnerRule.JAMES_RICKS;

import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.errortracking.CriticalEventType;
import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.beans.errortracking.EventStatus;
import io.harness.cvng.beans.errortracking.SavedFilter;
import io.harness.cvng.beans.errortracking.Scorecard;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceNotificationRuleCondition;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.rule.Owner;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SavedFilterEmailNotificationTest {
  ErrorTrackingNotificationData errorTrackingNotificationData;
  SavedFilter savedFilter;
  Long savedFilterId;
  Timestamp from;
  Timestamp to;

  boolean aggregated = true;

  MonitoredServiceCodeErrorCondition codeErrorCondition;
  String baseLinkUrl;
  MonitoredService monitoredService;
  NotificationRule notificationRule;
  String environmentId = "testEnvironmentId";

  @Before
  public void setup() {
    List<String> environmentIdentifierList = Collections.singletonList(environmentId);

    monitoredService = MonitoredService.builder()
                           .accountId("testAccountId")
                           .orgIdentifier("testOrg")
                           .projectIdentifier("testProject")
                           .serviceIdentifier("testService")
                           .environmentIdentifierList(environmentIdentifierList)
                           .identifier("testService_testEnvironment")
                           .build();

    List<Scorecard> scorecards = new ArrayList<>();
    Scorecard scorecard = Scorecard.builder()
                              .newHitCount(1)
                              .hitCount(10)
                              .versionIdentifier("testVersion")
                              .accountIdentifier(monitoredService.getAccountId())
                              .organizationIdentifier(monitoredService.getOrgIdentifier())
                              .projectIdentifier(monitoredService.getProjectIdentifier())
                              .serviceIdentifier(monitoredService.getServiceIdentifier())
                              .environmentIdentifier(environmentId)
                              .build();
    scorecards.add(scorecard);
    List<CriticalEventType> savedFilterEventTypes = new ArrayList<>();
    // I'm not including any logic to filter out ANY type - ET service should handle that logic
    savedFilterEventTypes.add(CriticalEventType.ANY);
    savedFilterEventTypes.add(CriticalEventType.CAUGHT_EXCEPTION);
    savedFilterEventTypes.add(CriticalEventType.UNCAUGHT_EXCEPTION);
    savedFilterEventTypes.add(CriticalEventType.SWALLOWED_EXCEPTION);
    savedFilterEventTypes.add(CriticalEventType.LOGGED_WARNING);
    savedFilterEventTypes.add(CriticalEventType.LOGGED_ERROR);
    savedFilterEventTypes.add(CriticalEventType.CUSTOM_ERROR);
    savedFilterEventTypes.add(CriticalEventType.HTTP_ERROR);

    List<EventStatus> eventStatus = new ArrayList<>();
    eventStatus.add(EventStatus.NEW_EVENTS);
    eventStatus.add(EventStatus.CRITICAL_EVENTS);
    eventStatus.add(EventStatus.RESURFACED_EVENTS);

    savedFilterId = 990L;
    savedFilter = buildSavedFilter(savedFilterId);

    Long currentTime = System.currentTimeMillis();
    from = new Timestamp(currentTime);
    to = new Timestamp(currentTime + 6000);

    errorTrackingNotificationData =
        ErrorTrackingNotificationData.builder().scorecards(scorecards).filter(savedFilter).from(from).to(to).build();

    codeErrorCondition =
        MonitoredServiceCodeErrorCondition.builder().aggregated(aggregated).savedFilterId(savedFilterId).build();
    baseLinkUrl = "http://testurl.com";

    List<MonitoredServiceNotificationRuleCondition> codeErrorConditions = Collections.singletonList(codeErrorCondition);

    notificationRule =
        MonitoredServiceNotificationRule.builder().conditions(codeErrorConditions).name("testNotificationRule").build();
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getNotificationDataMapTest() {
    final Map<String, String> notificationDataMap =
        SavedFilterEmailNotification.getNotificationDataMap(errorTrackingNotificationData, codeErrorCondition,
            baseLinkUrl, monitoredService, notificationRule, environmentId);
    assert notificationDataMap.get(ENVIRONMENT_NAME).equals(environmentId);
    assert notificationDataMap.get(EVENT_STATUS).equals("New Events, Critical Events, Resurfaced Events");
    assert notificationDataMap.get(NOTIFICATION_EVENT_TRIGGER_LIST)
        .equals(
            "Any, Caught Exceptions, Uncaught Exceptions, Swallowed Exceptions, Logged Warnings, Logged Errors, Custom Errors, Http Errors, and search term (testSearchTerm)");
    assert notificationDataMap.get(EMAIL_MONITORED_SERVICE_NAME_HYPERLINK)
        .equals(
            "<a style=\"text-decoration: none; color: #0278D5;\" href=\"http://testurl.com/account/testAccountId/cet/orgs/testOrg/projects/testProject/etmonitoredservices/edit/testService_testEnvironment\">testService_testEnvironment</a>");

    assert notificationDataMap.get(EMAIL_NOTIFICATION_NAME_HYPERLINK)
        .equals(
            "<a style=\"text-decoration: none; color: #0278D5;\" href=\"http://testurl.com/account/testAccountId/cet/orgs/testOrg/projects/testProject/etmonitoredservices/edit/testService_testEnvironment\">testNotificationRule</a>");
    assert notificationDataMap.get(EMAIL_SAVED_SEARCH_FILTER_SECTION)
        .equals("<div style=\"margin-bottom: 8.5px\">\n"
            + "<span style=\"color: #6b6d85\">Saved Search Filter </span>\n"
            + "<span>testFilterName</span>\n"
            + "</div>");
    assert notificationDataMap.get(EMAIL_FORMATTED_VERSION_LIST)
        .equals(
            "<div style=\"margin-bottom: 16px\"><span>Events appeared on the deployment version <span style=\"font-weight: bold;\">testVersion</span></span><div style =\"margin-top: 4px;\"><span><a style=\"text-decoration: none; color: #0278D5;\" href=\"http://testurl.com/account/testAccountId/cet/orgs/testOrg/projects/testProject/eventsummary/events?env=testEnvironmentId&service=testService&dep=testVersion&fromTimestamp="
            + from.getTime() / 1000 + "&toTimestamp=" + to.getTime() / 1000
            + "&eventStatus=NewEvents\">New Events (1)</a><div style=\"display: inline; border-right: 1px solid #b0b1c3; height: 20px; margin: 0px 16px 0px 16px\"></div>Critical Events (0)<div style=\"display: inline; border-right: 1px solid #b0b1c3; height: 20px; margin: 0px 16px 0px 16px\"></div>Resurfaced Events (0)</span></div></div>");

    assert notificationDataMap.get(EMAIL_EVENT_DETAILS_BUTTON).equals("");
  }
}