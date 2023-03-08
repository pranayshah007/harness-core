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
import io.harness.delegate.beans.terraformcloud.PlanType;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskType;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudOrganizationsTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRollbackTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRunTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudValidateTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudWorkspacesTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.terraformcloud.TerraformCloudApiTokenCredentials;
import io.harness.terraformcloud.TerraformCloudConfig;
import io.harness.terraformcloud.model.Attributes;
import io.harness.terraformcloud.model.Relationship;
import io.harness.terraformcloud.model.ResourceLinkage;
import io.harness.terraformcloud.model.RunData;
import io.harness.terraformcloud.model.RunRequest;
import io.harness.terraformcloud.model.RunStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
  public void testValidateTaskTypeSuccessfully() throws IOException {
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
  public void testValidateTaskTypeFailed() throws IOException {
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
  public void testGetOrganizations() throws IOException {
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
  public void testGetWorkspaces() throws IOException {
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
  public void testRunRefreshStateTaskType() throws IOException {
    RunData runData = new RunData();
    runData.setId("run-123");
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    doReturn(runData).when(terraformCloudTaskHelper).createRun(any(), any(), any(), anyBoolean(), any());
    doReturn(new ArrayList<>()).when(terraformCloudTaskHelper).getPolicyCheckData(any(), any(), any());
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_REFRESH_STATE);
    on(task).set("runRequestCreator", runRequestCreator);
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRunTaskResponse.class);
    verify(terraformCloudTaskHelper, times(1))
        .createRun(any(), any(), runRequestArgumentCaptor.capture(), anyBoolean(), any());
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isRefreshOnly());
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isAutoApply());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    verify(terraformCloudTaskHelper, times(1)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).streamSentinelPolicies(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(0)).uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanOnlyTaskType() throws IOException {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TaskParameters taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_PLAN_ONLY);
    RunData runData = new RunData();
    runData.setId("run-123");
    on(task).set("runRequestCreator", runRequestCreator);
    ArgumentCaptor<RunRequest> runRequestArgumentCaptor = ArgumentCaptor.forClass(RunRequest.class);
    doReturn(runData)
        .when(terraformCloudTaskHelper)
        .createRun(any(), any(), runRequestArgumentCaptor.capture(), anyBoolean(), any());
    doReturn(new ArrayList<>()).when(terraformCloudTaskHelper).getPolicyCheckData(any(), any(), any());
    doReturn("policyCheckJsonId")
        .when(terraformCloudTaskHelper)
        .uploadJsonFile(any(), any(), any(), any(), any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRunTaskResponse.class);
    TerraformCloudRunTaskResponse tfcResponse = (TerraformCloudRunTaskResponse) delegateResponseData;
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanOnly());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfPlanJsonFileId()).isNull();
    assertThat(tfcResponse.getTfOutput()).isNull();
    assertThat(tfcResponse.getPolicyChecksJsonFileId()).isEqualTo("policyCheckJsonId");
    verify(terraformCloudTaskHelper, times(1)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).streamSentinelPolicies(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanAndApplyTaskType() throws IOException {
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

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRunTaskResponse.class);
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanAndApply());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    TerraformCloudRunTaskResponse tfcResponse = (TerraformCloudRunTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfPlanJsonFileId()).isNull();
    assertThat(tfcResponse.getTfOutput()).isEqualTo("output");
    verify(terraformCloudTaskHelper, times(1)).streamApplyLogs(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanAndApplyTaskTypeWhenPolicyShouldOverride() throws IOException {
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

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRunTaskResponse.class);
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanAndApply());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    TerraformCloudRunTaskResponse tfcResponse = (TerraformCloudRunTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfPlanJsonFileId()).isNull();
    assertThat(tfcResponse.getTfOutput()).isEqualTo("output");
    verify(terraformCloudTaskHelper, times(1)).streamApplyLogs(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(2)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).overridePolicy(any(), any(), any(), any());
  }

  @Test(expected = TaskNGDataException.class)
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanAndApplyTaskTypeWhenPolicyCantOverride() {
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
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanAndDestroyTaskType() throws IOException {
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

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRunTaskResponse.class);
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isPlanAndApply());
    assertTrue(runRequestArgumentCaptor.getValue().getData().getAttributes().isDestroy());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    TerraformCloudRunTaskResponse tfcResponse = (TerraformCloudRunTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfPlanJsonFileId()).isNull();
    assertThat(tfcResponse.getTfOutput()).isEqualTo("output");
    verify(terraformCloudTaskHelper, times(1)).streamApplyLogs(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).getPolicyCheckData(any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunPlanTaskType() throws IOException {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TerraformCloudTaskParams taskParameters = TerraformCloudTaskParams.builder()
                                                  .workspace(WORKSPACE)
                                                  .organization(ORG)
                                                  .accountId("accountId")
                                                  .entityId("entityId")
                                                  .planType(PlanType.APPLY)
                                                  .terraformCloudTaskType(TerraformCloudTaskType.RUN_PLAN)
                                                  .build();

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
    doReturn(new ArrayList<>()).when(terraformCloudTaskHelper).getPolicyCheckData(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRunTaskResponse.class);
    assertFalse(runRequestArgumentCaptor.getValue().getData().getAttributes().isDestroy());
    assertThat(runRequestArgumentCaptor.getValue().getData().getRelationships().get("workspace").getData().getId())
        .isEqualTo(WORKSPACE);
    TerraformCloudRunTaskResponse tfcResponse = (TerraformCloudRunTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfPlanJsonFileId()).isEqualTo("jsonId");
    assertThat(tfcResponse.getTfOutput()).isNull();
    assertThat(tfcResponse.getPolicyChecksJsonFileId()).isEqualTo("jsonId");
    verify(terraformCloudTaskHelper, times(2)).uploadJsonFile(any(), any(), any(), any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).streamSentinelPolicies(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunApplyTaskTypeWhenItPolicyCheckedStatus() throws IOException {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TerraformCloudTaskParams taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_APPLY);
    taskParameters.setRunId("run-123");
    doReturn(RunStatus.POLICY_CHECKED).when(terraformCloudTaskHelper).getRunStatus(any(), any(), any());
    doReturn("output").when(terraformCloudTaskHelper).applyRun(any(), any(), any(), any(), any());
    RunData runData = new RunData();
    runData.setId("run-123");
    runData.setAttributes(Attributes.builder().status(RunStatus.POLICY_CHECKED).build());
    doReturn(runData).when(terraformCloudTaskHelper).getRun(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRunTaskResponse.class);
    TerraformCloudRunTaskResponse tfcResponse = (TerraformCloudRunTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfPlanJsonFileId()).isNull();
    assertThat(tfcResponse.getTfOutput()).isEqualTo("output");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunApplyTaskTypeWhenItPolicyOverrideStatus() throws IOException {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TerraformCloudTaskParams taskParameters = getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_APPLY);
    taskParameters.setRunId("run-123");
    RunData runData = new RunData();
    runData.setId("run-123");
    runData.setAttributes(Attributes.builder().status(RunStatus.POLICY_OVERRIDE).build());
    doReturn(runData).when(terraformCloudTaskHelper).getRun(any(), any(), any());
    doReturn(RunStatus.POLICY_CHECKED).when(terraformCloudTaskHelper).getRunStatus(any(), any(), any());
    doReturn("output").when(terraformCloudTaskHelper).applyRun(any(), any(), any(), any(), any());
    doReturn(new ArrayList<>()).when(terraformCloudTaskHelper).getPolicyCheckData(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRunTaskResponse.class);
    TerraformCloudRunTaskResponse tfcResponse = (TerraformCloudRunTaskResponse) delegateResponseData;
    assertThat(tfcResponse.getRunId()).isEqualTo("run-123");
    assertThat(tfcResponse.getTfPlanJsonFileId()).isNull();
    assertThat(tfcResponse.getTfOutput()).isEqualTo("output");
    verify(terraformCloudTaskHelper, times(1)).getRunStatus(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).getPolicyCheckData(any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).overridePolicy(any(), any(), any(), any());
    verify(terraformCloudTaskHelper, times(1)).applyRun(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRunRollbackTaskType() throws IOException {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TaskParameters taskParameters = getRollbackTaskParams();
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
  public void testRunRollbackTaskShouldBeSkipedType() throws IOException {
    doReturn(terraformCloudConfig).when(terraformCloudConfigMapper).mapTerraformCloudConfigWithDecryption(any(), any());
    TaskParameters taskParameters = getRollbackTaskParams();
    on(task).set("runRequestCreator", runRequestCreator);
    doReturn(getRollbackRunData()).when(terraformCloudTaskHelper).getRun(any(), any(), any());
    doReturn("relationshipId").when(terraformCloudTaskHelper).getRelationshipId(any(), any());
    doReturn("run-123").when(terraformCloudTaskHelper).getLastAppliedRunId(any(), any(), any());

    DelegateResponseData delegateResponseData = task.run(taskParameters);

    assertThat(delegateResponseData).isInstanceOf(TerraformCloudRollbackTaskResponse.class);
    verify(terraformCloudTaskHelper, times(0)).createRun(any(), any(), any(), anyBoolean(), any());
    verify(terraformCloudTaskHelper, times(0)).streamApplyLogs(any(), any(), any(), any());
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

  private TaskParameters getRollbackTaskParams() {
    TerraformCloudTaskParams terraformCloudTaskParams = getTerraformCloudTaskParams(TerraformCloudTaskType.ROLLBACK);
    terraformCloudTaskParams.setRunId("run-123");
    terraformCloudTaskParams.setMessage("dummy");
    return terraformCloudTaskParams;
  }

  private TerraformCloudTaskParams getTerraformCloudTaskParams(
      TerraformCloudTaskType taskType, boolean shouldOverride) {
    return TerraformCloudTaskParams.builder()
        .terraformCloudTaskType(taskType)
        .encryptionDetails(null)
        .terraformCloudConnectorDTO(getTerraformCloudConnectorDTO())
        .workspace(WORKSPACE)
        .policyOverride(shouldOverride)
        .organization(ORG)
        .build();
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
