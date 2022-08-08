/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.NgAutoLogContext;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.scm.GitWebhookTaskType;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.logging.AutoLogContext;
import io.harness.ng.BaseUrls;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.WebhookConstants;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.ng.webhook.services.api.WebhookService;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class WebhookServiceImpl implements WebhookService, WebhookEventService {
  private final WebhookEventRepository webhookEventRepository;
  private final BaseUrls baseUrls;
  private final ScmOrchestratorService scmOrchestratorService;
  private final AccountOrgProjectHelper accountOrgProjectHelper;

  @Inject
  public WebhookServiceImpl(WebhookEventRepository webhookEventRepository,
      ScmOrchestratorService scmOrchestratorService, AccountOrgProjectHelper accountOrgProjectHelper,
      BaseUrls baseUrls) {
    this.webhookEventRepository = webhookEventRepository;
    this.baseUrls = baseUrls;
    this.scmOrchestratorService = scmOrchestratorService;
    this.accountOrgProjectHelper = accountOrgProjectHelper;
  }

  @Override
  public WebhookEvent addEventToQueue(WebhookEvent webhookEvent) {
    try {
      log.info("received webhook event in the accountId {} ", webhookEvent.getAccountId());
      return webhookEventRepository.save(webhookEvent);
    } catch (Exception e) {
      throw new InvalidRequestException("Webhook event could not be saved for processing");
    }
  }

  public UpsertWebhookResponseDTO upsertWebhook(UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    try (AutoLogContext ignore1 = new NgAutoLogContext(upsertWebhookRequestDTO.getProjectIdentifier(),
             upsertWebhookRequestDTO.getOrgIdentifier(), upsertWebhookRequestDTO.getAccountIdentifier(),
             AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      String target = getTargetUrl(upsertWebhookRequestDTO.getAccountIdentifier());
      CreateWebhookResponse createWebhookResponse =
          scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
              -> scmClientFacilitatorService.upsertWebhook(upsertWebhookRequestDTO, target, GitWebhookTaskType.UPSERT),
              upsertWebhookRequestDTO.getProjectIdentifier(), upsertWebhookRequestDTO.getOrgIdentifier(),
              upsertWebhookRequestDTO.getAccountIdentifier(), upsertWebhookRequestDTO.getConnectorIdentifierRef(), null,
              null);
      UpsertWebhookResponseDTO response = UpsertWebhookResponseDTO.builder()
                                              .webhookResponse(createWebhookResponse.getWebhook())
                                              .error(createWebhookResponse.getError())
                                              .status(createWebhookResponse.getStatus())
                                              .build();
      log.info("Upsert Webhook Response : {}", response);
      return response;
    } catch (Exception exception) {
      log.error("Upsert Webhook Error for accountId: {}, orgId:{}, projectId:{} : ",
          upsertWebhookRequestDTO.getAccountIdentifier(), upsertWebhookRequestDTO.getOrgIdentifier(),
          upsertWebhookRequestDTO.getProjectIdentifier(), exception);
      throw exception;
    }
  }

  @VisibleForTesting
  String getTargetUrl(String accountIdentifier) {
    String vanityUrl = accountOrgProjectHelper.getVanityUrl(accountIdentifier);
    String webhookBaseUrlFromConfig = getWebhookBaseUrl();
    if (!webhookBaseUrlFromConfig.endsWith("/")) {
      webhookBaseUrlFromConfig += "/";
    }
    log.info("The vanity url is {} and webhook url is {}", vanityUrl, webhookBaseUrlFromConfig);
    String basewebhookUrl = vanityUrl == null ? webhookBaseUrlFromConfig : getVanityUrlForNG(vanityUrl);
    StringBuilder webhookUrl = new StringBuilder(basewebhookUrl)
                                   .append(WebhookConstants.WEBHOOK_ENDPOINT)
                                   .append('?')
                                   .append(NGCommonEntityConstants.ACCOUNT_KEY)
                                   .append('=')
                                   .append(accountIdentifier);
    log.info("The complete webhook url is {}", webhookUrl.toString());
    return webhookUrl.toString();
  }

  private String getVanityUrlForNG(String vanityUrl) {
    if (!vanityUrl.endsWith("/")) {
      vanityUrl += "/";
    }
    return vanityUrl + "ng/api/";
  }

  @VisibleForTesting
  String getWebhookBaseUrl() {
    return baseUrls.getWebhookBaseUrl();
  }
}
