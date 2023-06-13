/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import io.harness.PipelineServiceConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.logging.AutoLogContext;
import io.harness.notification.PipelineEventType;
import io.harness.notification.PipelineEventTypeConstants;
import io.harness.notification.TriggerExecutionInfo;
import io.harness.notification.WebhookNotificationEvent;
import io.harness.notification.WebhookNotificationEvent.WebhookNotificationEventBuilder;
import io.harness.notification.bean.NotificationChannelWrapper;
import io.harness.notification.bean.NotificationRules;
import io.harness.notification.bean.PipelineEvent;
import io.harness.notification.channelDetails.NotificationChannelType;
import io.harness.notification.channelDetails.PmsEmailChannel;
import io.harness.notification.channelDetails.PmsNotificationChannel;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.pms.approval.notification.ApprovalNotificationHandlerImpl;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.sanitizer.HtmlInputSanitizer;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class NotificationHelper {
  @Inject NotificationClient notificationClient;
  @Inject PlanExecutionService planExecutionService;
  @Inject PipelineServiceConfiguration pipelineServiceConfiguration;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject PmsEngineExpressionService pmsEngineExpressionService;
  @Inject PMSPipelineService pmsPipelineService;
  @Inject PipelineExpressionHelper pipelineExpressionHelper;
  @Inject HtmlInputSanitizer userNameSanitizer;
  @Inject PMSExecutionService pmsExecutionService;

  public Optional<PipelineEventType> getEventTypeForStage(NodeExecution nodeExecution) {
    if (!OrchestrationUtils.isStageNode(nodeExecution)) {
      return Optional.empty();
    }
    if (nodeExecution.getStatus() == Status.SUCCEEDED) {
      return Optional.of(io.harness.notification.PipelineEventType.STAGE_SUCCESS);
    }
    if (StatusUtils.brokeStatuses().contains(nodeExecution.getStatus())) {
      return Optional.of(io.harness.notification.PipelineEventType.STAGE_FAILED);
    }
    return Optional.empty();
  }

  public void sendNotification(
      Ambiance ambiance, PipelineEventType pipelineEventType, NodeExecution nodeExecution, Long updatedAt) {
    PlanExecutionMetadata planExecutionMetadata = getPlanExecutionMetadata(ambiance.getPlanExecutionId());
    boolean notifyOnlyMe = Boolean.TRUE.equals(planExecutionMetadata.getNotifyOnlyUser());
    if (notifyOnlyMe) {
      if (PipelineEventType.notifyOnlyUserEvents.contains(pipelineEventType)) {
        sendNotificationOnlyToUserWhoTriggeredPipeline(ambiance, pipelineEventType, nodeExecution, updatedAt, true);
      }
      return;
    }

    if (!ambiance.getMetadata().getIsNotificationConfigured()) {
      return;
    }
    String identifier = getStageIdentifier(nodeExecution);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    String yaml = planExecutionMetadata.getYaml();
    if (EmptyPredicate.isEmpty(yaml)) {
      log.error("Empty yaml found in executionMetaData for execution id: {}", ambiance.getPlanExecutionId());
      return;
    }
    List<NotificationRules> notificationRules = null;
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      notificationRules = getNotificationRulesFromYaml(yaml, ambiance);
    } catch (IOException exception) {
      log.error("Unable to parse yaml to get notification objects", exception);
    }
    if (EmptyPredicate.isEmpty(notificationRules)) {
      return;
    }

    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      sendNotificationInternal(notificationRules, pipelineEventType, identifier, accountId,
          constructTemplateData(
              ambiance, pipelineEventType, nodeExecution, updatedAt, orgIdentifier, projectIdentifier),
          orgIdentifier, projectIdentifier, ambiance, notifyOnlyMe);
    } catch (Exception ex) {
      log.error("Exception occurred in sendNotificationInternal", ex);
    }
  }

  @VisibleForTesting
  void sendNotificationInternal(List<NotificationRules> notificationRulesList, PipelineEventType pipelineEventType,
      String identifier, String accountIdentifier, Map<String, String> notificationContent, String orgIdentifier,
      String projectIdentifier, Ambiance ambiance, boolean notifyOnlyMe) {
    for (NotificationRules notificationRules : notificationRulesList) {
      if (!notificationRules.isEnabled()) {
        continue;
      }
      List<PipelineEvent> pipelineEvents = notificationRules.getPipelineEvents();
      boolean shouldSendNotification =
          notifyOnlyMe || shouldSendNotification(pipelineEvents, pipelineEventType, identifier);
      if (shouldSendNotification) {
        NotificationChannelWrapper wrapper = notificationRules.getNotificationChannelWrapper().getValue();
        if (wrapper.getType() != null) {
          String templateId = getNotificationTemplate(pipelineEventType.getLevel(), wrapper.getType());
          NotificationChannel channel = wrapper.getNotificationChannel().toNotificationChannel(
              accountIdentifier, orgIdentifier, projectIdentifier, templateId, notificationContent, ambiance);
          log.info(
              "Sending notification via notification-client for plan execution id: {} ", ambiance.getPlanExecutionId());
          try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
            notificationClient.sendNotificationAsync(channel);
          } catch (Exception ex) {
            log.error("Unable to send notification because of following exception", ex);
          }
        } else {
          log.error(
              "Unable to send notification for plan execution id: {} for pipeline : {} because notification type is null",
              ambiance.getPlanExecutionId(), ambiance.getMetadata().getPipelineIdentifier());
        }
      }
    }
  }

  private String getNotificationTemplate(String level, String channelType) {
    return String.format("pms_%s_%s_plain", level.toLowerCase(), channelType.toLowerCase());
  }

  private boolean shouldSendNotification(
      List<PipelineEvent> pipelineEvents, PipelineEventType pipelineEventType, String identifier) {
    String pipelineEventTypeLevel = pipelineEventType.getLevel();
    for (PipelineEvent pipelineEvent : pipelineEvents) {
      io.harness.notification.PipelineEventType thisEventType = pipelineEvent.getType();
      if (thisEventType == io.harness.notification.PipelineEventType.ALL_EVENTS) {
        return true;
      } else if (thisEventType == pipelineEventType && pipelineEventTypeLevel.equals("Stage")) {
        List<String> stages = pipelineEvent.getForStages();
        if (stages.contains(identifier) || stages.contains("AllStages")) {
          return true;
        }
      } else if (thisEventType == pipelineEventType) {
        return true;
      }
    }
    return false;
  }

  List<NotificationRules> getNotificationRulesFromYaml(String yaml, Ambiance ambiance) throws IOException {
    BasicPipeline basicPipeline = YamlUtils.read(yaml, BasicPipeline.class);
    return RecastOrchestrationUtils.fromMap(
        (Map<String, Object>) pmsEngineExpressionService.resolve(
            ambiance, RecastOrchestrationUtils.toMap(basicPipeline.getNotificationRules()), true),
        List.class);
  }

  public String generateUrl(Ambiance ambiance) {
    return pipelineExpressionHelper.generateUrl(ambiance);
  }

  public String generatePipelineUrl(Ambiance ambiance) {
    return pipelineExpressionHelper.generatePipelineUrl(ambiance);
  }

  PlanExecutionMetadata getPlanExecutionMetadata(String planExecutionId) {
    Optional<PlanExecutionMetadata> optional = planExecutionMetadataService.findByPlanExecutionId(planExecutionId);
    if (!optional.isPresent()) {
      throw new InvalidRequestException("PlanExecutionMetadata not found!");
    }
    return optional.get();
  }

  String obtainYaml(String planExecutionId) {
    Optional<PlanExecutionMetadata> optional = Optional.ofNullable(getPlanExecutionMetadata(planExecutionId));
    return optional.map(PlanExecutionMetadata::getYaml).orElse(null);
  }

  @VisibleForTesting
  void sendNotificationOnlyToUserWhoTriggeredPipeline(Ambiance ambiance, PipelineEventType pipelineEventType,
      NodeExecution nodeExecution, Long updatedAt, boolean notifyOnlyMe) {
    String identifier = nodeExecution != null ? AmbianceUtils.obtainStepIdentifier(nodeExecution.getAmbiance()) : "";
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    NotificationRules notificationRules = createNotificationRules(ambiance, pipelineEventType);

    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      sendNotificationInternal(Collections.singletonList(notificationRules), pipelineEventType, identifier, accountId,
          constructTemplateData(
              ambiance, pipelineEventType, nodeExecution, updatedAt, orgIdentifier, projectIdentifier),
          orgIdentifier, projectIdentifier, ambiance, notifyOnlyMe);
    } catch (Exception ex) {
      log.error("Exception occurred in sendNotificationInternal", ex);
    }
  }

  @VisibleForTesting
  NotificationRules createNotificationRules(Ambiance ambiance, PipelineEventType pipelineEventType) {
    List<PipelineEvent> pipelineEvents =
        Collections.singletonList(PipelineEvent.builder().type(pipelineEventType).build());

    // Currently Email is HardCoded, other channel types would be supported in future
    String email = AmbianceUtils.getEmail(ambiance);
    List<String> recipientList = Collections.singletonList(email);
    PmsNotificationChannel pmsNotificationChannel =
        PmsEmailChannel.builder().recipients(recipientList).userGroups(Collections.EMPTY_LIST).build();

    NotificationChannelWrapper notificationChannelWrapper = NotificationChannelWrapper.builder()
                                                                .type(NotificationChannelType.EMAIL)
                                                                .notificationChannel(pmsNotificationChannel)
                                                                .build();
    ParameterField<NotificationChannelWrapper> notificationChannelWrapperField =
        ParameterField.createValueField(notificationChannelWrapper);

    return NotificationRules.builder()
        .enabled(true)
        .pipelineEvents(pipelineEvents)
        .notificationChannelWrapper(notificationChannelWrapperField)
        .build();
  }

  @VisibleForTesting
  Map<String, String> constructTemplateData(Ambiance ambiance, PipelineEventType pipelineEventType,
      NodeExecution nodeExecution, Long updatedAt, String orgIdentifier, String projectIdentifier) {
    Map<String, String> templateData = new HashMap<>();
    PlanExecution planExecution = planExecutionService.getPlanExecutionMetadata(ambiance.getPlanExecutionId());
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(
            AmbianceUtils.getAccountId(ambiance), orgIdentifier, projectIdentifier, ambiance.getPlanExecutionId());
    String pipelineId = ambiance.getMetadata().getPipelineIdentifier();

    WebhookNotificationEventBuilder webhookNotificationEvent =
        WebhookNotificationEvent.builder()
            .triggeredBy(getTriggerExecutionInfo(pipelineExecutionSummaryEntity))
            .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .pipelineIdentifier(pipelineId)
            .planExecutionId(ambiance.getPlanExecutionId())
            .eventType(pipelineEventType);

    String userName;
    Long startTs;
    Long endTs;
    String startDate;
    String endDate;
    String nodeIdentifier = "";
    String stepIdentifier = "";
    String stageIdentifier = "";
    String stepName = "";
    String imageStatus = PipelineNotificationUtils.getStatusForImage(planExecution.getStatus());
    String themeColor = PipelineNotificationUtils.getThemeColor(planExecution.getStatus());
    String nodeStatus = PipelineNotificationUtils.getNodeStatus(planExecution.getStatus());
    if (!pipelineEventType.getLevel().equals("Pipeline")) {
      imageStatus = PipelineNotificationUtils.getStatusForImage(nodeExecution.getStatus());
      themeColor = PipelineNotificationUtils.getThemeColor(nodeExecution.getStatus());
      nodeStatus = PipelineNotificationUtils.getNodeStatus(nodeExecution.getStatus());
      userName = nodeExecution.getAmbiance().getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier();
      startTs = nodeExecution.getStartTs() / 1000;
      endTs = updatedAt / 1000;
      startDate = new Date(startTs * 1000).toString();
      endDate = new Date(endTs * 1000).toString();
      nodeIdentifier = AmbianceUtils.obtainStepIdentifier(nodeExecution.getAmbiance());
      if (pipelineEventType.isStepLevelEvent()) {
        stepIdentifier = nodeIdentifier;
        Optional<Level> stageOptional = AmbianceUtils.getStageLevelFromAmbiance(ambiance);
        if (stageOptional.isPresent()) {
          stageIdentifier = stageOptional.get().getIdentifier();
        }
      } else {
        stageIdentifier = nodeIdentifier;
      }
      stepName = nodeExecution.getName();
    } else {
      userName = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier();
      startTs = planExecution.getStartTs() / 1000;
      if (pipelineEventType.getDisplayName().equals(PipelineEventTypeConstants.PIPELINE_START)) {
        endTs = startTs;
      } else {
        endTs = planExecution.getEndTs() / 1000;
      }
      startDate = new Date(startTs * 1000).toString();
      endDate = new Date(endTs * 1000).toString();
    }
    templateData.put("USER_NAME", userNameSanitizer.sanitizeInput(userName));
    templateData.put("ORG_IDENTIFIER", orgIdentifier);
    templateData.put("PROJECT_IDENTIFIER", projectIdentifier);
    templateData.put("EVENT_TYPE", pipelineEventType.getDisplayName());
    templateData.put("PIPELINE", pipelineId);
    templateData.put("PIPELINE_STEP", nodeIdentifier);
    templateData.put("PIPELINE_STEP_NAME", stepName);
    templateData.put("START_TS_SECS", String.valueOf(startTs));
    templateData.put("END_TS_SECS", String.valueOf(endTs));
    templateData.put("START_DATE", startDate);
    templateData.put("END_DATE", endDate);
    templateData.put("DURATION_READABLE", ApprovalNotificationHandlerImpl.formatDuration((endTs - startTs) * 1000));
    templateData.put("DURATION", String.valueOf(endTs - startTs));
    templateData.put("URL", generateUrl(ambiance));
    templateData.put("PIPELINE_URL", generatePipelineUrl(ambiance));
    templateData.put("OUTER_DIV", PipelineNotificationConstants.OUTER_DIV);
    templateData.put("IMAGE_STATUS", imageStatus);
    templateData.put("COLOR", themeColor);
    templateData.put("NODE_STATUS", nodeStatus);
    webhookNotificationEvent.startTime(startDate);
    webhookNotificationEvent.startTs(startTs);

    if (!EmptyPredicate.isEmpty(endDate) && !PipelineEventType.startEvents.contains(pipelineEventType)) {
      webhookNotificationEvent.endTime(endDate);
      webhookNotificationEvent.endTs(endTs);
    }
    if (EmptyPredicate.isNotEmpty(stepIdentifier)) {
      webhookNotificationEvent.stepIdentifier(stepIdentifier);
    }
    if (EmptyPredicate.isNotEmpty(stageIdentifier)) {
      webhookNotificationEvent.stageIdentifier(stageIdentifier);
    }
    templateData.put("WEBHOOK_EVENT_DATA", JsonPipelineUtils.getJsonString(webhookNotificationEvent.build()));
    return templateData;
  }

  @VisibleForTesting
  String getStageIdentifier(NodeExecution nodeExecution) {
    String identifier = nodeExecution != null ? AmbianceUtils.obtainStepIdentifier(nodeExecution.getAmbiance()) : "";
    // Returning identifier of strategy level in case of stages wrapped in looping strategy as their own identifiers
    // (stageId_0, stageId_1, etc..) won't match with the actual stage identifier (stageId) mentioned in notification
    // rules
    if (nodeExecution != null && nodeExecution.getStepType() != null
        && nodeExecution.getStepType().getStepCategory() == StepCategory.STAGE) {
      Optional<Level> strategyLevelOptional = AmbianceUtils.getStrategyLevelFromAmbiance(nodeExecution.getAmbiance());
      if (strategyLevelOptional.isPresent()) {
        identifier = strategyLevelOptional.get().getIdentifier();
      }
    }
    return identifier;
  }

  private TriggerExecutionInfo getTriggerExecutionInfo(PipelineExecutionSummaryEntity summaryEntity) {
    return TriggerExecutionInfo.builder()
        .triggerType(summaryEntity.getExecutionTriggerInfo().getTriggerType().toString())
        .name(summaryEntity.getExecutionTriggerInfo().getTriggeredBy().getIdentifier())
        .email(summaryEntity.getExecutionTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email"))
        .build();
  }
}
