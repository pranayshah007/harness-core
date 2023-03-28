/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.git.checks;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.cistatus.service.GithubAppConfig;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.azurerepo.AzureRepoConfig;
import io.harness.cistatus.service.azurerepo.AzureRepoContext;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.cistatus.service.bitbucket.BitbucketConfig;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.gitlab.GitlabConfig;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.cistatus.service.gitlab.GitlabServiceImpl;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.git.GitClientHelper;
import io.harness.git.GitTokenRetriever;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
public class GitStatusCheckHelper {
  public static final String TARGET_URL = "target_url";
  @Inject private GithubService githubService;
  @Inject private BitbucketService bitbucketService;
  @Inject private GitlabService gitlabService;
  @Inject private AzureRepoService azureRepoService;

  @Inject private GitTokenRetriever gitTokenRetriever;
  private static final String DESC = "description";
  private static final String STATE = "state";
  private static final String URL = "url";
  private static final String CONTEXT = "context";

  private static final String BITBUCKET_KEY = "key";
  private static final String GITHUB_API_URL = "https://api.github.com/";
  private static final String BITBUCKET_API_URL = "https://api.bitbucket.org/";
  private static final String GITLAB_API_URL = "https://gitlab.com/api/";
  private static final String AZURE_REPO_API_URL = "https://dev.azure.com/";
  private static final String AZURE_REPO_GENRE = "HarnessCI";
  private static final String PATH_SEPARATOR = "/";
  private static final String GITLAB_GENRE = "Harness CI";

  private static final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 3;

  // Sends the status check to the scm provider
  public boolean sendStatus(GitStatusCheckParams gitStatusCheckParams) {
    String sha = gitStatusCheckParams.getSha();
    try {
      boolean statusSent = false;
      if (gitStatusCheckParams.getGitSCMType() == GitSCMType.GITHUB) {
        statusSent = sendBuildStatusToGitHub(gitStatusCheckParams);
      } else if (gitStatusCheckParams.getGitSCMType() == GitSCMType.BITBUCKET) {
        statusSent = sendBuildStatusToBitbucket(gitStatusCheckParams);
      } else if (gitStatusCheckParams.getGitSCMType() == GitSCMType.GITLAB) {
        statusSent = sendBuildStatusToGitLab(gitStatusCheckParams);
      } else if (gitStatusCheckParams.getGitSCMType() == GitSCMType.AZURE_REPO) {
        statusSent = sendBuildStatusToAzureRepo(gitStatusCheckParams);
      } else {
        throw new UnsupportedOperationException("Not supported");
      }

      if (statusSent) {
        log.info("Successfully sent the git status for sha {}, stage identifier {}", gitStatusCheckParams.getSha(),
            gitStatusCheckParams.getIdentifier());
        return true;
      } else {
        log.info("Failed to send the git status for sha {}, stage identifier {}", gitStatusCheckParams.getSha(),
            gitStatusCheckParams.getIdentifier());
        return false;
      }
    } catch (Exception ex) {
      log.error(String.format("failed to send status for sha %s", sha), ex);
      return false;
    }
  }

  private boolean sendBuildStatusToGitHub(GitStatusCheckParams gitStatusCheckParams) {
    GithubConnectorDTO gitConfigDTO =
        (GithubConnectorDTO) gitStatusCheckParams.getConnectorDetails().getConnectorConfig();

    GithubApiAccessDTO githubApiAccessDTO = gitConfigDTO.getApiAccess();
    if (githubApiAccessDTO == null) {
      log.warn("Not sending status because api access is not enabled for sha {}", gitStatusCheckParams.getSha());
      return false;
    }

    String token = gitTokenRetriever.retrieveAuthToken(
        gitStatusCheckParams.getGitSCMType(), gitStatusCheckParams.getConnectorDetails());
    if (isNotEmpty(token)) {
      Map<String, Object> bodyObjectMap = new HashMap<>();
      bodyObjectMap.put(DESC, gitStatusCheckParams.getDesc());
      bodyObjectMap.put(CONTEXT, gitStatusCheckParams.getIdentifier());
      bodyObjectMap.put(STATE, gitStatusCheckParams.getState());
      bodyObjectMap.put(TARGET_URL, gitStatusCheckParams.getDetailsUrl());
      // TODO Sending Just URL will require refactoring in sendStatus method, Will be done POST CI GA
      GithubAppConfig githubAppConfig =
          GithubAppConfig.builder().githubUrl(getGitApiURL(gitConfigDTO.getUrl())).build();

      RetryPolicy<Object> retryPolicy =
          getRetryPolicy(format("[Retrying failed call to send status for github check: [%s], attempt: {}",
                             gitStatusCheckParams.getSha()),
              format("Failed call to send status for github check: [%s] after retrying {} times",
                  gitStatusCheckParams.getSha()));

      return Failsafe.with(retryPolicy)
          .get(()
                   -> githubService.sendStatus(githubAppConfig, token, gitStatusCheckParams.getSha(),
                       gitStatusCheckParams.getOwner(), gitStatusCheckParams.getRepo(), bodyObjectMap));
    } else {
      log.error("Not sending status because token is empty for sha {}", gitStatusCheckParams.getSha());
      return false;
    }
  }

