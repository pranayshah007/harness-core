/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.common.EntityTypeConstants;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogLine;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.helpers.CurrentUserHelper;
import io.harness.remote.client.NGRestUtils;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.dto.UserPrincipal;
import io.harness.steps.approval.step.ApprovalInstanceResponseMapper;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivity;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.user.remote.UserClient;
import io.harness.usergroups.UserGroupClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class ApprovalResourceImplServiceImplTest extends CategoryTest {
  @Mock private ApprovalInstanceService approvalInstanceService;
  @Mock private ApprovalInstanceResponseMapper approvalInstanceResponseMapper;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private UserGroupClient userGroupClient;
  @Mock private CurrentUserHelper currentUserHelper;
  @Mock private UserClient userClient;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;

  ApprovalResourceServiceImpl approvalResourceService;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    approvalResourceService = new ApprovalResourceServiceImpl(approvalInstanceService, approvalInstanceResponseMapper,
        planExecutionService, userGroupClient, currentUserHelper, userClient, logStreamingStepClientFactory);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGet() {
    String id = "dummy";
    ApprovalInstanceResponseDTO approvalInstanceResponseDTO = ApprovalInstanceResponseDTO.builder().id(id).build();
    ApprovalInstance approvalInstance = HarnessApprovalInstance.builder().build();
    approvalInstance.setId(id);
    when(approvalInstanceService.get(id)).thenReturn(approvalInstance);
    when(approvalInstanceResponseMapper.toApprovalInstanceResponseDTO(approvalInstance))
        .thenReturn(approvalInstanceResponseDTO);
    assertEquals(approvalResourceService.get(id), approvalInstanceResponseDTO);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAddHarnessApprovalActivity() throws IOException {
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    String uuid = "uuid";
    String id = "dummy";
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .build();
    EmbeddedUser embeddedUser = EmbeddedUser.builder().email("email").name("name").uuid(uuid).build();
    List<String> userGroups = new ArrayList<>();
    userGroups.add("approver");
    HarnessApprovalInstance harnessApprovalInstance =
        HarnessApprovalInstance.builder().approvers(ApproversDTO.builder().userGroups(userGroups).build()).build();
    harnessApprovalInstance.setAmbiance(ambiance);
    HarnessApprovalActivityRequestDTO harnessApprovalActivityRequestDTO =
        HarnessApprovalActivityRequestDTO.builder().build();
    when(approvalInstanceService.getHarnessApprovalInstance(id)).thenReturn(harnessApprovalInstance);
    List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder().build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    when(currentUserHelper.getPrincipalFromSecurityContext())
        .thenReturn(new UserPrincipal("email@harness.io", "name", "user", "ACCOUNTID"));
    Call userCall = mock(Call.class);
    when(userClient.getUserById("email@harness.io")).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    // Should approve successfully
    approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO);

    harnessApprovalInstance.getApprovers().setUserGroups(Collections.emptyList());
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");

    harnessApprovalInstance.getApprovers().setDisallowPipelineExecutor(true);
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder()
                                     .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                         .setTriggeredBy(TriggeredBy.newBuilder().setUuid(uuid).build())
                                                         .build())
                                     .build();
    when(planExecutionService.getExecutionMetadataFromPlanExecution(any())).thenReturn(metadata);
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");

    harnessApprovalInstance.setApprovalActivities(
        Collections.singletonList(HarnessApprovalActivity.builder().user(embeddedUser).build()));
    assertThatCode(() -> approvalResourceService.addHarnessApprovalActivity(id, harnessApprovalActivityRequestDTO))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User not authorized to approve/reject");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testSnippetWithServiceNowCreateUpdate() throws IOException {
    String yaml = approvalResourceService.getYamlSnippet(ApprovalType.SERVICENOW_APPROVAL, "accountId");
    assertThat(yaml.contains(EntityTypeConstants.SERVICENOW_CREATE)).isTrue();
    assertThat(yaml.contains(EntityTypeConstants.SERVICENOW_UPDATE)).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testRejectPreviousExecutions() throws IOException {
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    EmbeddedUser embeddedUser = EmbeddedUser.builder().email("email").name("name").uuid("uuid").build();
    List<String> userGroups = new ArrayList<>();
    userGroups.add("approver");
    String accountId = "accountId";
    String orgId = "orgId";
    String projId = "projectId";
    String pipelineId = "pipelineId";
    Ambiance ambiance1 = Ambiance.newBuilder()
                             .setPlanExecutionId("planId1")
                             .putSetupAbstractions("accountId", accountId)
                             .putSetupAbstractions("orgIdentifier", orgId)
                             .putSetupAbstractions("projectIdentifier", projId)
                             .build();
    Ambiance ambiance2 = Ambiance.newBuilder()
                             .setPlanExecutionId("planId2")
                             .putSetupAbstractions("accountId", accountId)
                             .putSetupAbstractions("orgIdentifier", orgId)
                             .putSetupAbstractions("projectIdentifier", projId)
                             .build();
    HarnessApprovalInstance newInstance = HarnessApprovalInstance.builder()
                                              .approvalKey("approvalKey")
                                              .approvalMessage("message")
                                              .includePipelineExecutionHistory(false)
                                              .approvers(ApproversDTO.builder().userGroups(userGroups).build())
                                              .approvalActivities(Collections.emptyList())
                                              .isAutoRejectEnabled(true)
                                              .build();
    newInstance.setId("uuid1");
    newInstance.setAccountId(accountId);
    newInstance.setOrgIdentifier(orgId);
    newInstance.setProjectIdentifier(projId);
    newInstance.setPipelineIdentifier(pipelineId);
    newInstance.setAmbiance(ambiance1);
    HarnessApprovalInstance oldInstance =
        HarnessApprovalInstance.builder()
            .approvalKey("approvalKey")
            .approvalMessage("message")
            .includePipelineExecutionHistory(false)
            .approvers(ApproversDTO.builder().userGroups(userGroups).build())
            .approvalActivities(Collections.singletonList(
                HarnessApprovalActivity.builder().action(HarnessApprovalAction.APPROVE).user(embeddedUser).build()))
            .isAutoRejectEnabled(true)
            .build();
    oldInstance.setId("uuid2");
    oldInstance.setAccountId(accountId);
    oldInstance.setOrgIdentifier(orgId);
    oldInstance.setProjectIdentifier(projId);
    oldInstance.setPipelineIdentifier(pipelineId);
    oldInstance.setAmbiance(ambiance2);
    List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder().build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    when(currentUserHelper.getPrincipalFromSecurityContext())
        .thenReturn(new UserPrincipal("email@harness.io", "name", "user", "ACCOUNTID"));
    Call userCall = mock(Call.class);
    when(userClient.getUserById("email@harness.io")).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse(Optional.of(UserInfo.builder().build()))));
    List<String> approvalInstanceIds = Collections.singletonList("uuid2");
    when(approvalInstanceService.findAllPreviousWaitingApprovals(
             accountId, orgId, projId, pipelineId, "approvalKey", ambiance1))
        .thenReturn(approvalInstanceIds);
    when(approvalInstanceService.getHarnessApprovalInstance("uuid2")).thenReturn(oldInstance);
    ILogStreamingStepClient stepClient = Mockito.mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance1)).thenReturn(stepClient);
    ArgumentCaptor<LogLine> logLineArgumentCaptor = ArgumentCaptor.forClass(LogLine.class);
    ArgumentCaptor<Boolean> booleanArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    doNothing().when(approvalInstanceService).rejectPreviousExecutions(anyString(), any(), anyBoolean(), any());
    approvalResourceService.rejectPreviousExecutions(newInstance);
    verify(approvalInstanceService, times(1))
        .rejectPreviousExecutions(stringArgumentCaptor.capture(), any(), booleanArgumentCaptor.capture(), any());
    verify(stepClient, times(1)).writeLogLine(logLineArgumentCaptor.capture(), anyString());
    assertThat(stringArgumentCaptor.getValue()).isEqualTo("uuid2");
    assertThat(booleanArgumentCaptor.getValue()).isEqualTo(false);
    oldInstance.getApprovers().setUserGroups(Collections.emptyList());
    ArgumentCaptor<Boolean> booleanArgumentCaptor2 = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<String> stringArgumentCaptor2 = ArgumentCaptor.forClass(String.class);
    approvalResourceService.rejectPreviousExecutions(newInstance);
    verify(approvalInstanceService, times(2))
        .rejectPreviousExecutions(stringArgumentCaptor2.capture(), any(), booleanArgumentCaptor2.capture(), any());
    assertThat(stringArgumentCaptor2.getValue()).isEqualTo("uuid2");
    assertThat(booleanArgumentCaptor2.getValue()).isEqualTo(true);
    assertThat(logLineArgumentCaptor.getValue().getMessage())
        .isEqualTo(
            "Successfully rejected 1 previous executions waiting for approval on this step that the user was authorized to reject");
  }
}
