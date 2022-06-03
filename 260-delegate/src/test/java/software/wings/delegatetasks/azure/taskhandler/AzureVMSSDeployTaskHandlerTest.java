/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureAutoScaleSettingsClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.AzureVMSSPreDeploymentData;
import io.harness.delegate.task.azure.request.AzureVMSSDeployTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSDeployTaskResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.microsoft.azure.Page;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet.UpdateStages.WithApply;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet.UpdateStages.WithPrimaryLoadBalancer;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import com.microsoft.azure.management.compute.implementation.VirtualMachineScaleSetVMInner;
import com.microsoft.azure.management.network.PublicIPAddressDnsSettings;
import com.microsoft.azure.management.network.VirtualMachineScaleSetNetworkInterface;
import com.microsoft.azure.management.network.VirtualMachineScaleSetNicIPConfiguration;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceIPConfigurationInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.rest.RestException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import org.assertj.core.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import rx.Observable;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureVMSSDeployTaskHandlerTest extends WingsBaseTest {
  @Mock private AzureComputeClient azureComputeClient;
  @Mock private AzureAutoScaleSettingsClient azureAutoScaleSettingsClient;
  @Mock private TimeLimiter mockTimeLimiter;
  @Spy @InjectMocks AzureVMSSDeployTaskHandler deployTaskHandler;
  @Inject @InjectMocks AzureVMSSRollbackTaskHandler rollbackTaskHandler;
  private final int newInstancesSize = 5;
  private final int oldInstancesSize = 3;

  @Before
  public void setupMocks() {
    AzureVMSSDeployTaskParameters deployTaskParameters = buildDeployTaskParameters();
    VirtualMachineScaleSet newVirtualMachineScaleSet = mock(VirtualMachineScaleSet.class);
    VirtualMachineScaleSet oldVirtualMachineScaleSet = mock(VirtualMachineScaleSet.class);

    doReturn(Optional.of(newVirtualMachineScaleSet))
        .when(azureComputeClient)
        .getVirtualMachineScaleSetByName(any(AzureConfig.class), eq(deployTaskParameters.getSubscriptionId()),
            eq(deployTaskParameters.getResourceGroupName()),
            eq(deployTaskParameters.getNewVirtualMachineScaleSetName()));

    doReturn(Optional.of(oldVirtualMachineScaleSet))
        .when(azureComputeClient)
        .getVirtualMachineScaleSetByName(any(AzureConfig.class), eq(deployTaskParameters.getSubscriptionId()),
            eq(deployTaskParameters.getResourceGroupName()),
            eq(deployTaskParameters.getOldVirtualMachineScaleSetName()));

    mockNewVirtualMachineScaleSetCapacity(newVirtualMachineScaleSet);
    mockOldVirtualMachineScaleSetCapacity(oldVirtualMachineScaleSet);

    PagedList<VirtualMachineScaleSetVM> newVirtualMachineScaleSetList = buildScaleSetVMs(newInstancesSize);
    PagedList<VirtualMachineScaleSetVM> oldVirtualMachineScaleSetList = buildScaleSetVMs(oldInstancesSize);

    doReturn(newVirtualMachineScaleSetList)
        .when(azureComputeClient)
        .listVirtualMachineScaleSetVMs(any(AzureConfig.class), eq(deployTaskParameters.getSubscriptionId()),
            eq(deployTaskParameters.getResourceGroupName()),
            eq(deployTaskParameters.getNewVirtualMachineScaleSetName()));

    doReturn(oldVirtualMachineScaleSetList)
        .when(azureComputeClient)
        .listVirtualMachineScaleSetVMs(any(AzureConfig.class), eq(deployTaskParameters.getSubscriptionId()),
            eq(deployTaskParameters.getResourceGroupName()),
            eq(deployTaskParameters.getOldVirtualMachineScaleSetName()));

    doReturn(Collections.emptyList())
        .when(deployTaskHandler)
        .getExistingInstanceIds(any(AzureConfig.class), any(), eq(deployTaskParameters));
  }

  private void mockOldVirtualMachineScaleSetCapacity(VirtualMachineScaleSet oldVirtualMachineScaleSet) {
    WithPrimaryLoadBalancer oldWithPrimaryLoadBalancer = mock(WithPrimaryLoadBalancer.class);
    doReturn(oldWithPrimaryLoadBalancer).when(oldVirtualMachineScaleSet).update();

    WithApply oldVMSSWithApply = mock(WithApply.class);
    doReturn(oldVMSSWithApply).when(oldWithPrimaryLoadBalancer).withCapacity(anyInt());
    doReturn(Observable.from(Arrays.array(oldVirtualMachineScaleSet))).when(oldVMSSWithApply).applyAsync();
  }

  private void mockNewVirtualMachineScaleSetCapacity(VirtualMachineScaleSet newVirtualMachineScaleSet) {
    WithPrimaryLoadBalancer newWithPrimaryLoadBalancer = mock(WithPrimaryLoadBalancer.class);
    doReturn(newWithPrimaryLoadBalancer).when(newVirtualMachineScaleSet).update();

    WithApply newVMSSWithApply = mock(WithApply.class);
    doReturn(newVMSSWithApply).when(newWithPrimaryLoadBalancer).withCapacity(anyInt());
    doReturn(Observable.from(Arrays.array(newWithPrimaryLoadBalancer))).when(newVMSSWithApply).applyAsync();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testScaleSetDeploySuccess() {
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureVMSSDeployTaskParameters deployTaskParameters = buildDeployTaskParameters();
    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse =
        deployTaskHandler.executeTaskInternal(deployTaskParameters, azureConfig);

    AzureVMSSDeployTaskResponse azureVMSSTaskResponse =
        (AzureVMSSDeployTaskResponse) azureVMSSTaskExecutionResponse.getAzureVMSSTaskResponse();
    int newSize = azureVMSSTaskResponse.getVmInstancesAdded().size();
    int oldSize = azureVMSSTaskResponse.getVmInstancesExisting().size();

    assertThat(azureVMSSTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(newSize).isEqualTo(newInstancesSize);
    assertThat(oldSize).isEqualTo(oldInstancesSize);

    deployTaskParameters.setResizeNewFirst(true);
    azureVMSSTaskExecutionResponse = deployTaskHandler.executeTaskInternal(deployTaskParameters, azureConfig);
    assertThat(azureVMSSTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testScaleSetDeployFailure() {
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureVMSSDeployTaskParameters deployTaskParameters = buildDeployTaskParameters();
    doAnswer(invocation -> { throw new Exception(); })
        .when(azureComputeClient)
        .getVirtualMachineScaleSetByName(eq(azureConfig), eq(deployTaskParameters.getSubscriptionId()),
            eq(deployTaskParameters.getResourceGroupName()),
            eq(deployTaskParameters.getOldVirtualMachineScaleSetName()));

    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse =
        deployTaskHandler.executeTaskInternal(deployTaskParameters, azureConfig);
    assertThat(azureVMSSTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testScaleSetNotPresent() {
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureVMSSDeployTaskParameters deployTaskParameters = buildDeployTaskParameters();
    deployTaskParameters.setOldVirtualMachineScaleSetName(null);
    doReturn(Optional.empty()).when(azureComputeClient).getVirtualMachineScaleSetByName(any(), any(), any(), any());

    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse =
        deployTaskHandler.executeTaskInternal(deployTaskParameters, azureConfig);
    AzureVMSSDeployTaskResponse azureVMSSTaskResponse =
        (AzureVMSSDeployTaskResponse) azureVMSSTaskExecutionResponse.getAzureVMSSTaskResponse();

    assertThat(azureVMSSTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(azureVMSSTaskResponse.getVmInstancesExisting().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRollbackScaleSetSuccess() {
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureVMSSDeployTaskParameters deployTaskParameters = buildDeployTaskParameters();
    deployTaskParameters.setNewDesiredCount(0);

    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse =
        rollbackTaskHandler.executeTaskInternal(deployTaskParameters, azureConfig);

    assertThat(azureVMSSTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRollbackScaleSetFailure() {
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureVMSSDeployTaskParameters deployTaskParameters = buildDeployTaskParameters();
    deployTaskParameters.setNewDesiredCount(0);

    doAnswer(invocation -> { throw new Exception(); })
        .when(azureComputeClient)
        .deleteVirtualMachineScaleSetById(eq(azureConfig), any(), any());

    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse =
        rollbackTaskHandler.executeTaskInternal(deployTaskParameters, azureConfig);

    assertThat(azureVMSSTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(FAILURE);
  }

  private AzureVMSSDeployTaskParameters buildDeployTaskParameters() {
    String subscriptionId = "subscription-id";
    String resourceGroup = "resourceGroup";
    String newScaleSetName = "newScaleSet";
    String oldScaleSetName = "oldScaleSet";

    return AzureVMSSDeployTaskParameters.builder()
        .newVirtualMachineScaleSetName(newScaleSetName)
        .oldVirtualMachineScaleSetName(oldScaleSetName)
        .subscriptionId(subscriptionId)
        .resourceGroupName(resourceGroup)
        .newDesiredCount(newInstancesSize)
        .oldDesiredCount(oldInstancesSize)
        .timeoutIntervalInMin(10)
        .resizeNewFirst(false)
        .preDeploymentData(AzureVMSSPreDeploymentData.builder()
                               .desiredCapacity(1)
                               .minCapacity(1)
                               .oldVmssName(oldScaleSetName)
                               .scalingPolicyJSON(Collections.emptyList())
                               .build())
        .build();
  }

  private PagedList<VirtualMachineScaleSetVM> buildScaleSetVMs(int count) {
    PagedList<VirtualMachineScaleSetVM> vmPage = new PagedList<VirtualMachineScaleSetVM>() {
      @Override
      public Page<VirtualMachineScaleSetVM> nextPage(String s) throws RestException {
        return null;
      }
    };
    ArrayList<VirtualMachineScaleSetVM> vms = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      VirtualMachineScaleSetVM vm = mock(VirtualMachineScaleSetVM.class);
      VirtualMachineScaleSetVMInner vmInner = mock(VirtualMachineScaleSetVMInner.class);
      PagedList<VirtualMachineScaleSetNetworkInterface> networkInterfacePagedList = buildNetworkInterfaces(i);
      String vmId = "VirtualMachine" + i;

      when(vm.inner()).thenReturn(vmInner);
      when(vm.name()).thenReturn(vmId);
      when(vm.listNetworkInterfaces()).thenReturn(networkInterfacePagedList);
      when(vmInner.vmId()).thenReturn(vmId);
      vms.add(vm);
    }
    vmPage.addAll(vms);
    return vmPage;
  }

  private PagedList<VirtualMachineScaleSetNetworkInterface> buildNetworkInterfaces(int count) {
    PagedList<VirtualMachineScaleSetNetworkInterface> networkInterfacePagedList =
        new PagedList<VirtualMachineScaleSetNetworkInterface>() {
          @Override
          public Page<VirtualMachineScaleSetNetworkInterface> nextPage(String s) throws RestException {
            return null;
          }
        };
    VirtualMachineScaleSetNetworkInterface networkInterface = mock(VirtualMachineScaleSetNetworkInterface.class);
    VirtualMachineScaleSetNicIPConfiguration ipConfiguration = mock(VirtualMachineScaleSetNicIPConfiguration.class);
    NetworkInterfaceIPConfigurationInner ipConfigurationInner = mock(NetworkInterfaceIPConfigurationInner.class);
    PublicIPAddressInner publicIPAddressInner = mock(PublicIPAddressInner.class);
    PublicIPAddressDnsSettings ipAddressDnsSettings = mock(PublicIPAddressDnsSettings.class);

    when(networkInterface.primaryPrivateIP()).thenReturn("10.0.5." + count);
    when(networkInterface.primaryIPConfiguration()).thenReturn(ipConfiguration);
    when(ipConfiguration.inner()).thenReturn(ipConfigurationInner);
    when(ipConfigurationInner.publicIPAddress()).thenReturn(publicIPAddressInner);
    when(publicIPAddressInner.dnsSettings()).thenReturn(ipAddressDnsSettings);
    when(ipAddressDnsSettings.fqdn()).thenReturn("fully-qualified-name" + count);

    networkInterfacePagedList.add(networkInterface);
    return networkInterfacePagedList;
  }
}
