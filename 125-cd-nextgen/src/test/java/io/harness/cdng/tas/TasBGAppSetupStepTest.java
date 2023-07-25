/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.cdng.tas.TasStepHelperTest.AUTOSCALAR_YML;
import static io.harness.cdng.tas.TasStepHelperTest.MANIFEST_WITH_ROUTES_ARRAY_YML;
import static io.harness.cdng.tas.TasStepHelperTest.MANIFEST_YML;
import static io.harness.cdng.tas.TasStepHelperTest.MANIFEST_YML_WITH_ROUTES;
import static io.harness.cdng.tas.TasStepHelperTest.VARS_WITH_INVALID_ROUTES_YML;
import static io.harness.cdng.tas.TasStepHelperTest.VARS_YML_1;
import static io.harness.rule.OwnerRule.RISHABH;

import static software.wings.beans.TaskType.TAS_BG_SETUP;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.TasBGAppSetupStepParameters.TasBGAppSetupStepParametersBuilder;
import io.harness.cdng.tas.outcome.TasSetupDataOutcome;
import io.harness.cdng.tas.outcome.TasSetupVariablesOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.beans.pcf.TasResizeStrategyType;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.artifact.TasArtifactConfig;
import io.harness.delegate.task.pcf.request.CfBlueGreenSetupRequestNG;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.delegate.task.pcf.response.CfBlueGreenSetupResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pcf.CfCommandUnitConstants;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasBGAppSetupStepTest extends CDNGTestBase {
  @Mock private CDStepHelper cdStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Spy @InjectMocks private TasStepHelper tasStepHelper;
  @Mock private TasInfraConfig tasInfraConfig;
  @Mock private TasArtifactConfig tasArtifactConfig;
  @Mock private ArtifactOutcome artifactOutcome;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @InjectMocks private TasBGAppSetupStep tasBGAppSetupStep;

  private final TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
      TanzuApplicationServiceInfrastructureOutcome.builder()
          .connectorRef("account.tas")
          .organization("dev-org")
          .space("dev-space")
          .build();
  private final TasBGAppSetupStepParametersBuilder parameters =
      TasBGAppSetupStepParameters.infoBuilder()
          .delegateSelectors(ParameterField.createValueField(List.of(new TaskSelectorYaml("selector-1"))))
          .existingVersionToKeep(ParameterField.createValueField("3"))
          .tempRoutes(ParameterField.createValueField(new ArrayList<>()));
  private final StepElementParameters stepElementParametersFromManifest =
      StepElementParameters.builder()
          .type("BGAppSetup")
          .timeout(ParameterField.createValueField("10m"))
          .spec(parameters.tasInstanceCountType(TasInstanceCountType.FROM_MANIFEST).build())
          .build();
  private final StepElementParameters stepElementParametersMatchRunningInstances =
      StepElementParameters.builder()
          .type("BGAppSetup")
          .timeout(ParameterField.createValueField("10m"))
          .spec(parameters.tasInstanceCountType(TasInstanceCountType.MATCH_RUNNING_INSTANCES).build())
          .build();
  private final List<String> tempRouteMap = asList("temp-route1", "temp-route2");
  private final List<String> finalRouteMap = asList("route1", "route2");
  private final String newApplicationName = "test-tas__INACTIVE";
  private final String activeApplicationName = "test-tas";
  private final TasApplicationInfo newApplicationInfo = TasApplicationInfo.builder()
                                                            .applicationName(newApplicationName)
                                                            .applicationGuid("1234")
                                                            .attachedRoutes(tempRouteMap)
                                                            .build();
  private final TasApplicationInfo activeApplicationInfo = TasApplicationInfo.builder()
                                                               .runningCount(3)
                                                               .applicationName(activeApplicationName)
                                                               .applicationGuid("4567")
                                                               .attachedRoutes(List.of("route1"))
                                                               .build();

  private final Ambiance ambiance = getAmbiance();
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Before
  public void setup() {
    ILogStreamingStepClient logStreamingStepClient;
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
    when(stepHelper.getEnvironmentType(ambiance)).thenReturn(EnvironmentType.PROD);
    when(cdStepHelper.getInfrastructureOutcome(ambiance)).thenReturn(infrastructureOutcome);
    when(cdStepHelper.resolveArtifactsOutcome(ambiance)).thenReturn(Optional.of(artifactOutcome));
    when(cdStepHelper.getTasInfraConfig(infrastructureOutcome, ambiance)).thenReturn(tasInfraConfig);
    doReturn(tasArtifactConfig).when(tasStepHelper).getPrimaryArtifactConfig(ambiance, artifactOutcome);
    doCallRealMethod().when(tasStepHelper).cfCliVersionNGMapper(any());
    doReturn(finalRouteMap).when(tasStepHelper).getRouteMaps(any(), any());
    doCallRealMethod().when(tasStepHelper).finalizeSubstitution(any(), any());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextStepException() throws Exception {
    List<UnitProgress> unitProgresses =
        List.of(UnitProgress.newBuilder().setUnitName("Setup Application").setStatus(UnitStatus.FAILURE).build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    StepExceptionPassThroughData passThroughData =
        StepExceptionPassThroughData.builder()
            .errorMessage("error_msg")
            .unitProgressData(UnitProgressData.builder().unitProgresses(unitProgresses).build())
            .build();
    CfBlueGreenSetupResponseNG cfBlueGreenSetupResponseNG = CfBlueGreenSetupResponseNG.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    StepResponse stepResponse = tasBGAppSetupStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParametersFromManifest, passThroughData, () -> cfBlueGreenSetupResponseNG);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo("error_msg");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextFailedResponse() throws Exception {
    List<UnitProgress> unitProgresses =
        List.of(UnitProgress.newBuilder().setUnitName("Setup Application").setStatus(UnitStatus.FAILURE).build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    CfBlueGreenSetupResponseNG cfBlueGreenSetupResponseNG = CfBlueGreenSetupResponseNG.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                                .errorMessage("error_msg")
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    StepResponse stepResponse =
        tasBGAppSetupStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParametersFromManifest,
            TasExecutionPassThroughData.builder().build(), () -> cfBlueGreenSetupResponseNG);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo("error_msg");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextFirstDeploy() throws Exception {
    ArgumentCaptor<ExecutionSweepingOutput> executionSweepingOutputArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    when(executionSweepingOutputService.consume(any(), eq(OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME),
             executionSweepingOutputArgumentCaptor.capture(), eq(StepCategory.STEP.name())))
        .thenReturn(null);
    doReturn(2).when(tasStepHelper).fetchMaxCountFromManifest(any());
    List<UnitProgress> unitProgresses =
        List.of(UnitProgress.newBuilder().setUnitName("Setup Application").setStatus(UnitStatus.SUCCESS).build());
    TasManifestsPackage tasManifestsPackage =
        TasManifestsPackage.builder().manifestYml(MANIFEST_YML_WITH_ROUTES).variableYmls(List.of(VARS_YML_1)).build();
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    CfBlueGreenSetupResponseNG cfBlueGreenSetupResponseNG = CfBlueGreenSetupResponseNG.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                                .newApplicationInfo(newApplicationInfo)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    StepResponse stepResponse =
        tasBGAppSetupStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParametersFromManifest,
            TasExecutionPassThroughData.builder()
                .cfCliVersion(CfCliVersionNG.V7)
                .applicationName(activeApplicationName)
                .tasManifestsPackage(tasManifestsPackage)
                .build(),
            () -> cfBlueGreenSetupResponseNG);
    ExecutionSweepingOutput executionSweepingOutput = executionSweepingOutputArgumentCaptor.getValue();
    assertThat(executionSweepingOutput).isInstanceOf(TasSetupDataOutcome.class);
    TasSetupDataOutcome tasSetupDataOutcome = (TasSetupDataOutcome) executionSweepingOutput;
    TasSetupDataOutcome tasSetupDataOutcomeReq = TasSetupDataOutcome.builder()
                                                     .routeMaps(finalRouteMap)
                                                     .tempRouteMap(tempRouteMap)
                                                     .cfCliVersion(CfCliVersion.V7)
                                                     .timeoutIntervalInMinutes(10)
                                                     .resizeStrategy(TasResizeStrategyType.UPSCALE_NEW_FIRST)
                                                     .maxCount(2)
                                                     .useAppAutoScalar(false)
                                                     .desiredActualFinalCount(2)
                                                     .newReleaseName(newApplicationName)
                                                     .activeApplicationDetails(null)
                                                     .newApplicationDetails(newApplicationInfo)
                                                     .manifestsPackage(tasManifestsPackage)
                                                     .cfAppNamePrefix(activeApplicationName)
                                                     .isBlueGreen(true)
                                                     .instanceCountType(TasInstanceCountType.FROM_MANIFEST)
                                                     .build();
    TasSetupVariablesOutcome tasSetupVariablesOutcomeReq = TasSetupVariablesOutcome.builder()
                                                               .inActiveAppName(newApplicationInfo.getApplicationName())
                                                               .newAppGuid(newApplicationInfo.getApplicationGuid())
                                                               .newAppRoutes(null)
                                                               .finalRoutes(finalRouteMap)
                                                               .activeAppName(null)
                                                               .oldAppGuid(null)
                                                               .oldAppRoutes(null)
                                                               .newAppName(null)
                                                               .oldAppName(null)
                                                               .tempRoutes(tempRouteMap)
                                                               .build();
    assertThat(tasSetupDataOutcome).isEqualTo(tasSetupDataOutcomeReq);
    assertThat(stepResponse.getStepOutcomes())
        .isEqualTo(List.of(StepResponse.StepOutcome.builder()
                               .outcome(tasSetupVariablesOutcomeReq)
                               .name(OutcomeExpressionConstants.TAS_INBUILT_VARIABLES_OUTCOME)
                               .group(StepCategory.STAGE.name())
                               .build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContext() throws Exception {
    ArgumentCaptor<ExecutionSweepingOutput> executionSweepingOutputArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    when(executionSweepingOutputService.consume(any(), eq(OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME),
             executionSweepingOutputArgumentCaptor.capture(), eq(StepCategory.STEP.name())))
        .thenReturn(null);
    doReturn(2).when(tasStepHelper).fetchMaxCountFromManifest(any());
    List<UnitProgress> unitProgresses =
        List.of(UnitProgress.newBuilder().setUnitName("Setup Application").setStatus(UnitStatus.SUCCESS).build());
    TasManifestsPackage tasManifestsPackage =
        TasManifestsPackage.builder().manifestYml(MANIFEST_YML).variableYmls(List.of(VARS_YML_1)).build();
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    CfBlueGreenSetupResponseNG cfBlueGreenSetupResponseNG = CfBlueGreenSetupResponseNG.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                                .newApplicationInfo(newApplicationInfo)
                                                                .activeApplicationInfo(activeApplicationInfo)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    StepResponse stepResponse =
        tasBGAppSetupStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParametersFromManifest,
            TasExecutionPassThroughData.builder()
                .cfCliVersion(CfCliVersionNG.V7)
                .applicationName(activeApplicationName)
                .tasManifestsPackage(tasManifestsPackage)
                .build(),
            () -> cfBlueGreenSetupResponseNG);
    ExecutionSweepingOutput executionSweepingOutput = executionSweepingOutputArgumentCaptor.getValue();
    assertThat(executionSweepingOutput).isInstanceOf(TasSetupDataOutcome.class);
    TasSetupDataOutcome tasSetupDataOutcome = (TasSetupDataOutcome) executionSweepingOutput;
    TasSetupDataOutcome tasSetupDataOutcomeReq = TasSetupDataOutcome.builder()
                                                     .routeMaps(finalRouteMap)
                                                     .tempRouteMap(tempRouteMap)
                                                     .cfCliVersion(CfCliVersion.V7)
                                                     .timeoutIntervalInMinutes(10)
                                                     .resizeStrategy(TasResizeStrategyType.UPSCALE_NEW_FIRST)
                                                     .maxCount(2)
                                                     .useAppAutoScalar(false)
                                                     .desiredActualFinalCount(2)
                                                     .newReleaseName(newApplicationName)
                                                     .activeApplicationDetails(activeApplicationInfo)
                                                     .newApplicationDetails(newApplicationInfo)
                                                     .manifestsPackage(tasManifestsPackage)
                                                     .cfAppNamePrefix(activeApplicationName)
                                                     .isBlueGreen(true)
                                                     .instanceCountType(TasInstanceCountType.FROM_MANIFEST)
                                                     .build();
    TasSetupVariablesOutcome tasSetupVariablesOutcomeReq =
        TasSetupVariablesOutcome.builder()
            .inActiveAppName(newApplicationInfo.getApplicationName())
            .newAppGuid(newApplicationInfo.getApplicationGuid())
            .newAppRoutes(null)
            .finalRoutes(finalRouteMap)
            .activeAppName(activeApplicationInfo.getApplicationName())
            .oldAppGuid(activeApplicationInfo.getApplicationGuid())
            .oldAppRoutes(activeApplicationInfo.getAttachedRoutes())
            .newAppName(null)
            .oldAppName(null)
            .tempRoutes(tempRouteMap)
            .build();
    assertThat(tasSetupDataOutcome).isEqualTo(tasSetupDataOutcomeReq);
    assertThat(stepResponse.getStepOutcomes())
        .isEqualTo(List.of(StepResponse.StepOutcome.builder()
                               .outcome(tasSetupVariablesOutcomeReq)
                               .name(OutcomeExpressionConstants.TAS_INBUILT_VARIABLES_OUTCOME)
                               .group(StepCategory.STAGE.name())
                               .build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextMatchRunningFirstDeploy() throws Exception {
    ArgumentCaptor<ExecutionSweepingOutput> executionSweepingOutputArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    when(executionSweepingOutputService.consume(any(), eq(OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME),
             executionSweepingOutputArgumentCaptor.capture(), eq(StepCategory.STEP.name())))
        .thenReturn(null);
    doReturn(2).when(tasStepHelper).fetchMaxCountFromManifest(any());
    List<UnitProgress> unitProgresses =
        List.of(UnitProgress.newBuilder().setUnitName("Setup Application").setStatus(UnitStatus.SUCCESS).build());
    TasManifestsPackage tasManifestsPackage =
        TasManifestsPackage.builder().manifestYml(MANIFEST_YML).variableYmls(List.of(VARS_YML_1)).build();
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    CfBlueGreenSetupResponseNG cfBlueGreenSetupResponseNG = CfBlueGreenSetupResponseNG.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                                .newApplicationInfo(newApplicationInfo)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    StepResponse stepResponse =
        tasBGAppSetupStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParametersMatchRunningInstances,
            TasExecutionPassThroughData.builder()
                .cfCliVersion(CfCliVersionNG.V7)
                .applicationName(activeApplicationName)
                .tasManifestsPackage(tasManifestsPackage)
                .build(),
            () -> cfBlueGreenSetupResponseNG);
    ExecutionSweepingOutput executionSweepingOutput = executionSweepingOutputArgumentCaptor.getValue();
    assertThat(executionSweepingOutput).isInstanceOf(TasSetupDataOutcome.class);
    TasSetupDataOutcome tasSetupDataOutcome = (TasSetupDataOutcome) executionSweepingOutput;
    TasSetupDataOutcome tasSetupDataOutcomeReq = TasSetupDataOutcome.builder()
                                                     .routeMaps(finalRouteMap)
                                                     .tempRouteMap(tempRouteMap)
                                                     .cfCliVersion(CfCliVersion.V7)
                                                     .timeoutIntervalInMinutes(10)
                                                     .resizeStrategy(TasResizeStrategyType.UPSCALE_NEW_FIRST)
                                                     .maxCount(0)
                                                     .useAppAutoScalar(false)
                                                     .desiredActualFinalCount(0)
                                                     .newReleaseName(newApplicationName)
                                                     .activeApplicationDetails(null)
                                                     .newApplicationDetails(newApplicationInfo)
                                                     .manifestsPackage(tasManifestsPackage)
                                                     .cfAppNamePrefix(activeApplicationName)
                                                     .isBlueGreen(true)
                                                     .instanceCountType(TasInstanceCountType.MATCH_RUNNING_INSTANCES)
                                                     .build();
    TasSetupVariablesOutcome tasSetupVariablesOutcomeReq = TasSetupVariablesOutcome.builder()
                                                               .inActiveAppName(newApplicationInfo.getApplicationName())
                                                               .newAppGuid(newApplicationInfo.getApplicationGuid())
                                                               .newAppRoutes(null)
                                                               .finalRoutes(finalRouteMap)
                                                               .activeAppName(null)
                                                               .oldAppGuid(null)
                                                               .oldAppRoutes(null)
                                                               .newAppName(null)
                                                               .oldAppName(null)
                                                               .tempRoutes(tempRouteMap)
                                                               .build();
    assertThat(tasSetupDataOutcome).isEqualTo(tasSetupDataOutcomeReq);
    assertThat(stepResponse.getStepOutcomes())
        .isEqualTo(List.of(StepResponse.StepOutcome.builder()
                               .outcome(tasSetupVariablesOutcomeReq)
                               .name(OutcomeExpressionConstants.TAS_INBUILT_VARIABLES_OUTCOME)
                               .group(StepCategory.STAGE.name())
                               .build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextInvalidRoutes() throws Exception {
    ArgumentCaptor<ExecutionSweepingOutput> executionSweepingOutputArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    when(executionSweepingOutputService.consume(any(), eq(OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME),
             executionSweepingOutputArgumentCaptor.capture(), eq(StepCategory.STEP.name())))
        .thenReturn(null);
    doReturn(2).when(tasStepHelper).fetchMaxCountFromManifest(any());
    doCallRealMethod().when(tasStepHelper).getRouteMaps(any(), any());
    List<UnitProgress> unitProgresses =
        List.of(UnitProgress.newBuilder().setUnitName("Setup Application").setStatus(UnitStatus.SUCCESS).build());
    TasManifestsPackage tasManifestsPackage = TasManifestsPackage.builder()
                                                  .manifestYml(MANIFEST_WITH_ROUTES_ARRAY_YML)
                                                  .variableYmls(List.of(VARS_WITH_INVALID_ROUTES_YML))
                                                  .build();
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    CfBlueGreenSetupResponseNG cfBlueGreenSetupResponseNG = CfBlueGreenSetupResponseNG.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                                .newApplicationInfo(newApplicationInfo)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    StepResponse stepResponse =
        tasBGAppSetupStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParametersMatchRunningInstances,
            TasExecutionPassThroughData.builder()
                .cfCliVersion(CfCliVersionNG.V7)
                .applicationName(activeApplicationName)
                .tasManifestsPackage(tasManifestsPackage)
                .build(),
            () -> cfBlueGreenSetupResponseNG);
    ExecutionSweepingOutput executionSweepingOutput = executionSweepingOutputArgumentCaptor.getValue();
    assertThat(executionSweepingOutput).isInstanceOf(TasSetupDataOutcome.class);
    TasSetupDataOutcome tasSetupDataOutcome = (TasSetupDataOutcome) executionSweepingOutput;
    TasSetupDataOutcome tasSetupDataOutcomeReq = TasSetupDataOutcome.builder()
                                                     .tempRouteMap(tempRouteMap)
                                                     .cfCliVersion(CfCliVersion.V7)
                                                     .timeoutIntervalInMinutes(10)
                                                     .resizeStrategy(TasResizeStrategyType.UPSCALE_NEW_FIRST)
                                                     .maxCount(0)
                                                     .useAppAutoScalar(false)
                                                     .desiredActualFinalCount(0)
                                                     .newReleaseName(newApplicationName)
                                                     .activeApplicationDetails(null)
                                                     .newApplicationDetails(newApplicationInfo)
                                                     .manifestsPackage(tasManifestsPackage)
                                                     .cfAppNamePrefix(activeApplicationName)
                                                     .isBlueGreen(true)
                                                     .instanceCountType(TasInstanceCountType.MATCH_RUNNING_INSTANCES)
                                                     .build();
    assertThat(tasSetupDataOutcome).isEqualTo(tasSetupDataOutcomeReq);
    assertThat(stepResponse.getStepOutcomes()).isEqualTo(emptyList());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
    assertThat(stepResponse.getFailureInfo())
        .isEqualTo(
            FailureInfo.newBuilder()
                .setErrorMessage(
                    "Invalid Route Format In Manifest: class java.lang.String cannot be cast to class java.util.Map (java.lang.String and java.util.Map are in module java.base of loader 'bootstrap')")
                .build());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextMatchRunning() throws Exception {
    ArgumentCaptor<ExecutionSweepingOutput> executionSweepingOutputArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    when(executionSweepingOutputService.consume(any(), eq(OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME),
             executionSweepingOutputArgumentCaptor.capture(), eq(StepCategory.STEP.name())))
        .thenReturn(null);
    doReturn(2).when(tasStepHelper).fetchMaxCountFromManifest(any());
    List<UnitProgress> unitProgresses =
        List.of(UnitProgress.newBuilder().setUnitName("Setup Application").setStatus(UnitStatus.SUCCESS).build());
    TasManifestsPackage tasManifestsPackage =
        TasManifestsPackage.builder().manifestYml(MANIFEST_YML).variableYmls(List.of(VARS_YML_1)).build();
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    CfBlueGreenSetupResponseNG cfBlueGreenSetupResponseNG = CfBlueGreenSetupResponseNG.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                                .newApplicationInfo(newApplicationInfo)
                                                                .activeApplicationInfo(activeApplicationInfo)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    StepResponse stepResponse =
        tasBGAppSetupStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParametersMatchRunningInstances,
            TasExecutionPassThroughData.builder()
                .cfCliVersion(CfCliVersionNG.V7)
                .applicationName(activeApplicationName)
                .tasManifestsPackage(tasManifestsPackage)
                .build(),
            () -> cfBlueGreenSetupResponseNG);
    ExecutionSweepingOutput executionSweepingOutput = executionSweepingOutputArgumentCaptor.getValue();
    assertThat(executionSweepingOutput).isInstanceOf(TasSetupDataOutcome.class);
    TasSetupDataOutcome tasSetupDataOutcome = (TasSetupDataOutcome) executionSweepingOutput;
    TasSetupDataOutcome tasSetupDataOutcomeReq = TasSetupDataOutcome.builder()
                                                     .routeMaps(finalRouteMap)
                                                     .tempRouteMap(tempRouteMap)
                                                     .cfCliVersion(CfCliVersion.V7)
                                                     .timeoutIntervalInMinutes(10)
                                                     .resizeStrategy(TasResizeStrategyType.UPSCALE_NEW_FIRST)
                                                     .maxCount(3)
                                                     .useAppAutoScalar(false)
                                                     .desiredActualFinalCount(3)
                                                     .newReleaseName(newApplicationName)
                                                     .activeApplicationDetails(activeApplicationInfo)
                                                     .newApplicationDetails(newApplicationInfo)
                                                     .manifestsPackage(tasManifestsPackage)
                                                     .cfAppNamePrefix(activeApplicationName)
                                                     .isBlueGreen(true)
                                                     .instanceCountType(TasInstanceCountType.MATCH_RUNNING_INSTANCES)
                                                     .build();
    TasSetupVariablesOutcome tasSetupVariablesOutcomeReq =
        TasSetupVariablesOutcome.builder()
            .inActiveAppName(newApplicationInfo.getApplicationName())
            .newAppGuid(newApplicationInfo.getApplicationGuid())
            .newAppRoutes(null)
            .finalRoutes(finalRouteMap)
            .activeAppName(activeApplicationInfo.getApplicationName())
            .oldAppGuid(activeApplicationInfo.getApplicationGuid())
            .oldAppRoutes(activeApplicationInfo.getAttachedRoutes())
            .newAppName(null)
            .oldAppName(null)
            .tempRoutes(tempRouteMap)
            .build();
    assertThat(tasSetupDataOutcome).isEqualTo(tasSetupDataOutcomeReq);
    assertThat(stepResponse.getStepOutcomes())
        .isEqualTo(List.of(StepResponse.StepOutcome.builder()
                               .outcome(tasSetupVariablesOutcomeReq)
                               .name(OutcomeExpressionConstants.TAS_INBUILT_VARIABLES_OUTCOME)
                               .group(StepCategory.STAGE.name())
                               .build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testExecuteTasTask() {
    TasManifestsPackage tasManifestsPackage =
        TasManifestsPackage.builder().manifestYml(MANIFEST_YML).variableYmls(List.of(VARS_YML_1)).build();
    TasExecutionPassThroughData tasExecutionPassThroughData = TasExecutionPassThroughData.builder()
                                                                  .tasManifestsPackage(tasManifestsPackage)
                                                                  .applicationName("tas-test")
                                                                  .cfCliVersion(CfCliVersionNG.V7)
                                                                  .build();
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(
             any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    TaskChainResponse taskChainResponse =
        tasBGAppSetupStep.executeTasTask(null, ambiance, stepElementParametersFromManifest, tasExecutionPassThroughData,
            true, UnitProgressData.builder().unitProgresses(new ArrayList<>()).build());
    assertThat(taskChainResponse).isNotNull();

    CfBlueGreenSetupRequestNG requestParameters =
        (CfBlueGreenSetupRequestNG) taskDataArgumentCaptor.getValue().getParameters()[0];

    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TAS_BG_SETUP.toString());
    assertThat(requestParameters.getCfCliVersion()).isEqualTo(CfCliVersion.V7);
    assertThat(requestParameters.getReleaseNamePrefix()).isEqualTo("tas-test");
    assertThat(requestParameters.isUseAppAutoScalar()).isFalse();
    assertThat(requestParameters.getTasManifestsPackage()).isEqualTo(tasManifestsPackage);
    assertThat(requestParameters.getOlderActiveVersionCountToKeep()).isEqualTo(3);
    assertThat(requestParameters.getRouteMaps()).isEqualTo(new ArrayList<>());
    assertThat(requestParameters.isUseCfCLI()).isTrue();
    assertThat(requestParameters.getTasInfraConfig()).isEqualTo(tasInfraConfig);
    assertThat(requestParameters.getCfCommandTypeNG()).isEqualTo(CfCommandTypeNG.TAS_BG_SETUP);
    assertThat(requestParameters.getCommandName()).isEqualTo(CfCommandUnitConstants.PcfSetup);
    assertThat(requestParameters.getAccountId()).isEqualTo("account");
    assertThat(requestParameters.getTimeoutIntervalInMin()).isEqualTo(10);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testExecuteTasTaskWithAutoScalar() {
    TasManifestsPackage tasManifestsPackage = TasManifestsPackage.builder()
                                                  .manifestYml(MANIFEST_YML)
                                                  .variableYmls(List.of(VARS_YML_1))
                                                  .autoscalarManifestYml(AUTOSCALAR_YML)
                                                  .build();
    TasExecutionPassThroughData tasExecutionPassThroughData = TasExecutionPassThroughData.builder()
                                                                  .tasManifestsPackage(tasManifestsPackage)
                                                                  .applicationName("tas-test")
                                                                  .cfCliVersion(CfCliVersionNG.V7)
                                                                  .build();
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(
             any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    TaskChainResponse taskChainResponse =
        tasBGAppSetupStep.executeTasTask(null, ambiance, stepElementParametersFromManifest, tasExecutionPassThroughData,
            true, UnitProgressData.builder().unitProgresses(new ArrayList<>()).build());
    assertThat(taskChainResponse).isNotNull();

    CfBlueGreenSetupRequestNG requestParameters =
        (CfBlueGreenSetupRequestNG) taskDataArgumentCaptor.getValue().getParameters()[0];

    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(TAS_BG_SETUP.toString());
    assertThat(requestParameters.getCfCliVersion()).isEqualTo(CfCliVersion.V7);
    assertThat(requestParameters.getReleaseNamePrefix()).isEqualTo("tas-test");
    assertThat(requestParameters.isUseAppAutoScalar()).isTrue();
    assertThat(requestParameters.getTasManifestsPackage()).isEqualTo(tasManifestsPackage);
    assertThat(requestParameters.getOlderActiveVersionCountToKeep()).isEqualTo(3);
    assertThat(requestParameters.getRouteMaps()).isEqualTo(new ArrayList<>());
    assertThat(requestParameters.isUseCfCLI()).isTrue();
    assertThat(requestParameters.getTasInfraConfig()).isEqualTo(tasInfraConfig);
    assertThat(requestParameters.getCfCommandTypeNG()).isEqualTo(CfCommandTypeNG.TAS_BG_SETUP);
    assertThat(requestParameters.getCommandName()).isEqualTo(CfCommandUnitConstants.PcfSetup);
    assertThat(requestParameters.getAccountId()).isEqualTo("account");
    assertThat(requestParameters.getTimeoutIntervalInMin()).isEqualTo(10);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateResourcesFFEnabled() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_SVC_ENV_REDESIGN));
    tasBGAppSetupStep.validateResources(getAmbiance(), stepElementParametersFromManifest);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateResourcesFFDisabled() {
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_SVC_ENV_REDESIGN));
    assertThatThrownBy(() -> tasBGAppSetupStep.validateResources(getAmbiance(), stepElementParametersFromManifest))
        .hasMessage("CDS_TAS_NG FF is not enabled for this account. Please contact harness customer care.");
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

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(tasBGAppSetupStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }
}
