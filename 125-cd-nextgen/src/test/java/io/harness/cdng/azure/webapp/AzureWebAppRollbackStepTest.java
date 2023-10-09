/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.azure.model.AzureConstants.DEPLOY_TO_SLOT;
import static io.harness.azure.model.AzureConstants.FETCH_ARTIFACT_FILE;
import static io.harness.azure.model.AzureConstants.SLOT_SWAP;
import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.azure.model.AzureConstants.UPDATE_SLOT_CONFIGURATION_SETTINGS;
import static io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType.ROLLBACK;
import static io.harness.rule.OwnerRule.TMACARI;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.azure.webapp.beans.AzureWebAppPreDeploymentDataOutput;
import io.harness.cdng.azure.webapp.beans.AzureWebAppSlotDeploymentDataOutput;
import io.harness.cdng.azure.webapp.beans.AzureWebAppSwapSlotsDataOutput;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.execution.azure.webapps.AzureWebAppsStageExecutionDetails;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppRollbackRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppNGRollbackResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzurePackageArtifactConfig;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class AzureWebAppRollbackStepTest extends CDNGTestBase {
  private static String ACCOUNT_ID = "accountId";
  private static String SWAP_SLOT_STEP_FQN = "swapSlotStepFqn";
  private static String SLOT_DEPLOYMENT_STEP_FQN = "slotDeploymentStepFqn";

  @Mock private AzureWebAppStepHelper stepHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private InstanceInfoService instanceInfoService;

  @InjectMocks private AzureWebAppRollbackStep azureWebAppRollbackStep;

  private AzureWebAppRollbackStepParameters parameters =
      AzureWebAppRollbackStepParameters.infoBuilder()
          .swapSlotStepFqn(SWAP_SLOT_STEP_FQN)
          .slotDeploymentStepFqn(SLOT_DEPLOYMENT_STEP_FQN)
          .delegateSelectors(ParameterField.createValueField(List.of(new TaskSelectorYaml("selector-1"))))
          .build();
  private final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();
  private final Ambiance ambiance = getAmbiance();

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    AzureWebAppInfraDelegateConfig azureWebAppInfraDelegateConfig = AzureWebAppInfraDelegateConfig.builder().build();
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        AzureAppServicePreDeploymentData.builder().build();
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(AzureWebAppSwapSlotsDataOutput.builder()
                             .deploymentProgressMarker(AppServiceDeploymentProgress.SWAP_SLOT.name())
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                SWAP_SLOT_STEP_FQN + "." + AzureWebAppSwapSlotsDataOutput.OUTPUT_NAME)));
    doReturn(azureAppServicePreDeploymentData).when(stepHelper).getPreDeploymentData(eq(ambiance), any());
    doReturn(azureWebAppInfraDelegateConfig).when(stepHelper).getInfraDelegateConfig(any(), any(), any());
    doReturn(AzureContainerArtifactConfig.builder().build()).when(stepHelper).getPrimaryArtifactConfig(any(), any());

    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    doReturn(TaskRequest.newBuilder().build())
        .when(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), any());

    TaskRequest taskRequest =
        azureWebAppRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();

    verify(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG),
            eq(Arrays.asList(SLOT_SWAP, UPDATE_SLOT_CONFIGURATION_SETTINGS, DEPLOY_TO_SLOT, SLOT_TRAFFIC_PERCENTAGE)));

    AzureWebAppRollbackRequest requestParameters = (AzureWebAppRollbackRequest) taskParametersArgumentCaptor.getValue();

    assertThat(requestParameters.getRequestType()).isEqualTo(ROLLBACK);
    assertThat(requestParameters.getPreDeploymentData()).isEqualTo(azureAppServicePreDeploymentData);
    assertThat(requestParameters.getPreDeploymentData().getDeploymentProgressMarker())
        .isEqualTo(AppServiceDeploymentProgress.SWAP_SLOT.name());
    assertThat(requestParameters.getInfrastructure()).isEqualTo(azureWebAppInfraDelegateConfig);
    assertThat(requestParameters.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(requestParameters.getArtifact()).isNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacPackageDeploymentNoPreviousArtifactSwapSlotHappened() {
    AzureWebAppInfraDelegateConfig azureWebAppInfraDelegateConfig = AzureWebAppInfraDelegateConfig.builder().build();
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        AzureAppServicePreDeploymentData.builder().build();
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(AzureWebAppSwapSlotsDataOutput.builder()
                             .deploymentProgressMarker(AppServiceDeploymentProgress.SWAP_SLOT.name())
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                SWAP_SLOT_STEP_FQN + "." + AzureWebAppSwapSlotsDataOutput.OUTPUT_NAME)));
    doReturn(azureAppServicePreDeploymentData).when(stepHelper).getPreDeploymentData(eq(ambiance), any());
    doReturn(azureWebAppInfraDelegateConfig).when(stepHelper).getInfraDelegateConfig(any(), any(), any());
    doReturn(AzurePackageArtifactConfig.builder().build()).when(stepHelper).getPrimaryArtifactConfig(any(), any());

    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    doReturn(TaskRequest.newBuilder().build())
        .when(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), any());

    TaskRequest taskRequest =
        azureWebAppRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();

    verify(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG),
            eq(Arrays.asList(SLOT_SWAP, UPDATE_SLOT_CONFIGURATION_SETTINGS, DEPLOY_TO_SLOT, SLOT_TRAFFIC_PERCENTAGE)));

    AzureWebAppRollbackRequest requestParameters = (AzureWebAppRollbackRequest) taskParametersArgumentCaptor.getValue();

    assertThat(requestParameters.getRequestType()).isEqualTo(ROLLBACK);
    assertThat(requestParameters.getPreDeploymentData()).isEqualTo(azureAppServicePreDeploymentData);
    assertThat(requestParameters.getPreDeploymentData().getDeploymentProgressMarker())
        .isEqualTo(AppServiceDeploymentProgress.SWAP_SLOT.name());
    assertThat(requestParameters.getInfrastructure()).isEqualTo(azureWebAppInfraDelegateConfig);
    assertThat(requestParameters.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(requestParameters.getArtifact()).isNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacPackageDeploymentNoPreviousArtifactSwapSlotDidNotHappen() {
    AzureWebAppInfraDelegateConfig azureWebAppInfraDelegateConfig = AzureWebAppInfraDelegateConfig.builder().build();
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        AzureAppServicePreDeploymentData.builder().build();
    doReturn(OptionalSweepingOutput.builder().found(false).output(null).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                SWAP_SLOT_STEP_FQN + "." + AzureWebAppSwapSlotsDataOutput.OUTPUT_NAME)));
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(AzureWebAppSlotDeploymentDataOutput.builder().deploymentProgressMarker(DEPLOY_TO_SLOT).build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                SLOT_DEPLOYMENT_STEP_FQN + "." + AzureWebAppSlotDeploymentDataOutput.OUTPUT_NAME)));
    doReturn(azureAppServicePreDeploymentData).when(stepHelper).getPreDeploymentData(eq(ambiance), any());
    doReturn(azureWebAppInfraDelegateConfig).when(stepHelper).getInfraDelegateConfig(any(), any(), any());
    doReturn(true).when(stepHelper).isPackageArtifactType(any());

    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    doReturn(TaskRequest.newBuilder().build())
        .when(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), any());

    TaskRequest taskRequest =
        azureWebAppRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest().getMessage())
        .isEqualTo("No swap slots done and previous artifact not found, skipping rollback");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacPackageDeploymentPreviousArtifact() {
    AzureWebAppInfraDelegateConfig azureWebAppInfraDelegateConfig = AzureWebAppInfraDelegateConfig.builder().build();
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        AzureAppServicePreDeploymentData.builder().build();
    AzurePackageArtifactConfig lastArtifactConfig = AzurePackageArtifactConfig.builder().build();
    AzureWebAppsStageExecutionDetails azureWebAppsStageExecutionDetails =
        AzureWebAppsStageExecutionDetails.builder().artifactConfig(lastArtifactConfig).cleanDeployment(false).build();
    doReturn(azureWebAppsStageExecutionDetails)
        .when(stepHelper)
        .findLastSuccessfulStageExecutionDetails(ambiance, azureWebAppInfraDelegateConfig);
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(AzureWebAppSwapSlotsDataOutput.builder()
                             .deploymentProgressMarker(AppServiceDeploymentProgress.SWAP_SLOT.name())
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                SWAP_SLOT_STEP_FQN + "." + AzureWebAppSwapSlotsDataOutput.OUTPUT_NAME)));
    doReturn(azureAppServicePreDeploymentData).when(stepHelper).getPreDeploymentData(eq(ambiance), any());
    doReturn(azureWebAppInfraDelegateConfig).when(stepHelper).getInfraDelegateConfig(any(), any(), any());
    doReturn(true).when(stepHelper).isPackageArtifactType(any());
    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    doReturn(TaskRequest.newBuilder().build())
        .when(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), any());

    TaskRequest taskRequest =
        azureWebAppRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();

    verify(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG),
            eq(Arrays.asList(FETCH_ARTIFACT_FILE, SLOT_SWAP, UPDATE_SLOT_CONFIGURATION_SETTINGS, DEPLOY_TO_SLOT,
                SLOT_TRAFFIC_PERCENTAGE)));

    AzureWebAppRollbackRequest requestParameters = (AzureWebAppRollbackRequest) taskParametersArgumentCaptor.getValue();

    assertThat(requestParameters.getRequestType()).isEqualTo(ROLLBACK);
    assertThat(requestParameters.getPreDeploymentData()).isEqualTo(azureAppServicePreDeploymentData);
    assertThat(requestParameters.getPreDeploymentData().getDeploymentProgressMarker())
        .isEqualTo(AppServiceDeploymentProgress.SWAP_SLOT.name());
    assertThat(requestParameters.getInfrastructure()).isEqualTo(azureWebAppInfraDelegateConfig);
    assertThat(requestParameters.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(requestParameters.getArtifact()).isEqualTo(lastArtifactConfig);
    assertThat(requestParameters.isCleanDeployment()).isFalse();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacPackageDeploymentUsingPreLastArtifact() {
    AzureWebAppInfraDelegateConfig azureWebAppInfraDelegateConfig = AzureWebAppInfraDelegateConfig.builder().build();
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        AzureAppServicePreDeploymentData.builder().build();
    AzurePackageArtifactConfig lastArtifactConfig =
        AzurePackageArtifactConfig.builder().connectorConfig(ArtifactoryConnectorDTO.builder().build()).build();
    AzurePackageArtifactConfig preLastArtifactConfig =
        AzurePackageArtifactConfig.builder().connectorConfig(ArtifactoryConnectorDTO.builder().build()).build();
    AzureWebAppsStageExecutionDetails lastAzureExecutionDetails =
        AzureWebAppsStageExecutionDetails.builder().targetSlot("targetSlot").artifactConfig(lastArtifactConfig).build();
    AzureWebAppsStageExecutionDetails preLastExecutionDetails =
        AzureWebAppsStageExecutionDetails.builder().artifactConfig(preLastArtifactConfig).cleanDeployment(true).build();
    doReturn(preLastExecutionDetails)
        .when(stepHelper)
        .findLastSuccessfulStageExecutionDetails(ambiance, azureWebAppInfraDelegateConfig);
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(AzureWebAppSwapSlotsDataOutput.builder()
                             .deploymentProgressMarker(AppServiceDeploymentProgress.SWAP_SLOT.name())
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                SWAP_SLOT_STEP_FQN + "." + AzureWebAppSwapSlotsDataOutput.OUTPUT_NAME)));
    doReturn(azureAppServicePreDeploymentData).when(stepHelper).getPreDeploymentData(eq(ambiance), any());
    doReturn(azureWebAppInfraDelegateConfig).when(stepHelper).getInfraDelegateConfig(any(), any(), any());
    doReturn(true).when(stepHelper).isPackageArtifactType(any());
    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    doReturn(TaskRequest.newBuilder().build())
        .when(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), any());

    TaskRequest taskRequest =
        azureWebAppRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();

    verify(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG),
            eq(Arrays.asList(FETCH_ARTIFACT_FILE, SLOT_SWAP, UPDATE_SLOT_CONFIGURATION_SETTINGS, DEPLOY_TO_SLOT,
                SLOT_TRAFFIC_PERCENTAGE)));

    AzureWebAppRollbackRequest requestParameters = (AzureWebAppRollbackRequest) taskParametersArgumentCaptor.getValue();

    assertThat(requestParameters.getRequestType()).isEqualTo(ROLLBACK);
    assertThat(requestParameters.getPreDeploymentData()).isEqualTo(azureAppServicePreDeploymentData);
    assertThat(requestParameters.getPreDeploymentData().getDeploymentProgressMarker())
        .isEqualTo(AppServiceDeploymentProgress.SWAP_SLOT.name());
    assertThat(requestParameters.getInfrastructure()).isEqualTo(azureWebAppInfraDelegateConfig);
    assertThat(requestParameters.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(requestParameters.getArtifact()).isEqualTo(preLastArtifactConfig);
    assertThat(requestParameters.isCleanDeployment()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacNoSwapSlots() {
    AzureWebAppInfraDelegateConfig azureWebAppInfraDelegateConfig = AzureWebAppInfraDelegateConfig.builder().build();
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        AzureAppServicePreDeploymentData.builder().build();
    doReturn(OptionalSweepingOutput.builder().found(false).output(null).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                SWAP_SLOT_STEP_FQN + "." + AzureWebAppSwapSlotsDataOutput.OUTPUT_NAME)));
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(AzureWebAppSlotDeploymentDataOutput.builder()
                             .deploymentProgressMarker(AppServiceDeploymentProgress.DEPLOY_TO_SLOT.name())
                             .build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                SLOT_DEPLOYMENT_STEP_FQN + "." + AzureWebAppSlotDeploymentDataOutput.OUTPUT_NAME)));
    doReturn(azureAppServicePreDeploymentData).when(stepHelper).getPreDeploymentData(eq(ambiance), any());
    doReturn(azureWebAppInfraDelegateConfig).when(stepHelper).getInfraDelegateConfig(any(), any(), any());
    doReturn(AzureContainerArtifactConfig.builder().build()).when(stepHelper).getPrimaryArtifactConfig(any(), any());

    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    doReturn(TaskRequest.newBuilder().build())
        .when(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), any());

    TaskRequest taskRequest =
        azureWebAppRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();

    verify(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG),
            eq(Arrays.asList(UPDATE_SLOT_CONFIGURATION_SETTINGS, DEPLOY_TO_SLOT, SLOT_TRAFFIC_PERCENTAGE)));

    AzureWebAppRollbackRequest requestParameters = (AzureWebAppRollbackRequest) taskParametersArgumentCaptor.getValue();

    assertThat(requestParameters.getRequestType()).isEqualTo(ROLLBACK);
    assertThat(requestParameters.getPreDeploymentData()).isEqualTo(azureAppServicePreDeploymentData);
    assertThat(requestParameters.getPreDeploymentData().getDeploymentProgressMarker())
        .isEqualTo(AppServiceDeploymentProgress.DEPLOY_TO_SLOT.name());
    assertThat(requestParameters.getInfrastructure()).isEqualTo(azureWebAppInfraDelegateConfig);
    assertThat(requestParameters.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacNoDeploymentMarker() {
    AzureWebAppInfraDelegateConfig azureWebAppInfraDelegateConfig = AzureWebAppInfraDelegateConfig.builder().build();
    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        AzureAppServicePreDeploymentData.builder().build();
    doReturn(OptionalSweepingOutput.builder().found(false).output(null).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                SWAP_SLOT_STEP_FQN + "." + AzureWebAppSwapSlotsDataOutput.OUTPUT_NAME)));
    doReturn(OptionalSweepingOutput.builder().found(false).output(null).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                SLOT_DEPLOYMENT_STEP_FQN + "." + AzureWebAppSlotDeploymentDataOutput.OUTPUT_NAME)));
    doReturn(azureAppServicePreDeploymentData).when(stepHelper).getPreDeploymentData(eq(ambiance), any());
    doReturn(azureWebAppInfraDelegateConfig).when(stepHelper).getInfraDelegateConfig(any(), any(), any());
    doReturn(AzureContainerArtifactConfig.builder().build()).when(stepHelper).getPrimaryArtifactConfig(any(), any());

    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    doReturn(TaskRequest.newBuilder().build())
        .when(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), any());

    TaskRequest taskRequest =
        azureWebAppRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();

    verify(stepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG),
            eq(Arrays.asList(UPDATE_SLOT_CONFIGURATION_SETTINGS, DEPLOY_TO_SLOT, SLOT_TRAFFIC_PERCENTAGE)));

    AzureWebAppRollbackRequest requestParameters = (AzureWebAppRollbackRequest) taskParametersArgumentCaptor.getValue();

    assertThat(requestParameters.getRequestType()).isEqualTo(ROLLBACK);
    assertThat(requestParameters.getPreDeploymentData()).isEqualTo(azureAppServicePreDeploymentData);
    assertThat(requestParameters.getPreDeploymentData().getDeploymentProgressMarker())
        .isEqualTo(AppServiceDeploymentProgress.DEPLOY_TO_SLOT.name());
    assertThat(requestParameters.getInfrastructure()).isEqualTo(azureWebAppInfraDelegateConfig);
    assertThat(requestParameters.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacNoPreDeploymentData() {
    doReturn(OptionalSweepingOutput.builder().found(false).output(null).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(),
            eq(RefObjectUtils.getSweepingOutputRefObject(
                SLOT_DEPLOYMENT_STEP_FQN + "." + AzureWebAppPreDeploymentDataOutput.OUTPUT_NAME)));

    TaskRequest taskRequest =
        azureWebAppRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest.getSkipTaskRequest().getMessage())
        .isEqualTo("Slot deployment step was not successful, rollback data not found");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleResponseWithSecurityContext() throws Exception {
    List<UnitProgress> unitProgresses = singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    AzureWebAppTaskResponse azureWebAppTaskResponse =
        AzureWebAppTaskResponse.builder()
            .commandUnitsProgress(unitProgressData)
            .requestResponse(AzureWebAppNGRollbackResponse.builder()
                                 .azureAppDeploymentData(Arrays.asList(AzureAppDeploymentData.builder().build()))
                                 .deploymentProgressMarker(SLOT_SWAP)
                                 .build())
            .build();

    StepResponse stepResponse = azureWebAppRollbackStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> azureWebAppTaskResponse);

    verify(instanceInfoService).saveServerInstancesIntoSweepingOutput(any(), any());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextExceptionThrown() {
    assertThatThrownBy(
        () -> azureWebAppRollbackStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> null))
        .isInstanceOf(Exception.class);
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, ACCOUNT_ID);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }
}
