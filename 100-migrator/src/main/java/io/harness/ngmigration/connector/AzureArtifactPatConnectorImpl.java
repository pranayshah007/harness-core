/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.SettingAttribute;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class AzureArtifactPatConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    return Collections.singletonList(((AzureArtifactsPATConfig) settingAttribute.getValue()).getEncryptedPat());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.AZURE_ARTIFACTS;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    AzureArtifactsPATConfig azureArtifactsPATConfig = (AzureArtifactsPATConfig) settingAttribute.getValue();
    SecretRefData secretRefData =
        MigratorUtility.getSecretRef(migratedEntities, azureArtifactsPATConfig.getEncryptedPat());
    return AzureArtifactsConnectorDTO.builder()
        .azureArtifactsUrl(azureArtifactsPATConfig.getAzureDevopsUrl())
        .auth(AzureArtifactsAuthenticationDTO.builder()
                  .credentials(AzureArtifactsCredentialsDTO.builder()
                                   .credentialsSpec(AzureArtifactsTokenDTO.builder().tokenRef(secretRefData).build())
                                   .type(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)
                                   .build())
                  .build())
        .executeOnDelegate(true)
        .build();
  }
}
