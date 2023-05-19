/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.notification.PipelineEventType.STAGE_FAILED;
import static io.harness.notification.PipelineEventType.STAGE_SUCCESS;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.PipelineServiceConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.notification.PipelineEventType;
import io.harness.notification.bean.NotificationRules;
import io.harness.notification.channelDetails.NotificationChannelType;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationClientImpl;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(HarnessTeam.PIPELINE)
public class NotificationHelperTest extends CategoryTest {
  NotificationClient notificationClient;
  PlanExecutionService planExecutionService;
  PipelineServiceConfiguration pipelineServiceConfiguration;
  PlanExecutionMetadataService planExecutionMetadataService;
  NotificationHelper notificationHelper;
  PmsEngineExpressionService pmsEngineExpressionService;
  PMSPipelineService pmsPipelineService;
  PipelineExpressionHelper pipelineExpressionHelper;
  String executionUrl =
      "http:127.0.0.1:8080/account/dummyAccount/cd/orgs/dummyOrg/projects/dummyProject/pipelines/dummyPipeline/executions/dummyPlanExecutionId/pipeline";
  Ambiance ambiance =
      Ambiance.newBuilder()
          .putSetupAbstractions("accountId", "dummyAccount")
          .putSetupAbstractions("orgIdentifier", "dummyOrg")
          .putSetupAbstractions("projectIdentifier", "dummyProject")
          .setMetadata(
              ExecutionMetadata.newBuilder()
                  .setModuleType("cd")
                  .setIsNotificationConfigured(true)
                  .setPipelineIdentifier("dummyPipeline")
                  .setTriggerInfo(
                      io.harness.pms.contracts.plan.ExecutionTriggerInfo.newBuilder()
                          .setTriggeredBy(
                              io.harness.pms.contracts.plan.TriggeredBy.newBuilder().setIdentifier("dummy").build())
                          .build())
                  .build())
          .setPlanExecutionId("dummyPlanExecutionId")
          .build();
  PipelineEventType pipelineEventType = PipelineEventType.PIPELINE_END;
  Long updatedAt = 0L;
  String yaml = "pipeline:\n"
      + "    name: DockerTest\n"
      + "    identifier: DockerTest\n"
      + "    notificationRules:\n"
      + "        - name: N2\n"
      + "          pipelineEvents:\n"
      + "              - type: PipelineSuccess\n"
      + "              - type: StageFailed\n"
      + "                forStages:\n"
      + "                    - stage1\n"
      + "          notificationMethod:\n"
      + "              type: Slack\n"
      + "              spec:\n"
      + "                  userGroups: []\n"
      + "                  webhookUrl: https://hooks.slack.com/services/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\n"
      + "          enabled: true\n";
  String emailNotificationYaml = "pipeline:\n"
      + "    name: DockerTest\n"
      + "    identifier: DockerTest\n"
      + "    notificationRules:\n"
      + "        - name: N2\n"
      + "          pipelineEvents:\n"
      + "              - type: PipelineSuccess\n"
      + "              - type: StageFailed\n"
      + "                forStages:\n"
      + "                    - stage1\n"
      + "          notificationMethod:\n"
      + "              type: Email\n"
      + "              spec:\n"
      + "                  userGroups: []\n"
      + "                  recipients: \n"
      + "                    - admin@harness.io \n"
      + "                    - test@harness.io \n"
      + "          enabled: true\n";
  String allEventsYaml = "pipeline:\n"
      + "    name: DockerTest\n"
      + "    identifier: DockerTest\n"
      + "    notificationRules:\n"
      + "        - name: N2\n"
      + "          pipelineEvents:\n"
      + "              - type: AllEvents\n"
      + "          notificationMethod:\n"
      + "              type: Email\n"
      + "              spec:\n"
      + "                  userGroups: []\n"
      + "                  recipients: \n"
      + "                    - admin@harness.io \n"
      + "                    - test@harness.io \n"
      + "          enabled: true\n";

