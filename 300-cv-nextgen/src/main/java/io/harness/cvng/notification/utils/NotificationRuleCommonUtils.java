/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.utils;

import io.harness.account.AccountClient;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeImpactCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceChangeObservedCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceHealthScoreCondition;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule.MonitoredServiceNotificationRuleCondition;
import io.harness.cvng.notification.entities.SLONotificationRule.SLOErrorBudgetBurnRateCondition;
import io.harness.cvng.notification.entities.SLONotificationRule.SLOErrorBudgetRemainingMinutesCondition;
import io.harness.cvng.notification.entities.SLONotificationRule.SLOErrorBudgetRemainingPercentageCondition;
import io.harness.cvng.notification.entities.SLONotificationRule.SLONotificationRuleCondition;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.remote.client.RestClientUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class NotificationRuleCommonUtils {
  @Inject private AccountClient accountClient;
  @Inject private NextGenService nextGenService;
  @Inject @Named("portalUrl") String portalUrl;
  @Inject private MonitoredServiceService monitoredServiceService;
  public static final Duration COOL_OFF_DURATION = Duration.ofHours(1);
  private static final String themeColor = "#EC372E";
  private static final String outerDiv =
      "<div style=\"margin:15px; padding-left:7px; border-left-width:3px; border-radius:3px; border-left-style:solid; font-size:small; border-left-color:";

  public static long getDurationInMillis(String duration) {
    long lookBackDurationInMillis = 0;
    if (duration != null) {
      Preconditions.checkState(!duration.isEmpty(), "duration can not be empty");
      if (duration.charAt(duration.length() - 1) != 'm') {
        throw new IllegalArgumentException("duration should end with m, ex: 5m, 10m etc.");
      }
      if (duration.charAt(0) == '-') {
        throw new IllegalArgumentException("duration cannot be a negative value");
      }
      duration = duration.substring(0, duration.length() - 1);
      try {
        long lookBackDurationInLong = Long.parseLong(duration);
        lookBackDurationInMillis = TimeUnit.MINUTES.toMillis(lookBackDurationInLong);
      } catch (NumberFormatException numberFormatException) {
        throw new IllegalArgumentException(
            "can not parse duration please check format for duration., ex: 5m, 10m etc.", numberFormatException);
      }
    }
    return lookBackDurationInMillis;
  }

  public static String getDurationAsString(long duration) {
    return TimeUnit.MILLISECONDS.toMinutes(duration) + "m";
  }

  public static String getNotificationTemplateId(
      NotificationRuleType notificationRuleType, CVNGNotificationChannelType notificationChannelType) {
    return String.format("cvng_%s_%s", notificationRuleType.getTemplateSuffixIdentifier().toLowerCase(),
        notificationChannelType.getTemplateSuffixIdentifier().toLowerCase());
  }

  public Map<String, String> getNotificationTemplateDataForSLO(
      ServiceLevelObjective serviceLevelObjective, SLONotificationRuleCondition condition, Instant currentInstant) {
    // TODO: convert below to GMT, currently it is in IST
    long startTime = currentInstant.getEpochSecond();
    String startDate = new Date(startTime * 1000).toString();
    Long endTime = currentInstant.plus(2, ChronoUnit.HOURS).toEpochMilli();
    String vanityUrl = getVanityUrl(serviceLevelObjective.getAccountId());
    String baseUrl = getBaseUrl(getPortalUrl(), vanityUrl);
    String moduleName = "cv";
    String url = String.format("%s/account/%s/%s/orgs/%s/projects/%s/slos/%s?endTime=%s&duration=FOUR_HOURS", baseUrl,
        serviceLevelObjective.getAccountId(), moduleName, serviceLevelObjective.getOrgIdentifier(),
        serviceLevelObjective.getProjectIdentifier(), serviceLevelObjective.getIdentifier(), endTime);

    AccountDTO accountDTO =
        RestClientUtils.getResponse(accountClient.getAccountDTO(serviceLevelObjective.getAccountId()));
    OrganizationDTO organizationDTO =
        nextGenService.getOrganization(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier());
    ProjectDTO projectDTO = nextGenService.getProject(serviceLevelObjective.getAccountId(),
        serviceLevelObjective.getOrgIdentifier(), serviceLevelObjective.getProjectIdentifier());
    MonitoredService monitoredService = monitoredServiceService.getMonitoredService(
        MonitoredServiceParams.builder()
            .accountIdentifier(serviceLevelObjective.getAccountId())
            .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
            .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
            .monitoredServiceIdentifier(serviceLevelObjective.getMonitoredServiceIdentifier())
            .build());
    ServiceResponseDTO serviceResponseDTO =
        nextGenService.getService(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier(),
            serviceLevelObjective.getProjectIdentifier(), monitoredService.getServiceIdentifier());

    String headerMessage = getHeaderMessageForSLO(condition);
    String triggerMessage = getTriggerMessageForSLO(condition);

    return new HashMap<String, String>() {
      {
        put("COLOR", themeColor);
        put("OUTER_DIV", outerDiv);
        put("SLO_NAME", serviceLevelObjective.getName());
        put("HEADER_MESSAGE", headerMessage);
        put("SERVICE_NAME", serviceResponseDTO.getName());
        put("ACCOUNT_NAME", accountDTO.getName());
        put("ORG_NAME", organizationDTO.getName());
        put("PROJECT_NAME", projectDTO.getName());
        put("TRIGGER_MESSAGE", triggerMessage);
        put("START_TS_SECS", String.valueOf(startTime));
        put("START_DATE", startDate);
        put("URL", url);
      }
    };
  }

  public Map<String, String> getNotificationTemplateDataForMonitoredService(
      MonitoredService monitoredService, MonitoredServiceNotificationRuleCondition condition, Instant currentInstant) {
    // TODO: convert below to GMT, currently it is in IST
    long startTime = currentInstant.getEpochSecond();
    String startDate = new Date(startTime * 1000).toString();
    Long endTime = currentInstant.plus(2, ChronoUnit.HOURS).toEpochMilli();
    String vanityUrl = getVanityUrl(monitoredService.getAccountId());
    String baseUrl = getBaseUrl(getPortalUrl(), vanityUrl);
    String moduleName = "cv";
    String url = String.format(
        "%s/account/%s/%s/orgs/%s/projects/%s/monitoringservices/edit/%s?tab=ServiceHealth&endTime=%s&duration=FOUR_HOURS",
        baseUrl, monitoredService.getAccountId(), moduleName, monitoredService.getOrgIdentifier(),
        monitoredService.getProjectIdentifier(), monitoredService.getIdentifier(), endTime);

    AccountDTO accountDTO = RestClientUtils.getResponse(accountClient.getAccountDTO(monitoredService.getAccountId()));
    OrganizationDTO organizationDTO =
        nextGenService.getOrganization(monitoredService.getAccountId(), monitoredService.getOrgIdentifier());
    ProjectDTO projectDTO = nextGenService.getProject(
        monitoredService.getAccountId(), monitoredService.getOrgIdentifier(), monitoredService.getProjectIdentifier());
    ServiceResponseDTO serviceResponseDTO =
        nextGenService.getService(monitoredService.getAccountId(), monitoredService.getOrgIdentifier(),
            monitoredService.getProjectIdentifier(), monitoredService.getServiceIdentifier());

    String headerMessage = getHeaderMessageForMonitoredService(condition);
    String triggerMessage = getTriggerMessageForMonitoredService(condition);

    return new HashMap<String, String>() {
      {
        put("COLOR", themeColor);
        put("OUTER_DIV", outerDiv);
        put("MONITORED_SERVICE_NAME", monitoredService.getName());
        put("HEADER_MESSAGE", headerMessage);
        put("SERVICE_NAME", serviceResponseDTO.getName());
        put("ACCOUNT_NAME", accountDTO.getName());
        put("ORG_NAME", organizationDTO.getName());
        put("PROJECT_NAME", projectDTO.getName());
        put("TRIGGER_MESSAGE", triggerMessage);
        put("START_TS_SECS", String.valueOf(startTime));
        put("START_DATE", startDate);
        put("URL", url);
      }
    };
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

  private String getHeaderMessageForSLO(SLONotificationRuleCondition condition) {
    switch (condition.getType()) {
      case ERROR_BUDGET_REMAINING_PERCENTAGE:
        SLOErrorBudgetRemainingPercentageCondition remainingPercentageCondition =
            (SLOErrorBudgetRemainingPercentageCondition) condition;
        return "error budget remains less than " + remainingPercentageCondition.getThreshold() + "% for";
      case ERROR_BUDGET_REMAINING_MINUTES:
        SLOErrorBudgetRemainingMinutesCondition remainingMinutesCondition =
            (SLOErrorBudgetRemainingMinutesCondition) condition;
        return "error budget remains less than " + remainingMinutesCondition.getThreshold() + " minutes for";
      case ERROR_BUDGET_BURN_RATE:
        SLOErrorBudgetBurnRateCondition burnRateCondition = (SLOErrorBudgetBurnRateCondition) condition;
        return "current burn rate is " + burnRateCondition.getThreshold() + "% for";
      default:
        throw new InvalidArgumentsException("Not a valid Notification Rule Condition " + condition.getType());
    }
  }

  private String getTriggerMessageForSLO(SLONotificationRuleCondition condition) {
    switch (condition.getType()) {
      case ERROR_BUDGET_REMAINING_PERCENTAGE:
        SLOErrorBudgetRemainingPercentageCondition remainingPercentageCondition =
            (SLOErrorBudgetRemainingPercentageCondition) condition;
        return "When Error Budget remaining percentage drops below " + remainingPercentageCondition.getThreshold()
            + "%";
      case ERROR_BUDGET_REMAINING_MINUTES:
        SLOErrorBudgetRemainingMinutesCondition remainingMinutesCondition =
            (SLOErrorBudgetRemainingMinutesCondition) condition;
        return "When Error Budget remaining minutes drops below " + remainingMinutesCondition.getThreshold()
            + " minutes";
      case ERROR_BUDGET_BURN_RATE:
        SLOErrorBudgetBurnRateCondition burnRateCondition = (SLOErrorBudgetBurnRateCondition) condition;
        return "When Error Budget burn rate drops below " + burnRateCondition.getThreshold() + "% in the last "
            + getDurationAsString(burnRateCondition.getLookBackDuration()) + " minutes";
      default:
        throw new InvalidArgumentsException("Not a valid Notification Rule Condition " + condition.getType());
    }
  }

  private String getHeaderMessageForMonitoredService(MonitoredServiceNotificationRuleCondition condition) {
    switch (condition.getType()) {
      case CHANGE_IMPACT:
        MonitoredServiceChangeImpactCondition changeImpactCondition = (MonitoredServiceChangeImpactCondition) condition;
        return "health score drops below " + changeImpactCondition.getThreshold() + " for";
      case HEALTH_SCORE:
        MonitoredServiceHealthScoreCondition healthScoreCondition = (MonitoredServiceHealthScoreCondition) condition;
        return "health score drops below " + healthScoreCondition.getThreshold() + " for";
      case CHANGE_OBSERVED:
        MonitoredServiceChangeObservedCondition changeObservedCondition =
            (MonitoredServiceChangeObservedCondition) condition;
        return "observed a change in a " + changeObservedCondition.getChangeEventTypes().get(0).getDisplayName()
            + " for";
      default:
        throw new InvalidArgumentsException("Not a valid Notification Rule Condition " + condition.getType());
    }
  }

  private String getTriggerMessageForMonitoredService(MonitoredServiceNotificationRuleCondition condition) {
    switch (condition.getType()) {
      case CHANGE_IMPACT:
        MonitoredServiceChangeImpactCondition changeImpactCondition = (MonitoredServiceChangeImpactCondition) condition;
        return "When service health score drops below " + changeImpactCondition.getThreshold() + " for longer than "
            + getDurationAsString(changeImpactCondition.getPeriod()) + " minutes due to a change in "
            + changeImpactCondition.getChangeEventTypes().get(0);
      case HEALTH_SCORE:
        MonitoredServiceHealthScoreCondition healthScoreCondition = (MonitoredServiceHealthScoreCondition) condition;
        return "When service health score drops below " + healthScoreCondition.getThreshold() + " for longer than "
            + getDurationAsString(healthScoreCondition.getPeriod()) + " minutes";
      case CHANGE_OBSERVED:
        MonitoredServiceChangeObservedCondition changeObservedCondition =
            (MonitoredServiceChangeObservedCondition) condition;
        return "When a change observed in a " + changeObservedCondition.getChangeEventTypes().get(0).getDisplayName();
      default:
        throw new InvalidArgumentsException("Not a valid Notification Rule Condition " + condition.getType());
    }
  }
}
