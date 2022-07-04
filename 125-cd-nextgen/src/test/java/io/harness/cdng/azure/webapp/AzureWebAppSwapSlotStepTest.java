/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.azure.model.AzureConstants.SLOT_SWAP;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.AZURE_WEBAPP_SWAP_SLOTS_OUTCOME;
import static io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType.SWAP_SLOTS;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppSwapSlotsRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppSwapSlotsResponseNG;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
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
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(PowerMockRunner.class)
@PrepareForTest({StepUtils.class})
public class AzureWebAppSwapSlotStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private CDStepHelper cdStepHelper;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private StepHelper stepHelper;
  @Mock private AzureHelperService azureHelperService;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  @InjectMocks private AzureWebAppSwapSlotStep azureWebAppSwapSlotStep;

  private AzureWebAppSwapSlotStepParameters parameters =
      AzureWebAppSwapSlotStepParameters.infoBuilder()
          .delegateSelectors(ParameterField.createValueField(List.of(new TaskSelectorYaml("selector-1"))))
          .build();
  private final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();
  private final Ambiance ambiance = getAmbiance();
  private AzureWebAppInfrastructureOutcome azureInfraOutcome = AzureWebAppInfrastructureOutcome.builder()
                                                                   .connectorRef("connectorRef")
                                                                   .webApp("webAppName")
                                                                   .deploymentSlot("deploymentSlotName")
                                                                   .targetSlot("dummy-production")
                                                                   .build();

  AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder()
                                            .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                            .credential(AzureCredentialDTO.builder().build())
                                            .build();

  @Captor ArgumentCaptor<List<EntityDetail>> captor;

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testSwapSlotObtainTaskAfterRbac() {
    doReturn(azureInfraOutcome).when(cdStepHelper).getInfrastructureOutcome(any());

    when(azureHelperService.getConnector(any())).thenReturn(azureConnectorDTO);
    when(azureHelperService.getEncryptionDetails(any(), any()))
        .thenReturn(Collections.singletonList(EncryptedDataDetail.builder().build()));

    Mockito.mockStatic(StepUtils.class);
    PowerMockito.when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TaskRequest taskRequest =
        azureWebAppSwapSlotStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();

    PowerMockito.verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());

    AzureWebAppSwapSlotsRequest requestParameters =
        (AzureWebAppSwapSlotsRequest) taskDataArgumentCaptor.getValue().getParameters()[0];

    assertThat(requestParameters.getRequestType()).isEqualTo(SWAP_SLOTS);
    assertThat(requestParameters.getTargetSlot()).isEqualTo("dummy-production");
    assertThat(requestParameters.getInfrastructure().getDeploymentSlot()).isEqualTo("deploymentSlotName");
    assertThat(requestParameters.getInfrastructure().getAppName()).isEqualTo("webAppName");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testHandleResponseWithSecurityContext() throws Exception {
    doReturn(azureInfraOutcome).when(cdStepHelper).getInfrastructureOutcome(any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    AzureWebAppTaskResponse azureWebAppTaskResponse =
        AzureWebAppTaskResponse.builder()
            .commandUnitsProgress(unitProgressData)
            .requestResponse(AzureWebAppSwapSlotsResponseNG.builder().deploymentProgressMarker(SLOT_SWAP).build())
            .build();

    StepResponse stepResponse = azureWebAppSwapSlotStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> azureWebAppTaskResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(AZURE_WEBAPP_SWAP_SLOTS_OUTCOME), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateExceptionIsThrownIfFFisNotEnabled() {
    assertThatThrownBy(
        () -> azureWebAppSwapSlotStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> null))
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
