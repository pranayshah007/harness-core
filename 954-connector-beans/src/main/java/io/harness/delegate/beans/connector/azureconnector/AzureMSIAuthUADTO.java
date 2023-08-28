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
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureMSIAuthUAOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
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
@ApiModel("AzureMSIAuth")
@Schema(name = "AzureMSIAuth", description = "This contains azure MSI auth details")
@RecasterAlias("io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO")
public class AzureMSIAuthUADTO implements AzureMSIAuthDTO {
  @NotNull @JsonProperty("type") AzureManagedIdentityType azureManagedIdentityType;

  @JsonProperty("spec") @NotNull AzureUserAssignedMSIAuthDTO credentials;

  @Builder
  public AzureMSIAuthUADTO(AzureManagedIdentityType azureManagedIdentityType, AzureUserAssignedMSIAuthDTO credentials) {
    this.azureManagedIdentityType = azureManagedIdentityType;
    this.credentials = credentials;
  }

  @Override
  public AzureMSIAuthUAOutcomeDTO toOutcome() {
    return AzureMSIAuthUAOutcomeDTO.builder()
        .type(this.azureManagedIdentityType)
        .spec(this.credentials.toOutcome())
        .build();
  }
}
