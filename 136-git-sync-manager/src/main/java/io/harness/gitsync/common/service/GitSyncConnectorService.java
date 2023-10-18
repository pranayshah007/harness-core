/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.service;

import io.harness.beans.Scope;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.git.YamlGitConfigDTO;

import java.util.Optional;

public interface GitSyncConnectorService {
  ScmConnector getDecryptedConnector(
      String yamlGitConfigIdentifier, String projectIdentifier, String orgIdentifier, String accountId);

  ScmConnector getDecryptedConnector(YamlGitConfigDTO gitSyncConfigDTO, String accountId);

  ScmConnector getDecryptedConnector(
      String accountId, String orgIdentifier, String projectIdentifier, ScmConnector connectorDTO);

  ScmConnector getDecryptedConnectorForNewGitX(
      String accountId, String orgIdentifier, String projectIdentifier, ScmConnector connectorDTO);

  ScmConnector getDecryptedConnector(
      YamlGitConfigDTO gitSyncConfigDTO, String accountId, ConnectorResponseDTO connectorDTO);

  ScmConnector getScmConnector(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorRef, String connectorRepo, String connectorBranch);

  ScmConnector getDecryptedConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repoUrl);

  Optional<ConnectorResponseDTO> getConnectorFromDefaultBranchElseFromGitBranch(String accountId, String orgIdentifier,
      String projectIdentifier, String identifier, String connectorRepo, String connectorBranch);

  Optional<ConnectorResponseDTO> getConnectorFromRepoBranch(String accountId, String orgIdentifier,
      String projectIdentifier, String identifier, String connectorRepo, String connectorBranch);

  ScmConnector getScmConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef);

  ScmConnector getDecryptedConnectorByRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef);

  ScmConnector getScmConnectorForGivenRepo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repoName);

  ScmConnector getScmConnectorForGivenRepo(Scope scope, String connectorRef, String repoName);

  ScmConnector getDecryptedConnectorForGivenRepo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repoName);
}
