/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.spotconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

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

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("SpotConnector")
@Schema(name = "SpotConnector", description = "This contains details of the Spot connector")
@RecasterAlias("io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO")
public class SpotConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable, ManagerExecutable {
  @Valid @NotNull SpotCredentialDTO credential;
  Set<String> delegateSelectors;
  @Builder.Default Boolean executeOnDelegate = true;

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    if (credential.getSpotCredentialType() == SpotCredentialType.PERMANENT_TOKEN) {
      SpotPermanentTokenConfigSpecDTO credentialConfig = (SpotPermanentTokenConfigSpecDTO) credential.getConfig();
      return Collections.singletonList(credentialConfig);
    }
    return Collections.emptyList();
  }

  @Override
  public void validate() {}
}
