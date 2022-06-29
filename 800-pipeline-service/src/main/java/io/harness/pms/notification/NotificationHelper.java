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
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.notification.PipelineEventType;
import io.harness.notification.PipelineEventTypeConstants;
import io.harness.notification.bean.NotificationChannelWrapper;
import io.harness.notification.bean.NotificationRules;
import io.harness.notification.bean.PipelineEvent;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.expression.PmsEngineExpressionService;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import java.io.IOException;
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
    if (!ambiance.getMetadata().getIsNotificationConfigured()) {
      return;
    }
    String identifier = nodeExecution != null ? AmbianceUtils.obtainStepIdentifier(nodeExecution.getAmbiance()) : "";
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    String yaml = obtainYaml(ambiance.getPlanExecutionId());
    if (EmptyPredicate.isEmpty(yaml)) {
      log.error("Empty yaml found in executionMetaData");
      return;
    }
    List<NotificationRules> notificationRules = null;
    try {
      notificationRules = getNotificationRulesFromYaml(yaml, ambiance);
    } catch (IOException exception) {
      log.error("Unable to parse yaml to get notification objects", exception);
    }
    if (EmptyPredicate.isEmpty(notificationRules)) {
      return;
    }

    try {
      sendNotificationInternal(notificationRules, pipelineEventType, identifier, accountId,
          constructTemplateData(
              ambiance, pipelineEventType, nodeExecution, identifier, updatedAt, orgIdentifier, projectIdentifier),
          orgIdentifier, projectIdentifier, ambiance);
    } catch (Exception ex) {
      log.error("Exception occurred in sendNotificationInternal", ex);
    }
  }

  private void sendNotificationInternal(List<NotificationRules> notificationRulesList,
      PipelineEventType pipelineEventType, String identifier, String accountIdentifier,
      Map<String, String> notificationContent, String orgIdentifier, String projectIdentifier, Ambiance ambiance) {
    for (NotificationRules notificationRules : notificationRulesList) {
      if (!notificationRules.isEnabled()) {
        continue;
      }
      List<PipelineEvent> pipelineEvents = notificationRules.getPipelineEvents();
      boolean shouldSendNotification = shouldSendNotification(pipelineEvents, pipelineEventType, identifier);
      if (shouldSendNotification) {
        NotificationChannelWrapper wrapper = notificationRules.getNotificationChannelWrapper().getValue();
        String templateId = getNotificationTemplate(pipelineEventType.getLevel(), wrapper.getType());
        NotificationChannel channel = wrapper.getNotificationChannel().toNotificationChannel(
            accountIdentifier, orgIdentifier, projectIdentifier, templateId, notificationContent, ambiance);
        log.info("Sending notification via notification-client");
        try {
          notificationClient.sendNotificationAsync(channel);
        } catch (Exception ex) {
          log.error("Unable to send notification because of following exception", ex);
        }
      }
    }
  }

  private String getNotificationTemplate(String level, String channelType) {
    return String.format("pms_%s_%s_plain", level.toLowerCase(), channelType.toLowerCase());
  }

  private boolean shouldSendNotification(List<PipelineEvent> pipelineEvents,
      io.harness.notification.PipelineEventType pipelineEventType, String identifier) {
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

  String obtainYaml(String planExecutionId) {
    Optional<PlanExecutionMetadata> optional = planExecutionMetadataService.findByPlanExecutionId(planExecutionId);
    return optional.map(PlanExecutionMetadata::getYaml).orElse(null);
  }

  private Map<String, String> constructTemplateData(Ambiance ambiance, PipelineEventType pipelineEventType,
      NodeExecution nodeExecution, String identifier, Long updatedAt, String orgIdentifier, String projectIdentifier) {
    Map<String, String> templateData = new HashMap<>();
    PlanExecution planExecution = planExecutionService.get(ambiance.getPlanExecutionId());
    String pipelineId = ambiance.getMetadata().getPipelineIdentifier();
    String userName;
    Long startTs;
    Long endTs;
    String startDate;
    String endDate;
    String stepIdentifier = "";
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
      stepIdentifier = AmbianceUtils.obtainStepIdentifier(nodeExecution.getAmbiance());
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
    templateData.put("USER_NAME", userName);
    templateData.put("ORG_IDENTIFIER", orgIdentifier);
    templateData.put("PROJECT_IDENTIFIER", projectIdentifier);
    templateData.put("EVENT_TYPE", pipelineEventType.getDisplayName());
    templateData.put("PIPELINE", pipelineId);
    templateData.put("PIPELINE_STEP", stepIdentifier);
    templateData.put("START_TS_SECS", String.valueOf(startTs));
    templateData.put("END_TS_SECS", String.valueOf(endTs));
    templateData.put("START_DATE", startDate);
    templateData.put("END_DATE", endDate);
    templateData.put("DURATION", String.valueOf(endTs - startTs));
    templateData.put("URL", generateUrl(ambiance));
    templateData.put("OUTER_DIV", PipelineNotificationConstants.OUTER_DIV);
    templateData.put("IMAGE_STATUS", imageStatus);
    templateData.put("COLOR", themeColor);
    templateData.put("NODE_STATUS", nodeStatus);
    return templateData;
  }
}
