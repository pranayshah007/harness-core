/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gitapi.client.impl;

import static io.harness.delegate.beans.connector.scm.github.GithubApiAccessType.GITHUB_APP;
import static io.harness.delegate.beans.connector.scm.github.GithubApiAccessType.TOKEN;
import static io.harness.exception.WingsException.SRE;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cistatus.service.GithubAppConfig;
import io.harness.cistatus.service.GithubService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.gitapi.GitApiFindPRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiMergePRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse.GitApiTaskResponseBuilder;
import io.harness.delegate.task.gitapi.client.GitApiClient;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;
import io.harness.exception.GitClientException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class GithubApiClient implements GitApiClient {
  private final GithubService githubService;
  private final SecretDecryptionService secretDecryptionService;

  private static final String GITHUB_API_URL = "https://api.github.com/";
  private static final String GIT_URL_REGEX = "(https|git)(:\\/\\/|@)([^\\/:]+)[\\/:]([^\\/:]+)";

  @Override
  public DelegateResponseData findPullRequest(GitApiTaskParams gitApiTaskParams) {
    GitApiTaskResponseBuilder responseBuilder = GitApiTaskResponse.builder();
    ConnectorDetails gitConnector = gitApiTaskParams.getConnectorDetails();
    try {
      if (gitConnector == null
          || !gitConnector.getConnectorConfig().getClass().isAssignableFrom(GithubConnectorDTO.class)) {
        throw new InvalidRequestException(
            format("Invalid Connector %s, Need GithubConfig: ", gitConnector.getIdentifier()));
      }
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      String token = retrieveAuthToken(gitConnector);
      String gitApiURL = getGitApiURL(gitConfigDTO.getUrl());

      String prJson = githubService.findPR(
          gitApiURL, token, gitApiTaskParams.getOwner(), gitApiTaskParams.getRepo(), gitApiTaskParams.getPrNumber());
      if (isNotBlank(prJson)) {
        responseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .gitApiResult(GitApiFindPRTaskResponse.builder().prJson(prJson).build());
      } else {
        responseBuilder.commandExecutionStatus(FAILURE).errorMessage("Received blank pr details");
      }
    } catch (Exception e) {
      log.error(new StringBuilder("failed while fetching PR Details using connector: ")
                    .append(gitConnector.getIdentifier())
                    .toString(),
          e);
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(e.getMessage());
    }

    return responseBuilder.build();
  }

  @Override
  public DelegateResponseData mergePR(GitApiTaskParams gitApiTaskParams) {
    GitApiTaskResponseBuilder responseBuilder = GitApiTaskResponse.builder();
    ConnectorDetails gitConnector = gitApiTaskParams.getConnectorDetails();
    try {
      if (gitConnector == null
          || !gitConnector.getConnectorConfig().getClass().isAssignableFrom(GithubConnectorDTO.class)) {
        throw new InvalidRequestException(
            format("Invalid Connector %s, Need GithubConfig: ", gitConnector.getIdentifier()));
      }
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      String token = retrieveAuthToken(gitConnector);
      String gitApiURL = getGitApiURL(gitConfigDTO.getUrl());
      String repo = gitApiTaskParams.getRepo();
      String prNumber = gitApiTaskParams.getPrNumber();
      JSONObject mergePRResponse = githubService.mergePR(gitApiURL, token, gitApiTaskParams.getOwner(), repo, prNumber);
      if (mergePRResponse != null && (boolean) mergePRResponse.get("merged") == true) {
        responseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .gitApiResult(GitApiMergePRTaskResponse.builder().sha(mergePRResponse.get("sha").toString()).build());
      } else {
        responseBuilder.commandExecutionStatus(FAILURE).errorMessage(
            format("Merging PR encountered a problem. URL:%s Repo:%s PrNumber:%s Message:%s Code:%s", gitApiURL, repo,
                prNumber, mergePRResponse.get("error"), mergePRResponse.get("code")));
      }
    } catch (Exception e) {
      log.error(new StringBuilder("Failed while merging PR using connector: ")
                    .append(gitConnector.getIdentifier())
                    .toString(),
          e);
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(e.getMessage());
    }

    return responseBuilder.build();
  }

  @Override
  public DelegateResponseData deleteRef(GitApiTaskParams gitApiTaskParams) {
    GitApiTaskResponseBuilder responseBuilder = GitApiTaskResponse.builder();
    ConnectorDetails gitConnector = gitApiTaskParams.getConnectorDetails();
    try {
      if (gitConnector == null
          || !gitConnector.getConnectorConfig().getClass().isAssignableFrom(GithubConnectorDTO.class)) {
        throw new InvalidRequestException(
            format("Invalid Connector %s, Need GithubConfig: ", gitConnector.getIdentifier()));
      }
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      String token = retrieveAuthToken(gitConnector);
      String gitApiURL = getGitApiURL(gitConfigDTO.getUrl());
      String repo = gitApiTaskParams.getRepo();
      String ref = gitApiTaskParams.getRef();

      boolean success = githubService.deleteRef(gitApiURL, token, gitApiTaskParams.getOwner(), repo, ref);
      if (!success) {
        String err = format("Failed to delete reference %s for github connector %s", ref, gitConnector.getIdentifier());
        log.info(err);
        responseBuilder.commandExecutionStatus(FAILURE).errorMessage("Failed to delete");
      } else {
        responseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS);
      }
    } catch (Exception e) {
      log.error(
          new StringBuilder(format("Failed to delete reference %s for github connector ", gitApiTaskParams.getRef()))
              .append(gitConnector.getIdentifier())
              .toString(),
          e);
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(e.getMessage());
    }
    return responseBuilder.build();
  }

  public List<GitPollingWebhookData> getWebhookRecentDeliveryEvents(GitHubPollingDelegateRequest attributesRequest) {
    ConnectorDetails gitConnector = attributesRequest.getConnectorDetails();
    if (gitConnector == null
        || !gitConnector.getConnectorConfig().getClass().isAssignableFrom(GithubConnectorDTO.class)) {
      throw new InvalidRequestException(format("Invalid Connector %s, Need GithubConfig: ", gitConnector));
    }
    GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
    String gitApiURL = getGitApiURL(gitConfigDTO.getUrl());
    String repoOwner = gitConfigDTO.getGitRepositoryDetails().getOrg();
    String repoName = gitConfigDTO.getGitRepositoryDetails().getName() != null
        ? gitConfigDTO.getGitRepositoryDetails().getName()
        : attributesRequest.getRepository();
    String webhookId = attributesRequest.getWebhookId();
    String token = retrieveAuthToken(gitConnector);

    return githubService.getWebhookRecentDeliveryEvents(gitApiURL, token, repoOwner, repoName, webhookId);
  }

  private String getGitApiURL(String url) {
    Pattern GIT_URL = Pattern.compile(GIT_URL_REGEX);
    Matcher m = GIT_URL.matcher(url);

    String domain = extractDomain(url, m);
    if (domain.equalsIgnoreCase("github.com")) {
      return GITHUB_API_URL;
    } else {
      return "https://" + domain + "/api/v3/";
    }
  }

  private String extractDomain(String url, Matcher m) {
    String group = EMPTY;
    try {
      if (m.find()) {
        group = m.toMatchResult().group(3);
      } else {
        throw new GitClientException(format("Invalid git repo url  %s", url), SRE);
      }

    } catch (Exception e) {
      throw new GitClientException(format("Failed to parse repo from git url  %s", url), SRE);
    }
    return group;
  }

  public String retrieveAuthToken(ConnectorDetails gitConnector) {
    GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
    if (gitConfigDTO.getApiAccess() == null || gitConfigDTO.getApiAccess().getType() == null) {
      throw new InvalidRequestException(
          format("Failed to retrieve token info for github connector: %s", gitConnector.getIdentifier()));
    }

    GithubApiAccessSpecDTO spec = gitConfigDTO.getApiAccess().getSpec();
    GithubApiAccessType apiAccessType = gitConfigDTO.getApiAccess().getType();
    secretDecryptionService.decrypt(spec, gitConnector.getEncryptedDataDetails());
    String token = null;
    if (apiAccessType == TOKEN) {
      token = new String(((GithubTokenSpecDTO) spec).getTokenRef().getDecryptedValue());
    } else if (gitConfigDTO.getApiAccess().getType() == GITHUB_APP) {
      token = fetchTokenUsingGithubAppSpec(gitConfigDTO, (GithubAppSpecDTO) spec);
    } else {
      throw new InvalidRequestException(
          format("Failed while retrieving token. Unsupported access type %s for Github accessType. Use Token Access",
              gitConfigDTO.getApiAccess().getType()));
    }

    return token;
  }

  private String fetchTokenUsingGithubAppSpec(GithubConnectorDTO gitConfigDTO, GithubAppSpecDTO spec) {
    return githubService.getToken(GithubAppConfig.builder()
                                      .installationId(spec.getInstallationId())
                                      .appId(spec.getApplicationId())
                                      .privateKey(new String(spec.getPrivateKeyRef().getDecryptedValue()))
                                      .githubUrl(getGitApiURL(gitConfigDTO.getUrl()))
                                      .build());
  }
}
