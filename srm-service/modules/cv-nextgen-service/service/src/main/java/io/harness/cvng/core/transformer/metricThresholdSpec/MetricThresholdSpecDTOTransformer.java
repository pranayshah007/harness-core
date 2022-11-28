/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.metricThresholdSpec;

import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.FailMetricThresholdSpec;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.IgnoreMetricThresholdSpec;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricCustomThresholdActions;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdSpec;
import io.harness.cvng.core.entities.TimeSeriesThreshold;

public class MetricThresholdSpecDTOTransformer {
  public static MetricThresholdSpec getDto(TimeSeriesThreshold baseMetricThreshold) {
    switch (baseMetricThreshold.getAction()) {
      case IGNORE:
        return IgnoreMetricThresholdSpec.builder()
            .action(MetricCustomThresholdActions.getMetricCustomThresholdActions(
                baseMetricThreshold.getCriteria().getAction()))
            .build();
      case FAIL:
        return FailMetricThresholdSpec.builder()
            .action(MetricCustomThresholdActions.getMetricCustomThresholdActions(
                baseMetricThreshold.getCriteria().getAction()))
            .spec(FailMetricThresholdSpec
                      .FailMetricCustomThresholdSpec

                      .builder()
                      .count(baseMetricThreshold.getCriteria().getOccurrenceCount())
                      .build())
            .build();
      default:
        throw new IllegalArgumentException("Invalid value: " + baseMetricThreshold.getAction());
    }
  }
}
