/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.hsqs.client.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class HsqsDequeueConfig {
  @JsonProperty(defaultValue = "20") @Builder.Default int batchSize = 20;
  // This is the sleep when no. of messages fetched from redis is equal to the batch size
  @JsonProperty(defaultValue = "30") @Builder.Default int threadSleepTimeInMillis = 30;
}
