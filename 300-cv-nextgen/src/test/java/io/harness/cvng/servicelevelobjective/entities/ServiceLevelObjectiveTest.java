/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail.TimeRangeFilter;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.MonthlyCalenderTarget;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.RollingSLOTarget;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.TimePeriod;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.WeeklyCalenderTarget;
import io.harness.rule.Owner;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceLevelObjectiveTest extends CategoryTest {
  private BuilderFactory builderFactory;
  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCurrentTimeRange_rolling() {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTarget(RollingSLOTarget.builder().periodLengthDays(30).build())
            .build();
    assertThat(serviceLevelObjective.getSloTarget().getCurrentTimeRange(LocalDateTime.parse("2021-12-03T10:15:00")))
        .isEqualTo(TimePeriod.createWithLocalTime(
            LocalDateTime.parse("2021-11-03T10:15:00"), LocalDateTime.parse("2021-12-03T10:15:00")));
    assertThat(serviceLevelObjective.getSloTarget().getCurrentTimeRange(LocalDateTime.parse("2021-12-31T10:15:00")))
        .isEqualTo(TimePeriod.createWithLocalTime(
            LocalDateTime.parse("2021-12-01T10:15:00"), LocalDateTime.parse("2021-12-31T10:15:00")));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCurrentTimeRange_calenderWeekly() {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTarget(WeeklyCalenderTarget.builder().dayOfWeek(DayOfWeek.MONDAY).build())
            .build();
    assertThat(serviceLevelObjective.getSloTarget().getCurrentTimeRange(LocalDateTime.parse("2021-12-03T10:15:00")))
        .isEqualTo(TimePeriod.builder()
                       .startDate(LocalDate.parse("2021-11-30"))
                       .endDate(LocalDate.parse("2021-12-07"))
                       .build());
    assertThat(serviceLevelObjective.getSloTarget().getCurrentTimeRange(LocalDateTime.parse("2021-12-06T10:15:00")))
        .isEqualTo(TimePeriod.builder()
                       .startDate(LocalDate.parse("2021-11-30"))
                       .endDate(LocalDate.parse("2021-12-07"))
                       .build());
    assertThat(serviceLevelObjective.getSloTarget()
                   .getCurrentTimeRange(LocalDateTime.parse("2021-01-01T10:15:15"))
                   .getStartTime())
        .isEqualTo(LocalDateTime.parse("2020-12-29T00:00:00"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCurrentTimeRange_calenderQuarterly() {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTarget(ServiceLevelObjective.QuarterlyCalenderTarget.builder().build())
            .build();
    assertThat(serviceLevelObjective.getSloTarget().getCurrentTimeRange(LocalDateTime.parse("2021-12-03T10:15:00")))
        .isEqualTo(TimePeriod.builder()
                       .startDate(LocalDate.parse("2021-10-01"))
                       .endDate(LocalDate.parse("2022-01-01"))
                       .build());
    assertThat(serviceLevelObjective.getSloTarget().getCurrentTimeRange(LocalDateTime.parse("2021-12-31T10:15:00")))
        .isEqualTo(TimePeriod.builder()
                       .startDate(LocalDate.parse("2021-10-01"))
                       .endDate(LocalDate.parse("2022-01-01"))
                       .build());
    assertThat(serviceLevelObjective.getSloTarget().getCurrentTimeRange(LocalDateTime.parse("2021-10-01T10:15:00")))
        .isEqualTo(TimePeriod.builder()
                       .startDate(LocalDate.parse("2021-10-01"))
                       .endDate(LocalDate.parse("2022-01-01"))
                       .build());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testTotalDays_Quarterly() {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTarget(ServiceLevelObjective.QuarterlyCalenderTarget.builder().build())
            .build();
    assertThat(serviceLevelObjective.getSloTarget()
                   .getCurrentTimeRange(LocalDateTime.parse("2021-12-03T10:15:00"))
                   .getTotalDays())
        .isEqualTo(92);
    assertThat(serviceLevelObjective.getSloTarget()
                   .getCurrentTimeRange(LocalDateTime.parse("2021-09-30T10:15:00"))
                   .getTotalDays())
        .isEqualTo(92);
    assertThat(serviceLevelObjective.getSloTarget()
                   .getCurrentTimeRange(LocalDateTime.parse("2021-05-27T10:15:00"))
                   .getTotalDays())
        .isEqualTo(91);
    assertThat(serviceLevelObjective.getSloTarget()
                   .getCurrentTimeRange(LocalDateTime.parse("2021-01-01T10:15:00"))
                   .getTotalDays())
        .isEqualTo(90);
    assertThat(serviceLevelObjective.getSloTarget()
                   .getCurrentTimeRange(LocalDateTime.parse("2021-02-01T10:15:15"))
                   .getStartTime())
        .isEqualTo(LocalDateTime.parse("2021-01-01T00:00:00"));
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testTotalDays_rolling() {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTarget(ServiceLevelObjective.RollingSLOTarget.builder().periodLengthDays(7).build())
            .build();
    assertThat(serviceLevelObjective.getSloTarget()
                   .getCurrentTimeRange(LocalDateTime.parse("2021-12-03T10:15:00"))
                   .getTotalDays())
        .isEqualTo(7);
    assertThat(serviceLevelObjective.getSloTarget()
                   .getCurrentTimeRange(LocalDateTime.parse("2021-09-30T10:15:00"))
                   .getTotalDays())
        .isEqualTo(7);
    assertThat(serviceLevelObjective.getSloTarget()
                   .getCurrentTimeRange(LocalDateTime.parse("2021-05-27T10:15:00"))
                   .getTotalDays())
        .isEqualTo(7);
    assertThat(serviceLevelObjective.getSloTarget()
                   .getCurrentTimeRange(LocalDateTime.parse("2021-01-01T10:15:00"))
                   .getTotalDays())
        .isEqualTo(7);
    assertThat(serviceLevelObjective.getSloTarget()
                   .getCurrentTimeRange(LocalDateTime.parse("2021-01-01T10:15:15"))
                   .getStartTime())
        .isEqualTo(LocalDateTime.parse("2020-12-25T10:15:00"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCurrentTimeRange_calenderMonthlyMiddleRange() {
    testCurrentTimeRangeMonthly(3, "2021-12-03", "2021-11-04", "2021-12-04");
    testCurrentTimeRangeMonthly(3, "2021-11-30", "2021-11-04", "2021-12-04");
    testCurrentTimeRangeMonthly(3, "2021-11-04", "2021-11-04", "2021-12-04");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetCurrentTimeRange_calenderMonthlyEndOfMonth() {
    testCurrentTimeRangeMonthly(31, "2021-12-03", "2021-12-01", "2022-01-01");
    testCurrentTimeRangeMonthly(31, "2021-11-30", "2021-11-01", "2021-12-01");
    testCurrentTimeRangeMonthly(31, "2021-02-28", "2021-02-01", "2021-03-01");
    testCurrentTimeRangeMonthly(28, "2021-12-03", "2021-11-29", "2021-12-29");
    testCurrentTimeRangeMonthly(28, "2021-11-30", "2021-11-29", "2021-12-29");
    testCurrentTimeRangeMonthly(28, "2021-11-28", "2021-10-29", "2021-11-29");
    testCurrentTimeRangeMonthly(28, "2021-03-01", "2021-03-01", "2021-03-29");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetTotalErrorBudgetMinutes() {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTargetPercentage(99.0)
            .sloTarget(WeeklyCalenderTarget.builder().dayOfWeek(DayOfWeek.MONDAY).build())
            .build();
    assertThat(serviceLevelObjective.getTotalErrorBudgetMinutes(LocalDateTime.parse("2021-12-03T10:15:00")))
        .isEqualTo(101);

    serviceLevelObjective = builderFactory.getServiceLevelObjectiveBuilder()
                                .sloTargetPercentage(99.0)
                                .sloTarget(MonthlyCalenderTarget.builder().windowEndDayOfMonth(28).build())
                                .build();
    assertThat(serviceLevelObjective.getTotalErrorBudgetMinutes(LocalDateTime.parse("2021-02-01T10:15:00")))
        .isEqualTo(446); // 31 days
    assertThat(serviceLevelObjective.getTotalErrorBudgetMinutes(LocalDateTime.parse("2021-01-01T10:15:00")))
        .isEqualTo(446);
    assertThat(serviceLevelObjective.getTotalErrorBudgetMinutes(LocalDateTime.parse("2021-03-01T10:15:00")))
        .isEqualTo(403); // 28 days
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetTimeRangeFilters_CalendarWeekly() {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTarget(ServiceLevelObjective.WeeklyCalenderTarget.builder().build())
            .build();
    List<TimeRangeFilter> timeRangeFilters = serviceLevelObjective.getTimeRangeFilters();
    assertThat(timeRangeFilters.get(0)).isEqualTo(TimeRangeFilter.ONE_HOUR_FILTER);
    assertThat(timeRangeFilters.get(1)).isEqualTo(TimeRangeFilter.ONE_DAY_FILTER);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetTimeRangeFilters_CalendarMonthly() {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTarget(ServiceLevelObjective.MonthlyCalenderTarget.builder().build())
            .build();
    List<TimeRangeFilter> timeRangeFilters = serviceLevelObjective.getTimeRangeFilters();
    assertThat(timeRangeFilters.get(0)).isEqualTo(TimeRangeFilter.ONE_HOUR_FILTER);
    assertThat(timeRangeFilters.get(1)).isEqualTo(TimeRangeFilter.ONE_DAY_FILTER);
    assertThat(timeRangeFilters.get(2)).isEqualTo(TimeRangeFilter.ONE_WEEK_FILTER);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetTimeRangeFilters_CalendarQuarterly() {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTarget(ServiceLevelObjective.QuarterlyCalenderTarget.builder().build())
            .build();
    List<TimeRangeFilter> timeRangeFilters = serviceLevelObjective.getTimeRangeFilters();
    assertThat(timeRangeFilters.get(0)).isEqualTo(TimeRangeFilter.ONE_HOUR_FILTER);
    assertThat(timeRangeFilters.get(1)).isEqualTo(TimeRangeFilter.ONE_DAY_FILTER);
    assertThat(timeRangeFilters.get(2)).isEqualTo(TimeRangeFilter.ONE_WEEK_FILTER);
    assertThat(timeRangeFilters.get(3)).isEqualTo(TimeRangeFilter.ONE_MONTH_FILTER);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetTimeRangeFilters_RollingLessThanWeek() {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTarget(ServiceLevelObjective.RollingSLOTarget.builder().periodLengthDays(5).build())
            .build();
    List<TimeRangeFilter> timeRangeFilters = serviceLevelObjective.getTimeRangeFilters();
    assertThat(timeRangeFilters.get(0)).isEqualTo(TimeRangeFilter.ONE_HOUR_FILTER);
    assertThat(timeRangeFilters.get(1)).isEqualTo(TimeRangeFilter.ONE_DAY_FILTER);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetTimeRangeFilters_RollingMoreThanWeek() {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTarget(ServiceLevelObjective.RollingSLOTarget.builder().periodLengthDays(10).build())
            .build();
    List<TimeRangeFilter> timeRangeFilters = serviceLevelObjective.getTimeRangeFilters();
    assertThat(timeRangeFilters.get(0)).isEqualTo(TimeRangeFilter.ONE_HOUR_FILTER);
    assertThat(timeRangeFilters.get(1)).isEqualTo(TimeRangeFilter.ONE_DAY_FILTER);
    assertThat(timeRangeFilters.get(2)).isEqualTo(TimeRangeFilter.ONE_WEEK_FILTER);
  }

  private void testCurrentTimeRangeMonthly(
      int windowEndDate, String currentDate, String expectedStartDate, String expectedEndDate) {
    ServiceLevelObjective serviceLevelObjective =
        builderFactory.getServiceLevelObjectiveBuilder()
            .sloTarget(MonthlyCalenderTarget.builder().windowEndDayOfMonth(windowEndDate).build())
            .build();
    TimePeriod timePeriod =
        serviceLevelObjective.getSloTarget().getCurrentTimeRange(LocalDate.parse(currentDate).atStartOfDay());
    assertThat(timePeriod)
        .isEqualTo(TimePeriod.builder()
                       .startDate(LocalDate.parse(expectedStartDate))
                       .endDate(LocalDate.parse(expectedEndDate))
                       .build());
  }
}
