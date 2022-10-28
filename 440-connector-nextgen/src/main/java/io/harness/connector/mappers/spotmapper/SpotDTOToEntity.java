/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.spotmapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.spotconnector.SpotConfig;
import io.harness.connector.entities.embedded.spotconnector.SpotConfig.SpotConfigBuilder;
import io.harness.connector.entities.embedded.spotconnector.SpotPermanentTokenCredential;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCredentialType;
import io.harness.delegate.beans.connector.spotconnector.SpotPermanentTokenConfigSpecDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.DX)
@Singleton
public class SpotDTOToEntity implements ConnectorDTOToEntityMapper<SpotConnectorDTO, SpotConfig> {
  @Override
  public SpotConfig toConnectorEntity(SpotConnectorDTO connectorDTO) {
    final SpotCredentialDTO credential = connectorDTO.getCredential();
    final SpotCredentialType credentialType = credential.getSpotCredentialType();
    SpotConfigBuilder spotConfigBuilder;
    if (credentialType == SpotCredentialType.PERMANENT_TOKEN) {
      spotConfigBuilder = buildManualCredential(credential);
    } else {
      throw new InvalidRequestException("Invalid Credential type.");
    }
    return spotConfigBuilder.build();
  }

  private SpotConfigBuilder buildManualCredential(SpotCredentialDTO connector) {
    final SpotPermanentTokenConfigSpecDTO config = (SpotPermanentTokenConfigSpecDTO) connector.getConfig();
    final String apiTokenRef = SecretRefHelper.getSecretConfigString(config.getApiTokenRef());
    final String accountIdRef = SecretRefHelper.getSecretConfigString(config.getSpotAccountIdRef());
    SpotPermanentTokenCredential credential = SpotPermanentTokenCredential.builder()
                                                  .spotAccountId(config.getSpotAccountId())
                                                  .spotAccountIdRef(accountIdRef)
                                                  .apiTokenRef(apiTokenRef)
                                                  .build();
    return SpotConfig.builder().credentialType(SpotCredentialType.PERMANENT_TOKEN).credential(credential);
  }
}
