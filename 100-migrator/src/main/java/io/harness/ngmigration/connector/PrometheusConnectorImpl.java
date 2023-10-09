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
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO.PrometheusConnectorDTOBuilder;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.PrometheusConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class PrometheusConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    PrometheusConfig config = (PrometheusConfig) settingAttribute.getValue();
    return Lists.newArrayList(config.getEncryptedPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.PROMETHEUS;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    PrometheusConfig prometheusConfig = (PrometheusConfig) settingAttribute.getValue();
    PrometheusConnectorDTOBuilder dtoBuilder = PrometheusConnectorDTO.builder().url(prometheusConfig.getUrl());
    if (StringUtils.isNotBlank(prometheusConfig.getUsername())) {
      dtoBuilder.username(prometheusConfig.getUsername());
    }
    if (StringUtils.isNotBlank(prometheusConfig.getEncryptedPassword())) {
      dtoBuilder.passwordRef(MigratorUtility.getSecretRef(migratedEntities, prometheusConfig.getEncryptedPassword()));
    }
    return dtoBuilder.build();
  }
}
