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
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.rancher.RancherAuthType;
import io.harness.delegate.beans.connector.rancher.RancherConfigType;
import io.harness.delegate.beans.connector.rancher.RancherConnectorBearerTokenAuthenticationDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigAuthCredentialsDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigAuthDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorConfigDTO;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class RancherEntityToDTO implements ConnectorEntityToDTOMapper<RancherConnectorDTO, RancherConfig> {
  @Override
  public RancherConnectorDTO createConnectorDTO(RancherConfig rancherConfig) {
    final RancherConfigType rancherConfigType = rancherConfig.getConfigType();
    if (rancherConfigType == RancherConfigType.MANUAL_CONFIG) {
      RancherManualConfigCredential rancherManualConfigCredential =
          (RancherManualConfigCredential) rancherConfig.getRancherConfigCredential();
      return buildManualCredential(rancherManualConfigCredential, rancherConfig.getDelegateSelectors());
    }

    throw new InvalidRequestException("Invalid Credential type " + rancherConfigType.getDisplayName());
  }

  private RancherConnectorDTO buildManualCredential(
      RancherManualConfigCredential credential, Set<String> delegateSelectors) {
    RancherAuthType rancherAuthType = credential.getRancherAuthType();
    if (rancherAuthType == RancherAuthType.BEARER_TOKEN) {
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
      return RancherConnectorDTO.builder()
          .config(RancherConnectorConfigDTO.builder()
                      .configType(RancherConfigType.MANUAL_CONFIG)
                      .config(rancherConnectorConfigAuthDTO)
                      .build())
          .delegateSelectors(delegateSelectors)
          .build();
    }
    throw new InvalidRequestException("Invalid Authentication type " + rancherAuthType);
  }
}