  private boolean sendBuildStatusToBitbucket(GitStatusCheckParams gitStatusCheckParams) {
    Map<String, Object> bodyObjectMap = new HashMap<>();
    bodyObjectMap.put(DESC, gitStatusCheckParams.getDesc());
    bodyObjectMap.put(BITBUCKET_KEY, gitStatusCheckParams.getIdentifier());
    bodyObjectMap.put(STATE, gitStatusCheckParams.getState());
    bodyObjectMap.put(URL, gitStatusCheckParams.getDetailsUrl());

    String token = gitTokenRetriever.retrieveAuthToken(
        gitStatusCheckParams.getGitSCMType(), gitStatusCheckParams.getConnectorDetails());

    BitbucketConnectorDTO gitConfigDTO =
        (BitbucketConnectorDTO) gitStatusCheckParams.getConnectorDetails().getConnectorConfig();

    if (isNotEmpty(token)) {
      RetryPolicy<Object> retryPolicy =
          getRetryPolicy(format("[Retrying failed call to send status for bitbucket check: [%s], attempt: {}",
                             gitStatusCheckParams.getSha()),
              format("Failed call to send status for bitbucket check: [%s] after retrying {} times",
                  gitStatusCheckParams.getSha()));

      return Failsafe.with(retryPolicy)
          .get(()
                   -> bitbucketService.sendStatus(
                       BitbucketConfig.builder().bitbucketUrl(getBitBucketApiURL(gitConfigDTO.getUrl())).build(),
                       gitStatusCheckParams.getUserName(), token, null, gitStatusCheckParams.getSha(),
                       gitStatusCheckParams.getOwner(), gitStatusCheckParams.getRepo(), bodyObjectMap));
    } else {
      log.error("Not sending status because token is empty sha {}", gitStatusCheckParams.getSha());
      return false;
    }
  }

  private boolean sendBuildStatusToGitLab(GitStatusCheckParams gitStatusCheckParams) {
    Map<String, Object> bodyObjectMap = new HashMap<>();
    bodyObjectMap.put(GitlabServiceImpl.DESC, gitStatusCheckParams.getDesc());
    bodyObjectMap.put(GitlabServiceImpl.CONTEXT, GITLAB_GENRE + ": " + gitStatusCheckParams.getIdentifier());
    bodyObjectMap.put(GitlabServiceImpl.STATE, gitStatusCheckParams.getState());
    bodyObjectMap.put(GitlabServiceImpl.TARGET_URL, gitStatusCheckParams.getDetailsUrl());

    String token = gitTokenRetriever.retrieveAuthToken(
        gitStatusCheckParams.getGitSCMType(), gitStatusCheckParams.getConnectorDetails());

    GitlabConnectorDTO gitConfigDTO =
        (GitlabConnectorDTO) gitStatusCheckParams.getConnectorDetails().getConnectorConfig();

    if (isNotEmpty(token)) {
      RetryPolicy<Object> retryPolicy =
          getRetryPolicy(format("[Retrying failed call to send status for gitlab check: [%s], attempt: {}",
                             gitStatusCheckParams.getSha()),
              format("Failed call to send status for gitlab check: [%s] after retrying {} times",
                  gitStatusCheckParams.getSha()));

      return Failsafe.with(retryPolicy)
          .get(()
                   -> gitlabService.sendStatus(
                       GitlabConfig.builder().gitlabUrl(getGitlabApiURL(gitConfigDTO.getUrl())).build(),
                       gitStatusCheckParams.getUserName(), token, null, gitStatusCheckParams.getSha(),
                       gitStatusCheckParams.getOwner(), gitStatusCheckParams.getRepo(), bodyObjectMap));
    } else {
      log.error("Not sending status because token is empty sha {}", gitStatusCheckParams.getSha());
      return false;
    }
  }

