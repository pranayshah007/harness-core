/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

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
public class AzureMSIAuthUADTO implements AzureMSIAuthDTO {
  @NotNull @JsonProperty("type") AzureManagedIdentityType azureManagedIdentityType;

  @JsonProperty("spec") @NotNull AzureUserAssignedMSIAuthDTO credentials;

  @Builder
  public AzureMSIAuthUADTO(AzureManagedIdentityType azureManagedIdentityType, AzureUserAssignedMSIAuthDTO credentials) {
    this.azureManagedIdentityType = azureManagedIdentityType;
    this.credentials = credentials;
  }
}
