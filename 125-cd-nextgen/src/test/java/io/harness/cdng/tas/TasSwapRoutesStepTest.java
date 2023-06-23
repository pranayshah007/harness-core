/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.outcome.TasAppResizeDataOutcome;
import io.harness.cdng.tas.outcome.TasSetupDataOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfSwapRouteCommandResult;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.beans.pcf.TasResizeStrategyType;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.request.CfSwapRoutesRequestNG;
import io.harness.delegate.task.pcf.response.CfSwapRouteCommandResponseNG;
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

public class TasSwapRoutesStepTest extends CategoryTest {
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

  private final TasSwapRoutesStepParameters tasSwapRoutesStepParameters =
      TasSwapRoutesStepParameters.infoBuilder().build();
  private final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                                  .spec(tasSwapRoutesStepParameters)
                                                                  .timeout(ParameterField.createValueField("10m"))
                                                                  .build();

  @Mock private InstanceInfoService instanceInfoService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private OutcomeService outcomeService;
  @Mock private TaskRequestsUtils taskRequestsUtils;
  @Mock private TasEntityHelper tasEntityHelper;
  @Mock private TasStepHelper tasStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @InjectMocks private TasSwapRoutesStep tasSwapRoutesStep;

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void getStepParametersClassTest() {
    Class<StepElementParameters> stepElementParametersClass = tasSwapRoutesStep.getStepParametersClass();
    assertThat(stepElementParametersClass).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testValidateResourcesFFEnabled() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_SVC_ENV_REDESIGN));
    tasSwapRoutesStep.validateResources(ambiance, stepElementParameters);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testValidateResourcesFFDisabled() {
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_SVC_ENV_REDESIGN));
    assertThatThrownBy(() -> tasSwapRoutesStep.validateResources(ambiance, stepElementParameters))
        .hasMessage("CDS_TAS_NG FF is not enabled for this account. Please contact harness customer care.");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextCommandExecutionStatusFailureTest() throws Exception {
    tasSwapRoutesStepParameters.setDownSizeOldApplication(ParameterField.createValueField(true));
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    CfSwapRouteCommandResponseNG responseData = CfSwapRouteCommandResponseNG.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                    .errorMessage("error")
                                                    .unitProgressData(unitProgressData)
                                                    .build();
    doReturn(unitProgressData).when(tasStepHelper).completeUnitProgressData(any(), any(), any());
    StepResponse stepResponse =
        tasSwapRoutesStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);

    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo(responseData.getErrorMessage());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextWithNotNullAppResizeData() throws Exception {
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    tasSwapRoutesStepParameters.setDownSizeOldApplication(ParameterField.createValueField(true));
    CfSwapRouteCommandResponseNG responseData = CfSwapRouteCommandResponseNG.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .unitProgressData(unitProgressData)
                                                    .cfSwapRouteCommandResult(null)
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
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().output(tasAppResizeDataOutcome).found(true).build();
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doReturn(stepOutcomeMock).when(instanceInfoService).saveServerInstancesIntoSweepingOutput(any(), any());
    doReturn(optionalSweepingOutput).when(executionSweepingOutputService).resolveOptional(any(), any());

    StepResponse stepResponse =
        tasSwapRoutesStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    StepResponse.StepOutcome stepOutcome = new ArrayList<>(stepResponse.getStepOutcomes()).get(0);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(((TasAppResizeDataOutcome) stepOutcome.getOutcome()).getCfInstanceElements())
        .isEqualTo(tasAppResizeDataOutcome.getCfInstanceElements());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextWithNullAppResizeData() throws Exception {
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    tasSwapRoutesStepParameters.setDownSizeOldApplication(ParameterField.createValueField(true));
    CfSwapRouteCommandResult cfSwapRouteCommandResult =
        CfSwapRouteCommandResult.builder()
            .newAppInstances(getCfInstances(Arrays.asList("app1", "app2", "app3")))
            .build();
    CfSwapRouteCommandResponseNG responseData = CfSwapRouteCommandResponseNG.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .unitProgressData(unitProgressData)
                                                    .cfSwapRouteCommandResult(cfSwapRouteCommandResult)
                                                    .build();

    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(ORG).space(SPACE).build();

    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(false).build();
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doReturn(optionalSweepingOutput).when(executionSweepingOutputService).resolveOptional(any(), any());

    StepResponse stepResponse =
        tasSwapRoutesStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    ArgumentCaptor<List<ServerInstanceInfo>> serverInstanceInfoCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(instanceInfoService).saveServerInstancesIntoSweepingOutput(any(), serverInstanceInfoCaptor.capture());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    List<ServerInstanceInfo> capturedList = serverInstanceInfoCaptor.getValue();

    assertThat(capturedList.size()).isEqualTo(3);
    assertThat(((TasServerInstanceInfo) capturedList.get(0)).getTasApplicationName()).isEqualTo("app1");
    assertThat(((TasServerInstanceInfo) capturedList.get(1)).getTasApplicationName()).isEqualTo("app2");
    assertThat(((TasServerInstanceInfo) capturedList.get(2)).getTasApplicationName()).isEqualTo("app3");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    tasSwapRoutesStepParameters.setDownSizeOldApplication(ParameterField.createValueField(true));
    TasSetupDataOutcome tasSetupDataOutcome =
        TasSetupDataOutcome.builder()
            .cfCliVersion(CfCliVersion.V7)
            .activeApplicationDetails(
                TasApplicationInfo.builder().applicationGuid(APP_ID).applicationName("app").build())
            .desiredActualFinalCount(4)
            .isBlueGreen(true)
            .newReleaseName("app")
            .newApplicationDetails(TasApplicationInfo.builder().applicationName("app").build())
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

    stepElementParameters.setSpec(tasSwapRoutesStepParameters);
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    tasSwapRoutesStep.obtainTaskAfterRbac(ambiance, stepElementParameters, null);
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    CfSwapRoutesRequestNG taskData = (CfSwapRoutesRequestNG) taskDataArgumentCaptor.getValue().getParameters()[0];

    assertThat(taskData.getNewApplicationName()).isEqualTo("app");
    assertThat(taskData.getCommandName()).isEqualTo(CfCommandTypeNG.SWAP_ROUTES.toString());
    assertThat(taskData.getCfCommandTypeNG()).isEqualTo(CfCommandTypeNG.SWAP_ROUTES);
    assertThat(taskData.getTasInfraConfig().getSpace()).isEqualTo(SPACE);
    assertThat(taskData.getTasInfraConfig().getOrganization()).isEqualTo(ORG);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testObtainTaskWithNullSetupData() {
    tasSwapRoutesStepParameters.setDownSizeOldApplication(ParameterField.createValueField(true));
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(TasConnectorDTO.builder().build()).build();
    TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(ORG).space(SPACE).build();
    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().output(null).found(false).build();

    doReturn(optionalSweepingOutput).when(tasEntityHelper).getSetupOutcome(any(), any(), any(), any(), any(), any());
    doReturn(infrastructureOutcome).when(outcomeService).resolve(any(), any());
    doReturn(connectorInfoDTO).when(tasEntityHelper).getConnectorInfoDTO(any(), any(), any(), any());
    doReturn(null).when(tasEntityHelper).getEncryptionDataDetails(any(), any());
    doReturn(null).when(stepHelper).getEnvironmentType(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    stepElementParameters.setSpec(tasSwapRoutesStepParameters);
    TaskRequest taskRequest = tasSwapRoutesStep.obtainTaskAfterRbac(ambiance, stepElementParameters, null);

    assertThat(taskRequest.getSkipTaskRequest().getMessage())
        .isEqualTo("Tas Swap Route Step was not executed. Skipping.");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithNullActiveApplicationDetails() {
    tasSwapRoutesStepParameters.setDownSizeOldApplication(ParameterField.createValueField(true));
    TasSetupDataOutcome tasSetupDataOutcome = TasSetupDataOutcome.builder()
                                                  .cfCliVersion(CfCliVersion.V7)
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

    stepElementParameters.setSpec(tasSwapRoutesStepParameters);
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    tasSwapRoutesStep.obtainTaskAfterRbac(ambiance, stepElementParameters, null);
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    CfSwapRoutesRequestNG taskData = (CfSwapRoutesRequestNG) taskDataArgumentCaptor.getValue().getParameters()[0];

    assertThat(taskData.getCommandName()).isEqualTo(CfCommandTypeNG.SWAP_ROUTES.toString());
    assertThat(taskData.getCfCommandTypeNG()).isEqualTo(CfCommandTypeNG.SWAP_ROUTES);
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
