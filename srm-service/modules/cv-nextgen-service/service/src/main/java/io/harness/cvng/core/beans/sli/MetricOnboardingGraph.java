/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.sli;

import io.harness.cvng.core.beans.TimeGraphResponse;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@Builder
public class MetricOnboardingGraph {
  Map<String, SLIOnboardingGraphs.MetricGraph> metricGraphs;

  RatioMetricPercentageGraph metricPercentageGraph;

  @Data
  @NoArgsConstructor
  @SuperBuilder
  public static class RatioMetricPercentageGraph extends TimeGraphResponse {
    private String metricIdentifier1;

    private String metricIdentifier2;
  }
}
