/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.UnsupportedOperationException;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.shell.AuthenticationScheme;

import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GitConnectorImpl implements BaseConnector {
  @Override
  public String getSecretId(SettingAttribute settingAttribute) {
    return ((GitConfig) settingAttribute.getValue()).getEncryptedPassword();
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.GIT;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(SettingAttribute settingAttribute, Set<CgEntityId> childEntities,
      Map<CgEntityId, NgEntityDetail> migratedEntities) {
    GitConfig gitConfig = (GitConfig) settingAttribute.getValue();

    return GitConfigDTO.builder()
        .branchName(gitConfig.getBranch())
        .delegateSelectors(new HashSet<>(CollectionUtils.emptyIfNull(gitConfig.getDelegateSelectors())))
        .executeOnDelegate(true)
        .gitAuthType(getAuthType(gitConfig.getAuthenticationScheme()))
        .gitAuth(getGitAuth(gitConfig, childEntities, migratedEntities))
        .gitConnectionType(getGitConnectionType(gitConfig.getUrlType()))
        .url(gitConfig.getRepoUrl())
        .build();
  }

  private static GitAuthenticationDTO getGitAuth(
      GitConfig gitConfig, Set<CgEntityId> childEntities, Map<CgEntityId, NgEntityDetail> migratedEntities) {
    if (gitConfig.getAuthenticationScheme() == AuthenticationScheme.HTTP_PASSWORD) {
      CgEntityId passwordRefEntityId =
          childEntities.stream()
              .filter(childEntity -> childEntity.getId().equals(gitConfig.getEncryptedPassword()))
              .findFirst()
              .get();
      String identifier = migratedEntities.get(passwordRefEntityId).getIdentifier();

      return GitHTTPAuthenticationDTO.builder()
          .username(gitConfig.getUsername())

          .passwordRef(
              // TODO: scope will come from inputs
              SecretRefData.builder().identifier(identifier).scope(Scope.PROJECT).build())
          .build();
    } else if (gitConfig.getAuthenticationScheme() == AuthenticationScheme.SSH_KEY) {
      return GitSSHAuthenticationDTO.builder()
          .encryptedSshKey(
              SecretRefData
                  .builder()
                  // TODO: identifier will come from inside ssh key ref setting attribute. It needs to be discovered and
                  // mapped to a secret. Ref of that secret will be used here.
                  .identifier(MigratorUtility.generateIdentifier(gitConfig.getSshSettingAttribute().getName()))
                  .scope(Scope.PROJECT)
                  .build())
          .build();
    } else {
      throw new UnsupportedOperationException("Unsupported git auth type: " + gitConfig.getAuthenticationScheme());
    }
  }

  private static GitConnectionType getGitConnectionType(GitConfig.UrlType urlType) {
    return urlType == GitConfig.UrlType.REPO ? GitConnectionType.REPO : GitConnectionType.ACCOUNT;
  }

  private static GitAuthType getAuthType(AuthenticationScheme authenticationScheme) {
    switch (authenticationScheme) {
      case HTTP_PASSWORD:
        return GitAuthType.HTTP;
      case SSH_KEY:
        return GitAuthType.SSH;
      default:
        throw new UnsupportedOperationException("Git auth Type not supported : " + authenticationScheme);
    }
  }
}
