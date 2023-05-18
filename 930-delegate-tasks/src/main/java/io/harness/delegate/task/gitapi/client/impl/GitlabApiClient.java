/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gitapi.client.impl;

import static io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType.TOKEN;
import static io.harness.exception.WingsException.SRE;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.gitapi.GitApiMergePRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse.GitApiTaskResponseBuilder;
import io.harness.delegate.task.gitapi.client.GitApiClient;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;
import io.harness.exception.GitClientException;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.impl.scm.ScmGitProviderMapper;
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
public class GitlabApiClient implements GitApiClient {
  private final GitlabService gitlabService;
  private final SecretDecryptionService secretDecryptionService;

  private static final String GITLAB_API_URL = "https://gitlab.com/api";
  private static final String GIT_URL_REGEX = "(https|git)(:\\/\\/|@)([^\\/:]+)[\\/:]([^\\/:]+)";
  private static final Pattern GIT_URL = Pattern.compile(GIT_URL_REGEX);

  @Override
  public DelegateResponseData findPullRequest(GitApiTaskParams gitApiTaskParams) {
    throw new InvalidRequestException("Not implemented");
  }

  @Override
  public DelegateResponseData mergePR(GitApiTaskParams gitApiTaskParams) {
    GitApiTaskResponseBuilder responseBuilder = GitApiTaskResponse.builder();
    ConnectorDetails gitConnector = gitApiTaskParams.getConnectorDetails();
    try {
      if (gitConnector == null
          || !gitConnector.getConnectorConfig().getClass().isAssignableFrom(GitlabConnectorDTO.class)) {
        throw new InvalidRequestException(
            format("Invalid Connector %s, Need GitlabConfig: ", gitConnector.getIdentifier()));
      }
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      String token = retrieveAuthToken(gitConnector);
      String gitApiURL =
          GitClientHelper.getGitlabApiURL(gitConfigDTO.getUrl(), ScmGitProviderMapper.getGitlabApiUrl(gitConfigDTO));
      String slug = gitApiTaskParams.getSlug();
      String prNumber = gitApiTaskParams.getPrNumber();
      boolean deleteSourceBranch = gitApiTaskParams.isDeleteSourceBranch();
      JSONObject mergePRResponse = gitlabService.mergePR(gitApiURL, slug, token, prNumber, deleteSourceBranch);
      if (mergePRResponse != null) {
        responseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .gitApiResult(GitApiMergePRTaskResponse.builder().sha(mergePRResponse.get("sha").toString()).build());
      } else {
        responseBuilder.commandExecutionStatus(FAILURE).errorMessage(
            format("Merging PR encountered a problem. URL:%s Slug:%s PrNumber:%s", gitApiURL, slug, prNumber));
      }
    } catch (Exception e) {
      log.error(new StringBuilder("failed while merging PR using connector: ")
                    .append(gitConnector.getIdentifier())
                    .toString(),
          e);
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(e.getMessage());
    }

    return responseBuilder.build();
  }

  @Override
  public List<GitPollingWebhookData> getWebhookRecentDeliveryEvents(GitHubPollingDelegateRequest attributesRequest) {
    throw new InvalidRequestException("Not implemented");
  }

  @Override
  public DelegateResponseData deleteRef(GitApiTaskParams gitApiTaskParams) {
    throw new InvalidRequestException("Not implemented");
  }

  private String getGitApiURL(String url) {
    Matcher m = GIT_URL.matcher(url);
    String domain = extractDomain(url, m);
    if (domain.equalsIgnoreCase("gitlab.com")) {
      return GITLAB_API_URL;
    }
    return "https://" + domain + "/api/";
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
    GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
    if (gitConfigDTO.getApiAccess() == null || gitConfigDTO.getApiAccess().getType() == null) {
      throw new InvalidRequestException(
          format("Failed to retrieve token info for gitlab connector: ", gitConnector.getIdentifier()));
    }
    GitlabApiAccessSpecDTO spec = gitConfigDTO.getApiAccess().getSpec();
    GitlabApiAccessType apiAccessType = gitConfigDTO.getApiAccess().getType();
    secretDecryptionService.decrypt(spec, gitConnector.getEncryptedDataDetails());
    String token = null;
    if (apiAccessType == TOKEN) {
      token = new String(((GitlabTokenSpecDTO) spec).getTokenRef().getDecryptedValue());
    } else {
      throw new InvalidRequestException(
          format("Failed while retrieving token. Unsupported access type %s for Gitlab accessType. Use Token Access",
              gitConfigDTO.getApiAccess().getType()));
    }

    return token;
  }
}
