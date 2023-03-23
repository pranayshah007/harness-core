/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.services.api;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.ACCOUNT_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ANOMALOUS_METRIC;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.COLOR;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.HEADER_MESSAGE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ORG_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PROJECT_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SERVICE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.START_DATE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.START_TS_SECS;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.THEME_COLOR;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.TRIGGER_MESSAGE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.URL;

import io.harness.account.AccountClient;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.NotificationRuleConditionEntity;
import io.harness.cvng.notification.utils.NotificationRuleCommonUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;

public abstract class NotificationRuleTemplateDataGenerator<T extends NotificationRuleConditionEntity> {
  @Inject private AccountClient accountClient;
  @Inject private NextGenService nextGenService;
  @Inject @Named("portalUrl") String portalUrl;
  @Inject protected Clock clock;

  public Map<String, String> getTemplateData(ProjectParams projectParams, String name, String identifier,
      String serviceIdentifier, String monitoredServiceIdentifier, T condition,
      Map<String, String> notificationDataMap) {
    Instant currentInstant = clock.instant();
    long startTime = currentInstant.getEpochSecond();
    long startTimeInMillis = startTime * 1000;
    String startDate = new Date(startTime * 1000).toString();
    Long endTime = currentInstant.toEpochMilli();
    String vanityUrl = getVanityUrl(projectParams.getAccountIdentifier());
    String baseUrl = getBaseUrl(getPortalUrl(), vanityUrl);

    AccountDTO accountDTO = CGRestUtils.getResponse(accountClient.getAccountDTO(projectParams.getAccountIdentifier()));
    OrganizationDTO organizationDTO =
        nextGenService.getOrganization(projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier());
    ProjectDTO projectDTO = nextGenService.getProject(
        projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier());
    ServiceResponseDTO serviceResponseDTO = null;
    if (serviceIdentifier != null) {
      serviceResponseDTO = nextGenService.getService(projectParams.getAccountIdentifier(),
          projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), serviceIdentifier);
    }
    String serviceName = Objects.isNull(serviceResponseDTO) ? null : serviceResponseDTO.getName();

    return new HashMap<String, String>() {
      {
        put(COLOR, THEME_COLOR);
        put(SERVICE_NAME, serviceName);
        put(ACCOUNT_NAME, accountDTO.getName());
        put(ORG_NAME, organizationDTO.getName());
        put(PROJECT_NAME, projectDTO.getName());
        put(START_TS_SECS, String.valueOf(startTime));
        put(START_DATE, startDate);
        put(URL, getUrl(baseUrl, projectParams, identifier, condition.getType().getNotificationRuleType(), endTime));
        put(getEntityName(), name);
        put(HEADER_MESSAGE, getHeaderMessage(notificationDataMap));
        put(TRIGGER_MESSAGE, getTriggerMessage(condition));
        put(ANOMALOUS_METRIC, getAnomalousMetrics(projectParams, identifier, startTimeInMillis, condition));
      }
    };
  }

  protected abstract String getEntityName();
  protected abstract String getUrl(
      String baseUrl, ProjectParams projectParams, String identifier, NotificationRuleType type, Long endTime);
  protected abstract String getHeaderMessage(Map<String, String> notificationDataMap);
  protected abstract String getTriggerMessage(T condition);
  protected abstract String getAnomalousMetrics(
      ProjectParams projectParams, String identifier, long startTime, T condition);

  protected String getPortalUrl() {
    return portalUrl.concat("ng/#");
  }

  protected String getVanityUrl(String accountIdentifier) {
    return CGRestUtils.getResponse(accountClient.getVanityUrl(accountIdentifier));
  }

  protected static String getBaseUrl(String defaultBaseUrl, String vanityUrl) {
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

  public String getTemplateId(
      NotificationRuleType notificationRuleType, CVNGNotificationChannelType notificationChannelType) {
    return NotificationRuleCommonUtils.getNotificationTemplateId(notificationRuleType, notificationChannelType);
  }

  @Value
  @Builder
  public static class NotificationData {
    @Accessors(fluent = true) boolean shouldSendNotification;
    Map<String, String> templateDataMap;
  }
}
