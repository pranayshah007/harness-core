/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermapper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.gcpsecretmanager.GcpSecretManagerConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSecretManagerConnectorDTO;
import io.harness.encryption.SecretRefHelper;

@OwnedBy(PL)
public class GcpSecretManagerEntityToDTO
    implements ConnectorEntityToDTOMapper<GcpSecretManagerConnectorDTO, GcpSecretManagerConnector> {
  @Override
  public GcpSecretManagerConnectorDTO createConnectorDTO(GcpSecretManagerConnector connector) {
    return GcpSecretManagerConnectorDTO.builder()
        .isDefault(connector.getIsDefault())
        .credentialsRef(connector.getCredentialsRef() != null
                ? SecretRefHelper.createSecretRef(connector.getCredentialsRef())
                : null)
        .assumeCredentialsOnDelegate(connector.getAssumeCredentialsOnDelegate())
        .build();
  }
}