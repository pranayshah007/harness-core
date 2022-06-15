/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.utils;

import io.harness.account.AccountClient;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.data.structure.EmptyPredicate;
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
  @Inject @Named("portalUrl") String portalUrl;
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

  public Map<String, String> getNotificationTemplateDataForSLO(ServiceLevelObjective serviceLevelObjective,
      String notificationRuleName, String conditionName, Instant currentInstant) {
    long startTime = currentInstant.getEpochSecond();
    String startDate = new Date(startTime * 1000).toString();
    Long endTime = currentInstant.plus(2, ChronoUnit.HOURS).toEpochMilli();
    String vanityUrl = getVanityUrl(serviceLevelObjective.getAccountId());
    String baseUrl = getBaseUrl(getPortalUrl(), vanityUrl);
    String moduleName = "cv";
    String url = String.format("%s/account/%s/%s/orgs/%s/projects/%s/slos/%s?endTime=%s&duration=FOUR_HOURS", baseUrl,
        serviceLevelObjective.getAccountId(), moduleName, serviceLevelObjective.getOrgIdentifier(),
        serviceLevelObjective.getProjectIdentifier(), serviceLevelObjective.getIdentifier(), endTime);
    return new HashMap<String, String>() {
      {
        put("COLOR", themeColor);
        put("OUTER_DIV", outerDiv);
        put("SLO_NAME", serviceLevelObjective.getName());
        put("ACCOUNT_ID", serviceLevelObjective.getAccountId());
        put("ORG_ID", serviceLevelObjective.getOrgIdentifier());
        put("PROJECT_ID", serviceLevelObjective.getProjectIdentifier());
        put("RULE_NAME", notificationRuleName);
        put("CONDITION_NAME", conditionName);
        put("START_TS_SECS", String.valueOf(startTime));
        put("START_DATE", startDate);
        put("URL", url);
      }
    };
  }

  public Map<String, String> getNotificationTemplateDataForMonitoredService(
      MonitoredService monitoredService, String notificationRuleName, String conditionName, Instant currentInstant) {
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
    return new HashMap<String, String>() {
      {
        put("COLOR", themeColor);
        put("OUTER_DIV", outerDiv);
        put("SERVICE_NAME", monitoredService.getServiceIdentifier());
        put("ENVIRONMENT_NAME", monitoredService.getEnvironmentIdentifier());
        put("ACCOUNT_ID", monitoredService.getAccountId());
        put("ORG_ID", monitoredService.getOrgIdentifier());
        put("PROJECT_ID", monitoredService.getProjectIdentifier());
        put("RULE_NAME", notificationRuleName);
        put("CONDITION_NAME", conditionName);
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
}
