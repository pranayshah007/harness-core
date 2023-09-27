/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.beans.connector.tasconnector;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.tasconnector.outcome.TasConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.tasconnector.outcome.TasCredentialOutcomeDTO;
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
@ApiModel("TasConnector")
@Schema(name = "TasConnector", description = "This contains details of the Tas connector")
public class TasConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable, ManagerExecutable {
  @NotNull @Valid TasCredentialDTO credential;
  Set<String> delegateSelectors;
  @Builder.Default Boolean executeOnDelegate = true;
  @Builder.Default private ConnectorType connectorType = ConnectorType.TAS;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (credential.getType() == TasCredentialType.MANUAL_CREDENTIALS) {
      return Collections.singletonList(credential.getSpec());
    }
    return null;
  }

  @Override
  public void validate() {
    TasManualDetailsDTO tasManualDetailsDTO = (TasManualDetailsDTO) credential.getSpec();
    if (isNull(tasManualDetailsDTO.getUsername()) && isNull(tasManualDetailsDTO.getUsernameRef())) {
      throw new InvalidRequestException("Username Can not be null for tas Connector");
    }
  }
  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    TasCredentialOutcomeDTO tasCredentialOutcomeDTO = null;
    if (credential.getType().equals(TasCredentialType.MANUAL_CREDENTIALS)) {
      tasCredentialOutcomeDTO =
          TasCredentialOutcomeDTO.builder().spec(credential.getSpec()).type(credential.getType()).build();
    }
    return TasConnectorOutcomeDTO.builder().credential(tasCredentialOutcomeDTO).build();
  }
}