  String notificationRulesString =
      "{\"__recast\":\"java.util.ArrayList\",\"__encodedValue\":[{\"__recast\":\"io.harness.notification.bean.NotificationRules\",\"name\":\"N2\",\"enabled\":true,\"pipelineEvents\":[{\"__recast\":\"io.harness.notification.bean.PipelineEvent\",\"type\":\"ALL_EVENTS\",\"forStages\":null},{\"__recast\":\"io.harness.notification.bean.PipelineEvent\",\"type\":\"STAGE_FAILED\",\"forStages\":[\"stage1\"]}],\"notificationChannelWrapper\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expressionValue\":null,\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":{\"__recast\":\"io.harness.notification.bean.NotificationChannelWrapper\",\"type\":\"Email\",\"notificationChannel\":{\"__recast\":\"io.harness.notification.channelDetails.PmsEmailChannel\",\"userGroups\":[],\"recipients\":[\"admin@harness.io\",\"test@harness.io\"]}}},\"valueClass\":\"io.harness.notification.bean.NotificationChannelWrapper\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false,\"responseField\":null}}}]}";
  Map<String, Object> notificationRulesMap =
      RecastOrchestrationUtils.toMap(RecastOrchestrationUtils.fromJson(notificationRulesString));

  @Before
  public void setup() {
    notificationClient = mock(NotificationClientImpl.class);
    planExecutionService = mock(PlanExecutionService.class);
    pipelineServiceConfiguration = mock(PipelineServiceConfiguration.class);
    planExecutionMetadataService = mock(PlanExecutionMetadataService.class);
    pmsEngineExpressionService = mock(PmsEngineExpressionService.class);
    pmsPipelineService = mock(PMSPipelineService.class);
    pipelineExpressionHelper = mock(PipelineExpressionHelper.class);
    notificationHelper = spy(new NotificationHelper());
    notificationHelper.notificationClient = notificationClient;
    notificationHelper.planExecutionService = planExecutionService;
    notificationHelper.pipelineServiceConfiguration = pipelineServiceConfiguration;
    notificationHelper.planExecutionMetadataService = planExecutionMetadataService;
    notificationHelper.pmsEngineExpressionService = pmsEngineExpressionService;
    notificationHelper.pmsPipelineService = pmsPipelineService;
    notificationHelper.pipelineExpressionHelper = pipelineExpressionHelper;
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGenerateUrl() {
    when(pipelineServiceConfiguration.getPipelineServiceBaseUrl()).thenReturn("http:127.0.0.1:8080");
    doReturn(executionUrl).when(pipelineExpressionHelper).generateUrl(ambiance);
    String generatedUrl = notificationHelper.generateUrl(ambiance);
    assertEquals(executionUrl, generatedUrl);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSendNotification() {
    PlanNode planNode = PlanNode.builder().identifier("dummyIdentifier").build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().yaml(yaml).build();
    NodeExecution nodeExecution =
        NodeExecution.builder().planNode(planNode).status(Status.SUCCEEDED).startTs(0L).ambiance(ambiance).build();
    when(planExecutionMetadataService.findByPlanExecutionId(any()))
        .thenReturn(java.util.Optional.ofNullable(planExecutionMetadata));
    doReturn(null).when(notificationClient).sendNotificationAsync(any());
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    doReturn(executionUrl).when(notificationHelper).generateUrl(any());
    // testing pipeline level event flow.
    assertThatCode(()
                       -> notificationHelper.sendNotification(
                           ambiance, PipelineEventType.PIPELINE_SUCCESS, nodeExecution, updatedAt))
        .doesNotThrowAnyException();
    // testing stage level(non pipeline) flow.
    assertThatCode(
        () -> notificationHelper.sendNotification(ambiance, PipelineEventType.STAGE_FAILED, nodeExecution, updatedAt))
        .doesNotThrowAnyException();
  }
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetEventTypeForStage() {
    String planExecutionId = generateUuid();
    PlanNode pipelinePlanNode = PlanNode.builder()
                                    .uuid(generateUuid())
                                    .stepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                                    .identifier("dummyIdentifier")
                                    .build();
    PlanNode stagePlanNode = PlanNode.builder()
                                 .uuid(generateUuid())
                                 .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                 .identifier("dummyIdentifier")
                                 .build();

    Ambiance.Builder ambianceBuilder =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode));
    NodeExecutionBuilder nodeExecutionBuilder =
        NodeExecution.builder().ambiance(ambianceBuilder.build()).status(Status.SUCCEEDED);
    assertEquals(notificationHelper.getEventTypeForStage(nodeExecutionBuilder.build()), Optional.empty());
    ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagePlanNode));
    assertEquals(
        notificationHelper.getEventTypeForStage(nodeExecutionBuilder.ambiance(ambianceBuilder.build()).build()),
        Optional.of(STAGE_SUCCESS));
    nodeExecutionBuilder.status(Status.FAILED);
    assertEquals(notificationHelper.getEventTypeForStage(nodeExecutionBuilder.build()), Optional.of(STAGE_FAILED));
    nodeExecutionBuilder.status(Status.ABORTED);
    assertEquals(notificationHelper.getEventTypeForStage(nodeExecutionBuilder.build()), Optional.empty());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testEmailNotificationIsSentToAllRecipients() {
    PlanNode planNode = PlanNode.builder().identifier("dummyIdentifier").build();
    NodeExecution nodeExecution =
        NodeExecution.builder().planNode(planNode).status(Status.SUCCEEDED).startTs(0L).ambiance(ambiance).build();
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    when(planExecutionMetadataService.findByPlanExecutionId(anyString()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().yaml(emailNotificationYaml).build()));
    ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
        ArgumentCaptor.forClass(NotificationChannel.class);
    doReturn(notificationRulesMap).when(pmsEngineExpressionService).resolve(eq(ambiance), any(), eq(true));

    notificationHelper.sendNotification(ambiance, PipelineEventType.PIPELINE_SUCCESS, nodeExecution, 1L);
    verify(notificationClient, times(1)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
    EmailChannel notificationChannel = (EmailChannel) notificationChannelArgumentCaptor.getValue();
    assertTrue(notificationChannel.getRecipients().contains("admin@harness.io"));
    assertTrue(notificationChannel.getRecipients().contains("test@harness.io"));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testNotificationIsSentForAllEvents() {
    List<PipelineEventType> pipelineEventTypeList = new ArrayList<>();
    pipelineEventTypeList.add(PipelineEventType.PIPELINE_START);
    pipelineEventTypeList.add(PipelineEventType.PIPELINE_END);
    pipelineEventTypeList.add(PipelineEventType.PIPELINE_FAILED);
    pipelineEventTypeList.add(PipelineEventType.PIPELINE_PAUSED);
    pipelineEventTypeList.add(PipelineEventType.PIPELINE_SUCCESS);
    pipelineEventTypeList.add(PipelineEventType.STAGE_START);
    pipelineEventTypeList.add(STAGE_FAILED);
    pipelineEventTypeList.add(STAGE_SUCCESS);
    pipelineEventTypeList.add(PipelineEventType.STEP_FAILED);

    PlanNode planNode = PlanNode.builder().identifier("dummyIdentifier").build();
    NodeExecution nodeExecution =
        NodeExecution.builder().planNode(planNode).status(Status.SUCCEEDED).startTs(0L).ambiance(ambiance).build();
    when(planExecutionService.getPlanExecutionMetadata(anyString()))
        .thenReturn(PlanExecution.builder().status(Status.SUCCEEDED).startTs(0L).endTs(0L).build());
    when(planExecutionMetadataService.findByPlanExecutionId(anyString()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().yaml(allEventsYaml).build()));
    doReturn(notificationRulesMap).when(pmsEngineExpressionService).resolve(eq(ambiance), any(), eq(true));
    for (int idx = 0; idx < pipelineEventTypeList.size(); idx++) {
      notificationHelper.sendNotification(ambiance, pipelineEventTypeList.get(idx), nodeExecution, 1L);
      verify(notificationClient, times(idx + 1)).sendNotificationAsync(any());
    }
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testcreateNotificationRules() {
    NotificationRules notificationRules = notificationHelper.createNotificationRules(ambiance, pipelineEventType);
    assertThat(notificationRules.isEnabled()).isTrue();
    assertThat(notificationRules.getPipelineEvents().stream().findFirst().get().getType()).isEqualTo(pipelineEventType);
    assertThat(notificationRules.getNotificationChannelWrapper().getValue().getType())
        .isEqualTo(NotificationChannelType.EMAIL);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testSendNotificationOnlyToUserWhoTriggeredPipeline() {
    doNothing()
        .when(notificationHelper)
        .sendNotificationInternal(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    doReturn(Collections.EMPTY_MAP)
        .when(notificationHelper)
        .constructTemplateData(any(), any(), any(), any(), any(), any());
    notificationHelper.sendNotificationOnlyToUserWhoTriggeredPipeline(ambiance, pipelineEventType, null, null, true);
    verify(notificationHelper, times(1)).createNotificationRules(any(), any());
    verify(notificationHelper, times(1))
        .sendNotificationInternal(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetStageIdentifier() {
    String planExecutionId = generateUuid();
    PlanNode stagePlanNode = PlanNode.builder()
                                 .uuid(generateUuid())
                                 .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                 .identifier("dummyIdentifier_0")
                                 .build();
    PlanNode strategyPlanNode = PlanNode.builder()
                                    .uuid(generateUuid())
                                    .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                    .identifier("dummyIdentifier")
                                    .build();
    Ambiance.Builder ambianceBuilder = Ambiance.newBuilder()
                                           .setPlanExecutionId(planExecutionId)
                                           .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagePlanNode));
    NodeExecutionBuilder nodeExecutionBuilder = NodeExecution.builder().ambiance(ambianceBuilder.build());
    assertEquals(notificationHelper.getStageIdentifier(nodeExecutionBuilder.build()), "dummyIdentifier_0");
    ambianceBuilder.addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), strategyPlanNode));
    nodeExecutionBuilder.ambiance(ambianceBuilder.build());
    assertEquals(notificationHelper.getStageIdentifier(nodeExecutionBuilder.build()), "dummyIdentifier");
  }
}
