package io.harness.delegate.beans.connector.scm.gittea;

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
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.gittea.outcome.GitteaConnectorOutcomeDTO;
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
import org.hibernate.validator.constraints.NotBlank;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("GitteaConnector")
@OwnedBy(HarnessTeam.DX)
@Schema(name = "GitteaConnector", description = "This contains details of Gittea connectors")
public class GitteaConnectorDTO
        extends ConnectorConfigDTO implements ScmConnector, DelegateSelectable, ManagerExecutable {
  @NotNull @JsonProperty("type") GitConnectionType connectionType;
  @NotNull @NotBlank String url;
  private String validationRepo;
  @Valid @NotNull GitteaAuthenticationDTO authentication;
  Set<String> delegateSelectors;
  Boolean executeOnDelegate = true;
  String gitConnectionUrl;

  @Builder
  public GitteaConnectorDTO(GitConnectionType connectionType, String url, String validationRepo,
                            GitteaAuthenticationDTO authentication, Set<String> delegateSelectors,
                            Boolean executeOnDelegate) {
    this.connectionType = connectionType;
    this.url = url;
    this.validationRepo = validationRepo;
    this.authentication = authentication;
    this.delegateSelectors = delegateSelectors;
    this.executeOnDelegate = executeOnDelegate;
  }

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    List<DecryptableEntity> decryptableEntities = new ArrayList<>();
    if (authentication.getAuthType() == GitAuthType.HTTP) {
      GitteaHttpCredentialsSpecDTO httpCredentialsSpec =
              ((GitteaHttpCredentialsDTO) authentication.getCredentials()).getHttpCredentialsSpec();
      if (httpCredentialsSpec != null) {
        decryptableEntities.add(httpCredentialsSpec);
      }
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
    return ConnectorType.GITTEA;
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
      return GitRepositoryDTO.builder()
              .name(GitClientHelper.getGitRepo(url))
              .org(GitClientHelper.getGitOwner(url, false))
              .build();
    }
    return GitRepositoryDTO.builder().org(GitClientHelper.getGitOwner(url, true)).build();
  }

  @Override
  public String getFileUrl(String branchName, String filePath, String commitId, GitRepositoryDTO gitRepositoryDTO) {
    final String FILE_URL_FORMAT = "%s/tree/%s/%s";
    ScmConnectorHelper.validateGetFileUrlParams(branchName, filePath);
    String repoUrl = removeStartingAndEndingSlash(getGitConnectionUrl(gitRepositoryDTO));
    String httpRepoUrl = GitClientHelper.getCompleteHTTPUrlForGithub(repoUrl);
    filePath = removeStartingAndEndingSlash(filePath);
    return String.format(FILE_URL_FORMAT, httpRepoUrl, branchName, filePath);
  }

  @Override
  public void validate() {
    GitClientHelper.validateURL(url);
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return GitteaConnectorOutcomeDTO.builder()
            .type(this.connectionType)
            .url(this.url)
            .validationRepo(this.validationRepo)
            .authentication(this.authentication.toOutcome())
            .delegateSelectors(this.delegateSelectors)
            .executeOnDelegate(this.executeOnDelegate)
            .build();
  }
}