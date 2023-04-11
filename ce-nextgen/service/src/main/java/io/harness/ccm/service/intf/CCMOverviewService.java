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
