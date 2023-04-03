/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(CDP)
public enum AwsSdkClientBackoffStrategyType {
  @JsonProperty(AwsConstants.FIXED_DELAY_BACKOFF_STRATEGY)
  FIXED_DELAY_BACKOFF_STRATEGY(AwsConstants.FIXED_DELAY_BACKOFF_STRATEGY),
  @JsonProperty(AwsConstants.EQUAL_JITTER_BACKOFF_STRATEGY)
  EQUAL_JITTER_BACKOFF_STRATEGY(AwsConstants.EQUAL_JITTER_BACKOFF_STRATEGY),
  @JsonProperty(AwsConstants.FULL_JITTER_BACKOFF_STRATEGY)
  FULL_JITTER_BACKOFF_STRATEGY(AwsConstants.FULL_JITTER_BACKOFF_STRATEGY);

  private final String displayName;

  AwsSdkClientBackoffStrategyType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  @JsonValue
  final String displayName() {
    return this.displayName;
  }

  public static AwsSdkClientBackoffStrategyType fromString(String typeEnum) {
    for (AwsSdkClientBackoffStrategyType enumValue : AwsSdkClientBackoffStrategyType.values()) {
      if (enumValue.getDisplayName().equals(typeEnum)) {
        return enumValue;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + typeEnum);
  }
}
