/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO.DynatraceConnectorDTOBuilder;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.DynaTraceConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import com.google.common.collect.Lists;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DynatraceConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    DynaTraceConfig config = (DynaTraceConfig) settingAttribute.getValue();
    return Lists.newArrayList(config.getEncryptedApiToken());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.DYNATRACE;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    DynaTraceConfig config = (DynaTraceConfig) settingAttribute.getValue();
    DynatraceConnectorDTOBuilder dtoBuilder =
        DynatraceConnectorDTO.builder()
            .url(config.getDynaTraceUrl())
            .apiTokenRef(MigratorUtility.getSecretRef(migratedEntities, config.getEncryptedApiToken()))
            .delegateSelectors(new HashSet<>());
    return dtoBuilder.build();
  }
}
