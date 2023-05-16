/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.azure.manager;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.CategoryTest;
import io.harness.azure.model.SubscriptionData;
import io.harness.azure.model.VirtualMachineScaleSetData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.response.AzureVMSSGetVirtualMachineScaleSetResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListResourceGroupsNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListSubscriptionsResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListVirtualMachineScaleSetsResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.rule.Owner;

import software.wings.beans.AzureConfig;
import software.wings.service.intfc.DelegateService;
import software.wings.sm.states.azure.AzureStateHelper;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AzureVMSSHelperServiceManagerImplTest extends CategoryTest {
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListSubscriptions() throws InterruptedException {
    AzureVMSSHelperServiceManagerImpl service = spy(AzureVMSSHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = getDelegateServiceMock(service);

    doReturn(AzureVMSSTaskExecutionResponse.builder()
                 .azureVMSSTaskResponse(AzureVMSSListSubscriptionsResponse.builder()
                                            .subscriptions(Collections.singletonList(
                                                SubscriptionData.builder().id("id").name("name").build()))
                                            .build())
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());

    AzureConfig azureConfig =
        AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();
    List<SubscriptionData> asgs = service.listSubscriptions(azureConfig, Collections.emptyList(), "appId");

    assertThat(asgs).isNotNull();
    assertThat(asgs.size()).isEqualTo(1);
    assertThat(asgs.get(0).getName()).isEqualTo("name");
    assertThat(asgs.get(0).getId()).isEqualTo("id");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListResourceGroupsNames() throws InterruptedException {
    AzureVMSSHelperServiceManagerImpl service = spy(AzureVMSSHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = getDelegateServiceMock(service);

    doReturn(AzureVMSSTaskExecutionResponse.builder()
                 .azureVMSSTaskResponse(AzureVMSSListResourceGroupsNamesResponse.builder()
                                            .resourceGroupsNames(Collections.singletonList("resourceGroupName"))
                                            .build())
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());

    AzureConfig azureConfig =
        AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();
    List<String> asgs =
        service.listResourceGroupsNames(azureConfig, "subscriptionId", Collections.emptyList(), "appId");

    assertThat(asgs).isNotNull();
    assertThat(asgs.size()).isEqualTo(1);
    assertThat(asgs.get(0)).isEqualTo("resourceGroupName");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListVirtualMachineScaleSets() throws InterruptedException {
    AzureVMSSHelperServiceManagerImpl service = spy(AzureVMSSHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = getDelegateServiceMock(service);

    doReturn(AzureVMSSTaskExecutionResponse.builder()
                 .azureVMSSTaskResponse(AzureVMSSListVirtualMachineScaleSetsResponse.builder()
                                            .virtualMachineScaleSets(Collections.singletonList(
                                                VirtualMachineScaleSetData.builder()
                                                    .id("id")
                                                    .name("name")
                                                    .virtualMachineAdministratorUsername("administratorName")
                                                    .build()))
                                            .build())
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());

    AzureConfig azureConfig =
        AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();
    List<VirtualMachineScaleSetData> asgs = service.listVirtualMachineScaleSets(
        azureConfig, "subscriptionId", "resourceGroupName", Collections.emptyList(), "appId");

    assertThat(asgs).isNotNull();
    assertThat(asgs.size()).isEqualTo(1);
    assertThat(asgs.get(0).getName()).isEqualTo("name");
    assertThat(asgs.get(0).getId()).isEqualTo("id");
    assertThat(asgs.get(0).getVirtualMachineAdministratorUsername()).isEqualTo("administratorName");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetVirtualMachineScaleSet() throws InterruptedException {
    AzureVMSSHelperServiceManagerImpl service = spy(AzureVMSSHelperServiceManagerImpl.class);
    DelegateService mockDelegateService = getDelegateServiceMock(service);

    doReturn(AzureVMSSTaskExecutionResponse.builder()
                 .azureVMSSTaskResponse(
                     AzureVMSSGetVirtualMachineScaleSetResponse.builder()
                         .virtualMachineScaleSet(VirtualMachineScaleSetData.builder()
                                                     .id("id")
                                                     .name("name")
                                                     .virtualMachineAdministratorUsername("administratorName")
                                                     .build())
                         .build())
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());

    AzureConfig azureConfig =
        AzureConfig.builder().clientId("clientId").tenantId("tenantId").key("key".toCharArray()).build();
    VirtualMachineScaleSetData vmssData = service.getVirtualMachineScaleSet(
        azureConfig, "subscriptionId", "resourceGroupName", "vmssName", Collections.emptyList(), "appId");

    assertThat(vmssData).isNotNull();
    assertThat(vmssData.getName()).isEqualTo("name");
    assertThat(vmssData.getId()).isEqualTo("id");
    assertThat(vmssData.getVirtualMachineAdministratorUsername()).isEqualTo("administratorName");
  }

  private DelegateService getDelegateServiceMock(AzureVMSSHelperServiceManagerImpl service) {
    DelegateService mockDelegateService = mock(DelegateService.class);
    AzureStateHelper azureStateHelper = mock(AzureStateHelper.class);
    on(service).set("delegateService", mockDelegateService);
    on(service).set("azureStateHelper", azureStateHelper);
    return mockDelegateService;
  }
}
