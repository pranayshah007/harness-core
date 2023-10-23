/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.gitapi.client.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.cistatus.service.azurerepo.AzureRepoConfig;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.gitapi.GitApiMergePRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse.GitApiTaskResponseBuilder;
import io.harness.delegate.task.gitapi.client.GitApiClient;
import io.harness.delegate.task.gitpolling.github.GitHubPollingDelegateRequest;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class AzureRepoApiClient implements GitApiClient {
  private AzureRepoService azureRepoService;
  private final SecretDecryptionService secretDecryptionService;

  private static final String PATH_SEPARATOR = "/";
  private static final String AZURE_REPO_API_URL = "https://dev.azure.com/";

  @Override
  public DelegateResponseData findPullRequest(GitApiTaskParams gitApiTaskParams) {
    throw new InvalidRequestException("Not implemented");
  }

  @Override
  public DelegateResponseData mergePR(GitApiTaskParams gitApiTaskParams) {
    GitApiTaskResponseBuilder responseBuilder = GitApiTaskResponse.builder();

    AzureRepoConnectorDTO azureRepoConnectorDTO =
        (AzureRepoConnectorDTO) gitApiTaskParams.getConnectorDetails().getConnectorConfig();

    String token = retrieveAzureRepoAuthToken(gitApiTaskParams.getConnectorDetails());
    try {
      if (isNotEmpty(token)) {
        String completeUrl = azureRepoConnectorDTO.getUrl();

        if (azureRepoConnectorDTO.getConnectionType() == AzureRepoConnectionTypeDTO.PROJECT) {
          completeUrl = StringUtils.join(
              StringUtils.stripEnd(
                  StringUtils.substringBeforeLast(completeUrl, gitApiTaskParams.getOwner()), PATH_SEPARATOR),
              PATH_SEPARATOR, gitApiTaskParams.getOwner(), PATH_SEPARATOR, gitApiTaskParams.getRepo());
        }

        String orgAndProject;

        if (azureRepoConnectorDTO.getAuthentication().getAuthType() == GitAuthType.HTTP) {
          orgAndProject = GitClientHelper.getAzureRepoOrgAndProjectHTTP(completeUrl);
        } else {
          orgAndProject = GitClientHelper.getAzureRepoOrgAndProjectSSH(completeUrl);
        }

        String project = GitClientHelper.getAzureRepoProject(orgAndProject);
        String repo = gitApiTaskParams.getRepo();
        String prNumber = gitApiTaskParams.getPrNumber();
        String sha = gitApiTaskParams.getSha();
        AzureRepoConfig azureRepoConfig =
            AzureRepoConfig.builder().azureRepoUrl(getAzureRepoApiURL(azureRepoConnectorDTO.getUrl())).build();

        JSONObject mergePRResponse;
        mergePRResponse = azureRepoService.mergePR(azureRepoConfig, token, sha, gitApiTaskParams.getOwner(), project,
            repo, prNumber, gitApiTaskParams.isDeleteSourceBranch(), gitApiTaskParams.getApiParamOptions());

        prepareResponseBuilder(responseBuilder, repo, prNumber, sha, mergePRResponse);
      }
    } catch (Exception e) {
      log.error(
          new StringBuilder("Failed while merging PR using ConnectorDisconnectHandler: ").append(gitApiTaskParams.getRepo()).toString(),
          e);
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(e.getMessage());
    }

    return responseBuilder.build();
  }

  @VisibleForTesting
  void prepareResponseBuilder(
      GitApiTaskResponseBuilder responseBuilder, String repo, String prNumber, String sha, JSONObject mergePRResponse) {
    if (mergePRResponse != null) {
      Object merged = getValue(mergePRResponse, "merged");
      if (merged instanceof Boolean && (boolean) merged) {
        Object responseSha = getValue(mergePRResponse, "sha");
        responseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .gitApiResult(
                GitApiMergePRTaskResponse.builder().sha(responseSha == null ? null : responseSha.toString()).build());
      } else {
        responseBuilder.commandExecutionStatus(FAILURE).errorMessage(
            format("Merging PR encountered a problem. sha:%s Repo:%s PrNumber:%s Message:%s Code:%s", sha, repo,
                prNumber, getValue(mergePRResponse, "error"), getValue(mergePRResponse, "code")));
      }
    } else {
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(
          format("Merging PR encountered a problem. sha:%s Repo:%s PrNumber:%s", sha, repo, prNumber));
    }
  }

  @VisibleForTesting
  Object getValue(JSONObject jsonObject, String key) {
    if (jsonObject == null) {
      return null;
    }
    try {
      return jsonObject.get(key);
    } catch (Exception ex) {
      log.error("Failed to get key: {} in JsonObject: {}", key, jsonObject);
      return null;
    }
  }

  @Override
  public List<GitPollingWebhookData> getWebhookRecentDeliveryEvents(GitHubPollingDelegateRequest attributesRequest) {
    throw new InvalidRequestException("Not implemented");
  }

  @Override
  public DelegateResponseData deleteRef(GitApiTaskParams gitApiTaskParams) {
    throw new InvalidRequestException("Not implemented");
  }

  private String retrieveAzureRepoAuthToken(ConnectorDetails gitConnector) {
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
    if (azureRepoConnectorDTO.getApiAccess() == null) {
      throw new InvalidRequestException(
          format("Failed to retrieve token info for Azure repo ConnectorDisconnectHandler: %s", gitConnector.getIdentifier()));
    }
    if (azureRepoConnectorDTO.getApiAccess().getType() == AzureRepoApiAccessType.TOKEN) {
      AzureRepoTokenSpecDTO azureRepoTokenSpecDTO =
          (AzureRepoTokenSpecDTO) azureRepoConnectorDTO.getApiAccess().getSpec();
      DecryptableEntity decryptableEntity =
          secretDecryptionService.decrypt(azureRepoTokenSpecDTO, gitConnector.getEncryptedDataDetails());
      azureRepoConnectorDTO.getApiAccess().setSpec((AzureRepoApiAccessSpecDTO) decryptableEntity);

      return new String(((AzureRepoTokenSpecDTO) decryptableEntity).getTokenRef().getDecryptedValue());
    } else {
      throw new InvalidRequestException(
          format("Unsupported access type %s for Azure repo status", azureRepoConnectorDTO.getApiAccess().getType()));
    }
  }

  private String getAzureRepoApiURL(String url) {
    if (url.contains("azure.com")) {
      return AZURE_REPO_API_URL;
    }
    String domain = GitClientHelper.getGitSCM(url);
    return "https://" + domain + PATH_SEPARATOR;
  }
}
