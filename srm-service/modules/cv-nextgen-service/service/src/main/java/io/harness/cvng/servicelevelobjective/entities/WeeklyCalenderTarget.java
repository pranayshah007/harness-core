/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class WeeklyCalenderTarget extends CalenderSLOTarget {
  private DayOfWeek dayOfWeek;
  private final SLOCalenderType calenderType = SLOCalenderType.WEEKLY;

  @Override
  public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
    LocalDate nextDayOfWeek = dayOfWeek.getNextDayOfWeek(currentDateTime.toLocalDate());
    return TimePeriod.builder().startDate(nextDayOfWeek.minusDays(6)).endDate(nextDayOfWeek.plusDays(1)).build();
  }

  @Override
  public List<SLODashboardDetail.TimeRangeFilter> getTimeRangeFilters() {
    List<SLODashboardDetail.TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
    timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_HOUR_FILTER);
    timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_DAY_FILTER);
    return timeRangeFilterList;
  }
}
