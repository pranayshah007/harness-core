/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ViewQueryParams {
  String accountId;
  boolean isTimeTruncGroupByRequired;
  boolean isUsedByTimeSeriesStats; // only true when grid call is made to generate filters for charts
  boolean isClusterQuery;
  boolean isTotalCountQuery; // only true while calculating total number of rows returned by query
  int timeOffsetInDays; // time offset in case of budget timeSeries query
  boolean skipRoundOff;
  boolean skipDefaultGroupBy;
  boolean skipGroupBy; // Skipping groupBy for total cost queries
}
