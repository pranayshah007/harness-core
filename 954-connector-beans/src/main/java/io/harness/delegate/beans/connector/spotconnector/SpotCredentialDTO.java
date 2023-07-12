/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.spotconnector;

import io.harness.annotation.RecasterAlias;
import io.harness.annotation.RecasterFieldName;

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
@ApiModel("SpotCredential")
@JsonDeserialize(using = SpotCredentialDTODeserializer.class)
@Schema(name = "SpotCredential", description = "This contains details of the Spot connector credential")
@RecasterAlias("io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO")
public class SpotCredentialDTO {
  @NotNull @RecasterFieldName(name = "type") @JsonProperty("type") SpotCredentialType spotCredentialType;
  @RecasterFieldName(name = "spec")
  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  SpotCredentialSpecDTO config;

  @Builder
  public SpotCredentialDTO(SpotCredentialType spotCredentialType, SpotCredentialSpecDTO config) {
    this.spotCredentialType = spotCredentialType;
    this.config = config;
  }
}
