/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.gcpmappers;

import io.harness.connector.entities.embedded.gcpconnector.GcpConfig;
import io.harness.connector.entities.embedded.gcpconnector.GcpOidcConfig;
import io.harness.connector.entities.embedded.gcpconnector.GcpServiceAccountKey;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpOidcDetailsDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@Singleton
public class GcpDTOToEntity implements ConnectorDTOToEntityMapper<GcpConnectorDTO, GcpConfig> {
  @Override
  public GcpConfig toConnectorEntity(GcpConnectorDTO connectorDTO) {
    final GcpConnectorCredentialDTO credential = connectorDTO.getCredential();
    final GcpCredentialType credentialType = credential.getGcpCredentialType();
    switch (credentialType) {
      case INHERIT_FROM_DELEGATE:
        return buildInheritFromDelegate(credential);
      case MANUAL_CREDENTIALS:
        return buildManualCredential(credential);
      case OIDC_AUTHENTICATION:
        return buildOidcAuthenticationDetails(credential);
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
  }

  private GcpConfig buildManualCredential(GcpConnectorCredentialDTO connector) {
    final GcpManualDetailsDTO connectorConfig = (GcpManualDetailsDTO) connector.getConfig();
    final String secretConfigString = SecretRefHelper.getSecretConfigString(connectorConfig.getSecretKeyRef());
    GcpServiceAccountKey gcpSecretKeyAuth = GcpServiceAccountKey.builder().secretKeyRef(secretConfigString).build();
    return GcpConfig.builder()
        .credentialType(GcpCredentialType.MANUAL_CREDENTIALS)
        .credential(gcpSecretKeyAuth)
        .build();
  }

  private GcpConfig buildInheritFromDelegate(GcpConnectorCredentialDTO connector) {
    return GcpConfig.builder().credentialType(GcpCredentialType.INHERIT_FROM_DELEGATE).credential(null).build();
  }

  private GcpConfig buildOidcAuthenticationDetails(GcpConnectorCredentialDTO connector) {
    final GcpOidcDetailsDTO connectorConfig = (GcpOidcDetailsDTO) connector.getConfig();

    GcpOidcConfig gcpOidcConfig = GcpOidcConfig.builder()
                                      .workloadPoolId(connectorConfig.getWorkloadPoolId())
                                      .providerId(connectorConfig.getProviderId())
                                      .gcpProjectId(connectorConfig.getGcpProjectId())
                                      .serviceAccountEmail(connectorConfig.getServiceAccountEmail())
                                      .build();

    return GcpConfig.builder().credentialType(GcpCredentialType.OIDC_AUTHENTICATION).credential(gcpOidcConfig).build();
  }
}
