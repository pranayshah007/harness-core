/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceResourceUtilities;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppFetchPreDeploymentDataRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppFetchPreDeploymentDataResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.common.AzureAppServiceService;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureWebAppFetchPreDeploymentDataRequestHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private AzureLogCallbackProvider logCallbackProvider;

  @Mock private AzureAppServiceResourceUtilities azureResourceUtilities;
  @Mock private AzureAppServiceService azureAppServiceService;

  @InjectMocks private AzureWebAppFetchPreDeploymentDataRequestHandler requestHandler;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteContainer() {
    final AzureWebAppFetchPreDeploymentDataRequest request =
        AzureWebAppFetchPreDeploymentDataRequest.builder()
            .applicationSettings(Collections.emptyList())
            .connectionStrings(Collections.emptyList())
            .artifact(AzureTestUtils.createTestContainerArtifactConfig())
            .infraDelegateConfig(AzureTestUtils.createTestWebAppInfraDelegateConfig())
            .build();

    final AzureAppServicePreDeploymentData preDeploymentData =
        AzureTestUtils.buildTestPreDeploymentData(AppServiceDeploymentProgress.DEPLOY_TO_SLOT);
    final AzureConfig azureConfig = AzureTestUtils.createTestAzureConfig();

    doReturn(preDeploymentData)
        .when(azureAppServiceService)
        .getDockerDeploymentPreDeploymentData(any(AzureAppServiceDockerDeploymentContext.class));

    AzureWebAppRequestResponse response = requestHandler.execute(request, azureConfig, logCallbackProvider);
    assertThat(response).isInstanceOf(AzureWebAppFetchPreDeploymentDataResponse.class);
    AzureWebAppFetchPreDeploymentDataResponse preDeploymentDataResponse =
        (AzureWebAppFetchPreDeploymentDataResponse) response;
    assertThat(preDeploymentDataResponse.getPreDeploymentData()).isSameAs(preDeploymentData);
  }
}