/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.azure.AzureTestUtils.APP_NAME;
import static io.harness.delegate.task.azure.AzureTestUtils.DEPLOYMENT_SLOT;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceResourceUtilities;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServicePackageDeploymentContext;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.appservice.webapp.ng.exception.AzureWebAppSlotDeploymentExceptionData;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppSlotDeploymentRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppSlotDeploymentResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.artifact.ArtifactDownloadContext;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadResponse;
import io.harness.delegate.task.azure.artifact.AzureArtifactDownloadService;
import io.harness.delegate.task.azure.artifact.AzureRegistrySettingsAdapter;
import io.harness.delegate.task.azure.common.AzureAppServiceService;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.rule.Owner;

import software.wings.utils.ArtifactType;

import java.io.File;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureWebAppSlotDeploymentRequestHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private File artifactFile;

  @Mock private AzureAppServiceDeploymentService azureAppServiceDeploymentService;
  @Mock private AzureAppServiceService azureAppServiceService;
  @Mock private AzureAppServiceResourceUtilities azureAppServiceResourceUtilities;
  @Mock private AzureLogCallbackProvider logCallbackProvider;
  @Mock private AzureRegistrySettingsAdapter azureRegistrySettingsAdapter;
  @Mock private AzureArtifactDownloadService artifactDownloadService;

  @InjectMocks AzureWebAppSlotDeploymentRequestHandler requestHandler;

  @Before
  public void setup() {
    doReturn(AzureAppServicePreDeploymentData.builder())
        .when(azureAppServiceService)
        .getDefaultPreDeploymentDataBuilder(APP_NAME, DEPLOYMENT_SLOT);
    doReturn(AzureAppServicePreDeploymentData.builder().appName(APP_NAME).build())
        .when(azureAppServiceService)
        .getDockerDeploymentPreDeploymentData(any(AzureAppServiceDockerDeploymentContext.class));

    doNothing()
        .when(azureAppServiceDeploymentService)
        .deployDockerImage(
            any(AzureAppServiceDockerDeploymentContext.class), any(AzureAppServicePreDeploymentData.class));

    doCallRealMethod().when(azureAppServiceResourceUtilities).getAppSettingsToAdd(anyList());
    doCallRealMethod().when(azureAppServiceResourceUtilities).getConnectionSettingsToAdd(anyList());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteContainerArtifact() {
    final AzureWebAppSlotDeploymentRequest request =
        AzureWebAppSlotDeploymentRequest.builder()
            .accountId("accountId")
            .preDeploymentData(AzureAppServicePreDeploymentData.builder().deploymentProgressMarker("test").build())
            .infrastructure(AzureTestUtils.createTestWebAppInfraDelegateConfig())
            .artifact(AzureTestUtils.createTestContainerArtifactConfig())
            .applicationSettings(AppSettingsFile.create(
                "[{\"name\": \"test1\", \"value\": \"test1\"}, {\"name\": \"test2\", \"value\": \"test2\"}]"))
            .connectionStrings(AppSettingsFile.create(
                "[{\"name\": \"ctest1\", \"value\": \"ctest1\"}, {\"name\": \"ctest2\", \"value\": \"ctest2\"}]"))
            .timeoutIntervalInMin(10)
            .build();

    final List<AzureAppDeploymentData> deploymentDataList = singletonList(AzureAppDeploymentData.builder().build());
    doReturn(deploymentDataList)
        .when(azureAppServiceService)
        .fetchDeploymentData(any(AzureWebClientContext.class), eq(DEPLOYMENT_SLOT));

    AzureWebAppRequestResponse requestResponse =
        requestHandler.execute(request, AzureTestUtils.createTestAzureConfig(), logCallbackProvider);
    assertThat(requestResponse).isInstanceOf(AzureWebAppSlotDeploymentResponse.class);
    AzureWebAppSlotDeploymentResponse slotRequestResponse = (AzureWebAppSlotDeploymentResponse) requestResponse;
    assertThat(slotRequestResponse.getAzureAppDeploymentData()).isSameAs(deploymentDataList);

    verify(azureAppServiceDeploymentService)
        .deployDockerImage(
            any(AzureAppServiceDockerDeploymentContext.class), any(AzureAppServicePreDeploymentData.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteContainerArtifactFailure() {
    final AzureWebAppSlotDeploymentRequest request =
        AzureWebAppSlotDeploymentRequest.builder()
            .accountId("accountId")
            .infrastructure(AzureTestUtils.createTestWebAppInfraDelegateConfig())
            .artifact(AzureTestUtils.createTestContainerArtifactConfig())
            .preDeploymentData(AzureAppServicePreDeploymentData.builder().deploymentProgressMarker("test").build())
            .timeoutIntervalInMin(10)
            .build();

    doThrow(new RuntimeException("Failed to fetch deployment data"))
        .when(azureAppServiceService)
        .fetchDeploymentData(any(AzureWebClientContext.class), eq(DEPLOYMENT_SLOT));

    assertThatThrownBy(
        () -> requestHandler.execute(request, AzureTestUtils.createTestAzureConfig(), logCallbackProvider))
        .isInstanceOf(AzureWebAppSlotDeploymentExceptionData.class)
        .matches(exception -> {
          AzureWebAppSlotDeploymentExceptionData dataException = (AzureWebAppSlotDeploymentExceptionData) exception;
          assertThat(dataException.getDeploymentProgressMarker()).isEqualTo("test");
          return true;
        });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecutePackageArtifact() {
    final AzureAppServicePreDeploymentData preDeploymentData =
        AzureAppServicePreDeploymentData.builder().deploymentProgressMarker("test").build();
    final AzureWebAppSlotDeploymentRequest request =
        AzureWebAppSlotDeploymentRequest.builder()
            .accountId("accountId")
            .infrastructure(AzureTestUtils.createTestWebAppInfraDelegateConfig())
            .artifact(AzureTestUtils.createTestPackageArtifactConfig())
            .preDeploymentData(preDeploymentData)
            .timeoutIntervalInMin(10)
            .build();

    final List<AzureAppDeploymentData> deploymentDataList = singletonList(AzureAppDeploymentData.builder().build());
    doReturn(deploymentDataList)
        .when(azureAppServiceService)
        .fetchDeploymentData(any(AzureWebClientContext.class), eq(DEPLOYMENT_SLOT));

    doReturn(ArtifactDownloadContext.builder().build())
        .when(azureAppServiceResourceUtilities)
        .toArtifactNgDownloadContext(any(), any(), any());

    doReturn(AzureArtifactDownloadResponse.builder().artifactFile(artifactFile).artifactType(ArtifactType.JAR).build())
        .when(artifactDownloadService)
        .download(any(ArtifactDownloadContext.class));

    AzureWebAppRequestResponse requestResponse =
        requestHandler.execute(request, AzureTestUtils.createTestAzureConfig(), logCallbackProvider);
    assertThat(requestResponse).isInstanceOf(AzureWebAppSlotDeploymentResponse.class);
    AzureWebAppSlotDeploymentResponse slotRequestResponse = (AzureWebAppSlotDeploymentResponse) requestResponse;
    assertThat(slotRequestResponse.getAzureAppDeploymentData()).isSameAs(deploymentDataList);

    verify(azureAppServiceDeploymentService)
        .deployPackage(any(AzureAppServicePackageDeploymentContext.class), eq(preDeploymentData));
    verify(artifactDownloadService).download(any(ArtifactDownloadContext.class));
  }
}