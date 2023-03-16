/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.exception.WingsException.USER;

import io.harness.NGCommonEntityConstants;
import io.harness.NgAutoLogContext;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.task.scm.GitWebhookTaskType;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.runtime.SCMRuntimeException;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.logging.AutoLogContext;
import io.harness.ng.BaseUrls;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.WebhookConstants;
import io.harness.ng.webhook.constants.ScmApis;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.errorhandler.ScmApiErrorHandlingHelper;
import io.harness.ng.webhook.errorhandler.dtos.ErrorMetadata;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.ng.webhook.services.api.WebhookService;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class WebhookServiceImpl implements WebhookService, WebhookEventService {
  private final WebhookEventRepository webhookEventRepository;
  private final BaseUrls baseUrls;
  private final ScmOrchestratorService scmOrchestratorService;
  private final AccountOrgProjectHelper accountOrgProjectHelper;
  private final ConnectorService connectorService;

  @Inject
  public WebhookServiceImpl(WebhookEventRepository webhookEventRepository,
      ScmOrchestratorService scmOrchestratorService, AccountOrgProjectHelper accountOrgProjectHelper, BaseUrls baseUrls,
      @Named("connectorDecoratorService") ConnectorService connectorService) {
    this.webhookEventRepository = webhookEventRepository;
    this.baseUrls = baseUrls;
    this.scmOrchestratorService = scmOrchestratorService;
    this.accountOrgProjectHelper = accountOrgProjectHelper;
    this.connectorService = connectorService;
  }

  @Override
  public WebhookEvent addEventToQueue(WebhookEvent webhookEvent) {
    try {
      log.info(
          "received webhook event with id {} in the accountId {}", webhookEvent.getUuid(), webhookEvent.getAccountId());
      return webhookEventRepository.save(webhookEvent);
    } catch (Exception e) {
      throw new InvalidRequestException("Webhook event could not be saved for processing");
    }
  }

  public UpsertWebhookResponseDTO upsertWebhook(UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    UpsertWebhookResponseDTO upsertWebhookResponseDTO = null;
    ScmConnector scmConnector =
        getScmConnector(upsertWebhookRequestDTO.getAccountIdentifier(), upsertWebhookRequestDTO.getOrgIdentifier(),
            upsertWebhookRequestDTO.getProjectIdentifier(), upsertWebhookRequestDTO.getConnectorIdentifierRef());
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
      processAndThrowException(
          createWebhookResponse.getStatus(), scmConnector, createWebhookResponse.getError(), upsertWebhookRequestDTO);
      UpsertWebhookResponseDTO response = UpsertWebhookResponseDTO.builder()
                                              .webhookResponse(createWebhookResponse.getWebhook())
                                              .error(createWebhookResponse.getError())
                                              .status(createWebhookResponse.getStatus())
                                              .build();
      log.info("Upsert Webhook Response : {}", response);
      upsertWebhookResponseDTO = response;
    } catch (ExplanationException ex) {
      int statusCode = convertScmErrorCodeToStatusCode(((ScmException) ex.getCause()).getCode());
      processAndThrowException(statusCode, scmConnector, ex.getMessage(), upsertWebhookRequestDTO);
    } catch (HintException ex) {
      ErrorCode errorCode = ((ScmException) ex.getCause().getCause()).getCode();
      int statusCode = convertScmErrorCodeToStatusCode(errorCode);
      processAndThrowException(statusCode, scmConnector, ex.getMessage(), upsertWebhookRequestDTO);
    } catch (ScmException ex) {
      int statusCode = convertScmErrorCodeToStatusCode(ex.getCode());
      processAndThrowException(statusCode, scmConnector, ex.getMessage(), upsertWebhookRequestDTO);
    } catch (SCMRuntimeException ex) {
      log.error("Upsert Webhook Error for accountId: {}, orgId:{}, projectId:{} : ",
          upsertWebhookRequestDTO.getAccountIdentifier(), upsertWebhookRequestDTO.getOrgIdentifier(),
          upsertWebhookRequestDTO.getProjectIdentifier(), ex);
      throw new ScmBadRequestException(
          "Unable to connect to Git Provider. Please check if credentials provided are correct and the repo url is correct.");
    } catch (Exception exception) {
      log.error("Upsert Webhook Error for accountId: {}, orgId:{}, projectId:{} : ",
          upsertWebhookRequestDTO.getAccountIdentifier(), upsertWebhookRequestDTO.getOrgIdentifier(),
          upsertWebhookRequestDTO.getProjectIdentifier(), exception);
      throw exception;
    }
    return upsertWebhookResponseDTO;
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

  private ScmConnector getScmConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.getByRef(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
    if (connectorDTO.isPresent()) {
      ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnector();
      ConnectorConfigDTO connectorConfigDTO = connectorInfoDTO.getConnectorConfig();
      if (connectorConfigDTO instanceof ScmConnector) {
        return (ScmConnector) connectorInfoDTO.getConnectorConfig();
      } else {
        throw new UnexpectedException(String.format(
            "The connector with the  identifier [%s], accountIdentifier [%s], orgIdentifier [%s], projectIdentifier [%s] is not an scm connector",
            connectorInfoDTO.getIdentifier(), accountIdentifier, orgIdentifier, projectIdentifier));
      }
    }
    throw new ConnectorNotFoundException(
        String.format(
            "No connector found for accountIdentifier: [%s], orgIdentifier : [%s], projectIdentifier : [%s], connectorRef : [%s]",
            accountIdentifier, orgIdentifier, projectIdentifier, connectorRef),
        USER);
  }

  public void processAndThrowException(
      int statusCode, ScmConnector scmConnector, String errorMessage, UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    if (ScmApiErrorHandlingHelper.isFailureResponse(statusCode, scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.UPSERT_WEBHOOK, scmConnector.getConnectorType(),
          scmConnector.getUrl(), statusCode, errorMessage,
          ErrorMetadata.builder().connectorRef(upsertWebhookRequestDTO.getConnectorIdentifierRef()).build());
    }
  }
  public int convertScmErrorCodeToStatusCode(ErrorCode errorCode) {
    switch (errorCode) {
      case SCM_NOT_MODIFIED:
        return 304;
      case SCM_NOT_FOUND_ERROR:
        return 404;
      case SCM_CONFLICT_ERROR:
        return 409;
      case SCM_UNPROCESSABLE_ENTITY:
        return 422;
      case SCM_FORBIDDEN:
        return 403;
      case SCM_UNAUTHORIZED:
        return 401;
      case SCM_BAD_REQUEST:
        return 400;
      default:
        return 500;
    }
  }
}
