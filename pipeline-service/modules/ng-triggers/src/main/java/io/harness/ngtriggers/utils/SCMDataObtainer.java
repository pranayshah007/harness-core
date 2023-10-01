/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;
import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;
import static io.harness.delegate.beans.connector.scm.adapter.AzureRepoToGitMapper.mapToGitConnectionType;

import static software.wings.beans.TaskType.SCM_GIT_REF_TASK;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
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
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.TriggerException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.git.GitAware;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.WebhookConfigHelper;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.service.ScmServiceClient;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.ConnectorUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@Singleton
@OwnedBy(CI)
public class SCMDataObtainer implements GitProviderBaseDataObtainer {
  private final TaskExecutionUtils taskExecutionUtils;
  private final ConnectorUtils connectorUtils;
  private final KryoSerializer kryoSerializer;
  private final KryoSerializer referenceFalseKryoSerializer;
  public static final String GIT_URL_SUFFIX = ".git";
  public static final String PATH_SEPARATOR = "/";
  public static final String AZURE_REPO_BASE_URL = "azure.com";
  private static final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 3;
  @Inject private SCMGrpc.SCMBlockingStub scmBlockingStub;
  @Inject SecretDecryptor secretDecryptor;
  @Inject ScmServiceClient scmServiceClient;

  @Inject
  public SCMDataObtainer(TaskExecutionUtils taskExecutionUtils, ConnectorUtils connectorUtils,
      KryoSerializer kryoSerializer,
      @Named("referenceFalseKryoSerializer") KryoSerializer referenceFalseKryoSerializer) {
    this.taskExecutionUtils = taskExecutionUtils;
    this.connectorUtils = connectorUtils;
    this.kryoSerializer = kryoSerializer;
    this.referenceFalseKryoSerializer = referenceFalseKryoSerializer;
  }

  @Override
  public void acquireProviderData(FilterRequestData filterRequestData, List<TriggerDetails> triggers) {
    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();
    ParseWebhookResponse parseWebhookResponse = webhookPayloadData.getParseWebhookResponse();
    if (parseWebhookResponse.hasPr()) {
      acquirePullRequestCommits(filterRequestData, triggers);
    }
  }

  public String getGitURL(GitConnectionType connectionType, String url, String repoName) {
    String gitUrl = retrieveGenericGitConnectorURL(repoName, connectionType, url);

    if (!url.endsWith(GIT_URL_SUFFIX) && !url.contains(AZURE_REPO_BASE_URL)) {
      gitUrl += GIT_URL_SUFFIX;
    }
    return gitUrl;
  }

  public String retrieveGenericGitConnectorURL(String repoName, GitConnectionType connectionType, String url) {
    String gitUrl = "";
    if (connectionType == GitConnectionType.REPO) {
      gitUrl = url;
    } else if (connectionType == GitConnectionType.PROJECT) {
      if (isEmpty(repoName)) {
        throw new IllegalArgumentException("Repo name is not set in trigger git connector spec");
      }
      if (url.contains(AZURE_REPO_BASE_URL)) {
        gitUrl = GitClientHelper.getCompleteUrlForProjectLevelAzureConnector(url, repoName);
      }
    } else if (connectionType == GitConnectionType.ACCOUNT) {
      if (isEmpty(repoName)) {
        throw new IllegalArgumentException("Repo name is not set in trigger git connector spec");
      }
      gitUrl = StringUtils.join(
          StringUtils.stripEnd(url, PATH_SEPARATOR), PATH_SEPARATOR, StringUtils.stripStart(repoName, PATH_SEPARATOR));
    } else {
      throw new InvalidArgumentsException(
          format("Invalid connection type for git connector: %s", connectionType.toString()), WingsException.USER);
    }

    return gitUrl;
  }

  public void acquirePullRequestCommits(FilterRequestData filterRequestData, List<TriggerDetails> triggers) {
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
      return mapToGitConnectionType(gitConfigDTO.getConnectionType());
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
      String repoName = gitAware.fetchRepoName();

      scmConnector.setUrl(getGitURL(retrieveGitConnectionType(connectorDetails), scmConnector.getUrl(), repoName));
    } catch (Exception ex) {
      log.error("Failed to update url");
    }

    ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                  .prNumber(number)
                                                  .gitRefType(GitRefType.PULL_REQUEST_COMMITS)
                                                  .encryptedDataDetails(connectorDetails.getEncryptedDataDetails())
                                                  .scmConnector(scmConnector)
                                                  .build();
    boolean executeOnDelegate =
        connectorDetails.getExecuteOnDelegate() == null || connectorDetails.getExecuteOnDelegate();

    if (executeOnDelegate) {
      return fetchPrCommitsViaDelegate(connectorDetails, scmGitRefTaskParams, triggerDetails);
    } else {
      return fetchPrCommitsViaManager(connectorDetails, scmGitRefTaskParams, triggerDetails);
    }
  }

  private List<Commit> fetchPrCommitsViaManager(
      ConnectorDetails connectorDetails, ScmGitRefTaskParams scmGitRefTaskParams, TriggerDetails triggerDetails) {
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        format("[Retrying failed call to fetch codebase metadata: [%s], attempt: {}", connectorDetails.getIdentifier()),
        format(
            "Failed call to fetch codebase metadata: [%s] after retrying {} times", connectorDetails.getIdentifier()));

    decrypt(scmGitRefTaskParams.getScmConnector(), connectorDetails.getEncryptedDataDetails());
    ListCommitsInPRResponse listCommitsInPRResponse =
        Failsafe.with(retryPolicy)
            .get(()
                     -> scmServiceClient.listCommitsInPR(
                         scmGitRefTaskParams.getScmConnector(), scmGitRefTaskParams.getPrNumber(), scmBlockingStub));

    return listCommitsInPRResponse.getCommitsList();
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

  private void decrypt(ScmConnector connector, List<EncryptedDataDetail> encryptedDataDetails) {
    final DecryptableEntity decryptableEntity = secretDecryptor.decrypt(
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(connector), encryptedDataDetails);
    GitApiAccessDecryptionHelper.setAPIAccessDecryptableEntity(connector, decryptableEntity);
  }

  private List<Commit> fetchPrCommitsViaDelegate(
      ConnectorDetails connectorDetails, ScmGitRefTaskParams scmGitRefTaskParams, TriggerDetails triggerDetails) {
    if (ScmConnector.class.isAssignableFrom(connectorDetails.getConnectorConfig().getClass())) {
      ResponseData responseData =
          taskExecutionUtils.executeSyncTask(DelegateTaskRequest.builder()
                                                 .accountId(triggerDetails.getNgTriggerEntity().getAccountId())
                                                 .executionTimeout(Duration.ofSeconds(30))
                                                 .taskType(SCM_GIT_REF_TASK.name())
                                                 .taskParameters(scmGitRefTaskParams)
                                                 .build());

      if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
        BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
        Object object = binaryResponseData.isUsingKryoWithoutReference()
            ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
            : kryoSerializer.asInflatedObject(binaryResponseData.getData());
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
}
