/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.notification;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.notification.EmailConfigDTO;
import io.harness.ng.core.notification.NotificationSettingConfigDTO;
import io.harness.ng.core.notification.SlackConfigDTO;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.notification.NotificationHelper;
import io.harness.pms.pipeline.mappers.GraphLayoutDtoMapper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.usergroups.UserGroupClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@OwnedBy(PIPELINE)
@PrepareForTest(NGRestUtils.class)
public class ApprovalNotificationHandlerImplTest extends CategoryTest {
  @Mock private UserGroupClient userGroupClient;
  @Mock private NotificationClient notificationClient;
  @Mock private NotificationHelper notificationHelper;
  @Mock private PMSExecutionService pmsExecutionService;
  @Mock private ApprovalInstance approvalInstance;
  @InjectMocks ApprovalNotificationHandlerImpl approvalNotificationHandler;
  private static String accountId = "accountId";
  private static String orgIdentifier = "orgIdentifier";
  private static String projectIdentifier = "projectIdentifier";
  private static String pipelineIdentifier = "pipelineIdentifier";
  private static String startingNodeId = "startingNodeId";
  @Before
  public void setUp() {
    //        MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSendNotification() {
    String url =
        "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
    Mockito.mockStatic(NGRestUtils.class);
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    HarnessApprovalInstance approvalInstance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().userGroups(Collections.singletonList("user")).build())
            .build();
    approvalInstance.setAmbiance(ambiance);
    approvalInstance.setCreatedAt(System.currentTimeMillis());
    approvalInstance.setDeadline(2L * System.currentTimeMillis());
    approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgIdentifier)
                                                                        .projectIdentifier(projectIdentifier)
                                                                        .pipelineIdentifier(pipelineIdentifier)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
    notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
    notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());

    List<UserGroupDTO> userGroupDTOS =
        Collections.singletonList(UserGroupDTO.builder().notificationConfigs(notificationSettingConfigDTOS).build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    when(NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);

    doReturn(url).when(notificationHelper).generateUrl(ambiance);
    approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
    verify(notificationClient, times(2)).sendNotificationAsync(any());
    verify(pmsExecutionService, times(1))
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    verify(userGroupClient, times(1)).getFilteredUserGroups(any());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification1() {
    String url =
        "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
    Mockito.mockStatic(NGRestUtils.class);

    GraphLayoutNode graphLayoutNode = GraphLayoutNode.newBuilder()
                                          .setNodeIdentifier("nodeIdentifier")
                                          .setNodeType("Approval")
                                          .setNodeUUID("aBcDeFgH")
                                          .setName("Node name")
                                          .setNodeGroup("STAGE")
                                          .build();
    GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(graphLayoutNode);
    HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    HarnessApprovalInstance approvalInstance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().userGroups(Collections.singletonList("user")).build())
            .build();
    approvalInstance.setAmbiance(ambiance);
    approvalInstance.setCreatedAt(System.currentTimeMillis());
    approvalInstance.setDeadline(2L * System.currentTimeMillis());
    approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    approvalInstance.setIncludePipelineExecutionHistory(true);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgIdentifier)
                                                                        .projectIdentifier(projectIdentifier)
                                                                        .pipelineIdentifier(pipelineIdentifier)
                                                                        .startingNodeId(startingNodeId)
                                                                        .layoutNodeMap(layoutNodeDTOMap)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
    notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
    notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());

    List<UserGroupDTO> userGroupDTOS =
        Collections.singletonList(UserGroupDTO.builder().notificationConfigs(notificationSettingConfigDTOS).build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    when(NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);

    doReturn(url).when(notificationHelper).generateUrl(ambiance);
    approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
    verify(notificationClient, times(2)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification2() {
    String url =
        "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
    Mockito.mockStatic(NGRestUtils.class);

    GraphLayoutNode graphLayoutNode = GraphLayoutNode.newBuilder()
                                          .setNodeIdentifier("nodeIdentifier")
                                          .setNodeType("Approval")
                                          .setNodeUUID("aBcDeFgH")
                                          .setName("Node name")
                                          //                    .status(ExecutionStatus.SUCCESS)
                                          .setNodeGroup("STAGE")
                                          .build();

    GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(graphLayoutNode);
    graphLayoutNodeDTO.setStatus(ExecutionStatus.SUCCESS);

    //    graphLayoutNodeDTO.status= ExecutionStatus.SUCCESS;
    HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    HarnessApprovalInstance approvalInstance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().userGroups(Collections.singletonList("user")).build())
            .build();
    approvalInstance.setAmbiance(ambiance);
    approvalInstance.setCreatedAt(System.currentTimeMillis());
    approvalInstance.setDeadline(2L * System.currentTimeMillis());
    approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    approvalInstance.setIncludePipelineExecutionHistory(true);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgIdentifier)
                                                                        .projectIdentifier(projectIdentifier)
                                                                        .pipelineIdentifier(pipelineIdentifier)
                                                                        .startingNodeId(startingNodeId)
                                                                        .layoutNodeMap(layoutNodeDTOMap)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
    notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
    notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());

    List<UserGroupDTO> userGroupDTOS =
        Collections.singletonList(UserGroupDTO.builder().notificationConfigs(notificationSettingConfigDTOS).build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    when(NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);

    doReturn(url).when(notificationHelper).generateUrl(ambiance);
    approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
    verify(notificationClient, times(2)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification3() {
    String url =
        "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
    Mockito.mockStatic(NGRestUtils.class);

    GraphLayoutNode graphLayoutNode = GraphLayoutNode.newBuilder()
                                          .setNodeIdentifier("nodeIdentifier")
                                          .setNodeType("Approval")
                                          .setNodeUUID("aBcDeFgH")
                                          .setName("Node name")
                                          //                    .status(ExecutionStatus.SUCCESS)
                                          .setNodeGroup("STAGE")
                                          .build();

    GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(graphLayoutNode);
    graphLayoutNodeDTO.setStatus(ExecutionStatus.ASYNCWAITING);

    //    graphLayoutNodeDTO.status= ExecutionStatus.SUCCESS;
    HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    HarnessApprovalInstance approvalInstance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().userGroups(Collections.singletonList("user")).build())
            .build();
    approvalInstance.setAmbiance(ambiance);
    approvalInstance.setCreatedAt(System.currentTimeMillis());
    approvalInstance.setDeadline(2L * System.currentTimeMillis());
    approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    approvalInstance.setIncludePipelineExecutionHistory(true);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgIdentifier)
                                                                        .projectIdentifier(projectIdentifier)
                                                                        .pipelineIdentifier(pipelineIdentifier)
                                                                        .startingNodeId(startingNodeId)
                                                                        .layoutNodeMap(layoutNodeDTOMap)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
    notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
    notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());

    List<UserGroupDTO> userGroupDTOS =
        Collections.singletonList(UserGroupDTO.builder().notificationConfigs(notificationSettingConfigDTOS).build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    when(NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);

    doReturn(url).when(notificationHelper).generateUrl(ambiance);
    approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
    verify(notificationClient, times(2)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification4() {
    String url =
        "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
    Mockito.mockStatic(NGRestUtils.class);

    GraphLayoutNode graphLayoutNode1 =
        GraphLayoutNode.newBuilder()
            .setNodeIdentifier("nodeIdentifier")
            .setNodeType("Approval")
            .setNodeUUID("aBcDeFgH")
            .setName("Node name")
            .setNodeGroup("STAGE")
            .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds("nextId").addCurrentNodeChildren("child").build())
            .build();
    GraphLayoutNodeDTO graphLayoutNodeDTO1 = GraphLayoutDtoMapper.toDto(graphLayoutNode1);
    HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO1);

    GraphLayoutNode graphLayoutNode2 = GraphLayoutNode.newBuilder()
                                           .setNodeIdentifier("nodeIdentifier")
                                           .setNodeType("Approval")
                                           .setNodeUUID("aBcDeFgH")
                                           .setName("Node name")
                                           .setNodeGroup("STAGE")
                                           .build();
    GraphLayoutNodeDTO graphLayoutNodeDTO2 = GraphLayoutDtoMapper.toDto(graphLayoutNode2);
    layoutNodeDTOMap.put("nextId", graphLayoutNodeDTO2);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    HarnessApprovalInstance approvalInstance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().userGroups(Collections.singletonList("user")).build())
            .build();
    approvalInstance.setAmbiance(ambiance);
    approvalInstance.setCreatedAt(System.currentTimeMillis());
    approvalInstance.setDeadline(2L * System.currentTimeMillis());
    approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    approvalInstance.setIncludePipelineExecutionHistory(true);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgIdentifier)
                                                                        .projectIdentifier(projectIdentifier)
                                                                        .pipelineIdentifier(pipelineIdentifier)
                                                                        .startingNodeId(startingNodeId)
                                                                        .layoutNodeMap(layoutNodeDTOMap)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
    notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
    notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());

    List<UserGroupDTO> userGroupDTOS =
        Collections.singletonList(UserGroupDTO.builder().notificationConfigs(notificationSettingConfigDTOS).build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    when(NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);

    doReturn(url).when(notificationHelper).generateUrl(ambiance);
    approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
    verify(notificationClient, times(2)).sendNotificationAsync(any());
  }
}