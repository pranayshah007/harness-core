/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stateutils.buildstate;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.common.BuildEnvironmentConstants.DRONE_AWS_REGION;
import static io.harness.common.BuildEnvironmentConstants.DRONE_BUILD_EVENT;
import static io.harness.common.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR;
import static io.harness.common.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR_AVATAR;
import static io.harness.common.BuildEnvironmentConstants.DRONE_COMMIT_AUTHOR_EMAIL;
import static io.harness.common.BuildEnvironmentConstants.DRONE_COMMIT_BEFORE;
import static io.harness.common.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.common.BuildEnvironmentConstants.DRONE_COMMIT_REF;
import static io.harness.common.BuildEnvironmentConstants.DRONE_COMMIT_SHA;
import static io.harness.common.BuildEnvironmentConstants.DRONE_NETRC_MACHINE;
import static io.harness.common.BuildEnvironmentConstants.DRONE_NETRC_PORT;
import static io.harness.common.BuildEnvironmentConstants.DRONE_NETRC_USERNAME;
import static io.harness.common.BuildEnvironmentConstants.DRONE_REMOTE_URL;
import static io.harness.common.BuildEnvironmentConstants.DRONE_SOURCE_BRANCH;
import static io.harness.common.BuildEnvironmentConstants.DRONE_TAG;
import static io.harness.common.BuildEnvironmentConstants.DRONE_TARGET_BRANCH;
import static io.harness.common.CIExecutionConstants.AWS_CODE_COMMIT_URL_REGEX;
import static io.harness.common.CIExecutionConstants.AZURE_REPO_GIT_LABEL;
import static io.harness.common.CIExecutionConstants.PATH_SEPARATOR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.CODECOMMIT;
import static io.harness.delegate.beans.connector.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;

import static java.lang.String.format;

import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.ci.integrationstage.BuildEnvironmentUtils;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
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
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
public class CodebaseUtils {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;

