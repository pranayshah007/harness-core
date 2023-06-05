/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.intf;

import io.harness.ccm.remote.beans.CostOverviewDTO;
import io.harness.ccm.views.dto.CcmOverviewDTO;
import io.harness.ccm.views.dto.TimeSeriesDataPoints;
import io.harness.ccm.views.graphql.QLCEViewTimeGroupType;

import java.util.List;

public interface CCMOverviewService {
  CcmOverviewDTO getCCMAccountOverviewData(
      String accountId, long startTime, long endTime, QLCEViewTimeGroupType groupBy);
  List<TimeSeriesDataPoints> getCostTimeSeriesData(
      String accountId, long startTime, long endTime, QLCEViewTimeGroupType groupBy);
  CostOverviewDTO getTotalCostStats(String accountId, long startTime, long endTime);
  Integer getRecommendationsCount(String accountId);
}
