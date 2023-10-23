/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureCredentialSpecOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(HarnessTeam.CDP)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AzureManualDetailsDTO.class, name = AzureConstants.MANUAL_CONFIG)
  , @JsonSubTypes.Type(value = AzureInheritFromDelegateDetailsDTO.class, name = AzureConstants.INHERIT_FROM_DELEGATE)
})
@ApiModel("AzureCredentialSpec")
@Schema(name = "AzureCredentialSpec", description = "This contains Azure ConnectorDisconnectHandler credentials spec")
public interface AzureCredentialSpecDTO {
  default AzureCredentialSpecOutcomeDTO toOutcome() {
    return null;
  }
}
