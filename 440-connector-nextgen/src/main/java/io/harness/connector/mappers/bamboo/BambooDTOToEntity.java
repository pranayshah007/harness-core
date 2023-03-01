/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.bamboo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.bamboo.BambooConnector;
import io.harness.connector.entities.embedded.bamboo.BambooConnector.BambooConnectorBuilder;
import io.harness.connector.entities.embedded.bamboo.BambooUserNamePasswordAuthentication;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.bamboo.BambooAuthType;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.beans.connector.bamboo.BambooUserNamePasswordDTO;
import io.harness.encryption.SecretRefHelper;

import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class BambooDTOToEntity implements ConnectorDTOToEntityMapper<BambooConnectorDTO, BambooConnector> {
  @Override
  public BambooConnector toConnectorEntity(BambooConnectorDTO configDTO) {
    BambooAuthType bambooAuthType = configDTO.getAuth().getAuthType();
    BambooConnectorBuilder bambooConnectorBuilder =
        BambooConnector.builder().url(StringUtils.trim(configDTO.getBambooUrl())).authType(bambooAuthType);
    if (bambooAuthType == BambooAuthType.USER_PASSWORD) {
      BambooUserNamePasswordDTO bambooUserNamePasswordDTO =
          (BambooUserNamePasswordDTO) configDTO.getAuth().getCredentials();
      bambooConnectorBuilder.bambooAuthentication(createBambooAuthentication(bambooUserNamePasswordDTO));
    }

    BambooConnector bambooConnector = bambooConnectorBuilder.build();
    bambooConnector.setType(ConnectorType.BAMBOO);
    return bambooConnector;
  }

  private BambooUserNamePasswordAuthentication createBambooAuthentication(
      BambooUserNamePasswordDTO bambooUserNamePasswordDTO) {
    return BambooUserNamePasswordAuthentication.builder()
        .username(bambooUserNamePasswordDTO.getUsername())
        .usernameRef(SecretRefHelper.getSecretConfigString(bambooUserNamePasswordDTO.getUsernameRef()))
        .passwordRef(SecretRefHelper.getSecretConfigString(bambooUserNamePasswordDTO.getPasswordRef()))
        .build();
  }
}
