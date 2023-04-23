/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.rule.OwnerRule.BUHA;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.terraformcloud.outcome.TerraformCloudRunOutcome;
import io.harness.cdng.provision.terraformcloud.steps.TerraformCloudRunStep;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.terraformcloud.PlanType;
import io.harness.delegate.task.terraformcloud.TerraformCloudTaskType;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudApplyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudGetLastAppliedTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanTaskParams;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudApplyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudGetLastAppliedTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanAndApplyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanAndDestroyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanOnlyTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudPlanTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRefreshTaskResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({TaskRequestsUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudRunStepTest extends CategoryTest {
  TerraformCloudTestStepUtils utils = new TerraformCloudTestStepUtils();

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private KryoSerializer kryoSerializer;
  @Mock private TerraformCloudStepHelper helper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private StepHelper stepHelper;
  @Mock private TerraformCloudParamsMapper mapper;
  @Mock private EncryptionHelper encryptionHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @InjectMocks private TerraformCloudRunStep terraformCloudRunStep;

  @Captor ArgumentCaptor<List<EntityDetail>> captor;

  private final Ambiance ambiance = utils.getAmbiance();
  private final UnitProgressData unitProgressData =
      UnitProgressData.builder().unitProgresses(Collections.singletonList(UnitProgress.newBuilder().build())).build();

  @Before
  public void setup() {
    Mockito.doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidateResourcesWithTFCConnectorPlan() {
    terraformCloudRunStep.validateResources(ambiance, getStepElementParams(TerraformCloudRunType.PLAN));
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(1);
    assertThat(entityDetails.get(0).getType().name()).isEqualTo("CONNECTORS");
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("tcConnectorRef");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(0).getEntityRef().getProjectIdentifier()).isEqualTo("test-project");
    assertThat(entityDetails.get(0).getEntityRef().getOrgIdentifier()).isEqualTo("test-org");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidateResourcesWithTFCConnectorApply() {
    terraformCloudRunStep.validateResources(ambiance, getStepElementParams(TerraformCloudRunType.APPLY));
    verifyNoInteractions(pipelineRbacHelper);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbacForPlan() {
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();

    doReturn("test-account/test-org/test-project/id").when(helper).generateFullIdentifier(any(), any());

    when(mapper.mapRunSpecToTaskParams(any(), any()))
        .thenReturn(utils.getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_PLAN));
    when(helper.getTerraformCloudConnector(any(), any())).thenReturn(utils.getTerraformCloudConnector());
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any()))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    ArgumentCaptor<List<String>> commandUnitsCaptor = ArgumentCaptor.forClass(List.class);

    TaskChainResponse taskChainResponse = terraformCloudRunStep.startChainLinkAfterRbac(
        ambiance, getStepElementParams(TerraformCloudRunType.PLAN), stepInputPackage);

    assertThat(taskChainResponse).isNotNull();
    assertTrue(taskChainResponse.isChainEnd());
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), commandUnitsCaptor.capture(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformCloudPlanTaskParams taskParameters =
        (TerraformCloudPlanTaskParams) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TerraformCloudTaskType.RUN_PLAN);
    assertThat(taskParameters.getMessage()).isEqualTo("Triggered from Harness");
    assertThat(taskParameters.getTerraformCloudConnectorDTO().getTerraformCloudUrl()).isEqualTo("https://some.io");
    assertThat(taskParameters.getAccountId()).isEqualTo("test-account");
    assertThat(taskParameters.getPlanType()).isEqualTo(PlanType.APPLY);
    assertThat(taskParameters.getWorkspace()).isEqualTo("ws");
    assertThat(commandUnitsCaptor.getValue()).isNotNull();
    assertThat(commandUnitsCaptor.getValue()).contains("Plan");
    assertThat(commandUnitsCaptor.getValue()).contains("Policy check");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbacForPlanAndApply() {
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();

    doReturn("test-account/test-org/test-project/id").when(helper).generateFullIdentifier(any(), any());

    when(mapper.mapRunSpecToTaskParams(any(), any()))
        .thenReturn(utils.getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_PLAN_AND_APPLY));
    when(helper.getTerraformCloudConnector(any(), any())).thenReturn(utils.getTerraformCloudConnector());
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any()))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    ArgumentCaptor<List<String>> commandUnitsCaptor = ArgumentCaptor.forClass(List.class);

    TaskChainResponse taskChainResponse = terraformCloudRunStep.startChainLinkAfterRbac(
        ambiance, getStepElementParams(TerraformCloudRunType.PLAN_AND_APPLY), stepInputPackage);

    assertThat(taskChainResponse).isNotNull();
    assertFalse(taskChainResponse.isChainEnd());
    assertThat(taskChainResponse.getPassThroughData()).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), commandUnitsCaptor.capture(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformCloudGetLastAppliedTaskParams taskParameters =
        (TerraformCloudGetLastAppliedTaskParams) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TerraformCloudTaskType.GET_LAST_APPLIED_RUN);
    assertThat(taskParameters.getTerraformCloudConnectorDTO().getTerraformCloudUrl()).isEqualTo("https://some.io");
    assertThat(taskParameters.getWorkspace()).isEqualTo("ws");
    assertThat(commandUnitsCaptor.getValue()).isNotNull();
    assertThat(commandUnitsCaptor.getValue()).contains("Plan");
    assertThat(commandUnitsCaptor.getValue()).contains("Policy check");
  }
  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(terraformCloudRunStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextRefresh() throws Exception {
    StepResponse stepResponse = terraformCloudRunStep.finalizeExecutionWithSecurityContext(ambiance,
        getStepElementParams(TerraformCloudRunType.REFRESH_STATE), null,
        ()
            -> TerraformCloudRefreshTaskResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                   .unitProgressData(unitProgressData)
                   .runId("run-123")
                   .build());

    TerraformCloudRunOutcome terraformCloudRunOutcome = getOutcomeFromResponse(stepResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(terraformCloudRunOutcome.getRunId()).isEqualTo("run-123");
    verifyNoInteractions(helper);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextPlanOnly() throws Exception {
    doReturn("provisionerId").when(helper).getProvisionIdentifier(any());

    TerraformCloudPlanOnlyTaskResponse response = TerraformCloudPlanOnlyTaskResponse.builder()
                                                      .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                      .unitProgressData(unitProgressData)
                                                      .tfPlanJsonFileId("tfPlanJsonFieldId")
                                                      .runId("run-123")
                                                      .build();
    StepResponse stepResponse = terraformCloudRunStep.finalizeExecutionWithSecurityContext(
        ambiance, getStepElementParams(TerraformCloudRunType.PLAN_ONLY), null, () -> response);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);

    TerraformCloudRunOutcome terraformCloudRunOutcome = getOutcomeFromResponse(stepResponse);
    assertThat(terraformCloudRunOutcome.getJsonFilePath()).isEqualTo("<+terraformCloudPlanJson.\"provisionerId\">");
    assertThat(terraformCloudRunOutcome.getRunId()).isEqualTo("run-123");
    verify(helper, times(1)).saveTerraformCloudPlanExecutionDetails(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextPlanAndApply() throws Exception {
    doReturn(Collections.singletonMap("x1", "y1")).when(helper).parseTerraformOutputs(any());

    TerraformCloudPlanAndApplyTaskResponse terraformCloudRunTaskResponse =
        TerraformCloudPlanAndApplyTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(unitProgressData)
            .runId("run-123")
            .tfOutput("{x1 : y1}")
            .build();

    StepResponse stepResponse = terraformCloudRunStep.finalizeExecutionWithSecurityContext(ambiance,
        getStepElementParams(TerraformCloudRunType.PLAN_AND_APPLY), null, () -> terraformCloudRunTaskResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    TerraformCloudRunOutcome terraformCloudRunOutcome = getOutcomeFromResponse(stepResponse);

    assertThat(terraformCloudRunOutcome.getJsonFilePath()).isNull();
    assertThat(terraformCloudRunOutcome.getRunId()).isEqualTo("run-123");
    assertThat(terraformCloudRunOutcome.getOutputs().get("x1")).isEqualTo("y1");
    verify(helper, times(1)).saveTerraformCloudPlanExecutionDetails(any(), any(), any(), any(), any());
    verify(helper, times(1)).parseTerraformOutputs(any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextPlanAndDestroy() throws Exception {
    doReturn(Collections.singletonMap("x1", "y1")).when(helper).parseTerraformOutputs(any());

    TerraformCloudPlanAndDestroyTaskResponse terraformCloudRunTaskResponse =
        TerraformCloudPlanAndDestroyTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(unitProgressData)
            .runId("run-123")
            .tfOutput("{x1 : y1}")
            .build();
    StepResponse stepResponse = terraformCloudRunStep.finalizeExecutionWithSecurityContext(ambiance,
        getStepElementParams(TerraformCloudRunType.PLAN_AND_DESTROY), null, () -> terraformCloudRunTaskResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    TerraformCloudRunOutcome terraformCloudRunOutcome = getOutcomeFromResponse(stepResponse);

    assertThat(terraformCloudRunOutcome.getJsonFilePath()).isNull();
    assertThat(terraformCloudRunOutcome.getRunId()).isEqualTo("run-123");
    assertThat(terraformCloudRunOutcome.getOutputs().get("x1")).isEqualTo("y1");
    verify(helper, times(1)).saveTerraformCloudPlanExecutionDetails(any(), any(), any(), any(), any());
    verify(helper, times(1)).parseTerraformOutputs(any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextPlan() throws Exception {
    doReturn("provisionerId").when(helper).getProvisionIdentifier(any());
    TerraformCloudPlanTaskResponse terraformCloudRunTaskResponse =
        TerraformCloudPlanTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(unitProgressData)
            .runId("run-123")
            .tfPlanJsonFileId("jsonFileId")
            .build();
    StepResponse stepResponse = terraformCloudRunStep.finalizeExecutionWithSecurityContext(
        ambiance, getStepElementParams(TerraformCloudRunType.PLAN), null, () -> terraformCloudRunTaskResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    TerraformCloudRunOutcome terraformCloudRunOutcome = getOutcomeFromResponse(stepResponse);

    assertThat(terraformCloudRunOutcome.getJsonFilePath()).isEqualTo("<+terraformCloudPlanJson.\"provisionerId\">");
    assertThat(terraformCloudRunOutcome.getRunId()).isEqualTo("run-123");
    assertThat(terraformCloudRunOutcome.getOutputs()).isNull();
    verify(helper, times(1)).saveTerraformCloudPlanExecutionDetails(any(), any(), any(), any(), any(), anyBoolean());
    verify(helper, times(0)).parseTerraformOutputs(any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextApply() throws Exception {
    doReturn(Collections.singletonMap("x1", "y1")).when(helper).parseTerraformOutputs(any());

    TerraformCloudApplyTaskResponse terraformCloudRunTaskResponse =
        TerraformCloudApplyTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(unitProgressData)
            .runId("run-123")
            .tfOutput("{x1 : y1}")
            .build();
    StepResponse stepResponse = terraformCloudRunStep.finalizeExecutionWithSecurityContext(
        ambiance, getStepElementParams(TerraformCloudRunType.APPLY), null, () -> terraformCloudRunTaskResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    TerraformCloudRunOutcome terraformCloudRunOutcome = getOutcomeFromResponse(stepResponse);
    assertThat(terraformCloudRunOutcome.getJsonFilePath()).isNull();
    assertThat(terraformCloudRunOutcome.getRunId()).isEqualTo("run-123");
    assertThat(terraformCloudRunOutcome.getOutputs().get("x1")).isEqualTo("y1");
    verify(helper, times(1)).updateRunDetails(any(), any());
    verify(helper, times(1)).parseTerraformOutputs(any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContext() throws Exception {
    doReturn("test-account/test-org/test-project/id").when(helper).generateFullIdentifier(any(), any());

    when(mapper.mapRunSpecToTaskParams(any(), any()))
        .thenReturn(utils.getTerraformCloudTaskParams(TerraformCloudTaskType.RUN_APPLY));
    when(helper.getTerraformCloudConnector(any(), any())).thenReturn(utils.getTerraformCloudConnector());
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any()))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    ArgumentCaptor<List<String>> commandUnitsCaptor = ArgumentCaptor.forClass(List.class);

    TaskChainResponse taskChainResponse = terraformCloudRunStep.executeNextLinkWithSecurityContext(ambiance,
        getStepElementParams(TerraformCloudRunType.APPLY), null, TerraformCloudPassThroughData.builder().build(),
        ()
            -> TerraformCloudGetLastAppliedTaskResponse.builder()
                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                   .unitProgressData(unitProgressData)
                   .lastAppliedRun("run-123")
                   .build());

    assertThat(taskChainResponse).isNotNull();
    assertTrue(taskChainResponse.isChainEnd());
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), commandUnitsCaptor.capture(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformCloudApplyTaskParams taskParameters =
        (TerraformCloudApplyTaskParams) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TerraformCloudTaskType.RUN_APPLY);
    assertThat(commandUnitsCaptor.getValue()).isNotNull();
    assertThat(commandUnitsCaptor.getValue()).contains("Apply");
    verify(helper, times(1)).saveTerraformCloudConfig(any(), any(), any(), any());
  }

  private StepElementParameters getStepElementParams(TerraformCloudRunType type) {
    return StepElementParameters.builder().spec(utils.getTerraformCloudRunStepParams(type)).build();
  }

  private TerraformCloudRunOutcome getOutcomeFromResponse(StepResponse stepResponse) {
    return stepResponse.getStepOutcomes()
        .stream()
        .map(stepOutcome -> (TerraformCloudRunOutcome) stepOutcome.getOutcome())
        .findFirst()
        .orElseThrow();
  }
}
