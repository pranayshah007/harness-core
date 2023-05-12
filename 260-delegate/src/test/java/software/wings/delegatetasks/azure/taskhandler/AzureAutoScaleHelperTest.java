/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.azure.model.AzureConstants.DEFAULT_AZURE_VMSS_DESIRED_INSTANCES;
import static io.harness.azure.model.AzureConstants.DEFAULT_AZURE_VMSS_MAX_INSTANCES;
import static io.harness.azure.model.AzureConstants.DEFAULT_AZURE_VMSS_MIN_INSTANCES;
import static io.harness.azure.model.AzureConstants.SETUP_COMMAND_UNIT;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.JELENA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureAutoScaleSettingsClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureVMSSAutoScaleSettingsData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.request.AzureVMSSSetupTaskParameters;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;

import com.azure.resourcemanager.compute.models.VirtualMachineScaleSet;
import com.azure.resourcemanager.monitor.models.AutoscaleProfile;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureAutoScaleHelperTest extends WingsBaseTest {
  @Mock private DelegateLogService mockDelegateLogService;
  @Mock private AzureComputeClient mockAzureComputeClient;
  @Mock private AzureAutoScaleSettingsClient mockAzureAutoScaleSettingsClient;

  @Spy @InjectMocks AzureAutoScaleHelper azureAutoScaleHelper;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetVMSSAutoScaleInstanceLimitsWorkflowInput() throws Exception {
    mockExecutionLogCallbackMethods();
    AzureConfig azureConfig = AzureConfig.builder().build();
    VirtualMachineScaleSet mostRecentActiveVMSS = mock(VirtualMachineScaleSet.class);
    int minInstances = 0;
    int maxInstances = 2;
    int desiredInstances = 1;
    AzureVMSSSetupTaskParameters setupTaskParameters = AzureVMSSSetupTaskParameters.builder()
                                                           .minInstances(minInstances)
                                                           .maxInstances(maxInstances)
                                                           .desiredInstances(desiredInstances)
                                                           .build();

    // logic, use instance limits from setup task params
    boolean isUseCurrentRunningCount = false;

    AzureVMSSAutoScaleSettingsData response = azureAutoScaleHelper.getVMSSAutoScaleInstanceLimits(
        azureConfig, setupTaskParameters, mostRecentActiveVMSS, isUseCurrentRunningCount, SETUP_COMMAND_UNIT);

    assertThat(response).isNotNull();
    assertThat(response.getMinInstances()).isEqualTo(minInstances);
    assertThat(response.getMaxInstances()).isEqualTo(maxInstances);
    assertThat(response.getDesiredInstances()).isEqualTo(desiredInstances);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetVMSSAutoScaleInstanceLimitsFromMostRecentActiveVMSS() throws Exception {
    mockExecutionLogCallbackMethods();
    AzureConfig azureConfig = AzureConfig.builder().build();
    String mostRecentActiveVMSSId = "id";
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String mostRecentActiveVMSSName = "mostRecentActiveVMSSName";
    int mostRecentActiveVMSSCapacity = 5;
    int minInstances = 0;
    int maxInstances = 2;
    int desiredInstances = 1;
    AzureVMSSSetupTaskParameters setupTaskParameters = AzureVMSSSetupTaskParameters.builder()
                                                           .subscriptionId(subscriptionId)
                                                           .resourceGroupName(resourceGroupName)
                                                           .build();
    // logic, use instance limits from most recent VMSS and most recent VMSS is not null
    boolean isUseCurrentRunningCount = true;
    VirtualMachineScaleSet mostRecentActiveVMSS = mock(VirtualMachineScaleSet.class);
    doReturn(mostRecentActiveVMSSId).when(mostRecentActiveVMSS).id();
    doReturn(resourceGroupName).when(mostRecentActiveVMSS).resourceGroupName();
    doReturn(mostRecentActiveVMSSName).when(mostRecentActiveVMSS).name();
    doReturn(mostRecentActiveVMSSCapacity).when(mostRecentActiveVMSS).capacity();
    mockAutoscaleProfile(azureConfig, mostRecentActiveVMSSId, subscriptionId, resourceGroupName, minInstances,
        maxInstances, desiredInstances);

    AzureVMSSAutoScaleSettingsData response = azureAutoScaleHelper.getVMSSAutoScaleInstanceLimits(
        azureConfig, setupTaskParameters, mostRecentActiveVMSS, isUseCurrentRunningCount, SETUP_COMMAND_UNIT);

    assertThat(response).isNotNull();
    assertThat(response.getMinInstances()).isEqualTo(minInstances);
    assertThat(response.getMaxInstances()).isEqualTo(maxInstances);
    assertThat(response.getDesiredInstances()).isEqualTo(desiredInstances);
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testGetVMSSAutoScaleInstanceLimitsFromMostRecentActiveVMSSWithManualScaling() throws Exception {
    mockExecutionLogCallbackMethods();
    AzureConfig azureConfig = AzureConfig.builder().build();
    String mostRecentActiveVMSSId = "id";
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String mostRecentActiveVMSSName = "mostRecentActiveVMSSName";
    int mostRecentActiveVMSSCapacity = 2;
    AzureVMSSSetupTaskParameters setupTaskParameters = AzureVMSSSetupTaskParameters.builder()
                                                           .subscriptionId(subscriptionId)
                                                           .resourceGroupName(resourceGroupName)
                                                           .build();
    // when there's no default autoscaling profile for most recent VMSS (manual scaling), take capacity as instance
    // limit
    boolean isUseCurrentRunningCount = true;

    VirtualMachineScaleSet mostRecentActiveVMSS = mock(VirtualMachineScaleSet.class);
    doReturn(mostRecentActiveVMSSId).when(mostRecentActiveVMSS).id();
    doReturn(resourceGroupName).when(mostRecentActiveVMSS).resourceGroupName();
    doReturn(mostRecentActiveVMSSName).when(mostRecentActiveVMSS).name();
    doReturn(mostRecentActiveVMSSCapacity).when(mostRecentActiveVMSS).capacity();
    doReturn(Optional.empty())
        .when(mockAzureAutoScaleSettingsClient)
        .getDefaultAutoScaleProfile(azureConfig, subscriptionId, resourceGroupName, mostRecentActiveVMSSId);

    AzureVMSSAutoScaleSettingsData response = azureAutoScaleHelper.getVMSSAutoScaleInstanceLimits(
        azureConfig, setupTaskParameters, mostRecentActiveVMSS, isUseCurrentRunningCount, SETUP_COMMAND_UNIT);

    assertThat(response).isNotNull();
    assertThat(response.getMinInstances()).isEqualTo(mostRecentActiveVMSSCapacity);
    assertThat(response.getMaxInstances()).isEqualTo(mostRecentActiveVMSSCapacity);
    assertThat(response.getDesiredInstances()).isEqualTo(mostRecentActiveVMSSCapacity);
  }

  private void mockAutoscaleProfile(AzureConfig azureConfig, String targetResourceId, String subscriptionId,
      String resourceGroupName, int minInstances, int maxInstances, int desiredInstances) {
    AutoscaleProfile autoscaleProfile = mock(AutoscaleProfile.class);
    doReturn(minInstances).when(autoscaleProfile).minInstanceCount();
    doReturn(maxInstances).when(autoscaleProfile).maxInstanceCount();
    doReturn(desiredInstances).when(autoscaleProfile).defaultInstanceCount();
    doReturn(Optional.of(autoscaleProfile))
        .when(mockAzureAutoScaleSettingsClient)
        .getDefaultAutoScaleProfile(azureConfig, subscriptionId, resourceGroupName, targetResourceId);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetVMSSAutoScaleInstanceLimitsDefault() throws Exception {
    mockExecutionLogCallbackMethods();
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureVMSSSetupTaskParameters setupTaskParameters = AzureVMSSSetupTaskParameters.builder().build();

    // logic, use instance limits from most recent VMSS and most recent VMSS is null
    boolean isUseCurrentRunningCount = true;
    VirtualMachineScaleSet mostRecentActiveVMSS = null;

    AzureVMSSAutoScaleSettingsData response = azureAutoScaleHelper.getVMSSAutoScaleInstanceLimits(
        azureConfig, setupTaskParameters, mostRecentActiveVMSS, isUseCurrentRunningCount, SETUP_COMMAND_UNIT);

    assertThat(response).isNotNull();
    assertThat(response.getMinInstances()).isEqualTo(DEFAULT_AZURE_VMSS_MIN_INSTANCES);
    assertThat(response.getMaxInstances()).isEqualTo(DEFAULT_AZURE_VMSS_MAX_INSTANCES);
    assertThat(response.getDesiredInstances()).isEqualTo(DEFAULT_AZURE_VMSS_DESIRED_INSTANCES);
  }

  private void mockExecutionLogCallbackMethods() throws Exception {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetVMSSAutoScaleSettingsJSONs() {
    String mostRecentActiveVMSSId = "id";
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    AzureConfig azureConfig = AzureConfig.builder().build();
    VirtualMachineScaleSet virtualMachineScaleSet = mock(VirtualMachineScaleSet.class);
    doReturn(resourceGroupName).when(virtualMachineScaleSet).resourceGroupName();
    doReturn(mostRecentActiveVMSSId).when(virtualMachineScaleSet).id();

    String autoScaleSettings = "autoScaleSetting:{...}";
    doReturn(Optional.of(autoScaleSettings))
        .when(mockAzureAutoScaleSettingsClient)
        .getAutoScaleSettingJSONByTargetResourceId(
            azureConfig, subscriptionId, resourceGroupName, mostRecentActiveVMSSId);

    List<String> response =
        azureAutoScaleHelper.getVMSSAutoScaleSettingsJSONs(azureConfig, subscriptionId, virtualMachineScaleSet);

    assertThat(response).isNotNull();
    assertThat(response.get(0)).isNotNull();
    assertThat(response.get(0)).isEqualTo(autoScaleSettings);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetVMSSAutoScaleSettingsJSONsWithVMSSNull() {
    AzureConfig azureConfig = AzureConfig.builder().build();
    String subscriptionId = "subscriptionId";
    VirtualMachineScaleSet mostRecentActiveVMSS = null;

    List<String> response =
        azureAutoScaleHelper.getVMSSAutoScaleSettingsJSONs(azureConfig, subscriptionId, mostRecentActiveVMSS);

    assertThat(response).isNotNull();
    assertThat(response.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetVMSSAutoScaleSettingsJSONsByVMSSName() {
    String mostRecentActiveVMSSId = "id";
    String resourceGroupName = "resourceGroupName";
    String subscriptionId = "subscriptionId";
    String virtualMachineScaleSetName = "virtualMachineScaleSetName";
    AzureConfig azureConfig = AzureConfig.builder().build();

    VirtualMachineScaleSet virtualMachineScaleSet = mock(VirtualMachineScaleSet.class);
    doReturn(mostRecentActiveVMSSId).when(virtualMachineScaleSet).id();
    doReturn(Optional.of(virtualMachineScaleSet))
        .when(mockAzureComputeClient)
        .getVirtualMachineScaleSetByName(azureConfig, subscriptionId, resourceGroupName, virtualMachineScaleSetName);

    String autoScaleSettings = "autoScaleSetting:{...}";
    doReturn(Optional.of(autoScaleSettings))
        .when(mockAzureAutoScaleSettingsClient)
        .getAutoScaleSettingJSONByTargetResourceId(
            azureConfig, subscriptionId, resourceGroupName, mostRecentActiveVMSSId);

    List<String> response = azureAutoScaleHelper.getVMSSAutoScaleSettingsJSONs(
        azureConfig, subscriptionId, resourceGroupName, virtualMachineScaleSetName);

    assertThat(response).isNotNull();
    assertThat(response.get(0)).isNotNull();
    assertThat(response.get(0)).isEqualTo(autoScaleSettings);
  }
}
