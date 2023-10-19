/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import static io.harness.cvng.servicelevelobjective.beans.QuarterStart.getFirstDayOfQuarter;

import io.harness.cvng.servicelevelobjective.beans.QuarterStart;
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
public class QuarterlyCalenderTarget extends CalenderSLOTarget {
  private final SLOCalenderType calenderType = SLOCalenderType.QUARTERLY;
  QuarterStart quarterStart;

  @Override
  public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
    LocalDate firstDayOfQuarter = getFirstDayOfQuarter(quarterStart, currentDateTime);

    LocalDate lastDayOfQuarter = firstDayOfQuarter.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
    return TimePeriod.builder().startDate(firstDayOfQuarter).endDate(lastDayOfQuarter.plusDays(1)).build();
  }

  @Override
  public TimePeriod getTimeRangeForHistory(LocalDateTime currentDateTime) {
    LocalDate firstDayOfQuarter = getFirstDayOfQuarter(quarterStart, currentDateTime.minusMonths(3));

    LocalDate lastDayOfQuarter = firstDayOfQuarter.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
    return TimePeriod.builder().startDate(firstDayOfQuarter).endDate(lastDayOfQuarter.plusDays(1)).build();
  }

  @Override
  public List<SLODashboardDetail.TimeRangeFilter> getTimeRangeFilters() {
    List<SLODashboardDetail.TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
    timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_HOUR_FILTER);
    timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_DAY_FILTER);
    timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_WEEK_FILTER);
    timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_MONTH_FILTER);
    return timeRangeFilterList;
  }

  public QuarterStart getQuarterStart() {
    if (quarterStart.getStartQuarter() == null) {
      return QuarterStart.JAN_APR_JUL_OCT;
    } else {
      return quarterStart;
    }
  }
}
