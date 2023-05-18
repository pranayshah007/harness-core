/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.VM_PROVISIONING_SUCCEEDED_STATUS;
import static io.harness.delegate.task.azure.request.AzureVMSSTaskParameters.AzureVMSSTaskType.AZURE_VMSS_SWITCH_ROUTE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.resourcemanager.compute.models.InstanceViewStatus;
import com.azure.resourcemanager.compute.models.VirtualMachineInstanceView;
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSet;
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSetVM;
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSetVMs;
import com.azure.resourcemanager.network.models.LoadBalancer;
import com.azure.resourcemanager.resources.fluentcore.utils.PagedConverter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import reactor.core.publisher.Mono;

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

    List<VirtualMachineScaleSetVM> responseList = new ArrayList<>();
    Response simpleResponse = new SimpleResponse(null, 200, null, responseList);
    doReturn(newVirtualMachineScaleSetVMs).when(newVirtualMachineScaleSet).virtualMachines();
    doReturn("VM-1").when(newVirtualMachineScaleSetVM).name();
    doReturn(getPagedIterable(simpleResponse)).when(newVirtualMachineScaleSetVMs).list();
    doReturn(newVMSSName).when(newVirtualMachineScaleSet).name();

    // oldVirtualMachineScaleSet virtualMachines()
    VirtualMachineScaleSet oldVirtualMachineScaleSet = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSetVM oldVirtualMachineScaleSetVM = mock(VirtualMachineScaleSetVM.class);
    doReturn("old1").when(oldVirtualMachineScaleSetVM).instanceId();

    VirtualMachineInstanceView mockOldVirtualMachineInstanceView = mock(VirtualMachineInstanceView.class);
    doReturn(mockOldVirtualMachineInstanceView).when(oldVirtualMachineScaleSetVM).instanceView();
    List<InstanceViewStatus> mockOldStatuses = new ArrayList<>();
    InstanceViewStatus mockOldInstanceViewStatus = mock(InstanceViewStatus.class);
    doReturn(VM_PROVISIONING_SUCCEEDED_STATUS).when(mockOldInstanceViewStatus).displayStatus();
    mockOldStatuses.add(mockOldInstanceViewStatus);
    doReturn(mockOldStatuses).when(mockOldVirtualMachineInstanceView).statuses();

    doReturn("/subscription/old-vm-is/-1").when(oldVirtualMachineScaleSetVM).id();
    VirtualMachineScaleSetVMs oldVirtualMachineScaleSetVMs = mock(VirtualMachineScaleSetVMs.class);
    List<VirtualMachineScaleSetVM> oldPageList = new ArrayList<>();
    oldPageList.add(oldVirtualMachineScaleSetVM);
    Response simpleResponse2 = new SimpleResponse(null, 200, null, oldPageList);
    doReturn(oldVirtualMachineScaleSetVMs).when(oldVirtualMachineScaleSet).virtualMachines();
    doReturn(getPagedIterable(simpleResponse2)).when(oldVirtualMachineScaleSetVMs).list();
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
    VirtualMachineScaleSet.UpdateStages.WithPrimaryLoadBalancer newVMSSUpdater =
        mock(VirtualMachineScaleSet.UpdateStages.WithPrimaryLoadBalancer.class);
    VirtualMachineScaleSet.UpdateStages.WithApply newWithApply =
        mock(VirtualMachineScaleSet.UpdateStages.WithApply.class);
    VirtualMachineScaleSet.UpdateStages.WithCapacity newWithCapacity =
        mock(VirtualMachineScaleSet.UpdateStages.WithCapacity.class);
    when(newVirtualMachineScaleSet.update()).thenReturn(newVMSSUpdater);
    when(newVMSSUpdater.withTag(anyString(), anyString())).thenReturn(newWithApply);
    when(newVMSSUpdater.withCapacity(any(Long.class))).thenReturn(newWithApply);
    when(newVMSSUpdater.withCapacity(any(Integer.class))).thenReturn(newWithApply);
    when(newVMSSUpdater.withCapacity(any(Short.class))).thenReturn(newWithApply);
    when(newVMSSUpdater.withCapacity(any(Byte.class))).thenReturn(newWithApply);

    Mono<VirtualMachineScaleSet> newVMSSResponseMono = mock(Mono.class);
    doReturn(newVMSSResponseMono).when(newVMSSResponseMono).doOnError(any());
    doReturn(newVMSSResponseMono).when(newVMSSResponseMono).doOnSuccess(any());
    doReturn(newVirtualMachineScaleSet).when(newVMSSResponseMono).block(any());
    doReturn(newVMSSResponseMono).when(newWithApply).applyAsync();
    doReturn(newVirtualMachineScaleSet).when(newWithApply).apply();

    // oldVirtualMachineScaleSet updateTags()
    VirtualMachineScaleSet.UpdateStages.WithPrimaryLoadBalancer oldVMSSUpdater =
        mock(VirtualMachineScaleSet.UpdateStages.WithPrimaryLoadBalancer.class);
    VirtualMachineScaleSet.UpdateStages.WithApply oldWithApply =
        mock(VirtualMachineScaleSet.UpdateStages.WithApply.class);
    when(oldVirtualMachineScaleSet.update()).thenReturn(oldVMSSUpdater);
    when(oldVMSSUpdater.withTag(anyString(), anyString())).thenReturn(oldWithApply);
    when(oldVMSSUpdater.withCapacity(any(Long.class))).thenReturn(oldWithApply);
    when(oldVMSSUpdater.withCapacity(any(Integer.class))).thenReturn(oldWithApply);
    when(oldVMSSUpdater.withCapacity(any(Short.class))).thenReturn(oldWithApply);
    when(oldVMSSUpdater.withCapacity(any(Byte.class))).thenReturn(oldWithApply);

    Mono<VirtualMachineScaleSet> oldVMSSResponseMono = mock(Mono.class);
    doReturn(oldVMSSResponseMono).when(oldVMSSResponseMono).doOnError(any());
    doReturn(oldVMSSResponseMono).when(oldVMSSResponseMono).doOnSuccess(any());
    doReturn(oldVirtualMachineScaleSet).when(oldVMSSResponseMono).block(any());
    doReturn(oldVMSSResponseMono).when(oldWithApply).applyAsync();
    doReturn(oldVirtualMachineScaleSet).when(oldWithApply).apply();

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
  public <T> PagedIterable<T> getPagedIterable(Response<List<T>> response) {
    return new PagedIterable<T>(PagedConverter.convertListToPagedFlux(Mono.just(response)));
  }
}
