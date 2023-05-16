/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.beans.connector.scm.adapter.AzureRepoToGitMapper.mapToGitConnectionType;
import static io.harness.ngtriggers.Constants.DOT_GIT;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.NO_MATCHING_TRIGGER_FOR_REPO;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.AWS_CODECOMMIT;
import static io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo.AZURE_REPO;
import static io.harness.utils.IdentifierRefHelper.getFullyQualifiedIdentifierRefString;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Repository;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.git.GitClientHelper;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse.WebhookEventMappingResponseBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.TriggerGitConnectorWrapper;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.utils.GitProviderDataObtainmentManager;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class GitWebhookTriggerRepoFilter implements TriggerFilter {
  // TODO: This should come from scm parsing service
  private static final String AWS_CODECOMMIT_URL_PATTERN = "https://git-codecommit.%s.amazonaws.com/v1/repos/%s";
  private final NGTriggerService ngTriggerService;
  private final GitProviderDataObtainmentManager additionalDataObtainmentManager;

  @Override
  public WebhookEventMappingResponse applyFilter(FilterRequestData filterRequestData) {
    WebhookEventMappingResponseBuilder mappingResponseBuilder = initWebhookEventMappingResponse(filterRequestData);

    WebhookPayloadData webhookPayloadData = filterRequestData.getWebhookPayloadData();
    TriggerWebhookEvent originalEvent = webhookPayloadData.getOriginalEvent();
    Repository repository = webhookPayloadData.getRepository();
    Set<String> urls = getUrls(repository, originalEvent.getSourceRepoType());

    // {connectorFQN, connectorConfig, List<Trigger>}
    List<TriggerGitConnectorWrapper> triggerGitConnectorWrappers =
        prepareTriggerConnectorWrapperList(originalEvent.getAccountId(), filterRequestData.getDetails());

    List<TriggerDetails> eligibleTriggers = new ArrayList<>();
    for (TriggerGitConnectorWrapper wrapper : triggerGitConnectorWrappers) {
      // update GitConnectionType and repoUrl values in wrapper.
      updateConnectionTypeAndUrlInWrapper(wrapper);

      if (wrapper.getGitConnectionType() == GitConnectionType.REPO) {
        evaluateWrapperForRepoLevelGitConnector(urls, eligibleTriggers, wrapper);
      } else if (wrapper.getGitConnectionType() == GitConnectionType.PROJECT) {
        evaluateWrapperForProjectLevelGitConnector(urls, eligibleTriggers, wrapper);
      } else if (wrapper.getGitConnectionType() == GitConnectionType.ACCOUNT) {
        evaluateWrapperForAccountLevelGitConnector(urls, eligibleTriggers, wrapper);
      }
    }

    if (isEmpty(eligibleTriggers)) {
      String msg = format("No trigger found for repoUrl: %s for Account %s",
          webhookPayloadData.getRepository().getLink(), filterRequestData.getAccountId());
      log.info(msg);
      mappingResponseBuilder.failedToFindTrigger(true)
          .webhookEventResponse(
              TriggerEventResponseHelper.toResponse(NO_MATCHING_TRIGGER_FOR_REPO, originalEvent, null, null, msg, null))
          .build();
    } else {
      // fetches additional information
      additionalDataObtainmentManager.acquireProviderData(filterRequestData, eligibleTriggers);
      addDetails(mappingResponseBuilder, filterRequestData, eligibleTriggers);
    }

    return mappingResponseBuilder.build();
  }

  @VisibleForTesting
  HashSet<String> getUrls(Repository repository, String sourceRepoType) {
    if (AWS_CODECOMMIT.name().equals(sourceRepoType)) {
      String[] arnTokens = repository.getId().split(":");
      String awsRepoUrl = format(AWS_CODECOMMIT_URL_PATTERN, arnTokens[3], arnTokens[5]);
      return new HashSet<>(Collections.singletonList(awsRepoUrl));
    } else if (AZURE_REPO.name().equals(sourceRepoType)) {
      HashSet<String> urls = new HashSet<>();
      String httpUrl = repository.getHttpURL().toLowerCase();
      String sshUrl = isEmpty(repository.getSshURL()) ? GitClientHelper.getCompleteSSHUrlFromHttpUrlForAzure(httpUrl)
                                                      : repository.getSshURL();
      httpUrl = GitClientHelper.convertToNewHTTPUrlForAzure(httpUrl);
      sshUrl = GitClientHelper.convertToNewSSHUrlForAzure(sshUrl);
      urls.add(httpUrl);
      urls.add(sshUrl);
      return urls;
    }

    HashSet<String> urls = new HashSet<>();

    String httpUrl = repository.getHttpURL().toLowerCase();
    httpUrl = GitClientHelper.convertToHttps(httpUrl);
    urls.add(httpUrl);
    // Add url without .git, to handle case, where user entered url without .git on connector
    if (httpUrl.endsWith(DOT_GIT)) {
      urls.add(httpUrl.substring(0, httpUrl.length() - 4));
    }
    // Add url without .git, to handle case, where user entered url without .git on connector
    String sshUrl = repository.getSshURL().toLowerCase();
    if (sshUrl.endsWith(DOT_GIT)) {
      urls.add(sshUrl.substring(0, sshUrl.length() - 4));
    }
    urls.add(sshUrl);
    urls.add(repository.getLink().toLowerCase());

    return urls;
  }

  private void evaluateWrapperForProjectLevelGitConnector(
      Set<String> urls, List<TriggerDetails> eligibleTriggers, TriggerGitConnectorWrapper wrapper) {
    String accUrl = wrapper.getUrl();
    String sanitizedAccUrl = sanitizeUrl(accUrl);

    for (TriggerDetails details : wrapper.getTriggers()) {
      try {
        if (wrapper.getConnectorType() == ConnectorType.AZURE_REPO) {
          final String sanitizedRepoUrl = GitClientHelper.getCompleteUrlForProjectLevelAzureConnector(
              sanitizedAccUrl, details.getNgTriggerEntity().getMetadata().getWebhook().getGit().getRepoName());
          String finalUrl = urls.stream().filter(u -> u.equalsIgnoreCase(sanitizedRepoUrl)).findAny().orElse(null);

          if (!isBlank(finalUrl)) {
            eligibleTriggers.add(details);
          } else {
            final String repoUrl = GitClientHelper.getCompleteUrlForProjectLevelAzureConnector(
                accUrl, details.getNgTriggerEntity().getMetadata().getWebhook().getGit().getRepoName());
            finalUrl = urls.stream().filter(u -> u.equalsIgnoreCase(repoUrl)).findAny().orElse(null);

            if (!isBlank(finalUrl)) {
              eligibleTriggers.add(details);
            }
          }
        }
      } catch (Exception e) {
        log.error(getTriggerSkipMessage(details.getNgTriggerEntity()));
      }
    }
  }

  private void evaluateWrapperForAccountLevelGitConnector(
      Set<String> urls, List<TriggerDetails> eligibleTriggers, TriggerGitConnectorWrapper wrapper) {
    String accUrl = wrapper.getUrl();
    String sanitizedAccUrl = sanitizeUrl(accUrl);

    for (TriggerDetails details : wrapper.getTriggers()) {
      try {
        final String sanitizedRepoUrl =
            new StringBuilder(128)
                .append(sanitizedAccUrl)
                .append(sanitizedAccUrl.endsWith("/") ? EMPTY : '/')
                .append(details.getNgTriggerEntity().getMetadata().getWebhook().getGit().getRepoName())
                .toString();

        String finalUrl = urls.stream().filter(u -> u.equalsIgnoreCase(sanitizedRepoUrl)).findAny().orElse(null);

        if (!isBlank(finalUrl)) {
          eligibleTriggers.add(details);
        } else {
          final String repoUrl =
              new StringBuilder(128)
                  .append(accUrl)
                  .append(accUrl.endsWith("/") ? EMPTY : '/')
                  .append(details.getNgTriggerEntity().getMetadata().getWebhook().getGit().getRepoName())
                  .toString();

          finalUrl = urls.stream().filter(u -> u.equalsIgnoreCase(repoUrl)).findAny().orElse(null);

          if (!isBlank(finalUrl)) {
            eligibleTriggers.add(details);
          }
        }
      } catch (Exception e) {
        log.error(getTriggerSkipMessage(details.getNgTriggerEntity()));
      }
    }
  }

  @VisibleForTesting
  void evaluateWrapperForRepoLevelGitConnector(
      Set<String> urls, List<TriggerDetails> eligibleTriggers, TriggerGitConnectorWrapper wrapper) {
    String url = wrapper.getUrl();
    final String modifiedUrl = sanitizeUrl(url);

    String finalUrl = urls.stream().filter(u -> u.equalsIgnoreCase(modifiedUrl)).findAny().orElse(null);

    if (!isBlank(finalUrl)) {
      eligibleTriggers.addAll(wrapper.getTriggers());
    } else {
      finalUrl = urls.stream().filter(u -> u.equalsIgnoreCase(url)).findAny().orElse(null);

      if (!isBlank(finalUrl)) {
        eligibleTriggers.addAll(wrapper.getTriggers());
      }
    }
  }

  /*
  Since the url coming from scm response are in the form of
    1. https://github.com/<something>.git
    2. https://github.com/<something>
    3. git@github.com:<something>.git
    4. git@github.com:<something>
  And we allow connector url as http://<something>, http://www.<something>, https://www.<something>
  This method will do the required fix
   */
  @VisibleForTesting
  String sanitizeUrl(String url) {
    String modifiedUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    modifiedUrl = modifiedUrl.replaceFirst("http://", "https://");
    modifiedUrl = modifiedUrl.replaceFirst("https://www.", "https://");

    if (modifiedUrl.contains("ssh://")) {
      modifiedUrl = !modifiedUrl.contains("@") ? modifiedUrl.replaceFirst("ssh://", "git@")
                                               : modifiedUrl.replaceFirst("ssh://", "");
      modifiedUrl = modifiedUrl.contains(":") ? modifiedUrl : modifiedUrl.replaceFirst("/", ":");
    }

    return modifiedUrl;
  }

  @VisibleForTesting
  void updateConnectionTypeAndUrlInWrapper(TriggerGitConnectorWrapper wrapper) {
    ConnectorConfigDTO connectorConfigDTO = wrapper.getConnectorConfigDTO();

    if (connectorConfigDTO.getClass().isAssignableFrom(GithubConnectorDTO.class)) {
      GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorConfigDTO;
      wrapper.setConnectorType(ConnectorType.GITHUB);
      wrapper.setUrl(githubConnectorDTO.getUrl());
      wrapper.setGitConnectionType(githubConnectorDTO.getConnectionType());
    } else if (connectorConfigDTO.getClass().isAssignableFrom(GitlabConnectorDTO.class)) {
      GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorConfigDTO;
      wrapper.setConnectorType(ConnectorType.GITLAB);
      wrapper.setUrl(gitlabConnectorDTO.getUrl());
      wrapper.setGitConnectionType(gitlabConnectorDTO.getConnectionType());
    } else if (connectorConfigDTO.getClass().isAssignableFrom(BitbucketConnectorDTO.class)) {
      BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorConfigDTO;
      wrapper.setConnectorType(ConnectorType.BITBUCKET);
      wrapper.setUrl(bitbucketConnectorDTO.getUrl());
      wrapper.setGitConnectionType(bitbucketConnectorDTO.getConnectionType());
    } else if (connectorConfigDTO.getClass().isAssignableFrom(AzureRepoConnectorDTO.class)) {
      AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) connectorConfigDTO;
      wrapper.setConnectorType(ConnectorType.AZURE_REPO);
      wrapper.setUrl(azureRepoConnectorDTO.getUrl());
      wrapper.setGitConnectionType(mapToGitConnectionType(azureRepoConnectorDTO.getConnectionType()));
    } else if (connectorConfigDTO.getClass().isAssignableFrom(AwsCodeCommitConnectorDTO.class)) {
      AwsCodeCommitConnectorDTO awsCodeCommitConnectorDTO = (AwsCodeCommitConnectorDTO) connectorConfigDTO;
      wrapper.setConnectorType(ConnectorType.CODECOMMIT);
      wrapper.setUrl(awsCodeCommitConnectorDTO.getUrl());
      AwsCodeCommitUrlType urlType = awsCodeCommitConnectorDTO.getUrlType();
      if (urlType == AwsCodeCommitUrlType.REGION) {
        wrapper.setGitConnectionType(GitConnectionType.ACCOUNT);
      } else if (urlType == AwsCodeCommitUrlType.REPO) {
        wrapper.setGitConnectionType(GitConnectionType.REPO);
      }
    }
  }

  @VisibleForTesting
  List<TriggerGitConnectorWrapper> prepareTriggerConnectorWrapperList(
      String accountId, List<TriggerDetails> triggerDetails) {
    // Map 1
    Map<String, List<TriggerDetails>> triggerToConnectorMap = new HashMap<>();
    triggerDetails.forEach(
        triggerDetail -> generateConnectorFQNFromTriggerConfig(triggerDetail, triggerToConnectorMap));

    // Map 2
    Map<String, ConnectorConfigDTO> connectorMap = new HashMap<>();
    List<ConnectorResponseDTO> connectors =
        ngTriggerService.fetchConnectorsByFQN(accountId, new ArrayList<>(triggerToConnectorMap.keySet()));
    log.info("Trigger Connectors list count {} , received connectors counts from NG {}, triggerDetails count {} ",
        triggerToConnectorMap.keySet().size(), connectors.size(), triggerDetails.size());
    log.info("Eligible connectors list {}  ",
        String.join(",",
            connectors.stream()
                .map(connectorResponseDTO -> connectorResponseDTO.getConnector().getIdentifier())
                .sorted()
                .collect(Collectors.toList())));
    connectors.forEach(connector
        -> connectorMap.put(
            FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountId,
                connector.getConnector().getOrgIdentifier(), connector.getConnector().getProjectIdentifier(),
                connector.getConnector().getIdentifier()),
            connector.getConnector().getConnectorConfig()));

    return connectorMap.keySet()
        .stream()
        .map(fqn
            -> TriggerGitConnectorWrapper.builder()
                   .connectorFQN(fqn)
                   .connectorConfigDTO(connectorMap.get(fqn))
                   .triggers(triggerToConnectorMap.get(fqn))
                   .build())
        .collect(toList());
  }

  @VisibleForTesting
  void generateConnectorFQNFromTriggerConfig(
      TriggerDetails triggerDetail, Map<String, List<TriggerDetails>> triggerToConnectorMap) {
    NGTriggerEntity ngTriggerEntity = triggerDetail.getNgTriggerEntity();
    WebhookMetadata webhook = ngTriggerEntity.getMetadata().getWebhook();
    if (webhook == null || webhook.getGit() == null) {
      return;
    }

    try {
      String fullyQualifiedIdentifier = getFullyQualifiedIdentifierRefString(IdentifierRefHelper.getIdentifierRef(
          webhook.getGit().getConnectorIdentifier(), ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
          ngTriggerEntity.getProjectIdentifier()));

      List<TriggerDetails> triggerDetailList =
          triggerToConnectorMap.computeIfAbsent(fullyQualifiedIdentifier, k -> new ArrayList<>());

      triggerDetailList.add(triggerDetail);
    } catch (Exception ex) {
      log.error(getTriggerSkipMessage(triggerDetail.getNgTriggerEntity()));
    }
  }
}
