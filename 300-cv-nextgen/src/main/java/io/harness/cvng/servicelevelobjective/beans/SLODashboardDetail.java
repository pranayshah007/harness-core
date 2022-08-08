/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import java.time.Duration;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SLODashboardDetail {
  SLODashboardWidget sloDashboardWidget;
  String description;
  Long createdAt;
  Long lastModifiedAt;
  List<TimeRangeFilter> timeRangeFilters;

  @Value
  @Builder
  public static class TimeRangeFilter {
    public static final TimeRangeFilter ONE_HOUR_FILTER =
        TimeRangeFilter.builder().displayName("1 Hour").durationMilliSeconds(Duration.ofHours(1).toMillis()).build();
    public static final TimeRangeFilter ONE_DAY_FILTER =
        TimeRangeFilter.builder().displayName("1 Day").durationMilliSeconds(Duration.ofDays(1).toMillis()).build();
    public static final TimeRangeFilter ONE_WEEK_FILTER =
        TimeRangeFilter.builder().displayName("1 Week").durationMilliSeconds(Duration.ofDays(7).toMillis()).build();
    public static final TimeRangeFilter ONE_MONTH_FILTER =
        TimeRangeFilter.builder().displayName("1 Month").durationMilliSeconds(Duration.ofDays(31).toMillis()).build();

    String displayName;
    long durationMilliSeconds;
  }
}
