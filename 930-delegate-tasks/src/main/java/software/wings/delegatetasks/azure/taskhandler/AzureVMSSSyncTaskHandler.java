/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.azure.model.AzureConstants.VM_POWER_STATE_PREFIX;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureVMData;
import io.harness.azure.model.SubscriptionData;
import io.harness.azure.model.VirtualMachineScaleSetData;
import io.harness.delegate.task.azure.request.AzureVMSSGetVirtualMachineScaleSetParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListLoadBalancerBackendPoolsNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListLoadBalancersNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListResourceGroupsNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListVMDataParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListVirtualMachineScaleSetsParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSGetVirtualMachineScaleSetResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListLoadBalancerBackendPoolsNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListLoadBalancersNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListResourceGroupsNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListSubscriptionsResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListVMDataResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListVirtualMachineScaleSetsResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskResponse;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import com.microsoft.azure.management.compute.PowerState;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import com.microsoft.azure.management.network.LoadBalancer;
import com.microsoft.azure.management.network.LoadBalancerBackend;
import com.microsoft.azure.management.network.PublicIPAddressDnsSettings;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.management.resources.fluentcore.arm.models.HasName;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureVMSSSyncTaskHandler extends AzureVMSSTaskHandler {
  @Override
  protected AzureVMSSTaskExecutionResponse executeTaskInternal(
      AzureVMSSTaskParameters azureVMSSTaskParameters, AzureConfig azureConfig) {
    if (!azureVMSSTaskParameters.isSyncTask()) {
      throw new InvalidRequestException(format("Unrecognized object of class: [%s] while executing sync task",
          azureVMSSTaskParameters.getCommandType().name()));
    }

    AzureVMSSTaskResponse azureVMSSTaskResponse;
    switch (azureVMSSTaskParameters.getCommandType()) {
      case AZURE_VMSS_LIST_SUBSCRIPTIONS: {
        List<Subscription> subscriptions = azureComputeClient.listSubscriptions(azureConfig);
        List<SubscriptionData> subscriptionsData =
            subscriptions.stream().map(toSubscriptionData()).collect(Collectors.toList());

        azureVMSSTaskResponse = AzureVMSSListSubscriptionsResponse.builder().subscriptions(subscriptionsData).build();
        break;
      }
      case AZURE_VMSS_LIST_RESOURCE_GROUPS_NAMES: {
        String subscriptionId =
            ((AzureVMSSListResourceGroupsNamesParameters) azureVMSSTaskParameters).getSubscriptionId();

        List<String> resourceGroupsNames =
            azureComputeClient.listResourceGroupsNamesBySubscriptionId(azureConfig, subscriptionId);

        azureVMSSTaskResponse =
            AzureVMSSListResourceGroupsNamesResponse.builder().resourceGroupsNames(resourceGroupsNames).build();
        break;
      }
      case AZURE_VMSS_LIST_VIRTUAL_MACHINE_SCALE_SETS: {
        String subscriptionId =
            ((AzureVMSSListVirtualMachineScaleSetsParameters) azureVMSSTaskParameters).getSubscriptionId();
        String resourceGroupName =
            ((AzureVMSSListVirtualMachineScaleSetsParameters) azureVMSSTaskParameters).getResourceGroupName();

        List<VirtualMachineScaleSet> virtualMachineScaleSets =
            azureComputeClient.listVirtualMachineScaleSetsByResourceGroupName(
                azureConfig, subscriptionId, resourceGroupName);
        List<VirtualMachineScaleSetData> virtualMachineScaleSetsList =
            virtualMachineScaleSets.stream().map(toVirtualMachineScaleSetData()).collect(Collectors.toList());

        azureVMSSTaskResponse = AzureVMSSListVirtualMachineScaleSetsResponse.builder()
                                    .virtualMachineScaleSets(virtualMachineScaleSetsList)
                                    .build();
        break;
      }
      case AZURE_VMSS_GET_VIRTUAL_MACHINE_SCALE_SET: {
        String subscriptionId =
            ((AzureVMSSGetVirtualMachineScaleSetParameters) azureVMSSTaskParameters).getSubscriptionId();
        String resourceGroupName =
            ((AzureVMSSGetVirtualMachineScaleSetParameters) azureVMSSTaskParameters).getResourceGroupName();
        String virtualMachineScaleSetName =
            ((AzureVMSSGetVirtualMachineScaleSetParameters) azureVMSSTaskParameters).getVmssName();

        Optional<VirtualMachineScaleSet> virtualMachineScaleSetOp = azureComputeClient.getVirtualMachineScaleSetByName(
            azureConfig, subscriptionId, resourceGroupName, virtualMachineScaleSetName);

        if (!virtualMachineScaleSetOp.isPresent()) {
          throw new InvalidRequestException(
              format("There is no Virtual Machine Scale Set with name %s, subscriptionId: %s, resourceGroupName: %s",
                  virtualMachineScaleSetName, subscriptionId, resourceGroupName));
        }

        VirtualMachineScaleSet virtualMachineScaleSet = virtualMachineScaleSetOp.get();

        String administratorUsername = virtualMachineScaleSet.virtualMachines()
                                           .list()
                                           .stream()
                                           .findFirst()
                                           .map(VirtualMachineScaleSetVM::administratorUserName)
                                           .orElse(EMPTY);

        VirtualMachineScaleSetData virtualMachineScaleSetData =
            VirtualMachineScaleSetData.builder()
                .id(virtualMachineScaleSet.id())
                .name(virtualMachineScaleSet.name())
                .virtualMachineAdministratorUsername(administratorUsername)
                .build();

        azureVMSSTaskResponse = AzureVMSSGetVirtualMachineScaleSetResponse.builder()
                                    .virtualMachineScaleSet(virtualMachineScaleSetData)
                                    .build();
        break;
      }
      case AZURE_VMSS_LIST_LOAD_BALANCERS_NAMES: {
        String subscriptionId =
            ((AzureVMSSListLoadBalancersNamesParameters) azureVMSSTaskParameters).getSubscriptionId();
        String resourceGroupName =
            ((AzureVMSSListLoadBalancersNamesParameters) azureVMSSTaskParameters).getResourceGroupName();

        List<LoadBalancer> loadBalancers =
            azureNetworkClient.listLoadBalancersByResourceGroup(azureConfig, subscriptionId, resourceGroupName);

        List<String> loadBalancersNames = loadBalancers.stream().map(HasName::name).collect(Collectors.toList());

        azureVMSSTaskResponse =
            AzureVMSSListLoadBalancersNamesResponse.builder().loadBalancersNames(loadBalancersNames).build();
        break;
      }
      case AZURE_VMSS_LIST_LOAD_BALANCER_BACKEND_POOLS_NAMES: {
        String subscriptionId =
            ((AzureVMSSListLoadBalancerBackendPoolsNamesParameters) azureVMSSTaskParameters).getSubscriptionId();
        String resourceGroupName =
            ((AzureVMSSListLoadBalancerBackendPoolsNamesParameters) azureVMSSTaskParameters).getResourceGroupName();
        String loadBalancerName =
            ((AzureVMSSListLoadBalancerBackendPoolsNamesParameters) azureVMSSTaskParameters).getLoadBalancerName();

        List<LoadBalancerBackend> loadBalancerBackendPools = azureNetworkClient.listLoadBalancerBackendPools(
            azureConfig, subscriptionId, resourceGroupName, loadBalancerName);

        List<String> loadBalancerBackendPoolsNames =
            loadBalancerBackendPools.stream().map(HasName::name).collect(Collectors.toList());

        azureVMSSTaskResponse = AzureVMSSListLoadBalancerBackendPoolsNamesResponse.builder()
                                    .loadBalancerBackendPoolsNames(loadBalancerBackendPoolsNames)
                                    .build();
        break;
      }
      case AZURE_VMSS_LIST_VM_DATA: {
        String subscriptionId = ((AzureVMSSListVMDataParameters) azureVMSSTaskParameters).getSubscriptionId();
        String vmssId = ((AzureVMSSListVMDataParameters) azureVMSSTaskParameters).getVmssId();

        List<VirtualMachineScaleSetVM> virtualMachines =
            azureComputeClient.listVirtualMachineScaleSetVMs(azureConfig, subscriptionId, vmssId);
        List<AzureVMData> vmDataList = virtualMachines.stream().map(toVMData()).collect(Collectors.toList());

        azureVMSSTaskResponse = AzureVMSSListVMDataResponse.builder().vmssId(vmssId).vmData(vmDataList).build();
        break;
      }
      default: {
        throw new InvalidRequestException(format("Unrecognized object of class: [%s] while executing sync task",
            azureVMSSTaskParameters.getCommandType().name()));
      }
    }
    return AzureVMSSTaskExecutionResponse.builder()
        .azureVMSSTaskResponse(azureVMSSTaskResponse)
        .commandExecutionStatus(SUCCESS)
        .build();
  }

  @NotNull
  private Function<Subscription, SubscriptionData> toSubscriptionData() {
    return subscription
        -> SubscriptionData.builder().id(subscription.subscriptionId()).name(subscription.displayName()).build();
  }

  @NotNull
  private Function<VirtualMachineScaleSet, VirtualMachineScaleSetData> toVirtualMachineScaleSetData() {
    return vmss -> VirtualMachineScaleSetData.builder().id(vmss.id()).name(vmss.name()).build();
  }

  @NotNull
  private Function<VirtualMachineScaleSetVM, AzureVMData> toVMData() {
    return vm -> {
      String id = vm.inner().id();
      Optional<PublicIPAddressInner> publicIPAddressOp = azureComputeClient.getVMPublicIPAddress(vm);
      String publicIp = publicIPAddressOp.map(PublicIPAddressInner::ipAddress).orElse(EMPTY);
      String publicDnsName =
          publicIPAddressOp.map(PublicIPAddressInner::dnsSettings).map(PublicIPAddressDnsSettings::fqdn).orElse(EMPTY);
      String powerState = fixPowerState(vm.powerState());
      String size = vm.size() != null ? vm.size().toString() : EMPTY;
      return AzureVMData.builder()
          .id(id)
          .ip(publicIp)
          .publicDns(publicDnsName)
          .powerState(powerState)
          .size(size)
          .build();
    };
  }

  @NotNull
  private String fixPowerState(PowerState ps) {
    return ps != null ? ps.toString().replace(VM_POWER_STATE_PREFIX, EMPTY) : EMPTY;
  }
}
