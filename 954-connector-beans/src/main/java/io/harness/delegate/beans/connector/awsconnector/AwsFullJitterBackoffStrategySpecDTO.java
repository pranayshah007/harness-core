/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDP)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(AwsConstants.FULL_JITTER_BACKOFF_STRATEGY)
@ApiModel("AwsFullJitterBackoffStrategy")
@Schema(name = "AwsFullJitterBackoffStrategy",
    description = "Backoff strategy that uses a full jitter strategy for computing the next backoff delay.")
public class AwsFullJitterBackoffStrategySpecDTO implements AwsSdkClientBackoffStrategySpecDTO {
  long baseDelay;
  long maxBackoffTime;
  int retryCount;
}
