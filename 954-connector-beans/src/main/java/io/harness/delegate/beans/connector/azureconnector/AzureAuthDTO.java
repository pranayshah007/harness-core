/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureAuthOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "AzureAuth", description = "This contains azure auth details")
@RecasterAlias("io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO")
public class AzureAuthDTO {
  @NotNull @JsonProperty("type") AzureSecretType azureSecretType;

  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @NotNull
  @Valid
  AzureAuthCredentialDTO credentials;

  @Builder
  public AzureAuthDTO(AzureSecretType azureSecretType, AzureAuthCredentialDTO credentials) {
    this.azureSecretType = azureSecretType;
    this.credentials = credentials;
  }

  public AzureAuthOutcomeDTO toOutcome() {
    return AzureAuthOutcomeDTO.builder().type(this.azureSecretType).spec(this.credentials.toOutcome()).build();
  }
}
