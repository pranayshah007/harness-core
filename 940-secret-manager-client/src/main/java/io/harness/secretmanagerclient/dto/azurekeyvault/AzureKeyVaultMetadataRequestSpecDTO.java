/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagerclient.dto.azurekeyvault;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.azure.AzureEnvironmentType.AZURE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.delegate.beans.connector.azureconnector.AzureManagedIdentityType;
import io.harness.encryption.SecretRefData;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataRequestSpecDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("AZURE_VAULT")
public class AzureKeyVaultMetadataRequestSpecDTO extends SecretManagerMetadataRequestSpecDTO {
  private String clientId;
  private String tenantId;
  @ApiModelProperty(dataType = "string") private SecretRefData secretKey;
  @NotNull private String subscription;
  private AzureEnvironmentType azureEnvironmentType = AZURE;
  private Set<String> delegateSelectors;
  private Boolean useManagedIdentity;
  private AzureManagedIdentityType azureManagedIdentityType;
  private String managedClientId;
}
