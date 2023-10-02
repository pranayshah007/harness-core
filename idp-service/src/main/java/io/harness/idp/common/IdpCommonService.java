/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.client.NgConnectorManagerClient;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.SlackChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.remote.client.CGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpCommonService {
  @Inject NgConnectorManagerClient ngConnectorManagerClient;
  @Inject @Named("env") private String env;
  @Inject AccountClient accountClient;
  @Inject NotificationClient notificationClient;

  public void checkUserAuthorization() {
    String userId = SecurityContextBuilder.getPrincipal().getName();
    boolean isAuthorized = getResponse(ngConnectorManagerClient.isHarnessSupportUser(userId));
    if (!isAuthorized) {
      String errorMessage = String.format("User : %s not allowed to do action on IDP module", userId);
      log.error(errorMessage);
      throw new AccessDeniedException(errorMessage, WingsException.USER);
    }
  }

  public <T> Response buildPageResponse(int pageIndex, int pageLimit, long totalElements, T response) {
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, totalElements, pageIndex, pageLimit);
    return responseBuilderWithLinks.entity(response).build();
  }

  public void sendSlackNotification(SlackChannel slackChannel) {
    sendNotification(slackChannel);
  }

  public void sendNotification(NotificationChannel notificationChannel) {
    try {
      AccountDTO accountDTO = getAccountDTO(notificationChannel.getAccountId());
      notificationChannel.getTemplateData().put("accountName", accountDTO.getName());
      notificationChannel.getTemplateData().put("env", env);
      log.info("Sending notification - accountIdentifier = {}, templateId = {}", notificationChannel.getAccountId(),
          notificationChannel.getTemplateId());
      notificationClient.sendNotificationAsync(notificationChannel);
      log.info("Sent notification - accountIdentifier = {}, templateId = {}", notificationChannel.getAccountId(),
          notificationChannel.getTemplateId());
    } catch (Exception ex) {
      log.error("Error in sending notification - accountIdentifier = {}, templateId = {}, error = {}",
          notificationChannel.getAccountId(), notificationChannel.getTemplateId(), ex.getMessage(), ex);
    }
  }

  public AccountDTO getAccountDTO(String accountIdentifier) {
    return CGRestUtils.getResponse(accountClient.getAccountDTO(accountIdentifier));
  }
}
