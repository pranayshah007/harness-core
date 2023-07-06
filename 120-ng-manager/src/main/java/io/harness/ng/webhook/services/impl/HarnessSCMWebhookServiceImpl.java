/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;

import io.harness.NGCommonEntityConstants;
import io.harness.NgAutoLogContext;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.gitsync.GitWebhookDetails;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessType;
import io.harness.delegate.beans.connector.scm.harness.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessJWTTokenSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.logging.AutoLogContext;
import io.harness.ng.BaseUrls;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.WebhookConstants;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.dto.ServicePrincipal;
import io.harness.service.ScmClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CODE)
public class HarnessSCMWebhookServiceImpl implements WebhookEventService {
  private final BaseUrls baseUrls;
  private final ScmClient scmClient;
  private final ServiceTokenGenerator tokenGenerator;
  private final String ngServiceSecret;

  @Inject
  public HarnessSCMWebhookServiceImpl(BaseUrls baseUrls, ScmClient scmClient, ServiceTokenGenerator tokenGenerator,
      @Named("ngServiceSecret") String ngServiceSecret) {
    this.baseUrls = baseUrls;
    this.scmClient = scmClient;
    this.tokenGenerator = tokenGenerator;
    this.ngServiceSecret = ngServiceSecret;
  }

  @Override
  public UpsertWebhookResponseDTO upsertWebhook(UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    UpsertWebhookResponseDTO upsertWebhookResponseDTO = null;
    ScmConnector scmConnector = getScmConnector(upsertWebhookRequestDTO.getRepoURL());
    try (AutoLogContext ignore1 = new NgAutoLogContext(upsertWebhookRequestDTO.getProjectIdentifier(),
             upsertWebhookRequestDTO.getOrgIdentifier(), upsertWebhookRequestDTO.getAccountIdentifier(),
             AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      String target = getTargetUrl(upsertWebhookRequestDTO.getAccountIdentifier());
      GitWebhookDetails gitWebhookDetails =
          GitWebhookDetails.builder().hookEventType(upsertWebhookRequestDTO.getHookEventType()).target(target).build();
      CreateWebhookResponse createWebhookResponse = scmClient.upsertWebhook(scmConnector, gitWebhookDetails);
      UpsertWebhookResponseDTO response = UpsertWebhookResponseDTO.builder()
                                              .webhookResponse(createWebhookResponse.getWebhook())
                                              .error(createWebhookResponse.getError())
                                              .status(createWebhookResponse.getStatus())
                                              .build();
      log.info("Upsert Webhook Response : {}", response);
      upsertWebhookResponseDTO = response;
    } catch (Exception exception) {
      log.error("Upsert Webhook Error for accountId: {}, orgId:{}, projectId:{} : ",
          upsertWebhookRequestDTO.getAccountIdentifier(), upsertWebhookRequestDTO.getOrgIdentifier(),
          upsertWebhookRequestDTO.getProjectIdentifier(), exception);
      throw exception;
    }
    return upsertWebhookResponseDTO;
  }

  private String getTargetUrl(String accountIdentifier) {
    String basewebhookUrl = baseUrls.getNgManagerScmBaseUrl();
    if (!basewebhookUrl.endsWith("/")) {
      basewebhookUrl += "/";
    }
    StringBuilder webhookUrl = new StringBuilder(basewebhookUrl)
                                   .append(WebhookConstants.WEBHOOK_ENDPOINT)
                                   .append('?')
                                   .append(NGCommonEntityConstants.ACCOUNT_KEY)
                                   .append('=')
                                   .append(accountIdentifier);
    log.info("The complete webhook url is {}", webhookUrl);
    return webhookUrl.toString();
  }

  private ScmConnector getScmConnector(String repoURL) {
    return HarnessConnectorDTO.builder()
        .connectionType(GitConnectionType.REPO)
        .url(repoURL)
        .apiAccess(getApiAccess())
        .build();
  }

  private HarnessApiAccessDTO getApiAccess() {
    // Using service principal
    String serviceTokenWithDuration = tokenGenerator.getServiceTokenWithDuration(
        ngServiceSecret, Duration.ofHours(4), new ServicePrincipal(NG_MANAGER.getServiceId()));
    return HarnessApiAccessDTO.builder()
        .type(HarnessApiAccessType.JWT_TOKEN)
        .spec(HarnessJWTTokenSpecDTO.builder()
                  .tokenRef(SecretRefData.builder().decryptedValue(serviceTokenWithDuration.toCharArray()).build())
                  .build())
        .build();
  }
}
