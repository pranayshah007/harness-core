/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.harnesscode;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.githubconnector.*;
import io.harness.connector.entities.embedded.harnesscodeconnector.HarnessCodeConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.*;
import io.harness.delegate.beans.connector.scm.harnesscode.HarnessCodeConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.UnknownEnumTypeException;

@OwnedBy(HarnessTeam.DX)
public class HarnessCodeDTOToEntity
    implements ConnectorDTOToEntityMapper<HarnessCodeConnectorDTO, HarnessCodeConnector> {
  @Override
  public HarnessCodeConnector toConnectorEntity(HarnessCodeConnectorDTO configDTO) {
    return HarnessCodeConnector.builder().url(configDTO.getUrl()).build();
  }
}
