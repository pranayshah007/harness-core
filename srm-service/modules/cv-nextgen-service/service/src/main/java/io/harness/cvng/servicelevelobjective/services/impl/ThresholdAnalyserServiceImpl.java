/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.services.api.SLIAnalyserService;

import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThresholdAnalyserServiceImpl implements SLIAnalyserService<ThresholdSLIMetricSpec> {
  @Override
  public SLIState analyse(Map<String, Double> sliAnalyseRequest, ThresholdSLIMetricSpec sliSpec) {
    Double metricValue = sliAnalyseRequest.get(sliSpec.getMetric1());
    if (Objects.isNull(metricValue)) {
      return SLIState.NO_DATA;
    }
    if (sliSpec.getThresholdType().compute(metricValue, sliSpec.getThresholdValue())) {
      return SLIState.GOOD;
    } else {
      return SLIState.BAD;
    }
  }
}
