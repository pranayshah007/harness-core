/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.bitbucket;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.FilePathUtils.FILE_PATH_SEPARATOR;
import static io.harness.utils.FilePathUtils.removeStartingAndEndingSlash;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
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
import java.net.URL;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("BitbucketConnector")
@Slf4j
@OwnedBy(HarnessTeam.DX)
@Schema(name = "BitbucketConnector", description = "This contains details of Bitbucket connectors")
public class BitbucketConnectorDTO extends ConnectorConfigDTO implements ScmConnector, DelegateSelectable {
  @NotNull @JsonProperty("type") private GitConnectionType connectionType;
  @NotNull @NotBlank private String url;
  private String validationRepo;
  @Valid @NotNull private BitbucketAuthenticationDTO authentication;
  @Valid private BitbucketApiAccessDTO apiAccess;
  private Set<String> delegateSelectors;
  private String gitConnectionUrl;

  @Builder
  public BitbucketConnectorDTO(GitConnectionType connectionType, String url, String validationRepo,
      BitbucketAuthenticationDTO authentication, BitbucketApiAccessDTO apiAccess, Set<String> delegateSelectors) {
    this.connectionType = connectionType;
    this.url = url;
    this.validationRepo = validationRepo;
    this.authentication = authentication;
    this.apiAccess = apiAccess;
    this.delegateSelectors = delegateSelectors;
  }

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    List<DecryptableEntity> decryptableEntities = new ArrayList<>();
    if (authentication.getAuthType() == GitAuthType.HTTP) {
      BitbucketHttpCredentialsSpecDTO httpCredentialsSpec =
          ((BitbucketHttpCredentialsDTO) authentication.getCredentials()).getHttpCredentialsSpec();
      if (httpCredentialsSpec != null) {
        decryptableEntities.add(httpCredentialsSpec);
      }
    } else {
      BitbucketSshCredentialsDTO sshCredential = (BitbucketSshCredentialsDTO) authentication.getCredentials();
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
    return ConnectorType.BITBUCKET;
  }

  public String getUrl() {
    if (isNotEmpty(gitConnectionUrl)) {
      return gitConnectionUrl;
    }
    return url;
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
      return getUrl();
    }
    return FilePathUtils.addEndingSlashIfMissing(getUrl()) + gitRepositoryDTO.getName();
  }

  @Override
  public GitRepositoryDTO getGitRepositoryDetails() {
    if (GitClientHelper.isBitBucketSAAS(url)) {
      if (GitConnectionType.REPO.equals(connectionType)) {
        return GitRepositoryDTO.builder()
            .name(GitClientHelper.getGitRepo(url))
            .org(GitClientHelper.getGitOwner(url, false))
            .build();
      }
      return GitRepositoryDTO.builder().org(GitClientHelper.getGitOwner(url, true)).build();
    }
    return getGitRepositoryDetailsForBitbucketServer();
  }

  @Override
  public String getFileUrl(String branchName, String filePath, GitRepositoryDTO gitRepositoryDTO) {
    ScmConnectorHelper.validateGetFileUrlParams(branchName, filePath);
    String repoUrl = removeStartingAndEndingSlash(getGitConnectionUrl(gitRepositoryDTO));
    filePath = removeStartingAndEndingSlash(filePath);
    if (GitClientHelper.isBitBucketSAAS(repoUrl)) {
      return String.format("%s/src/%s/%s", repoUrl, branchName, filePath);
    }
    return getFileUrlForBitbucketServer(repoUrl, branchName, filePath);
  }

  @Override
  public void validate() {
    GitClientHelper.validateURL(url);
  }

  private GitRepositoryDTO getGitRepositoryDetailsForBitbucketServer() {
    final String HOST_URL_AND_ORG_SEPARATOR = "scm";
    String repoName = GitClientHelper.getGitRepo(url);
    String orgName = GitClientHelper.getGitOwner(url, true);
    String[] parts = new String[0];
    if (orgName.equals(HOST_URL_AND_ORG_SEPARATOR)) {
      parts = repoName.split(FILE_PATH_SEPARATOR);
    } else if (repoName.startsWith(HOST_URL_AND_ORG_SEPARATOR + FILE_PATH_SEPARATOR)) {
      parts = StringUtils.removeStart(repoName, HOST_URL_AND_ORG_SEPARATOR + FILE_PATH_SEPARATOR)
                  .split(FILE_PATH_SEPARATOR);
    }
    if (GitConnectionType.REPO.equals(connectionType) && parts.length == 2) {
      return GitRepositoryDTO.builder().org(parts[0]).name(parts[1]).build();
    } else if (GitConnectionType.ACCOUNT.equals(connectionType) && parts.length == 1) {
      return GitRepositoryDTO.builder().org(parts[0]).build();
    } else {
      throw new InvalidRequestException("Provided bitbucket server repository url is invalid.");
    }
  }

  private String getFileUrlForBitbucketServer(String repoUrl, String branchName, String filePath) {
    String hostUrl = "";
    try {
      URL url1 = new URL(repoUrl);
      hostUrl = url1.getProtocol() + "://" + url1.getHost();
    } catch (Exception ex) {
      log.error("Exception occurred while parsing bitbucket server url.", ex);
      throw new InvalidRequestException("Exception occurred while parsing bitbucket server url.");
    }
    return String.format("%s/projects/%s/repos/%s/browse/%s?at=refs/heads/%s", hostUrl,
        getGitRepositoryDetails().getOrg(), getGitRepositoryDetails().getName(), filePath, branchName);
  }
}
