/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azurekeyvaultconnector;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.azure.AzureEnvironmentType.AZURE;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.outcome.AzureKeyVaultConnectorOutcomeDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.BooleanUtils;

@OwnedBy(PL)
@Getter
@Setter
@Builder
@ToString(exclude = {"secretKey"})
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
@Schema(name = "AzureKeyVaultConnector",
    description = "Returns configuration details for the Azure Key Vault Secret Manager.")
public class AzureKeyVaultConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable {
  @Schema(description = "Application ID of the Azure App.") private String clientId;
  @SecretReference
  @ApiModelProperty(dataType = "string")
  @Schema(description = "This is the Harness text secret with the Azure authentication key as its value.")
  private SecretRefData secretKey;
  @Schema(description = "The Azure Active Directory (AAD) directory ID where you created your application.")
  private String tenantId;
  @NotNull @Schema(description = "The Azure Vault name") private String vaultName;
  @NotNull @Schema(description = "Azure Subscription ID.") private String subscription;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;
  private boolean vaultConfiguredManually;
  @Builder.Default
  @Schema(description = "This specifies the Azure Environment type, which is AZURE by default.")
  private AzureEnvironmentType azureEnvironmentType = AZURE;
  @Schema(description = SecretManagerDescriptionConstants.DELEGATE_SELECTORS) private Set<String> delegateSelectors;

  @Schema(description = "Boolean value to indicate if managed identity is used") private Boolean useManagedIdentity;
  @Schema(description = "Managed Identity Type") private AzureManagedIdentityType azureManagedIdentityType;
  @Schema(description = "Client Id of the ManagedIdentity resource") String managedClientId;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }

  @Override
  public AzureKeyVaultConnectorOutcomeDTO toOutcome() {
    return AzureKeyVaultConnectorOutcomeDTO.builder()
        .clientId(clientId)
        .secretKey(secretKey)
        .tenantId(tenantId)
        .vaultName(vaultName)
        .subscription(subscription)
        .isDefault(isDefault)
        .vaultConfiguredManually(vaultConfiguredManually)
        .azureEnvironmentType(azureEnvironmentType)
        .delegateSelectors(delegateSelectors)
        .useManagedIdentity(useManagedIdentity)
        .azureManagedIdentityType(azureManagedIdentityType)
        .managedClientId(managedClientId)
        .build();
  }

  @Override
  public void validate() {
    Preconditions.checkNotNull(this.subscription, "subscription cannot be empty");
    Preconditions.checkNotNull(this.vaultName, "vaultName cannot be empty");
    if (BooleanUtils.isTrue(this.useManagedIdentity)) {
      if (this.azureManagedIdentityType == null) {
        throw new InvalidRequestException("managedIdentityType cannot be empty");
      }
      if (AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY.equals(this.azureManagedIdentityType)
          && this.managedClientId == null) {
        throw new InvalidRequestException("managedClientId cannot be empty");
      }
    } else {
      if (this.clientId == null) {
        throw new InvalidRequestException("clientId cannot be empty");
      }
      if (this.tenantId == null) {
        throw new InvalidRequestException("tenantId cannot be empty");
      }
      if (this.secretKey == null) {
        throw new InvalidRequestException("secretKey cannot be empty");
      }
    }
  }
}
