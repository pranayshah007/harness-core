/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking;

import static io.harness.cvng.core.services.impl.monitoredService.ErrorTrackingNotificationServiceImplTest.createHitSummary;
import static io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition;
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
import io.harness.cvng.beans.errortracking.ErrorTrackingHitSummary;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceNotificationRuleCondition;
import io.harness.cvng.notification.entities.NotificationRule;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ImmediateEmailNotificationTest {
  boolean aggregated = false;

  List<ErrorTrackingEventStatus> errorTrackingEventStatus;
  List<ErrorTrackingEventType> errorTrackingEventTypes;

  MonitoredServiceCodeErrorCondition codeErrorCondition;
  String baseLinkUrl;
  MonitoredService monitoredService;
  NotificationRule notificationRule;
  String environmentId = "testEnvironmentId";
  Long currentTime;
  ErrorTrackingHitSummary errorTrackingHitSummary;

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

    errorTrackingHitSummary = createHitSummary("testVersion1");

    errorTrackingEventStatus = new ArrayList<>();
    errorTrackingEventStatus.add(ErrorTrackingEventStatus.NEW_EVENTS);
    errorTrackingEventStatus.add(ErrorTrackingEventStatus.CRITICAL_EVENTS);
    errorTrackingEventStatus.add(ErrorTrackingEventStatus.RESURFACED_EVENTS);

    errorTrackingEventTypes = new ArrayList<>();
    errorTrackingEventTypes.add(ErrorTrackingEventType.CUSTOM);
    errorTrackingEventTypes.add(ErrorTrackingEventType.EXCEPTION);
    errorTrackingEventTypes.add(ErrorTrackingEventType.HTTP);
    errorTrackingEventTypes.add(ErrorTrackingEventType.LOG);

    codeErrorCondition = MonitoredServiceCodeErrorCondition.builder()
                             .aggregated(aggregated)
                             .errorTrackingEventStatus(errorTrackingEventStatus)
                             .errorTrackingEventTypes(errorTrackingEventTypes)
                             .build();
    baseLinkUrl = "http://testurl.com";

    List<MonitoredServiceNotificationRuleCondition> codeErrorConditions = Collections.singletonList(codeErrorCondition);

    notificationRule =
        MonitoredServiceNotificationRule.builder().conditions(codeErrorConditions).name("testNotificationRule").build();
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getNotificationDataMapTest() {
    final Map<String, String> notificationDataMap = ImmediateEmailNotification.getNotificationDataMap(
        errorTrackingHitSummary, baseLinkUrl, monitoredService, notificationRule, environmentId);
    assert notificationDataMap.get(ENVIRONMENT_NAME).equals(environmentId);
    assert notificationDataMap.get(EVENT_STATUS).equals("New Events");
    assert notificationDataMap.get(NOTIFICATION_EVENT_TRIGGER_LIST)
        .equals(
            "Caught Exceptions, Uncaught Exceptions, Swallowed Exceptions, Logged Errors, Logged Warnings, Http Errors, Custom Errors");
    assert notificationDataMap.get(EMAIL_MONITORED_SERVICE_NAME_HYPERLINK)
        .equals(
            "<a style=\"text-decoration: none; color: #0278D5;\" href=\"http://testurl.com/account/testAccountId/cet/orgs/testOrg/projects/testProject/etmonitoredservices/edit/testService_testEnvironment\">testService_testEnvironment</a>");
    assert notificationDataMap.get(EMAIL_NOTIFICATION_NAME_HYPERLINK)
        .equals(
            "<a style=\"text-decoration: none; color: #0278D5;\" href=\"http://testurl.com/account/testAccountId/cet/orgs/testOrg/projects/testProject/etmonitoredservices/edit/testService_testEnvironment\">testNotificationRule</a>");

    assert notificationDataMap.get(EMAIL_FORMATTED_VERSION_LIST)
        .equals(
            "<div style=\"margin-bottom: 16px\"><span>Events appeared on the deployment version <span style=\"font-weight: bold;\">testVersion</span></span><div style =\"margin-top: 4px; background-color: #383946; border-radius: 3px;\"><p style=\"color:white; padding: 15px; padding-top: 18px; padding-bottom:18px;\">stacktrace line 1<br/>stacktrace line 2<br/>stacktrace line 3</p></div></div>");

    assert notificationDataMap.get(EMAIL_EVENT_DETAILS_BUTTON)
        .equals(
            "<a style=\"float:left;font-size: 13px;font-weight: bold;line-height: 16px;text-decoration: none;background-color: #EEEEEE;color: #333333;background-color: white;border: solid;border-width: 1px;border-radius: 3px;border-color: #BABABA;padding: 8px;padding-left: 16px;padding-right: 16px;\" href=\"http://testurl.com/account/testAccountId/cet/orgs/testOrg/projects/testProject/eventsummary/events/arc?request=27&environment=testEnvironmentId&harnessService=testService_testEnvironment&dep=testVersion&fromTimestamp="
            + ((currentTime / 1000) - 30) + "&toTimestamp=" + ((currentTime / 1000) + 30)
            + "\" class=\"button\">View Event Details</a>");
    assert notificationDataMap.get(EMAIL_SAVED_SEARCH_FILTER_SECTION).equals("");
  }
}