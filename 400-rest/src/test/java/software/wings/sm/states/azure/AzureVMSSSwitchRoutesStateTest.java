/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.azure.model.AzureConstants.SKIP_VMSS_DEPLOY;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ANIL;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.dto.Log.Builder.aLog;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.azure.AzureVMSSPreDeploymentData;
import io.harness.delegate.task.azure.request.AzureLoadBalancerDetailForBGDeployment;
import io.harness.delegate.task.azure.request.AzureVMSSSwitchRouteTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSSwitchRoutesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Activity;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.command.AzureVMSSDummyCommandUnit;
import software.wings.service.impl.azure.manager.AzureVMSSCommandRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.ManagerExecutionLogCallback;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AzureVMSSSwitchRoutesStateTest extends WingsBaseTest {
  @Mock private DelegateService delegateService;
  @Mock private AzureVMSSStateHelper azureVMSSStateHelper;
  @Mock private ActivityService activityService;
  @Mock private LogService logService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @InjectMocks
  private final AzureVMSSSwitchRoutesState switchRoutesState = new AzureVMSSSwitchRoutesState("switch-route-state");
  @InjectMocks
  private final AzureVMSSSwitchRoutesRollbackState switchRouteRollbackState =
      new AzureVMSSSwitchRoutesRollbackState("switch-route-rollback-state");

  private final int timeOut = 10;
  private static final String NEW_SCALE_SET = "newScaleSet";
  private static final String OLD_SCALE_SET = "oldScaleSet";
  private static final String LOAD_BALANCER = "loadBalancer";
  private static final String PROD_BACKEND_POOL = "prodPool";
  private static final String STAGE_BACKEND_POOL = "stagePool";

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteExecute() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, true, true);
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
    switchRoutesState.handleAbortEvent(mockContext);
    ExecutionResponse response = switchRoutesState.execute(mockContext);
    assertThat(switchRoutesState.getTimeoutMillis(mockContext)).isEqualTo(timeOut);
    assertThat(switchRoutesState.getSkipMessage()).isEqualTo(SKIP_VMSS_DEPLOY);
    verifyDelegateTaskCreationResult(response, false);
    verify(stateExecutionService).appendDelegateTaskDetails(any(), any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteFailure() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doAnswer(invocation -> { throw new Exception(); })
        .when(azureVMSSStateHelper)
        .createAndSaveActivity(eq(mockContext), eq(null), anyString(), anyString(), any(), any());
    switchRoutesState.execute(mockContext);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteWingsExceptionFailure() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doAnswer(invocation -> { throw new WingsException(""); })
        .when(azureVMSSStateHelper)
        .createAndSaveActivity(eq(mockContext), eq(null), anyString(), anyString(), any(), any());
    switchRoutesState.execute(mockContext);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteExecuteFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, false, true);
    ExecutionResponse response = switchRoutesState.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteExecuteSetupElementFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, false, false);
    ExecutionResponse response = switchRoutesState.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteHandleAsyncResponse() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, true, true);
    AzureVMSSTaskExecutionResponse taskExecutionResponse =
        AzureVMSSTaskExecutionResponse.builder()
            .azureVMSSTaskResponse(
                AzureVMSSSwitchRoutesResponse.builder().delegateMetaInfo(DelegateMetaInfo.builder().build()).build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();

    ExecutionResponse response =
        switchRoutesState.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, taskExecutionResponse));
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteHandleAsyncResponseFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, true, true);
    AzureVMSSTaskExecutionResponse taskExecutionResponse =
        AzureVMSSTaskExecutionResponse.builder()
            .azureVMSSTaskResponse(
                AzureVMSSSwitchRoutesResponse.builder().delegateMetaInfo(DelegateMetaInfo.builder().build()).build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    doAnswer(invocation -> { throw new Exception(); }).when(azureVMSSStateHelper).getExecutionStatus(any());
    switchRoutesState.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, taskExecutionResponse));
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteHandleAsyncResponseWingsExceptionFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, true, true);
    AzureVMSSTaskExecutionResponse taskExecutionResponse =
        AzureVMSSTaskExecutionResponse.builder()
            .azureVMSSTaskResponse(
                AzureVMSSSwitchRoutesResponse.builder().delegateMetaInfo(DelegateMetaInfo.builder().build()).build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    doAnswer(invocation -> { throw new WingsException(""); }).when(azureVMSSStateHelper).getExecutionStatus(any());
    switchRoutesState.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, taskExecutionResponse));
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteRollBackExecute() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRouteRollbackState, true, true);
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
    ExecutionResponse response = switchRouteRollbackState.execute(mockContext);
    assertThat(switchRouteRollbackState.isDownsizeOldVMSS()).isTrue();
    assertThat(switchRouteRollbackState.getTimeoutMillis()).isNull();
    verifyDelegateTaskCreationResult(response, true);
    verify(stateExecutionService).appendDelegateTaskDetails(any(), any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteRollBackSetupElementFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRouteRollbackState, true, false);
    ExecutionResponse response = switchRouteRollbackState.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(SKIPPED);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteRollBackFailure() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doAnswer(invocation -> { throw new Exception(); })
        .when(azureVMSSStateHelper)
        .createAndSaveActivity(eq(mockContext), eq(null), anyString(), anyString(), any(), any());
    switchRouteRollbackState.execute(mockContext);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteRollBackWingsExceptionFailure() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doAnswer(invocation -> { throw new WingsException(""); })
        .when(azureVMSSStateHelper)
        .createAndSaveActivity(eq(mockContext), eq(null), anyString(), anyString(), any(), any());
    switchRouteRollbackState.execute(mockContext);
  }

  private ExecutionContextImpl initializeMockSetup(
      AzureVMSSSwitchRoutesState routeState, boolean isSuccess, boolean contextElement) {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    routeState.setDownsizeOldVMSS(true);

    AzureVMSSStateData azureVMSSStateData = AzureVMSSStateData.builder()
                                                .application(anApplication().uuid(APP_ID).build())
                                                .service(Service.builder().build())
                                                .serviceId("serviceId")
                                                .azureConfig(AzureConfig.builder().build())
                                                .azureEncryptedDataDetails(emptyList())
                                                .infrastructureMapping(AzureVMSSInfrastructureMapping.builder()
                                                                           .resourceGroupName("ResourceGroup")
                                                                           .subscriptionId("subscriptionId")
                                                                           .baseVMSSName("baseScaleSet")
                                                                           .build())
                                                .artifact(anArtifact().withUuid(ARTIFACT_ID).build())
                                                .environment(anEnvironment().uuid(ENV_ID).build())
                                                .build();

    doReturn(azureVMSSStateData).when(azureVMSSStateHelper).populateStateData(mockContext);
    doReturn(timeOut).when(azureVMSSStateHelper).getAzureVMSSStateTimeoutFromContext(mockContext);

    if (contextElement) {
      AzureVMSSSetupContextElement azureVMSSSetupContextElement =
          AzureVMSSSetupContextElement.builder()
              .isBlueGreen(true)
              .desiredInstances(2)
              .maxInstances(4)
              .minInstances(1)
              .autoScalingSteadyStateVMSSTimeout(10)
              .newVirtualMachineScaleSetName(NEW_SCALE_SET)
              .oldVirtualMachineScaleSetName(OLD_SCALE_SET)
              .oldDesiredCount(1)
              .baseVMSSScalingPolicyJSONs(emptyList())
              .resizeStrategy(ResizeStrategy.RESIZE_NEW_FIRST)
              .commandName(AzureVMSSSwitchRoutesState.AZURE_VMSS_SWAP_ROUTE)
              .infraMappingId("infraId")
              .azureLoadBalancerDetail(AzureLoadBalancerDetailForBGDeployment.builder()
                                           .loadBalancerName(LOAD_BALANCER)
                                           .prodBackendPool(PROD_BACKEND_POOL)
                                           .stageBackendPool(STAGE_BACKEND_POOL)
                                           .build())
              .preDeploymentData(AzureVMSSPreDeploymentData.builder().build())
              .build();

      doReturn(azureVMSSSetupContextElement).when(mockContext).getContextElement(any());
    }

    Activity activity = Activity.builder()
                            .uuid(ACTIVITY_ID)
                            .commandUnits(Collections.singletonList(new AzureVMSSDummyCommandUnit()))
                            .build();
    doReturn(activity)
        .when(azureVMSSStateHelper)
        .createAndSaveActivity(eq(mockContext), eq(null), anyString(), anyString(), any(), any());

    ManagerExecutionLogCallback executionLogCallback = new ManagerExecutionLogCallback(logService,
        aLog().appId(activity.getAppId()).activityId(activity.getUuid()).commandUnitName("test"), activity.getUuid());
    doReturn(executionLogCallback).when(azureVMSSStateHelper).getExecutionLogCallback(eq(activity));

    AzureVMSSSwitchRouteStateExecutionData stateExecutionData =
        AzureVMSSSwitchRouteStateExecutionData.builder().build();
    doReturn(stateExecutionData).when(mockContext).getStateExecutionData();

    doReturn(SUCCESS).when(azureVMSSStateHelper).getExecutionStatus(any());

    if (!isSuccess) {
      doAnswer(invocation -> { throw new Exception(); }).when(delegateService).queueTaskV2(any());
    }
    return mockContext;
  }

  private void verifyDelegateTaskCreationResult(ExecutionResponse response, boolean isRollback) {
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());

    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(1).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof AzureVMSSCommandRequest).isTrue();
    AzureVMSSCommandRequest params = (AzureVMSSCommandRequest) delegateTask.getData().getParameters()[0];

    assertThat(params.getAzureVMSSTaskParameters() instanceof AzureVMSSSwitchRouteTaskParameters).isTrue();
    AzureVMSSSwitchRouteTaskParameters azureVMSSTaskParameters =
        (AzureVMSSSwitchRouteTaskParameters) params.getAzureVMSSTaskParameters();

    assertThat(azureVMSSTaskParameters.getOldVMSSName()).isEqualTo(OLD_SCALE_SET);
    assertThat(azureVMSSTaskParameters.getNewVMSSName()).isEqualTo(NEW_SCALE_SET);
    assertThat(azureVMSSTaskParameters.isDownscaleOldVMSS()).isEqualTo(true);
    assertThat(azureVMSSTaskParameters.isRollback()).isEqualTo(isRollback);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    assertThat(response.getStateExecutionData()).isNotNull();
    assertThat(response.getStateExecutionData()).isInstanceOf(AzureVMSSSwitchRouteStateExecutionData.class);
    AzureVMSSSwitchRouteStateExecutionData stateExecutionData =
        (AzureVMSSSwitchRouteStateExecutionData) response.getStateExecutionData();
    assertThat(stateExecutionData.equals(new AzureVMSSSwitchRouteStateExecutionData())).isFalse();
    assertThat(stateExecutionData.toString()).isNotNull();
    assertThat(stateExecutionData.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(stateExecutionData.getNewVirtualMachineScaleSetName()).isEqualTo(NEW_SCALE_SET);
    assertThat(stateExecutionData.getOldVirtualMachineScaleSetName()).isEqualTo(OLD_SCALE_SET);
    assertThat(stateExecutionData.getProdBackendPool()).isEqualTo(PROD_BACKEND_POOL);
    assertThat(stateExecutionData.getStageBackendPool()).isEqualTo(STAGE_BACKEND_POOL);

    assertThat(stateExecutionData.getStepExecutionSummary()).isNotNull();
    assertThat(stateExecutionData.getExecutionDetails()).isNotEmpty();
    assertThat(stateExecutionData.getExecutionSummary()).isNotEmpty();
  }
}
