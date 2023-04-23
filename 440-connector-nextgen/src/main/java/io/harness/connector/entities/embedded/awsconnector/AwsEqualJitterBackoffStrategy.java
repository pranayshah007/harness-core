/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.awsconnector;

import io.harness.delegate.beans.connector.awsconnector.AwsSdkClientBackoffStrategyType;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@FieldNameConstants(innerTypeName = "AwsEqualJitterBackoffStrategyKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("io.harness.connector.entities.embedded.awsconnector.AwsEqualJitterBackoffStrategy")
public class AwsEqualJitterBackoffStrategy implements AwsSdkClientBackoffStrategy {
  long baseDelay;
  long maxBackoffTime;
  int retryCount;

  @Override
  public AwsSdkClientBackoffStrategyType getBackoffStrategyType() {
    return AwsSdkClientBackoffStrategyType.EQUAL_JITTER_BACKOFF_STRATEGY;
  }
}
