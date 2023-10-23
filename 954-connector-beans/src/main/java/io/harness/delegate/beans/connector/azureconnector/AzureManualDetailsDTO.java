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
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureCredentialSpecOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureManualDetailsOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@JsonTypeName(AzureConstants.MANUAL_CONFIG)
@ApiModel("AzureManualDetails")
@Schema(name = "AzureManualDetails", description = "This contains Azure manual credentials ConnectorDisconnectHandler details")
@RecasterAlias("io.harness.delegate.beans.ConnectorDisconnectHandler.azureconnector.AzureManualDetailsDTO")
public class AzureManualDetailsDTO implements AzureCredentialSpecDTO {
  @Schema(description = "Application ID of the Azure App.") @JsonProperty("applicationId") @NotNull String clientId;

  @NotNull
  @Schema(description = "The Azure Active Directory (AAD) directory ID where you created your application.")
  String tenantId;

  @NotNull @JsonProperty("auth") @Schema(description = "The auth azure details ") AzureAuthDTO authDTO;

  @Override
  public AzureCredentialSpecOutcomeDTO toOutcome() {
    return AzureManualDetailsOutcomeDTO.builder()
        .applicationId(this.clientId)
        .tenantId(this.tenantId)
        .auth(this.authDTO.toOutcome())
        .build();
  }
}
