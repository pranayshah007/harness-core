/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
public class RollingSLOTarget extends SLOTarget {
  int periodLengthDays;
  private final SLOTargetType type = SLOTargetType.ROLLING;

  @Override
  public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
    currentDateTime = currentDateTime.truncatedTo(ChronoUnit.MINUTES);
    return TimePeriod.createWithLocalTime(
        currentDateTime.minusMinutes(TimeUnit.DAYS.toMinutes(periodLengthDays)), currentDateTime);
  }

  @Override
  public List<SLODashboardDetail.TimeRangeFilter> getTimeRangeFilters() {
    List<SLODashboardDetail.TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
    timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_HOUR_FILTER);
    timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_DAY_FILTER);
    if (this.periodLengthDays > 7) {
      timeRangeFilterList.add(SLODashboardDetail.TimeRangeFilter.ONE_WEEK_FILTER);
    }
    return timeRangeFilterList;
  }
}
