/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.EXCEPTION_WHILE_PROCESSING;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.FAILED_TO_FETCH_PR_DETAILS;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.beans.IssueCommentWebhookEvent;
import io.harness.beans.Repository;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.gitapi.GitApiFindPRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiRequestType;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.TriggerException;
import io.harness.exception.WingsException;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;
import io.harness.product.ci.scm.proto.FindPRResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequest.Builder;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.Reference;
import io.harness.product.ci.scm.proto.SCMGrpc.SCMBlockingStub;
import io.harness.product.ci.scm.proto.User;
import io.harness.secrets.SecretDecryptor;
import io.harness.serializer.KryoSerializer;
import io.harness.service.ScmServiceClient;
import io.harness.service.WebhookParserSCMService;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.ConnectorUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class GithubIssueCommentTriggerFilter implements TriggerFilter {
  private TaskExecutionUtils taskExecutionUtils;
  private ConnectorUtils connectorUtils;
  private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  private WebhookEventPayloadParser webhookEventPayloadParser;
  private PayloadConditionsTriggerFilter payloadConditionsTriggerFilter;
  private WebhookParserSCMService webhookParserSCMService;
  private SecretDecryptor secretDecryptor;
  private SCMBlockingStub scmBlockingStub;
  private ScmServiceClient scmServiceClient;
  public static final String PATH_SEPARATOR = "/";
  private static final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 3;

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);
    Optional<PullRequest> optionalPullRequest = fetchPrDetailsFromGithub(filterRequestData);
    if (optionalPullRequest.isEmpty()) {
      return mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(FAILED_TO_FETCH_PR_DETAILS,
              filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null, "Failed to fetch PR Details",
              null))
          .build();
    }

    try {
      filterRequestData.setWebhookPayloadData(generateUpdateWebhookPayloadDataWithPrHook(
          filterRequestData, optionalPullRequest.get(), mappingResponseBuilder));
    } catch (Exception e) {
      String errorMsg = new StringBuilder(128)
                            .append("Failed  while deserializing PR details for IssueComment event. ")
                            .append("Account : ")
                            .append(filterRequestData.getAccountId())
                            .append(", with Exception")
                            .append(e.getMessage())
                            .toString();
      log.error(errorMsg);
      return mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(EXCEPTION_WHILE_PROCESSING,
              filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
              "Failed to fetch PR Details: " + e, null))
          .build();
    }

    return payloadConditionsTriggerFilter.applyFilter(filterRequestData);
  }

  private WebhookPayloadData generateUpdateWebhookPayloadDataWithPrHook(FilterRequestData filterRequestData,
      PullRequest pullRequest, WebhookEventMappingResponseBuilder mappingResponseBuilder) throws Exception {
    ParseWebhookResponse originalParseWebhookResponse =
        filterRequestData.getWebhookPayloadData().getParseWebhookResponse();

    PullRequestHook pullRequestHook = PullRequestHook.newBuilder()
                                          .setRepo(originalParseWebhookResponse.getComment().getRepo())
                                          .setSender(originalParseWebhookResponse.getComment().getSender())
                                          .setPr(pullRequest)
                                          .build();

    ParseWebhookResponse newParseWebhookResponse =
        ParseWebhookResponse.newBuilder(originalParseWebhookResponse).setPr(pullRequestHook).build();

    mappingResponseBuilder.parseWebhookResponse(newParseWebhookResponse);
    WebhookPayloadData originalWebhookPayloadData = filterRequestData.getWebhookPayloadData();

    return WebhookPayloadData.builder()
        .repository(originalWebhookPayloadData.getRepository())
        .originalEvent(originalWebhookPayloadData.getOriginalEvent())
        .webhookGitUser(originalWebhookPayloadData.getWebhookGitUser())
        .parseWebhookResponse(newParseWebhookResponse)
        .webhookEvent(webhookParserSCMService.convertPRWebhookEvent(pullRequestHook))
        .build();
  }

  private PullRequest generateProtoFromJson(String prJson) throws Exception {
    JsonNode productNode = JsonPipelineUtils.readTree(prJson);
    Builder builder = PullRequest.newBuilder();
    long prNum = productNode.get("number").longValue();
    builder.setNumber(prNum);
    builder.setTitle(productNode.get("title").textValue());
    builder.setSha(productNode.get("head").get("sha").textValue());
    builder.setRef(new StringBuilder(128).append("refs/pull/").append(prNum).append("/head").toString());

    String headRef = productNode.get("head").get("ref").textValue();
    builder.setSource(headRef);
    String baseRef = productNode.get("base").get("ref").textValue();
    builder.setTarget(baseRef);
    builder.setFork(productNode.get("head").get("repo").get("full_name").textValue());

    builder.setLink(productNode.get("html_url").textValue());
    builder.setClosed(!"open".equalsIgnoreCase(productNode.get("state").textValue()));
    builder.setMerged(productNode.get("merged_at") != null && isNotBlank(productNode.get("merged_at").textValue()));

    builder.setHead(Reference.newBuilder()
                        .setSha(productNode.get("head").get("sha").textValue())
                        .setName(headRef)
                        .setPath(expandRef(headRef))
                        .build());

    builder.setBase(Reference.newBuilder()
                        .setSha(productNode.get("base").get("sha").textValue())
                        .setName(baseRef)
                        .setPath(expandRef(baseRef))
                        .build());

    builder.setAuthor(User.newBuilder()
                          .setLogin(productNode.get("user").get("login").textValue())
                          .setAvatar(productNode.get("user").get("avatar_url").textValue())
                          .build());

    return builder.build();
  }

  private String expandRef(String name) {
    if (name.startsWith("refs/")) {
      return name;
    }

    return "refs/heads/" + name;
  }

  private Optional<PullRequest> fetchPrDetailsFromGithub(FilterRequestData filterRequestData) {
    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();
    for (TriggerDetails details : filterRequestData.getDetails()) {
      try {
        String connectorIdentifier =
            details.getNgTriggerEntity().getMetadata().getWebhook().getGit().getConnectorIdentifier();
        ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(
            IdentifierRef.builder()
                .accountIdentifier(details.getNgTriggerEntity().getAccountId())
                .orgIdentifier(details.getNgTriggerEntity().getOrgIdentifier())
                .projectIdentifier(details.getNgTriggerEntity().getProjectIdentifier())
                .build(),
            connectorIdentifier);
        boolean executeOnDelegate =
            connectorDetails.getExecuteOnDelegate() == null || connectorDetails.getExecuteOnDelegate();
        if (executeOnDelegate) {
          GitApiTaskResponse taskResponse = buildAndFireDelegateTask(webhookPayloadData, details, connectorDetails);
          if (taskResponse.getCommandExecutionStatus() == SUCCESS) {
            GitApiFindPRTaskResponse gitApiResult = (GitApiFindPRTaskResponse) taskResponse.getGitApiResult();
            return Optional.of(generateProtoFromJson(gitApiResult.getPrJson()));
          }
        } else {
          return Optional.of(getPrJsonDetailsViaManager(connectorDetails, webhookPayloadData));
        }
      } catch (Exception e) {
        log.error(new StringBuilder(128)
                      .append("Failed  while deserializing PR details for IssueComment event. ")
                      .append("Account : ")
                      .append(filterRequestData.getAccountId())
                      .append(", with Exception")
                      .append(e.getMessage())
                      .toString(),
            e);
      }
    }
    return Optional.empty();
  }

  private GitApiTaskResponse buildAndFireDelegateTask(
      WebhookPayloadData webhookPayloadData, TriggerDetails details, ConnectorDetails connectorDetails) {
    Repository repository = webhookPayloadData.getRepository();
    ResponseData responseData = taskExecutionUtils.executeSyncTask(
        DelegateTaskRequest.builder()
            .accountId(details.getNgTriggerEntity().getAccountId())
            .executionTimeout(Duration.ofSeconds(30))
            .taskType("GIT_API_TASK")
            .taskParameters(
                GitApiTaskParams.builder()
                    .gitRepoType(GitRepoType.GITHUB)
                    .requestType(GitApiRequestType.FIND_PULL_REQUEST_DETAILS)
                    .connectorDetails(connectorDetails)
                    .prNumber(((IssueCommentWebhookEvent) webhookPayloadData.getWebhookEvent()).getPullRequestNum())
                    .slug(repository.getSlug())
                    .owner(repository.getNamespace())
                    .repo(repository.getName())
                    .build())
            .build());

    if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      Object object = binaryResponseData.isUsingKryoWithoutReference()
          ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
          : kryoSerializer.asInflatedObject(binaryResponseData.getData());
      if (object instanceof GitApiTaskResponse) {
        GitApiTaskResponse gitApiTaskResponse = (GitApiTaskResponse) object;
        if (gitApiTaskResponse.getGitApiResult() == null) {
          if (isNotEmpty(gitApiTaskResponse.getErrorMessage())) {
            throw new TriggerException(
                String.format("Failed to fetch PR Details. Reason: " + gitApiTaskResponse.getErrorMessage()),
                WingsException.SRE);
          }
        }
        return gitApiTaskResponse;
      } else if (object instanceof ErrorResponseData) {
        ErrorResponseData errorResponseData = (ErrorResponseData) object;
        throw new TriggerException(
            String.format("Failed to fetch PR Details. Reason: {}", errorResponseData.getErrorMessage()),
            WingsException.SRE);
      }
    }
    throw new TriggerException("Failed to fetch PR Details", WingsException.SRE);
  }

  private PullRequest getPrJsonDetailsViaManager(
      ConnectorDetails connectorDetails, WebhookPayloadData webhookPayloadData) {
    ScmConnector scmConnector = (ScmConnector) connectorDetails.getConnectorConfig();
    GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) connectorDetails.getConnectorConfig();
    Repository repository = webhookPayloadData.getRepository();
    scmConnector.setUrl(getGithubUrl(scmConnector.getUrl(), repository.getName(), gitConfigDTO.getConnectionType()));
    final DecryptableEntity decryptableEntity =
        secretDecryptor.decrypt(GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector),
            connectorDetails.getEncryptedDataDetails());
    GitApiAccessDecryptionHelper.setAPIAccessDecryptableEntity(scmConnector, decryptableEntity);
    long prNumber =
        Long.parseLong(((IssueCommentWebhookEvent) webhookPayloadData.getWebhookEvent()).getPullRequestNum());
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        format("[Retrying failed call to fetch codebase metadata: [%s], attempt: {}", connectorDetails.getIdentifier()),
        format(
            "Failed call to fetch codebase metadata: [%s] after retrying {} times", connectorDetails.getIdentifier()));
    FindPRResponse findPRResponse =
        Failsafe.with(retryPolicy).get(() -> scmServiceClient.findPR(scmConnector, prNumber, scmBlockingStub));
    return findPRResponse.getPr();
  }

  private String getGithubUrl(String url, String repo, GitConnectionType gitConnectionType) {
    if (gitConnectionType == GitConnectionType.ACCOUNT && isNotEmpty(repo)) {
      return StringUtils.join(
          StringUtils.stripEnd(url, PATH_SEPARATOR), PATH_SEPARATOR, StringUtils.stripStart(repo, PATH_SEPARATOR));
    }
    return url;
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
