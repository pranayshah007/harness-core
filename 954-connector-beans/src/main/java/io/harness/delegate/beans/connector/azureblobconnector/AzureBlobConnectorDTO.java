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
  @Schema(description = SecretManagerDescriptionConstants.AZURE_CLIENT_ID) @NotNull private String clientId;
  @SecretReference
  @ApiModelProperty(dataType = "string")
  @NotNull
  @Schema(description = SecretManagerDescriptionConstants.AZURE_SECRET_KEY)
  private SecretRefData secretKey;
  @NotNull @Schema(description = SecretManagerDescriptionConstants.AZURE_TENANT_ID) private String tenantId;
  @Schema(description = SecretManagerDescriptionConstants.AZURE_STORAGE_CONTAINER_URL)
  @NotNull
  private String containerURL;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;

  @Builder.Default
  @Schema(description = SecretManagerDescriptionConstants.AZURE_ENV_TYPE)
  private AzureEnvironmentType azureEnvironmentType = AZURE;
  @Schema(description = SecretManagerDescriptionConstants.DELEGATE_SELECTORS) private Set<String> delegateSelectors;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }
}
