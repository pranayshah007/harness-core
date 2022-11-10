/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector.outcome;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class AzureConnectorOutcomeDTO
    extends ConnectorConfigOutcomeDTO implements DelegateSelectable, ManagerExecutable {
  @NotNull @Valid AzureCredentialOutcomeDTO credential;
  Set<String> delegateSelectors;
  @NotNull private AzureEnvironmentType azureEnvironmentType;
  @Builder.Default Boolean executeOnDelegate = true;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (credential.getType() == AzureCredentialType.MANUAL_CREDENTIALS) {
      return Collections.singletonList(((AzureManualDetailsOutcomeDTO) credential.getSpec()).getAuth().getSpec());
    }
    if (credential.getType() == AzureCredentialType.INHERIT_FROM_DELEGATE) {
      AzureMSIAuthOutcomeDTO azureMSIAuthOutcomeDTO =
          ((AzureInheritFromDelegateDetailsOutcomeDTO) credential.getSpec()).getAuth();

      if (azureMSIAuthOutcomeDTO instanceof AzureMSIAuthUAOutcomeDTO) {
        return Collections.singletonList(((AzureMSIAuthUAOutcomeDTO) azureMSIAuthOutcomeDTO).getSpec());
      }
    }
    return null;
  }
}
