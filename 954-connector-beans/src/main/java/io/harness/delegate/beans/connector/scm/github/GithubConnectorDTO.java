/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.github;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
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
import org.hibernate.validator.constraints.NotBlank;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("GithubConnector")
@OwnedBy(HarnessTeam.DX)
@Schema(name = "GithubConnector", description = "This contains details of Github connectors")
public class GithubConnectorDTO
    extends ConnectorConfigDTO implements ScmConnector, DelegateSelectable, ManagerExecutable {
  @NotNull @JsonProperty("type") GitConnectionType connectionType;
  @NotBlank @NotNull String url;
  String validationRepo;
  @Valid @NotNull GithubAuthenticationDTO authentication;
  @Valid GithubApiAccessDTO apiAccess;
  Set<String> delegateSelectors;
  Boolean executeOnDelegate;

  @Builder
  public GithubConnectorDTO(GitConnectionType connectionType, String url, String validationRepo,
      GithubAuthenticationDTO authentication, GithubApiAccessDTO apiAccess, Set<String> delegateSelectors,
      boolean executeOnDelegate) {
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
      GithubHttpCredentialsSpecDTO httpCredentialsSpec =
          ((GithubHttpCredentialsDTO) authentication.getCredentials()).getHttpCredentialsSpec();
      if (httpCredentialsSpec != null) {
        decryptableEntities.add(httpCredentialsSpec);
      }
    } else {
      GithubSshCredentialsDTO sshCredential = (GithubSshCredentialsDTO) authentication.getCredentials();
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
  @JsonIgnore
  public ConnectorType getConnectorType() {
    return ConnectorType.GITHUB;
  }

  @Override
  public String getGitConnectionUrl(String repoName) {
    if (connectionType == GitConnectionType.REPO) {
      String linkedRepo = GitClientHelper.getGitRepo(url);
      if (!linkedRepo.equals(repoName)) {
        throw new InvalidRequestException(
            String.format("Provided repoName [%s] does not match with the repoName [%s] provided in connector.",
                repoName, linkedRepo));
      }
      return getUrl();
    }
    return FilePathUtils.addEndingSlashIfMissing(getUrl()) + repoName;
  }

  @Override
  public GitRepositoryDTO getGitRepositoryDetails() {
    if (GitConnectionType.REPO.equals(connectionType)) {
      return GitRepositoryDTO.builder()
          .name(GitClientHelper.getGitRepo(url))
          .org(GitClientHelper.getGitOwner(url, false))
          .build();
    }
    return GitRepositoryDTO.builder().org(GitClientHelper.getGitOwner(url, true)).build();
  }

  @Override
  public void validate() {
    GitClientHelper.validateURL(url);
  }
}
