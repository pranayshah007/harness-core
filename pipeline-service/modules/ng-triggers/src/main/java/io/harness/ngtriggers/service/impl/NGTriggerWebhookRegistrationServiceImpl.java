/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.stripEnd;
import static org.apache.commons.lang3.StringUtils.stripStart;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HookEventType;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.exception.ExceptionUtils;
import io.harness.git.GitClientHelper;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatusData;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatusData.WebhookRegistrationStatusDataBuilder;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookAutoRegistrationStatus;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerWebhookRegistrationService;
import io.harness.product.ci.scm.proto.WebhookResponse;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.utils.ConnectorUtils;
import io.harness.webhook.remote.WebhookEventClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerWebhookRegistrationServiceImpl implements NGTriggerWebhookRegistrationService {
  @Inject private final ConnectorUtils connectorUtils;
  @Inject private final NGTriggerElementMapper ngTriggerElementMapper;
  @Inject private final SecretManagerClientService ngSecretService;
  private final WebhookEventClient webhookEventClient;

  @Override

  public WebhookRegistrationStatusData registerWebhook(NGTriggerEntity ngTriggerEntity) {
    BaseNGAccess ngAccess = BaseNGAccess.builder()
                                .accountIdentifier(ngTriggerEntity.getAccountId())
                                .orgIdentifier(ngTriggerEntity.getOrgIdentifier())
                                .projectIdentifier(ngTriggerEntity.getProjectIdentifier())
                                .build();
    ConnectorDetails connectorDetails;
    if (Boolean.TRUE.equals(ngTriggerEntity.getMetadata().getWebhook().getGit().getIsHarnessScm())) {
      return handleHarnessScmWebhook(ngTriggerEntity);
    }
    try {
      connectorDetails = connectorUtils.getConnectorDetails(
          ngAccess, ngTriggerEntity.getMetadata().getWebhook().getGit().getConnectorIdentifier());
    } catch (Exception ex) {
      log.error("Failed to register webhook, could not fetch connector details", ex);
      WebhookRegistrationStatusDataBuilder metadataBuilder = WebhookRegistrationStatusData.builder();
      metadataBuilder.webhookAutoRegistrationStatus(
          WebhookAutoRegistrationStatus.builder()
              .detailedMessage("Failed to fetch connector details: " + ExceptionUtils.getMessage(ex))
              .registrationResult(WebhookRegistrationStatus.ERROR)
              .build());
      return metadataBuilder.build();
    }
    String url = connectorUtils.retrieveURL(connectorDetails);
    String repoName = ngTriggerEntity.getMetadata().getWebhook().getGit().getRepoName();
    String secretIdentifierRef = ngTriggerEntity.getEncryptedWebhookSecretIdentifier();

    if (connectorUtils.getConnectionType(connectorDetails).equals(GitConnectionType.ACCOUNT)) {
      if (isNotEmpty(repoName)) {
        url = format("%s/%s", stripEnd(url, "/"), stripStart(repoName, "/"));
      } else {
        log.warn("Repo name is empty for account level connector");
      }
    } else if (connectorUtils.getConnectionType(connectorDetails).equals(GitConnectionType.PROJECT)) {
      if (isNotEmpty(repoName)) {
        if (connectorDetails.getConnectorType() == ConnectorType.AZURE_REPO) {
          url = GitClientHelper.getCompleteUrlForProjectLevelAzureConnector(url, repoName);
        }
      } else {
        log.warn("Repo name is empty for project level connector");
      }
    }

    return registerWebhookInternal(ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getOrgIdentifier(),
        ngTriggerEntity.getAccountId(), url,
        ngTriggerEntity.getMetadata().getWebhook().getGit().getConnectorIdentifier(), secretIdentifierRef);
  }

  private WebhookRegistrationStatusData handleHarnessScmWebhook(NGTriggerEntity ngTriggerEntity) {
    UpsertWebhookRequestDTO upsertWebhookRequestDTO =
        UpsertWebhookRequestDTO.builder()
            .projectIdentifier(ngTriggerEntity.getProjectIdentifier())
            .orgIdentifier(ngTriggerEntity.getOrgIdentifier())
            .accountIdentifier(ngTriggerEntity.getAccountId())
            .repoURL(ngTriggerEntity.getMetadata().getWebhook().getGit().getRepoName())
            .isHarnessCode(true)
            .hookEventType(HookEventType.TRIGGER_EVENTS)
            .build();
    return getWebhookRegistrationStatusData(upsertWebhookRequestDTO);
  }

  private WebhookRegistrationStatusData registerWebhookInternal(String projectIdentifier, String orgIdentifier,
      String accountIdentifier, String repoUrl, String connectorIdentifierRef, String secretIdentifierRef) {
    UpsertWebhookRequestDTO upsertWebhookRequestDTO = UpsertWebhookRequestDTO.builder()
                                                          .projectIdentifier(projectIdentifier)
                                                          .orgIdentifier(orgIdentifier)
                                                          .accountIdentifier(accountIdentifier)
                                                          .connectorIdentifierRef(connectorIdentifierRef)
                                                          .repoURL(repoUrl)
                                                          .hookEventType(HookEventType.TRIGGER_EVENTS)
                                                          .webhookSecretIdentifierRef(secretIdentifierRef)
                                                          .build();
    return getWebhookRegistrationStatusData(upsertWebhookRequestDTO);
  }

  private WebhookRegistrationStatusData getWebhookRegistrationStatusData(
      UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    UpsertWebhookResponseDTO upsertWebhookResponseDTO = null;

    WebhookRegistrationStatusDataBuilder metadataBuilder = WebhookRegistrationStatusData.builder();

    try {
      upsertWebhookResponseDTO = getResponse(webhookEventClient.upsertWebhook(upsertWebhookRequestDTO));
    } catch (Exception ex) {
      log.error("Failed to register webhook", ex);
      metadataBuilder.webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                        .detailedMessage(ex.getMessage())
                                                        .registrationResult(WebhookRegistrationStatus.ERROR)
                                                        .build());

      return metadataBuilder.build();
    }
    if (upsertWebhookResponseDTO.getStatus() > 300) {
      log.info("Failed to auto register webhook: {}", upsertWebhookResponseDTO.getError());
      metadataBuilder.webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                        .detailedMessage(upsertWebhookResponseDTO.getError())
                                                        .registrationResult(WebhookRegistrationStatus.FAILED)
                                                        .build());

      return metadataBuilder.build();
    }
    WebhookResponse webhookResponse = upsertWebhookResponseDTO.getWebhookResponse();
    if (webhookResponse != null) {
      log.info("Auto registered webhook with following events: {}", webhookResponse.getName());
      metadataBuilder.webhookId(webhookResponse.getId());
    }
    metadataBuilder.webhookAutoRegistrationStatus(
        WebhookAutoRegistrationStatus.builder().registrationResult(WebhookRegistrationStatus.SUCCESS).build());
    return metadataBuilder.build();
  }
}
