/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;

import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class GitConnectorImpl implements BaseConnector {
  @Override
  public List<String> getConnectorIds(SettingAttribute settingAttribute) {
    GitConfig gitConfig = (GitConfig) settingAttribute.getValue();
    if (StringUtils.isNotBlank(gitConfig.getSshSettingId())) {
      return Collections.singletonList(gitConfig.getSshSettingId());
    }
    return Collections.emptyList();
  }

  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    return Collections.singletonList(((GitConfig) settingAttribute.getValue()).getEncryptedPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.GIT;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    GitConfig gitConfig = (GitConfig) settingAttribute.getValue();

    return GitConfigDTO.builder()
        .branchName(gitConfig.getBranch())
        .delegateSelectors(new HashSet<>(CollectionUtils.emptyIfNull(gitConfig.getDelegateSelectors())))
        .executeOnDelegate(true)
        .gitAuthType(getAuthType(gitConfig))
        .gitAuth(getGitAuth(gitConfig, migratedEntities))
        .gitConnectionType(getGitConnectionType(gitConfig.getUrlType()))
        .url(getRepoUrl(gitConfig))
        .build();
  }

  private static String getRepoUrl(GitConfig gitConfig) {
    // If it is a Http Git Connector and does not start with http, then we prefix with https://
    if (StringUtils.isBlank(gitConfig.getSshSettingId()) && !gitConfig.getRepoUrl().startsWith("http")) {
      return "https://" + gitConfig.getRepoUrl();
    }
    return gitConfig.getRepoUrl();
  }

  private static GitAuthenticationDTO getGitAuth(GitConfig gitConfig, Map<CgEntityId, NGYamlFile> migratedEntities) {
    if (StringUtils.isBlank(gitConfig.getSshSettingId())) {
      return GitHTTPAuthenticationDTO.builder()
          .username(gitConfig.getUsername())
          .passwordRef(MigratorUtility.getSecretRef(migratedEntities, gitConfig.getEncryptedPassword()))
          .build();
    } else {
      return GitSSHAuthenticationDTO.builder()
          .encryptedSshKey(MigratorUtility.getSecretRef(migratedEntities, gitConfig.getSshSettingId(), CONNECTOR))
          .build();
    }
  }

  private static GitConnectionType getGitConnectionType(GitConfig.UrlType urlType) {
    return urlType == GitConfig.UrlType.REPO ? GitConnectionType.REPO : GitConnectionType.ACCOUNT;
  }

  private static GitAuthType getAuthType(GitConfig gitConfig) {
    if (StringUtils.isNotBlank(gitConfig.getSshSettingId())) {
      return GitAuthType.SSH;
    }
    return GitAuthType.HTTP;
  }
}