  public Map<String, String> getCodebaseVars(Ambiance ambiance, CIExecutionArgs ciExecutionArgs) {
    Map<String, String> envVars = BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs);
    envVars.putAll(getRuntimeCodebaseVars(ambiance));
    return envVars;
  }

  public Map<String, String> getRuntimeCodebaseVars(Ambiance ambiance) {
    Map<String, String> codebaseRuntimeVars = new HashMap<>();

    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
    if (!optionalSweepingOutput.isFound()) {
      return codebaseRuntimeVars;
    }

    CodebaseSweepingOutput codebaseSweeping = (CodebaseSweepingOutput) optionalSweepingOutput.getOutput();

    /* Bitbucket SAAS does not generate refs/pull-requests/* which requires us to do this special handling.
      Override commit ref to source branch instead of pull request ref
     */
    String commitRef = codebaseSweeping.getCommitRef();
    if (isNotEmpty(codebaseSweeping.getPullRequestLink())
        && codebaseSweeping.getPullRequestLink().contains("bitbucket.org")) {
      commitRef = format("+refs/heads/%s", codebaseSweeping.getSourceBranch());
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
    if (isNotEmpty(codebaseSweeping.getCommitSha())) {
      codebaseRuntimeVars.put(DRONE_COMMIT_SHA, codebaseSweeping.getCommitSha());
    }

    return codebaseRuntimeVars;
  }

  public Map<String, String> getGitEnvVariables(ConnectorDetails gitConnector, CodeBase ciCodebase) {
    Map<String, String> envVars = new HashMap<>();
    if (gitConnector == null) {
      return envVars;
    }

    validateGitConnector(gitConnector);
    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      validateGithubConnectorAuth(gitConfigDTO);
      envVars = retrieveGitSCMEnvVar(ciCodebase, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      validateAzureRepoConnectorAuth(gitConfigDTO);
      envVars = retrieveGitSCMEnvVar(ciCodebase, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      validateGitlabConnectorAuth(gitConfigDTO);
      envVars = retrieveGitSCMEnvVar(ciCodebase, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      validateBitbucketConnectorAuth(gitConfigDTO);
      envVars = retrieveGitSCMEnvVar(ciCodebase, gitConfigDTO.getConnectionType(), gitConfigDTO.getUrl());
    } else if (gitConnector.getConnectorType() == CODECOMMIT) {
      AwsCodeCommitConnectorDTO gitConfigDTO = (AwsCodeCommitConnectorDTO) gitConnector.getConnectorConfig();
      envVars = retrieveAwsCodeCommitEnvVar(gitConfigDTO, ciCodebase);
    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      envVars = retrieveGitEnvVar(gitConfigDTO, ciCodebase);
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + gitConnector.getConnectorType());
    }

    return envVars;
  }

  private Map<String, String> retrieveGitSCMEnvVar(CodeBase ciCodebase, GitConnectionType connectionType, String url) {
    Map<String, String> envVars = new HashMap<>();
    String gitUrl = IntegrationStageUtils.getGitURL(ciCodebase, connectionType, url);
    String domain = GitClientHelper.getGitSCM(gitUrl);
    String port = GitClientHelper.getGitSCMPort(gitUrl);
    if (port != null) {
      envVars.put(DRONE_NETRC_PORT, port);
    }

    envVars.put(DRONE_REMOTE_URL, gitUrl);
    envVars.put(DRONE_NETRC_MACHINE, domain);
    return envVars;
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
            && gitAuth.getType() != GitlabHttpAuthenticationType.USERNAME_AND_TOKEN) {
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

  private Map<String, String> retrieveGitEnvVar(GitConfigDTO gitConfigDTO, CodeBase ciCodebase) {
    Map<String, String> envVars = new HashMap<>();
    String gitUrl =
        IntegrationStageUtils.getGitURL(ciCodebase, gitConfigDTO.getGitConnectionType(), gitConfigDTO.getUrl());
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

  private Map<String, String> retrieveAwsCodeCommitEnvVar(AwsCodeCommitConnectorDTO gitConfigDTO, CodeBase ciCodebase) {
    Map<String, String> envVars = new HashMap<>();
    GitConnectionType gitConnectionType =
        gitConfigDTO.getUrlType() == AwsCodeCommitUrlType.REPO ? GitConnectionType.REPO : GitConnectionType.ACCOUNT;
    String gitUrl = IntegrationStageUtils.getGitURL(ciCodebase, gitConnectionType, gitConfigDTO.getUrl());

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

    // TODO Validate all

    //    GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
    //    if (gitConfigDTO.getGitAuthType() != GitAuthType.HTTP && gitConfigDTO.getGitAuthType() != GitAuthType.SSH) {
    //      log.error("Git connector ref is of invalid auth type {}", gitConnector);
    //      throw new InvalidArgumentsException("Invalid auth provided for git connector", WingsException.USER);
    //    }
  }

  public ConnectorDetails getGitConnector(NGAccess ngAccess, CodeBase codeBase, boolean skipGitClone) {
    if (skipGitClone) {
      return null;
    }

    if (codeBase == null) {
      throw new CIStageExecutionException("CI codebase is mandatory in case git clone is enabled");
    }

    if (codeBase.getConnectorRef().getValue() == null) {
      throw new CIStageExecutionException("Git connector is mandatory in case git clone is enabled");
    }
    return connectorUtils.getConnectorDetails(ngAccess, codeBase.getConnectorRef().getValue());
  }

  public static String getCompleteURLFromConnector(
      ConnectorDetails connectorDetails, String projectName, String repoName) {
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
    GitConnectionType gitConnectionType = getGitConnectionType(connectorDetails);
    String completeURL = scmConnector.getUrl();

    if (isNotEmpty(repoName) && (gitConnectionType == null || gitConnectionType == GitConnectionType.ACCOUNT)) {
      if (scmConnector instanceof AzureRepoConnectorDTO) {
        if (isEmpty(projectName)) {
          throw new IllegalArgumentException("Project name is not set for azure repo");
        }
        completeURL = getCompleteUrlForAccountLevelAzureConnector(completeURL, projectName, repoName);
      } else {
        completeURL = StringUtils.join(StringUtils.stripEnd(scmConnector.getUrl(), PATH_SEPARATOR), PATH_SEPARATOR,
            StringUtils.stripStart(repoName, PATH_SEPARATOR));
      }
    }

    return completeURL;
  }

  public static GitConnectionType getGitConnectionType(ConnectorDetails gitConnector) {
    if (gitConnector == null) {
      return null;
    }

    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == CODECOMMIT) {
      AwsCodeCommitConnectorDTO gitConfigDTO = (AwsCodeCommitConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrlType() == AwsCodeCommitUrlType.REPO ? GitConnectionType.REPO
                                                                    : GitConnectionType.ACCOUNT;
    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getGitConnectionType();
    } else {
      throw new CIStageExecutionException("Unsupported git connector type" + gitConnector.getConnectorType());
    }
  }

  public static String getCompleteUrlForAccountLevelAzureConnector(String url, String projectName, String repoName) {
    String azureCompleteUrl = StringUtils.join(
        StringUtils.stripEnd(url, PATH_SEPARATOR), PATH_SEPARATOR, StringUtils.stripStart(projectName, PATH_SEPARATOR));
    if (GitClientHelper.isHTTPProtocol(azureCompleteUrl)) {
      azureCompleteUrl = StringUtils.join(azureCompleteUrl, AZURE_REPO_GIT_LABEL);
    } else if (GitClientHelper.isSSHProtocol(azureCompleteUrl)) {
      azureCompleteUrl = StringUtils.join(azureCompleteUrl, PATH_SEPARATOR);
    }
    return StringUtils.join(azureCompleteUrl, StringUtils.stripStart(repoName, PATH_SEPARATOR));
  }
}
