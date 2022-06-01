/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.common.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;

@Singleton
@OwnedBy(CDP)
public class GitResourceServiceHelper {
  private final ConnectorService connectorService;
  private final GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;

  @Inject
  public GitResourceServiceHelper(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper) {
    this.connectorService = connectorService;
    this.gitConfigAuthenticationInfoHelper = gitConfigAuthenticationInfoHelper;
  }

  public ConnectorInfoDTO getConnectorInfoDTO(String connectorId, NGAccess ngAccess) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorId, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    if (!connectorDTO.isPresent()) {
      throw new InvalidRequestException(format("Connector not found for identifier : [%s]", connectorId), USER);
    }
    return connectorDTO.get().getConnector();
  }

  public GitStoreDelegateConfig getGitStoreDelegateConfig(ConnectorInfoDTO connectorDTO, NGAccess ngAccess,
      FetchType fetchType, String branch, String commitId, String path) {
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO((ScmConnector) connectorDTO.getConnectorConfig());
    SSHKeySpecDTO sshKeySpecDTO = getSshKeySpecDTO(gitConfigDTO, ngAccess);
    List<EncryptedDataDetail> encryptedDataDetails =
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(gitConfigDTO, sshKeySpecDTO, ngAccess);
    return GitStoreDelegateConfig.builder()
        .gitConfigDTO(gitConfigDTO)
        .sshKeySpecDTO(sshKeySpecDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .fetchType(fetchType)
        .branch(branch)
        .commitId(commitId)
        .path(path)
        .connectorName(connectorDTO.getName())
        .build();
  }

  public SSHKeySpecDTO getSshKeySpecDTO(GitConfigDTO gitConfigDTO, NGAccess ngAccess) {
    return gitConfigAuthenticationInfoHelper.getSSHKey(
        gitConfigDTO, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
  }
}
