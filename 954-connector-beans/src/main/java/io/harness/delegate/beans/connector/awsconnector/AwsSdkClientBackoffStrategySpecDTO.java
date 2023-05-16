/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonSubTypes({
  @JsonSubTypes.
  Type(value = AwsFixedDelayBackoffStrategySpecDTO.class, name = AwsConstants.FIXED_DELAY_BACKOFF_STRATEGY)
  ,
      @JsonSubTypes.Type(
          value = AwsEqualJitterBackoffStrategySpecDTO.class, name = AwsConstants.EQUAL_JITTER_BACKOFF_STRATEGY),
      @JsonSubTypes.Type(
          value = AwsFullJitterBackoffStrategySpecDTO.class, name = AwsConstants.FULL_JITTER_BACKOFF_STRATEGY)
})
@ApiModel("AwsSdkClientBackOffStrategySpec")
@Schema(name = "AwsSdkClientBackOffStrategySpec", description = "This contains AWS Sdk Client BackOff strategy spec")
public interface AwsSdkClientBackoffStrategySpecDTO extends DecryptableEntity {}
