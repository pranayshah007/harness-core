/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation.estimators;

import io.harness.batch.processing.config.k8s.recommendation.ContainerState;

import java.util.Map;
import java.util.Set;

interface ResourceEstimator {
  // cpu in millicores, memory in bytes
  Map<String, Long> getResourceEstimation(ContainerState containerState);

  default ResourceEstimator withMargin(double marginFraction) {
    return MarginEstimator.of(marginFraction, this);
  }

  default ResourceEstimator withMinResources(Map<String, Long> minResources) {
    return MinResourceEstimator.of(minResources, this);
  }

  default ResourceEstimator withConfidenceMultiplier(double multiplier, double exponent) {
    return ConfidenceMultiplierEstimator.of(multiplier, exponent, this);
  }

  default ResourceEstimator omitResources(String... omittedResources) {
    return new OmitResourceEstimator(omittedResources, this);
  }

  default ResourceEstimator omitResources(Set<String> noLimitResources) {
    return new OmitResourceEstimator(noLimitResources, this);
  }
}
