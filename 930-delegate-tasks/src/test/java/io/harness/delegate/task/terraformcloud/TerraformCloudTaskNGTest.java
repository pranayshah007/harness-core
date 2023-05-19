/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.TMACARI;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.terraformcloud.TerraformCloudConfigMapper;
import io.harness.connector.task.terraformcloud.TerraformCloudValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudApplyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudGetLastAppliedTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudGetOrganizationsTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudGetWorkspacesTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanAndApplyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanAndDestroyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanOnlyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudRefreshTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudRollbackTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudValidationTaskParams;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudApplyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudOrganizationsTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanAndApplyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanAndDestroyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanOnlyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRefreshTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRollbackTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudValidateTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudWorkspacesTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.terraformcloud.TerraformCloudApiTokenCredentials;
import io.harness.terraformcloud.TerraformCloudConfig;
import io.harness.terraformcloud.model.Attributes;
import io.harness.terraformcloud.model.PolicyCheckData;
import io.harness.terraformcloud.model.Relationship;
import io.harness.terraformcloud.model.ResourceLinkage;
import io.harness.terraformcloud.model.RunData;
import io.harness.terraformcloud.model.RunRequest;
import io.harness.terraformcloud.model.RunStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudTaskNGTest {
  private static final String token = "t-o-k-e-n";
  private static final String url = "https://some.io";
  private static final String WORKSPACE = "ws-123";
  private static final String ORG = "org-123";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private TerraformCloudConfigMapper terraformCloudConfigMapper;
  @Mock private TerraformCloudValidationHandler terraformCloudValidationHandler;
  @Mock private TerraformCloudTaskHelper terraformCloudTaskHelper;
  @InjectMocks RunRequestCreator runRequestCreator;

  @InjectMocks
  private TerraformCloudTaskNG task = new TerraformCloudTaskNG(
      DelegateTaskPackage.builder().delegateId("delegateId").data(TaskData.builder().build()).build(), null, null,
      null);

  private TerraformCloudConfig terraformCloudConfig;

  @Before
  public void setUp() {
    terraformCloudConfig =
        TerraformCloudConfig.builder()
            .terraformCloudCredentials(TerraformCloudApiTokenCredentials.builder().token(token).url(url).build())
            .build();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidateTaskTypeSuccessfully() {
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.VALIDATE);
    ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                              .status(ConnectivityStatus.SUCCESS)
                                                              .testedAt(System.currentTimeMillis())
                                                              .build();
    doReturn(connectorValidationResult).when(terraformCloudValidationHandler).validate(any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudValidateTaskResponse.class);
    TerraformCloudValidateTaskResponse terraformCloudValidateTaskResponse =
        (TerraformCloudValidateTaskResponse) delegateResponseData;
    assertThat(terraformCloudValidateTaskResponse.getConnectorValidationResult().getDelegateId())
        .isEqualTo("delegateId");
    assertThat(terraformCloudValidateTaskResponse.getConnectorValidationResult().getStatus())
        .isEqualTo(ConnectivityStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidateTaskTypeFailed() {
    TerraformCloudTaskParams taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.VALIDATE);
    ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                              .status(ConnectivityStatus.FAILURE)
                                                              .errorSummary("Some error")
                                                              .testedAt(System.currentTimeMillis())
                                                              .build();
    doReturn(connectorValidationResult).when(terraformCloudValidationHandler).validate(any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    verify(terraformCloudConfigMapper)
        .mapTerraformCloudConfigWithDecryption(
            taskParameters.getTerraformCloudConnectorDTO(), taskParameters.getEncryptionDetails());
    assertThat(delegateResponseData).isInstanceOf(TerraformCloudValidateTaskResponse.class);
    TerraformCloudValidateTaskResponse terraformCloudValidateTaskResponse =
        (TerraformCloudValidateTaskResponse) delegateResponseData;
    assertThat(terraformCloudValidateTaskResponse.getConnectorValidationResult().getStatus())
        .isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(terraformCloudValidateTaskResponse.getConnectorValidationResult().getDelegateId())
        .isEqualTo("delegateId");
    assertThat(terraformCloudValidateTaskResponse.getConnectorValidationResult().getErrorSummary())
        .isEqualTo("Some error");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetOrganizations() {
    Map<String, String> organizationsMap = Collections.singletonMap("id1", "org1");
    TerraformCloudTaskParams taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.GET_ORGANIZATIONS);
    doReturn(organizationsMap).when(terraformCloudTaskHelper).getOrganizationsMap(any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    verify(terraformCloudConfigMapper)
        .mapTerraformCloudConfigWithDecryption(
            taskParameters.getTerraformCloudConnectorDTO(), taskParameters.getEncryptionDetails());
    verify(terraformCloudTaskHelper).getOrganizationsMap(any());
    assertThat(delegateResponseData).isInstanceOf(TerraformCloudOrganizationsTaskResponse.class);
    TerraformCloudOrganizationsTaskResponse terraformCloudOrganizationsTaskResponse =
        (TerraformCloudOrganizationsTaskResponse) delegateResponseData;
    assertThat(terraformCloudOrganizationsTaskResponse.getOrganizations()).isEqualTo(organizationsMap);
    assertThat(terraformCloudOrganizationsTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetWorkspaces() {
    Map<String, String> workspacesMap = Collections.singletonMap("id1", "ws1");
    TerraformCloudTaskParams taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.GET_WORKSPACES);
    doReturn(workspacesMap).when(terraformCloudTaskHelper).getWorkspacesMap(any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    verify(terraformCloudConfigMapper)
        .mapTerraformCloudConfigWithDecryption(
            taskParameters.getTerraformCloudConnectorDTO(), taskParameters.getEncryptionDetails());
    verify(terraformCloudTaskHelper).getWorkspacesMap(any(), any());
    assertThat(delegateResponseData).isInstanceOf(TerraformCloudWorkspacesTaskResponse.class);
    TerraformCloudWorkspacesTaskResponse terraformCloudWorkspacesTaskResponse =
        (TerraformCloudWorkspacesTaskResponse) delegateResponseData;
    assertThat(terraformCloudWorkspacesTaskResponse.getWorkspaces()).isEqualTo(workspacesMap);
    assertThat(terraformCloudWorkspacesTaskResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunRefreshStateTaskType() {
    RunData runData = new RunData();
    runData.setId("run-123");
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    doReturn(runData).when(terraformCloudTaskHelper).createRun(any(), any(), any(), anyBoolean(), any());
    doReturn(Collections.singletonList(new PolicyCheckData()))
        .when(terraformCloudTaskHelper)
        .getPolicyCheckData(any(), any(), any());
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_REFRESH_STATE);
    on(task).set("runRequestCreator", runRequestCreator);
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRefreshTaskResponse.class);
    verify(terraformCloudTaskHelper, times(1))
        .createRun(any(), any(), runRequestArgumentCaptor.capture(), anyBoolean(), any());
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isRefreshOnly());
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isAutoApply());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    TerraformCloudRefreshTaskResponse terraformCloudRefreshTaskResponse =
        (TerraformCloudRefreshTaskResponse) delegateResponseData;
    assertThat(terraformCloudRefreshTaskResponse.getRunId()).isEqualTo("run-123");
    verify(terraformCloudTaskHelper, times(1)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).streamSentinelPolicies(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(0)).uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanOnlyTaskType() {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_PLAN_ONLY);
    RunData runData = new RunData();
    runData.setId("run-123");
    on(task).set("runRequestCreator", runRequestCreator);
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(runData)
        .when(terraformCloudTaskHelper)
        .createRun(any(), any(), runRequestArgumentCaptor.capture(), anyBoolean(), any());
    doReturn(List.of(new PolicyCheckData(), new PolicyCheckData()))
        .when(terraformCloudTaskHelper)
        .getPolicyCheckData(any(), any(), any());
    doReturn("policyCheckJsonId")
        .when(terraformCloudTaskHelper)
        .uploadJsonFile(any(), any(), any(), any(), any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudPlanOnlyTaskResponse.class);
    TerraformCloudPlanOnlyTaskResponse tfcResponse = (TerraformCloudPlanOnlyTaskResponse) delegateResponseData;
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanOnly());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfPlanJsonFileId()).isNull();
    assertThat(tfcResponse.getPolicyChecksJsonFileId()).isEqualTo("policyCheckJsonId");
    verify(terraformCloudTaskHelper, times(2)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).streamSentinelPolicies(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanAndApplyTaskType() {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_PLAN_AND_APPLY);
    RunData runData = new RunData();
    runData.setId("run-123");
    runData.setAttributes(Attributes.builder().status(RunStatus.POLICY_CHECKED).build());
    on(task).set("runRequestCreator", runRequestCreator);
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(runData)
        .when(terraformCloudTaskHelper)
        .createRun(any(), any(), runRequestArgumentCaptor.capture(), anyBoolean(), any());
    doReturn("output").when(terraformCloudTaskHelper).getApplyOutput(any(), any(), any());
    doReturn(new ArrayList<>()).when(terraformCloudTaskHelper).getPolicyCheckData(any(), any(), any());
    doReturn("policyCheckJsonId")
        .when(terraformCloudTaskHelper)
        .uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
    doReturn(runData).when(terraformCloudTaskHelper).getRun(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudPlanAndApplyTaskResponse.class);
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanAndApply());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    TerraformCloudPlanAndApplyTaskResponse tfcResponse = (TerraformCloudPlanAndApplyTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfOutput()).isEqualTo("output");
    verify(terraformCloudTaskHelper, times(1)).streamApplyLogs(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanAndApplyTaskTypeWhenPolicyShouldOverride() {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_PLAN_AND_APPLY, true);
    RunData runData = new RunData();
    runData.setId("run-123");
    runData.setAttributes(Attributes.builder().status(RunStatus.POLICY_OVERRIDE).build());
    on(task).set("runRequestCreator", runRequestCreator);
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(runData)
        .when(terraformCloudTaskHelper)
        .createRun(any(), any(), runRequestArgumentCaptor.capture(), anyBoolean(), any());
    doReturn("output").when(terraformCloudTaskHelper).getApplyOutput(any(), any(), any());
    doReturn(new ArrayList<>()).when(terraformCloudTaskHelper).getPolicyCheckData(any(), any(), any());
    doReturn("policyCheckJsonId")
        .when(terraformCloudTaskHelper)
        .uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
    doReturn(runData).when(terraformCloudTaskHelper).getRun(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudPlanAndApplyTaskResponse.class);
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanAndApply());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    TerraformCloudPlanAndApplyTaskResponse tfcResponse = (TerraformCloudPlanAndApplyTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfOutput()).isEqualTo("output");
    verify(terraformCloudTaskHelper, times(1)).streamApplyLogs(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(2)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).overridePolicy(any(), any(), any(), any());
  }

  @Test(expected = TaskNGDataException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanAndApplyTaskTypeWhenPolicyCantOverride() throws IOException {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_PLAN_AND_APPLY);
    RunData runData = new RunData();
    runData.setId("run-123");
    runData.setAttributes(Attributes.builder().status(RunStatus.POLICY_OVERRIDE).build());
    on(task).set("runRequestCreator", runRequestCreator);
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(runData)
        .when(terraformCloudTaskHelper)
        .createRun(any(), any(), runRequestArgumentCaptor.capture(), anyBoolean(), any());
    doReturn("output").when(terraformCloudTaskHelper).getApplyOutput(any(), any(), any());
    doReturn(new ArrayList<>()).when(terraformCloudTaskHelper).getPolicyCheckData(any(), any(), any());
    doReturn("policyCheckJsonId")
        .when(terraformCloudTaskHelper)
        .uploadJsonFile(any(), any(), any(), any(), any(), any(), any());

    task.run(taskParameters);

    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanAndApply());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    verify(terraformCloudTaskHelper, times(1)).streamApplyLogs(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(0)).overridePolicy(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).discardRun(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanAndDestroyTaskType() {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_PLAN_AND_DESTROY);
    RunData runData = new RunData();
    runData.setId("run-123");
    runData.setAttributes(Attributes.builder().status(RunStatus.POLICY_CHECKED).build());
    on(task).set("runRequestCreator", runRequestCreator);
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(runData)
        .when(terraformCloudTaskHelper)
        .createRun(any(), any(), runRequestArgumentCaptor.capture(), anyBoolean(), any());
    doReturn("output").when(terraformCloudTaskHelper).getApplyOutput(any(), any(), any());
    doReturn(new ArrayList<>()).when(terraformCloudTaskHelper).getPolicyCheckData(any(), any(), any());
    doReturn("policyCheckJsonId")
        .when(terraformCloudTaskHelper)
        .uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
    doReturn(runData).when(terraformCloudTaskHelper).getRun(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudPlanAndDestroyTaskResponse.class);
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanAndApply());
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isDestroy());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    TerraformCloudPlanAndDestroyTaskResponse tfcResponse =
        (TerraformCloudPlanAndDestroyTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfOutput()).isEqualTo("output");
    verify(terraformCloudTaskHelper, times(1)).streamApplyLogs(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).getPolicyCheckData(any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanTaskType() {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TerraformCloudPlanTaskParams taskParameters =
        (TerraformCloudPlanTaskParams) getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_PLAN);

    taskParameters.setExportJsonTfPlan(true);
    RunData runData = new RunData();
    runData.setId("run-123");
    on(task).set("runRequestCreator", runRequestCreator);
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(runData)
        .when(terraformCloudTaskHelper)
        .createRun(any(), any(), runRequestArgumentCaptor.capture(), anyBoolean(), any());
    doReturn("jsonPlan").when(terraformCloudTaskHelper).getJsonPlan(any(), any(), any());
    doReturn("jsonId").when(terraformCloudTaskHelper).uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
    doReturn(List.of(new PolicyCheckData(), new PolicyCheckData()))
        .when(terraformCloudTaskHelper)
        .getPolicyCheckData(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudPlanTaskResponse.class);
    assertFalse(runRequestArgumentCaptor.getValue().getData().getAttributes().isDestroy());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    TerraformCloudPlanTaskResponse tfcResponse = (TerraformCloudPlanTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfPlanJsonFileId()).isEqualTo("jsonId");
    assertThat(tfcResponse.getPolicyChecksJsonFileId()).isEqualTo("jsonId");
    verify(terraformCloudTaskHelper, times(2)).uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(2)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).streamSentinelPolicies(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunApplyTaskTypeWhenItPolicyCheckedStatus() {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TerraformCloudTaskParams taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_APPLY);
    doReturn("output").when(terraformCloudTaskHelper).applyRun(any(), any(), any(), any(), any());
    RunData runData = new RunData();
    runData.setId("run-123");
    runData.setAttributes(Attributes.builder()
                              .status(RunStatus.POLICY_CHECKED)
                              .actions(Attributes.Actions.builder().isConfirmable(true).build())
                              .build());
    doReturn(runData).when(terraformCloudTaskHelper).getRun(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudApplyTaskResponse.class);
    TerraformCloudApplyTaskResponse tfcResponse = (TerraformCloudApplyTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfOutput()).isEqualTo("output");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunApplyTaskTypeWhenItPolicyOverrideStatus() {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TerraformCloudTaskParams taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_APPLY);
    RunData runData = new RunData();
    runData.setId("run-123");
    runData.setAttributes(Attributes.builder().status(RunStatus.POLICY_OVERRIDE).build());
    RunData runData2 = new RunData();
    runData2.setId("run-123");
    runData2.setAttributes(Attributes.builder()
                               .status(RunStatus.POLICY_CHECKED)
                               .actions(Attributes.Actions.builder().isConfirmable(true).build())
                               .build());
    doReturn(runData, runData2).when(terraformCloudTaskHelper).getRun(any(), any(), any());
    doReturn("output").when(terraformCloudTaskHelper).applyRun(any(), any(), any(), any(), any());
    doReturn(new ArrayList<>()).when(terraformCloudTaskHelper).getPolicyCheckData(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudApplyTaskResponse.class);
    TerraformCloudApplyTaskResponse tfcResponse = (TerraformCloudApplyTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfOutput()).isEqualTo("output");
    verify(terraformCloudTaskHelper, times(2)).getRun(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).overridePolicy(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).applyRun(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunRollbackTaskType() {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.ROLLBACK);
    RunData runData = new RunData();
    runData.setId("run-123");
    runData.setAttributes(Attributes.builder().status(RunStatus.POLICY_CHECKED).build());
    on(task).set("runRequestCreator", runRequestCreator);
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(runData)
        .when(terraformCloudTaskHelper)
        .createRun(any(), any(), runRequestArgumentCaptor.capture(), anyBoolean(), any());
    doReturn("output").when(terraformCloudTaskHelper).getApplyOutput(any(), any(), any());
    doReturn(getRollbackRunData()).when(terraformCloudTaskHelper).getRun(any(), any(), any());
    doReturn("relationshipId").when(terraformCloudTaskHelper).getRelationshipId(any(), any());
    doReturn(new ArrayList<>()).when(terraformCloudTaskHelper).getPolicyCheckData(any(), any(), any());
    doReturn("policyCheckJsonId")
        .when(terraformCloudTaskHelper)
        .uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
    doReturn("run-124").when(terraformCloudTaskHelper).getLastAppliedRunId(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRollbackTaskResponse.class);
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanAndApply());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo("relationshipId");
    TerraformCloudRollbackTaskResponse tfcResponse = (TerraformCloudRollbackTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfOutput()).isEqualTo("output");
    verify(terraformCloudTaskHelper, times(1)).streamApplyLogs(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunRollbackTaskShouldBeSkipped() {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.ROLLBACK);
    on(task).set("runRequestCreator", runRequestCreator);
    doReturn(getRollbackRunData()).when(terraformCloudTaskHelper).getRun(any(), any(), any());
    doReturn("relationshipId").when(terraformCloudTaskHelper).getRelationshipId(any(), any());
    doReturn("run-123").when(terraformCloudTaskHelper).getLastAppliedRunId(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRollbackTaskResponse.class);
    verify(terraformCloudTaskHelper, times(0)).createRun(any(), any(), any(), anyBoolean(), any());
    verify(terraformCloudTaskHelper, times(0)).streamApplyLogs(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSkipPolicyCheckWhenThereIsNoAny() {
    RunData runData = new RunData();
    runData.setId("run-123");
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    doReturn(runData).when(terraformCloudTaskHelper).createRun(any(), any(), any(), anyBoolean(), any());
    doReturn(Collections.emptyList()).when(terraformCloudTaskHelper).getPolicyCheckData(any(), any(), any());
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_REFRESH_STATE);
    on(task).set("runRequestCreator", runRequestCreator);

    task.run(taskParameters);

    verify(terraformCloudTaskHelper, times(1)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(0)).streamSentinelPolicies(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(0)).uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
  }
  private RunData getRollbackRunData() {
    RunData runData = new RunData();
    runData.setRelationships(new HashMap<>());
    runData.setId("run-123");
    Relationship relationshipWorkspace = new Relationship();
    relationshipWorkspace.setData(
        Collections.singletonList(ResourceLinkage.builder().id("ws-123").type("workspaces").build()));
    runData.getRelationships().put("workspace", relationshipWorkspace);
    Relationship relationshipCv = new Relationship();
    relationshipCv.setData(
        Collections.singletonList(ResourceLinkage.builder().id("cv-123").type("configuration-versions").build()));
    runData.getRelationships().put("configuration-version", relationshipCv);
    runData.setAttributes(Attributes.builder().build());
    return runData;
  }

  private TerraformCloudTaskParams getTerraformCloudTaskParams(
      TerraformCloudTaskType taskType, boolean shouldOverride) {
    switch (taskType) {
      case VALIDATE:
        return TerraformCloudValidationTaskParams.builder()
            .encryptionDetails(null)
            .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
            .build();
      case GET_ORGANIZATIONS:
        return TerraformCloudGetOrganizationsTaskParams.builder()
            .encryptionDetails(null)
            .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
            .build();
      case GET_WORKSPACES:
        return TerraformCloudGetWorkspacesTaskParams.builder()
            .encryptionDetails(null)
            .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
            .organization(ORG)
            .build();
      case RUN_REFRESH_STATE:
        return TerraformCloudRefreshTaskParams.builder()
            .encryptionDetails(null)
            .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
            .workspace(WORKSPACE)
            .build();
      case RUN_PLAN_ONLY:
        return TerraformCloudPlanOnlyTaskParams.builder()
            .encryptionDetails(null)
            .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
            .accountId("accountId")
            .entityId("entityId")
            .workspace(WORKSPACE)
            .build();
      case RUN_PLAN_AND_APPLY:
        return TerraformCloudPlanAndApplyTaskParams.builder()
            .encryptionDetails(null)
            .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
            .workspace(WORKSPACE)
            .policyOverride(shouldOverride)
            .build();
      case RUN_PLAN_AND_DESTROY:
        return TerraformCloudPlanAndDestroyTaskParams.builder()
            .encryptionDetails(null)
            .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
            .workspace(WORKSPACE)
            .policyOverride(shouldOverride)
            .build();
      case RUN_PLAN:
        return TerraformCloudPlanTaskParams.builder()
            .workspace(WORKSPACE)
            .accountId("accountId")
            .entityId("entityId")
            .planType(PlanType.APPLY)
            .build();
      case RUN_APPLY:
        return TerraformCloudApplyTaskParams.builder()
            .encryptionDetails(null)
            .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
            .runId("run-123")
            .build();
      case ROLLBACK:
        return TerraformCloudRollbackTaskParams.builder()
            .encryptionDetails(null)
            .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
            .workspace(WORKSPACE)
            .message("dummy")
            .runId("run-123")
            .policyOverride(shouldOverride)
            .build();
      case GET_LAST_APPLIED_RUN:
        return TerraformCloudGetLastAppliedTaskParams.builder()
            .encryptionDetails(null)
            .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
            .workspace(WORKSPACE)
            .build();
      default:
        return null;
    }
  }

  private TerraformCloudTaskParams getTerraformCloudTaskParams(TerraformCloudTaskType taskType) {
    return getTerraformCloudTaskParams(taskType, false);
  }

  private TerraformCloudConnectorDTO getTerraformCloudConnectorDTO() {
    return TerraformCloudConnectorDTO.builder()
        .terraformCloudUrl(url)
        .delegateSelectors(null)
        .credential(TerraformCloudCredentialDTO.builder()
                        .type(TerraformCloudCredentialType.API_TOKEN)
                        .spec(TerraformCloudTokenCredentialsDTO.builder()
                                  .apiToken(SecretRefData.builder().decryptedValue(token.toCharArray()).build())
                                  .build())
                        .build())
        .build();
  }
}
