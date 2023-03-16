/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils;

import static io.harness.rule.OwnerRule.JAMES_RICKS;

import static junit.framework.TestCase.assertEquals;

import io.harness.category.element.UnitTests;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ErrorTrackingNotificationRuleUtilsTest {
  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getSingleEventStatusMessageTest() {
    List<ErrorTrackingEventStatus> errorTrackingEventStatus = new ArrayList<>();
    errorTrackingEventStatus.add(ErrorTrackingEventStatus.NEW_EVENTS);
    final ErrorTrackingNotificationRuleUtils.ErrorTrackingEvent event =
        ErrorTrackingNotificationRuleUtils.ErrorTrackingEvent.builder()
            .version("V1.0")
            .errorTrackingEventStatus(errorTrackingEventStatus)
            .newCount(12)
            .url("http://www.invalidurl.com?firstParam=test")
            .build();
    final String expectedEmailEvent =
        "<div style=\"margin-bottom: 16px\"><span>Events appeared on the deployment version "
        + "<span style=\"font-weight: bold;\">V1.0</span></span><div style =\"margin-top: 4px;\">"
        + "<span><a style=\"text-decoration: none; color: #0278D5;\""
        + " href=\"http://www.invalidurl.com?firstParam=test&eventStatus=NewEvents\">New Events (12)</a>"
        + "</span></div></div>";
    assertEquals(expectedEmailEvent, event.toEmailString());
    final String expectedSlackEvent = "Events appeared on the deployment version *V1.0*\n"
        + "<http://www.invalidurl.com?firstParam=test&eventStatus=NewEvents|New Events (12)>";
    assertEquals(expectedSlackEvent, event.toSlackString());
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getCriticalEventStatusMessageTest() {
    List<ErrorTrackingEventStatus> errorTrackingEventStatus = new ArrayList<>();
    errorTrackingEventStatus.add(ErrorTrackingEventStatus.CRITICAL_EVENTS);
    final ErrorTrackingNotificationRuleUtils.ErrorTrackingEvent event =
        ErrorTrackingNotificationRuleUtils.ErrorTrackingEvent.builder()
            .version("V1.0")
            .errorTrackingEventStatus(errorTrackingEventStatus)
            .newCount(12)
            .criticalCount(78)
            .url("http://www.invalidurl.com?firstParam=test")
            .build();
    final String expectedEmailEvent =
        "<div style=\"margin-bottom: 16px\"><span>Events appeared on the deployment version "
        + "<span style=\"font-weight: bold;\">V1.0</span></span><div style =\"margin-top: 4px;\">"
        + "<span><a style=\"text-decoration: none; color: #0278D5;\""
        + " href=\"http://www.invalidurl.com?firstParam=test&eventStatus=CriticalEvents\">Critical Events (78)</a>"
        + "</span></div></div>";
    assertEquals(expectedEmailEvent, event.toEmailString());
    final String expectedSlackEvent = "Events appeared on the deployment version *V1.0*\n"
        + "<http://www.invalidurl.com?firstParam=test&eventStatus=CriticalEvents|Critical Events (78)>";
    assertEquals(expectedSlackEvent, event.toSlackString());
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getAllEventStatusMessageTest() {
    final List<ErrorTrackingEventStatus> errorTrackingEventStatus = Arrays.asList(ErrorTrackingEventStatus.values());
    final ErrorTrackingNotificationRuleUtils.ErrorTrackingEvent event =
        ErrorTrackingNotificationRuleUtils.ErrorTrackingEvent.builder()
            .version("V1.0")
            .errorTrackingEventStatus(errorTrackingEventStatus)
            .newCount(11)
            .criticalCount(5)
            .resurfacedCount(7)
            .url("http://www.invalidurl.com?firstParam=test")
            .build();
    final String expectedEmailEvent =
        "<div style=\"margin-bottom: 16px\"><span>Events appeared on the deployment version "
        + "<span style=\"font-weight: bold;\">V1.0</span></span><div style =\"margin-top: 4px;\"><span>"
        + "<a style=\"text-decoration: none; color: #0278D5;\""
        + " href=\"http://www.invalidurl.com?firstParam=test&eventStatus=NewEvents\">New Events (11)</a>"
        + "<div style=\"display: inline; border-right: 1px solid #b0b1c3; height: 20px; margin: 0px 16px 0px 16px\">"
        + "</div><a style=\"text-decoration: none; color: #0278D5;\""
        + " href=\"http://www.invalidurl.com?firstParam=test&eventStatus=CriticalEvents\">Critical Events (5)</a>"
        + "<div style=\"display: inline; border-right: 1px solid #b0b1c3; height: 20px; margin: 0px 16px 0px 16px\">"
        + "</div><a style=\"text-decoration: none; color: #0278D5;\""
        + " href=\"http://www.invalidurl.com?firstParam=test&eventStatus=ResurfacedEvents\">Resurfaced Events (7)</a>"
        + "</span></div></div>";
    assertEquals(expectedEmailEvent, event.toEmailString());
    final String expectedSlackEvent = "Events appeared on the deployment version *V1.0*\n"
        + "<http://www.invalidurl.com?firstParam=test&eventStatus=NewEvents|New Events (11)>   |   "
        + "<http://www.invalidurl.com?firstParam=test&eventStatus=CriticalEvents|Critical Events (5)>   |   "
        + "<http://www.invalidurl.com?firstParam=test&eventStatus=ResurfacedEvents|Resurfaced Events (7)>";
    assertEquals(expectedSlackEvent, event.toSlackString());
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getAllEventStatusMessageNoEventsTest() {
    final List<ErrorTrackingEventStatus> errorTrackingEventStatus = Arrays.asList(ErrorTrackingEventStatus.values());
    final ErrorTrackingNotificationRuleUtils.ErrorTrackingEvent event =
        ErrorTrackingNotificationRuleUtils.ErrorTrackingEvent.builder()
            .version("V2.0")
            .errorTrackingEventStatus(errorTrackingEventStatus)
            .newCount(0)
            .criticalCount(1)
            .resurfacedCount(2)
            .url("http://www.invalidurl.com?firstParam=test")
            .build();
    final String expectedEmailEvent =
        "<div style=\"margin-bottom: 16px\"><span>Events appeared on the deployment version "
        + "<span style=\"font-weight: bold;\">V2.0</span></span><div style =\"margin-top: 4px;\"><span>"
        + "New Events (0)"
        + "<div style=\"display: inline; border-right: 1px solid #b0b1c3; height: 20px; margin: 0px 16px 0px 16px\">"
        + "</div><a style=\"text-decoration: none; color: #0278D5;\""
        + " href=\"http://www.invalidurl.com?firstParam=test&eventStatus=CriticalEvents\">Critical Events (1)</a>"
        + "<div style=\"display: inline; border-right: 1px solid #b0b1c3; height: 20px; margin: 0px 16px 0px 16px\">"
        + "</div><a style=\"text-decoration: none; color: #0278D5;\""
        + " href=\"http://www.invalidurl.com?firstParam=test&eventStatus=ResurfacedEvents\">Resurfaced Events (2)</a>"
        + "</span></div></div>";
    assertEquals(expectedEmailEvent, event.toEmailString());
    final String expectedSlackEvent = "Events appeared on the deployment version *V2.0*\n"
        + "New Events (0)   |   "
        + "<http://www.invalidurl.com?firstParam=test&eventStatus=CriticalEvents|Critical Events (1)>   |   "
        + "<http://www.invalidurl.com?firstParam=test&eventStatus=ResurfacedEvents|Resurfaced Events (2)>";
    assertEquals(expectedSlackEvent, event.toSlackString());
  }
}
