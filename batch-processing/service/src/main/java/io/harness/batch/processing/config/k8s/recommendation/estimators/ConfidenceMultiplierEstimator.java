/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation.estimators;

import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.cpu;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.makeResourceMap;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.memory;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ResourceAmountUtils.scaleResourceAmount;

import io.harness.batch.processing.config.k8s.recommendation.ContainerState;

import java.time.Duration;
import java.util.Map;
import lombok.Value;

/**
 * Returns resources computed by the underlying estimator, scaled based on the
 * confidence metric, which depends on the amount of available historical data.
 * Each resource is transformed as follows:
 *     scaledResource = originalResource * (1 + 1/confidence)^exponent.
 * This can be used to widen or narrow the gap between the lower and upper bound
 * estimators depending on how much input data is available to the estimators.
 */
@Value(staticConstructor = "of")
class ConfidenceMultiplierEstimator implements ResourceEstimator {
  double multiplier;
  double exponent;
  ResourceEstimator baseEstimator;

  @Override
  public Map<String, Long> getResourceEstimation(ContainerState containerState) {
    double confidence = getConfidence(containerState);
    Map<String, Long> original = baseEstimator.getResourceEstimation(containerState);
    double factor = Math.pow(1.0 + multiplier / confidence, exponent);
    return makeResourceMap(scaleResourceAmount(cpu(original), factor), scaleResourceAmount(memory(original), factor));
  }

  /*
   Returns a non-negative real number that heuristically measures how much
   confidence the history aggregated in the ContainerState provides.
   For a workload producing a steady stream of samples over N days at the rate
   of 1 sample per minute, this metric is equal to N.
   This implementation is a very simple heuristic which looks at the total count
   of samples and the time between the first and the last sample.
  */
  double getConfidence(ContainerState cs) {
    int millisPerDay = 86400_000;
    // Distance between the first and the last observed sample time, measured in days (fractional)
    double lifespanInDays =
        (double) Duration.between(cs.getFirstSampleStart(), cs.getLastSampleStart()).toMillis() / millisPerDay;
    // Total count of samples normalized such that it equals the number of days for
    // frequency of 1 sample/minute
    double samplesAmount = ((double) cs.getTotalSamplesCount()) / (60 * 24);
    return Math.min(lifespanInDays, samplesAmount);
  }
}
