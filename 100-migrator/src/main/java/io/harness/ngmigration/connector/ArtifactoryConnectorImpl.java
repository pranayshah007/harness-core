/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import static io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType.ANONYMOUS;
import static io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType.USER_PASSWORD;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthCredentialsDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.SettingAttribute;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class ArtifactoryConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    return Collections.singletonList(((ArtifactoryConfig) settingAttribute.getValue()).getEncryptedPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.ARTIFACTORY;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    ArtifactoryConfig artifactoryConfig = (ArtifactoryConfig) settingAttribute.getValue();
    ArtifactoryAuthType authType = StringUtils.isBlank(artifactoryConfig.getUsername()) ? ANONYMOUS : USER_PASSWORD;
    ArtifactoryAuthCredentialsDTO credentialsDTO = null;
    if (authType.equals(USER_PASSWORD)) {
      credentialsDTO =
          ArtifactoryUsernamePasswordAuthDTO.builder()
              .username(artifactoryConfig.getUsername())
              .passwordRef(MigratorUtility.getSecretRef(migratedEntities, artifactoryConfig.getEncryptedPassword()))
              .build();
    }
    return ArtifactoryConnectorDTO.builder()
        .artifactoryServerUrl(artifactoryConfig.getArtifactoryUrl())
        .delegateSelectors(toSet(artifactoryConfig.getDelegateSelectors()))
        .executeOnDelegate(true)
        .auth(ArtifactoryAuthenticationDTO.builder().authType(authType).credentials(credentialsDTO).build())
        .build();
  }
}
