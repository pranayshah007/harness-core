/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.genericgitconnector;

import io.harness.annotation.RecasterAlias;
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
import io.harness.delegate.beans.connector.scm.genericgitconnector.outcome.GitConfigOutcomeDTO;
import io.harness.git.GitClientHelper;
import io.harness.gitsync.beans.GitRepositoryDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
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
@OwnedBy(HarnessTeam.DX)

@Schema(name = "GitConfig", description = "This contains details of the Generic Git connector")
@RecasterAlias("io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO")
public class GitConfigDTO extends ConnectorConfigDTO implements ScmConnector, DelegateSelectable, ManagerExecutable {
  @NotNull @JsonProperty("type") private GitAuthType gitAuthType;
  @NotNull @JsonProperty("connectionType") private GitConnectionType gitConnectionType;
  @NotNull @NotBlank String url;
  private String validationRepo;
  private String branchName;
  private Set<String> delegateSelectors;
  private Boolean executeOnDelegate;
  private String gitConnectionUrl;

  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  @NotNull
  GitAuthenticationDTO gitAuth;

  @Builder
  public GitConfigDTO(GitAuthType gitAuthType, GitAuthenticationDTO gitAuth, GitConnectionType gitConnectionType,
      String url, String validationRepo, String branchName, Set<String> delegateSelectors, Boolean executeOnDelegate) {
    this.gitAuthType = gitAuthType;
    this.gitAuth = gitAuth;
    this.gitConnectionType = gitConnectionType;
    this.url = url;
    this.validationRepo = validationRepo;
    this.branchName = branchName;
    this.delegateSelectors = delegateSelectors;
    this.executeOnDelegate = executeOnDelegate;
  }

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(gitAuth);
  }

  @Override
  @JsonIgnore
  public ConnectorType getConnectorType() {
    return ConnectorType.GIT;
  }

  @Override
  public String getGitConnectionUrl(GitRepositoryDTO gitRepositoryDTO) {
    return "";
  }

  @Override
  public GitRepositoryDTO getGitRepositoryDetails() {
    return GitRepositoryDTO.builder().build();
  }

  @Override
  public String getFileUrl(String branchName, String filePath, String commitId, GitRepositoryDTO gitRepositoryDTO) {
    return "";
  }

  @Override
  public void validate() {
    GitClientHelper.validateURL(url);
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return GitConfigOutcomeDTO.builder()
        .type(this.gitAuthType)
        .connectionType(this.gitConnectionType)
        .url(this.url)
        .validationRepo(this.validationRepo)
        .branchName(this.branchName)
        .delegateSelectors(this.delegateSelectors)
        .executeOnDelegate(this.executeOnDelegate)
        .spec(this.gitAuth.toOutcome())
        .build();
  }
}
