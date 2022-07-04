/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.delegate.task.azure.AzureTestUtils.DEPLOYMENT_SLOT;
import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.delegate.task.azure.appservice.AzureAppServiceResourceUtilities;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppTrafficShiftRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTrafficShiftResponse;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AzureWebAppTrafficShiftRequestHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private AzureAppServiceDeploymentService azureAppServiceDeploymentService;
  @Mock private AzureAppServiceResourceUtilities azureAppServiceResourceUtilities;
  @Mock private AzureLogCallbackProvider logCallbackProvider;
  @InjectMocks AzureWebAppTrafficShiftRequestHandler requestHandler;
  private final double trafficPercent = 20.0;

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteTrafficShift() {
    doNothing()
        .when(azureAppServiceDeploymentService)
        .rerouteProductionSlotTraffic(
            any(AzureWebClientContext.class), anyString(), anyDouble(), any(AzureLogCallbackProvider.class));

    AzureWebAppTrafficShiftRequest trafficShiftRequest =
        AzureWebAppTrafficShiftRequest.builder()
            .infrastructure(AzureTestUtils.createTestWebAppInfraDelegateConfig())
            .trafficPercentage(trafficPercent)
            .build();
    AzureWebAppRequestResponse response =
        requestHandler.execute(trafficShiftRequest, AzureTestUtils.createTestAzureConfig(), logCallbackProvider);
    assertThat(response).isNotNull();
    assertThat(response).isInstanceOf(AzureWebAppTrafficShiftResponse.class);

    AzureWebAppTrafficShiftResponse trafficShiftResponse = (AzureWebAppTrafficShiftResponse) response;
    assertThat(trafficShiftResponse.getDeploymentProgressMarker()).isEqualTo(SLOT_TRAFFIC_PERCENTAGE);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteTrafficShiftFailure() {
    doThrow(new RuntimeException("Failed to update traffic percent"))
        .when(azureAppServiceDeploymentService)
        .rerouteProductionSlotTraffic(any(AzureWebClientContext.class), eq(DEPLOYMENT_SLOT), eq(trafficPercent),
            any(AzureLogCallbackProvider.class));

    AzureWebAppTrafficShiftRequest trafficShiftRequest =
        AzureWebAppTrafficShiftRequest.builder()
            .infrastructure(AzureTestUtils.createTestWebAppInfraDelegateConfig())
            .trafficPercentage(trafficPercent)
            .build();

    assertThatThrownBy(
        () -> requestHandler.execute(trafficShiftRequest, AzureTestUtils.createTestAzureConfig(), logCallbackProvider))
        .isInstanceOf(RuntimeException.class);
  }
}
