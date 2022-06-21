/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.stripEnd;
import static org.apache.commons.lang3.StringUtils.stripStart;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HookEventType;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatus;
import io.harness.ngtriggers.service.NGTriggerWebhookRegistrationService;
import io.harness.product.ci.scm.proto.WebhookResponse;
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
  private final WebhookEventClient webhookEventClient;

  @Override
  public WebhookRegistrationStatus registerWebhook(NGTriggerEntity ngTriggerEntity) {
    BaseNGAccess ngAccess = BaseNGAccess.builder()
                                .accountIdentifier(ngTriggerEntity.getAccountId())
                                .orgIdentifier(ngTriggerEntity.getOrgIdentifier())
                                .projectIdentifier(ngTriggerEntity.getProjectIdentifier())
                                .build();
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(
        ngAccess, ngTriggerEntity.getMetadata().getWebhook().getGit().getConnectorIdentifier());
    String url = connectorUtils.retrieveURL(connectorDetails);
    if (connectorUtils.getConnectionType(connectorDetails).equals(GitConnectionType.ACCOUNT)
        && ngTriggerEntity.getMetadata().getWebhook().getGit().getRepoName() != null) {
      url = format("%s/%s", stripEnd(url, "/"),
          stripStart(ngTriggerEntity.getMetadata().getWebhook().getGit().getRepoName(), "/"));
    }

    return registerWebhookInternal(ngTriggerEntity.getProjectIdentifier(), ngTriggerEntity.getOrgIdentifier(),
        ngTriggerEntity.getAccountId(), url,
        ngTriggerEntity.getMetadata().getWebhook().getGit().getConnectorIdentifier());
  }

  private WebhookRegistrationStatus registerWebhookInternal(String projectIdentifier, String orgIdentifier,
      String accountIdentifier, String repoUrl, String connectorIdentifierRef) {
    UpsertWebhookRequestDTO upsertWebhookRequestDTO = UpsertWebhookRequestDTO.builder()
                                                          .projectIdentifier(projectIdentifier)
                                                          .orgIdentifier(orgIdentifier)
                                                          .accountIdentifier(accountIdentifier)
                                                          .connectorIdentifierRef(connectorIdentifierRef)
                                                          .repoURL(repoUrl)
                                                          .hookEventType(HookEventType.TRIGGER_EVENTS)
                                                          .build();
    UpsertWebhookResponseDTO upsertWebhookResponseDTO = null;
    try {
      upsertWebhookResponseDTO = getResponse(webhookEventClient.upsertWebhook(upsertWebhookRequestDTO));
    } catch (Exception ex) {
      log.error("Failed to register webhook", ex);
      return WebhookRegistrationStatus.ERROR;
    }
    if (upsertWebhookResponseDTO.getStatus() > 300) {
      return WebhookRegistrationStatus.FAILED;
    }
    WebhookResponse webhookResponse = upsertWebhookResponseDTO.getWebhookResponse();
    if (webhookResponse != null) {
      log.info("Auto registered webhook with following events: {}", webhookResponse.getName());
    }
    return WebhookRegistrationStatus.SUCCESS;
  }
}
