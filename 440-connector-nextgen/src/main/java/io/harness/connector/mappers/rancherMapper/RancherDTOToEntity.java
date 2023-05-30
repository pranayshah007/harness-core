/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.rancherMapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.rancherconnector.RancherBearerTokenAuthCredential;
import io.harness.connector.entities.embedded.rancherconnector.RancherConfig;
import io.harness.connector.entities.embedded.rancherconnector.RancherManualConfigCredential;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.rancher.RancherAuthType;
import io.harness.delegate.beans.connector.rancher.RancherConfigType;
import io.harness.delegate.beans.connector.rancher.RancherConnectorBearerTokenAuthenticationDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigAuthDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class RancherDTOToEntity implements ConnectorDTOToEntityMapper<RancherConnectorDTO, RancherConfig> {
  @Override
  public RancherConfig toConnectorEntity(RancherConnectorDTO connectorDTO) {
    final RancherConnectorConfigDTO credential = connectorDTO.getConfig();
    final RancherConfigType credentialType = credential.getConfigType();
    if (credentialType == RancherConfigType.MANUAL_CONFIG) {
      return buildManualCredential(credential);
    }
    throw new InvalidRequestException("Invalid Rancher Credential type " + credentialType.getDisplayName());
  }

  private RancherConfig buildManualCredential(RancherConnectorConfigDTO rancherConnectorConfigDTO) {
    final RancherConnectorConfigAuthDTO rancherConnectorConfigAuthDTO = rancherConnectorConfigDTO.getConfig();
    RancherAuthType authType = rancherConnectorConfigAuthDTO.getCredentials().getAuthType();
    if (authType == RancherAuthType.BEARER_TOKEN) {
      RancherConnectorBearerTokenAuthenticationDTO rancherConnectorBearerTokenAuthenticationDTO =
          (RancherConnectorBearerTokenAuthenticationDTO) rancherConnectorConfigAuthDTO.getCredentials().getAuth();
      return RancherConfig.builder()
          .configType(RancherConfigType.MANUAL_CONFIG)
          .rancherConfigCredential(
              RancherManualConfigCredential.builder()
                  .rancherUrl(rancherConnectorConfigAuthDTO.getRancherUrl())
                  .rancherAuthType(RancherAuthType.BEARER_TOKEN)
                  .rancherConfigAuthCredential(RancherBearerTokenAuthCredential.builder()
                                                   .rancherPassword(SecretRefHelper.getSecretConfigString(
                                                       rancherConnectorBearerTokenAuthenticationDTO.getPasswordRef()))
                                                   .build())
                  .build())
          .build();
    }
    throw new InvalidRequestException("Invalid Rancher Authentication type " + authType);
  }
}
