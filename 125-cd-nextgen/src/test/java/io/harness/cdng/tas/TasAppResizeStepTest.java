/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.outcome.TasAppResizeDataOutcome;
import io.harness.cdng.tas.outcome.TasSetupDataOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.pcf.CfDeployCommandResult;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.beans.pcf.TasResizeStrategyType;
import io.harness.delegate.task.pcf.request.CfDeployCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfRollbackCommandResponseNG;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.pcf.model.CfCliVersion;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;

public class TasAppResizeStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private static final String APP_ID = "app_id";
  private static final String INDEX = "index";
  private static final String ORG = "Org";
  private static final String SPACE = "Space";

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private final TasAppResizeStepParameters tasAppResizeStepParameters =
      TasAppResizeStepParameters.infoBuilder().tasBasicAppSetupFqn("tasBasic").build();
  private final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                                  .spec(tasAppResizeStepParameters)
                                                                  .timeout(ParameterField.createValueField("10m"))
                                                                  .build();

  @Mock private InstanceInfoService instanceInfoService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private OutcomeService outcomeService;
  @Mock private TaskRequestsUtils taskRequestsUtils;
  @Mock private TasEntityHelper tasEntityHelper;
  @Mock private StepHelper stepHelper;
  @InjectMocks private TasAppResizeStep tasAppResizeStep;

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void getStepParametersClassTest() {
    Class<StepElementParameters> stepElementParametersClass = tasAppResizeStep.getStepParametersClass();
    assertThat(stepElementParametersClass).isEqualTo(StepElementParameters.class);
  }
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextCommandExecutionStatusFailureTest() throws Exception {
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    CfDeployCommandResponseNG responseData = CfDeployCommandResponseNG.builder()
                                                 .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                 .errorMessage("error")
                                                 .unitProgressData(unitProgressData)
                                                 .build();
    StepResponse stepResponse =
        tasAppResizeStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);

    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo(responseData.getErrorMessage());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }
  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextCommandExecutionStatusSuccessTest() throws Exception {
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    CfDeployCommandResult cfDeployCommandResult =
        CfDeployCommandResult.builder()
            .isStandardBG(false)
            .newAppInstances(getCfInstances(Arrays.asList("app1", "app2", "app3")))
            .build();
    CfDeployCommandResponseNG responseData = CfDeployCommandResponseNG.builder()
                                                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                 .unitProgressData(unitProgressData)
                                                 .cfDeployCommandResult(cfDeployCommandResult)
                                                 .build();
    TasAppResizeDataOutcome tasAppResizeDataOutcome =
        TasAppResizeDataOutcome.builder()
            .cfInstanceElements(getCfInstances(Arrays.asList("app1", "app2", "app3")))
            .build();
    StepResponse.StepOutcome stepOutcomeMock = StepResponse.StepOutcome.builder()
                                                   .name(OutcomeExpressionConstants.OUTPUT)
                                                   .outcome(tasAppResizeDataOutcome)
                                                   .build();

    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(ORG).space(SPACE).build();
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doReturn(stepOutcomeMock).when(instanceInfoService).saveServerInstancesIntoSweepingOutput(any(), any());
    doReturn("outcome").when(executionSweepingOutputService).consume(any(), any(), any(), any());

    StepResponse stepResponse =
        tasAppResizeStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    StepResponse.StepOutcome stepOutcome = new ArrayList<>(stepResponse.getStepOutcomes()).get(1);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepOutcome.getOutcome()).isEqualTo(stepOutcomeMock.getOutcome());
    assertThat(stepOutcome.getName()).isEqualTo(stepOutcomeMock.getName());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextCommandExecutionStatusWithNullResponseFromDelegate() {
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG = CfRollbackCommandResponseNG.builder().build();
    assertThatThrownBy(()
                           -> tasAppResizeStep.handleTaskResultWithSecurityContext(
                               ambiance, stepElementParameters, () -> cfRollbackCommandResponseNG))
        .hasMessageContaining("CfRollbackCommandResponseNG cannot be cast to class");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    TasCountInstanceSelection tasCountInstanceSelection =
        TasCountInstanceSelection.builder().value(ParameterField.createValueField("2")).build();
    TasPercentageInstanceSelection tasPercentageInstanceSelection =
        TasPercentageInstanceSelection.builder().value(ParameterField.createValueField("2")).build();
    TasAppResizeStepParameters tasAppResizeStepParameters =
        TasAppResizeStepParameters.infoBuilder()
            .newAppInstances(TasInstanceSelectionWrapper.builder()
                                 .type(TasInstanceUnitType.COUNT)
                                 .spec(tasCountInstanceSelection)
                                 .build())
            .oldAppInstances(TasInstanceSelectionWrapper.builder()
                                 .type(TasInstanceUnitType.PERCENTAGE)
                                 .spec(tasPercentageInstanceSelection)
                                 .build())
            .build();
    TasSetupDataOutcome tasSetupDataOutcome =
        TasSetupDataOutcome.builder()
            .cfCliVersion(CfCliVersion.V7)
            .activeApplicationDetails(
                TasApplicationInfo.builder().applicationGuid(APP_ID).applicationName("app").build())
            .desiredActualFinalCount(4)
            .isBlueGreen(true)
            .newReleaseName("app")
            .resizeStrategy(TasResizeStrategyType.DOWNSCALE_OLD_FIRST)
            .build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(TasConnectorDTO.builder().build()).build();
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(ORG).space(SPACE).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(tasSetupDataOutcome).found(true).build();

    doReturn(optionalSweepingOutput).when(tasEntityHelper).getSetupOutcome(any(), any(), any(), any(), any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doReturn(connectorInfoDTO).when(tasEntityHelper).getConnectorInfoDTO(any(), any(), any(), any());
    doReturn(null).when(tasEntityHelper).getEncryptionDataDetails(any(), any());
    doReturn(null).when(stepHelper).getEnvironmentType(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    stepElementParameters.setSpec(tasAppResizeStepParameters);
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    tasAppResizeStep.obtainTaskAfterRbac(ambiance, stepElementParameters, null);
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    CfDeployCommandRequestNG taskData = (CfDeployCommandRequestNG) taskDataArgumentCaptor.getValue().getParameters()[0];

    assertThat(taskData.getResizeStrategy()).isEqualTo(TasResizeStrategyType.DOWNSCALE_OLD_FIRST);
    assertThat(taskData.getDownSizeCount()).isEqualTo(0);
    assertThat(taskData.getUpsizeCount()).isEqualTo(2);
    assertThat(taskData.getNewReleaseName()).isEqualTo("app");
    assertThat(taskData.getTasInfraConfig().getSpace()).isEqualTo(SPACE);
    assertThat(taskData.getTasInfraConfig().getOrganization()).isEqualTo(ORG);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithNoSetupOutcome() {
    TasCountInstanceSelection tasCountInstanceSelection =
        TasCountInstanceSelection.builder().value(ParameterField.createValueField("2")).build();
    TasPercentageInstanceSelection tasPercentageInstanceSelection =
        TasPercentageInstanceSelection.builder().value(ParameterField.createValueField("2")).build();
    TasAppResizeStepParameters tasAppResizeStepParameters =
        TasAppResizeStepParameters.infoBuilder()
            .newAppInstances(TasInstanceSelectionWrapper.builder()
                                 .type(TasInstanceUnitType.COUNT)
                                 .spec(tasCountInstanceSelection)
                                 .build())
            .oldAppInstances(TasInstanceSelectionWrapper.builder()
                                 .type(TasInstanceUnitType.PERCENTAGE)
                                 .spec(tasPercentageInstanceSelection)
                                 .build())
            .build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(TasConnectorDTO.builder().build()).build();
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(ORG).space(SPACE).build();
    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(false).build();

    doReturn(optionalSweepingOutput).when(tasEntityHelper).getSetupOutcome(any(), any(), any(), any(), any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doReturn(connectorInfoDTO).when(tasEntityHelper).getConnectorInfoDTO(any(), any(), any(), any());
    doReturn(null).when(tasEntityHelper).getEncryptionDataDetails(any(), any());
    doReturn(null).when(stepHelper).getEnvironmentType(any());

    stepElementParameters.setSpec(tasAppResizeStepParameters);
    TaskRequest taskRequest = tasAppResizeStep.obtainTaskAfterRbac(ambiance, stepElementParameters, null);
    assertThat(taskRequest.getSkipTaskRequest().getMessage())
        .isEqualTo("Tas App resize Step was not executed. Skipping .");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithUpsizeFirstResizeStrategy() {
    TasCountInstanceSelection tasCountInstanceSelection =
        TasCountInstanceSelection.builder().value(ParameterField.createValueField("2")).build();
    TasPercentageInstanceSelection tasPercentageInstanceSelection =
        TasPercentageInstanceSelection.builder().value(ParameterField.createValueField("2")).build();
    TasAppResizeStepParameters tasAppResizeStepParameters =
        TasAppResizeStepParameters.infoBuilder()
            .newAppInstances(TasInstanceSelectionWrapper.builder()
                                 .type(TasInstanceUnitType.PERCENTAGE)
                                 .spec(tasPercentageInstanceSelection)
                                 .build())
            .oldAppInstances(TasInstanceSelectionWrapper.builder()
                                 .type(TasInstanceUnitType.COUNT)
                                 .spec(tasCountInstanceSelection)
                                 .build())
            .build();
    TasSetupDataOutcome tasSetupDataOutcome =
        TasSetupDataOutcome.builder()
            .cfCliVersion(CfCliVersion.V7)
            .activeApplicationDetails(
                TasApplicationInfo.builder().applicationGuid(APP_ID).applicationName("app").build())
            .desiredActualFinalCount(4)
            .isBlueGreen(true)
            .newReleaseName("app")
            .resizeStrategy(TasResizeStrategyType.UPSCALE_NEW_FIRST)
            .build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(TasConnectorDTO.builder().build()).build();
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(ORG).space(SPACE).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(tasSetupDataOutcome).found(true).build();

    doReturn(optionalSweepingOutput).when(tasEntityHelper).getSetupOutcome(any(), any(), any(), any(), any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doReturn(connectorInfoDTO).when(tasEntityHelper).getConnectorInfoDTO(any(), any(), any(), any());
    doReturn(null).when(tasEntityHelper).getEncryptionDataDetails(any(), any());
    doReturn(null).when(stepHelper).getEnvironmentType(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    stepElementParameters.setSpec(tasAppResizeStepParameters);
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    tasAppResizeStep.obtainTaskAfterRbac(ambiance, stepElementParameters, null);
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    CfDeployCommandRequestNG taskData = (CfDeployCommandRequestNG) taskDataArgumentCaptor.getValue().getParameters()[0];

    assertThat(taskData.getResizeStrategy()).isEqualTo(TasResizeStrategyType.UPSCALE_NEW_FIRST);
    assertThat(taskData.getDownSizeCount()).isEqualTo(2);
    assertThat(taskData.getUpsizeCount()).isZero();
    assertThat(taskData.getNewReleaseName()).isEqualTo("app");
    assertThat(taskData.getTasInfraConfig().getSpace()).isEqualTo(SPACE);
    assertThat(taskData.getTasInfraConfig().getOrganization()).isEqualTo(ORG);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacIgnoreInstanceCountManifest() {
    TasCountInstanceSelection tasCountInstanceSelection =
        TasCountInstanceSelection.builder().value(ParameterField.createValueField("2")).build();
    TasPercentageInstanceSelection tasPercentageInstanceSelection =
        TasPercentageInstanceSelection.builder().value(ParameterField.createValueField("2")).build();
    TasAppResizeStepParameters tasAppResizeStepParameters =
        TasAppResizeStepParameters.infoBuilder()
            .newAppInstances(TasInstanceSelectionWrapper.builder()
                                 .type(TasInstanceUnitType.COUNT)
                                 .spec(tasCountInstanceSelection)
                                 .build())
            .oldAppInstances(TasInstanceSelectionWrapper.builder()
                                 .type(TasInstanceUnitType.PERCENTAGE)
                                 .spec(tasPercentageInstanceSelection)
                                 .build())
            .ignoreInstanceCountManifest(ParameterField.<Boolean>builder().value(true).build())
            .build();
    TasSetupDataOutcome tasSetupDataOutcome =
        TasSetupDataOutcome.builder()
            .cfCliVersion(CfCliVersion.V7)
            .activeApplicationDetails(
                TasApplicationInfo.builder().applicationGuid(APP_ID).applicationName("app").build())
            .desiredActualFinalCount(4)
            .isBlueGreen(true)
            .newReleaseName("app")
            .resizeStrategy(TasResizeStrategyType.DOWNSCALE_OLD_FIRST)
            .instanceCountType(TasInstanceCountType.FROM_MANIFEST)
            .build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(TasConnectorDTO.builder().build()).build();
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(ORG).space(SPACE).build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(tasSetupDataOutcome).found(true).build();

    doReturn(optionalSweepingOutput).when(tasEntityHelper).getSetupOutcome(any(), any(), any(), any(), any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doReturn(connectorInfoDTO).when(tasEntityHelper).getConnectorInfoDTO(any(), any(), any(), any());
    doReturn(null).when(tasEntityHelper).getEncryptionDataDetails(any(), any());
    doReturn(null).when(stepHelper).getEnvironmentType(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    stepElementParameters.setSpec(tasAppResizeStepParameters);
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    tasAppResizeStep.obtainTaskAfterRbac(ambiance, stepElementParameters, null);
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    CfDeployCommandRequestNG taskData = (CfDeployCommandRequestNG) taskDataArgumentCaptor.getValue().getParameters()[0];

    assertThat(taskData.getResizeStrategy()).isEqualTo(TasResizeStrategyType.DOWNSCALE_OLD_FIRST);
    assertThat(taskData.getDownSizeCount()).isEqualTo(0);
    assertThat(taskData.getUpsizeCount()).isEqualTo(2);
    assertThat(taskData.getNewReleaseName()).isEqualTo("app");
    assertThat(taskData.getTasInfraConfig().getSpace()).isEqualTo(SPACE);
    assertThat(taskData.getTasInfraConfig().getOrganization()).isEqualTo(ORG);
  }

  private List<CfInternalInstanceElement> getCfInstances(List<String> AppNames) {
    List<CfInternalInstanceElement> instances = new ArrayList<>();
    AppNames.forEach(app
        -> instances.add(CfInternalInstanceElement.builder()
                             .applicationId(APP_ID)
                             .instanceIndex(INDEX)
                             .displayName(app)
                             .isUpsize(false)
                             .build()));
    return instances;
  }
}
