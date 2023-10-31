/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;
import static io.harness.delegate.beans.connector.jenkins.JenkinsAuthType.BEARER_TOKEN;
import static io.harness.delegate.beans.connector.jenkins.JenkinsAuthType.USER_PASSWORD;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO.JenkinsAuthenticationDTOBuilder;
import io.harness.delegate.beans.connector.jenkins.JenkinsBearerTokenDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.JenkinsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;
import software.wings.service.impl.jenkins.JenkinsUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class JenkinsConnectorImpl implements BaseConnector {
  public static final String USERNAME_AUTH_TYPE = "Username/Password";
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    return settingAttribute.getValue().fetchRelevantEncryptedSecrets();
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.JENKINS;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    JenkinsConfig jenkinsConfig = (JenkinsConfig) settingAttribute.getValue();
    JenkinsAuthenticationDTOBuilder jenkinsAuthenticationDTOBuilder = JenkinsAuthenticationDTO.builder();
    if (JenkinsUtils.TOKEN_FIELD.equals(jenkinsConfig.getAuthMechanism())) {
      jenkinsAuthenticationDTOBuilder.authType(BEARER_TOKEN)
          .credentials(JenkinsBearerTokenDTO.builder()
                           .tokenRef(MigratorUtility.getSecretRef(migratedEntities, jenkinsConfig.getEncryptedToken()))
                           .build());
    } else {
      jenkinsAuthenticationDTOBuilder.authType(USER_PASSWORD)
          .credentials(
              JenkinsUserNamePasswordDTO.builder()
                  .username(jenkinsConfig.getUsername())
                  .passwordRef(MigratorUtility.getSecretRef(migratedEntities, jenkinsConfig.getEncryptedPassword()))
                  .build());
    }
    return JenkinsConnectorDTO.builder()
        .jenkinsUrl(jenkinsConfig.getJenkinsUrl())
        .auth(jenkinsAuthenticationDTOBuilder.build())
        .build();
  }
}
