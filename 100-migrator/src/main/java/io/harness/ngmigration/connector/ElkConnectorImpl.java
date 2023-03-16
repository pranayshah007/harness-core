/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.elkconnector.ELKAuthType;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO.ELKConnectorDTOBuilder;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.ElkConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;
import software.wings.service.impl.analysis.ElkValidationType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ElkConnectorImpl implements BaseConnector {
  @Override
  public boolean isConnectorSupported(SettingAttribute settingAttribute) {
    return true;
  }

  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    return Collections.singletonList(((ElkConfig) settingAttribute.getValue()).getEncryptedPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.ELASTICSEARCH;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    ElkConfig elkConfig = (ElkConfig) settingAttribute.getValue();
    ELKConnectorDTOBuilder dtoBuilder =
        ELKConnectorDTO.builder().url(elkConfig.getElkUrl()).delegateSelectors(new HashSet<>());

    if (ElkValidationType.TOKEN.equals(elkConfig.getValidationType())) {
      dtoBuilder.authType(ELKAuthType.API_CLIENT_TOKEN);
      dtoBuilder.apiKeyId(elkConfig.getUsername());
      dtoBuilder.apiKeyRef(MigratorUtility.getSecretRef(migratedEntities, elkConfig.getEncryptedPassword()));
    } else {
      dtoBuilder.authType(ELKAuthType.USERNAME_PASSWORD);
      dtoBuilder.passwordRef(MigratorUtility.getSecretRef(migratedEntities, elkConfig.getEncryptedPassword()));
      dtoBuilder.username(elkConfig.getUsername());
    }

    return dtoBuilder.build();
  }
}
