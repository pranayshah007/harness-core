/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpconnector;

import static io.harness.ConnectorConstants.INHERIT_FROM_DELEGATE_TYPE_ERROR_MSG;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpconnector.outcome.GcpConnectorCredentialOutcomeDTO;
import io.harness.delegate.beans.connector.gcpconnector.outcome.GcpConnectorOutcomeDTO;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("GcpConnector")
@Schema(name = "GcpConnector", description = "This contains GCP connector details")
@RecasterAlias("io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO")
public class GcpConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable, ManagerExecutable {
  @Valid @NotNull GcpConnectorCredentialDTO credential;
  Set<String> delegateSelectors;
  @Builder.Default Boolean executeOnDelegate = true;
  ConnectorType connectorType;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (credential.getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
      return Collections.singletonList(credential.getConfig());
    }
    return null;
  }

  @Override
  public void validate() {
    if (GcpCredentialType.INHERIT_FROM_DELEGATE.equals(credential.getGcpCredentialType())
        && isEmpty(delegateSelectors)) {
      throw new InvalidRequestException(INHERIT_FROM_DELEGATE_TYPE_ERROR_MSG);
    }
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return GcpConnectorOutcomeDTO.builder()
        .credential(GcpConnectorCredentialOutcomeDTO.builder()
                        .gcpCredentialType(this.credential.getGcpCredentialType())
                        .config(this.credential.getConfig())
                        .build())
        .delegateSelectors(this.delegateSelectors)
        .executeOnDelegate(this.executeOnDelegate)
        .build();
  }
}
