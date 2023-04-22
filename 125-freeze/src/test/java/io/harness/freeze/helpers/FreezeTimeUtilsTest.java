/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.freeze.beans.CurrentOrUpcomingWindow;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.Recurrence;
import io.harness.freeze.beans.RecurrenceSpec;
import io.harness.freeze.beans.RecurrenceType;
import io.harness.rule.Owner;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class FreezeTimeUtilsTest extends CategoryTest {
  public static final String timeZone = "Asia/Calcutta";

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_fetchUpcomingTimeWindows() {
    assertThat(FreezeTimeUtils.fetchUpcomingTimeWindow(null)).isEqualTo(new LinkedList<>());

    Recurrence recurrence = new Recurrence();
    recurrence.setRecurrenceType(RecurrenceType.DAILY);
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("2022-12-19 05:00 PM");
    freezeWindow.setStartTime("2022-12-19 04:30 PM");
    freezeWindow.setTimeZone(timeZone);
    freezeWindow.setRecurrence(recurrence);
    List<Long> nextIterations = FreezeTimeUtils.fetchUpcomingTimeWindow(Collections.singletonList(freezeWindow));
    assertThat(nextIterations.size()).isEqualTo(10);
    long currTime = new Date().getTime();
    assertThat(currTime).isLessThanOrEqualTo(nextIterations.get(0));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_validateFreezeYaml_1() {
    Recurrence recurrence = new Recurrence();
    recurrence.setRecurrenceType(RecurrenceType.DAILY);
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("2022-12-19 05:00 PM");
    freezeWindow.setStartTime("2022-12-19 04:30 PM");
    freezeWindow.setTimeZone("Asia/Calcutt");
    freezeWindow.setRecurrence(recurrence);
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow, FreezeStatus.ENABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Invalid TimeZone Selected"));
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow, FreezeStatus.DISABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Invalid TimeZone Selected"));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_currentAndUpcomingTimeWindows_WithoutRecurrence() {
    assertThat(FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(null)).isNull();

    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("2023-12-21 05:00 PM");
    freezeWindow.setStartTime("2023-01-21 04:30 PM");
    freezeWindow.setTimeZone(timeZone);
    List<FreezeWindow> windows = new ArrayList<>();
    windows.add(freezeWindow);
    CurrentOrUpcomingWindow currentOrUpcomingWindow = FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(windows);
    assertThat(currentOrUpcomingWindow.getStartTime()).isEqualTo(1674298800000L);
    assertThat(currentOrUpcomingWindow.getEndTime()).isEqualTo(1703158200000L);

    FreezeWindow freezeWindow1 = new FreezeWindow();
    freezeWindow1.setEndTime("2022-12-21 05:00 PM");
    freezeWindow1.setStartTime("2022-01-21 04:30 PM");
    freezeWindow1.setTimeZone(timeZone);
    List<FreezeWindow> windows1 = new ArrayList<>();
    windows1.add(freezeWindow1);
    assertThat(FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(windows1)).isNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_currentAndUpcomingTimeWindows_WithRecurrence() {
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("2023-01-19 05:00 PM");
    freezeWindow.setStartTime("2023-01-19 04:30 PM");
    freezeWindow.setTimeZone(timeZone);
    RecurrenceSpec recurrenceSpec = new RecurrenceSpec();
    recurrenceSpec.setUntil("2023-01-21 05:00 PM");
    Recurrence recurrence = new Recurrence();
    recurrence.setRecurrenceType(RecurrenceType.DAILY);
    recurrence.setSpec(recurrenceSpec);
    freezeWindow.setRecurrence(recurrence);
    List<FreezeWindow> windows = new ArrayList<>();
    windows.add(freezeWindow);
    assertThat(FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(windows)).isNull();

    FreezeWindow freezeWindow1 = new FreezeWindow();
    freezeWindow1.setDuration("30m");
    freezeWindow1.setStartTime("2023-01-21 04:30 PM");
    freezeWindow1.setTimeZone(timeZone);
    Recurrence recurrence1 = new Recurrence();
    recurrence1.setRecurrenceType(RecurrenceType.YEARLY);
    freezeWindow1.setRecurrence(recurrence1);
    List<FreezeWindow> windows1 = new ArrayList<>();
    windows1.add(freezeWindow1);
    CurrentOrUpcomingWindow currentOrUpcomingWindow = FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(windows1);
    assertThat(currentOrUpcomingWindow.getStartTime()).isEqualTo(1705834800000L);
    assertThat(currentOrUpcomingWindow.getEndTime()).isEqualTo(1705836600000L);

    FreezeWindow freezeWindow2 = new FreezeWindow();
    freezeWindow2.setDuration("30m");
    freezeWindow2.setStartTime("2027-01-21 04:30 PM");
    freezeWindow2.setTimeZone(timeZone);
    Recurrence recurrence2 = new Recurrence();
    recurrence2.setRecurrenceType(RecurrenceType.MONTHLY);
    RecurrenceSpec recurrenceSpec1 = new RecurrenceSpec();
    recurrenceSpec.setValue(3);
    recurrence2.setSpec(recurrenceSpec1);
    freezeWindow2.setRecurrence(recurrence2);
    List<FreezeWindow> windows2 = new ArrayList<>();
    windows2.add(freezeWindow2);
    CurrentOrUpcomingWindow currentOrUpcomingWindow1 = FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(windows2);
    assertThat(currentOrUpcomingWindow1.getStartTime()).isEqualTo(1800529200000L);
    assertThat(currentOrUpcomingWindow1.getEndTime()).isEqualTo(1800531000000L);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_fetchUpcomingTimeWindows_WithoutRecurrence() {
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("2025-12-19 05:00 PM");
    freezeWindow.setStartTime("2025-12-19 04:30 PM");
    freezeWindow.setTimeZone(timeZone);
    List<Long> nextIterations = FreezeTimeUtils.fetchUpcomingTimeWindow(Collections.singletonList(freezeWindow));
    assertThat(nextIterations.size()).isEqualTo(1);
    assertThat(nextIterations.get(0)).isEqualTo(1766142000000L);

    FreezeWindow freezeWindow1 = new FreezeWindow();
    freezeWindow1.setEndTime("2022-12-19 05:00 PM");
    freezeWindow1.setStartTime("2022-12-19 04:30 PM");
    freezeWindow1.setTimeZone(timeZone);
    List<Long> nextIterations1 = FreezeTimeUtils.fetchUpcomingTimeWindow(Collections.singletonList(freezeWindow1));
    assertThat(nextIterations1.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_fetchUpcomingTimeWindows_WithRecurrence() {
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("2023-01-19 05:00 PM");
    freezeWindow.setStartTime("2023-01-19 04:30 PM");
    freezeWindow.setTimeZone(timeZone);
    RecurrenceSpec recurrenceSpec = new RecurrenceSpec();
    recurrenceSpec.setUntil("2023-01-21 05:00 PM");
    Recurrence recurrence = new Recurrence();
    recurrence.setRecurrenceType(RecurrenceType.WEEKLY);
    recurrence.setSpec(recurrenceSpec);
    freezeWindow.setRecurrence(recurrence);
    List<FreezeWindow> windows = new ArrayList<>();
    windows.add(freezeWindow);
    assertThat(FreezeTimeUtils.fetchUpcomingTimeWindow(windows)).isEqualTo(new LinkedList<>());

    FreezeWindow freezeWindow1 = new FreezeWindow();
    freezeWindow1.setDuration("30m");
    freezeWindow1.setStartTime("2023-01-21 04:30 PM");
    freezeWindow1.setTimeZone(timeZone);
    Recurrence recurrence1 = new Recurrence();
    recurrence1.setRecurrenceType(RecurrenceType.YEARLY);
    freezeWindow1.setRecurrence(recurrence1);
    List<FreezeWindow> windows1 = new ArrayList<>();
    windows1.add(freezeWindow1);
    List<Long> nextWindow = FreezeTimeUtils.fetchUpcomingTimeWindow(windows1);
    assertThat(nextWindow.get(0)).isEqualTo(1705834800000L);

    FreezeWindow freezeWindow2 = new FreezeWindow();
    freezeWindow2.setDuration("30m");
    freezeWindow2.setStartTime("2027-01-21 04:30 PM");
    freezeWindow2.setTimeZone(timeZone);
    Recurrence recurrence2 = new Recurrence();
    recurrence2.setRecurrenceType(RecurrenceType.MONTHLY);
    RecurrenceSpec recurrenceSpec1 = new RecurrenceSpec();
    recurrenceSpec.setUntil("2023-01-21 05:00 PM");
    recurrenceSpec.setValue(3);
    recurrence2.setSpec(recurrenceSpec1);
    freezeWindow2.setRecurrence(recurrence2);
    List<FreezeWindow> windows2 = new ArrayList<>();
    windows2.add(freezeWindow2);
    List<Long> nextWindow1 = FreezeTimeUtils.fetchUpcomingTimeWindow(windows2);
    assertThat(nextWindow1.get(0)).isEqualTo(1800529200000L);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_CurrentOrUpcomingWindowIsActive() {
    CurrentOrUpcomingWindow currentOrUpcomingWindow1 =
        CurrentOrUpcomingWindow.builder().startTime(1671447600000L).endTime(1923908400000L).build();
    CurrentOrUpcomingWindow currentOrUpcomingWindow2 =
        CurrentOrUpcomingWindow.builder().startTime(1671447600000L).endTime(1674126000000L).build();
    assertThat(FreezeTimeUtils.currentWindowIsActive(currentOrUpcomingWindow1)).isEqualTo(true);
    assertThat(FreezeTimeUtils.currentWindowIsActive(currentOrUpcomingWindow2)).isEqualTo(false);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_GlobalFreezeIsActive() {
    assertThat(FreezeTimeUtils.getEpochValueFromDateString(null, any())).isEqualTo(null);
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("2025-01-19 05:00 PM");
    freezeWindow.setStartTime("2025-01-19 04:30 PM");
    freezeWindow.setTimeZone(timeZone);
    RecurrenceSpec recurrenceSpec = new RecurrenceSpec();
    recurrenceSpec.setUntil("2025-02-21 05:00 PM");
    Recurrence recurrence = new Recurrence();
    recurrence.setRecurrenceType(RecurrenceType.MONTHLY);
    recurrence.setSpec(recurrenceSpec);
    freezeWindow.setRecurrence(recurrence);
    assertThat(FreezeTimeUtils.globalFreezeIsActive(freezeWindow)).isEqualTo(false);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_setCurrWindowStartAndEndTime() {
    TimeZone timezone = TimeZone.getTimeZone(timeZone);
    LocalDateTime startTime = Instant.ofEpochMilli(1737284400000L).atZone(timezone.toZoneId()).toLocalDateTime();
    LocalDateTime endTime = Instant.ofEpochMilli(1737288000000L).atZone(timezone.toZoneId()).toLocalDateTime();
    Pair<LocalDateTime, LocalDateTime> windowTimes =
        FreezeTimeUtils.setCurrWindowStartAndEndTime(startTime, endTime, RecurrenceType.DAILY, timezone);
    assertThat(windowTimes.getLeft()).isEqualTo(startTime);
    assertThat(windowTimes.getRight()).isEqualTo(endTime);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_validateTimeRange_1() {
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("2035-01-19 05:00 PM");
    freezeWindow.setStartTime("2035-01-19 04:30 PM");
    RecurrenceSpec recurrenceSpec = new RecurrenceSpec();
    recurrenceSpec.setUntil("2025-02-21 05:00 PM");
    Recurrence recurrence = new Recurrence();
    recurrence.setRecurrenceType(RecurrenceType.MONTHLY);
    recurrence.setSpec(recurrenceSpec);
    freezeWindow.setRecurrence(recurrence);
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow, FreezeStatus.ENABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Time zone cannot be empty"));
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow, FreezeStatus.DISABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Time zone cannot be empty"));
    freezeWindow.setTimeZone(timeZone);
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow, FreezeStatus.ENABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Freeze window start time should be less than 5 years"));
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow, FreezeStatus.DISABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Freeze window start time should be less than 5 years"));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_validateTimeRange_2() {
    FreezeWindow freezeWindow = new FreezeWindow();
    freezeWindow.setEndTime("2025-01-19 04:00 PM");
    freezeWindow.setStartTime("2025-01-19 04:30 PM");
    freezeWindow.setTimeZone(timeZone);
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow, FreezeStatus.ENABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Window Start time is greater than Window end Time"));

    FreezeWindow freezeWindow1 = new FreezeWindow();
    freezeWindow1.setEndTime("2025-01-19 05:00 PM");
    freezeWindow1.setStartTime("2025-01-19 04:35 PM");
    freezeWindow1.setTimeZone(timeZone);
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow1, FreezeStatus.ENABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Freeze window time should be at least 30 minutes"));
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow1, FreezeStatus.DISABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Freeze window time should be at least 30 minutes"));

    FreezeWindow freezeWindow2 = new FreezeWindow();
    freezeWindow2.setEndTime("2026-01-19 05:00 PM");
    freezeWindow2.setStartTime("2025-01-19 04:30 PM");
    freezeWindow2.setTimeZone(timeZone);
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow2, FreezeStatus.ENABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Freeze window time should be less than 365 days"));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_validateTimeRange_3() {
    FreezeWindow freezeWindow1 = new FreezeWindow();
    freezeWindow1.setEndTime("2022-01-19 05:00 PM");
    freezeWindow1.setStartTime("2022-01-19 04:30 PM");
    freezeWindow1.setTimeZone(timeZone);
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow1, FreezeStatus.ENABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Freeze Window is already expired"));

    FreezeWindow freezeWindow2 = new FreezeWindow();
    freezeWindow2.setEndTime("2025-01-19 05:00 PM");
    freezeWindow2.setStartTime("2025-01-19 04:30 PM");
    freezeWindow2.setTimeZone(timeZone);
    assertThatCode(() -> FreezeTimeUtils.validateTimeRange(freezeWindow2, FreezeStatus.ENABLED))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_validateTimeRange_4() {
    FreezeWindow freezeWindow1 = new FreezeWindow();
    freezeWindow1.setEndTime("2022-01-19 05:00 PM");
    freezeWindow1.setStartTime("2022-01-19 04:30 PM");
    freezeWindow1.setTimeZone(timeZone);
    RecurrenceSpec recurrenceSpec1 = new RecurrenceSpec();
    recurrenceSpec1.setUntil("2025-02-21 05:00 PM");
    Recurrence recurrence1 = new Recurrence();
    recurrence1.setSpec(recurrenceSpec1);
    freezeWindow1.setRecurrence(recurrence1);
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow1, FreezeStatus.ENABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("Recurrence Type cannot be empty"));

    FreezeWindow freezeWindow2 = new FreezeWindow();
    freezeWindow2.setEndTime("2022-01-19 05:00 PM");
    freezeWindow2.setStartTime("2022-01-19 04:30 PM");
    freezeWindow2.setTimeZone(timeZone);
    RecurrenceSpec recurrenceSpec2 = new RecurrenceSpec();
    recurrenceSpec2.setUntil("2022-02-21 05:00 PM");
    Recurrence recurrence2 = new Recurrence();
    recurrence2.setSpec(recurrenceSpec2);
    recurrence2.setRecurrenceType(RecurrenceType.DAILY);
    freezeWindow2.setRecurrence(recurrence2);
    assertThatThrownBy(() -> FreezeTimeUtils.validateTimeRange(freezeWindow2, FreezeStatus.ENABLED))
        .isInstanceOf(InvalidRequestException.class)
        .matches(ex -> ex.getMessage().equals("End time for recurrence cannot be less than current time"));

    FreezeWindow freezeWindow3 = new FreezeWindow();
    freezeWindow3.setEndTime("2025-01-19 05:00 PM");
    freezeWindow3.setStartTime("2025-01-19 04:30 PM");
    freezeWindow3.setTimeZone(timeZone);
    RecurrenceSpec recurrenceSpec3 = new RecurrenceSpec();
    recurrenceSpec3.setUntil("2025-02-21 05:00 PM");
    Recurrence recurrence3 = new Recurrence();
    recurrence3.setSpec(recurrenceSpec3);
    recurrence3.setRecurrenceType(RecurrenceType.DAILY);
    freezeWindow3.setRecurrence(recurrence3);
    assertThatCode(() -> FreezeTimeUtils.validateTimeRange(freezeWindow3, FreezeStatus.ENABLED))
        .doesNotThrowAnyException();
  }
}