/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation.estimators;

import static io.harness.ccm.commons.utils.ResourceAmountUtils.cpuAmountFromCores;
import static io.harness.ccm.commons.utils.ResourceAmountUtils.makeResourceMap;
import static io.harness.ccm.commons.utils.ResourceAmountUtils.memoryAmountFromBytes;

import io.harness.batch.processing.config.k8s.recommendation.ContainerState;

import java.util.Map;
import lombok.Value;

/**
 * Returns an estimation based on specific percentiles of cpu usage & memory peak distribution.
 */
@Value(staticConstructor = "of")
class PercentileEstimator implements ResourceEstimator {
  double cpuPercentile;
  double memoryPercentile;

  @Override
  public Map<String, Long> getResourceEstimation(ContainerState containerState) {
    return makeResourceMap(cpuAmountFromCores(containerState.getCpuHistogram().getPercentile(cpuPercentile)),
        memoryAmountFromBytes(containerState.getMemoryHistogram().getPercentile(memoryPercentile)));
  }
}
