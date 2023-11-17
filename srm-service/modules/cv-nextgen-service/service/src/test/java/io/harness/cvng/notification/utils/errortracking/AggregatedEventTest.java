/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.utils.errortracking;

import static io.harness.rule.OwnerRule.JAMES_RICKS;

import static junit.framework.TestCase.assertEquals;

import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.errortracking.CriticalEventType;
import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.beans.errortracking.EventStatus;
import io.harness.cvng.beans.errortracking.SavedFilter;
import io.harness.cvng.beans.errortracking.Scorecard;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.rule.Owner;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AggregatedEventTest {
  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getDefinedFilterAggregatedEventsTest() {
    String baseLinkUrl = "http://testurl.com";
    String environmentId = "testEnvironmentId";
    List<String> environmentIdentifierList = Collections.singletonList(environmentId);

    MonitoredService monitoredService = MonitoredService.builder()
                                            .accountId("testAccountId")
                                            .orgIdentifier("testOrg")
                                            .projectIdentifier("testProject")
                                            .serviceIdentifier("testService")
                                            .environmentIdentifierList(environmentIdentifierList)
                                            .identifier("testService_testEnvironment")
                                            .build();

    List<Scorecard> scorecards = new ArrayList<>();

    Scorecard childScorecard1 = Scorecard.builder()
                                    .newHitCount(2)
                                    .hitCount(7)
                                    .versionIdentifier("testVersion1")
                                    .accountIdentifier(monitoredService.getAccountId())
                                    .organizationIdentifier(monitoredService.getOrgIdentifier())
                                    .projectIdentifier(monitoredService.getProjectIdentifier())
                                    .serviceIdentifier(monitoredService.getServiceIdentifier())
                                    .environmentIdentifier(environmentId)
                                    .build();

    Scorecard childScorecard2 = Scorecard.builder()
                                    .newHitCount(1)
                                    .hitCount(3)
                                    .versionIdentifier("testVersion2")
                                    .accountIdentifier(monitoredService.getAccountId())
                                    .organizationIdentifier(monitoredService.getOrgIdentifier())
                                    .projectIdentifier(monitoredService.getProjectIdentifier())
                                    .serviceIdentifier(monitoredService.getServiceIdentifier())
                                    .environmentIdentifier(environmentId)
                                    .build();
    List<Scorecard> scorecardChildren = new ArrayList<>();
    scorecardChildren.add(childScorecard1);
    scorecardChildren.add(childScorecard2);

    Scorecard parentScorecard = Scorecard.builder()
                                    .newHitCount(3)
                                    .hitCount(10)
                                    .accountIdentifier(monitoredService.getAccountId())
                                    .organizationIdentifier(monitoredService.getOrgIdentifier())
                                    .projectIdentifier(monitoredService.getProjectIdentifier())
                                    .serviceIdentifier(monitoredService.getServiceIdentifier())
                                    .environmentIdentifier(environmentId)
                                    .children(scorecardChildren)
                                    .build();

    scorecards.add(parentScorecard);

    Long currentTime = System.currentTimeMillis();
    Timestamp from = new Timestamp(currentTime);
    Timestamp to = new Timestamp(currentTime + 6000);

    ErrorTrackingNotificationData errorTrackingNotificationData =
        ErrorTrackingNotificationData.builder().scorecards(scorecards).from(from).to(to).build();

    List<ErrorTrackingEventStatus> errorTrackingEventStatus = new ArrayList<>();
    errorTrackingEventStatus.add(ErrorTrackingEventStatus.NEW_EVENTS);
    errorTrackingEventStatus.add(ErrorTrackingEventStatus.CRITICAL_EVENTS);
    errorTrackingEventStatus.add(ErrorTrackingEventStatus.RESURFACED_EVENTS);

    List<ErrorTrackingEventType> errorTrackingEventTypes;
    errorTrackingEventTypes = new ArrayList<>();
    errorTrackingEventTypes.add(ErrorTrackingEventType.CUSTOM);
    errorTrackingEventTypes.add(ErrorTrackingEventType.EXCEPTION);
    errorTrackingEventTypes.add(ErrorTrackingEventType.HTTP);
    errorTrackingEventTypes.add(ErrorTrackingEventType.LOG);

    MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition codeErrorCondition =
        MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition.builder()
            .aggregated(true)
            .errorTrackingEventStatus(errorTrackingEventStatus)
            .errorTrackingEventTypes(errorTrackingEventTypes)
            .build();

    final List<AggregatedEvent> aggregatedEvents =
        AggregatedEvent.getAggregatedEvents(codeErrorCondition, errorTrackingNotificationData, baseLinkUrl);
    assert aggregatedEvents.size() == 2;
    final String emailString = aggregatedEvents.get(0).toEmailString();
    assert emailString.contains(AggregatedEvent.NEW_EVENT_LABEL);
    assert emailString.contains(AggregatedEvent.CRITICAL_EVENT_LABEL);
    assert emailString.contains(AggregatedEvent.RESURFACED_EVENT_LABEL);

    final String slackString = aggregatedEvents.get(0).toSlackString();
    assert slackString.contains(AggregatedEvent.NEW_EVENT_LABEL);
    assert slackString.contains(AggregatedEvent.CRITICAL_EVENT_LABEL);
    assert slackString.contains(AggregatedEvent.RESURFACED_EVENT_LABEL);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getSavedFilterAggregatedEventsTest() {
    String baseLinkUrl = "http://testurl.com";
    String environmentId = "testEnvironmentId";
    List<String> environmentIdentifierList = Collections.singletonList(environmentId);

    MonitoredService monitoredService = MonitoredService.builder()
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

    Long currentTime = System.currentTimeMillis();
    Timestamp from = new Timestamp(currentTime);
    Timestamp to = new Timestamp(currentTime + 6000);

    Long savedFilterId = 22L;
    final SavedFilter savedFilter = buildSavedFilter(savedFilterId);

    ErrorTrackingNotificationData errorTrackingNotificationData =
        ErrorTrackingNotificationData.builder().scorecards(scorecards).from(from).to(to).filter(savedFilter).build();

    MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition codeErrorCondition =
        MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition.builder()
            .aggregated(true)
            .savedFilterId(savedFilterId)
            .build();

    final List<AggregatedEvent> aggregatedEvents =
        AggregatedEvent.getAggregatedEvents(codeErrorCondition, errorTrackingNotificationData, baseLinkUrl);
    assert aggregatedEvents.size() == 1;
    final String emailString = aggregatedEvents.get(0).toEmailString();
    assert emailString.contains(AggregatedEvent.NEW_EVENT_LABEL);
    assert emailString.contains(AggregatedEvent.CRITICAL_EVENT_LABEL);
    assert emailString.contains(AggregatedEvent.RESURFACED_EVENT_LABEL);

    final String slackString = aggregatedEvents.get(0).toSlackString();
    assert slackString.contains(AggregatedEvent.NEW_EVENT_LABEL);
    assert slackString.contains(AggregatedEvent.CRITICAL_EVENT_LABEL);
    assert slackString.contains(AggregatedEvent.RESURFACED_EVENT_LABEL);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getSingleEventStatusMessageTest() {
    List<ErrorTrackingEventStatus> errorTrackingEventStatus = new ArrayList<>();
    errorTrackingEventStatus.add(ErrorTrackingEventStatus.NEW_EVENTS);
    final AggregatedEvent event = AggregatedEvent.builder()
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
    final AggregatedEvent event = AggregatedEvent.builder()
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
    final AggregatedEvent event = AggregatedEvent.builder()
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
    final AggregatedEvent event = AggregatedEvent.builder()
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
  public static SavedFilter buildSavedFilter(Long savedFilterId) {
    final List<EventStatus> eventStatus = Arrays.asList(EventStatus.values());

    // no logic is included to filter out CriticalEventType.ANY - ET service should handle that logic
    List<CriticalEventType> savedFilterEventTypes = Arrays.asList(CriticalEventType.values());

    return SavedFilter.builder()
        .statuses(eventStatus)
        .filterName("testFilterName")
        .searchTerm("testSearchTerm")
        .eventTypes(savedFilterEventTypes)
        .id(savedFilterId)
        .build();
  }
}