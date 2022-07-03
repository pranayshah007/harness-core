/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.api;

import io.harness.account.AccountClient;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.entities.NotificationRuleConditionEntity;
import io.harness.cvng.notification.utils.NotificationRuleCommonUtils.NotificationMessage;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.remote.client.RestClientUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public abstract class NotificationRuleTemplateDataGenerator<T extends NotificationRuleConditionEntity> {
  @Inject private AccountClient accountClient;
  @Inject private NextGenService nextGenService;
  @Inject @Named("portalUrl") String portalUrl;
  @Inject private Clock clock;

  private static final String themeColor = "#EC372E";
  private static final String moduleName = "cv";

  public abstract Map<String, String> getTemplateData(ProjectParams projectParams, String name, String identifier,
      String serviceIdentifier, T condition, NotificationMessage notificationMessage);

  protected Map<String, String> getCommonTemplateData(
      ProjectParams projectParams, String identifier, String serviceIdentifier, T condition) {
    Instant currentInstant = clock.instant();
    long startTime = currentInstant.getEpochSecond();
    String startDate = new Date(startTime * 1000).toString();
    Long endTime = currentInstant.plus(2, ChronoUnit.HOURS).toEpochMilli();
    String vanityUrl = getVanityUrl(projectParams.getAccountIdentifier());
    String baseUrl = getBaseUrl(getPortalUrl(), vanityUrl);
    String url = getUrl(baseUrl, projectParams, identifier, condition.getType().getNotificationRuleType(), endTime);

    AccountDTO accountDTO =
        RestClientUtils.getResponse(accountClient.getAccountDTO(projectParams.getAccountIdentifier()));
    OrganizationDTO organizationDTO =
        nextGenService.getOrganization(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier());
    ProjectDTO projectDTO = nextGenService.getProject(
        projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier());
    ServiceResponseDTO serviceResponseDTO = nextGenService.getService(projectParams.getAccountIdentifier(),
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), serviceIdentifier);

    return new HashMap<String, String>() {
      {
        put("COLOR", themeColor);
        // put("MONITORED_SERVICE_NAME", name);
        // put("HEADER_MESSAGE", headerMessage);
        put("SERVICE_NAME", serviceResponseDTO.getName());
        put("ACCOUNT_NAME", accountDTO.getName());
        put("ORG_NAME", organizationDTO.getName());
        put("PROJECT_NAME", projectDTO.getName());
        // put("TRIGGER_MESSAGE", triggerMessage);
        put("START_TS_SECS", String.valueOf(startTime));
        put("START_DATE", startDate);
        put("URL", url);
      }
    };
  }

  private String getUrl(
      String baseUrl, ProjectParams projectParams, String identifier, NotificationRuleType type, Long endTime) {
    switch (type) {
      case MONITORED_SERVICE:
        return String.format(
            "%s/account/%s/%s/orgs/%s/projects/%s/monitoringservices/edit/%s?tab=ServiceHealth&endTime=%s&duration=FOUR_HOURS",
            baseUrl, projectParams.getAccountIdentifier(), moduleName, projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier(), identifier, endTime);
      case SLO:
        return String.format("%s/account/%s/%s/orgs/%s/projects/%s/slos/%s?endTime=%s&duration=FOUR_HOURS", baseUrl,
            projectParams.getAccountIdentifier(), moduleName, projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier(), identifier, endTime);
      default:
        throw new InvalidArgumentsException("Invalid Notification Rule Type: " + type);
    }
  }

  private String getPortalUrl() {
    return portalUrl.concat("ng/#");
  }

  private String getVanityUrl(String accountIdentifier) {
    return RestClientUtils.getResponse(accountClient.getVanityUrl(accountIdentifier));
  }

  private static String getBaseUrl(String defaultBaseUrl, String vanityUrl) {
    // e.g Prod Default Base URL - 'https://app.harness.io/ng/#'
    if (EmptyPredicate.isEmpty(vanityUrl)) {
      return defaultBaseUrl;
    }
    String newBaseUrl = vanityUrl;
    if (vanityUrl.endsWith("/")) {
      newBaseUrl = vanityUrl.substring(0, vanityUrl.length() - 1);
    }
    try {
      URL url = new URL(defaultBaseUrl);
      String hostUrl = String.format("%s://%s", url.getProtocol(), url.getHost());
      return newBaseUrl + defaultBaseUrl.substring(hostUrl.length());
    } catch (Exception ex) {
      throw new IllegalStateException("There was error while generating vanity URL", ex);
    }
  }
}
