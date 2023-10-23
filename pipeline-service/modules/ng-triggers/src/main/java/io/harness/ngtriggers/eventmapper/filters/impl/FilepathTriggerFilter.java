/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE_REPO;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.CODECOMMIT;
import static io.harness.delegate.beans.connector.ConnectorType.GIT;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.beans.connector.ConnectorType.GITLAB;
import static io.harness.ngtriggers.Constants.BITBUCKET_LOWER_CASE;
import static io.harness.ngtriggers.Constants.CHANGED_FILES;
import static io.harness.ngtriggers.Constants.COMMIT_FILE_ADDED;
import static io.harness.ngtriggers.Constants.COMMIT_FILE_MODIFIED;
import static io.harness.ngtriggers.Constants.COMMIT_FILE_REMOVED;
import static io.harness.ngtriggers.Constants.GITHUB_LOWER_CASE;
import static io.harness.ngtriggers.Constants.GITLAB_LOWER_CASE;
import static io.harness.ngtriggers.Constants.TRIGGER_PAYLOAD_COMMITS;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_FILEPATH_CONDITIONS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.task.scm.ScmPathFilterEvaluationTaskResponse;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.conditionchecker.ConditionEvaluator;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.utils.SCMFilePathEvaluator;
import io.harness.ngtriggers.utils.SCMFilePathEvaluatorFactory;
import io.harness.ngtriggers.utils.WebhookTriggerFilterUtils;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.utils.ConnectorUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class FilepathTriggerFilter implements TriggerFilter {
  private SCMFilePathEvaluatorFactory scmFilePathEvaluatorFactory;
  private NGTriggerElementMapper ngTriggerElementMapper;
  private ConnectorUtils connectorUtils;

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);

    // If not push or PR, return list as is, as path filters does not apply
    if (pathFilterEvaluationNotNeeded(filterRequestData)) {
      return mappingResponseBuilder.failedToFindTrigger(false)
          .parseWebhookResponse(filterRequestData.getWebhookPayloadData().getParseWebhookResponse())
          .triggers(filterRequestData.getDetails())
          .build();
    }

    List<TriggerDetails> matchedTriggers = new ArrayList<>();
    for (TriggerDetails trigger : filterRequestData.getDetails()) {
      NGTriggerConfigV2 ngTriggerConfig = trigger.getNgTriggerConfigV2();
      if (ngTriggerConfig == null) {
        ngTriggerConfig = ngTriggerElementMapper.toTriggerConfigV2(trigger.getNgTriggerEntity());
      }

      TriggerDetails triggerDetails = TriggerDetails.builder()
                                          .ngTriggerConfigV2(ngTriggerConfig)
                                          .ngTriggerEntity(trigger.getNgTriggerEntity())
                                          .build();
      if (checkTriggerEligibility(filterRequestData, triggerDetails)) {
        matchedTriggers.add(triggerDetails);
      }
    }

    if (isEmpty(matchedTriggers)) {
      log.info("No trigger matched Path condition after filter evaluation:");
      mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_FILEPATH_CONDITIONS,
              filterRequestData.getWebhookPayloadData().getOriginalEvent(), null, null,
              "No trigger matched Path condition after filter evaluation for Account: "
                  + filterRequestData.getAccountId(),
              null))
          .build();
    } else {
      addDetails(mappingResponseBuilder, filterRequestData, matchedTriggers);
    }
    return mappingResponseBuilder.build();
  }

  @VisibleForTesting
  boolean pathFilterEvaluationNotNeeded(FilterRequestData filterRequestData) {
    ParseWebhookResponse parseWebhookResponse = filterRequestData.getWebhookPayloadData().getParseWebhookResponse();
    if (parseWebhookResponse == null) {
      return true;
    }

    if (filterRequestData.getWebhookPayloadData().getOriginalEvent().getSourceRepoType().equalsIgnoreCase(
            WebhookTriggerType.AWS_CODECOMMIT.getEntityMetadataName())) {
      return true;
    }

    if (!parseWebhookResponse.hasPr() && !parseWebhookResponse.hasPush() && !parseWebhookResponse.hasComment()) {
      return true;
    }
    return false;
  }

  @VisibleForTesting
  boolean checkTriggerEligibility(FilterRequestData filterRequestData, TriggerDetails triggerDetails) {
    try {
      NGTriggerSpecV2 spec = triggerDetails.getNgTriggerConfigV2().getSource().getSpec();
      if (!WebhookTriggerConfigV2.class.isAssignableFrom(spec.getClass())) {
        log.error("Trigger spec is not a WebhookTriggerConfig");
        return false;
      }

      WebhookTriggerConfigV2 webhookTriggerConfig = (WebhookTriggerConfigV2) spec;
      List<TriggerEventDataCondition> payloadConditions =
          webhookTriggerConfig.getSpec().fetchPayloadAware().fetchPayloadConditions();

      if (isEmpty(payloadConditions)) {
        return true;
      }

      TriggerEventDataCondition pathCondition =
          payloadConditions.stream()
              .filter(payloadCondition -> CHANGED_FILES.equalsIgnoreCase(payloadCondition.getKey()))
              .findFirst()
              .orElse(null);
      if (pathCondition == null) {
        return true;
      }

      if (shouldEvaluateOnSCM(filterRequestData)) {
        return initiateSCMTaskAndEvaluate(filterRequestData, triggerDetails, pathCondition);
      } else {
        return evaluateFromPushPayload(filterRequestData, pathCondition);
      }
    } catch (Exception e) {
      log.error(getTriggerSkipMessage(triggerDetails.getNgTriggerEntity()), e);
      return false;
    }
  }

  @VisibleForTesting
  boolean evaluateFromPushPayload(FilterRequestData filterRequestData, TriggerEventDataCondition pathCondition) {
    Set<String> payloadFiles = getFilesFromPushPayload(filterRequestData);

    boolean eligible = false;
    for (String pathFetched : payloadFiles) {
      if (ConditionEvaluator.evaluate(pathFetched, pathCondition.getValue(), pathCondition.getOperator().getValue())) {
        eligible = true;
        break;
      }
    }

    return eligible;
  }

  @VisibleForTesting
  boolean initiateSCMTaskAndEvaluate(
      FilterRequestData filterRequestData, TriggerDetails triggerDetails, TriggerEventDataCondition pathCondition) {
    ScmPathFilterEvaluationTaskResponse scmPathFilterEvaluationTaskResponse =
        performScmPathFilterEvaluation(triggerDetails.getNgTriggerEntity(), filterRequestData, pathCondition);
    if (scmPathFilterEvaluationTaskResponse == null) {
      log.error(new StringBuilder(128)
                    .append(getTriggerSkipMessage(triggerDetails.getNgTriggerEntity()))
                    .append(", Null response from Delegate Task: ")
                    .toString());
      return false;
    } else {
      if (isNotEmpty(scmPathFilterEvaluationTaskResponse.getErrorMessage())) {
        log.error(new StringBuilder(128)
                      .append(getTriggerSkipMessage(triggerDetails.getNgTriggerEntity()))
                      .append(", Error Message from Delegate Task: ")
                      .append(scmPathFilterEvaluationTaskResponse.getErrorMessage())
                      .toString());
      }
      return scmPathFilterEvaluationTaskResponse.isMatched();
    }
  }

  private ScmPathFilterEvaluationTaskResponse performScmPathFilterEvaluation(
      NGTriggerEntity ngTriggerEntity, FilterRequestData filterRequestData, TriggerEventDataCondition pathCondition) {
    try {
      WebhookMetadata webhook = ngTriggerEntity.getMetadata().getWebhook();
      ConnectorDetails connectorDetails =
          connectorUtils.getConnectorDetails(IdentifierRef.builder()
                                                 .accountIdentifier(ngTriggerEntity.getAccountId())
                                                 .orgIdentifier(ngTriggerEntity.getOrgIdentifier())
                                                 .projectIdentifier(ngTriggerEntity.getProjectIdentifier())
                                                 .build(),
              webhook.getGit().getConnectorIdentifier());

      ScmConnector scmConnector = getSCMConnector(connectorDetails, webhook);

      if (scmConnector == null) {
        return null;
      }

      boolean executeOnDelegate =
          connectorDetails.getExecuteOnDelegate() == null || connectorDetails.getExecuteOnDelegate();

      SCMFilePathEvaluator scmFilePathEvaluator = scmFilePathEvaluatorFactory.getEvaluator(executeOnDelegate);
      return scmFilePathEvaluator.execute(filterRequestData, pathCondition, connectorDetails, scmConnector);
    } catch (Exception e) {
      log.error(getTriggerSkipMessage(ngTriggerEntity) + ". Filed in executing delegate task", e);
    }

    return null;
  }

  private ScmConnector getSCMConnector(ConnectorDetails connectorDetails, WebhookMetadata webhookMetadata) {
    ScmConnector connector = null;
    ConnectorConfigDTO connectorConfigDTO = connectorDetails.getConnectorConfig();
    switch (connectorDetails.getConnectorType()) {
      case GITHUB:
        connector = (GithubConnectorDTO) connectorConfigDTO;
        break;
      case GITLAB:
        connector = (GitlabConnectorDTO) connectorConfigDTO;
        break;
      case BITBUCKET:
        connector = (BitbucketConnectorDTO) connectorConfigDTO;
        break;
      case AZURE_REPO:
        connector = (AzureRepoConnectorDTO) connectorConfigDTO;
        break;
      default:
        break;
    }

    if (connector != null) {
      String completeUrl = connector.getUrl();
      GitConnectionType gitConnectionType = getGitConnectionType(connectorDetails);
      if (isAzureRepoProjectLevel(connector)) {
        completeUrl = GitClientHelper.getCompleteUrlForProjectLevelAzureConnector(
            completeUrl, webhookMetadata.getGit().getRepoName());
      } else if (isNotEmpty(webhookMetadata.getGit().getRepoName())
          && (gitConnectionType == null || gitConnectionType == GitConnectionType.ACCOUNT)) {
        completeUrl = StringUtils.stripEnd(connector.getUrl(), "/") + "/"
            + StringUtils.stripStart(webhookMetadata.getGit().getRepoName(), "/");
      }
      connector.setUrl(completeUrl);
    }

    return connector;
  }

  private GitConnectionType getGitConnectionType(ConnectorDetails gitConnector) {
    if (gitConnector == null) {
      return null;
    }

    if (gitConnector.getConnectorType() == GITHUB) {
      GithubConnectorDTO gitConfigDTO = (GithubConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == GITLAB) {
      GitlabConnectorDTO gitConfigDTO = (GitlabConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == BITBUCKET) {
      BitbucketConnectorDTO gitConfigDTO = (BitbucketConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType();
    } else if (gitConnector.getConnectorType() == CODECOMMIT) {
      AwsCodeCommitConnectorDTO gitConfigDTO = (AwsCodeCommitConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getUrlType() == AwsCodeCommitUrlType.REPO ? GitConnectionType.REPO
                                                                    : GitConnectionType.ACCOUNT;
    } else if (gitConnector.getConnectorType() == AZURE_REPO) {
      AzureRepoConnectorDTO gitConfigDTO = (AzureRepoConnectorDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getConnectionType() == AzureRepoConnectionTypeDTO.REPO ? GitConnectionType.REPO
                                                                                 : GitConnectionType.PROJECT;

    } else if (gitConnector.getConnectorType() == GIT) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitConnector.getConnectorConfig();
      return gitConfigDTO.getGitConnectionType();
    } else {
      throw new CIStageExecutionException("Unsupported git ConnectorDisconnectHandler type" + gitConnector.getConnectorType());
    }
  }

  private boolean isAzureRepoProjectLevel(ScmConnector connector) {
    if (connector instanceof AzureRepoConnectorDTO
        && ((AzureRepoConnectorDTO) connector).getConnectionType() == AzureRepoConnectionTypeDTO.PROJECT) {
      return true;
    }
    return false;
  }

  // Gitlab docs say, payload would contains details about 20 commits.
  // So if there more than or equal to 20 commits, there is a chance, few commits were truncated.
  // So, we go to delegate task.
  @VisibleForTesting
  boolean shouldEvaluateOnSCM(FilterRequestData filterRequestData) {
    if (filterRequestData.getWebhookPayloadData().getParseWebhookResponse().hasPr()) {
      return true;
    } else if (filterRequestData.getWebhookPayloadData().getParseWebhookResponse().hasPush()) {
      String sourceRepoType =
          filterRequestData.getWebhookPayloadData().getOriginalEvent().getSourceRepoType().toLowerCase();
      switch (sourceRepoType) {
        case GITHUB_LOWER_CASE:
          // There are no documented limits on Github's push payload (apart from payload being capped to 25MB).
          // In which case the webhook will not event be fired.
          // ref: https://docs.github.com/en/developers/webhooks-and-events/webhooks/webhook-events-and-payloads#push
          // As of 02/01/2023, we verified experimentally that Github's `compareCommits` API is limited to returning
          // 300 files changed, and no pagination is possible, so we should always use the webhook's payload here.
          return false;
        case GITLAB_LOWER_CASE:
          int commitsCount =
              filterRequestData.getWebhookPayloadData().getParseWebhookResponse().getPush().getCommitsCount();
          return commitsCount >= 20;
        case BITBUCKET_LOWER_CASE:
        default:
          return true;
      }
    } else {
      // No Path filter evaluation needed.
      return true;
    }
  }

  @VisibleForTesting
  Set<String> getFilesFromPushPayload(FilterRequestData filterRequestData) {
    Set<String> pushPayloadFiles = new HashSet<>();
    TriggerExpressionEvaluator triggerExpressionEvaluator =
        WebhookTriggerFilterUtils.generatorPMSExpressionEvaluator(filterRequestData.getWebhookPayloadData());
    switch (filterRequestData.getWebhookPayloadData().getOriginalEvent().getSourceRepoType().toLowerCase()) {
      case GITHUB_LOWER_CASE:
      case GITLAB_LOWER_CASE:
        for (Object commitObject : (List) triggerExpressionEvaluator.evaluateExpression(TRIGGER_PAYLOAD_COMMITS)) {
          Map<String, Object> commitJson = (Map) commitObject;
          for (Object added : (List) commitJson.get(COMMIT_FILE_ADDED)) {
            pushPayloadFiles.add((String) added);
          }
          for (Object modified : (List) commitJson.get(COMMIT_FILE_MODIFIED)) {
            pushPayloadFiles.add((String) modified);
          }
          for (Object removed : (List) commitJson.get(COMMIT_FILE_REMOVED)) {
            pushPayloadFiles.add((String) removed);
          }
        }
        return pushPayloadFiles;
      case BITBUCKET_LOWER_CASE:
      default:
        return pushPayloadFiles;
    }
  }
}
