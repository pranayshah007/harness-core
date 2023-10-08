/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import static io.harness.delegate.beans.connector.docker.DockerAuthType.ANONYMOUS;
import static io.harness.delegate.beans.connector.docker.DockerAuthType.USER_PASSWORD;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerAuthCredentialsDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerRegistryProviderType;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.DockerConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class DockerConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    return Collections.singletonList(((DockerConfig) settingAttribute.getValue()).getEncryptedPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.DOCKER;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    DockerConfig dockerConfig = (DockerConfig) settingAttribute.getValue();
    DockerAuthType dockerAuthType = StringUtils.isBlank(dockerConfig.getUsername()) ? ANONYMOUS : USER_PASSWORD;
    DockerAuthCredentialsDTO credentialsDTO = null;
    if (dockerAuthType.equals(USER_PASSWORD)) {
      credentialsDTO =
          DockerUserNamePasswordDTO.builder()
              .username(dockerConfig.getUsername())
              .passwordRef(MigratorUtility.getSecretRef(migratedEntities, dockerConfig.getEncryptedPassword()))
              .build();
    }
    return DockerConnectorDTO.builder()
        .dockerRegistryUrl(dockerConfig.getDockerRegistryUrl())
        .delegateSelectors(toSet(dockerConfig.getDelegateSelectors()))
        .providerType(DockerRegistryProviderType.OTHER)
        .auth(DockerAuthenticationDTO.builder().authType(dockerAuthType).credentials(credentialsDTO).build())
        .build();
  }
}
