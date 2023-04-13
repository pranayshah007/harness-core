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
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@OwnedBy(PL)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@ToString(exclude = {"secretKey"})
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureKeyVaultConfigDTO extends SecretManagerConfigDTO {
  private String clientId;
  private String secretKey;
  private String tenantId;
  private String vaultName;
  private String subscription;
  private Set<String> delegateSelectors;
  private Boolean useManagedIdentity;
  private AzureManagedIdentityType azureManagedIdentityType;
  private String managedClientId;

  @Builder.Default AzureEnvironmentType azureEnvironmentType = AZURE;
}
