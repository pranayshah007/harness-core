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
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpOidcDetailsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@Singleton
public class GcpEntityToDTO implements ConnectorEntityToDTOMapper<GcpConnectorDTO, GcpConfig> {
  @Override
  public GcpConnectorDTO createConnectorDTO(GcpConfig connector) {
    final GcpCredentialType credentialType = connector.getCredentialType();
    switch (credentialType) {
      case INHERIT_FROM_DELEGATE:
        return buildInheritFromDelegate(connector);
      case MANUAL_CREDENTIALS:
        return buildManualCredential(connector);
      case OIDC_AUTHENTICATION:
        return buildOidcAuthenticationDetails(connector);
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
  }

  private GcpConnectorDTO buildManualCredential(GcpConfig connector) {
    final GcpServiceAccountKey auth = (GcpServiceAccountKey) connector.getCredential();
    final SecretRefData secretRef = SecretRefHelper.createSecretRef(auth.getSecretKeyRef());
    final GcpManualDetailsDTO gcpManualDetailsDTO = GcpManualDetailsDTO.builder().secretKeyRef(secretRef).build();
    return GcpConnectorDTO.builder()
        .delegateSelectors(connector.getDelegateSelectors())
        .credential(GcpConnectorCredentialDTO.builder()
                        .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                        .config(gcpManualDetailsDTO)
                        .build())
        .build();
  }

  private GcpConnectorDTO buildInheritFromDelegate(GcpConfig connector) {
    return GcpConnectorDTO.builder()
        .delegateSelectors(connector.getDelegateSelectors())
        .credential(GcpConnectorCredentialDTO.builder()
                        .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                        .config(null)
                        .build())
        .build();
  }

  private GcpConnectorDTO buildOidcAuthenticationDetails(GcpConfig connector) {
    final GcpOidcConfig gcpOidcConfig = (GcpOidcConfig) connector.getCredential();

    final GcpOidcDetailsDTO gcpOidcDetailsDTO = GcpOidcDetailsDTO.builder()
                                                    .workloadPoolId(gcpOidcConfig.getWorkloadPoolId())
                                                    .providerId(gcpOidcConfig.getProviderId())
                                                    .gcpProjectId(gcpOidcConfig.getGcpProjectId())
                                                    .build();

    return GcpConnectorDTO.builder()
        .delegateSelectors(connector.getDelegateSelectors())
        .credential(GcpConnectorCredentialDTO.builder()
                        .gcpCredentialType(GcpCredentialType.OIDC_AUTHENTICATION)
                        .config(gcpOidcDetailsDTO)
                        .build())
        .build();
  }
}
