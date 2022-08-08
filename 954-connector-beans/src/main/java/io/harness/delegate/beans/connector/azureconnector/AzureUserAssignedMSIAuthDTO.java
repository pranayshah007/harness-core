/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@JsonTypeName(AzureConstants.USER_ASSIGNED_MANAGED_IDENTITY)
@ApiModel("AzureUserAssignedMSIAuth")
@Schema(name = "AzureUserAssignedMSIAuth", description = "This contains azure UserAssigned MSI auth details")
public class AzureUserAssignedMSIAuthDTO extends AzureAuthCredentialDTO {
  @Schema(description = "Client Id of the ManagedIdentity resource") @NotNull String clientId;
}