  private boolean sendBuildStatusToAzureRepo(GitStatusCheckParams gitStatusCheckParams) {
    Map<String, Object> bodyObjectMap = new HashMap<>();
    bodyObjectMap.put(GitlabServiceImpl.DESC, gitStatusCheckParams.getDesc());
    bodyObjectMap.put(GitlabServiceImpl.CONTEXT,
        AzureRepoContext.builder().genre(AZURE_REPO_GENRE).name(gitStatusCheckParams.getIdentifier()).build());
    bodyObjectMap.put(GitlabServiceImpl.STATE, gitStatusCheckParams.getState());
    bodyObjectMap.put(GitlabServiceImpl.TARGET_URL, gitStatusCheckParams.getDetailsUrl());

    String token = gitTokenRetriever.retrieveAuthToken(
        gitStatusCheckParams.getGitSCMType(), gitStatusCheckParams.getConnectorDetails());

    AzureRepoConnectorDTO gitConfigDTO =
        (AzureRepoConnectorDTO) gitStatusCheckParams.getConnectorDetails().getConnectorConfig();

    if (isNotEmpty(token)) {
      String completeUrl = gitConfigDTO.getUrl();

      if (gitConfigDTO.getConnectionType() == AzureRepoConnectionTypeDTO.PROJECT) {
        completeUrl = StringUtils.join(
            StringUtils.stripEnd(
                StringUtils.substringBeforeLast(completeUrl, gitStatusCheckParams.getOwner()), PATH_SEPARATOR),
            PATH_SEPARATOR, gitStatusCheckParams.getOwner(), PATH_SEPARATOR, gitStatusCheckParams.getRepo());
      }

      String orgAndProject;

      if (gitConfigDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
        orgAndProject = GitClientHelper.getAzureRepoOrgAndProjectHTTP(completeUrl);
      } else {
        orgAndProject = GitClientHelper.getAzureRepoOrgAndProjectSSH(completeUrl);
      }

      String project = GitClientHelper.getAzureRepoProject(orgAndProject);
      String repo = StringUtils.substringAfterLast(completeUrl, PATH_SEPARATOR);

      RetryPolicy<Object> retryPolicy =
          getRetryPolicy(format("[Retrying failed call to send status for azure repo check: [%s], attempt: {}",
                             gitStatusCheckParams.getSha()),
              format("Failed call to send status for azure repo check: [%s] after retrying {} times",
                  gitStatusCheckParams.getSha()));

      return Failsafe.with(retryPolicy)
          .get(()
                   -> azureRepoService.sendStatus(
                       AzureRepoConfig.builder().azureRepoUrl(getAzureRepoApiURL(gitConfigDTO.getUrl())).build(),
                       gitStatusCheckParams.getUserName(), token, gitStatusCheckParams.getSha(),
                       gitStatusCheckParams.getOwner(), project, repo, bodyObjectMap));
    } else {
      log.error("Not sending status because token is empty sha {}", gitStatusCheckParams.getSha());
      return false;
    }
  }

  private String getGitApiURL(String url) {
    if (GitClientHelper.isGithubSAAS(url)) {
      return GITHUB_API_URL;
    } else {
      String domain = GitClientHelper.getGitSCM(url);
      return "https://" + domain + "/api/v3/";
    }
  }

  private String getBitBucketApiURL(String url) {
    if (url.contains("bitbucket.org")) {
      return BITBUCKET_API_URL;
    } else {
      String domain = GitClientHelper.getGitSCM(url);
      domain = fetchCustomBitbucketDomain(url, domain);
      return "https://" + domain + "/";
    }
  }

  private static String fetchCustomBitbucketDomain(String url, String domain) {
    final String SCM_SPLITTER = "/scm";
    String[] splits = url.split(domain);
    if (splits.length <= 1) {
      // URL only contains the domain
      return domain;
    }

    String scmString = splits[1];
    if (!scmString.contains(SCM_SPLITTER)) {
      // Remaining URL does not contain the custom splitter string
      // Fallback to the original domain
      return domain;
    }

    String[] endpointSplits = scmString.split(SCM_SPLITTER);
    if (endpointSplits.length == 0) {
      // URL does not have anything after the splitter
      // as well as between domain and splitter
      return domain;
    }

    String customEndpoint = endpointSplits[0];
    return domain + customEndpoint;
  }

  private String getGitlabApiURL(String url) {
    if (url.contains("gitlab.com")) {
      return GITLAB_API_URL;
    } else {
      String domain = GitClientHelper.getGitSCM(url);
      return "https://" + domain + "/api/";
    }
  }

  private String getAzureRepoApiURL(String url) {
    if (url.contains("azure.com")) {
      return AZURE_REPO_API_URL;
    } else {
      String domain = GitClientHelper.getGitSCM(url);
      return "https://" + domain + PATH_SEPARATOR;
    }
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .abortOn(ConnectorNotFoundException.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
