/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector.outcome;

import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTODeserializer;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;

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
public class AwsCredentialOutcomeDTO {
  @Valid CrossAccountAccessDTO crossAccountAccess;
  @NotNull AwsCredentialType type;
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  AwsCredentialSpecDTO spec;

  @JsonProperty("region") String testRegion;

  // this is only for connection validation. This testRegion should not be used in other places. If we have some use
  // case of region, We have to take it separately but not use this one.

  @Builder
  public AwsCredentialOutcomeDTO(AwsCredentialType awsCredentialType, AwsCredentialSpecDTO config,
      CrossAccountAccessDTO crossAccountAccess, String testRegion) {
    this.type = awsCredentialType;
    this.spec = config;
    this.crossAccountAccess = crossAccountAccess;
    this.testRegion = testRegion;
  }
}
