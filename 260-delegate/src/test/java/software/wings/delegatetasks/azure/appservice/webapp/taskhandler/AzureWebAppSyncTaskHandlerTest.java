/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.webapp.taskhandler;

import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_PRODUCTION_TYPE;
import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters.AzureAppServiceType;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppDeploymentSlotsParameters;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppInstancesParameters;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppNamesParameters;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppDeploymentSlotsResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppInstancesResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppNamesResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.DeploymentSlotData;
import io.harness.delegate.task.azure.common.AzureAppServiceService;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.artifact.ArtifactStreamAttributes;

import com.azure.resourcemanager.appservice.models.DeploymentSlot;
import com.azure.resourcemanager.appservice.models.WebApp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureWebAppSyncTaskHandlerTest extends CategoryTest {
  private static final String RESOURCE_GROUP = "test-resourceGroup";
  private static final String SUBSCRIPTION_ID = "test-subscriptionId";
  private static final String WEB_APP = "testWebApp";
  private static final String WEB_APP_SLOT = "stage";
  private static final String APP_SERVICE_TYPE = "WEB_APP";

  @Mock private AzureWebClient azureWebClient;
  @Mock private ILogStreamingTaskClient mockLogStreamingTaskClient;
  @Mock private AzureAppServiceDeploymentService deploymentService;
  @Mock private AzureAppServiceService azureAppServiceService;

  @InjectMocks private AzureWebAppListWebAppDeploymentSlotNamesTaskHandler listWebAppDeploymentSlotNamesTaskHandler;
  @InjectMocks private AzureWebAppListWebAppNamesTaskHandler listWebAppNamesTaskHandler;
  @InjectMocks private AzureWebAppListWebAppInstancesTaskHandler listWebAppInstancesTaskHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testListDeploymentSlots() {
    AzureWebAppListWebAppDeploymentSlotsParameters parameters = AzureWebAppListWebAppDeploymentSlotsParameters.builder()
                                                                    .resourceGroupName(RESOURCE_GROUP)
                                                                    .subscriptionId(SUBSCRIPTION_ID)
                                                                    .appName(WEB_APP)
                                                                    .appServiceType(APP_SERVICE_TYPE)
                                                                    .build();

    DeploymentSlot stage = mock(DeploymentSlot.class);
    DeploymentSlot dev = mock(DeploymentSlot.class);
    doReturn("stage").when(stage).name();
    doReturn("dev").when(dev).name();
    List<DeploymentSlot> slots = new ArrayList<>();
    slots.add(stage);
    slots.add(dev);
    doReturn(slots).when(azureWebClient).listDeploymentSlotsByWebAppName(any());

    AzureWebAppListWebAppDeploymentSlotsResponse response =
        (AzureWebAppListWebAppDeploymentSlotsResponse) listWebAppDeploymentSlotNamesTaskHandler.executeTaskInternal(
            parameters, getAzureConfig(), mockLogStreamingTaskClient);

    assertThat(response).isNotNull();
    List<DeploymentSlotData> deploymentSlots = response.getDeploymentSlots();
    assertThat(deploymentSlots).isNotNull();
    assertThat(deploymentSlots.size()).isEqualTo(3);

    List<DeploymentSlotData> productionSlot =
        deploymentSlots.stream()
            .filter(slot -> slot.getType().equalsIgnoreCase(DEPLOYMENT_SLOT_PRODUCTION_TYPE))
            .collect(Collectors.toList());
    assertThat(productionSlot.size()).isEqualTo(1);
    assertThat(productionSlot.get(0).getName()).isEqualTo(WEB_APP);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testListWebAppNames() {
    AzureWebAppListWebAppNamesParameters parameters = AzureWebAppListWebAppNamesParameters.builder()
                                                          .subscriptionId(SUBSCRIPTION_ID)
                                                          .resourceGroupName(RESOURCE_GROUP)
                                                          .appServiceType(APP_SERVICE_TYPE)
                                                          .build();

    WebApp app1 = mock(WebApp.class);
    WebApp app2 = mock(WebApp.class);
    doReturn("webApp1").when(app1).name();
    doReturn("webApp2").when(app2).name();
    List<WebApp> webApps = new ArrayList<>();
    webApps.add(app1);
    webApps.add(app2);
    doReturn(webApps).when(azureWebClient).listWebAppsByResourceGroupName(any());

    AzureWebAppListWebAppNamesResponse response =
        (AzureWebAppListWebAppNamesResponse) listWebAppNamesTaskHandler.executeTaskInternal(
            parameters, getAzureConfig(), mockLogStreamingTaskClient);

    assertThat(response).isNotNull();
    assertThat(response.getWebAppNames().size()).isEqualTo(2);
    assertThat(response.getWebAppNames()).containsExactly("webApp1", "webApp2");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testListWebAppNamesFailure() {
    AzureWebAppListWebAppNamesParameters parameters = AzureWebAppListWebAppNamesParameters.builder()
                                                          .subscriptionId(SUBSCRIPTION_ID)
                                                          .resourceGroupName(RESOURCE_GROUP)
                                                          .appServiceType(APP_SERVICE_TYPE)
                                                          .build();
    ArtifactStreamAttributes artifactStreamAttributes = buildArtifactStreamAttributes(true);

    doAnswer(invocationOnMock -> { throw new Exception(); }).when(azureWebClient).listWebAppsByResourceGroupName(any());
    listWebAppNamesTaskHandler.executeTask(
        parameters, getAzureConfig(), mockLogStreamingTaskClient, artifactStreamAttributes);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testListWebAppInstances() {
    AzureWebAppListWebAppInstancesParameters parameters = AzureWebAppListWebAppInstancesParameters.builder()
                                                              .subscriptionId(SUBSCRIPTION_ID)
                                                              .resourceGroupName(RESOURCE_GROUP)
                                                              .appServiceType(AzureAppServiceType.WEB_APP)
                                                              .appName(WEB_APP)
                                                              .slotName(WEB_APP_SLOT)
                                                              .build();

    List<AzureAppDeploymentData> deploymentData = new ArrayList<>();
    IntStream.range(1, 3).forEach(x
        -> deploymentData.add(AzureAppDeploymentData.builder()
                                  .instanceId(String.valueOf(x))
                                  .appName(WEB_APP)
                                  .deploySlot(WEB_APP_SLOT)
                                  .build()));
    doReturn(deploymentData).when(azureAppServiceService).fetchDeploymentData(any(), eq(WEB_APP_SLOT));

    AzureWebAppListWebAppInstancesResponse response =
        (AzureWebAppListWebAppInstancesResponse) listWebAppInstancesTaskHandler.executeTaskInternal(
            parameters, getAzureConfig(), mockLogStreamingTaskClient);
    assertThat(response).isNotNull();
    assertThat(response.getDeploymentData().size()).isEqualTo(2);
  }

  private AzureConfig getAzureConfig() {
    return AzureConfig.builder().clientId("clientId").key("key".toCharArray()).tenantId("tenantId").build();
  }

  private ArtifactStreamAttributes buildArtifactStreamAttributes(boolean isDockerArtifactType) {
    return isDockerArtifactType ? null : ArtifactStreamAttributes.builder().build();
  }
}
