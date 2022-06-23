/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;

import static software.wings.beans.TaskType.SCM_GIT_REF_TASK;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.ScmGitRefTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.TriggerException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.AzureRepoSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.WebhookConfigHelper;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.ConnectorUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@OwnedBy(CI)
public class SCMDataObtainer implements GitProviderBaseDataObtainer {
  private final TaskExecutionUtils taskExecutionUtils;
  private final ConnectorUtils connectorUtils;
  private final KryoSerializer kryoSerializer;
  public static final String GIT_URL_SUFFIX = ".git";
  public static final String PATH_SEPARATOR = "/";
  public static final String AZURE_REPO_BASE_URL = "azure.com";

  @Inject
  public SCMDataObtainer(
      TaskExecutionUtils taskExecutionUtils, ConnectorUtils connectorUtils, KryoSerializer kryoSerializer) {
    this.taskExecutionUtils = taskExecutionUtils;
    this.connectorUtils = connectorUtils;
    this.kryoSerializer = kryoSerializer;
  }

  @Override
  public void acquireProviderData(FilterRequestData filterRequestData, List<TriggerDetails> triggers) {
    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();
    ParseWebhookResponse parseWebhookResponse = webhookPayloadData.getParseWebhookResponse();
    if (parseWebhookResponse.hasPr()) {
      acquirePullRequestCommits(filterRequestData, triggers);
    }
  }

  private String getGitURL(GitConnectionType connectionType, String url, String projectName, String repoName) {
    String gitUrl = retrieveGenericGitConnectorURL(projectName, repoName, connectionType, url);

    if (!url.endsWith(GIT_URL_SUFFIX) && !url.contains(AZURE_REPO_BASE_URL)) {
      gitUrl += GIT_URL_SUFFIX;
    }
    return gitUrl;
  }

  private String retrieveGenericGitConnectorURL(
      String projectName, String repoName, GitConnectionType connectionType, String url) {
    String gitUrl;
    if (connectionType == GitConnectionType.REPO) {
      gitUrl = url;
    } else if (connectionType == GitConnectionType.ACCOUNT) {
      if (isEmpty(repoName)) {
        throw new IllegalArgumentException("Repo name is not set in trigger git connector spec");
      }

      if (isNotEmpty(projectName) && url.contains(AZURE_REPO_BASE_URL)) {
        gitUrl = GitClientHelper.getCompleteUrlForAccountLevelAzureConnector(url, projectName, repoName);
      } else {
        gitUrl = StringUtils.join(StringUtils.stripEnd(url, PATH_SEPARATOR), PATH_SEPARATOR,
            StringUtils.stripStart(repoName, PATH_SEPARATOR));
      }
    } else {
      throw new InvalidArgumentsException(
          format("Invalid connection type for git connector: %s", connectionType.toString()), WingsException.USER);
    }

    return gitUrl;
  }

  private void acquirePullRequestCommits(FilterRequestData filterRequestData, List<TriggerDetails> triggers) {
    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();
    ParseWebhookResponse parseWebhookResponse = webhookPayloadData.getParseWebhookResponse();
    PullRequestHook pullRequestHook = parseWebhookResponse.getPr();
    PullRequest pullRequest = pullRequestHook.getPr();
    List<Commit> commitsInPr = new ArrayList<>();
    for (TriggerDetails triggerDetails : triggers) {
      try {
        String connectorIdentifier =
            triggerDetails.getNgTriggerEntity().getMetadata().getWebhook().getGit().getConnectorIdentifier();

        ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(
            IdentifierRef.builder()
                .accountIdentifier(triggerDetails.getNgTriggerEntity().getAccountId())
                .orgIdentifier(triggerDetails.getNgTriggerEntity().getOrgIdentifier())
                .projectIdentifier(triggerDetails.getNgTriggerEntity().getProjectIdentifier())
                .build(),
            connectorIdentifier);

        commitsInPr.addAll(getCommitsInPr(connectorDetails, triggerDetails, pullRequest.getNumber()));
        break;

      } catch (Exception e) {
        log.error("Failed while fetching additional information from git provider for branch webhook event"
                + "Project : " + filterRequestData.getAccountId() + ", with Exception" + e.getMessage(),
            e);
      }
    }
    PullRequest updatedPullRequest = pullRequest.toBuilder().addAllCommits(commitsInPr).build();
    PullRequestHook updatedPullRequestHook = pullRequestHook.toBuilder().setPr(updatedPullRequest).build();
    ParseWebhookResponse updatedParseWebhookResponse =
        parseWebhookResponse.toBuilder().setPr(updatedPullRequestHook).build();
    WebhookPayloadData updatedWebhookPayloadData =
        webhookPayloadData.toBuilder().parseWebhookResponse(updatedParseWebhookResponse).build();
    filterRequestData.setWebhookPayloadData(updatedWebhookPayloadData);
  }

