/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.harness;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
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
@ApiModel("HarnessConnector")
@OwnedBy(HarnessTeam.DX)
@Schema(name = "HarnessConnector", description = "This contains details of Harness connectors")
public class HarnessConnectorDTO extends ConnectorConfigDTO implements ScmConnector, ManagerExecutable {
  @NotNull
  @JsonProperty("type")
  @Schema(type = "string", allowableValues = {"Account", "Repo"})
  GitConnectionType connectionType;
  @NotBlank @NotNull String url;
  String validationRepo;
  @Valid @NotNull HarnessAuthenticationDTO authentication;
  @Valid HarnessApiAccessDTO apiAccess;
  Boolean executeOnDelegate = Boolean.FALSE;
  String gitConnectionUrl;

  @Builder
  public HarnessConnectorDTO(GitConnectionType connectionType, String url, String validationRepo,
      HarnessAuthenticationDTO authentication, HarnessApiAccessDTO apiAccess, boolean executeOnDelegate) {
    this.connectionType = connectionType;
    this.url = url;
    this.validationRepo = validationRepo;
    this.authentication = authentication;
    this.apiAccess = apiAccess;
    this.executeOnDelegate = executeOnDelegate;
  }

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    List<DecryptableEntity> decryptableEntities = new ArrayList<>();
    HarnessHttpCredentialsSpecDTO httpCredentialsSpec =
        ((HarnessHttpCredentialsDTO) authentication.getCredentials()).getHttpCredentialsSpec();
    if (httpCredentialsSpec != null) {
      decryptableEntities.add(httpCredentialsSpec);
    }
    if (apiAccess != null && apiAccess.getSpec() != null) {
      decryptableEntities.add(apiAccess.getSpec());
    }
    return decryptableEntities;
  }

  @Override
  @JsonIgnore
  public ConnectorType getConnectorType() {
    return ConnectorType.HARNESS;
  }

  public String getUrl() {
    if (isNotEmpty(gitConnectionUrl)) {
      return gitConnectionUrl;
    }
    return url;
  }

  @Override
  public String getGitConnectionUrl(GitRepositoryDTO gitRepositoryDTO) {
    return FilePathUtils.addEndingSlashIfMissing(url) + gitRepositoryDTO.getName();
  }

  @Override
  public GitRepositoryDTO getGitRepositoryDetails() {
    return GitRepositoryDTO.builder().org(GitClientHelper.getGitOwner(url, true)).build();
  }

  @Override
  public String getFileUrl(String branchName, String filePath, String commitId, GitRepositoryDTO gitRepositoryDTO) {
    return null;
  }

  @Override
  public void validate() {
    GitClientHelper.validateURL(url);
  }
}
