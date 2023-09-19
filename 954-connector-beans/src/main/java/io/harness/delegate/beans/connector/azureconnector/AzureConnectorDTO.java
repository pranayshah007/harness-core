/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import static io.harness.ConnectorConstants.INHERIT_FROM_DELEGATE_TYPE_ERROR_MSG;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureConnectorOutcomeDTO;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("AzureConnector")
@Schema(name = "AzureConnector", description = "This contains details of the Azure connector")
@RecasterAlias("io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO")
public class AzureConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable, ManagerExecutable {
  @NotNull @Valid AzureCredentialDTO credential;
  Set<String> delegateSelectors;
  @Builder.Default
  @NotNull
  @Schema(description = "This specifies the Azure Environment type, which is AZURE by default.")
  private AzureEnvironmentType azureEnvironmentType;
  @Builder.Default Boolean executeOnDelegate = true;
  ConnectorType connectorType;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (credential.getAzureCredentialType() == AzureCredentialType.MANUAL_CREDENTIALS) {
      return Collections.singletonList(((AzureManualDetailsDTO) credential.getConfig()).getAuthDTO().getCredentials());
    }
    if (credential.getAzureCredentialType() == AzureCredentialType.INHERIT_FROM_DELEGATE) {
      AzureMSIAuthDTO azureMSIAuthDTO = ((AzureInheritFromDelegateDetailsDTO) credential.getConfig()).getAuthDTO();

      if (azureMSIAuthDTO instanceof AzureMSIAuthUADTO) {
        return Collections.singletonList(((AzureMSIAuthUADTO) azureMSIAuthDTO).getCredentials());
      }
    }
    return null;
  }

  @Override
  public void validate() {
    if (AzureCredentialType.INHERIT_FROM_DELEGATE.equals(credential.getAzureCredentialType())
        && isEmpty(delegateSelectors)) {
      throw new InvalidRequestException(INHERIT_FROM_DELEGATE_TYPE_ERROR_MSG);
    }
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return AzureConnectorOutcomeDTO.builder()
        .credential(this.credential.toOutcome())
        .delegateSelectors(this.delegateSelectors)
        .executeOnDelegate(this.executeOnDelegate)
        .azureEnvironmentType(this.azureEnvironmentType)
        .build();
  }
}
