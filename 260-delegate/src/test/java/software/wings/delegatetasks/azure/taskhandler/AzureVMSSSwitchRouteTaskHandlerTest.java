/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.azure.request.AzureVMSSTaskParameters.AzureVMSSTaskType.AZURE_VMSS_SWITCH_ROUTE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureAutoScaleSettingsClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.client.AzureNetworkClient;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.concurent.HTimeLimiterMocker;
import io.harness.delegate.task.azure.AzureVMSSPreDeploymentData;
import io.harness.delegate.task.azure.request.AzureLoadBalancerDetailForBGDeployment;
import io.harness.delegate.task.azure.request.AzureVMSSSwitchRouteTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;

import com.google.common.util.concurrent.TimeLimiter;
import javax.inject.Inject;
import com.microsoft.azure.Page;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet.UpdateStages.WithApply;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet.UpdateStages.WithPrimaryLoadBalancer;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVMs;
import com.microsoft.azure.management.network.LoadBalancer;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.assertj.core.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import rx.Observable;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureVMSSSwitchRouteTaskHandlerTest extends WingsBaseTest {
  @Mock private AzureComputeClient azureComputeClient;
  @Mock private AzureNetworkClient azureNetworkClient;
  @Mock private AzureAutoScaleSettingsClient azureAutoScaleSettingsClient;
  @Mock private TimeLimiter mockTimeLimiter;

  @Inject @InjectMocks AzureVMSSSwitchRouteTaskHandler switchRouteTaskHandler;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalSwapRoutes() throws Exception {
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters = getAzureVMSSSwitchRouteTaskParameters();

    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse =
        switchRouteTaskHandler.executeTaskInternal(switchRouteTaskParameters, azureConfig);

    assertThat(azureVMSSTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalRollback() throws Exception {
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters = getAzureVMSSSwitchRouteTaskParameters();
    switchRouteTaskParameters.setRollback(true);

    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse =
        switchRouteTaskHandler.executeTaskInternal(switchRouteTaskParameters, azureConfig);

    assertThat(azureVMSSTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }

  public AzureVMSSSwitchRouteTaskParameters getAzureVMSSSwitchRouteTaskParameters() throws Exception {
    String loadBalancerName = "loadBalancerName";
    String prodBackendPool = "prodBackendPool";
    String stageBackendPool = "stageBackendPool";
    String oldVmssName = "oldVmssName";
    List<String> baseScalingPolicyJSON = Collections.emptyList();
    String accountId = "accountId";
    String appId = "appId";
    String activityId = "activityId";
    String commandName = "commandName";
    String newVMSSName = "newVMSSName";
    String oldVMSSName = "oldVMSSName";
    String resourceGroupName = "resourceGroupName";
    String subscriptionId = "subscriptionId";
    boolean isRollback = false;
    boolean isDownscaleOldVMSS = false;

    // newVirtualMachineScaleSet virtualMachines()
    VirtualMachineScaleSet newVirtualMachineScaleSet = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSetVM newVirtualMachineScaleSetVM = mock(VirtualMachineScaleSetVM.class);
    doReturn("1").when(newVirtualMachineScaleSetVM).instanceId();
    VirtualMachineScaleSetVMs newVirtualMachineScaleSetVMs = mock(VirtualMachineScaleSetVMs.class);
    PagedList<VirtualMachineScaleSetVM> pageList = getPageList();
    pageList.add(newVirtualMachineScaleSetVM);
    doReturn(newVirtualMachineScaleSetVMs).when(newVirtualMachineScaleSet).virtualMachines();
    doReturn("VM-1").when(newVirtualMachineScaleSetVM).name();
    doReturn(pageList).when(newVirtualMachineScaleSetVMs).list();
    doReturn(newVMSSName).when(newVirtualMachineScaleSet).name();

    // oldVirtualMachineScaleSet virtualMachines()
    VirtualMachineScaleSet oldVirtualMachineScaleSet = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSetVM oldVirtualMachineScaleSetVM = mock(VirtualMachineScaleSetVM.class);
    doReturn("/subscription/old-vm-is/-1").when(oldVirtualMachineScaleSetVM).id();
    VirtualMachineScaleSetVMs oldVirtualMachineScaleSetVMs = mock(VirtualMachineScaleSetVMs.class);
    PagedList<VirtualMachineScaleSetVM> oldPageList = getPageList();
    oldPageList.add(oldVirtualMachineScaleSetVM);
    doReturn(oldVirtualMachineScaleSetVMs).when(oldVirtualMachineScaleSet).virtualMachines();
    doReturn(pageList).when(oldVirtualMachineScaleSetVMs).list();
    doReturn(oldVMSSName).when(oldVirtualMachineScaleSet).name();

    // ExecutionLogCallback and TimeLimiter
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    LoadBalancer loadBalancer = mock(LoadBalancer.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any(), any());
    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenReturn(Boolean.TRUE);

    AzureConfig azureConfig = AzureConfig.builder().build();
    when(
        azureComputeClient.getVirtualMachineScaleSetByName(azureConfig, subscriptionId, resourceGroupName, newVMSSName))
        .thenReturn(Optional.of(newVirtualMachineScaleSet));
    when(
        azureComputeClient.getVirtualMachineScaleSetByName(azureConfig, subscriptionId, resourceGroupName, oldVMSSName))
        .thenReturn(Optional.of(oldVirtualMachineScaleSet));
    when(azureNetworkClient.getLoadBalancerByName(azureConfig, subscriptionId, resourceGroupName, loadBalancerName))
        .thenReturn(Optional.of(loadBalancer));

    // detachVMSSFromBackendPools()
    when(azureComputeClient.detachVMSSFromBackendPools(azureConfig, newVirtualMachineScaleSet, stageBackendPool))
        .thenReturn(newVirtualMachineScaleSet);
    when(azureComputeClient.detachVMSSFromBackendPools(azureConfig, newVirtualMachineScaleSet, prodBackendPool))
        .thenReturn(newVirtualMachineScaleSet);
    when(azureComputeClient.detachVMSSFromBackendPools(azureConfig, oldVirtualMachineScaleSet, prodBackendPool))
        .thenReturn(oldVirtualMachineScaleSet);

    // attachVMSSToBackendPools()
    when(azureComputeClient.attachVMSSToBackendPools(
             azureConfig, newVirtualMachineScaleSet, loadBalancer, prodBackendPool))
        .thenReturn(newVirtualMachineScaleSet);
    when(azureComputeClient.attachVMSSToBackendPools(
             azureConfig, oldVirtualMachineScaleSet, loadBalancer, prodBackendPool))
        .thenReturn(oldVirtualMachineScaleSet);

    when(azureComputeClient.checkIsRequiredNumberOfVMInstances(any(), anyString(), anyString(), anyInt()))
        .thenReturn(true);

    doNothing().when(azureComputeClient).updateVMInstances(any(), anyString());
    doNothing()
        .when(azureAutoScaleSettingsClient)
        .clearAutoScaleSettingOnTargetResourceId(any(), anyString(), anyString(), anyString());
    doNothing()
        .when(azureAutoScaleSettingsClient)
        .attachAutoScaleSettingToTargetResourceId(any(), anyString(), anyString(), anyString(), anyString());
    doNothing().when(azureComputeClient).deleteVirtualMachineScaleSetById(any(), anyString(), anyString());

    // newVirtualMachineScaleSet updateTags()
    WithPrimaryLoadBalancer newVMSSUpdater = mock(WithPrimaryLoadBalancer.class);
    WithApply newWithApply = mock(WithApply.class);
    when(newVirtualMachineScaleSet.update()).thenReturn(newVMSSUpdater);
    when(newVMSSUpdater.withTag(anyString(), anyString())).thenReturn(newWithApply);
    when(newVMSSUpdater.withCapacity(anyInt())).thenReturn(newWithApply);
    when(newWithApply.apply()).thenReturn(newVirtualMachineScaleSet);
    when(newWithApply.applyAsync()).thenReturn(Observable.from(Arrays.array(newVirtualMachineScaleSet)));

    // oldVirtualMachineScaleSet updateTags()
    WithPrimaryLoadBalancer oldVMSSUpdater = mock(WithPrimaryLoadBalancer.class);
    WithApply oldWithApply = mock(WithApply.class);
    when(oldVirtualMachineScaleSet.update()).thenReturn(oldVMSSUpdater);
    when(oldVMSSUpdater.withTag(anyString(), anyString())).thenReturn(oldWithApply);
    when(oldVMSSUpdater.withCapacity(anyInt())).thenReturn(oldWithApply);
    when(oldWithApply.apply()).thenReturn(oldVirtualMachineScaleSet);
    when(oldWithApply.applyAsync()).thenReturn(Observable.from(Arrays.array(oldVirtualMachineScaleSet)));

    AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail = AzureLoadBalancerDetailForBGDeployment.builder()
                                                                         .loadBalancerName(loadBalancerName)
                                                                         .prodBackendPool(prodBackendPool)
                                                                         .stageBackendPool(stageBackendPool)
                                                                         .build();

    AzureVMSSPreDeploymentData preDeploymentData = AzureVMSSPreDeploymentData.builder()
                                                       .desiredCapacity(1)
                                                       .minCapacity(0)
                                                       .oldVmssName(oldVmssName)
                                                       .scalingPolicyJSON(baseScalingPolicyJSON)
                                                       .build();

    return AzureVMSSSwitchRouteTaskParameters.builder()
        .accountId(accountId)
        .appId(appId)
        .activityId(activityId)
        .autoScalingSteadyStateVMSSTimeout(10)
        .azureLoadBalancerDetail(azureLoadBalancerDetail)
        .commandName(commandName)
        .downscaleOldVMSS(isDownscaleOldVMSS)
        .newVMSSName(newVMSSName)
        .oldVMSSName(oldVMSSName)
        .preDeploymentData(preDeploymentData)
        .resourceGroupName(resourceGroupName)
        .rollback(isRollback)
        .subscriptionId(subscriptionId)
        .commandType(AZURE_VMSS_SWITCH_ROUTE)
        .build();
  }

  @NotNull
  public PagedList<VirtualMachineScaleSetVM> getPageList() {
    return new PagedList<VirtualMachineScaleSetVM>() {
      @Override
      public Page<VirtualMachineScaleSetVM> nextPage(String s) {
        return new Page<VirtualMachineScaleSetVM>() {
          @Override
          public String nextPageLink() {
            return null;
          }
          @Override
          public List<VirtualMachineScaleSetVM> items() {
            return null;
          }
        };
      }
    };
  }
}
