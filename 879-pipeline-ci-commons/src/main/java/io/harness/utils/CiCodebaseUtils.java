/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_AWS_REGION;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_LINK;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR_AVATAR;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR_EMAIL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BEFORE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_MESSAGE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_REF;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_SHA;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_MACHINE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_PORT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_NETRC_USERNAME;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_PULL_REQUEST_TITLE;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_REMOTE_URL;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_SOURCE_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TAG;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TARGET_BRANCH;
import static io.harness.ci.commonconstants.CIExecutionConstants.AWS_CODE_COMMIT_URL_REGEX;
import static io.harness.ci.commonconstants.CIExecutionConstants.PATH_SEPARATOR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.CODECOMMIT;
import static io.harness.delegate.beans.connector.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;
import static io.harness.delegate.beans.connector.scm.adapter.AzureRepoToGitMapper.mapToGitConnectionType;

import static java.lang.String.format;

import io.harness.account.AccountClient;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsCredentialsDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CiCodebaseUtils {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Inject private AccountClient accountClient;

  @Inject private ConnectorUtils connectorUtils;

  private final String PRMergeStatus = "merged";

  @Inject @Named("ngBaseUrl") private String ngBaseUrl;

  public Map<String, String> getGitEnvVariables(ConnectorDetails gitConnector, String repoName) {
    Map<String, String> envVars = new HashMap<>();
    if (gitConnector == null) {
      return envVars;
    }

    validateGitConnector(gitConnector);
    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      validateGithubConnectorAuth(gitConfigDTO);
      envVars = retrieveGitSCMEnvVar(repoName, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      validateAzureRepoConnectorAuth(gitConfigDTO);
      GitConnectionType gitConnectionType = mapToGitConnectionType(gitConfigDTO.getConnectionType());
      envVars = retrieveGitSCMEnvVar(repoName, gitConnectionType, gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      validateGitlabConnectorAuth(gitConfigDTO);
      envVars = retrieveGitSCMEnvVar(repoName, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      validateBitbucketConnectorAuth(gitConfigDTO);
      envVars = retrieveGitSCMEnvVar(repoName, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == CODECOMMIT) {
      AwsCodeCommitConnectorDTO gitConfigDTO = (AwsCodeCommitConnectorDTO) gitConnector.getConnectorConfig();
      envVars = retrieveAwsCodeCommitEnvVar(gitConfigDTO, repoName);
    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      envVars = retrieveGitEnvVar(gitConfigDTO, repoName);
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + gitConnector.getConnectorType());
    }

    return envVars;
  }

  private void validateGitConnector(ConnectorDetails gitConnector) {
    if (gitConnector == null) {
      log.error("Git connector is not valid {}", gitConnector);
      throw new InvalidArgumentsException("Git connector is not valid", WingsException.USER);
    }
    if (gitConnector.getConnectorType() != GIT && gitConnector.getConnectorType() != ConnectorType.GITHUB
        && gitConnector.getConnectorType() != ConnectorType.GITLAB && gitConnector.getConnectorType() != BITBUCKET
        && gitConnector.getConnectorType() != CODECOMMIT && gitConnector.getConnectorType() != AZURE_REPO) {
      log.error("Git connector ref is not of type git {}", gitConnector.getConnectorType());
      throw new InvalidArgumentsException(
          "Connector type is not from supported connectors list GITHUB, GITLAB, BITBUCKET, CODECOMMIT ",
          WingsException.USER);
    }
  }

  private void validateGithubConnectorAuth(GithubConnectorDTO gitConfigDTO) {
    switch (gitConfigDTO.getAuthentication().getAuthType()) {
      case HTTP:
        GithubHttpCredentialsDTO gitAuth = (GithubHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
        if (gitAuth.getType() != GithubHttpAuthenticationType.USERNAME_AND_PASSWORD
            && gitAuth.getType() != GithubHttpAuthenticationType.USERNAME_AND_TOKEN
            && gitAuth.getType() != GithubHttpAuthenticationType.OAUTH) {
          throw new CIStageExecutionException("Unsupported github connector auth type" + gitAuth.getType());
        }
        break;
      case SSH:
        break;
      default:
        throw new CIStageExecutionException(
            "Unsupported github connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
  }

  private void validateBitbucketConnectorAuth(BitbucketConnectorDTO gitConfigDTO) {
    switch (gitConfigDTO.getAuthentication().getAuthType()) {
      case HTTP:
        BitbucketHttpCredentialsDTO gitAuth =
            (BitbucketHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
        if (gitAuth.getType() != BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD) {
          throw new CIStageExecutionException("Unsupported bitbucket connector auth type" + gitAuth.getType());
        }
        break;
      case SSH:
        break;
      default:
        throw new CIStageExecutionException(
            "Unsupported bitbucket connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
  }

  private void validateAzureRepoConnectorAuth(AzureRepoConnectorDTO gitConfigDTO) {
    switch (gitConfigDTO.getAuthentication().getAuthType()) {
      case HTTP:
        AzureRepoHttpCredentialsDTO gitAuth =
            (AzureRepoHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
        if (gitAuth.getType() != AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN) {
          throw new CIStageExecutionException("Unsupported azure repo connector auth type" + gitAuth.getType());
        }
        break;
      case SSH:
        break;
      default:
        throw new CIStageExecutionException(
            "Unsupported azure repo connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
  }

  private void validateGitlabConnectorAuth(GitlabConnectorDTO gitConfigDTO) {
    switch (gitConfigDTO.getAuthentication().getAuthType()) {
      case HTTP:
        GitlabHttpCredentialsDTO gitAuth = (GitlabHttpCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
        if (gitAuth.getType() != GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD
            && gitAuth.getType() != GitlabHttpAuthenticationType.USERNAME_AND_TOKEN
            && gitAuth.getType() != GitlabHttpAuthenticationType.OAUTH) {
          throw new CIStageExecutionException("Unsupported gitlab connector auth type" + gitAuth.getType());
        }
        break;
      case SSH:
        break;
      default:
        throw new CIStageExecutionException(
            "Unsupported gitlab connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
  }

  private Map<String, String> retrieveGitEnvVar(GitConfigDTO gitConfigDTO, String repoName) {
    Map<String, String> envVars = new HashMap<>();
    String gitUrl =
        CiIntegrationStageUtils.getGitURL(repoName, gitConfigDTO.getGitConnectionType(), gitConfigDTO.getUrl());
    String domain = GitClientHelper.getGitSCM(gitUrl);

    envVars.put(DRONE_REMOTE_URL, gitUrl);
    envVars.put(DRONE_NETRC_MACHINE, domain);
    switch (gitConfigDTO.getGitAuthType()) {
      case HTTP:
        GitHTTPAuthenticationDTO gitAuth = (GitHTTPAuthenticationDTO) gitConfigDTO.getGitAuth();
        envVars.put(DRONE_NETRC_USERNAME, gitAuth.getUsername());
        break;
      case SSH:
        break;
      default:
        throw new CIStageExecutionException("Unsupported bitbucket connector auth" + gitConfigDTO.getGitAuthType());
    }
    return envVars;
  }

  private Map<String, String> retrieveGitSCMEnvVar(String repoName, GitConnectionType connectionType, String url) {
    Map<String, String> envVars = new HashMap<>();
    String gitUrl = CiIntegrationStageUtils.getGitURL(repoName, connectionType, url);
    String domain = GitClientHelper.getGitSCM(gitUrl);
    String port = GitClientHelper.getGitSCMPort(gitUrl);
    if (port != null) {
      envVars.put(DRONE_NETRC_PORT, port);
    }

    envVars.put(DRONE_REMOTE_URL, gitUrl);
    envVars.put(DRONE_NETRC_MACHINE, domain);
    return envVars;
  }

  private Map<String, String> retrieveAwsCodeCommitEnvVar(AwsCodeCommitConnectorDTO gitConfigDTO, String repoName) {
    Map<String, String> envVars = new HashMap<>();
    GitConnectionType gitConnectionType =
        gitConfigDTO.getUrlType() == AwsCodeCommitUrlType.REPO ? GitConnectionType.REPO : GitConnectionType.ACCOUNT;
    String gitUrl = CiIntegrationStageUtils.getGitURL(repoName, gitConnectionType, gitConfigDTO.getUrl());

    envVars.put(DRONE_REMOTE_URL, gitUrl);
    envVars.put(DRONE_AWS_REGION, getAwsCodeCommitRegion(gitConfigDTO.getUrl()));
    if (gitConfigDTO.getAuthentication().getAuthType() == AwsCodeCommitAuthType.HTTPS) {
      AwsCodeCommitHttpsCredentialsDTO credentials =
          (AwsCodeCommitHttpsCredentialsDTO) gitConfigDTO.getAuthentication().getCredentials();
      if (credentials.getType() != AwsCodeCommitHttpsAuthType.ACCESS_KEY_AND_SECRET_KEY) {
        throw new CIStageExecutionException("Unsupported aws code commit connector auth type" + credentials.getType());
      }
    } else {
      throw new CIStageExecutionException(
          "Unsupported aws code commit connector auth" + gitConfigDTO.getAuthentication().getAuthType());
    }
    return envVars;
  }

  private String getAwsCodeCommitRegion(String url) {
    Pattern r = Pattern.compile(AWS_CODE_COMMIT_URL_REGEX);
    Matcher m = r.matcher(url);

    if (m.find()) {
      return m.group(1);
    } else {
      throw new InvalidRequestException("Url does not have region information");
    }
  }

  public Map<String, String> getRuntimeCodebaseVars(Ambiance ambiance, ConnectorDetails gitConnectorDetails) {
    Map<String, String> codebaseRuntimeVars = new HashMap<>();

    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
    if (!optionalSweepingOutput.isFound()) {
      return codebaseRuntimeVars;
    }

    CodebaseSweepingOutput codebaseSweeping = (CodebaseSweepingOutput) optionalSweepingOutput.getOutput();
    String commitSha = codebaseSweeping.getCommitSha();

    /* Bitbucket SAAS does not generate refs/pull-requests/* which requires us to do this special handling.
      Override commit ref to source branch instead of pull request ref. Same is true for some versions of
      bitbucket server too.
     */
    String commitRef = codebaseSweeping.getCommitRef();
    if (gitConnectorDetails != null && gitConnectorDetails.getConnectorType() == BITBUCKET) {
      if (isNotEmpty(codebaseSweeping.getState()) && codebaseSweeping.getState().equals(PRMergeStatus)) {
        commitRef = format("+refs/heads/%s", codebaseSweeping.getTargetBranch());
        if (isNotEmpty(codebaseSweeping.getMergeSha())) {
          commitSha = codebaseSweeping.getMergeSha();
        }
      } else {
        commitRef = format("+refs/heads/%s", codebaseSweeping.getSourceBranch());
      }
    }

    String commitMessage = codebaseSweeping.getCommitMessage();
    if (isNotEmpty(commitMessage)) {
      codebaseRuntimeVars.put(DRONE_COMMIT_MESSAGE, commitMessage);
    }

    String buildLink = getBuildDetailsUrl(ambiance);
    if (isNotEmpty(buildLink)) {
      codebaseRuntimeVars.put(DRONE_BUILD_LINK, buildLink);
    }

    if (isNotEmpty(codebaseSweeping.getPrTitle())) {
      codebaseRuntimeVars.put(DRONE_PULL_REQUEST_TITLE, codebaseSweeping.getPrTitle());
    }

    if (isNotEmpty(commitRef)) {
      codebaseRuntimeVars.put(DRONE_COMMIT_REF, commitRef);
    }

    if (isNotEmpty(codebaseSweeping.getBranch())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_BRANCH, codebaseSweeping.getBranch());
    }

    if (codebaseSweeping.getBuild() != null && isNotEmpty(codebaseSweeping.getBuild().getType())
        && codebaseSweeping.getBuild().getType().equals("PR")) {
      codebaseRuntimeVars.put(DRONE_BUILD_EVENT, "pull_request");
    }

    if (!isEmpty(codebaseSweeping.getTag())) {
      codebaseRuntimeVars.put(DRONE_TAG, codebaseSweeping.getTag());
      codebaseRuntimeVars.put(DRONE_BUILD_EVENT, "tag");
    }

    if (!isEmpty(codebaseSweeping.getTargetBranch())) {
      codebaseRuntimeVars.put(DRONE_TARGET_BRANCH, codebaseSweeping.getTargetBranch());
    }

    if (!isEmpty(codebaseSweeping.getSourceBranch())) {
      codebaseRuntimeVars.put(DRONE_SOURCE_BRANCH, codebaseSweeping.getSourceBranch());
    }

    if (!isEmpty(codebaseSweeping.getGitUserEmail())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_AUTHOR_EMAIL, codebaseSweeping.getGitUserEmail());
    }
    if (!isEmpty(codebaseSweeping.getGitUserAvatar())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_AUTHOR_AVATAR, codebaseSweeping.getGitUserAvatar());
    }

    if (!isEmpty(codebaseSweeping.getGitUserId())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_AUTHOR, codebaseSweeping.getGitUserId());
    }
    if (isNotEmpty(codebaseSweeping.getBaseCommitSha())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_BEFORE, codebaseSweeping.getBaseCommitSha());
    }
    if (isNotEmpty(commitSha)) {
      codebaseRuntimeVars.put(DRONE_COMMIT_SHA, commitSha);
    }

    return codebaseRuntimeVars;
  }

  public String getBuildDetailsUrl(Ambiance ambiance) {
    String stageSetupId = AmbianceUtils.getStageSetupIdAmbiance(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String baseUrl = getNgBaseUrl(getVanityUrl(ngAccess.getAccountIdentifier()), ngBaseUrl);
    String pipelineId = ambiance.getMetadata().getPipelineIdentifier();
    String executionId = ambiance.getMetadata().getExecutionUuid();
    return getBuildDetailsUrl(ngAccess, pipelineId, executionId, baseUrl, stageSetupId, stageExecutionId);
  }

  private String getVanityUrl(String accountID) {
    return CGRestUtils.getResponse(accountClient.getVanityUrl(accountID));
  }

  private String getNgBaseUrl(String vanityUrl, String defaultBaseUrl) {
    if (isEmpty(vanityUrl)) {
      return defaultBaseUrl;
    }

    String newBaseUrl = StringUtils.stripEnd(vanityUrl, PATH_SEPARATOR);
    try {
      URL url = new URL(defaultBaseUrl);
      String hostUrl = String.format("%s://%s", url.getProtocol(), url.getHost());
      return StringUtils.join(newBaseUrl, defaultBaseUrl.substring(hostUrl.length()));
    } catch (Exception e) {
      log.warn("There was error while generating vanity URL", e);
      return defaultBaseUrl;
    }
  }

  public String getBuildDetailsUrl(NGAccess ngAccess, String pipelineId, String executionId, String ngBaseUrl,
      String stageSetupId, String stageExecutionId) {
    String detailsUrl = new StringBuilder(ngBaseUrl)
                            .append(String.format("/account/%s", ngAccess.getAccountIdentifier()))
                            .append(String.format("/ci/orgs/%s", ngAccess.getOrgIdentifier()))
                            .append(String.format("/projects/%s", ngAccess.getProjectIdentifier()))
                            .append(String.format("/pipelines/%s", pipelineId))
                            .append(String.format("/executions/%s", executionId))
                            .append("/pipeline?")
                            .append(String.format("stage=%s", stageSetupId))
                            .append(String.format("&stageExecId=%s", stageExecutionId))
                            .toString();
    log.info("DetailsUrl is: [{}]", detailsUrl);
    return detailsUrl;
  }

  public ConnectorDetails getGitConnector(NGAccess ngAccess, String gitConnectorRefValue) {
    if (gitConnectorRefValue == null) {
      throw new CIStageExecutionException("Git connector is mandatory in case git clone is enabled");
    }
    return connectorUtils.getConnectorDetails(ngAccess, gitConnectorRefValue);
  }
}
