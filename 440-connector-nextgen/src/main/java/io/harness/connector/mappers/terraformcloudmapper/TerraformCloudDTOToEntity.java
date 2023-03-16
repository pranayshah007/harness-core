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
import io.harness.connector.entities.embedded.terraformcloudconncetor.TerraformCloudConfig.TerraformCloudConfigBuilder;
import io.harness.connector.entities.embedded.terraformcloudconncetor.TerraformCloudTokenCredential;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialSpecDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class TerraformCloudDTOToEntity
    implements ConnectorDTOToEntityMapper<TerraformCloudConnectorDTO, TerraformCloudConfig> {
  @Override
  public TerraformCloudConfig toConnectorEntity(TerraformCloudConnectorDTO connectorDTO) {
    final TerraformCloudCredentialDTO credential = connectorDTO.getCredential();
    final TerraformCloudCredentialSpecDTO credentialSpec = credential.getSpec();
    final TerraformCloudCredentialType credentialType = credential.getType();
    TerraformCloudConfigBuilder terraformCloudConfigBuilder =
        TerraformCloudConfig.builder().url(connectorDTO.getTerraformCloudUrl());
    switch (credentialType) {
      case API_TOKEN:
        TerraformCloudTokenCredentialsDTO tokenCredentials = (TerraformCloudTokenCredentialsDTO) credentialSpec;
        terraformCloudConfigBuilder.credentialType(TerraformCloudCredentialType.API_TOKEN)
            .credential(buildFromApiTokenCredential(tokenCredentials));
        break;
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
    return terraformCloudConfigBuilder.build();
  }

  private TerraformCloudTokenCredential buildFromApiTokenCredential(TerraformCloudTokenCredentialsDTO credential) {
    return TerraformCloudTokenCredential.builder()
        .tokenRef(SecretRefHelper.getSecretConfigString(credential.getApiToken()))
        .build();
  }
}
