/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType.SLOT_TRAFFIC_SHIFT;
import static io.harness.rule.OwnerRule.VLICA;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppTrafficShiftRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTrafficShiftResponse;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class AzureWebAppTrafficShiftStepTest extends CDNGTestBase {
  @Mock private AzureWebAppStepHelper stepHelper;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  @InjectMocks private AzureWebAppTrafficShiftStep azureWebAppTrafficShiftStep;

  private AzureWebAppTrafficShiftStepParameters parameters =
      AzureWebAppTrafficShiftStepParameters.infoBuilder()
          .traffic(ParameterField.createValueField("10.0"))
          .delegateSelectors(ParameterField.createValueField(List.of(new TaskSelectorYaml("selector-1"))))
          .build();
  private final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();
  private final Ambiance ambiance = getAmbiance();
  private final TaskRequest taskRequest = TaskRequest.newBuilder().build();
  private AzureWebAppInfraDelegateConfig azureInfra =
      AzureWebAppInfraDelegateConfig.builder().appName("webAppName").deploymentSlot("deploymentSlotName").build();

  @Before
  public void testSetup() {
    doReturn(azureInfra).when(stepHelper).getInfraDelegateConfig(ambiance);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    doReturn(taskRequest)
        .when(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), any(TaskParameters.class),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), anyList());

    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);

    TaskRequest taskRequest =
        azureWebAppTrafficShiftStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isEqualTo(this.taskRequest);

    verify(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), eq(singletonList(SLOT_TRAFFIC_PERCENTAGE)));

    AzureWebAppTrafficShiftRequest requestParameters =
        (AzureWebAppTrafficShiftRequest) taskParametersArgumentCaptor.getValue();

    assertThat(requestParameters.getRequestType()).isEqualTo(SLOT_TRAFFIC_SHIFT);
    assertThat(requestParameters.getTrafficPercentage()).isEqualTo(10);
    assertThat(requestParameters.getInfrastructure().getDeploymentSlot()).isEqualTo("deploymentSlotName");
    assertThat(requestParameters.getInfrastructure().getAppName()).isEqualTo("webAppName");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testHandleResponseWithSecurityContext() throws Exception {
    List<UnitProgress> unitProgresses = singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    AzureWebAppTaskResponse azureWebAppTaskResponse =
        AzureWebAppTaskResponse.builder()
            .commandUnitsProgress(unitProgressData)
            .requestResponse(
                AzureWebAppTrafficShiftResponse.builder().deploymentProgressMarker(SLOT_TRAFFIC_PERCENTAGE).build())
            .build();

    StepResponse stepResponse = azureWebAppTrafficShiftStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> azureWebAppTaskResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateExceptionIsThrownIfFFisNotEnabled() {
    assertThatThrownBy(()
                           -> azureWebAppTrafficShiftStep.handleTaskResultWithSecurityContext(
                               ambiance, stepElementParameters, () -> null))
        .isInstanceOf(Exception.class);
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }
}
