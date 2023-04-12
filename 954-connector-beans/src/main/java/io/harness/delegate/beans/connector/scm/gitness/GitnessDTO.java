/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.gitness;

import static io.harness.utils.FilePathUtils.removeStartingAndEndingSlash;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.utils.ScmConnectorHelper;
import io.harness.git.GitClientHelper;
import io.harness.gitsync.beans.GitRepositoryDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("GithubConnector")
@OwnedBy(HarnessTeam.DX)
@Schema(name = "GithubConnector", description = "This contains details of Github connectors")
public class GitnessDTO extends ConnectorConfigDTO implements ScmConnector, ManagerExecutable {
  String url;

  ConnectorType connectorType = ConnectorType.GITNESS;

  @Builder
  public GitnessDTO(String url, ConnectorType connectorType) {
    this.url = url;
    this.connectorType = connectorType;
  }

  @Override
  public ConnectorType getConnectorType() {
    return null;
  }

  @Override
  public String getGitConnectionUrl(GitRepositoryDTO gitRepositoryDTO) {
    return url;
  }

  @Override
  public void setGitConnectionUrl(String url) {
    this.url = url;
  }

  @Override
  public String getGitConnectionUrl() {
    return null;
  }

  @Override
  public GitRepositoryDTO getGitRepositoryDetails() {
    return GitRepositoryDTO.builder()
        .name(GitClientHelper.getGitRepo(url))
        .org(GitClientHelper.getGitOwner(url, false))
        .build();
  }

  @Override
  public String getFileUrl(String branchName, String filePath, String commitId, GitRepositoryDTO gitRepositoryDTO) {
    ScmConnectorHelper.validateGetFileUrlParams(branchName, filePath);
    String repoUrl = removeStartingAndEndingSlash(getGitConnectionUrl(gitRepositoryDTO));
    String httpRepoUrl = GitClientHelper.getCompleteHTTPUrlForGithub(repoUrl);
    filePath = removeStartingAndEndingSlash(filePath);
    // todo: see if this needs change
    return String.format("%s/blob/%s/%s", httpRepoUrl, branchName, filePath);
  }

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return null;
  }

  @Override
  public Boolean getExecuteOnDelegate() {
    return null;
  }

  @Override
  public void setExecuteOnDelegate(Boolean executeOnDelegate) {}
}
