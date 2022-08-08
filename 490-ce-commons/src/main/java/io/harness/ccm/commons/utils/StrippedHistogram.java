/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.utils;

import io.harness.histogram.HistogramCheckpoint;

import java.util.Arrays;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StrippedHistogram {
  double[] bucketWeights;
  int numBuckets;
  int minBucket;
  int maxBucket;

  public static StrippedHistogram fromCheckpoint(HistogramCheckpoint histogram, int numBuckets) {
    double[] bucketWeights = bucketWeightsMapToArr(histogram, numBuckets);
    return stripZeroes(bucketWeights);
  }

  private static double[] bucketWeightsMapToArr(HistogramCheckpoint histogram, int numBuckets) {
    double[] bucketWeightsArr = new double[numBuckets];
    long sum = 0;
    for (Integer weight : histogram.getBucketWeights().values()) {
      sum += weight;
    }
    if (sum != 0) {
      double ratio = histogram.getTotalWeight() / sum;
      for (int i = 0; i < numBuckets; i++) {
        bucketWeightsArr[i] = Optional.ofNullable(histogram.getBucketWeights().get(i)).orElse(0) * ratio;
      }
    }
    return bucketWeightsArr;
  }

  private static StrippedHistogram stripZeroes(double[] weights) {
    int minBucket = weights.length - 1;
    int maxBucket = 0;
    for (int i = 0; i < weights.length; i++) {
      if (weights[i] > 0) {
        minBucket = Math.min(minBucket, i);
        maxBucket = Math.max(maxBucket, i);
      }
    }
    if (minBucket <= maxBucket) {
      double[] newWeights = Arrays.copyOfRange(weights, minBucket, maxBucket + 1);
      return StrippedHistogram.builder()
          .bucketWeights(newWeights)
          .numBuckets(maxBucket - minBucket + 1)
          .minBucket(minBucket)
          .maxBucket(maxBucket)
          .build();
    }
    return StrippedHistogram.builder().numBuckets(0).bucketWeights(new double[0]).build();
  }
}
