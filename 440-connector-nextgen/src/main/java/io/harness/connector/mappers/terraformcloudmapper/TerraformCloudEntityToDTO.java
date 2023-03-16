/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.terraformcloudmapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.terraformcloudconncetor.TerraformCloudConfig;
import io.harness.connector.entities.embedded.terraformcloudconncetor.TerraformCloudTokenCredential;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO.TerraformCloudConnectorDTOBuilder;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class TerraformCloudEntityToDTO
    implements ConnectorEntityToDTOMapper<TerraformCloudConnectorDTO, TerraformCloudConfig> {
  @Override
  public TerraformCloudConnectorDTO createConnectorDTO(TerraformCloudConfig connector) {
    TerraformCloudConnectorDTOBuilder terraformCloudConnectorDTOBuilder =
        TerraformCloudConnectorDTO.builder()
            .terraformCloudUrl(connector.getUrl())
            .executeOnDelegate(connector.getExecuteOnDelegate())
            .delegateSelectors(connector.getDelegateSelectors());
    switch (connector.getCredentialType()) {
      case API_TOKEN:
        TerraformCloudTokenCredential terraformCloudTokenCredential =
            (TerraformCloudTokenCredential) connector.getCredential();
        terraformCloudConnectorDTOBuilder.credential(buildFromApiTokenCredentials(terraformCloudTokenCredential));
        break;
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
    return terraformCloudConnectorDTOBuilder.build();
  }

  private TerraformCloudCredentialDTO buildFromApiTokenCredentials(
      TerraformCloudTokenCredential terraformCloudTokenCredential) {
    return TerraformCloudCredentialDTO.builder()
        .type(TerraformCloudCredentialType.API_TOKEN)
        .spec(TerraformCloudTokenCredentialsDTO.builder()
                  .apiToken(SecretRefHelper.createSecretRef(terraformCloudTokenCredential.getTokenRef()))
                  .build())
        .build();
  }
}
