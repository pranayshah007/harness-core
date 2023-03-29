/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@OwnedBy(CDP)
@Data
@Builder
public class AwsSdkClientFullJitterBackoffStrategy implements AwsSdkClientBackoffStrategyOverride {
  private long baseDelay;
  private long maxBackoffTime;
  private int retryCount;

  @Override
  public AwsSdkClientBackoffStrategyOverrideType getAwsBackoffStrategyOverrideType() {
    return AwsSdkClientBackoffStrategyOverrideType.FULL_JITTER_BACKOFF_STRATEGY;
  }

  @Override
  public int getRetryCount() {
    return retryCount;
  }
}
