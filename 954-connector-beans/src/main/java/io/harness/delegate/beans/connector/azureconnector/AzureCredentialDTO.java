/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureCredentialOutcomeDTO;

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

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("AzureCredential")
@JsonDeserialize(using = AzureCredentialDTODeserializer.class)
@Schema(name = "AzureCredential", description = "This contains Azure connector credentials")
@RecasterAlias("io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO")
public class AzureCredentialDTO {
  @NotNull @JsonProperty("type") AzureCredentialType azureCredentialType;
  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  AzureCredentialSpecDTO config;

  @Builder
  public AzureCredentialDTO(AzureCredentialType azureCredentialType, AzureCredentialSpecDTO config) {
    this.azureCredentialType = azureCredentialType;
    this.config = config;
  }

  public AzureCredentialOutcomeDTO toOutcome() {
    return AzureCredentialOutcomeDTO.builder().type(this.azureCredentialType).spec(this.config.toOutcome()).build();
  }
}
