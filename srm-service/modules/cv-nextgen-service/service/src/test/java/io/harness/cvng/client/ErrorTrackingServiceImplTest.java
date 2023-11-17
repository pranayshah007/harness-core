/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.client;

import static io.harness.cvng.beans.errortracking.NewEventDefinition.NEVER_SEEN_BEFORE;
import static io.harness.cvng.core.services.impl.monitoredService.ErrorTrackingNotificationServiceImplTest.createHitSummary;
import static io.harness.cvng.notification.utils.errortracking.AggregatedEventTest.buildSavedFilter;
import static io.harness.rule.OwnerRule.JAMES_RICKS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.errortracking.ErrorTrackingHitSummary;
import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.beans.errortracking.EventType;
import io.harness.cvng.beans.errortracking.InvocationSummary;
import io.harness.cvng.beans.errortracking.SavedFilter;
import io.harness.cvng.beans.errortracking.Scorecard;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

public class ErrorTrackingServiceImplTest extends CvNextGenTestBase {
  private static final long FIVE_MINUTES = 300_000; // in millis

  private static final String ACCOUNT = "testAccountId";
  private static final String PROJECT = "testProjectId";
  private static final String VERSION = "testVersionId";
  private static final String ENVIRONMENT = "testEnvironmentId";
  private static final String ORG = "testOrgId";
  private static final String SERVICE = "testServiceId";

  private long beginTimeMillis;

  @Inject private ErrorTrackingServiceImpl errorTrackingService;
  @Mock ErrorTrackingClient errorTrackingClientMock;

