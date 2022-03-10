/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureblobconnector;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.azure.AzureEnvironmentType.AZURE;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.*;

@OwnedBy(PL)
@Getter
@Setter
@Builder
@ToString(exclude = {"secretKey"})
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AzureBlobConnector", description = "Returns configuration details for the Azure Blob Secret Manager.")
public class AzureBlobConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable {
  @Schema(description = "Application ID of the Azure App.") @NotNull private String clientId;
  @SecretReference
  @ApiModelProperty(dataType = "string")
  @NotNull
  @Schema(description = "This is the Harness text secret with the Azure authentication key as its value.")
  private SecretRefData secretKey;
  @NotNull
  @Schema(description = "The Azure Active Directory (AAD) directory ID where you created your application.")
  private String tenantId;
  @NotNull
  @Schema(description = "The Azure Active Directory (AAD) directory ID where you created your application.")
  private String vaultName;
  @NotNull @Schema(description = "Azure Subscription ID.") private String subscription;
  @Schema(description = "Connection string for Azure storage.") @NotNull private String connectionString;
  @Schema(description = "Continer name of the Azure Storge Conatiner where blob is to be stored.")
  @NotNull
  private String containerName;
  @Schema(description = "Key ID of the Azure Key Vault Key to be used for blob encryption")
  @NotNull
  private String keyId;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;

  @Builder.Default
  @Schema(description = "This specifies the Azure Environment type, which is AZURE by default.")
  private AzureEnvironmentType azureEnvironmentType = AZURE;
  @Schema(description = SecretManagerDescriptionConstants.DELEGATE_SELECTORS) private Set<String> delegateSelectors;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }

  @Override
  public void validate() {
    Preconditions.checkNotNull(this.clientId, "clientId cannot be empty");
    Preconditions.checkNotNull(this.tenantId, "tenantId cannot be empty");
    Preconditions.checkNotNull(this.vaultName, "vaultName cannot be empty");
    Preconditions.checkNotNull(this.subscription, "subscription cannot be empty");
    Preconditions.checkNotNull(this.connectionString, "connectionString cannot be empty");
    Preconditions.checkNotNull(this.containerName, "containerName cannot be empty");
    Preconditions.checkNotNull(this.keyId, "keyId cannot be empty");
  }
}
