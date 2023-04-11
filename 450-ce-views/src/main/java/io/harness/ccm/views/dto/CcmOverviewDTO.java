package io.harness.ccm.views.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "CCMOverview", description = "This object contains CCM Overview details")
public class CcmOverviewDTO {
  List<TimeSeriesDataPoints> costPerDay;
  Double totalCost;
  Double totalCostTrend;
  Integer recommendationsCount;
}
