/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import static io.harness.delegate.beans.connector.nexusconnector.NexusAuthType.ANONYMOUS;
import static io.harness.delegate.beans.connector.nexusconnector.NexusAuthType.USER_PASSWORD;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthCredentialsDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.SettingAttribute;
import software.wings.beans.config.NexusConfig;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class NexusConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    return Collections.singletonList(((NexusConfig) settingAttribute.getValue()).getEncryptedPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.NEXUS;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    NexusConfig nexusConfig = (NexusConfig) settingAttribute.getValue();
    NexusAuthType authType = StringUtils.isBlank(nexusConfig.getUsername()) ? ANONYMOUS : USER_PASSWORD;
    NexusAuthCredentialsDTO credentialsDTO = null;
    if (authType.equals(USER_PASSWORD)) {
      credentialsDTO =
          NexusUsernamePasswordAuthDTO.builder()
              .username(nexusConfig.getUsername())
              .passwordRef(MigratorUtility.getSecretRef(migratedEntities, nexusConfig.getEncryptedPassword()))
              .build();
    }
    return NexusConnectorDTO.builder()
        .nexusServerUrl(nexusConfig.getNexusUrl())
        .delegateSelectors(toSet(nexusConfig.getDelegateSelectors()))
        .version(nexusConfig.getVersion())
        .auth(NexusAuthenticationDTO.builder().authType(authType).credentials(credentialsDTO).build())
        .build();
  }
}
