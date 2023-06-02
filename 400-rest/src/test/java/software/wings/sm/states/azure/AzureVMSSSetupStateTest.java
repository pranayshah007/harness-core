/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.IVAN;

import static software.wings.sm.states.azure.AzureVMSSSetupState.AZURE_VMSS_SETUP_COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import io.harness.azure.model.AzureConstants;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.azure.AzureVMSSPreDeploymentData;
import io.harness.delegate.task.azure.request.AzureLoadBalancerDetailForBGDeployment;
import io.harness.delegate.task.azure.response.AzureVMSSSetupTaskResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.VMSSAuthType;
import software.wings.beans.VMSSDeploymentType;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.ManagerExecutionLogCallback;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class AzureVMSSSetupStateTest extends WingsBaseTest {
  @Mock private DelegateService delegateService;
  @Mock private AzureVMSSStateHelper azureVMSSStateHelper;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @Spy @InjectMocks AzureVMSSSetupState state = new AzureVMSSSetupState("Azure VMSS Setup State");

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecute() {
    reset(azureVMSSStateHelper);
    String appId = "appId";
    String serviceId = "serviceId";
    String envId = "envId";
    String activityId = "activityId";
    String infraMappingId = "infraMappingId";
    String userData = "userData";
    String namePrefix = "namePrefix";
    String delegateResult = "Done";
    int autoScalingSteadyStateVMSSTimeoutFixed = 1;
    Integer numberOfInstances = 1;
    boolean isBlueGreen = false;
    Application app = Application.Builder.anApplication().uuid(appId).build();
    Environment env = Environment.Builder.anEnvironment().uuid(envId).build();
    Service service = Service.builder().uuid(serviceId).build();
    Activity activity = Activity.builder().uuid(activityId).build();
    InfrastructureMapping azureVMSSInfrastructureMapping = AzureVMSSInfrastructureMapping.builder()
                                                               .baseVMSSName("baseVMSSName")
                                                               .resourceGroupName("resourceGroupName")
                                                               .subscriptionId("subscriptionId")
                                                               .passwordSecretTextName("password")
                                                               .userName("userName")
                                                               .vmssAuthType(VMSSAuthType.PASSWORD)
                                                               .vmssDeploymentType(VMSSDeploymentType.NATIVE_VMSS)
                                                               .build();
    azureVMSSInfrastructureMapping.setUuid("infraId");
    AzureConfig azureConfig = AzureConfig.builder().build();
    Artifact artifact = Artifact.Builder.anArtifact().build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    state.setResizeStrategy(ResizeStrategy.RESIZE_NEW_FIRST);
    state.setAutoScalingSteadyStateVMSSTimeout("10");
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    ManagerExecutionLogCallback managerExecutionLogCallback = mock(ManagerExecutionLogCallback.class);

    doReturn(app).when(azureVMSSStateHelper).getApplication(context);
    doReturn(env).when(azureVMSSStateHelper).getEnvironment(context);
    doReturn(service).when(azureVMSSStateHelper).getServiceByAppId(any(), anyString());
    doReturn(activity)
        .when(azureVMSSStateHelper)
        .createAndSaveActivity(any(), any(), anyString(), anyString(), any(), anyList());
    doReturn(managerExecutionLogCallback).when(azureVMSSStateHelper).getExecutionLogCallback(activity);
    doReturn(infraMappingId).when(context).fetchInfraMappingId();
    doReturn(azureVMSSInfrastructureMapping).when(infrastructureMappingService).get(infraMappingId, appId);
    doReturn(autoScalingSteadyStateVMSSTimeoutFixed)
        .when(azureVMSSStateHelper)
        .renderTimeoutExpressionOrGetDefault(anyString(), any(), anyInt());
    doReturn(azureVMSSInfrastructureMapping)
        .when(azureVMSSStateHelper)
        .getAzureVMSSInfrastructureMapping(anyString(), anyString());
    doReturn(azureConfig)
        .when(azureVMSSStateHelper)
        .getAzureConfig(azureVMSSInfrastructureMapping.getComputeProviderSettingId());
    doReturn(encryptedDataDetails)
        .when(azureVMSSStateHelper)
        .getEncryptedDataDetails(context, azureVMSSInfrastructureMapping.getComputeProviderSettingId());
    doReturn(artifact).when(azureVMSSStateHelper).getArtifact(any(), any());
    doReturn(isBlueGreen).when(azureVMSSStateHelper).isBlueGreenWorkflow(context);
    doReturn(userData).when(azureVMSSStateHelper).getBase64EncodedUserData(context, appId, serviceId);
    doReturn(namePrefix)
        .when(azureVMSSStateHelper)
        .fixNamePrefix(any(), anyString(), anyString(), anyString(), anyString());
    doReturn(numberOfInstances).when(azureVMSSStateHelper).renderExpressionOrGetDefault(anyString(), any(), anyInt());
    doReturn(delegateResult).when(delegateService).queueTaskV2(any());
    doReturn(10)
        .when(azureVMSSStateHelper)
        .renderExpressionOrGetDefault(eq("10"), eq(context), eq(AzureConstants.DEFAULT_AZURE_VMSS_TIMEOUT_MIN));
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());
    initializeFields(state);

    ExecutionResponse result = state.execute(context);

    assertThat(state.getVirtualMachineScaleSetName()).isEqualTo("newScaleSet");
    assertThat(state.getMinInstances()).isEqualTo("1");
    assertThat(state.getMaxInstances()).isEqualTo("5");
    assertThat(state.getDesiredInstances()).isEqualTo("2");
    assertThat(state.isUseCurrentRunningCount()).isEqualTo(false);

    assertThat(state.getTimeoutMillis(context)).isEqualTo(600000);
    assertThat(result).isNotNull();
    assertThat(result.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(result.getErrorMessage()).isNull();

    assertThat(result.getStateExecutionData()).isNotNull();
    assertThat(result.getStateExecutionData()).isInstanceOf(AzureVMSSSetupStateExecutionData.class);
    AzureVMSSSetupStateExecutionData stateExecutionData =
        (AzureVMSSSetupStateExecutionData) result.getStateExecutionData();
    assertThat(stateExecutionData.getActivityId()).isEqualTo(activityId);
    assertThat(stateExecutionData.getMaxInstances()).isEqualTo(1);
    assertThat(stateExecutionData.getDesiredInstances()).isEqualTo(1);
    assertThat(stateExecutionData.getResizeStrategy()).isEqualTo(ResizeStrategy.RESIZE_NEW_FIRST);
    assertThat(stateExecutionData.getInfrastructureMappingId()).isEqualTo("infraId");

    assertThat(stateExecutionData.getStepExecutionSummary()).isNotNull();
    AzureVMSSSetupExecutionSummary stepExecutionSummary = stateExecutionData.getStepExecutionSummary();
    assertThat(stepExecutionSummary.equals(AzureVMSSSetupExecutionSummary.builder().build())).isFalse();
    assertThat(stepExecutionSummary.toString()).isNotNull();

    assertThat(stateExecutionData.getExecutionDetails()).isNotEmpty();
    assertThat(stateExecutionData.getExecutionSummary()).isNotEmpty();
    verify(stateExecutionService).appendDelegateTaskDetails(any(), any());
  }

  private void initializeFields(AzureVMSSSetupState state) {
    state.setVirtualMachineScaleSetName("newScaleSet");
    state.setMinInstances("1");
    state.setMaxInstances("5");
    state.setDesiredInstances("2");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteGenericExceptionFailure() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doAnswer(invocation -> { throw new Exception(); }).when(azureVMSSStateHelper).getApplication(eq(context));
    state.execute(context);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteWingsExceptionFailure() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doThrow(WingsException.class).when(azureVMSSStateHelper).getApplication(eq(context));
    state.execute(context);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    reset(azureVMSSStateHelper);
    String newVirtualMachineScaleSetName = "newVirtualMachineScaleSetName";
    String lastDeployedVMSSName = "lastDeployedVMSSName";
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doNothing().when(azureVMSSStateHelper).updateActivityStatus(anyString(), anyString(), any());
    doReturn(false).when(azureVMSSStateHelper).isBlueGreenWorkflow(context);
    doReturn(5).when(azureVMSSStateHelper).renderTimeoutExpressionOrGetDefault(anyString(), any(), anyInt());
    doReturn(SUCCESS).when(azureVMSSStateHelper).getExecutionStatus(any());
    Map<String, ResponseData> responseMap = getDelegateResponse();
    AzureVMSSSetupStateExecutionData data = AzureVMSSSetupStateExecutionData.builder()
                                                .newVirtualMachineScaleSetName(newVirtualMachineScaleSetName)
                                                .oldVirtualMachineScaleSetName(lastDeployedVMSSName)
                                                .maxInstances(1)
                                                .desiredInstances(1)
                                                .build();
    doReturn(data).when(context).getStateExecutionData();

    ExecutionResponse executionResponse = state.handleAsyncResponse(context, responseMap);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    List<ContextElement> notifyElements = executionResponse.getNotifyElements();
    assertThat(notifyElements).isNotNull();
    assertThat(notifyElements.size()).isEqualTo(1);
    ContextElement contextElement = notifyElements.get(0);
    assertThat(contextElement).isNotNull();
    assertThat(contextElement instanceof AzureVMSSSetupContextElement).isTrue();
    AzureVMSSSetupContextElement azureVMSSSetupContextElement = (AzureVMSSSetupContextElement) contextElement;

    assertThat(azureVMSSSetupContextElement.equals(new AzureVMSSSetupContextElement())).isFalse();
    assertThat(azureVMSSSetupContextElement.getNewVirtualMachineScaleSetName())
        .isEqualTo(newVirtualMachineScaleSetName);
    assertThat(azureVMSSSetupContextElement.getOldVirtualMachineScaleSetName()).isEqualTo(lastDeployedVMSSName);
    assertThat(azureVMSSSetupContextElement.getUuid()).isNull();
    assertThat(azureVMSSSetupContextElement.getName()).isNull();
    assertThat(azureVMSSSetupContextElement.getCommandName()).isEqualTo(AZURE_VMSS_SETUP_COMMAND_NAME);
    assertThat(azureVMSSSetupContextElement.cloneMin()).isNull();
    assertThat(azureVMSSSetupContextElement.toString()).isNotNull();
    assertThat(azureVMSSSetupContextElement.getElementType()).isEqualTo(ContextElementType.AZURE_VMSS_SETUP);

    assertThat(executionResponse.getStateExecutionData()).isInstanceOf(AzureVMSSSetupStateExecutionData.class);
    AzureVMSSSetupStateExecutionData stateExecutionData =
        (AzureVMSSSetupStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(stateExecutionData.equals(new AzureVMSSSetupStateExecutionData())).isFalse();
    assertThat(stateExecutionData.toString()).isNotNull();
    assertThat(stateExecutionData.getNewVersion()).isEqualTo(3);
    assertThat(stateExecutionData.getOldVirtualMachineScaleSetName()).isEqualTo(lastDeployedVMSSName);
    assertThat(stateExecutionData.getNewVirtualMachineScaleSetName()).isEqualTo(newVirtualMachineScaleSetName);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseFailure() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    Map<String, ResponseData> responseMap = getDelegateResponse();
    doReturn(ExecutionStatus.FAILED).when(azureVMSSStateHelper).getExecutionStatus(any());
    ExecutionResponse executionResponse = state.handleAsyncResponse(context, responseMap);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseProcessingFailure() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    Map<String, ResponseData> responseMap = getDelegateResponse();
    doAnswer(invocation -> { throw new Exception(); }).when(azureVMSSStateHelper).getExecutionStatus(any());
    state.handleAsyncResponse(context, responseMap);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseWingsExceptionFailure() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    Map<String, ResponseData> responseMap = getDelegateResponse();
    doThrow(WingsException.class).when(azureVMSSStateHelper).getExecutionStatus(any());
    state.handleAsyncResponse(context, responseMap);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testValidateFields() {
    AzureVMSSSetupState azureSetupState = new AzureVMSSSetupState("Validate fields");
    azureSetupState.handleAbortEvent(mock(ExecutionContextImpl.class));

    assertThat(azureSetupState.isBlueGreen()).isFalse();
    assertThat(azureSetupState.validateFields()).isEmpty();

    azureSetupState.setBlueGreen(true);
    assertThat(azureSetupState.validateFields()).isNotEmpty();

    azureSetupState.setAzureLoadBalancerDetail(AzureLoadBalancerDetailForBGDeployment.builder().build());
    assertThat(azureSetupState.validateFields().size()).isEqualTo(3);

    azureSetupState.setAzureLoadBalancerDetail(AzureLoadBalancerDetailForBGDeployment.builder()
                                                   .loadBalancerName("lb")
                                                   .stageBackendPool("stage")
                                                   .prodBackendPool("prod")
                                                   .build());
    assertThat(azureSetupState.validateFields()).isEmpty();
  }

  @NotNull
  private ImmutableMap<String, ResponseData> getDelegateResponse() {
    return ImmutableMap.of(ACTIVITY_ID,
        AzureVMSSTaskExecutionResponse.builder()
            .azureVMSSTaskResponse(
                AzureVMSSSetupTaskResponse.builder()
                    .lastDeployedVMSSName("lastDeployedVMSSName")
                    .newVirtualMachineScaleSetName("newVirtualMachineScaleSetName")
                    .maxInstances(1)
                    .minInstances(1)
                    .desiredInstances(1)
                    .harnessRevision(3)
                    .preDeploymentData(AzureVMSSPreDeploymentData.builder().desiredCapacity(1).build())
                    .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build());
  }
}