  private GitConnectionType retrieveGitConnectionType(ConnectorDetails gitConnector) {
    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getGitConnectionType();
    } else {
      throw new CIStageExecutionException("scmType " + gitConnector.getConnectorType() + "is not supported");
    }
  }

  List<Commit> getCommitsInPr(ConnectorDetails connectorDetails, TriggerDetails triggerDetails, long number) {
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
    try {
      WebhookTriggerConfigV2 webhookTriggerConfigV2 =
          (WebhookTriggerConfigV2) triggerDetails.getNgTriggerConfigV2().getSource().getSpec();
      GitAware gitAware = WebhookConfigHelper.retrieveGitAware(webhookTriggerConfigV2);
      String projectName = getProjectNameIfApplicable(webhookTriggerConfigV2);
      String repoName = gitAware.fetchRepoName();

      scmConnector.setUrl(
          getGitURL(retrieveGitConnectionType(connectorDetails), scmConnector.getUrl(), projectName, repoName));
    } catch (Exception ex) {
      log.error("Failed to update url");
    }
    if (ScmConnector.class.isAssignableFrom(connectorDetails.getConnectorConfig().getClass())) {
      ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                    .prNumber(number)
                                                    .gitRefType(GitRefType.PULL_REQUEST_COMMITS)
                                                    .encryptedDataDetails(connectorDetails.getEncryptedDataDetails())
                                                    .scmConnector(scmConnector)
                                                    .build();
      ResponseData responseData =
          taskExecutionUtils.executeSyncTask(DelegateTaskRequest.builder()
                                                 .accountId(triggerDetails.getNgTriggerEntity().getAccountId())
                                                 .executionTimeout(Duration.ofSeconds(30))
                                                 .taskType(SCM_GIT_REF_TASK.name())
                                                 .taskParameters(scmGitRefTaskParams)
                                                 .build());

      if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
        BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
        Object object = kryoSerializer.asInflatedObject(binaryResponseData.getData());
        if (ScmGitRefTaskResponseData.class.isAssignableFrom(object.getClass())) {
          ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) object;
          try {
            return ListCommitsInPRResponse.parseFrom(scmGitRefTaskResponseData.getListCommitsInPRResponse())
                .getCommitsList();
          } catch (InvalidProtocolBufferException e) {
            throw new TriggerException("Unexpected error occurred while doing scm operation", WingsException.SRE);
          }
        } else if (object instanceof ErrorResponseData) {
          ErrorResponseData errorResponseData = (ErrorResponseData) object;
          throw new TriggerException(
              String.format("Failed to fetch commit details. Reason: %s", errorResponseData.getErrorMessage()),
              WingsException.SRE);
        }
      }
      throw new TriggerException("Failed to fetch commit details", WingsException.SRE);
    }
    return new ArrayList<>();
  }

  private String getProjectNameIfApplicable(WebhookTriggerConfigV2 webhookTriggerConfigV2) {
    WebhookTriggerType webhookTriggerType = webhookTriggerConfigV2.getType();
    if (webhookTriggerType == WebhookTriggerType.AZURE) {
      return ((AzureRepoSpec) webhookTriggerConfigV2.getSpec()).getSpec().getProjectName();
    }
    return null;
  }
}
