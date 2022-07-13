/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.azure.AzureTestUtils.APP_NAME;
import static io.harness.delegate.task.azure.AzureTestUtils.RESOURCE_GROUP;
import static io.harness.delegate.task.azure.AzureTestUtils.SUBSCRIPTION_ID;
import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.delegate.task.azure.appservice.AzureAppServiceResourceUtilities;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppSwapSlotsRequest;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.rule.Owner;

import lombok.NonNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureWebAppSlotSwapRequestHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private AzureAppServiceResourceUtilities azureAppServiceResourceUtilities;
  @Mock private AzureLogCallbackProvider logCallbackProvider;
  @InjectMocks AzureWebAppSlotSwapRequestHandler requestHandler;

  private final Integer timeout = 10;
  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteSwapSlots() {
    AzureWebAppInfraDelegateConfig azureWebAppInfraDelegateConfig =
        AzureTestUtils.createTestWebAppInfraDelegateConfig();
    AzureWebAppSwapSlotsRequest swapSlotsRequest = AzureWebAppSwapSlotsRequest.builder()
                                                       .accountId("accountId")
                                                       .infrastructure(azureWebAppInfraDelegateConfig)
                                                       .timeoutIntervalInMin(timeout)
                                                       .build();

    requestHandler.execute(swapSlotsRequest, AzureTestUtils.createTestAzureConfig(), logCallbackProvider);

    ArgumentCaptor<AzureWebClientContext> azureWebClientContextArgumentCaptor =
        ArgumentCaptor.forClass(AzureWebClientContext.class);

    verify(azureAppServiceResourceUtilities, times(1))
        .swapSlots(azureWebClientContextArgumentCaptor.capture(), eq(logCallbackProvider),
            eq(azureWebAppInfraDelegateConfig.getDeploymentSlot()), eq(azureWebAppInfraDelegateConfig.getTargetSlot()),
            eq(timeout));

    AzureWebClientContext azureWebClientContext = azureWebClientContextArgumentCaptor.getValue();
    assertThat(azureWebClientContext.getSubscriptionId()).isEqualTo(SUBSCRIPTION_ID);
    assertThat(azureWebClientContext.getResourceGroupName()).isEqualTo(RESOURCE_GROUP);
    assertThat(azureWebClientContext.getAppName()).isEqualTo(APP_NAME);

    @NonNull AzureConfig azureConfig = azureWebClientContext.getAzureConfig();
    assertThat(azureConfig.getCert()).isEqualTo(AzureTestUtils.CERT);
    assertThat(azureConfig.getClientId()).isEqualTo(AzureTestUtils.CLIENT_ID);
    assertThat(azureConfig.getTenantId()).isEqualTo(AzureTestUtils.TENANT_ID);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteSwapSlotsFailure() {
    doThrow(new RuntimeException("Failed to swap slot"))
        .when(azureAppServiceResourceUtilities)
        .swapSlots(any(), any(), any(), any(), any());

    AzureWebAppSwapSlotsRequest swapSlotsRequest =
        AzureWebAppSwapSlotsRequest.builder()
            .accountId("accountId")
            .infrastructure(AzureTestUtils.createTestWebAppInfraDelegateConfig())
            .timeoutIntervalInMin(timeout)
            .build();

    assertThatThrownBy(
        () -> requestHandler.execute(swapSlotsRequest, AzureTestUtils.createTestAzureConfig(), logCallbackProvider))
        .isInstanceOf(RuntimeException.class);
  }
}
