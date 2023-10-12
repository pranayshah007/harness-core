/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import static io.harness.rule.OwnerRule.SHASHWAT_SACHAN;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.QuarterStart;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLOTargetTest extends CvNextGenTestBase {
  @Inject Clock clock;

  @Before
  public void setUp() {
    clock = CVNGTestConstants.FIXED_TIME_FOR_TESTS;
  }

  @Test()
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGeTimeRangeForRolling() {
    SLOTarget sloTarget = RollingSLOTarget.builder().periodLengthDays(5).build();
    TimePeriod currentTimeRange =
        sloTarget.getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    TimePeriod prevTimeRange =
        sloTarget.getTimeRangeForHistory(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    assertThat(currentTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(clock.instant().minus(5, ChronoUnit.DAYS), ZoneOffset.UTC));
    assertThat(currentTimeRange.getEndTime()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

    assertThat(prevTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(clock.instant().minus(5, ChronoUnit.DAYS), ZoneOffset.UTC));
    assertThat(prevTimeRange.getEndTime()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
  }

  @Test()
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGeTimeRangeForWeeklyCalendar() {
    SLOTarget sloTarget = WeeklyCalenderTarget.builder().dayOfWeek(DayOfWeek.SUNDAY).build();
    TimePeriod currentTimeRange =
        sloTarget.getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    TimePeriod prevTimeRange =
        sloTarget.getTimeRangeForHistory(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    assertThat(currentTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-07-27T00:00:00Z"), ZoneOffset.UTC));
    assertThat(currentTimeRange.getEndTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-08-03T00:00:00Z"), ZoneOffset.UTC));

    assertThat(prevTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-07-20T00:00:00Z"), ZoneOffset.UTC));
    assertThat(prevTimeRange.getEndTime()).isEqualTo(currentTimeRange.getStartTime());
  }

  @Test()
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGeTimeRangeForMonthlyCalendar() {
    SLOTarget sloTarget = MonthlyCalenderTarget.builder().windowEndDayOfMonth(30).build();
    TimePeriod currentTimeRange =
        sloTarget.getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    TimePeriod prevTimeRange =
        sloTarget.getTimeRangeForHistory(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    assertThat(currentTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-07-01T00:00:00Z"), ZoneOffset.UTC));
    assertThat(currentTimeRange.getEndTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-08-01T00:00:00Z"), ZoneOffset.UTC));

    assertThat(prevTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-06-01T00:00:00Z"), ZoneOffset.UTC));
    assertThat(prevTimeRange.getEndTime()).isEqualTo(currentTimeRange.getStartTime());

    sloTarget = MonthlyCalenderTarget.builder().windowEndDayOfMonth(15).build();
    currentTimeRange = sloTarget.getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    prevTimeRange = sloTarget.getTimeRangeForHistory(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    assertThat(currentTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-07-16T00:00:00Z"), ZoneOffset.UTC));
    assertThat(currentTimeRange.getEndTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-08-16T00:00:00Z"), ZoneOffset.UTC));

    assertThat(prevTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-06-16T00:00:00Z"), ZoneOffset.UTC));
    assertThat(prevTimeRange.getEndTime()).isEqualTo(currentTimeRange.getStartTime());

    sloTarget = MonthlyCalenderTarget.builder().windowEndDayOfMonth(30).build();
    currentTimeRange =
        sloTarget.getCurrentTimeRange(LocalDateTime.ofInstant(Instant.parse("2020-06-27T10:50:00Z"), ZoneOffset.UTC));
    prevTimeRange = sloTarget.getTimeRangeForHistory(
        LocalDateTime.ofInstant(Instant.parse("2020-06-27T10:50:00Z"), ZoneOffset.UTC));
    assertThat(currentTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-06-01T00:00:00Z"), ZoneOffset.UTC));
    assertThat(currentTimeRange.getEndTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-07-01T00:00:00Z"), ZoneOffset.UTC));

    assertThat(prevTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-05-01T00:00:00Z"), ZoneOffset.UTC));
    assertThat(prevTimeRange.getEndTime()).isEqualTo(currentTimeRange.getStartTime());
  }

  @Test()
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGeTimeRangeForQuarterlyCalendar() {
    SLOTarget sloTarget = QuarterlyCalenderTarget.builder().build();
    TimePeriod currentTimeRange =
        sloTarget.getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    TimePeriod prevTimeRange =
        sloTarget.getTimeRangeForHistory(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    assertThat(currentTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-07-01T00:00:00Z"), ZoneOffset.UTC));
    assertThat(currentTimeRange.getEndTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-10-01T00:00:00Z"), ZoneOffset.UTC));

    assertThat(prevTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-04-01T00:00:00Z"), ZoneOffset.UTC));
    assertThat(prevTimeRange.getEndTime()).isEqualTo(currentTimeRange.getStartTime());
  }

  @Test()
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testGeTimeRangeForQuarterlyCalendarWithQuarterCycle2() {
    SLOTarget sloTarget = QuarterlyCalenderTarget.builder().quarterStart(QuarterStart.FEB_MAY_AUG_NOV).build();
    TimePeriod currentTimeRange =
        sloTarget.getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    TimePeriod prevTimeRange =
        sloTarget.getTimeRangeForHistory(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    assertThat(currentTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-05-01T00:00:00Z"), ZoneOffset.UTC));
    assertThat(currentTimeRange.getEndTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-08-01T00:00:00Z"), ZoneOffset.UTC));

    assertThat(prevTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-02-01T00:00:00Z"), ZoneOffset.UTC));
    assertThat(prevTimeRange.getEndTime()).isEqualTo(currentTimeRange.getStartTime());
  }

  @Test()
  @Owner(developers = SHASHWAT_SACHAN)
  @Category(UnitTests.class)
  public void testGeTimeRangeForQuarterlyCalendarWithQuarterCycle3() {
    SLOTarget sloTarget = QuarterlyCalenderTarget.builder().quarterStart(QuarterStart.MAR_JUN_SEP_DEC).build();
    TimePeriod currentTimeRange =
        sloTarget.getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    TimePeriod prevTimeRange =
        sloTarget.getTimeRangeForHistory(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    assertThat(currentTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-06-01T00:00:00Z"), ZoneOffset.UTC));
    assertThat(currentTimeRange.getEndTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-09-01T00:00:00Z"), ZoneOffset.UTC));

    assertThat(prevTimeRange.getStartTime())
        .isEqualTo(LocalDateTime.ofInstant(Instant.parse("2020-03-01T00:00:00Z"), ZoneOffset.UTC));
    assertThat(prevTimeRange.getEndTime()).isEqualTo(currentTimeRange.getStartTime());
  }
}
