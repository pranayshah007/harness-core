/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.rancherMapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.rancherconnector.RancherBearerTokenAuthCredential;
import io.harness.connector.entities.embedded.rancherconnector.RancherConfig;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.rancher.RancherAuthType;
import io.harness.delegate.beans.connector.rancher.RancherConfigType;
import io.harness.delegate.beans.connector.rancher.RancherConnectorBearerTokenAuthenticationDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigAuthCredentialsDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigAuthDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigDTO.RancherConnectorConfigDTOBuilder;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.DX)
@Singleton
public class RancherEntityToDTO implements ConnectorEntityToDTOMapper<RancherConnectorDTO, RancherConfig> {
  @Override
  public RancherConnectorDTO createConnectorDTO(RancherConfig rancherConfig) {
    final RancherConfigType rancherConfigType = rancherConfig.getConfigType();
    RancherConnectorConfigDTOBuilder rancherConnectorConfigDTOBuilder;
    if (rancherConfigType == RancherConfigType.MANUAL_CONFIG) {
      rancherConnectorConfigDTOBuilder =
          buildManualCredential((io.harness.connector.entities.embedded.rancherconnector.RancherManualConfigCredential)
                                    rancherConfig.getRancherConfigCredential());
    } else {
      throw new InvalidRequestException("Invalid Credential type.");
    }
    return RancherConnectorDTO.builder()
        .config(rancherConnectorConfigDTOBuilder.build())
        .delegateSelectors(rancherConfig.getDelegateSelectors())
        .build();
  }

  private RancherConnectorConfigDTOBuilder buildManualCredential(
      io.harness.connector.entities.embedded.rancherconnector.RancherManualConfigCredential credential) {
    if (credential.getRancherAuthType() == RancherAuthType.BEARER_TOKEN) {
      RancherBearerTokenAuthCredential rancherBearerTokenAuthCredential =
          (RancherBearerTokenAuthCredential) credential.getRancherConfigAuthCredential();
      final RancherConnectorConfigAuthDTO rancherConnectorConfigAuthDTO =
          RancherConnectorConfigAuthDTO.builder()
              .rancherUrl(credential.getRancherUrl())
              .credentials(RancherConnectorConfigAuthCredentialsDTO.builder()
                               .authType(RancherAuthType.BEARER_TOKEN)
                               .auth(RancherConnectorBearerTokenAuthenticationDTO.builder()
                                         .passwordRef(SecretRefHelper.createSecretRef(
                                             rancherBearerTokenAuthCredential.getRancherPassword()))
                                         .build())
                               .build())
              .build();
      return RancherConnectorConfigDTO.builder()
          .configType(RancherConfigType.MANUAL_CONFIG)
          .config(rancherConnectorConfigAuthDTO);
    } else {
      throw new InvalidRequestException("Invalid Authentication type.");
    }
  }
}
