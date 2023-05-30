/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.services.api.SLIAnalyserService;

import java.util.Map;
import java.util.Objects;

public class RatioAnalyserServiceImpl implements SLIAnalyserService<RatioSLIMetricSpec> {
  @Override
  public SLIState analyse(Map<String, Double> sliAnalyseRequest, RatioSLIMetricSpec sliSpec) {
    Double metricValue1 = sliAnalyseRequest.get(sliSpec.getMetric1());
    Double metricValue2 = sliAnalyseRequest.get(sliSpec.getMetric2());
    if (Objects.isNull(metricValue1) || Objects.isNull(metricValue2) || metricValue2 == 0) {
      return SLIState.NO_DATA;
    }
    double metricValue = sliSpec.getEventType().computeSLIMetricValue(metricValue1, metricValue2);

    if (sliSpec.getThresholdType().compute(metricValue, sliSpec.getThresholdValue())) {
      return SLIState.GOOD;
    } else {
      return SLIState.BAD;
    }
  }
}
