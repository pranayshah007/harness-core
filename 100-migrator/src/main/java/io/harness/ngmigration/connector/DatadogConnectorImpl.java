/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.DatadogConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class DatadogConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    DatadogConfig datadogConfig = (DatadogConfig) settingAttribute.getValue();
    return Lists.newArrayList(datadogConfig.getEncryptedApiKey(), datadogConfig.getEncryptedApplicationKey());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.DATADOG;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    DatadogConfig datadogConfig = (DatadogConfig) settingAttribute.getValue();
    return DatadogConnectorDTO.builder()
        .url(datadogConfig.getUrl().replace("/v1/", "/").replace("/v1", "/"))
        .apiKeyRef(MigratorUtility.getSecretRef(migratedEntities, datadogConfig.getEncryptedApiKey()))
        .applicationKeyRef(MigratorUtility.getSecretRef(migratedEntities, datadogConfig.getEncryptedApplicationKey()))
        .build();
  }
}
