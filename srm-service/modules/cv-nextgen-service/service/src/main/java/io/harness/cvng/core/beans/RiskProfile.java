/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.TimeSeriesThresholdType;

import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
@Builder
public class RiskProfile {
  @Deprecated private CVMonitoringCategory category;
  @Deprecated private TimeSeriesMetricType metricType;
  private RiskCategory riskCategory;
  @NotEmpty  private List<TimeSeriesThresholdType> thresholdTypes;

  public CVMonitoringCategory getCategory() {
    if (Objects.nonNull(riskCategory)) {
      return riskCategory.getCvMonitoringCategory();
    }
    return category;
  }

  public TimeSeriesMetricType getMetricType() {
    if (Objects.nonNull(riskCategory)) {
      return riskCategory.getTimeSeriesMetricType();
    }
    return metricType;
  }

  public RiskCategory getRiskCategory() {
    if (Objects.isNull(riskCategory) && Objects.nonNull(category) && Objects.nonNull(metricType)) {
      return RiskCategory.fromMetricAndCategory(metricType, category);
    }
    return riskCategory;
  }
}
