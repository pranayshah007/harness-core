/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.notifications;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Objects.isNull;

import io.harness.freeze.beans.FreezeDuration;
import io.harness.freeze.beans.FreezeEvent;
import io.harness.freeze.beans.FreezeNotificationChannelWrapper;
import io.harness.freeze.beans.FreezeNotifications;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.helpers.FreezeTimeUtils;
import io.harness.freeze.mappers.NGFreezeDtoMapper;
import io.harness.notification.FreezeEventType;
import io.harness.notification.channelDetails.PmsEmailChannel;
import io.harness.notification.channelDetails.PmsMSTeamChannel;
import io.harness.notification.channelDetails.PmsNotificationChannel;
import io.harness.notification.channelDetails.PmsPagerDutyChannel;
import io.harness.notification.channelDetails.PmsSlackChannel;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.sanitizer.HtmlInputSanitizer;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.api.client.util.ArrayMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class NotificationHelper {
  @Inject NotificationClient notificationClient;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private HtmlInputSanitizer userNameSanitizer;

  public void sendNotification(String yaml, boolean pipelineRejectedNotification, boolean freezeWindowNotification,
      Ambiance ambiance, String accountId, String executionUrl, String baseUrl, boolean globalFreeze)
      throws IOException {
    FreezeConfig freezeConfig = NGFreezeDtoMapper.toFreezeConfig(yaml);
    FreezeInfoConfig freezeInfoConfig = freezeConfig.getFreezeInfoConfig();
    if (freezeInfoConfig == null || freezeInfoConfig.getNotifications() == null) {
      return;
    }
    for (FreezeNotifications freezeNotifications : freezeInfoConfig.getNotifications()) {
      if (!freezeNotifications.isEnabled()) {
        continue;
      }
      FreezeNotificationChannelWrapper wrapper = freezeNotifications.getNotificationChannelWrapper().getValue();
      wrapper.setNotificationChannel(updateNullUserGroupWithEmptyList(wrapper.getNotificationChannel()));
      if (wrapper.getType() != null) {
        for (FreezeEvent freezeEvent : freezeNotifications.getEvents()) {
          String templateId = getNotificationTemplate(wrapper.getType(), freezeEvent);
          if (freezeEvent.getType().equals(FreezeEventType.FREEZE_WINDOW_ENABLED) && !freezeWindowNotification) {
            continue;
          }
          if (freezeEvent.getType().equals(FreezeEventType.DEPLOYMENT_REJECTED_DUE_TO_FREEZE)
              && !pipelineRejectedNotification) {
            continue;
          }
          Map<String, String> notificationContent = constructTemplateData(freezeEvent.getType(), freezeInfoConfig,
              ambiance, accountId, executionUrl, baseUrl, globalFreeze, freezeNotifications);
          NotificationChannel channel = wrapper.getNotificationChannel().toNotificationChannel(accountId,
              freezeInfoConfig.getOrgIdentifier(), freezeInfoConfig.getProjectIdentifier(), templateId,
              notificationContent, Ambiance.newBuilder().setExpressionFunctorToken(0).build());
          try {
            notificationClient.sendNotificationAsync(channel);
          } catch (Exception ex) {
            log.error("Failed to send notification ", ex);
          }
        }
      }
    }
  }

  private PmsNotificationChannel updateNullUserGroupWithEmptyList(PmsNotificationChannel notificationChannel) {
    if (notificationChannel instanceof PmsEmailChannel) {
      List<String> userGroups = ((PmsEmailChannel) notificationChannel).getUserGroups();
      if (isNull(userGroups)) {
        ((PmsEmailChannel) notificationChannel).setUserGroups(Collections.emptyList());
      }
    }
    if (notificationChannel instanceof PmsSlackChannel) {
      List<String> userGroups = ((PmsSlackChannel) notificationChannel).getUserGroups();
      if (isNull(userGroups)) {
        ((PmsSlackChannel) notificationChannel).setUserGroups(Collections.emptyList());
      }
    }
    if (notificationChannel instanceof PmsPagerDutyChannel) {
      List<String> userGroups = ((PmsPagerDutyChannel) notificationChannel).getUserGroups();
      if (isNull(userGroups)) {
        ((PmsPagerDutyChannel) notificationChannel).setUserGroups(Collections.emptyList());
      }
    } else if (notificationChannel instanceof PmsMSTeamChannel) {
      List<String> userGroups = ((PmsMSTeamChannel) notificationChannel).getUserGroups();
      if (isNull(userGroups)) {
        ((PmsMSTeamChannel) notificationChannel).setUserGroups(Collections.emptyList());
      }
    }
    return notificationChannel;
  }

  public Map<String, String> constructTemplateData(FreezeEventType freezeEventType, FreezeInfoConfig freezeInfoConfig,
      Ambiance ambiance, String accountId, String executionUrl, String baseUrl, boolean globalFreeze,
      FreezeNotifications freezeNotifications) {
    Map<String, String> data = new ArrayMap<>();
    if (globalFreeze) {
      data.put("BLACKOUT_WINDOW_URL", getGlobalFreezeUrl(baseUrl, freezeInfoConfig, accountId));
    } else {
      data.put("BLACKOUT_WINDOW_URL", getManualFreezeUrl(baseUrl, freezeInfoConfig, accountId));
    }
    data.put("BLACKOUT_WINDOW_NAME", freezeInfoConfig.getName());
    if (freezeInfoConfig.getWindows().size() > 0) {
      TimeZone timeZone = TimeZone.getTimeZone(freezeInfoConfig.getWindows().get(0).getTimeZone());
      LocalDateTime firstWindowStartTime =
          LocalDateTime.parse(freezeInfoConfig.getWindows().get(0).getStartTime(), FreezeTimeUtils.dtf);
      LocalDateTime firstWindowEndTime;
      if (freezeInfoConfig.getWindows().get(0).getEndTime() == null) {
        FreezeDuration freezeDuration = FreezeDuration.fromString(freezeInfoConfig.getWindows().get(0).getDuration());
        Long endTime = FreezeTimeUtils.getEpochValueFromDateString(firstWindowStartTime, timeZone)
            + freezeDuration.getTimeoutInMillis();
        firstWindowEndTime = Instant.ofEpochMilli(endTime).atZone(timeZone.toZoneId()).toLocalDateTime();
      } else {
        firstWindowEndTime =
            LocalDateTime.parse(freezeInfoConfig.getWindows().get(0).getEndTime(), FreezeTimeUtils.dtf);
      }
      Pair<LocalDateTime, LocalDateTime> windowTimes = Pair.of(firstWindowStartTime, firstWindowEndTime);
      if (freezeInfoConfig.getWindows().get(0).getRecurrence() != null
          && freezeInfoConfig.getWindows().get(0).getRecurrence().getRecurrenceType() != null) {
        windowTimes = FreezeTimeUtils.setCurrWindowStartAndEndTime(firstWindowStartTime, firstWindowEndTime,
            freezeInfoConfig.getWindows().get(0).getRecurrence().getRecurrenceType(), timeZone);
      }
      data.put("START_TIME", windowTimes.getLeft().toString());
      data.put("END_TIME", windowTimes.getRight().toString());
      data.put("ACCOUNT_ID", accountId);
    }
    if (freezeEventType.equals(FreezeEventType.DEPLOYMENT_REJECTED_DUE_TO_FREEZE) && ambiance != null) {
      data.put("USER_NAME",
          userNameSanitizer.sanitizeInput(ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier()));
      data.put("WORKFLOW_NAME", ambiance.getMetadata().getPipelineIdentifier());
      data.put("WORKFLOW_URL", executionUrl);
    }
    data.put("CUSTOMIZED_MESSAGE", getCustomizeMessage(freezeNotifications));
    return data;
  }

  private String getCustomizeMessage(FreezeNotifications freezeNotifications) {
    if (isNotEmpty(freezeNotifications.getCustomizedMessage())) {
      return " " + freezeNotifications.getCustomizedMessage();
    }
    return "";
  }
  private String getNotificationTemplate(String channelType, FreezeEvent freezeEvent) {
    if (freezeEvent.getType().equals(FreezeEventType.DEPLOYMENT_REJECTED_DUE_TO_FREEZE)) {
      return String.format("pipeline_rejected_%s_alert", channelType.toLowerCase());
    }
    return String.format("freeze_%s_alert", channelType.toLowerCase());
  }

  public void sendNotificationForFreezeConfigs(List<FreezeSummaryResponseDTO> manualFreezeConfigs,
      List<FreezeSummaryResponseDTO> globalFreezeConfigs, Ambiance ambiance, String executionUrl, String baseUrl) {
    for (FreezeSummaryResponseDTO freezeSummaryResponseDTO : globalFreezeConfigs) {
      if (freezeSummaryResponseDTO.getYaml() != null) {
        try {
          sendNotification(freezeSummaryResponseDTO.getYaml(), true, false, ambiance,
              freezeSummaryResponseDTO.getAccountId(), executionUrl, baseUrl, true);
        } catch (Exception e) {
          log.info("Unable to send pipeline rejected notifications for global freeze", e);
        }
      }
    }
    for (FreezeSummaryResponseDTO freezeSummaryResponseDTO : manualFreezeConfigs) {
      if (freezeSummaryResponseDTO.getYaml() != null) {
        try {
          sendNotification(freezeSummaryResponseDTO.getYaml(), true, false, ambiance,
              freezeSummaryResponseDTO.getAccountId(), executionUrl, baseUrl, false);
        } catch (Exception e) {
          log.info("Unable to send pipeline rejected notifications for manual freeze", e);
        }
      }
    }
  }

  public String getManualFreezeUrl(String baseUrl, FreezeInfoConfig freezeInfoConfig, String accountId) {
    String freezeUrl = "";
    if (freezeInfoConfig != null) {
      String orgId = freezeInfoConfig.getOrgIdentifier();
      String projectId = freezeInfoConfig.getProjectIdentifier();
      return getManualFreezeUrl(baseUrl, accountId, orgId, projectId, freezeInfoConfig.getIdentifier());
    }
    return freezeUrl;
  }

  public String getManualFreezeUrl(
      String baseUrl, String accountId, String orgId, String projectId, String identifier) {
    String freezeUrl = "";
    if (accountId != null) {
      if (orgId != null) {
        if (projectId != null) {
          freezeUrl = String.format("%s/account/%s/cd/orgs/%s/projects/%s/setup/freeze-window-studio/window/%s",
              baseUrl, accountId, orgId, projectId, identifier);
        } else {
          freezeUrl = String.format("%s/account/%s/settings/organizations/%s/setup/freeze-window-studio/window/%s",
              baseUrl, accountId, orgId, identifier);
        }
      } else {
        freezeUrl =
            String.format("%s/account/%s/settings/freeze-window-studio/window/%s", baseUrl, accountId, identifier);
      }
    }
    return freezeUrl;
  }

  public String getGlobalFreezeUrl(String baseUrl, FreezeInfoConfig freezeInfoConfig, String accountId) {
    String freezeUrl = "";
    if (freezeInfoConfig != null) {
      String orgId = freezeInfoConfig.getOrgIdentifier();
      String projectId = freezeInfoConfig.getProjectIdentifier();
      return getGlobalFreezeUrl(baseUrl, accountId, orgId, projectId);
    }
    return freezeUrl;
  }

  public String getGlobalFreezeUrl(String baseUrl, String accountId, String orgId, String projectId) {
    String freezeUrl = "";
    if (accountId != null) {
      if (orgId != null) {
        if (projectId != null) {
          freezeUrl = String.format(
              "%s/account/%s/cd/orgs/%s/projects/%s/setup/freeze-windows", baseUrl, accountId, orgId, projectId);
        } else {
          freezeUrl =
              String.format("%s/account/%s/settings/organizations/%s/setup/freeze-windows", baseUrl, accountId, orgId);
        }
      } else {
        freezeUrl = String.format("%s/account/%s/settings/freeze-windows", baseUrl, accountId);
      }
    }
    return freezeUrl;
  }
}
