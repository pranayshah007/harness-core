/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation.estimators;

import static io.harness.ccm.commons.utils.ResourceAmountUtils.cpu;
import static io.harness.ccm.commons.utils.ResourceAmountUtils.makeResourceMap;
import static io.harness.ccm.commons.utils.ResourceAmountUtils.memory;
import static io.harness.ccm.commons.utils.ResourceAmountUtils.scaleResourceAmount;

import io.harness.batch.processing.config.k8s.recommendation.ContainerState;

import java.util.Map;
import lombok.Value;

/**
 * Adds a safety margin to the estimation from underlying estimator.
 */
@Value(staticConstructor = "of")
class MarginEstimator implements ResourceEstimator {
  double marginFraction;
  ResourceEstimator baseEstimator;

  @Override
  public Map<String, Long> getResourceEstimation(ContainerState containerState) {
    Map<String, Long> original = baseEstimator.getResourceEstimation(containerState);
    return makeResourceMap(scaleResourceAmount(cpu(original), 1 + marginFraction),
        scaleResourceAmount(memory(original), 1 + marginFraction));
  }
}
