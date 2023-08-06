/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.notification;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.notification.EmailConfigDTO;
import io.harness.ng.core.notification.MicrosoftTeamsConfigDTO;
import io.harness.ng.core.notification.NotificationSettingConfigDTO;
import io.harness.ng.core.notification.SlackConfigDTO;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.templates.PredefinedTemplate;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.approval.notification.ApprovalSummary.ApprovalSummaryKeys;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.notification.NotificationHelper;
import io.harness.pms.pipeline.mappers.GraphLayoutDtoMapper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.project.remote.ProjectClient;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivity;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.usergroups.UserGroupClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class ApprovalNotificationHandlerImplTest extends CategoryTest {
  @Mock private UserGroupClient userGroupClient;
  @Mock private NotificationClient notificationClient;
  @Mock private NotificationHelper notificationHelper;
  @Mock private PMSExecutionService pmsExecutionService;
  @Mock private ApprovalInstance approvalInstance;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private ProjectClient projectClient;
  @Mock private OrganizationClient organizationClient;
  @InjectMocks ApprovalNotificationHandlerImpl approvalNotificationHandler;
  private static String accountId = "accountId";

  private static String userGroupIdentifier = "userGroupIdentifier";
  private static String orgIdentifier = "orgIdentifier";
  private static String projectIdentifier = "projectIdentifier";
  private static String pipelineIdentifier = "pipelineIdentifier";
  private static String startingNodeId = "startingNodeId";
  private static String pipelineName = "pipeline name";
  private static String orgName = "org name";
  private static String projectName = "project name";
  private static String userUuid = "XXXX YYYY XXXX";
  private static String userId = "userID";

  @Before
  public void setup() throws Exception {
    Call<ResponseDTO<Optional<OrganizationResponse>>> orgDTOCall = mock(Call.class);
    when(organizationClient.getOrganization(any(), any())).thenReturn(orgDTOCall);
    OrganizationResponse organizationResponse =
        OrganizationResponse.builder()
            .organization(OrganizationDTO.builder().identifier(orgIdentifier).name(orgName).build())
            .build();
    ResponseDTO<Optional<OrganizationResponse>> orgRestResponse =
        ResponseDTO.newResponse(Optional.of(organizationResponse));
    Response<ResponseDTO<Optional<OrganizationResponse>>> orgResponse = Response.success(orgRestResponse);
    when(orgDTOCall.execute()).thenReturn(orgResponse);

    Call<ResponseDTO<Optional<ProjectResponse>>> projDTOCall = mock(Call.class);
    when(projectClient.getProject(projectIdentifier, accountId, orgIdentifier)).thenReturn(projDTOCall);
    ProjectResponse projectResponse =
        ProjectResponse.builder()
            .project(ProjectDTO.builder().identifier(projectIdentifier).name(projectName).build())
            .build();
    ResponseDTO<Optional<ProjectResponse>> projRestResponse = ResponseDTO.newResponse(Optional.of(projectResponse));
    Response<ResponseDTO<Optional<ProjectResponse>>> projResponse = Response.success(projRestResponse);
    when(projDTOCall.execute()).thenReturn(projResponse);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSendNotification() throws Exception {
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url =
          "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
      TriggeredBy triggeredBy = TriggeredBy.newBuilder().setIdentifier(userId).setUuid(userUuid).build();
      ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).build();
      ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setTriggerInfo(triggerInfo).build();
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", accountId)
                              .putSetupAbstractions("orgIdentifier", orgIdentifier)
                              .putSetupAbstractions("projectIdentifier", projectIdentifier)
                              .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                              .setMetadata(executionMetadata)
                              .build();
      HarnessApprovalInstance approvalInstance =
          HarnessApprovalInstance.builder()
              .approvers(
                  ApproversDTO.builder()
                      .userGroups(new ArrayList<>(Arrays.asList("proj_faulty", "proj_right", "org.org_faulty",
                          "org.org_right", "account.acc_faulty", "account.acc_right", "proj_faulty", "proj_right")))
                      .build())
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
                                                                          .name(pipelineName)
                                                                          .build();
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
      List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
      notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(MicrosoftTeamsConfigDTO.builder().build());

      List<UserGroupDTO> userGroupDTOS =
          new ArrayList<>(Arrays.asList(UserGroupDTO.builder()
                                            .identifier("proj_right")
                                            .accountIdentifier(accountId)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .notificationConfigs(notificationSettingConfigDTOS)
                                            .build(),
              UserGroupDTO.builder()
                  .identifier("org_right")
                  .accountIdentifier(accountId)
                  .orgIdentifier(orgIdentifier)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build(),
              UserGroupDTO.builder()
                  .identifier("acc_right")
                  .accountIdentifier(accountId)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build()));

      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(ambiance);
      when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance))
          .thenReturn(Mockito.mock(ILogStreamingStepClient.class));

      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(9)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineName))
          .isEqualTo(pipelineName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.orgName)).isEqualTo(orgName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.projectName))
          .isEqualTo(projectName);
      // get userId in triggeredBy because email is not present
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.triggeredBy)).isEqualTo(userId);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineExecutionLink))
          .isEqualTo(url);
      assertThat(notificationChannels.get(8).getTemplateId())
          .isEqualTo(PredefinedTemplate.HARNESS_APPROVAL_NOTIFICATION_MSTEAMS.getIdentifier());
      assertThat(notificationChannels.get(8).getTeam()).isEqualTo(Team.PIPELINE);
      verify(pmsExecutionService, times(1))
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
      verify(ngLogCallback.constructed().get(0), times(2)).saveExecutionLog(anyString());
      ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
      verify(ngLogCallback.constructed().get(0), times(1))
          .saveExecutionLog(stringArgumentCaptor.capture(), eq(LogLevel.WARN));

      String invalidUserGroups = stringArgumentCaptor.getValue().split(":")[1].trim();
      List<String> invalidUserGroupsList =
          Arrays.stream(invalidUserGroups.substring(1, invalidUserGroups.length() - 1).split(","))
              .map(String::trim)
              .collect(Collectors.toList());
      List<String> expectedInvalidUserGroupsList =
          new ArrayList<>(Arrays.asList("proj_faulty", "org.org_faulty", "account.acc_faulty"));
      assertThat(invalidUserGroupsList.size() == expectedInvalidUserGroupsList.size()
          && invalidUserGroupsList.containsAll(expectedInvalidUserGroupsList)
          && expectedInvalidUserGroupsList.containsAll(invalidUserGroupsList))
          .isTrue();
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification1() throws IOException {
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url =
          "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";

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
          Collections.singletonList(UserGroupDTO.builder()
                                        .identifier(userGroupIdentifier)
                                        .notificationConfigs(notificationSettingConfigDTOS)
                                        .build());
      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(ambiance);
      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
      verify(ngLogCallback.constructed().get(0), times(2)).saveExecutionLog(anyString());
      verify(ngLogCallback.constructed().get(0), times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification2() throws IOException {
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url =
          "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";

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
          Collections.singletonList(UserGroupDTO.builder()
                                        .identifier(userGroupIdentifier)
                                        .notificationConfigs(notificationSettingConfigDTOS)
                                        .build());
      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(ambiance);
      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);

      verify(ngLogCallback.constructed().get(0), times(2)).saveExecutionLog(anyString());
      verify(ngLogCallback.constructed().get(0), times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification3() throws IOException {
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url =
          "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";

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
          Collections.singletonList(UserGroupDTO.builder()
                                        .identifier(userGroupIdentifier)
                                        .notificationConfigs(notificationSettingConfigDTOS)
                                        .build());
      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(ambiance);
      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);

      verify(ngLogCallback.constructed().get(0), times(2)).saveExecutionLog(anyString());
      verify(ngLogCallback.constructed().get(0), times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
    }
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification4() throws IOException {
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url =
          "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";

      GraphLayoutNode graphLayoutNode1 =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("nodeIdentifier")
              .setNodeType("Approval")
              .setNodeUUID("aBcDeFgH")
              .setName("Node name")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds("nextId").build())
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
          Collections.singletonList(UserGroupDTO.builder()
                                        .identifier(userGroupIdentifier)
                                        .notificationConfigs(notificationSettingConfigDTOS)
                                        .build());
      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(ambiance);
      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);

      verify(ngLogCallback.constructed().get(0), times(2)).saveExecutionLog(anyString());
      verify(ngLogCallback.constructed().get(0), times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
    }
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testSendNotificationForApprovalAction() throws Exception {
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url =
          "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
      TriggeredBy triggeredBy = TriggeredBy.newBuilder().setIdentifier(userId).setUuid(userUuid).build();
      ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).build();
      ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setTriggerInfo(triggerInfo).build();
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", accountId)
                              .putSetupAbstractions("orgIdentifier", orgIdentifier)
                              .putSetupAbstractions("projectIdentifier", projectIdentifier)
                              .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                              .setMetadata(executionMetadata)
                              .build();
      HarnessApprovalInstance approvalInstance =
          HarnessApprovalInstance.builder()
              .approvers(
                  ApproversDTO.builder()
                      .userGroups(new ArrayList<>(Arrays.asList("proj_faulty", "proj_right", "org.org_faulty",
                          "org.org_right", "account.acc_faulty", "account.acc_right", "proj_faulty", "proj_right")))
                      .build())
              .approvalActivities(Collections.singletonList(HarnessApprovalActivity.builder()
                                                                .user(EmbeddedUser.builder().email("email").build())
                                                                .approvedAt(20000000)
                                                                .action(HarnessApprovalAction.APPROVE)
                                                                .build())

                      )
              .build();
      approvalInstance.setAmbiance(ambiance);
      approvalInstance.setCreatedAt(System.currentTimeMillis());
      approvalInstance.setDeadline(2L * System.currentTimeMillis());
      approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
      approvalInstance.setStatus(ApprovalStatus.APPROVED);

      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                          .accountId(accountId)
                                                                          .orgIdentifier(orgIdentifier)
                                                                          .projectIdentifier(projectIdentifier)
                                                                          .pipelineIdentifier(pipelineIdentifier)
                                                                          .name(pipelineName)
                                                                          .build();
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
      List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
      notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(MicrosoftTeamsConfigDTO.builder().build());

      List<UserGroupDTO> userGroupDTOS =
          new ArrayList<>(Arrays.asList(UserGroupDTO.builder()
                                            .identifier("proj_right")
                                            .accountIdentifier(accountId)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .notificationConfigs(notificationSettingConfigDTOS)
                                            .build(),
              UserGroupDTO.builder()
                  .identifier("org_right")
                  .accountIdentifier(accountId)
                  .orgIdentifier(orgIdentifier)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build(),
              UserGroupDTO.builder()
                  .identifier("acc_right")
                  .accountIdentifier(accountId)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build()));

      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(ambiance);
      when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance))
          .thenReturn(Mockito.mock(ILogStreamingStepClient.class));

      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(9)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineName))
          .isEqualTo(pipelineName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.orgName)).isEqualTo(orgName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.projectName))
          .isEqualTo(projectName);

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.status))
          .isEqualTo(ApprovalStatus.APPROVED.toString().toLowerCase(Locale.ROOT));
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.action)).contains("email");

      // get userId in triggeredBy because email is not present
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.triggeredBy)).isEqualTo(userId);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineExecutionLink))
          .isEqualTo(url);
      assertThat(notificationChannels.get(8).getTemplateId())
          .isEqualTo(PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_MSTEAMS.getIdentifier());
      assertThat(notificationChannels.get(8).getTeam()).isEqualTo(Team.PIPELINE);
      verify(pmsExecutionService, times(1))
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testSendNotificationForRejectionAction() throws Exception {
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url =
          "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
      TriggeredBy triggeredBy = TriggeredBy.newBuilder().setIdentifier(userId).setUuid(userUuid).build();
      ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).build();
      ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setTriggerInfo(triggerInfo).build();
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", accountId)
                              .putSetupAbstractions("orgIdentifier", orgIdentifier)
                              .putSetupAbstractions("projectIdentifier", projectIdentifier)
                              .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                              .setMetadata(executionMetadata)
                              .build();
      HarnessApprovalInstance approvalInstance =
          HarnessApprovalInstance.builder()
              .approvers(
                  ApproversDTO.builder()
                      .userGroups(new ArrayList<>(Arrays.asList("proj_faulty", "proj_right", "org.org_faulty",
                          "org.org_right", "account.acc_faulty", "account.acc_right", "proj_faulty", "proj_right")))
                      .build())
              .approvalActivities(Collections.singletonList(HarnessApprovalActivity.builder()
                                                                .user(EmbeddedUser.builder().email("email").build())
                                                                .approvedAt(20000000)
                                                                .action(HarnessApprovalAction.REJECT)
                                                                .build())

                      )
              .build();
      approvalInstance.setAmbiance(ambiance);
      approvalInstance.setCreatedAt(System.currentTimeMillis());
      approvalInstance.setDeadline(2L * System.currentTimeMillis());
      approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
      approvalInstance.setStatus(ApprovalStatus.REJECTED);

      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                          .accountId(accountId)
                                                                          .orgIdentifier(orgIdentifier)
                                                                          .projectIdentifier(projectIdentifier)
                                                                          .pipelineIdentifier(pipelineIdentifier)
                                                                          .name(pipelineName)
                                                                          .build();
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
      List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
      notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());
      notificationSettingConfigDTOS.add(MicrosoftTeamsConfigDTO.builder().build());

      List<UserGroupDTO> userGroupDTOS =
          new ArrayList<>(Arrays.asList(UserGroupDTO.builder()
                                            .identifier("proj_right")
                                            .accountIdentifier(accountId)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .notificationConfigs(notificationSettingConfigDTOS)
                                            .build(),
              UserGroupDTO.builder()
                  .identifier("org_right")
                  .accountIdentifier(accountId)
                  .orgIdentifier(orgIdentifier)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build(),
              UserGroupDTO.builder()
                  .identifier("acc_right")
                  .accountIdentifier(accountId)
                  .notificationConfigs(notificationSettingConfigDTOS)
                  .build()));

      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(ambiance);
      when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance))
          .thenReturn(Mockito.mock(ILogStreamingStepClient.class));

      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(9)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineName))
          .isEqualTo(pipelineName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.orgName)).isEqualTo(orgName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.projectName))
          .isEqualTo(projectName);

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.status))
          .isEqualTo(ApprovalStatus.REJECTED.toString().toLowerCase(Locale.ROOT));
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.action)).contains("email");

      // get userId in triggeredBy because email is not present
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.triggeredBy)).isEqualTo(userId);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineExecutionLink))
          .isEqualTo(url);
      assertThat(notificationChannels.get(8).getTemplateId())
          .isEqualTo(PredefinedTemplate.HARNESS_APPROVAL_ACTION_NOTIFICATION_MSTEAMS.getIdentifier());
      assertThat(notificationChannels.get(8).getTeam()).isEqualTo(Team.PIPELINE);
      verify(pmsExecutionService, times(1))
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testSendNotificationForApprovalActionWithParallelStages() throws Exception {
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      String url =
          "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
      TriggeredBy triggeredBy = TriggeredBy.newBuilder().setIdentifier(userId).setUuid(userUuid).build();
      ExecutionTriggerInfo triggerInfo = ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).build();
      ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setTriggerInfo(triggerInfo).build();
      Ambiance ambiance = Ambiance.newBuilder()
                              .putSetupAbstractions("accountId", accountId)
                              .putSetupAbstractions("orgIdentifier", orgIdentifier)
                              .putSetupAbstractions("projectIdentifier", projectIdentifier)
                              .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                              .setMetadata(executionMetadata)
                              .build();
      HarnessApprovalInstance approvalInstance =
          HarnessApprovalInstance.builder()
              .approvers(
                  ApproversDTO.builder()
                      .userGroups(new ArrayList<>(Arrays.asList("proj_faulty", "proj_right", "org.org_faulty",
                          "org.org_right", "account.acc_faulty", "account.acc_right", "proj_faulty", "proj_right")))
                      .build())
              .approvalActivities(Collections.singletonList(HarnessApprovalActivity.builder()
                                                                .user(EmbeddedUser.builder().email("email").build())
                                                                .approvedAt(20000000)
                                                                .action(HarnessApprovalAction.APPROVE)
                                                                .build())

                      )
              .build();
      approvalInstance.setAmbiance(ambiance);
      approvalInstance.setCreatedAt(System.currentTimeMillis());
      approvalInstance.setDeadline(2L * System.currentTimeMillis());
      approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
      approvalInstance.setStatus(ApprovalStatus.APPROVED);
      approvalInstance.setIncludePipelineExecutionHistory(true);

      GraphLayoutNode graphLayoutNode1 =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("parallelnodeIdentifier_1parallel")
              .setNodeType("parallel")
              .setNodeUUID("nodeIdentifier_1")
              .setName("")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder()
                                     .addNextIds("stage_approval1")
                                     .addAllCurrentNodeChildren(List.of("stageBuild-1", "stageBuild-2"))
                                     .build())
              .build();

      GraphLayoutNode graphLayoutNode2 = GraphLayoutNode.newBuilder()
                                             .setNodeIdentifier("stageBuild-1")
                                             .setNodeType("Custom")
                                             .setNodeUUID("stageBuild-1")
                                             .setName("stageBuild-1")
                                             .setNodeGroup("STAGE")
                                             .build();

      GraphLayoutNode graphLayoutNode3 = GraphLayoutNode.newBuilder()
                                             .setNodeIdentifier("stageBuild-2")
                                             .setNodeType("Custom")
                                             .setNodeUUID("stageBuild-2")
                                             .setName("stageBuild-2")
                                             .setNodeGroup("STAGE")
                                             .build();

      GraphLayoutNode graphLayoutNode4 =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("stage_approval1")
              .setNodeType("Approval")
              .setNodeUUID("stage_approval1")
              .setName("Approval stage name")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds("stageDeploy-1").build())
              .build();

      GraphLayoutNode graphLayoutNode5 =
          GraphLayoutNode.newBuilder()
              .setNodeIdentifier("stageDeploy-1")
              .setNodeType("Custom")
              .setNodeUUID("stageDeploy-1")
              .setName("stageDeploy-1")
              .setNodeGroup("STAGE")
              .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds("stageDeploy-2").build())
              .build();

      GraphLayoutNode graphLayoutNode6 = GraphLayoutNode.newBuilder()
                                             .setNodeIdentifier("stageDeploy-2")
                                             .setNodeType("Custom")
                                             .setNodeUUID("stageDeploy-2")
                                             .setName("stageDeploy-2")
                                             .setNodeGroup("STAGE")
                                             .build();

      GraphLayoutNodeDTO graphLayoutNodeDTO1 = GraphLayoutDtoMapper.toDto(graphLayoutNode1);
      graphLayoutNodeDTO1.setStatus(ExecutionStatus.NOTSTARTED);
      GraphLayoutNodeDTO graphLayoutNodeDTO2 = GraphLayoutDtoMapper.toDto(graphLayoutNode2);
      graphLayoutNodeDTO2.setStatus(ExecutionStatus.SUCCESS);
      GraphLayoutNodeDTO graphLayoutNodeDTO3 = GraphLayoutDtoMapper.toDto(graphLayoutNode3);
      graphLayoutNodeDTO3.setStatus(ExecutionStatus.SUCCESS);
      GraphLayoutNodeDTO graphLayoutNodeDTO4 = GraphLayoutDtoMapper.toDto(graphLayoutNode4);
      graphLayoutNodeDTO4.setStatus(ExecutionStatus.APPROVAL_WAITING);
      GraphLayoutNodeDTO graphLayoutNodeDTO5 = GraphLayoutDtoMapper.toDto(graphLayoutNode5);
      graphLayoutNodeDTO5.setStatus(ExecutionStatus.NOTSTARTED);
      GraphLayoutNodeDTO graphLayoutNodeDTO6 = GraphLayoutDtoMapper.toDto(graphLayoutNode6);
      graphLayoutNodeDTO6.setStatus(ExecutionStatus.NOTSTARTED);

      HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
      layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO1);
      layoutNodeDTOMap.put("stageBuild-1", graphLayoutNodeDTO2);
      layoutNodeDTOMap.put("stageBuild-2", graphLayoutNodeDTO3);
      layoutNodeDTOMap.put("stage_approval1", graphLayoutNodeDTO4);
      layoutNodeDTOMap.put("stageDeploy-1", graphLayoutNodeDTO5);
      layoutNodeDTOMap.put("stageDeploy-2", graphLayoutNodeDTO6);

      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                          .accountId(accountId)
                                                                          .orgIdentifier(orgIdentifier)
                                                                          .projectIdentifier(projectIdentifier)
                                                                          .pipelineIdentifier(pipelineIdentifier)
                                                                          .name(pipelineName)
                                                                          .layoutNodeMap(layoutNodeDTOMap)
                                                                          .startingNodeId(startingNodeId)
                                                                          .build();
      doReturn(pipelineExecutionSummaryEntity)
          .when(pmsExecutionService)
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
      List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
      notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
      List<UserGroupDTO> userGroupDTOS =
          new ArrayList<>(Arrays.asList(UserGroupDTO.builder()
                                            .identifier("proj_right")
                                            .accountIdentifier(accountId)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .notificationConfigs(notificationSettingConfigDTOS)
                                            .build()));

      Call<ResponseDTO<List<UserGroupDTO>>> responseDTOCall = mock(Call.class);
      when(userGroupClient.getFilteredUserGroups(any())).thenReturn(responseDTOCall);
      ResponseDTO<List<UserGroupDTO>> restResponse = ResponseDTO.newResponse(userGroupDTOS);
      Response<ResponseDTO<List<UserGroupDTO>>> response = Response.success(restResponse);
      when(responseDTOCall.execute()).thenReturn(response);

      approvalInstance.setValidatedUserGroups(userGroupDTOS);

      doReturn(url).when(notificationHelper).generateUrl(ambiance);
      when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance))
          .thenReturn(Mockito.mock(ILogStreamingStepClient.class));

      approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
      ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor =
          ArgumentCaptor.forClass(NotificationChannel.class);
      verify(notificationClient, times(1)).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
      List<NotificationChannel> notificationChannels = notificationChannelArgumentCaptor.getAllValues();
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineName))
          .isEqualTo(pipelineName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.orgName)).isEqualTo(orgName);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.projectName))
          .isEqualTo(projectName);

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.finishedStages))
          .isEqualTo("stageBuild-1, stageBuild-2");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.runningStages))
          .isEqualTo("Approval stage name");
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.upcomingStages))
          .isEqualTo("stageDeploy-1, stageDeploy-2");

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.status))
          .isEqualTo(ApprovalStatus.APPROVED.toString().toLowerCase(Locale.ROOT));

      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.triggeredBy)).isEqualTo(userId);
      assertThat(notificationChannels.get(0).getTemplateData().get(ApprovalSummaryKeys.pipelineExecutionLink))
          .isEqualTo(url);
      verify(pmsExecutionService, times(1))
          .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }
  }
}