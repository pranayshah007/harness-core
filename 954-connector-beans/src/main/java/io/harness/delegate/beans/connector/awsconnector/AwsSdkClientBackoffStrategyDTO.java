/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("AwsSdkClientBackoffStrategy")
@JsonDeserialize(using = AwsSdkClientBackoffStrategyDTODeserializer.class)
@Schema(
    name = "AwsSdkClientBackoffStrategy", description = "This contains details of the AWS SDK Client Backoff Strategy")
public class AwsSdkClientBackoffStrategyDTO {
  @NotNull @JsonProperty("type") AwsSdkClientBackoffStrategyType awsSdkClientBackoffStrategyType;

  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  AwsSdkClientBackoffStrategySpecDTO backoffStrategyConfig;

  @Builder
  public AwsSdkClientBackoffStrategyDTO(AwsSdkClientBackoffStrategyType awsSdkClientBackoffStrategyType,
      AwsSdkClientBackoffStrategySpecDTO backoffStrategyConfig) {
    this.awsSdkClientBackoffStrategyType = awsSdkClientBackoffStrategyType;
    this.backoffStrategyConfig = backoffStrategyConfig;
  }
}
