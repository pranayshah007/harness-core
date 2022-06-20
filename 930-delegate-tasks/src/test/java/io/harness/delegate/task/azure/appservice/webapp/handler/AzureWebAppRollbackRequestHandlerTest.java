package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.azure.AzureTestUtils.DEPLOYMENT_SLOT;
import static io.harness.delegate.task.azure.AzureTestUtils.TRAFFIC_WEIGHT;
import static io.harness.rule.OwnerRule.VLICA;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceResourceUtilities;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.exception.AzureWebAppRollbackExceptionData;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppRollbackRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppNGRollbackResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.common.AzureAppServiceService;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

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
public class AzureWebAppRollbackRequestHandlerTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private AzureAppServiceDeploymentService azureAppServiceDeploymentService;
  @Mock private AzureAppServiceService azureAppServiceService;
  @Mock private AzureAppServiceResourceUtilities azureAppServiceResourceUtilities;
  @Mock private AzureLogCallbackProvider logCallbackProvider;
  @Mock private LogCallback mockLogCallback;

  @InjectMocks
  AzureWebAppRollbackRequestHandler requestHandler;

  @Before
  public void setup() {
    doNothing()
        .when(azureAppServiceDeploymentService)
        .deployDockerImage(
            any(AzureAppServiceDockerDeploymentContext.class), any(AzureAppServicePreDeploymentData.class));

    doReturn(mockLogCallback).when(logCallbackProvider).obtainLogCallback(anyString());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testExecuteContainerArtifact() {
    final AzureWebAppRollbackRequest request =
        AzureWebAppRollbackRequest.builder()
            .preDeploymentData(AzureTestUtils.buildTestPreDeploymentData(AppServiceDeploymentProgress.DEPLOY_TO_SLOT))
            .infrastructure(AzureTestUtils.createTestWebAppInfraDelegateConfig())
            .artifact(AzureTestUtils.createTestContainerArtifactConfig())
            .timeoutIntervalInMin(10)
            .blueGreen(true)
            .build();

    final List<AzureAppDeploymentData> deploymentDataList = singletonList(AzureAppDeploymentData.builder().build());
    doReturn(deploymentDataList)
        .when(azureAppServiceService)
        .fetchDeploymentData(any(AzureWebClientContext.class), eq(DEPLOYMENT_SLOT));

    AzureWebAppRequestResponse requestResponse =
        requestHandler.execute(request, AzureTestUtils.createTestAzureConfig(), logCallbackProvider);

    assertThat(requestResponse).isInstanceOf(AzureWebAppNGRollbackResponse.class);
    AzureWebAppNGRollbackResponse rollbackResponse = (AzureWebAppNGRollbackResponse) requestResponse;
    assertThat(rollbackResponse.getAzureAppDeploymentData()).isSameAs(deploymentDataList);

    verify(azureAppServiceDeploymentService)
        .deployDockerImage(
            any(AzureAppServiceDockerDeploymentContext.class), any(AzureAppServicePreDeploymentData.class));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testRollbackDeploymentAndTrafficShift() {
    mockRerouteProductionSlotTraffic();
    final AzureWebAppRollbackRequest request = AzureWebAppRollbackRequest.builder()
                                                   .preDeploymentData(AzureTestUtils.buildTestPreDeploymentData(
                                                       AppServiceDeploymentProgress.UPDATE_TRAFFIC_PERCENT))
                                                   .infrastructure(AzureTestUtils.createTestWebAppInfraDelegateConfig())
                                                   .artifact(AzureTestUtils.createTestContainerArtifactConfig())
                                                   .timeoutIntervalInMin(10)
                                                   .build();

    AzureWebAppRequestResponse requestResponse =
        requestHandler.execute(request, AzureTestUtils.createTestAzureConfig(), logCallbackProvider);

    assertThat(requestResponse).isNotNull();
    verify(azureAppServiceDeploymentService)
        .rerouteProductionSlotTraffic(any(), eq(DEPLOYMENT_SLOT), eq(TRAFFIC_WEIGHT), any());
  }

  private void mockRerouteProductionSlotTraffic() {
    doNothing()
        .when(azureAppServiceDeploymentService)
        .rerouteProductionSlotTraffic(any(), eq(DEPLOYMENT_SLOT), eq(TRAFFIC_WEIGHT), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testRollbackWithProgressMaker() {
    mockRerouteProductionSlotTraffic();
    final AzureWebAppRollbackRequest request =
        AzureWebAppRollbackRequest.builder()
            .preDeploymentData(AzureTestUtils.buildTestPreDeploymentData(AppServiceDeploymentProgress.STOP_SLOT))
            .infrastructure(AzureTestUtils.createTestWebAppInfraDelegateConfig())
            .artifact(AzureTestUtils.createTestContainerArtifactConfig())
            .timeoutIntervalInMin(10)
            .build();

    AzureWebAppRequestResponse requestResponse =
        requestHandler.execute(request, AzureTestUtils.createTestAzureConfig(), logCallbackProvider);
    assertThat(requestResponse).isNotNull();
    verify(azureAppServiceDeploymentService).startSlotAsyncWithSteadyCheck(any(), any(), any());
    verify(azureAppServiceService, never()).fetchDeploymentData(any(), eq(DEPLOYMENT_SLOT));
    verify(azureAppServiceDeploymentService, never()).deployDockerImage(any(), any());

    request.getPreDeploymentData().setDeploymentProgressMarker(AppServiceDeploymentProgress.DEPLOYMENT_COMPLETE.name());
    AzureWebAppRequestResponse requestRespons2 =
        requestHandler.execute(request, AzureTestUtils.createTestAzureConfig(), logCallbackProvider);
    assertThat(requestRespons2).isNotNull();
    verify(azureAppServiceDeploymentService, never()).deployDockerImage(any(), any());
    verify(azureAppServiceDeploymentService, never()).stopSlotAsyncWithSteadyCheck(any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testExecuteContainerArtifactFailure() {
    final AzureWebAppRollbackRequest request =
        AzureWebAppRollbackRequest.builder()
            .preDeploymentData(AzureTestUtils.buildTestPreDeploymentData(AppServiceDeploymentProgress.DEPLOY_TO_SLOT))
            .infrastructure(AzureTestUtils.createTestWebAppInfraDelegateConfig())
            .artifact(AzureTestUtils.createTestContainerArtifactConfig())
            .timeoutIntervalInMin(10)
            .build();

    doThrow(new RuntimeException("Failed to fetch deployment data"))
        .when(azureAppServiceService)
        .fetchDeploymentData(any(AzureWebClientContext.class), eq(DEPLOYMENT_SLOT));

    assertThatThrownBy(
        () -> requestHandler.execute(request, AzureTestUtils.createTestAzureConfig(), logCallbackProvider))
        .isInstanceOf(AzureWebAppRollbackExceptionData.class)
        .matches(exception -> {
          AzureWebAppRollbackExceptionData dataException = (AzureWebAppRollbackExceptionData) exception;
          assertThat(dataException.getDeploymentProgressMarker()).isEqualTo("DEPLOY_TO_SLOT");
          return true;
        });
  }
}
