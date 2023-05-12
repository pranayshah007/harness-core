/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.webapp.taskhandler;

import static io.harness.azure.model.AzureConstants.AZURE_APP_SVC_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.JELENA;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppRollbackParameters;
import io.harness.delegate.task.azure.common.AzureAppServiceService;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.delegate.task.azure.common.AzureLogCallbackProviderFactory;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.azure.common.ArtifactDownloaderServiceLogWrapper;
import software.wings.delegatetasks.azure.common.context.ArtifactDownloaderContext;

import java.io.File;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureWebAppRollbackTaskHandlerTest extends WingsBaseTest {
  public static final String SLOT_NAME = "slotName";
  public static final double TRAFFIC_WEIGHT = 20.0;
  public static final String ROLLBACK_COMMAND_NAME = "ROLLBACK";
  public static final String APP_NAME = "appName";
  public static final int TIME_OUT = 15;

  @Mock private ILogStreamingTaskClient mockLogStreamingTaskClient;
  @Mock private LogCallback mockLogCallback;
  @Mock private AzureLogCallbackProvider mockLogCallbackProvider;
  @Mock private AzureLogCallbackProviderFactory mockLogCallbackProviderFactory;
  @Mock private AzureAppServiceDeploymentService azureAppServiceDeploymentService;
  @Mock private AzureAppServiceService azureAppServiceService;
  @Mock private ArtifactDownloaderServiceLogWrapper artifactDownloaderServiceLogWrapper;
  @Spy @InjectMocks AzureWebAppRollbackTaskHandler azureWebAppRollbackTaskHandler;

  @Before
  public void setup() {
    doReturn(mockLogCallbackProvider).when(mockLogCallbackProviderFactory).createCg(mockLogStreamingTaskClient);
    doReturn(mockLogCallback).when(mockLogStreamingTaskClient).obtainLogCallback(anyString());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal() {
    AzureWebAppRollbackParameters rollbackParameters =
        buildAzureWebAppRollbackParameters(AppServiceDeploymentProgress.UPDATE_SLOT_CONFIGURATIONS_SETTINGS);
    AzureConfig azureConfig = buildAzureConfig();
    mockDeployDockerImage();
    mockRerouteProductionSlotTraffic();

    AzureAppServiceTaskResponse azureAppServiceTaskResponse = azureWebAppRollbackTaskHandler.executeTaskInternal(
        rollbackParameters, azureConfig, mockLogStreamingTaskClient, null);

    assertThat(azureAppServiceTaskResponse).isNotNull();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRollbackWhenDeploymentFailedAtSlotShiftTrafficStage() {
    AzureWebAppRollbackParameters rollbackParameters =
        buildAzureWebAppRollbackParameters(AppServiceDeploymentProgress.UPDATE_TRAFFIC_PERCENT);
    AzureConfig azureConfig = buildAzureConfig();
    mockDeployDockerImage();
    mockRerouteProductionSlotTraffic();

    AzureAppServiceTaskResponse azureAppServiceTaskResponse = azureWebAppRollbackTaskHandler.executeTaskInternal(
        rollbackParameters, azureConfig, mockLogStreamingTaskClient, null);

    assertThat(azureAppServiceTaskResponse).isNotNull();
    verify(azureAppServiceDeploymentService)
        .rerouteProductionSlotTraffic(any(), eq(SLOT_NAME), eq(TRAFFIC_WEIGHT), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRollbackWhenDeploymentFailedBasicDeployment() {
    AzureWebAppRollbackParameters rollbackParameters =
        buildAzureWebAppRollbackParameters(AppServiceDeploymentProgress.DEPLOY_TO_SLOT);
    rollbackParameters.setBasicDeployment(true);
    AzureConfig azureConfig = buildAzureConfig();
    mockDeployDockerImage();
    mockRerouteProductionSlotTraffic();

    AzureAppServiceTaskResponse azureAppServiceTaskResponse = azureWebAppRollbackTaskHandler.executeTaskInternal(
        rollbackParameters, azureConfig, mockLogStreamingTaskClient, null);

    assertThat(azureAppServiceTaskResponse).isNotNull();
    verify(azureAppServiceDeploymentService, times(0))
        .rerouteProductionSlotTraffic(any(), eq(SLOT_NAME), eq(TRAFFIC_WEIGHT), any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRollbackFromAllProgressMarker() {
    AzureConfig azureConfig = buildAzureConfig();
    mockDeployDockerImage();
    mockRerouteProductionSlotTraffic();

    AzureWebAppRollbackParameters rollbackParameters =
        buildAzureWebAppRollbackParameters(AppServiceDeploymentProgress.SAVE_CONFIGURATION);
    AzureAppServiceTaskResponse azureAppServiceTaskResponse = azureWebAppRollbackTaskHandler.executeTaskInternal(
        rollbackParameters, azureConfig, mockLogStreamingTaskClient, null);
    assertThat(azureAppServiceTaskResponse).isNotNull();
    verify(azureAppServiceService, never()).fetchDeploymentData(any(), eq(SLOT_NAME));
    verify(azureAppServiceDeploymentService, never()).deployDockerImage(any(), any());

    rollbackParameters.getPreDeploymentData().setDeploymentProgressMarker(
        AppServiceDeploymentProgress.STOP_SLOT.name());
    azureAppServiceTaskResponse = azureWebAppRollbackTaskHandler.executeTaskInternal(
        rollbackParameters, azureConfig, mockLogStreamingTaskClient, null);
    assertThat(azureAppServiceTaskResponse).isNotNull();
    verify(azureAppServiceDeploymentService).startSlotAsyncWithSteadyCheck(any(), any(), any());
    verify(azureAppServiceService, never()).fetchDeploymentData(any(), eq(SLOT_NAME));
    verify(azureAppServiceDeploymentService, never()).deployDockerImage(any(), any());

    rollbackParameters.getPreDeploymentData().setDeploymentProgressMarker(
        AppServiceDeploymentProgress.DEPLOYMENT_COMPLETE.name());
    azureAppServiceTaskResponse = azureWebAppRollbackTaskHandler.executeTaskInternal(
        rollbackParameters, azureConfig, mockLogStreamingTaskClient, null);
    assertThat(azureAppServiceTaskResponse).isNotNull();
    verify(azureAppServiceDeploymentService, never()).deployDockerImage(any(), any());
    verify(azureAppServiceDeploymentService, never()).stopSlotAsyncWithSteadyCheck(any(), any(), any());
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testRollbackArtifact() {
    AzureConfig azureConfig = buildAzureConfig();
    doNothing().when(azureAppServiceDeploymentService).deployPackage(any(), any());
    mockRerouteProductionSlotTraffic();
    ArtifactStreamAttributes artifactStreamAttributes = buildArtifactStreamAttributes(false);
    File artifactWorkdir = new File(AZURE_APP_SVC_ARTIFACT_DOWNLOAD_DIR_PATH);
    ArtifactDownloaderContext artifactDownloaderContext = ArtifactDownloaderContext.builder()
                                                              .appId("appId")
                                                              .accountId("accountId")
                                                              .activityId("activityId")
                                                              .commandName(ROLLBACK_COMMAND_NAME)
                                                              .workingDirectory(artifactWorkdir)
                                                              .build();

    doReturn(new File("test/path"))
        .when(artifactDownloaderServiceLogWrapper)
        .fetchArtifactFileForDeploymentAndLog(artifactDownloaderContext, mockLogStreamingTaskClient);

    AzureWebAppRollbackParameters rollbackParameters =
        buildAzureWebAppRollbackParameters(AppServiceDeploymentProgress.SAVE_CONFIGURATION);
    AzureAppServiceTaskResponse azureAppServiceTaskResponse = azureWebAppRollbackTaskHandler.executeTaskInternal(
        rollbackParameters, azureConfig, mockLogStreamingTaskClient, artifactStreamAttributes);
    assertThat(azureAppServiceTaskResponse).isNotNull();
    verify(azureAppServiceService, never()).fetchDeploymentData(any(), eq(SLOT_NAME));
    verify(azureAppServiceDeploymentService, never()).deployPackage(any(), any());

    rollbackParameters.getPreDeploymentData().setDeploymentProgressMarker(
        AppServiceDeploymentProgress.STOP_SLOT.name());
    azureAppServiceTaskResponse = azureWebAppRollbackTaskHandler.executeTaskInternal(
        rollbackParameters, azureConfig, mockLogStreamingTaskClient, artifactStreamAttributes);
    assertThat(azureAppServiceTaskResponse).isNotNull();
    verify(azureAppServiceDeploymentService).startSlotAsyncWithSteadyCheck(any(), any(), any());
    verify(azureAppServiceService, never()).fetchDeploymentData(any(), eq(SLOT_NAME));
    verify(azureAppServiceDeploymentService, never()).deployPackage(any(), any());

    rollbackParameters.getPreDeploymentData().setDeploymentProgressMarker(
        AppServiceDeploymentProgress.DEPLOYMENT_COMPLETE.name());
    azureAppServiceTaskResponse = azureWebAppRollbackTaskHandler.executeTaskInternal(
        rollbackParameters, azureConfig, mockLogStreamingTaskClient, artifactStreamAttributes);
    assertThat(azureAppServiceTaskResponse).isNotNull();
    verify(azureAppServiceDeploymentService, never()).deployPackage(any(), any());
    verify(azureAppServiceDeploymentService, never()).stopSlotAsyncWithSteadyCheck(any(), any(), any());
  }

  private void mockDeployDockerImage() {
    doNothing().when(azureAppServiceDeploymentService).deployDockerImage(any(), any());
  }

  private ArtifactStreamAttributes buildArtifactStreamAttributes(boolean isDockerArtifactType) {
    return isDockerArtifactType ? null : ArtifactStreamAttributes.builder().build();
  }

  private void mockRerouteProductionSlotTraffic() {
    doNothing()
        .when(azureAppServiceDeploymentService)
        .rerouteProductionSlotTraffic(any(), eq(SLOT_NAME), eq(TRAFFIC_WEIGHT), any());
  }

  private AzureWebAppRollbackParameters buildAzureWebAppRollbackParameters(
      AppServiceDeploymentProgress deploymentProgress) {
    return AzureWebAppRollbackParameters.builder()
        .appId("appId")
        .accountId("accountId")
        .activityId("activityId")
        .resourceGroupName("resourceGroupName")
        .subscriptionId("subscriptionId")
        .timeoutIntervalInMin(TIME_OUT)
        .commandName(ROLLBACK_COMMAND_NAME)
        .appName(APP_NAME)
        .preDeploymentData(buildPreDeploymentData(deploymentProgress))
        .build();
  }

  private AzureAppServicePreDeploymentData buildPreDeploymentData(AppServiceDeploymentProgress deploymentProgress) {
    return AzureAppServicePreDeploymentData.builder()
        .trafficWeight(TRAFFIC_WEIGHT)
        .appSettingsToAdd(Collections.emptyMap())
        .appSettingsToRemove(Collections.emptyMap())
        .connStringsToAdd(Collections.emptyMap())
        .connStringsToRemove(Collections.emptyMap())
        .dockerSettingsToAdd(Collections.emptyMap())
        .appName(APP_NAME)
        .slotName(SLOT_NAME)
        .imageNameAndTag("imageNameAndTag")
        .deploymentProgressMarker(deploymentProgress.name())
        .build();
  }

  private AzureConfig buildAzureConfig() {
    return AzureConfig.builder().clientId("clientId").key("key".toCharArray()).tenantId("tenantId").build();
  }
}
