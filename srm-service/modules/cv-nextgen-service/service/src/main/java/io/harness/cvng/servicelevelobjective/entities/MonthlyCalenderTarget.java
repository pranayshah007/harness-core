/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
public class MonthlyCalenderTarget extends CalenderSLOTarget {
  int windowEndDayOfMonth;
  private final SLOCalenderType calenderType = SLOCalenderType.MONTHLY;

  @Override
  public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
    LocalDate windowStart = getWindowEnd(currentDateTime.toLocalDate().minusMonths(1), windowEndDayOfMonth).plusDays(1);
    LocalDate windowEnd = getWindowEnd(currentDateTime.toLocalDate(), windowEndDayOfMonth).plusDays(1);
    return TimePeriod.builder().startDate(windowStart).endDate(windowEnd).build();
  }

  @Override
  public List<SLODashboardDetail.TimeRangeFilter> getTimeRangeFilters() {
    List<SLODashboardDetail.TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
    timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_HOUR_FILTER);
    timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_DAY_FILTER);
    timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_WEEK_FILTER);
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