  ErrorTrackingNotificationData notificationData;

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void notificationDataClientTest() throws IllegalAccessException, IOException {
    beginTimeMillis = System.currentTimeMillis();
    FieldUtils.writeField(errorTrackingService, "errorTrackingClient", errorTrackingClientMock, true);

    InvocationSummary invocationSummary = InvocationSummary.builder()
                                              .requestId(1)
                                              .harnessProjectId(1L)
                                              .harnessEnvironmentId(ENVIRONMENT)
                                              .harnessServiceId(SERVICE)
                                              .hits(0)
                                              .invocations(0)
                                              .firstSeen(new Timestamp(beginTimeMillis))
                                              .lastSeen(new Timestamp(beginTimeMillis))
                                              .build();

    Scorecard scorecard = Scorecard.builder()
                              .organizationIdentifier(ORG)
                              .accountIdentifier(ACCOUNT)
                              .projectIdentifier(PROJECT)
                              .serviceIdentifier(SERVICE)
                              .versionIdentifier(VERSION)
                              .environmentIdentifier(ENVIRONMENT)
                              .criticalHitCountThreshold(1)
                              .uniqueHitCountThreshold(2)
                              .newHitCountThreshold(3)
                              .newEventDefinition(NEVER_SEEN_BEFORE)
                              .criticalExceptions(Collections.singletonList("testCriticalException"))
                              .criticalHitCount(0)
                              .resurfacedHitCount(0)
                              .uniqueHitCount(0)
                              .newHitCount(0)
                              .hitCount(0)
                              .eventType(EventType.EXCEPTION)
                              .events(Collections.singletonList(invocationSummary))
                              .build();

    notificationData = ErrorTrackingNotificationData.builder()
                           .scorecards(Collections.singletonList(scorecard))
                           .from(new Timestamp(beginTimeMillis - FIVE_MINUTES))
                           .to(new Timestamp(beginTimeMillis))
                           .build();
    Call<ErrorTrackingNotificationData> callMock = mock(Call.class);
    when(callMock.clone()).thenReturn(callMock);
    when(errorTrackingClientMock.getNotificationData(eq(ORG), eq(ACCOUNT), eq(PROJECT), eq(SERVICE), eq(ENVIRONMENT),
             anyList(), anyList(), anyString(), anyInt()))
        .thenReturn(callMock);
    when(callMock.execute()).thenReturn(Response.success(notificationData));

    final List<ErrorTrackingEventStatus> errorTrackingEventStatus = Arrays.asList(ErrorTrackingEventStatus.values());
    final ErrorTrackingNotificationData responseNotificationData =
        errorTrackingService.getNotificationData(ORG, ACCOUNT, PROJECT, SERVICE, ENVIRONMENT, errorTrackingEventStatus,
            Collections.singletonList(ErrorTrackingEventType.EXCEPTION), "testNotificationId", 10);
    assertThat(responseNotificationData).isNotNull();
    assertThat(responseNotificationData).isEqualTo(notificationData);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void notificationSavedFilterDataClientTest() throws IllegalAccessException, IOException {
    beginTimeMillis = System.currentTimeMillis();
    FieldUtils.writeField(errorTrackingService, "errorTrackingClient", errorTrackingClientMock, true);

    InvocationSummary invocationSummary = InvocationSummary.builder()
                                              .requestId(1)
                                              .harnessProjectId(1L)
                                              .harnessEnvironmentId(ENVIRONMENT)
                                              .harnessServiceId(SERVICE)
                                              .hits(0)
                                              .invocations(0)
                                              .firstSeen(new Timestamp(beginTimeMillis))
                                              .lastSeen(new Timestamp(beginTimeMillis))
                                              .build();

    Scorecard scorecard = Scorecard.builder()
                              .organizationIdentifier(ORG)
                              .accountIdentifier(ACCOUNT)
                              .projectIdentifier(PROJECT)
                              .serviceIdentifier(SERVICE)
                              .versionIdentifier(VERSION)
                              .environmentIdentifier(ENVIRONMENT)
                              .criticalHitCountThreshold(1)
                              .uniqueHitCountThreshold(2)
                              .newHitCountThreshold(3)
                              .newEventDefinition(NEVER_SEEN_BEFORE)
                              .criticalExceptions(Collections.singletonList("testCriticalException"))
                              .criticalHitCount(0)
                              .resurfacedHitCount(0)
                              .uniqueHitCount(0)
                              .newHitCount(0)
                              .hitCount(0)
                              .eventType(EventType.EXCEPTION)
                              .events(Collections.singletonList(invocationSummary))
                              .build();

    Long savedFilterId = 22L;
    final SavedFilter savedFilter = buildSavedFilter(savedFilterId);

    notificationData = ErrorTrackingNotificationData.builder()
                           .scorecards(Collections.singletonList(scorecard))
                           .from(new Timestamp(beginTimeMillis - FIVE_MINUTES))
                           .to(new Timestamp(beginTimeMillis))
                           .filter(savedFilter)
                           .build();
    Call<ErrorTrackingNotificationData> callMock = mock(Call.class);
    when(callMock.clone()).thenReturn(callMock);

    when(errorTrackingClientMock.getNotificationSavedFilterData(
             eq(ORG), eq(ACCOUNT), eq(PROJECT), eq(SERVICE), eq(ENVIRONMENT), anyLong(), anyInt(), anyString()))
        .thenReturn(callMock);

    when(callMock.execute()).thenReturn(Response.success(notificationData));

    final ErrorTrackingNotificationData responseNotificationData = errorTrackingService.getNotificationSavedFilterData(
        ORG, ACCOUNT, PROJECT, SERVICE, ENVIRONMENT, savedFilterId, 10, "testNotificationId");

    assertThat(responseNotificationData).isNotNull();
    assertThat(responseNotificationData).isEqualTo(notificationData);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void notificationNewDataClientTest() throws IllegalAccessException, IOException {
    beginTimeMillis = System.currentTimeMillis();
    FieldUtils.writeField(errorTrackingService, "errorTrackingClient", errorTrackingClientMock, true);

    ErrorTrackingHitSummary errorTrackingHitSummary = createHitSummary("testVersion1");
    List<ErrorTrackingHitSummary> errorTrackingHitSummaries = new ArrayList<>();
    errorTrackingHitSummaries.add(errorTrackingHitSummary);

    Call<List<ErrorTrackingHitSummary>> callMock = mock(Call.class);
    when(callMock.clone()).thenReturn(callMock);
    when(errorTrackingClientMock.getNotificationNewEvents(
             eq(ORG), eq(ACCOUNT), eq(PROJECT), eq(SERVICE), eq(ENVIRONMENT)))
        .thenReturn(callMock);
    when(callMock.execute()).thenReturn(Response.success(errorTrackingHitSummaries));

    final List<ErrorTrackingHitSummary> responseNotificationData =
        errorTrackingService.getNotificationNewData(ORG, ACCOUNT, PROJECT, SERVICE, ENVIRONMENT);
    assertThat(responseNotificationData).isNotNull();
    assertThat(responseNotificationData).isEqualTo(errorTrackingHitSummaries);
  }
}