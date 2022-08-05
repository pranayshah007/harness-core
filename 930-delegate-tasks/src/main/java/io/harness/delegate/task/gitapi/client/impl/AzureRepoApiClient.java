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
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
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

        AzureRepoConfig azureRepoConfig =
            AzureRepoConfig.builder().azureRepoUrl(getAzureRepoApiURL(azureRepoConnectorDTO.getUrl())).build();

        JSONObject mergePRResponse;
        mergePRResponse =
            azureRepoService.mergePR(azureRepoConfig, gitApiTaskParams.getUserName(), token, gitApiTaskParams.getSha(),
                gitApiTaskParams.getOwner(), project, gitApiTaskParams.getRepo(), gitApiTaskParams.getPrNumber());

        if (mergePRResponse != null) {
          responseBuilder.commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .gitApiResult(GitApiMergePRTaskResponse.builder().sha(mergePRResponse.get("sha").toString()).build());
        } else {
          responseBuilder.commandExecutionStatus(FAILURE).errorMessage("Merging PR encountered a problem");
        }
      }
    } catch (Exception e) {
      log.error(
          new StringBuilder("Failed while merging PR using connector: ").append(gitApiTaskParams.getRepo()).toString(),
          e);
      responseBuilder.commandExecutionStatus(FAILURE).errorMessage(e.getMessage());
    }

    return responseBuilder.build();
  }

  private String retrieveAzureRepoAuthToken(ConnectorDetails gitConnector) {
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
    if (azureRepoConnectorDTO.getApiAccess() == null) {
      throw new InvalidRequestException(
          format("Failed to retrieve token info for Azure repo connector: %s", gitConnector.getIdentifier()));
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
