/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.azure.ManagementGroupData;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.arm.AzureARMTaskResponse;
import io.harness.delegate.task.azure.arm.response.AzureARMListManagementGroupResponse;
import io.harness.delegate.task.azure.arm.response.AzureARMListSubscriptionLocationsResponse;
import io.harness.rule.Owner;

import software.wings.beans.AzureConfig;
import software.wings.service.intfc.DelegateService;
import software.wings.sm.states.azure.AzureStateHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AzureARMManagerImplTest extends CategoryTest {
  private static final String APP_ID = "APP_ID";
  private static final String SUBSCRIPTION_ID = "SUBSCRIPTION_ID";
  private static final List<String> LOCATIONS = Arrays.asList("East US", "West US", "Central US");

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListSubscriptionLocations() throws InterruptedException {
    AzureARMManagerImpl service = spy(AzureARMManagerImpl.class);
    DelegateService mockDelegateService = getDelegateServiceMock(service);

    AzureARMListSubscriptionLocationsResponse azureTaskResponse =
        AzureARMListSubscriptionLocationsResponse.builder().locations(LOCATIONS).build();
    AzureTaskExecutionResponse azureTaskExecutionResponse = getAzureTaskExecutionResponse(azureTaskResponse);

    doReturn(azureTaskExecutionResponse).when(mockDelegateService).executeTaskV2(any());

    List<String> response =
        service.listSubscriptionLocations(getAzureConfig(), Collections.emptyList(), APP_ID, SUBSCRIPTION_ID);

    assertThat(response).isNotEmpty();
    assertThat(response.size()).isEqualTo(3);
    assertThat(response).contains(LOCATIONS.toArray(new String[0]));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListManagementGroups() throws InterruptedException {
    AzureARMManagerImpl service = spy(AzureARMManagerImpl.class);
    DelegateService mockDelegateService = getDelegateServiceMock(service);

    List<ManagementGroupData> mngGroups = Collections.singletonList(
        ManagementGroupData.builder().id("MG_ID").name("MG_NAME").displayName("MG_DISPLAY_NAME").build());

    AzureARMListManagementGroupResponse azureTaskResponse =
        AzureARMListManagementGroupResponse.builder().mngGroups(mngGroups).build();
    AzureTaskExecutionResponse azureTaskExecutionResponse = getAzureTaskExecutionResponse(azureTaskResponse);

    doReturn(azureTaskExecutionResponse).when(mockDelegateService).executeTaskV2(any());

    List<ManagementGroupData> response =
        service.listManagementGroups(getAzureConfig(), Collections.emptyList(), APP_ID);

    assertThat(response).isNotEmpty();
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0).getId()).isEqualTo("MG_ID");
    assertThat(response.get(0).getName()).isEqualTo("MG_NAME");
    assertThat(response.get(0).getDisplayName()).isEqualTo("MG_DISPLAY_NAME");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListAzureCloudProviderLocations() throws InterruptedException {
    AzureARMManagerImpl service = spy(AzureARMManagerImpl.class);
    DelegateService mockDelegateService = getDelegateServiceMock(service);

    AzureARMListSubscriptionLocationsResponse azureTaskResponse =
        AzureARMListSubscriptionLocationsResponse.builder().locations(LOCATIONS).build();
    AzureTaskExecutionResponse azureTaskExecutionResponse = getAzureTaskExecutionResponse(azureTaskResponse);

    doReturn(azureTaskExecutionResponse).when(mockDelegateService).executeTaskV2(any());

    List<String> response = service.listAzureCloudProviderLocations(getAzureConfig(), Collections.emptyList(), APP_ID);

    assertThat(response).isNotEmpty();
    assertThat(response.size()).isEqualTo(3);
    assertThat(response).contains(LOCATIONS.toArray(new String[0]));
  }

  private DelegateService getDelegateServiceMock(AzureARMManagerImpl service) {
    DelegateService mockDelegateService = mock(DelegateService.class);
    AzureStateHelper azureStateHelper = mock(AzureStateHelper.class);
    on(service).set("delegateService", mockDelegateService);
    on(service).set("azureStateHelper", azureStateHelper);
    return mockDelegateService;
  }

  private AzureTaskExecutionResponse getAzureTaskExecutionResponse(AzureARMTaskResponse azureTaskResponse) {
    return AzureTaskExecutionResponse.builder().azureTaskResponse(azureTaskResponse).build();
  }

  private AzureConfig getAzureConfig() {
    return AzureConfig.builder().clientId("CLIENT_ID").tenantId("TENANT_ID").key("KEY".toCharArray()).build();
  }
}
