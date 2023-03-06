/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
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
import io.harness.cdng.provision.terraformcloud.outcome.TerraformCloudRunOutcome;
import io.harness.cdng.provision.terraformcloud.steps.TerraformCloudRunStep;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskType;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRunTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRunTaskResponse.TerraformCloudRunTaskResponseBuilder;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import java.util.Collections;
import java.util.List;
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
  @InjectMocks private TerraformCloudRunStep terraformCloudRunStep;

  @Captor ArgumentCaptor<List<EntityDetail>> captor;

  private final Ambiance ambiance = utils.getAmbiance();
  private final UnitProgressData unitProgressData =
      UnitProgressData.builder().unitProgresses(Collections.singletonList(UnitProgress.newBuilder().build())).build();

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
  public void testObtainTaskAfterRbac() {
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

    TaskRequest taskRequest = terraformCloudRunStep.obtainTaskAfterRbac(
        ambiance, getStepElementParams(TerraformCloudRunType.PLAN), stepInputPackage);

    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), commandUnitsCaptor.capture(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformCloudTaskParams taskParameters =
        (TerraformCloudTaskParams) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTerraformCloudTaskType()).isEqualTo(TerraformCloudTaskType.RUN_PLAN);
    assertThat(taskParameters.getMessage()).isEqualTo("Triggered from Harness");
    assertThat(taskParameters.getTerraformCloudConnectorDTO().getTerraformCloudUrl()).isEqualTo("https://some.io");
    assertThat(taskParameters.getAccountId()).isEqualTo("test-account");
    assertThat(taskParameters.getPlanType()).isEqualTo(io.harness.delegate.beans.terraformcloud.PlanType.APPLY);
    assertThat(taskParameters.getOrganization()).isEqualTo("org");
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
  public void handleTaskResultWithSecurityContextRefresh() throws Exception {
    StepResponse stepResponse = terraformCloudRunStep.handleTaskResultWithSecurityContext(
        ambiance, getStepElementParams(TerraformCloudRunType.REFRESH_STATE), () -> getResponseBuilder().build());

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isEmpty();
    verifyNoInteractions(helper);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextPlanOnly() throws Exception {
    doReturn("provisionerId").when(helper).getProvisionIdentifier(any());
    doReturn(true).when(helper).isExportTfPlanJson(any());

    TerraformCloudRunTaskResponse response =
        getResponseBuilder().tfPlanJsonFileId("tfPlanJsonFieldId").runId("run-123").build();
    StepResponse stepResponse = terraformCloudRunStep.handleTaskResultWithSecurityContext(
        ambiance, getStepElementParams(TerraformCloudRunType.PLAN_ONLY), () -> response);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);

    TerraformCloudRunOutcome terraformCloudRunOutcome = getOutcomeFromResponse(stepResponse);
    assertThat(terraformCloudRunOutcome.getJsonFilePath()).isEqualTo("<+terraformPlanJson.\"provisionerId\">");
    assertThat(terraformCloudRunOutcome.getRunId()).isEqualTo("run-123");
    verify(helper, times(0)).saveTerraformCloudPlanOutput(any(), any(), any());
    verify(helper, times(1)).isExportTfPlanJson(any());
    verify(helper, times(1)).saveTerraformPlanExecutionDetails(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextPlanAndApply() throws Exception {
    doReturn(false).when(helper).isExportTfPlanJson(any());
    doReturn(Collections.singletonMap("x1", "y1")).when(helper).parseTerraformOutputs(any());

    TerraformCloudRunTaskResponse terraformCloudRunTaskResponse =
        getResponseBuilder().runId("run-123").tfOutput("{x1 : y1}").build();

    StepResponse stepResponse = terraformCloudRunStep.handleTaskResultWithSecurityContext(
        ambiance, getStepElementParams(TerraformCloudRunType.PLAN_AND_APPLY), () -> terraformCloudRunTaskResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    TerraformCloudRunOutcome terraformCloudRunOutcome = getOutcomeFromResponse(stepResponse);

    assertThat(terraformCloudRunOutcome.getJsonFilePath()).isNull();
    assertThat(terraformCloudRunOutcome.getRunId()).isEqualTo("run-123");
    assertThat(terraformCloudRunOutcome.getOutputs().get("x1")).isEqualTo("y1");
    verify(helper, times(0)).saveTerraformCloudPlanOutput(any(), any(), any());
    verify(helper, times(1)).isExportTfPlanJson(any());
    verify(helper, times(1)).saveTerraformPlanExecutionDetails(any(), any(), any(), any());
    verify(helper, times(1)).parseTerraformOutputs(any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextPlanAndDestroy() throws Exception {
    doReturn(false).when(helper).isExportTfPlanJson(any());
    doReturn(Collections.singletonMap("x1", "y1")).when(helper).parseTerraformOutputs(any());

    TerraformCloudRunTaskResponse terraformCloudRunTaskResponse =
        getResponseBuilder().runId("run-123").tfOutput("{x1 : y1}").build();
    StepResponse stepResponse = terraformCloudRunStep.handleTaskResultWithSecurityContext(
        ambiance, getStepElementParams(TerraformCloudRunType.PLAN_AND_DESTROY), () -> terraformCloudRunTaskResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    TerraformCloudRunOutcome terraformCloudRunOutcome = getOutcomeFromResponse(stepResponse);

    assertThat(terraformCloudRunOutcome.getJsonFilePath()).isNull();
    assertThat(terraformCloudRunOutcome.getRunId()).isEqualTo("run-123");
    assertThat(terraformCloudRunOutcome.getOutputs().get("x1")).isEqualTo("y1");
    verify(helper, times(0)).saveTerraformCloudPlanOutput(any(), any(), any());
    verify(helper, times(1)).saveTerraformPlanExecutionDetails(any(), any(), any(), any());
    verify(helper, times(1)).parseTerraformOutputs(any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextPlan() throws Exception {
    doReturn("provisionerId").when(helper).getProvisionIdentifier(any());
    doReturn(true).when(helper).isExportTfPlanJson(any());
    TerraformCloudRunTaskResponse terraformCloudRunTaskResponse =
        getResponseBuilder().runId("run-123").tfPlanJsonFileId("jsonFileId").build();
    StepResponse stepResponse = terraformCloudRunStep.handleTaskResultWithSecurityContext(
        ambiance, getStepElementParams(TerraformCloudRunType.PLAN), () -> terraformCloudRunTaskResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    TerraformCloudRunOutcome terraformCloudRunOutcome = getOutcomeFromResponse(stepResponse);

    assertThat(terraformCloudRunOutcome.getJsonFilePath()).isEqualTo("<+terraformPlanJson.\"provisionerId\">");
    assertThat(terraformCloudRunOutcome.getRunId()).isEqualTo("run-123");
    assertThat(terraformCloudRunOutcome.getOutputs()).isNull();
    verify(helper, times(1)).saveTerraformCloudPlanOutput(any(), any(), any());
    verify(helper, times(1)).saveTerraformPlanExecutionDetails(any(), any(), any(), any());
    verify(helper, times(0)).parseTerraformOutputs(any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextApply() throws Exception {
    doReturn(false).when(helper).isExportTfPlanJson(any());
    doReturn(Collections.singletonMap("x1", "y1")).when(helper).parseTerraformOutputs(any());

    TerraformCloudRunTaskResponse terraformCloudRunTaskResponse =
        getResponseBuilder().runId("run-123").tfOutput("{x1 : y1}").build();
    StepResponse stepResponse = terraformCloudRunStep.handleTaskResultWithSecurityContext(
        ambiance, getStepElementParams(TerraformCloudRunType.APPLY), () -> terraformCloudRunTaskResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    TerraformCloudRunOutcome terraformCloudRunOutcome = getOutcomeFromResponse(stepResponse);
    assertThat(terraformCloudRunOutcome.getJsonFilePath()).isNull();
    assertThat(terraformCloudRunOutcome.getRunId()).isEqualTo("run-123");
    assertThat(terraformCloudRunOutcome.getOutputs().get("x1")).isEqualTo("y1");
    verify(helper, times(0)).saveTerraformCloudPlanOutput(any(), any(), any());
    verify(helper, times(1)).saveTerraformPlanExecutionDetails(any(), any(), any(), any());
    verify(helper, times(1)).parseTerraformOutputs(any());
  }

  private StepElementParameters getStepElementParams(TerraformCloudRunType type) {
    return StepElementParameters.builder().spec(utils.getTerraformCloudRunStepParams(type)).build();
  }

  private TerraformCloudRunTaskResponseBuilder getResponseBuilder() {
    return TerraformCloudRunTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .unitProgressData(unitProgressData)
        .detailedExitCode(2);
  }

  private TerraformCloudRunOutcome getOutcomeFromResponse(StepResponse stepResponse) {
    return stepResponse.getStepOutcomes()
        .stream()
        .map(stepOutcome -> (TerraformCloudRunOutcome) stepOutcome.getOutcome())
        .findFirst()
        .orElseThrow();
  }
}
