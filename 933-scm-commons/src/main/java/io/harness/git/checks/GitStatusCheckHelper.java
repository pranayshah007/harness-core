package io.harness.git.checks;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType.TOKEN;

import static java.lang.String.format;

import io.harness.beans.DecryptableEntity;
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
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.secrets.SecretDecryptor;

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
  @Inject private SecretDecryptor secretDecryptor;

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

  private static final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 3;

  // Sends the status check to the scm provider
  // Non-null accountId is required if it runs on manager
  public boolean sendStatus(GitStatusCheckParams gitStatusCheckParams, String accountId) {
    String sha = gitStatusCheckParams.getSha();
    try {
      boolean statusSent = false;
      if (gitStatusCheckParams.getGitSCMType() == GitSCMType.GITHUB) {
        statusSent = sendBuildStatusToGitHub(gitStatusCheckParams, accountId);
      } else if (gitStatusCheckParams.getGitSCMType() == GitSCMType.BITBUCKET) {
        statusSent = sendBuildStatusToBitbucket(gitStatusCheckParams, accountId);
      } else if (gitStatusCheckParams.getGitSCMType() == GitSCMType.GITLAB) {
        statusSent = sendBuildStatusToGitLab(gitStatusCheckParams, accountId);
      } else if (gitStatusCheckParams.getGitSCMType() == GitSCMType.AZURE_REPO) {
        statusSent = sendBuildStatusToAzureRepo(gitStatusCheckParams, accountId);
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

  private GithubAppSpecDTO retrieveGithubAppSpecDTO(
      GithubConnectorDTO gitConfigDTO, ConnectorDetails connectorDetails, String accountId) {
    GithubApiAccessDTO githubApiAccessDTO = gitConfigDTO.getApiAccess();
    if (githubApiAccessDTO.getType() == GithubApiAccessType.GITHUB_APP) {
      GithubAppSpecDTO githubAppSpecDTO = (GithubAppSpecDTO) githubApiAccessDTO.getSpec();
      DecryptableEntity decryptableEntity =
          secretDecryptor.decrypt(githubAppSpecDTO, connectorDetails.getEncryptedDataDetails(), accountId);
      githubApiAccessDTO.setSpec((GithubAppSpecDTO) decryptableEntity);
      return (GithubAppSpecDTO) githubApiAccessDTO.getSpec();
    } else {
      throw new CIStageExecutionException(
          format("Unsupported access type %s for github status", githubApiAccessDTO.getType()));
    }
  }

  private boolean sendBuildStatusToGitHub(GitStatusCheckParams gitStatusCheckParams, String accountId) {
    GithubConnectorDTO gitConfigDTO =
        (GithubConnectorDTO) gitStatusCheckParams.getConnectorDetails().getConnectorConfig();

    GithubApiAccessDTO githubApiAccessDTO = gitConfigDTO.getApiAccess();

    if (githubApiAccessDTO == null) {
      log.warn("Not sending status because api access is not enabled for sha {}", gitStatusCheckParams.getSha());
      return false;
    }

    String token = null;
    if (githubApiAccessDTO.getType() == GithubApiAccessType.GITHUB_APP) {
      GithubAppSpecDTO githubAppSpecDTO =
          retrieveGithubAppSpecDTO(gitConfigDTO, gitStatusCheckParams.getConnectorDetails(), accountId);

      GithubAppConfig githubAppConfig =
          GithubAppConfig.builder()
              .installationId(githubAppSpecDTO.getInstallationId())
              .appId(githubAppSpecDTO.getApplicationId())
              .privateKey(new String(githubAppSpecDTO.getPrivateKeyRef().getDecryptedValue()))
              .githubUrl(getGitApiURL(gitConfigDTO.getUrl()))
              .build();
      token = githubService.getToken(githubAppConfig);
      if (EmptyPredicate.isEmpty(token)) {
        log.error("Not sending status because token is empty for appId {}, installationId {}, sha {}",
            githubAppSpecDTO.getApplicationId(), githubAppSpecDTO.getInstallationId(), gitStatusCheckParams.getSha());
        return false;
      }
    } else if (githubApiAccessDTO.getType() == GithubApiAccessType.TOKEN) {
      GithubTokenSpecDTO githubTokenSpecDTO = (GithubTokenSpecDTO) githubApiAccessDTO.getSpec();
      DecryptableEntity decryptableEntity = secretDecryptor.decrypt(
          githubTokenSpecDTO, gitStatusCheckParams.getConnectorDetails().getEncryptedDataDetails(), accountId);
      githubApiAccessDTO.setSpec((GithubApiAccessSpecDTO) decryptableEntity);

      token = new String(((GithubTokenSpecDTO) githubApiAccessDTO.getSpec()).getTokenRef().getDecryptedValue());

      if (EmptyPredicate.isEmpty(token)) {
        log.error("Not sending status because token is empty for sha {}", gitStatusCheckParams.getSha());
        return false;
      }
    } else {
      throw new CIStageExecutionException(
          format("Unsupported access type %s for github status", githubApiAccessDTO.getType()));
    }

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

      String finalToken = token;
      return Failsafe.with(retryPolicy)
          .get(()
                   -> githubService.sendStatus(githubAppConfig, finalToken, gitStatusCheckParams.getSha(),
                       gitStatusCheckParams.getOwner(), gitStatusCheckParams.getRepo(), bodyObjectMap));
    } else {
      log.error("Not sending status because token is empty for sha {}", gitStatusCheckParams.getSha());
      return false;
    }
  }

  private boolean sendBuildStatusToBitbucket(GitStatusCheckParams gitStatusCheckParams, String accountId) {
    Map<String, Object> bodyObjectMap = new HashMap<>();
    bodyObjectMap.put(DESC, gitStatusCheckParams.getDesc());
    bodyObjectMap.put(BITBUCKET_KEY, gitStatusCheckParams.getIdentifier());
    bodyObjectMap.put(STATE, gitStatusCheckParams.getState());
    bodyObjectMap.put(URL, gitStatusCheckParams.getDetailsUrl());

    String token =
        retrieveAuthToken(gitStatusCheckParams.getGitSCMType(), gitStatusCheckParams.getConnectorDetails(), accountId);

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

  private boolean sendBuildStatusToGitLab(GitStatusCheckParams gitStatusCheckParams, String accountId) {
    Map<String, Object> bodyObjectMap = new HashMap<>();
    bodyObjectMap.put(GitlabServiceImpl.DESC, gitStatusCheckParams.getDesc());
    bodyObjectMap.put(GitlabServiceImpl.CONTEXT, gitStatusCheckParams.getIdentifier());
    bodyObjectMap.put(GitlabServiceImpl.STATE, gitStatusCheckParams.getState());
    bodyObjectMap.put(GitlabServiceImpl.TARGET_URL, gitStatusCheckParams.getDetailsUrl());

    String token =
        retrieveAuthToken(gitStatusCheckParams.getGitSCMType(), gitStatusCheckParams.getConnectorDetails(), accountId);

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

  private boolean sendBuildStatusToAzureRepo(GitStatusCheckParams gitStatusCheckParams, String accountId) {
    Map<String, Object> bodyObjectMap = new HashMap<>();
    bodyObjectMap.put(GitlabServiceImpl.DESC, gitStatusCheckParams.getDesc());
    bodyObjectMap.put(GitlabServiceImpl.CONTEXT,
        AzureRepoContext.builder().genre(AZURE_REPO_GENRE).name(gitStatusCheckParams.getIdentifier()).build());
    bodyObjectMap.put(GitlabServiceImpl.STATE, gitStatusCheckParams.getState());
    bodyObjectMap.put(GitlabServiceImpl.TARGET_URL, gitStatusCheckParams.getDetailsUrl());

    String token =
        retrieveAuthToken(gitStatusCheckParams.getGitSCMType(), gitStatusCheckParams.getConnectorDetails(), accountId);

    AzureRepoConnectorDTO gitConfigDTO =
        (AzureRepoConnectorDTO) gitStatusCheckParams.getConnectorDetails().getConnectorConfig();

    if (isNotEmpty(token)) {
      String completeUrl = gitConfigDTO.getUrl();

      if (gitConfigDTO.getConnectionType() == GitConnectionType.ACCOUNT) {
        completeUrl = StringUtils.join(
            StringUtils.stripEnd(completeUrl, PATH_SEPARATOR), PATH_SEPARATOR, gitStatusCheckParams.getRepo());
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

  private String retrieveAuthToken(GitSCMType gitSCMType, ConnectorDetails gitConnector, String accountId) {
    switch (gitSCMType) {
      case GITHUB:
        return ""; // It does not require token because auth occurs via github app
      case GITLAB:
        GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
        if (gitConfigDTO.getApiAccess() == null) {
          throw new CIStageExecutionException(
              format("Failed to retrieve token info for gitlab connector: %s", gitConnector.getIdentifier()));
        }
        if (gitConfigDTO.getApiAccess().getType() == TOKEN) {
          GitlabApiAccessDTO gitlabApiAccessDTO = gitConfigDTO.getApiAccess();
          DecryptableEntity decryptableEntity =
              secretDecryptor.decrypt(gitlabApiAccessDTO.getSpec(), gitConnector.getEncryptedDataDetails(), accountId);
          gitlabApiAccessDTO.setSpec((GitlabApiAccessSpecDTO) decryptableEntity);
          return new String(((GitlabTokenSpecDTO) gitlabApiAccessDTO.getSpec()).getTokenRef().getDecryptedValue());
        } else {
          throw new CIStageExecutionException(
              format("Unsupported access type %s for gitlab status", gitConfigDTO.getApiAccess().getType()));
        }
      case BITBUCKET:
        BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
        if (bitbucketConnectorDTO.getApiAccess() == null) {
          throw new CIStageExecutionException(
              format("Failed to retrieve token info for Bitbucket connector: %s", gitConnector.getIdentifier()));
        }
        if (bitbucketConnectorDTO.getApiAccess().getType() == BitbucketApiAccessType.USERNAME_AND_TOKEN) {
          BitbucketUsernameTokenApiAccessDTO bitbucketTokenSpecDTO =
              (BitbucketUsernameTokenApiAccessDTO) bitbucketConnectorDTO.getApiAccess().getSpec();
          DecryptableEntity decryptableEntity =
              secretDecryptor.decrypt(bitbucketTokenSpecDTO, gitConnector.getEncryptedDataDetails(), accountId);
          bitbucketConnectorDTO.getApiAccess().setSpec((BitbucketApiAccessSpecDTO) decryptableEntity);
          return new String(((BitbucketUsernameTokenApiAccessDTO) bitbucketConnectorDTO.getApiAccess().getSpec())
                                .getTokenRef()
                                .getDecryptedValue());
        } else {
          throw new CIStageExecutionException(
              format("Unsupported access type %s for gitlab status", bitbucketConnectorDTO.getApiAccess().getType()));
        }
      case AZURE_REPO:
        AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
        if (azureRepoConnectorDTO.getApiAccess() == null) {
          throw new CIStageExecutionException(
              format("Failed to retrieve token info for Azure repo connector: %s", gitConnector.getIdentifier()));
        }
        if (azureRepoConnectorDTO.getApiAccess().getType() == AzureRepoApiAccessType.TOKEN) {
          AzureRepoTokenSpecDTO azureRepoTokenSpecDTO =
              (AzureRepoTokenSpecDTO) azureRepoConnectorDTO.getApiAccess().getSpec();
          DecryptableEntity decryptableEntity =
              secretDecryptor.decrypt(azureRepoTokenSpecDTO, gitConnector.getEncryptedDataDetails(), accountId);
          azureRepoConnectorDTO.getApiAccess().setSpec((AzureRepoApiAccessSpecDTO) decryptableEntity);

          return new String(((AzureRepoTokenSpecDTO) azureRepoConnectorDTO.getApiAccess().getSpec())
                                .getTokenRef()
                                .getDecryptedValue());
        } else {
          throw new CIStageExecutionException(format(
              "Unsupported access type %s for Azure repo status", azureRepoConnectorDTO.getApiAccess().getType()));
        }
      default:
        throw new CIStageExecutionException(format("Unsupported scm type %s for git status", gitSCMType));
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
    if (url.contains("bitbucket.org/")) {
      return BITBUCKET_API_URL;
    } else {
      String domain = GitClientHelper.getGitSCM(url);
      return "https://" + domain + "/";
    }
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
