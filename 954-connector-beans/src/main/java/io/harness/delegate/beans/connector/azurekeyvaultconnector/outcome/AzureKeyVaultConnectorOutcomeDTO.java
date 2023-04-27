/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azurekeyvaultconnector.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.AzureEnvironmentType.AZURE;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureKeyVaultConnectorOutcomeDTO extends ConnectorConfigOutcomeDTO implements DelegateSelectable {
  @Schema(description = "Application ID of the Azure App.") private String clientId;
  @SecretReference
  @Schema(description = "This is the Harness text secret with the Azure authentication key as its value.")
  private SecretRefData secretKey;
  @Schema(description = "The Azure Active Directory (AAD) directory ID where you created your application.")
  private String tenantId;
  @NotNull @Schema(description = "The Azure Vault name") private String vaultName;
  @NotNull @Schema(description = "Azure Subscription ID.") private String subscription;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;

  @Builder.Default
  @Schema(description = "This specifies the Azure Environment type, which is AZURE by default.")
  private AzureEnvironmentType azureEnvironmentType = AZURE;
  @Schema(description = SecretManagerDescriptionConstants.DELEGATE_SELECTORS) private Set<String> delegateSelectors;

  @Schema(description = "Boolean value to indicate if managed identity is used") private Boolean useManagedIdentity;
  @Schema(description = "Managed Identity Type") private AzureManagedIdentityType azureManagedIdentityType;
  @Schema(description = "Client Id of the ManagedIdentity resource") String managedClientId;
}
