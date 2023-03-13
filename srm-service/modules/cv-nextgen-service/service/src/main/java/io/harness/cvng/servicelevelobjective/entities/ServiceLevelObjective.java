/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail.TimeRangeFilter;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
public class ServiceLevelObjective {
  @Data
  @SuperBuilder
  @EqualsAndHashCode
  public abstract static class SLOTarget {
    public abstract TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime);
    public abstract SLOTargetType getType();
    public abstract List<TimeRangeFilter> getTimeRangeFilters();
  }
  @Data
  @SuperBuilder
  @EqualsAndHashCode(callSuper = true)
  public abstract static class CalenderSLOTarget extends SLOTarget {
    private final SLOTargetType type = SLOTargetType.CALENDER;
    public abstract SLOCalenderType getCalenderType();
  }

  @Value
  @SuperBuilder
  @EqualsAndHashCode(callSuper = true)
  public static class WeeklyCalenderTarget extends CalenderSLOTarget {
    private DayOfWeek dayOfWeek;
    private final SLOCalenderType calenderType = SLOCalenderType.WEEKLY;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      LocalDate nextDayOfWeek = dayOfWeek.getNextDayOfWeek(currentDateTime.toLocalDate());
      return TimePeriod.builder().startDate(nextDayOfWeek.minusDays(6)).endDate(nextDayOfWeek.plusDays(1)).build();
    }

    @Override
    public List<TimeRangeFilter> getTimeRangeFilters() {
      List<TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_DAY_FILTER);
      return timeRangeFilterList;
    }
  }

  @SuperBuilder
  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class MonthlyCalenderTarget extends CalenderSLOTarget {
    int windowEndDayOfMonth;
    private final SLOCalenderType calenderType = SLOCalenderType.MONTHLY;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      LocalDate windowStart =
          getWindowEnd(currentDateTime.toLocalDate().minusMonths(1), windowEndDayOfMonth).plusDays(1);
      LocalDate windowEnd = getWindowEnd(currentDateTime.toLocalDate(), windowEndDayOfMonth).plusDays(1);
      return TimePeriod.builder().startDate(windowStart).endDate(windowEnd).build();
    }

    @Override
    public List<TimeRangeFilter> getTimeRangeFilters() {
      List<TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_DAY_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_WEEK_FILTER);
      return timeRangeFilterList;
    }

    private LocalDate getWindowEnd(LocalDate currentDateTime, int windowEndDayOfMonth) {
      LocalDate windowEnd;
      if (windowEndDayOfMonth > 28) {
        windowEnd = currentDateTime.with(TemporalAdjusters.lastDayOfMonth());
      } else if (currentDateTime.getDayOfMonth() <= windowEndDayOfMonth) {
        windowEnd = getWindowEnd(currentDateTime);
      } else {
        windowEnd = getWindowEnd(currentDateTime.plusMonths(1));
      }
      return windowEnd;
    }

    private LocalDate getWindowEnd(LocalDate date) {
      return date.plusDays(windowEndDayOfMonth - date.getDayOfMonth());
    }
  }

  @SuperBuilder
  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class QuarterlyCalenderTarget extends CalenderSLOTarget {
    private final SLOCalenderType calenderType = SLOCalenderType.QUARTERLY;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      LocalDate firstDayOfQuarter = currentDateTime.toLocalDate()
                                        .with(currentDateTime.toLocalDate().getMonth().firstMonthOfQuarter())
                                        .with(TemporalAdjusters.firstDayOfMonth());

      LocalDate lastDayOfQuarter = firstDayOfQuarter.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
      return TimePeriod.builder().startDate(firstDayOfQuarter).endDate(lastDayOfQuarter.plusDays(1)).build();
    }

    @Override
    public List<TimeRangeFilter> getTimeRangeFilters() {
      List<TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_DAY_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_WEEK_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_MONTH_FILTER);
      return timeRangeFilterList;
    }
  }

  @SuperBuilder
  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class RollingSLOTarget extends SLOTarget {
    int periodLengthDays;
    private final SLOTargetType type = SLOTargetType.ROLLING;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      currentDateTime = currentDateTime.truncatedTo(ChronoUnit.MINUTES);
      return TimePeriod.createWithLocalTime(
          currentDateTime.minusMinutes(TimeUnit.DAYS.toMinutes(periodLengthDays)), currentDateTime);
    }

    @Override
    public List<TimeRangeFilter> getTimeRangeFilters() {
      List<TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_DAY_FILTER);
      if (this.periodLengthDays > 7) {
        timeRangeFilterList.add(TimeRangeFilter.ONE_WEEK_FILTER);
      }
      return timeRangeFilterList;
    }
  }
}
