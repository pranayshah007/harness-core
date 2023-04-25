/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.webapp.taskhandler;

import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureContainerRegistryConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.AzureTaskResponse;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppSlotSetupParameters;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotSetupResponse;
import io.harness.delegate.task.azure.common.AzureAppServiceService;
import io.harness.delegate.task.azure.common.AzureContainerRegistryService;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.delegate.task.azure.common.AzureLogCallbackProviderFactory;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.azure.common.ArtifactDownloaderServiceLogWrapper;

import com.azure.resourcemanager.containerregistry.models.AccessKeyType;
import com.azure.resourcemanager.containerregistry.models.RegistryCredentials;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureWebAppSlotSetupTaskHandlerTest extends WingsBaseTest {
  @Mock private ILogStreamingTaskClient mockLogStreamingTaskClient;
  @Mock private LogCallback mockLogCallback;
  @Mock private AzureLogCallbackProvider mockLogCallbackProvider;
  @Mock private AzureLogCallbackProviderFactory mockLogCallbackProviderFactory;
  @Mock private AzureAppServiceDeploymentService azureAppServiceDeploymentService;
  @Mock private AzureAppServiceService azureAppServiceService;
  @Mock private AzureContainerRegistryService azureContainerRegistryService;
  @Mock private ArtifactDownloaderServiceLogWrapper artifactDownloaderServiceLogWrapper;

  @Spy @InjectMocks AzureWebAppSlotSetupTaskHandler azureWebAppSlotSetupTaskHandler;

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
    AzureConfig azureConfig = buildAzureConfig();
    AzureAppServiceTaskParameters setupParameters = buildAzureAppServiceTaskParameters(false);
    AzureAppServicePreDeploymentData appServicePreDeploymentData = buildAzureAppServicePreDeploymentData();
    ArtifactStreamAttributes artifactStreamAttributes = buildArtifactStreamAttributes(true);
    AzureAppDeploymentData azureAppDeploymentData = buildAzureAppDeploymentData();

    doNothing().when(azureAppServiceDeploymentService).deployDockerImage(any(), any());
    doReturn(AzureAppServicePreDeploymentData.builder())
        .when(azureAppServiceService)
        .getDefaultPreDeploymentDataBuilder(any(), any());

    doReturn(appServicePreDeploymentData).when(azureAppServiceService).getDockerDeploymentPreDeploymentData(any());

    doReturn(Collections.singletonList(azureAppDeploymentData))
        .when(azureAppServiceService)
        .fetchDeploymentData(any(), anyString());

    AzureTaskExecutionResponse azureTaskExecutionResponse = azureWebAppSlotSetupTaskHandler.executeTask(
        setupParameters, azureConfig, mockLogStreamingTaskClient, artifactStreamAttributes);

    assertThat(azureTaskExecutionResponse).isNotNull();
    assertThat(azureTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    AzureAppServiceTaskResponse azureAppServiceTaskResponse =
        (AzureAppServiceTaskResponse) azureTaskExecutionResponse.getAzureTaskResponse();

    assertThat(azureAppServiceTaskResponse).isNotNull();
    assertThat(azureAppServiceTaskResponse).isInstanceOf(AzureWebAppSlotSetupResponse.class);

    AzureWebAppSlotSetupResponse slotSetupResponse = (AzureWebAppSlotSetupResponse) azureAppServiceTaskResponse;
    assertThat(slotSetupResponse.getErrorMsg()).isNull();
    assertThat(slotSetupResponse.getAzureAppDeploymentData()).isNotNull();
    assertThat(slotSetupResponse.getAzureAppDeploymentData().size()).isEqualTo(1);
    assertThat(slotSetupResponse.getAzureAppDeploymentData().get(0))
        .isEqualToComparingFieldByField(azureAppDeploymentData);
    assertThat(slotSetupResponse.getPreDeploymentData()).isNotNull();
    assertThat(slotSetupResponse.getPreDeploymentData()).isEqualToComparingFieldByField(appServicePreDeploymentData);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailure() {
    AzureConfig azureConfig = buildAzureConfig();
    AzureAppServiceTaskParameters setupParameters = buildAzureAppServiceTaskParameters(false);
    AzureAppServicePreDeploymentData appServicePreDeploymentData = buildAzureAppServicePreDeploymentData();
    ArtifactStreamAttributes artifactStreamAttributes = buildArtifactStreamAttributes(true);

    doReturn(appServicePreDeploymentData).when(azureAppServiceService).getDockerDeploymentPreDeploymentData(any());
    doReturn(AzureAppServicePreDeploymentData.builder())
        .when(azureAppServiceService)
        .getDefaultPreDeploymentDataBuilder(any(), any());
    doAnswer(invocation -> { throw new Exception(); })
        .when(azureAppServiceDeploymentService)
        .deployDockerImage(any(), any());

    AzureTaskExecutionResponse azureTaskExecutionResponse = azureWebAppSlotSetupTaskHandler.executeTask(
        setupParameters, azureConfig, mockLogStreamingTaskClient, artifactStreamAttributes);
    assertThat(azureTaskExecutionResponse).isNotNull();
    assertThat(azureTaskExecutionResponse.getAzureTaskResponse()).isInstanceOf(AzureWebAppSlotSetupResponse.class);
    AzureTaskResponse failureResponse = azureTaskExecutionResponse.getAzureTaskResponse();

    AzureWebAppSlotSetupResponse response = (AzureWebAppSlotSetupResponse) failureResponse;
    assertThat(response.getPreDeploymentData().getAppName()).isEqualTo("preAppName");
    assertThat(response.getPreDeploymentData().getSlotName()).isEqualTo("preSlotName");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteTaskExternalFailure() {
    AzureConfig azureConfig = buildAzureConfig();
    AzureAppServiceTaskParameters setupParameters = buildAzureAppServiceTaskParameters(false);
    ArtifactStreamAttributes artifactStreamAttributes = buildArtifactStreamAttributes(true);
    doAnswer(invocation -> { throw new Exception(); })
        .when(azureAppServiceService)
        .getDefaultPreDeploymentDataBuilder(any(), any());

    AzureTaskExecutionResponse azureTaskExecutionResponse = azureWebAppSlotSetupTaskHandler.executeTask(
        setupParameters, azureConfig, mockLogStreamingTaskClient, artifactStreamAttributes);
    assertThat(azureTaskExecutionResponse).isNotNull();
    assertThat(azureTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalACR() {
    AzureConfig azureConfig = buildAzureConfig();
    AzureAppServiceTaskParameters setupParameters = buildAzureAppServiceTaskParameters(true);
    AzureAppServicePreDeploymentData appServicePreDeploymentData = buildAzureAppServicePreDeploymentData();
    AzureAppDeploymentData azureAppDeploymentData = buildAzureAppDeploymentData();
    ArtifactStreamAttributes artifactStreamAttributes = buildArtifactStreamAttributes(true);

    RegistryCredentials registryCredentials = Mockito.mock(RegistryCredentials.class);
    doReturn(registryCredentials)
        .when(azureContainerRegistryService)
        .getContainerRegistryCredentials(Mockito.eq(azureConfig), any());
    doReturn("testUser").when(registryCredentials).username();
    Map<AccessKeyType, String> accessKeyTypeStringMap = new HashMap<>();
    accessKeyTypeStringMap.put(AccessKeyType.PRIMARY, "access-key");
    doReturn(accessKeyTypeStringMap).when(registryCredentials).accessKeys();

    doReturn(AzureAppServicePreDeploymentData.builder())
        .when(azureAppServiceService)
        .getDefaultPreDeploymentDataBuilder(any(), any());
    doNothing().when(azureAppServiceDeploymentService).deployDockerImage(any(), any());

    doReturn(appServicePreDeploymentData).when(azureAppServiceService).getDockerDeploymentPreDeploymentData(any());

    doReturn(Collections.singletonList(azureAppDeploymentData))
        .when(azureAppServiceService)
        .fetchDeploymentData(any(), anyString());

    AzureAppServiceTaskResponse azureAppServiceTaskResponse = azureWebAppSlotSetupTaskHandler.executeTaskInternal(
        setupParameters, azureConfig, mockLogStreamingTaskClient, artifactStreamAttributes);

    assertThat(azureAppServiceTaskResponse).isNotNull();
    assertThat(azureAppServiceTaskResponse).isInstanceOf(AzureWebAppSlotSetupResponse.class);
    AzureWebAppSlotSetupResponse slotSetupResponse = (AzureWebAppSlotSetupResponse) azureAppServiceTaskResponse;

    assertThat(slotSetupResponse.getErrorMsg()).isNull();
    assertThat(slotSetupResponse.getAzureAppDeploymentData()).isNotNull();
    assertThat(slotSetupResponse.getAzureAppDeploymentData().size()).isEqualTo(1);
    assertThat(slotSetupResponse.getAzureAppDeploymentData().get(0))
        .isEqualToComparingFieldByField(azureAppDeploymentData);

    assertThat(slotSetupResponse.getPreDeploymentData()).isNotNull();
    assertThat(slotSetupResponse.getPreDeploymentData()).isEqualToComparingFieldByField(appServicePreDeploymentData);
  }

  private AzureAppServicePreDeploymentData buildAzureAppServicePreDeploymentData() {
    return AzureAppServicePreDeploymentData.builder()
        .appName("preAppName")
        .dockerSettingsToAdd(Collections.emptyMap())
        .connStringsToRemove(Collections.emptyMap())
        .appSettingsToRemove(Collections.emptyMap())
        .appSettingsToAdd(Collections.emptyMap())
        .connStringsToAdd(Collections.emptyMap())
        .slotName("preSlotName")
        .imageNameAndTag("preImageNameAndTag")
        .build();
  }

  private ArtifactStreamAttributes buildArtifactStreamAttributes(boolean isDockerArtifactType) {
    return isDockerArtifactType ? null : ArtifactStreamAttributes.builder().build();
  }

  private AzureAppDeploymentData buildAzureAppDeploymentData() {
    return AzureAppDeploymentData.builder()
        .subscriptionId("subscriptionId")
        .resourceGroup("resourceGroup")
        .instanceState("running")
        .instanceType("Microsoft.Compute/webApp")
        .instanceName("instanceName")
        .appName("appName")
        .deploySlot("deploySlotName")
        .deploySlotId("deploySlotId")
        .hostName("hostName")
        .instanceIp("instanceIp")
        .build();
  }

  private AzureAppServiceTaskParameters buildAzureAppServiceTaskParameters(boolean acr) {
    return AzureWebAppSlotSetupParameters.builder()
        .azureRegistryType(acr ? AzureRegistryType.ACR : AzureRegistryType.ARTIFACTORY_PRIVATE_REGISTRY)
        .encryptedDataDetails(Collections.emptyList())
        .connectorConfigDTO(acr ? buildAzureContainerRegistry() : buildArtifactoryServerUr())
        .imageTag("imageTag")
        .imageName("imageName")
        .accountId("accountId")
        .webAppName("webAppName")
        .applicationSettings(Collections.emptyList())
        .connectionStrings(Collections.emptyList())
        .resourceGroupName("resourceGroupName")
        .slotName("slotName")
        .subscriptionId("subscriptionId")
        .activityId("activityId")
        .timeoutIntervalInMin(15)
        .build();
  }

  private ArtifactoryConnectorDTO buildArtifactoryServerUr() {
    return ArtifactoryConnectorDTO.builder()
        .artifactoryServerUrl("artifactoryServerUr")
        .auth(buildArtifactoryAuthenticationDTO())
        .build();
  }

  private ArtifactoryAuthenticationDTO buildArtifactoryAuthenticationDTO() {
    return ArtifactoryAuthenticationDTO.builder()
        .authType(ArtifactoryAuthType.USER_PASSWORD)
        .credentials(buildArtifactoryUsernamePasswordAuthDTO())
        .build();
  }

  private ArtifactoryUsernamePasswordAuthDTO buildArtifactoryUsernamePasswordAuthDTO() {
    return ArtifactoryUsernamePasswordAuthDTO.builder()
        .username("username")
        .passwordRef(new SecretRefData("", Scope.ACCOUNT, "decryptedValue".toCharArray()))
        .build();
  }

  private AzureConfig buildAzureConfig() {
    return AzureConfig.builder().clientId("clientId").key("key".toCharArray()).tenantId("tenantId").build();
  }

  private AzureContainerRegistryConnectorDTO buildAzureContainerRegistry() {
    return AzureContainerRegistryConnectorDTO.builder()
        .azureRegistryLoginServer("azure.registry.test.com")
        .azureRegistryName("test")
        .subscriptionId("subscriptionId")
        .resourceGroupName("resourceGroupName")
        .build();
  }
}
