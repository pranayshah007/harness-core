/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.histogram;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/**
 * HistogramOptions describing a histogram with a given number of fixed-size buckets, with the first bucket start at
 * 0.0 and the last bucket start larger or equal to maxValue.
 * Requires maxValue > 0, bucketSize > 0, epsilon > 0.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LinearHistogramOptions implements HistogramOptions {
  @Getter int numBuckets;
  double bucketSize;
  @Getter double epsilon;

  public LinearHistogramOptions(int numBuckets, double bucketSize, double epsilon) {
    checkArgument(numBuckets > 0 && bucketSize > 0.0 && epsilon > 0.0, "maxValue, bucketSize & epsilon must be +ve");
    this.numBuckets = numBuckets;
    this.bucketSize = bucketSize;
    this.epsilon = epsilon;
  }

  public LinearHistogramOptions(double maxValue, double bucketSize, double epsilon) {
    checkArgument(maxValue > 0.0 && bucketSize > 0.0 && epsilon > 0.0, "maxValue, bucketSize & epsilon must be +ve");
    this.numBuckets = (int) Math.ceil((maxValue / bucketSize) + 1);
    this.bucketSize = bucketSize;
    this.epsilon = epsilon;
  }

  @Override
  public int findBucket(double value) {
    int bucket = (int) (value / this.bucketSize);
    if (bucket < 0) {
      return 0;
    }
    if (bucket >= this.numBuckets) {
      return this.numBuckets - 1;
    }
    return bucket;
  }

  @Override
  public double getBucketStart(int bucket) {
    checkElementIndex(bucket, this.numBuckets);
    return bucket * this.bucketSize;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof LinearHistogramOptions)) {
      return false;
    }
    LinearHistogramOptions o = (LinearHistogramOptions) other;
    return this.numBuckets == o.numBuckets && this.bucketSize == o.bucketSize && this.epsilon == o.epsilon;
  }
}
