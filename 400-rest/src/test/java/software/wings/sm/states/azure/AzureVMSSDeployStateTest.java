/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.azure.model.AzureConstants.DEPLOYMENT_ERROR;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_STATUS;
import static io.harness.azure.model.AzureConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.IVAN;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.sm.StateType.AZURE_VMSS_DEPLOY;
import static software.wings.sm.states.azure.AzureVMSSDeployState.AZURE_VMSS_DEPLOY_COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.azure.response.AzureVMInstanceData;
import io.harness.delegate.task.azure.response.AzureVMSSDeployTaskResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.HostElement;
import software.wings.api.InstanceElementListParam;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.Service;
import software.wings.beans.VMSSAuthType;
import software.wings.beans.VMSSDeploymentType;
import software.wings.beans.command.CommandUnit;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.ManagerExecutionLogCallback;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class AzureVMSSDeployStateTest extends WingsBaseTest {
  @Mock private DelegateService delegateService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Spy @InjectMocks private AzureVMSSStateHelper azureVMSSStateHelper;
  @Spy @InjectMocks private AzureVMSSDeployState state = new AzureVMSSDeployState("Azure VMSS Deploy State");

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
    state.setInstanceCount(1);
    state.setInstanceUnitType(InstanceUnitType.COUNT);
    Application app = Application.Builder.anApplication().uuid(appId).build();
    Environment env = Environment.Builder.anEnvironment().uuid(envId).build();
    Service service = Service.builder().uuid(serviceId).build();
    Activity activity = Activity.builder().uuid(activityId).build();
    InfrastructureMapping azureVMSSInfrastructureMapping = getInfrastructureMapping();
    AzureConfig azureConfig = AzureConfig.builder().build();
    Artifact artifact = Artifact.Builder.anArtifact().build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    AzureVMSSSetupContextElement azureVMSSSetupContextElement =
        AzureVMSSSetupContextElement.builder()
            .infraMappingId("infraId")
            .autoScalingSteadyStateVMSSTimeout(10)
            .desiredInstances(1)
            .isBlueGreen(false)
            .maxInstances(1)
            .minInstances(1)
            .oldDesiredCount(1)
            .newVirtualMachineScaleSetName("newVirtualMachineScaleSetName")
            .oldVirtualMachineScaleSetName("oldVirtualMachineScaleSetName")
            .build();
    // mocks
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    ManagerExecutionLogCallback managerExecutionLogCallback = mock(ManagerExecutionLogCallback.class);

    doReturn(azureVMSSSetupContextElement).when(context).getContextElement(any());
    doReturn(app).when(azureVMSSStateHelper).getApplication(context);
    doReturn(env).when(azureVMSSStateHelper).getEnvironment(context);
    doReturn(service).when(azureVMSSStateHelper).getServiceByAppId(any(), any());
    doReturn(activity).when(azureVMSSStateHelper).createAndSaveActivity(any(), any(), any(), any(), any(), anyList());
    doReturn(managerExecutionLogCallback).when(azureVMSSStateHelper).getExecutionLogCallback(activity);
    doReturn(infraMappingId).when(context).fetchInfraMappingId();
    doReturn(azureVMSSInfrastructureMapping).when(infrastructureMappingService).get(infraMappingId, appId);
    doReturn(autoScalingSteadyStateVMSSTimeoutFixed)
        .when(azureVMSSStateHelper)
        .renderTimeoutExpressionOrGetDefault(any(), any(), anyInt());
    doReturn(azureVMSSInfrastructureMapping).when(azureVMSSStateHelper).getAzureVMSSInfrastructureMapping(any(), any());
    doReturn(azureConfig)
        .when(azureVMSSStateHelper)
        .getAzureConfig(azureVMSSInfrastructureMapping.getComputeProviderSettingId());
    doReturn(encryptedDataDetails)
        .when(azureVMSSStateHelper)
        .getEncryptedDataDetails(context, azureVMSSInfrastructureMapping.getComputeProviderSettingId());
    doReturn(artifact).when(azureVMSSStateHelper).getArtifact(any(), any());
    doReturn(isBlueGreen).when(azureVMSSStateHelper).isBlueGreenWorkflow(context);
    doReturn(userData).when(azureVMSSStateHelper).getBase64EncodedUserData(context, appId, serviceId);
    doReturn(namePrefix).when(azureVMSSStateHelper).fixNamePrefix(any(), any(), any(), any(), any());
    doReturn(numberOfInstances).when(azureVMSSStateHelper).renderExpressionOrGetDefault(any(), any(), anyInt());
    doReturn(delegateResult).when(delegateService).queueTaskV2(any());
    doReturn("newVirtualMachineScaleSetName-id")
        .when(azureVMSSStateHelper)
        .getVMSSIdFromName(eq("subscriptionId"), eq("resourceGroupName"), eq("newVirtualMachineScaleSetName"));
    doReturn("oldVirtualMachineScaleSetName-id")
        .when(azureVMSSStateHelper)
        .getVMSSIdFromName(eq("subscriptionId"), eq("resourceGroupName"), eq("oldVirtualMachineScaleSetName"));
    doReturn(60000).when(azureVMSSStateHelper).getAzureVMSSStateTimeoutFromContext(context);
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());

    ExecutionResponse result = state.execute(context);
    state.handleAbortEvent(context);
    assertThat(state.getTimeoutMillis(context)).isEqualTo(60000);

    assertThat(result).isNotNull();
    assertThat(result.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(result.getErrorMessage()).isNull();
    assertThat(result.getStateExecutionData()).isNotNull();
    assertThat(result.getStateExecutionData()).isInstanceOf(AzureVMSSDeployStateExecutionData.class);

    AzureVMSSDeployStateExecutionData stateExecutionData =
        (AzureVMSSDeployStateExecutionData) result.getStateExecutionData();
    assertThat(stateExecutionData.equals(new AzureVMSSDeployStateExecutionData())).isFalse();
    assertThat(stateExecutionData.getActivityId()).isEqualTo(activityId);
    assertThat(stateExecutionData.getInfraMappingId()).isEqualTo("infraId");
    assertThat(stateExecutionData.getCommandName()).isEqualTo(AZURE_VMSS_DEPLOY_COMMAND_NAME);
    assertThat(stateExecutionData.getNewVirtualMachineScaleSetName()).isEqualTo("newVirtualMachineScaleSetName");
    assertThat(stateExecutionData.getNewVirtualMachineScaleSetId()).isEqualTo("newVirtualMachineScaleSetName-id");
    assertThat(stateExecutionData.getOldVirtualMachineScaleSetName()).isEqualTo("oldVirtualMachineScaleSetName");
    assertThat(stateExecutionData.getOldVirtualMachineScaleSetId()).isEqualTo("oldVirtualMachineScaleSetName-id");
    assertThat(stateExecutionData.getNewDesiredCount()).isEqualTo(1);
    assertThat(stateExecutionData.getOldDesiredCount()).isEqualTo(0);
    assertThat(stateExecutionData.getStepExecutionSummary()).isNotNull();
    assertThat(stateExecutionData.getExecutionDetails()).isNotEmpty();
    assertThat(stateExecutionData.getExecutionSummary()).isNotEmpty();

    AzureVMSSDeployExecutionSummary deployExecutionSummary = stateExecutionData.getStepExecutionSummary();
    assertThat(deployExecutionSummary.equals(AzureVMSSDeployExecutionSummary.builder().build())).isFalse();
    assertThat(deployExecutionSummary).isNotNull();
    assertThat(deployExecutionSummary.getNewVirtualMachineScaleSetName()).isEqualTo("newVirtualMachineScaleSetName");
    assertThat(deployExecutionSummary.getNewVirtualMachineScaleSetId()).isEqualTo("newVirtualMachineScaleSetName-id");
    assertThat(deployExecutionSummary.getOldVirtualMachineScaleSetName()).isEqualTo("oldVirtualMachineScaleSetName");
    assertThat(deployExecutionSummary.getOldVirtualMachineScaleSetId()).isEqualTo("oldVirtualMachineScaleSetName-id");
    assertThat(deployExecutionSummary.toString()).isNotNull();

    verify(stateExecutionService).appendDelegateTaskDetails(any(), any());
  }

  private AzureVMSSInfrastructureMapping getInfrastructureMapping() {
    return AzureVMSSInfrastructureMapping.builder()
        .baseVMSSName("baseVMSSName")
        .resourceGroupName("resourceGroupName")
        .subscriptionId("subscriptionId")
        .passwordSecretTextName("password")
        .userName("userName")
        .vmssAuthType(VMSSAuthType.PASSWORD)
        .vmssDeploymentType(VMSSDeploymentType.NATIVE_VMSS)
        .build();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteFailure() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doThrow(InvalidRequestException.class).when(context).getContextElement(eq(ContextElementType.AZURE_VMSS_SETUP));
    state.execute(context);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteWingsExceptionFailure() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doThrow(WingsException.class).when(context).getContextElement(eq(ContextElementType.AZURE_VMSS_SETUP));
    state.execute(context);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteSkip() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doReturn(null).when(context).getContextElement(eq(ContextElementType.AZURE_VMSS_SETUP));
    ExecutionResponse response = state.execute(context);
    assertThat(response.getExecutionStatus()).isEqualTo(SKIPPED);
    assertThat(response.getErrorMessage()).isEqualTo(state.getSkipMessage());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    reset(azureVMSSStateHelper);
    String newVirtualMachineScaleSetName = "newVirtualMachineScaleSetName";
    String oldVirtualMachineScaleSetName = "oldVirtualMachineScaleSetName";
    String activityId = "activityId";
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    List<AzureVMInstanceData> instancesAdded = new ArrayList<>();
    String addedInstanceId = "addedInstanceId";
    String existingInstanceId = "existingInstanceId";
    instancesAdded.add(AzureVMInstanceData.builder().instanceId(addedInstanceId).build());
    List<AzureVMInstanceData> instancesExisting = new ArrayList<>();
    instancesExisting.add(AzureVMInstanceData.builder().instanceId(existingInstanceId).build());
    SweepingOutputInstance sweepingOutputInstance =
        SweepingOutputInstance.builder().name("sweepingOutputInstanceName").build();
    doNothing().when(azureVMSSStateHelper).updateActivityStatus(any(), any(), any());
    doReturn(false).when(azureVMSSStateHelper).isBlueGreenWorkflow(context);
    doReturn(5).when(azureVMSSStateHelper).renderTimeoutExpressionOrGetDefault(any(), any(), anyInt());
    doReturn(SUCCESS).when(azureVMSSStateHelper).getExecutionStatus(any());
    doReturn(sweepingOutputInstance).when(sweepingOutputService).save(any());
    doReturn(getInfrastructureMapping()).when(azureVMSSStateHelper).getAzureVMSSInfrastructureMapping(any(), any());
    doNothing().when(azureVMSSStateHelper).saveInstanceInfoToSweepingOutput(any(), any());
    Map<String, ResponseData> responseMap = getDelegateResponse(instancesAdded, instancesExisting);
    AzureVMSSDeployStateExecutionData data = AzureVMSSDeployStateExecutionData.builder()
                                                 .newVirtualMachineScaleSetName(newVirtualMachineScaleSetName)
                                                 .oldVirtualMachineScaleSetName(oldVirtualMachineScaleSetName)
                                                 .newDesiredCount(1)
                                                 .oldDesiredCount(1)
                                                 .activityId(activityId)
                                                 .build();
    doReturn(data).when(context).getStateExecutionData();
    doReturn(Collections.singletonList(anInstanceElement()
                                           .uuid("vmss-id1")
                                           .hostName("hostName")
                                           .displayName("vmss-test")
                                           .host(HostElement.builder().build())
                                           .build()))
        .when(azureVMSSStateHelper)
        .generateInstanceElements(any(), any(), any());

    ExecutionResponse executionResponse = state.handleAsyncResponse(context, responseMap);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    List<ContextElement> notifyElements = executionResponse.getNotifyElements();
    assertThat(notifyElements).isNotNull();
    assertThat(notifyElements.size()).isEqualTo(1);
    ContextElement contextElement = notifyElements.get(0);
    assertThat(contextElement).isNotNull();
    assertThat(contextElement instanceof InstanceElementListParam).isTrue();
    InstanceElementListParam instanceElementListParam = (InstanceElementListParam) contextElement;
    assertThat(instanceElementListParam).isNotNull();

    AzureVMSSDeployStateExecutionData stateExecutionData =
        (AzureVMSSDeployStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(stateExecutionData.getNewInstanceStatusSummaries().size()).isEqualTo(1);
  }

  @NotNull
  private ImmutableMap<String, ResponseData> getDelegateResponse(
      List<AzureVMInstanceData> instancesAdded, List<AzureVMInstanceData> instancesExisting) {
    return ImmutableMap.of(ACTIVITY_ID,
        AzureVMSSTaskExecutionResponse.builder()
            .azureVMSSTaskResponse(AzureVMSSDeployTaskResponse.builder()
                                       .vmInstancesAdded(instancesAdded)
                                       .vmInstancesExisting(instancesExisting)
                                       .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseProcessingFailure() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doThrow(InvalidRequestException.class).when(context).getAppId();
    state.handleAsyncResponse(context, getDelegateResponse(Collections.emptyList(), Collections.emptyList()));
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseWingsExceptionFailure() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doThrow(WingsException.class).when(context).getAppId();
    state.handleAsyncResponse(context, getDelegateResponse(Collections.emptyList(), Collections.emptyList()));
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseFailure() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doReturn(ExecutionStatus.FAILED).when(azureVMSSStateHelper).getExecutionStatus(any());
    ExecutionResponse executionResponse =
        state.handleAsyncResponse(context, getDelegateResponse(Collections.emptyList(), Collections.emptyList()));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testValidateFields() {
    AzureVMSSDeployState azureDeployState = new AzureVMSSDeployState("Validate fields");
    assertThat(azureDeployState.validateFields().size()).isEqualTo(1);

    azureDeployState.setInstanceCount(2);
    assertThat(azureDeployState.validateFields().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetTotalExpectedCount() {
    AzureVMSSDeployState azureDeployState = new AzureVMSSDeployState("Validate expected count", AZURE_VMSS_DEPLOY);
    azureDeployState.setInstanceUnitType(InstanceUnitType.PERCENTAGE);
    azureDeployState.setInstanceCount(40);
    assertThat(azureDeployState.getTotalExpectedCount(5)).isEqualTo(2);
    assertThat(azureDeployState.getInstanceUnitType()).isEqualTo(InstanceUnitType.PERCENTAGE);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetCommandUnits() {
    AzureVMSSDeployState azureDeployState = new AzureVMSSDeployState("Test Command Units", AZURE_VMSS_DEPLOY);
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doReturn(OrchestrationWorkflowType.BLUE_GREEN).when(context).getOrchestrationWorkflowType();

    List<CommandUnit> commandUnits = azureDeployState.getCommandUnits(context, ResizeStrategy.RESIZE_NEW_FIRST);
    List<String> commandUnitsName = commandUnits.stream().map(CommandUnit::getName).collect(Collectors.toList());
    assertThat(commandUnitsName)
        .containsExactly(
            UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, DEPLOYMENT_STATUS, DEPLOYMENT_ERROR);
  }
}
