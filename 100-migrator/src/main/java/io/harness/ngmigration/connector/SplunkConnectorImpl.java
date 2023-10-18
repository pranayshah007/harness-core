/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SplunkConfig;
import software.wings.ngmigration.CgEntityId;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class SplunkConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    SplunkConfig config = (SplunkConfig) settingAttribute.getValue();
    return Lists.newArrayList(config.getEncryptedPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.SPLUNK;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    SplunkConfig splunkConfig = (SplunkConfig) settingAttribute.getValue();
    return SplunkConnectorDTO.builder()
        .splunkUrl(splunkConfig.getSplunkUrl())
        .username(splunkConfig.getUsername())
        .passwordRef(MigratorUtility.getSecretRef(migratedEntities, splunkConfig.getEncryptedPassword()))
        .accountId(settingAttribute.getAccountId())
        .build();
  }
}
