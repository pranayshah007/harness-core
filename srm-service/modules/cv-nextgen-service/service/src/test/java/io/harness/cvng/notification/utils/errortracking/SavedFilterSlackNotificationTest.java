/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking;

import static io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_URL;
import static io.harness.cvng.notification.utils.errortracking.AggregatedEventTest.buildSavedFilter;
import static io.harness.cvng.notification.utils.errortracking.SavedFilterSlackNotification.SLACK_SAVED_SEARCH_FILTER_SECTION;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.ENVIRONMENT_NAME;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.EVENT_STATUS;
import static io.harness.cvng.notification.utils.errortracking.interfaces.ErrorTrackingNotification.NOTIFICATION_EVENT_TRIGGER_LIST;
import static io.harness.cvng.notification.utils.errortracking.interfaces.SlackNotification.NOTIFICATION_NAME;
import static io.harness.cvng.notification.utils.errortracking.interfaces.SlackNotification.NOTIFICATION_URL;
import static io.harness.cvng.notification.utils.errortracking.interfaces.SlackNotification.SLACK_EVENT_DETAILS_BUTTON;
import static io.harness.cvng.notification.utils.errortracking.interfaces.SlackNotification.SLACK_FORMATTED_VERSION_LIST;
import static io.harness.rule.OwnerRule.JAMES_RICKS;

import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
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

public class SavedFilterSlackNotificationTest {
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

    savedFilterId = 523L;
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
        SavedFilterSlackNotification.getNotificationDataMap(errorTrackingNotificationData, codeErrorCondition,
            baseLinkUrl, monitoredService, notificationRule, environmentId);
    assert notificationDataMap.get(ENVIRONMENT_NAME).equals(environmentId);
    assert notificationDataMap.get(EVENT_STATUS).equals("New Events, Critical Events, Resurfaced Events");
    assert notificationDataMap.get(NOTIFICATION_EVENT_TRIGGER_LIST)
        .equals(
            "Any, Caught Exceptions, Uncaught Exceptions, Swallowed Exceptions, Logged Warnings, Logged Errors, Custom Errors, Http Errors, and search term (testSearchTerm)");
    assert notificationDataMap.get(MONITORED_SERVICE_URL)
        .equals(
            "http://testurl.com/account/testAccountId/cet/orgs/testOrg/projects/testProject/etmonitoredservices/edit/testService_testEnvironment");

    assert notificationDataMap.get(MONITORED_SERVICE_NAME).equals("testService_testEnvironment");

    assert notificationDataMap.get(NOTIFICATION_URL)
        .equals(
            "http://testurl.com/account/testAccountId/cet/orgs/testOrg/projects/testProject/etmonitoredservices/edit/testService_testEnvironment");
    assert notificationDataMap.get(NOTIFICATION_NAME).equals("testNotificationRule");
    assert notificationDataMap.get(SLACK_SAVED_SEARCH_FILTER_SECTION)
        .equals(
            "{\"type\": \"section\",\"text\": {\"type\": \"mrkdwn\",\"text\": \"Saved Search Filter testFilterName\"}},");
    assert notificationDataMap.get(SLACK_FORMATTED_VERSION_LIST)
        .equals(
            "Events appeared on the deployment version *<http://testurl.com/account/testAccountId/cet/orgs/testOrg/projects/testProject/eventsummary/events?env=testEnvironmentId&service=testService&dep=testVersion&fromTimestamp="
            + from.getTime() / 1000 + "&toTimestamp=" + to.getTime() / 1000 + "&filterId=523|testVersion>*\n"
            + "New Events (1)   |   Critical Events (0)   |   Resurfaced Events (0)");
    assert notificationDataMap.get(SLACK_EVENT_DETAILS_BUTTON).equals("");
  }
}