/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.notification;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO.UserGroupFilterDTOBuilder;
import io.harness.ng.core.notification.EmailConfigDTO;
import io.harness.ng.core.notification.NotificationSettingConfigDTO;
import io.harness.ng.core.notification.SlackConfigDTO;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.SlackChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.templates.PredefinedTemplate;
import io.harness.pms.approval.notification.ApprovalSummary.ApprovalSummaryBuilder;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.notification.NotificationHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.remote.client.NGRestUtils;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.usergroups.UserGroupClient;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class ApprovalNotificationHandlerImpl implements ApprovalNotificationHandler {
  public static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM dd, hh:mm a z");
  public static final String STAGE_IDENTIFIER = "STAGE";

  private final UserGroupClient userGroupClient;
  private final NotificationClient notificationClient;
  private final NotificationHelper notificationHelper;
  private final PMSExecutionService pmsExecutionService;

  @Inject
  public ApprovalNotificationHandlerImpl(@Named("PRIVILEGED") UserGroupClient userGroupClient,
      NotificationClient notificationClient, NotificationHelper notificationHelper,
      PMSExecutionService pmsExecutionService) {
    this.userGroupClient = userGroupClient;
    this.notificationClient = notificationClient;
    this.notificationHelper = notificationHelper;
    this.pmsExecutionService = pmsExecutionService;
  }

  @Override
  public void sendNotification(HarnessApprovalInstance approvalInstance, Ambiance ambiance) {
    try (AutoLogContext ignore = approvalInstance.autoLogContext()) {
      sendNotificationInternal(approvalInstance, ambiance);
    }
  }

  private void sendNotificationInternal(HarnessApprovalInstance approvalInstance, Ambiance ambiance) {
    try {
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = getPipelineExecutionSummary(ambiance);

      List<UserGroupDTO> userGroups = getUserGroups(approvalInstance);
      ApprovalSummaryBuilder approvalSummaryBuilder = ApprovalSummary.builder();

      ApprovalSummary approvalSummary =
          approvalSummaryBuilder.pipelineName(pipelineExecutionSummaryEntity.getPipelineIdentifier())
              .orgIdentifier(pipelineExecutionSummaryEntity.getOrgIdentifier())
              .projectIdentifier(pipelineExecutionSummaryEntity.getProjectIdentifier())
              .approvalMessage(approvalInstance.getApprovalMessage())
              .startedAt(formatTime(approvalInstance.getCreatedAt()))
              .expiresAt(formatTime(approvalInstance.getDeadline()))
              .triggeredBy(getUser(ambiance))
              .runningStages(new LinkedHashSet<>())
              .upcomingStages(new LinkedHashSet<>())
              .finishedStages(new LinkedHashSet<>())
              .pipelineExecutionLink(notificationHelper.generateUrl(ambiance))
              .timeRemainingForApproval(formatDuration(approvalInstance.getDeadline() - System.currentTimeMillis()))
              .build();
      if (approvalInstance.isIncludePipelineExecutionHistory()) {
        generateModuleSpecificSummary(approvalSummary, pipelineExecutionSummaryEntity);
      }
      sendNotificationInternal(approvalInstance, userGroups, approvalSummary.toParams());
    } catch (Exception e) {
      log.error("Error while sending notification for harness approval", e);
    }
  }

  private void generateModuleSpecificSummary(
      ApprovalSummary approvalSummary, PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    String startingNodeId = pipelineExecutionSummaryEntity.getStartingNodeId();
    traverseGraph(approvalSummary, pipelineExecutionSummaryEntity.getLayoutNodeMap(), startingNodeId);
  }

  private void traverseGraph(
      ApprovalSummary approvalSummary, Map<String, GraphLayoutNodeDTO> layoutNodeMap, String currentNodeId) {
    GraphLayoutNodeDTO node = layoutNodeMap.get(currentNodeId);
    if (node.getNodeGroup().matches(STAGE_IDENTIFIER)) {
      if (node.getStatus() == ExecutionStatus.NOTSTARTED) {
        approvalSummary.getUpcomingStages().add(node.getName());
      } else if (StatusUtils.finalStatuses().stream().anyMatch(
                     status -> ExecutionStatus.getExecutionStatus(status) == node.getStatus())) {
        approvalSummary.getFinishedStages().add(node.getName());
      } else {
        approvalSummary.getRunningStages().add(node.getName());
      }
    }

    if (!isNull(node.getEdgeLayoutList()) && !isEmpty(node.getEdgeLayoutList().getNextIds())) {
      for (String nextId : node.getEdgeLayoutList().getNextIds()) {
        traverseGraph(approvalSummary, layoutNodeMap, nextId);
      }
    }
  }

  private void sendNotificationInternal(
      HarnessApprovalInstance instance, List<UserGroupDTO> userGroups, Map<String, String> templateData) {
    for (UserGroupDTO userGroup : userGroups) {
      for (NotificationSettingConfigDTO notificationSettingConfig : userGroup.getNotificationConfigs()) {
        NotificationChannel notificationChannel =
            getNotificationChannel(instance, notificationSettingConfig, userGroup, templateData);
        if (notificationChannel != null) {
          notificationClient.sendNotificationAsync(notificationChannel);
        } else {
          log.warn("Error constructing NotificationChannel for {}", notificationSettingConfig);
        }
      }
    }
  }

  private NotificationChannel getNotificationChannel(HarnessApprovalInstance instance,
      NotificationSettingConfigDTO notificationSettingConfig, UserGroupDTO userGroup,
      Map<String, String> templateData) {
    switch (notificationSettingConfig.getType()) {
      case SLACK:
        String slackTemplateId = instance.isIncludePipelineExecutionHistory()
            ? PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_SLACK.getIdentifier()
            : PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_SLACK.getIdentifier();
        SlackConfigDTO slackConfig = (SlackConfigDTO) notificationSettingConfig;
        return SlackChannel.builder()
            .accountId(userGroup.getAccountIdentifier())
            .team(Team.PIPELINE)
            .templateId(slackTemplateId)
            .templateData(templateData)
            .webhookUrls(Collections.singletonList(slackConfig.getSlackWebhookUrl()))
            .build();
      case EMAIL:
        String emailTemplateId = instance.isIncludePipelineExecutionHistory()
            ? PredefinedTemplate.HARNESS_APPROVAL_EXECUTION_NOTIFICATION_EMAIL.getIdentifier()
            : PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_EMAIL.getIdentifier();
        EmailConfigDTO emailConfigDTO = (EmailConfigDTO) notificationSettingConfig;
        return EmailChannel.builder()
            .accountId(userGroup.getAccountIdentifier())
            .team(Team.PIPELINE)
            .templateId(emailTemplateId)
            .templateData(templateData)
            .recipients(Collections.singletonList(emailConfigDTO.getGroupEmail()))
            .build();
      default:
        return null;
    }
  }

  private String getUser(Ambiance ambiance) {
    ExecutionTriggerInfo triggerInfo = ambiance.getMetadata().getTriggerInfo();
    String triggeredBy = triggerInfo.getTriggeredBy().getIdentifier();
    if (EmptyPredicate.isEmpty(triggeredBy)) {
      triggeredBy = "Unknown";
    }
    switch (triggerInfo.getTriggerType()) {
      case WEBHOOK:
      case WEBHOOK_CUSTOM:
        return triggeredBy + " (Webhook trigger)";
      case SCHEDULER_CRON:
        return triggeredBy + " (Scheduled trigger)";
      default:
        return triggeredBy;
    }
  }

  private List<UserGroupDTO> getUserGroups(HarnessApprovalInstance instance) {
    List<UserGroupDTO> userGroups = new ArrayList<>();
    List<String> userGroupIds = instance.getApprovers().getUserGroups();

    if (EmptyPredicate.isEmpty(userGroupIds)) {
      return userGroups;
    }

    String accountId = AmbianceUtils.getAccountId(instance.getAmbiance());
    String orgId = AmbianceUtils.getOrgIdentifier(instance.getAmbiance());
    String projectId = AmbianceUtils.getProjectIdentifier(instance.getAmbiance());
    Map<Scope, List<IdentifierRef>> identifierRefs =
        new HashSet<>(userGroupIds)
            .stream()
            .map(ug -> IdentifierRefHelper.getIdentifierRef(ug, accountId, orgId, projectId))
            .collect(Collectors.groupingBy(IdentifierRef::getScope));

    List<UserGroupFilterDTO> userGroupFilters = ImmutableList.of(Scope.ACCOUNT, Scope.ORG, Scope.PROJECT)
                                                    .stream()
                                                    // Find user groups corresponding to each scope.
                                                    .map(identifierRefs::get)
                                                    // Create a user group filter for each scope.
                                                    .map(this::prepareUserGroupFilter)
                                                    // Remove any scope that doesn't have any user group.
                                                    .filter(Optional::isPresent)
                                                    .map(Optional::get)
                                                    .collect(Collectors.toList());
    if (EmptyPredicate.isEmpty(userGroupFilters)) {
      return userGroups;
    }

    for (UserGroupFilterDTO userGroupFilter : userGroupFilters) {
      List<UserGroupDTO> use = NGRestUtils.getResponse(userGroupClient.getFilteredUserGroups(userGroupFilter));
      if (EmptyPredicate.isNotEmpty(use)) {
        userGroups.addAll(use);
      }
    }
    return userGroups;
  }

  private Optional<UserGroupFilterDTO> prepareUserGroupFilter(List<IdentifierRef> identifierRefs) {
    if (EmptyPredicate.isEmpty(identifierRefs)) {
      return Optional.empty();
    }

    IdentifierRef identifierRef = identifierRefs.get(0);
    UserGroupFilterDTOBuilder builder = UserGroupFilterDTO.builder().identifierFilter(
        identifierRefs.stream().map(IdentifierRef::getIdentifier).collect(Collectors.toSet()));
    switch (identifierRef.getScope()) {
      case ACCOUNT:
        builder.accountIdentifier(identifierRef.getAccountIdentifier());
        break;
      case ORG:
        builder.accountIdentifier(identifierRef.getAccountIdentifier()).orgIdentifier(identifierRef.getOrgIdentifier());
        break;
      case PROJECT:
        builder.accountIdentifier(identifierRef.getAccountIdentifier())
            .orgIdentifier(identifierRef.getOrgIdentifier())
            .projectIdentifier(identifierRef.getProjectIdentifier());
        break;
      default:
        return Optional.empty();
    }
    return Optional.of(builder.build());
  }

  private PipelineExecutionSummaryEntity getPipelineExecutionSummary(Ambiance ambiance) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

    return pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecutionId, false);
  }

  private static String formatTime(long epochMillis) {
    ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("GMT"));
    return DISPLAY_TIME_FORMAT.format(time);
  }

  private static String formatDuration(long durationMillis) {
    long elapsedDays = durationMillis / TimeUnit.DAYS.toMillis(1);
    durationMillis = durationMillis % TimeUnit.DAYS.toMillis(1);

    long elapsedHours = durationMillis / TimeUnit.HOURS.toMillis(1);
    durationMillis = durationMillis % TimeUnit.HOURS.toMillis(1);

    long elapsedMinutes = durationMillis / TimeUnit.MINUTES.toMillis(1);
    durationMillis = durationMillis % TimeUnit.MINUTES.toMillis(1);

    long elapsedSeconds = durationMillis / TimeUnit.SECONDS.toMillis(1);

    StringBuilder elapsed = new StringBuilder();

    if (elapsedDays > 0) {
      elapsed.append(elapsedDays).append('d');
    }
    if (elapsedHours > 0) {
      if (isNotEmpty(elapsed.toString())) {
        elapsed.append(' ');
      }
      elapsed.append(elapsedHours).append('h');
    }
    if (elapsedMinutes > 0) {
      if (isNotEmpty(elapsed.toString())) {
        elapsed.append(' ');
      }
      elapsed.append(elapsedMinutes).append('m');
    }
    if (elapsedSeconds > 0) {
      if (isNotEmpty(elapsed.toString())) {
        elapsed.append(' ');
      }
      elapsed.append(elapsedSeconds).append('s');
    }

    if (isEmpty(elapsed.toString())) {
      elapsed.append("0s");
    }

    return elapsed.toString();
  }
}
