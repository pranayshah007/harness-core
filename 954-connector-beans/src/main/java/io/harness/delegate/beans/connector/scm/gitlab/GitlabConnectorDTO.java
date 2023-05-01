/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.gitlab;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.FilePathUtils.removeStartingAndEndingSlash;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.scm.utils.ScmConnectorHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.utils.FilePathUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("GitlabConnector")
@OwnedBy(HarnessTeam.DX)
@Schema(name = "GitlabConnector", description = "This contains details of Gitlab connectors")
public class GitlabConnectorDTO
    extends ConnectorConfigDTO implements ScmConnector, DelegateSelectable, ManagerExecutable {
  @NotNull @JsonProperty("type") GitConnectionType connectionType;
  @NotNull @NotBlank String url;
  private String validationRepo;
  @Valid @NotNull GitlabAuthenticationDTO authentication;
  @Valid GitlabApiAccessDTO apiAccess;
  Set<String> delegateSelectors;
  Boolean executeOnDelegate = true;
  String gitConnectionUrl;

  @Builder
  public GitlabConnectorDTO(GitConnectionType connectionType, String url, String validationRepo,
      GitlabAuthenticationDTO authentication, GitlabApiAccessDTO apiAccess, Set<String> delegateSelectors,
      Boolean executeOnDelegate) {
    this.connectionType = connectionType;
    this.url = url;
    this.validationRepo = validationRepo;
    this.authentication = authentication;
    this.apiAccess = apiAccess;
    this.delegateSelectors = delegateSelectors;
    this.executeOnDelegate = executeOnDelegate;
  }

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    List<DecryptableEntity> decryptableEntities = new ArrayList<>();
    if (authentication.getAuthType() == GitAuthType.HTTP) {
      GitlabHttpCredentialsSpecDTO httpCredentialsSpec =
          ((GitlabHttpCredentialsDTO) authentication.getCredentials()).getHttpCredentialsSpec();
      if (httpCredentialsSpec != null) {
        decryptableEntities.add(httpCredentialsSpec);
      }
    } else {
      GitlabSshCredentialsDTO sshCredential = (GitlabSshCredentialsDTO) authentication.getCredentials();
      if (sshCredential != null) {
        decryptableEntities.add(sshCredential);
      }
    }
    if (apiAccess != null && apiAccess.getSpec() != null) {
      decryptableEntities.add(apiAccess.getSpec());
    }
    return decryptableEntities;
  }

  @Override
  public String getUrl() {
    if (isNotEmpty(gitConnectionUrl)) {
      return gitConnectionUrl;
    }
    return url;
  }

  @Override
  @JsonIgnore
  public ConnectorType getConnectorType() {
    return ConnectorType.GITLAB;
  }

  @Override
  public String getGitConnectionUrl(GitRepositoryDTO gitRepositoryDTO) {
    if (connectionType == GitConnectionType.REPO) {
      String linkedRepo = getGitRepositoryDetails().getName();
      if (!linkedRepo.equals(gitRepositoryDTO.getName())) {
        throw new InvalidRequestException(
            String.format("Provided repoName [%s] does not match with the repoName [%s] provided in connector.",
                gitRepositoryDTO.getName(), linkedRepo));
      }
      return url;
    }
    return FilePathUtils.addEndingSlashIfMissing(url) + gitRepositoryDTO.getName();
  }

  @Override
  public GitRepositoryDTO getGitRepositoryDetails() {
    if (GitConnectionType.REPO.equals(connectionType)) {
      GitRepositoryDTO gitRepositoryDTO = getRepositoryFromApiUrl();
      if (gitRepositoryDTO != null) {
        return gitRepositoryDTO;
      }

      return GitRepositoryDTO.builder()
          .name(GitClientHelper.getGitRepo(url))
          .org(GitClientHelper.getGitOwner(url, false))
          .build();
    }
    return GitRepositoryDTO.builder().org(GitClientHelper.getGitOwner(url, true)).build();
  }

  @Override
  public String getFileUrl(String branchName, String filePath, String commitId, GitRepositoryDTO gitRepositoryDTO) {
    String pathIdentifier = isEmpty(branchName) ? commitId : branchName;
    final String FILE_URL_FORMAT = "%s/-/blob/%s/%s";
    ScmConnectorHelper.validateGetFileUrlParams(pathIdentifier, filePath);
    String repoUrl = removeStartingAndEndingSlash(getGitConnectionUrl(gitRepositoryDTO));
    String httpRepoUrl = GitClientHelper.getCompleteHTTPUrlForGithub(repoUrl);
    filePath = removeStartingAndEndingSlash(filePath);
    return String.format(FILE_URL_FORMAT, httpRepoUrl, pathIdentifier, filePath);
  }

  @Override
  public void validate() {
    GitClientHelper.validateURL(url);
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return GitlabConnectorOutcomeDTO.builder()
        .type(this.connectionType)
        .url(this.url)
        .validationRepo(this.validationRepo)
        .authentication(this.authentication.toOutcome())
        .apiAccess(this.apiAccess)
        .delegateSelectors(this.delegateSelectors)
        .executeOnDelegate(this.executeOnDelegate)
        .build();
  }

  private GitRepositoryDTO getRepositoryFromApiUrl() {
    if (!GitConnectionType.REPO.equals(connectionType) || !GitAuthType.HTTP.equals(authentication.getAuthType())) {
      return null;
    }
    if (apiAccess == null || !(apiAccess.getSpec() instanceof GitlabTokenSpecDTO)) {
      return null;
    }
    GitlabTokenSpecDTO gitlabTokenSpecDTO = (GitlabTokenSpecDTO) apiAccess.getSpec();
    String apiUrl = gitlabTokenSpecDTO.getApiUrl();
    if (StringUtils.isBlank(apiUrl)) {
      return null;
    }
    apiUrl = StringUtils.removeEnd(apiUrl, "/") + "/";
    String ownerAndRepo = StringUtils.removeStart(url, apiUrl);
    ownerAndRepo = StringUtils.removeEnd(ownerAndRepo, ".git");
    if (ownerAndRepo.contains("/")) {
      String[] parts = ownerAndRepo.split("/");
      String repo = parts[parts.length - 1];
      String owner = StringUtils.removeEnd(ownerAndRepo, "/" + repo);
      return GitRepositoryDTO.builder().name(repo).org(owner).build();
    }

    return null;
  }
}
