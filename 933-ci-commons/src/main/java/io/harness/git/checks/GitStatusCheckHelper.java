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
import io.harness.code.CodeResourceClient;
import io.harness.code.HarnessCodePayload;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.git.GitTokenRetriever;
import io.harness.impl.scm.ScmGitProviderMapper;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
  @Inject(optional = true) private CodeResourceClient codeResourceClient;
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
      } else if (gitStatusCheckParams.getGitSCMType() == GitSCMType.HARNESS) {
        statusSent = sendBuildStatusToHarnessCode(gitStatusCheckParams);
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

    String username;
    if (gitConfigDTO != null && gitConfigDTO.getApiAccess() != null) {
      BitbucketUsernameTokenApiAccessDTO bitbucketUsernameTokenApiAccessDTO =
          (BitbucketUsernameTokenApiAccessDTO) gitConfigDTO.getApiAccess().getSpec();
      if (bitbucketUsernameTokenApiAccessDTO.getUsernameRef() != null) {
        username = gitTokenRetriever.retrieveBitbucketUsernameFromAPIAccess(
            bitbucketUsernameTokenApiAccessDTO, gitStatusCheckParams.getConnectorDetails().getEncryptedDataDetails());
      } else {
        username = gitStatusCheckParams.getUserName();
      }
    } else {
      username = gitStatusCheckParams.getUserName();
    }

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
                       username, token, null, gitStatusCheckParams.getSha(), gitStatusCheckParams.getOwner(),
                       gitStatusCheckParams.getRepo(), bodyObjectMap));
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
                   -> gitlabService.sendStatus(GitlabConfig.builder()
                                                   .gitlabUrl(getGitlabApiURL(gitConfigDTO.getUrl(),
                                                       ScmGitProviderMapper.getGitlabApiUrl(gitConfigDTO)))
                                                   .build(),
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

      String project = URLDecoder.decode(GitClientHelper.getAzureRepoProject(orgAndProject), StandardCharsets.UTF_8);
      String repo =
          URLDecoder.decode(StringUtils.substringAfterLast(completeUrl, PATH_SEPARATOR), StandardCharsets.UTF_8);

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
                       gitStatusCheckParams.getPrNumber(), gitStatusCheckParams.getOwner(), project, repo,
                       bodyObjectMap));
    } else {
      log.error("Not sending status because token is empty sha {}", gitStatusCheckParams.getSha());
      return false;
    }
  }

  private boolean sendBuildStatusToHarnessCode(GitStatusCheckParams gitStatusCheckParams) {
    String checkUid = gitStatusCheckParams.getIdentifier().replaceAll("/", "_").replaceAll("\\s", "");
    HarnessCodePayload harnessCodePayload =
        HarnessCodePayload.builder()
            .status(HarnessCodePayload.CheckStatus.fromString(gitStatusCheckParams.getState()))
            .link(gitStatusCheckParams.getDetailsUrl())
            .summary(gitStatusCheckParams.getDesc())
            .payload(HarnessCodePayload.Payload.builder().kind(HarnessCodePayload.CheckPayloadKind.raw).build())
            .check_uid(checkUid)
            .build();
    String[] repoSplit = gitStatusCheckParams.getRepo().split("/");
    int len = repoSplit.length;
    if (len < 3 || len > 5) {
      throw new InvalidRequestException(String.format("incorrect repo provided: %s, owner: %s, checkUid: %s",
          gitStatusCheckParams.getRepo(), gitStatusCheckParams.getOwner(), checkUid));
    }

    // CI splits URL to owner and repo. Owner is first part before / after URL and rest all is repo.
    // URL: https://git.qa.harness.io/acc/org/proj/repo.git Owner: acc Repo: org/pro/repo
    // URL: https://qa.harness.io/code/git/acc/org/proj/repo.git Owner: code Repo: git/org/proj/repo

    String orgId = repoSplit[len - 3];
    String projectId = repoSplit[len - 2];
    String repoId = repoSplit[len - 1];
    String accountId = gitStatusCheckParams.getOwner();
    if (len > 3) {
      accountId = repoSplit[len - 4];
    }
    log.info("Sending status {} for sha {} and repo {} and owner {} and checkUid {}", harnessCodePayload.getStatus(),
        gitStatusCheckParams.getSha(), gitStatusCheckParams.getRepo(), gitStatusCheckParams.getOwner(), checkUid);
    return NGRestUtils.getGeneralResponse(codeResourceClient.sendStatus(
               accountId, orgId, projectId, repoId, gitStatusCheckParams.getSha(), harnessCodePayload))
        != null;
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

  private String getGitlabApiURL(String url, String apiUrl) {
    if (url.contains("gitlab.com")) {
      return GITLAB_API_URL;
    } else if (!StringUtils.isBlank(apiUrl)) {
      return StringUtils.stripEnd(apiUrl, "/") + "/api/";
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
