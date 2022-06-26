/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.gitsync.beans.GitRepositoryDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Marker interface for all scm connectors.
 */

@OwnedBy(DX)
public interface ScmConnector {
  // Set gitConnectionUrl not this
  void setUrl(String url);
  // This will return gitConnectionUrl if that is set, else will return url.
  // This will be deprecated, moving forward use getGitConnectionUrl.
  String getUrl();
  ConnectorType getConnectorType();

  /**
   * This method is used to fetch final git connection url to the repo
   * If it is a REPO level connector, it should return its url directly
   * If it is an ACCOUNT level connector, it should construct corresponding connection url for input repo
   */
  String getGitConnectionUrl(GitRepositoryDTO gitRepositoryDTO);
  /**
   * This method is used to set gitConnection Url to the repo provided, instead of setting url, set gitConnectionUrl
   * In case of ACCOUNT type connector, a connection url to Repo is required for further usecase
   */
  @JsonIgnore void setGitConnectionUrl(String url);
  // It will return already set gitConnection Url
  String getGitConnectionUrl();

  @JsonIgnore GitRepositoryDTO getGitRepositoryDetails();

  @JsonIgnore String getFileUrl(String branchName, String filePath, GitRepositoryDTO gitRepositoryDTO);
}
