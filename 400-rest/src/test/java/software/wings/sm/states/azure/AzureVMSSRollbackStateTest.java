/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.rule.OwnerRule.ANIL;

import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.SweepingOutputInstanceBuilder;
import io.harness.category.element.UnitTests;
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
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.azure.manager.AzureVMSSAllPhaseRollbackData;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry.SweepingOutputInquiryBuilder;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.ManagerExecutionLogCallback;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class AzureVMSSRollbackStateTest extends WingsBaseTest {
  @Mock private DelegateService delegateService;
  @Mock private AzureVMSSStateHelper azureVMSSStateHelper;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @Spy @InjectMocks AzureVMSSRollbackState rollbackState = new AzureVMSSRollbackState("Azure VMSS Rollback State");

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteSuccess() {
    ExecutionContextImpl context = mockSetup();
    rollbackState.setInstanceCount(2);
    rollbackState.setInstanceUnitType(InstanceUnitType.COUNT);
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());

    ExecutionResponse executionResponse = rollbackState.execute(context);

    assertThat(rollbackState.validateFields()).isEmpty();
    assertThat(rollbackState.getTimeoutMillis()).isNull();
    assertThat(rollbackState.getInstanceCount()).isEqualTo(2);
    assertThat(rollbackState.getInstanceUnitType()).isEqualTo(InstanceUnitType.COUNT);
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getErrorMessage()).isNull();
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
    assertThat(executionResponse.getStateExecutionData()).isInstanceOf(AzureVMSSDeployStateExecutionData.class);
    assertThat(((AzureVMSSDeployStateExecutionData) executionResponse.getStateExecutionData()).getActivityId())
        .isEqualTo("activityId");
    verify(stateExecutionService).appendDelegateTaskDetails(any(), any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteSkip() {
    reset(sweepingOutputService);

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    AzureVMSSAllPhaseRollbackData rollbackData =
        AzureVMSSAllPhaseRollbackData.builder().allPhaseRollbackDone(false).build();
    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder = SweepingOutputInquiry.builder();

    when(sweepingOutputService.findSweepingOutput(any())).thenReturn(rollbackData);
    when(context.prepareSweepingOutputInquiryBuilder()).thenReturn(sweepingOutputInquiryBuilder);

    ExecutionResponse executionResponse = rollbackState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteFailure() {
    reset(sweepingOutputService);
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder = SweepingOutputInquiry.builder();
    when(context.prepareSweepingOutputInquiryBuilder()).thenReturn(sweepingOutputInquiryBuilder);

    doAnswer(invocation -> { throw new Exception(); }).when(sweepingOutputService).findSweepingOutput(any());
    assertThatThrownBy(() -> rollbackState.execute(context)).isInstanceOf(InvalidRequestException.class);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteWingsExceptionFailure() {
    reset(sweepingOutputService);
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder = SweepingOutputInquiry.builder();
    when(context.prepareSweepingOutputInquiryBuilder()).thenReturn(sweepingOutputInquiryBuilder);

    doAnswer(invocation -> { throw new WingsException(""); }).when(sweepingOutputService).findSweepingOutput(any());
    rollbackState.execute(context);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteRollbackPhaseDoneSuccess() {
    reset(sweepingOutputService);
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    AzureVMSSAllPhaseRollbackData rollbackData =
        AzureVMSSAllPhaseRollbackData.builder().allPhaseRollbackDone(true).build();
    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder = SweepingOutputInquiry.builder();

    when(sweepingOutputService.findSweepingOutput(any())).thenReturn(rollbackData);
    when(context.prepareSweepingOutputInquiryBuilder()).thenReturn(sweepingOutputInquiryBuilder);

    ExecutionResponse executionResponse = rollbackState.execute(context);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    verify(rollbackState, never()).executeInternal(eq(context));
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    reset(sweepingOutputService);
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    SweepingOutputInstanceBuilder outputInstanceBuilder = SweepingOutputInstance.builder();
    Map<String, ResponseData> responseMap = ImmutableMap.of(ACTIVITY_ID,
        AzureVMSSTaskExecutionResponse.builder()
            .azureVMSSTaskResponse(AzureVMSSDeployTaskResponse.builder()
                                       .vmInstancesAdded(Collections.emptyList())
                                       .vmInstancesExisting(Collections.emptyList())
                                       .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build());

    doReturn(ExecutionResponse.builder().build()).when(rollbackState).handleAsyncInternal(context, responseMap);
    when(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)).thenReturn(outputInstanceBuilder);

    rollbackState.handleAsyncResponse(context, responseMap);
    verify(rollbackState, times(1)).markAllPhaseRollbackDone(context);

    doAnswer(invocation -> { throw new Exception(); }).when(sweepingOutputService).save(any());
    assertThatThrownBy(() -> rollbackState.handleAsyncResponse(context, responseMap))
        .isInstanceOf(InvalidRequestException.class);

    doAnswer(invocation -> { throw new WingsException(""); }).when(sweepingOutputService).save(any());
    assertThatThrownBy(() -> rollbackState.handleAsyncResponse(context, responseMap))
        .isInstanceOf(WingsException.class);
  }

  @NotNull
  private ExecutionContextImpl mockSetup() {
    reset(sweepingOutputService);
    reset(azureVMSSStateHelper);
    rollbackState.setInstanceCount(1);
    rollbackState.setInstanceUnitType(InstanceUnitType.COUNT);

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    ManagerExecutionLogCallback managerExecutionLogCallback = mock(ManagerExecutionLogCallback.class);
    Application app = Application.Builder.anApplication().uuid("appId").build();
    Environment env = Environment.Builder.anEnvironment().uuid("envId").build();
    Service service = Service.builder().uuid("serviceId").build();
    Activity activity = Activity.builder().uuid("activityId").build();
    InfrastructureMapping azureVMSSInfrastructureMapping = AzureVMSSInfrastructureMapping.builder()
                                                               .baseVMSSName("baseVMSSName")
                                                               .resourceGroupName("resourceGroupName")
                                                               .subscriptionId("subscriptionId")
                                                               .passwordSecretTextName("password")
                                                               .userName("userName")
                                                               .vmssAuthType(VMSSAuthType.PASSWORD)
                                                               .vmssDeploymentType(VMSSDeploymentType.NATIVE_VMSS)
                                                               .build();
    AzureConfig azureConfig = AzureConfig.builder().build();
    Artifact artifact = Artifact.Builder.anArtifact().build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    AzureVMSSSetupContextElement azureVMSSSetupContextElement =
        AzureVMSSSetupContextElement.builder()
            .autoScalingSteadyStateVMSSTimeout(10)
            .desiredInstances(1)
            .isBlueGreen(false)
            .maxInstances(1)
            .minInstances(1)
            .oldDesiredCount(1)
            .newVirtualMachineScaleSetName("newVirtualMachineScaleSetName")
            .oldVirtualMachineScaleSetName("oldVirtualMachineScaleSetName")
            .build();

    AzureVMSSAllPhaseRollbackData rollbackData =
        AzureVMSSAllPhaseRollbackData.builder().allPhaseRollbackDone(false).build();
    SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder = SweepingOutputInquiry.builder();

    when(sweepingOutputService.findSweepingOutput(any())).thenReturn(rollbackData);
    when(context.prepareSweepingOutputInquiryBuilder()).thenReturn(sweepingOutputInquiryBuilder);

    doReturn(azureVMSSSetupContextElement).when(context).getContextElement(any());
    doReturn(app).when(azureVMSSStateHelper).getApplication(context);
    doReturn(env).when(azureVMSSStateHelper).getEnvironment(context);
    doReturn(service).when(azureVMSSStateHelper).getServiceByAppId(any(), anyString());
    doReturn(activity)
        .when(azureVMSSStateHelper)
        .createAndSaveActivity(any(), any(), anyString(), anyString(), any(), anyList());
    doReturn(managerExecutionLogCallback).when(azureVMSSStateHelper).getExecutionLogCallback(activity);
    doReturn("infraMappingId").when(context).fetchInfraMappingId();
    doReturn(azureVMSSInfrastructureMapping).when(infrastructureMappingService).get("infraMappingId", "appId");
    doReturn(10).when(azureVMSSStateHelper).renderTimeoutExpressionOrGetDefault(anyString(), any(), anyInt());
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
    doReturn(false).when(azureVMSSStateHelper).isBlueGreenWorkflow(context);
    doReturn("userData").when(azureVMSSStateHelper).getBase64EncodedUserData(context, "appId", "serviceId");
    doReturn("namePrefix")
        .when(azureVMSSStateHelper)
        .fixNamePrefix(any(), anyString(), anyString(), anyString(), anyString());
    doReturn(2).when(azureVMSSStateHelper).renderExpressionOrGetDefault(anyString(), any(), anyInt());
    doReturn("Done").when(delegateService).queueTaskV2(any());
    return context;
  }
}
