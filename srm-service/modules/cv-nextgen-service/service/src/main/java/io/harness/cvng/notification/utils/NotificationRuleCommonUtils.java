/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.utils;

import static io.harness.cvng.notification.utils.NotificationRuleConstants.CURRENT_SLO_TARGET;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ERROR_BUDGET_BURNED;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MODULE_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.NO_SLO_ASSOCIATED_WITH_MONITORED_SERVICE;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PAST_SLO_TARGET;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.PROJECT_SIMPLE_SLO_URL_FORMAT;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_TARGET;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SLO_URL;

import io.harness.cvng.core.beans.change.MSHealthReport;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ResourceParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.utils.ScopedInformation;

import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class NotificationRuleCommonUtils {
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

  public static long getDurationInSeconds(long durationInMillis) {
    return durationInMillis / (1000 * 60);
  }

  public static String getDurationAsString(long duration) {
    return TimeUnit.MILLISECONDS.toMinutes(duration) + "m";
  }

  public static String getDurationAsStringWithoutSuffix(long duration) {
    return String.valueOf(TimeUnit.MILLISECONDS.toMinutes(duration));
  }

  public static String getNotificationTemplateId(
      NotificationRuleType notificationRuleType, CVNGNotificationChannelType notificationChannelType) {
    return String.format("cvng_%s_%s", notificationRuleType.getTemplateSuffixIdentifier().toLowerCase(),
        notificationChannelType.getTemplateSuffixIdentifier().toLowerCase());
  }

  public static String getNotificationTemplateId(NotificationRuleType notificationRuleType,
      ServiceLevelObjectiveType serviceLevelObjectiveType, String scope,
      CVNGNotificationChannelType notificationChannelType) {
    return String.format("cvng_%s_%s_%s_%s", notificationRuleType.getTemplateSuffixIdentifier().toLowerCase(),
        serviceLevelObjectiveType.toString().toLowerCase(), scope,
        notificationChannelType.getTemplateSuffixIdentifier().toLowerCase());
  }

  public static String getSloPerformanceSectionForReport(
      List<MSHealthReport.AssociatedSLOsDetails> associatedSLOsDetails, Instant currentInstant, String baseUrl,
      String SLOPerformanceSectionTemplate) {
    StringBuilder sb = new StringBuilder();

    associatedSLOsDetails.forEach(sloDetails -> {
      ResourceParams resourceParams =
          ScopedInformation.getResourceParamsFromScopedIdentifier(sloDetails.getScopedMonitoredServiceIdentifier());
      Map<String, String> templateDataMap = new HashMap<>() {
        {
          put(SLO_URL, getAssociatedSLOUrl(resourceParams, sloDetails.getIdentifier(), currentInstant, baseUrl));
          put(SLO_NAME, sloDetails.getName());
          put(SLO_TARGET, sloDetails.getSloTarget().toString());
          put(PAST_SLO_TARGET, String.format("%.2f", sloDetails.getPastSLOPerformance()));
          put(CURRENT_SLO_TARGET, String.format("%.2f", sloDetails.getCurrentSLOPerformance()));
          put(ERROR_BUDGET_BURNED, String.format("%.2f", sloDetails.getErrorBudgetBurned()));
        }
      };
      final String[] sloPerformanceSection = {SLOPerformanceSectionTemplate};
      templateDataMap.forEach((key, value) -> {
        String variable = String.format("${%s}", key);
        sloPerformanceSection[0] = sloPerformanceSection[0].replace(variable, value);
      });
      sb.append(sloPerformanceSection[0]);
    });

    return sb.toString();
  }

  private static String getAssociatedSLOUrl(
      ProjectParams projectParams, String identifier, Instant currentInstant, String baseUrl) {
    return String.format(PROJECT_SIMPLE_SLO_URL_FORMAT, baseUrl, projectParams.getAccountIdentifier(), MODULE_NAME,
        projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier(), identifier,
        currentInstant.toEpochMilli());
  }

  public static String getServiceHealthMessageForReport(RiskData currentHealthScore) {
    switch (currentHealthScore.getRiskStatus()) {
      case HEALTHY:
        return String.format(
            "The service health remained healthy with a score of %s%%", currentHealthScore.getHealthScore());
      case OBSERVE:
        return String.format(
            "The service health needs to be observed. It has a score of %s%%", currentHealthScore.getHealthScore());
      case NEED_ATTENTION:
        return String.format(
            "The service health needs attention. It has a score of %s%%", currentHealthScore.getHealthScore());
      case UNHEALTHY:
        return String.format(
            "The service health remained unhealthy with a score of %s%%", currentHealthScore.getHealthScore());
      case NO_DATA:
      case NO_ANALYSIS:
      default:
        return "No health score data available for the period";
    }
  }

  public static String getSLOSummaryForReport(List<MSHealthReport.AssociatedSLOsDetails> associatedSLOsDetails) {
    int countOfSLOsWithErrorBudgetDepleted = 0;
    for (MSHealthReport.AssociatedSLOsDetails sloDetail : associatedSLOsDetails) {
      if (sloDetail.getErrorBudgetRemaining() < 0) {
        countOfSLOsWithErrorBudgetDepleted += 1;
      }
    }
    if (associatedSLOsDetails.isEmpty()) {
      return NO_SLO_ASSOCIATED_WITH_MONITORED_SERVICE;
    } else if (countOfSLOsWithErrorBudgetDepleted == associatedSLOsDetails.size()) {
      return String.format(
          "%s%s SLO%s configured for this service couldn't maintain their respective error budget%s.\n",
          associatedSLOsDetails.size() > 1 ? "All " : "", associatedSLOsDetails.size(),
          associatedSLOsDetails.size() > 1 ? "s" : "", associatedSLOsDetails.size() > 1 ? "s" : "");
    } else if (countOfSLOsWithErrorBudgetDepleted > 0) {
      return String.format(
          "Out of the %s SLO%s configured for this service, %s of them have exceeded their respective error budget%s.\n",
          associatedSLOsDetails.size(), associatedSLOsDetails.size() > 1 ? "s" : "", countOfSLOsWithErrorBudgetDepleted,
          countOfSLOsWithErrorBudgetDepleted > 1 ? "s" : "");
    } else {
      return String.format(
          "%s%s SLO%s configured for this service successfully maintained their error budget%s intact.\n",
          associatedSLOsDetails.size() > 1 ? "All " : "", associatedSLOsDetails.size(),
          associatedSLOsDetails.size() > 1 ? "s" : "", associatedSLOsDetails.size() > 1 ? "s" : "");
    }
  }
}
