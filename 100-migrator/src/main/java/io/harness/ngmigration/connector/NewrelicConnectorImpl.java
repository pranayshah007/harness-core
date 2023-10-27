/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.NewRelicConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class NewrelicConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    return Collections.singletonList(((NewRelicConfig) settingAttribute.getValue()).getEncryptedApiKey());
  }

  @Override
  public SupportStatus getSupportStatus() {
    return SupportStatus.MANUAL_EFFORT;
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.NEW_RELIC;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    NewRelicConfig newRelicConfig = (NewRelicConfig) settingAttribute.getValue();
    String newrelicAccountId = StringUtils.isNotBlank(newRelicConfig.getNewRelicAccountId())
        ? newRelicConfig.getNewRelicAccountId()
        : PLEASE_FIX_ME;
    return NewRelicConnectorDTO.builder()
        .url(newRelicConfig.getNewRelicUrl())
        .apiKeyRef(MigratorUtility.getSecretRef(migratedEntities, newRelicConfig.getEncryptedApiKey()))
        .newRelicAccountId(newrelicAccountId)
        .build();
  }
}
