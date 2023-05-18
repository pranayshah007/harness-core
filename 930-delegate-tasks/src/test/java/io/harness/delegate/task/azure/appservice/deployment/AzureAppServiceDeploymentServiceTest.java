/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.SLOT_STARTING_STATUS_CHECK_INTERVAL;
import static io.harness.azure.model.AzureConstants.SLOT_STOPPING_STATUS_CHECK_INTERVAL;
import static io.harness.azure.model.AzureConstants.SLOT_SWAP;
import static io.harness.azure.model.AzureConstants.START_DEPLOYMENT_SLOT;
import static io.harness.azure.model.AzureConstants.STOP_DEPLOYMENT_SLOT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier;
import io.harness.delegate.task.azure.appservice.deployment.verifier.SwapSlotStatusVerifier;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.resourcemanager.appservice.models.DeploymentSlot;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import reactor.core.publisher.Mono;

@OwnedBy(CDP)
public class AzureAppServiceDeploymentServiceTest extends CategoryTest {
  private static final String SLOT_NAME = "slotName";
  private static final String TARGET_SLOT_NAME = "targetSlotName";
  private static final String APP_NAME = "appName";
  private static final String RESOURCE_GROUP_NAME = "resourceGroupName";
  private static final String SUBSCRIPTION_ID = "subscriptionId";
  private static final String IMAGE_AND_TAG = "image/tag";

  @Mock private AzureWebClient mockAzureWebClient;
  @Mock private AzureLogCallbackProvider mockLogCallbackProvider;
  @Mock private LogCallback mockLogCallback;
  @Mock private SlotSteadyStateChecker slotSteadyStateChecker;

  @Spy @InjectMocks AzureAppServiceDeploymentService azureAppServiceDeploymentService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(mockLogCallback).when(mockLogCallbackProvider).obtainLogCallback(anyString());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testDeployDockerImage() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove =
        Collections.singletonMap("appSetting2", getAppSettings("appSetting2"));
    Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd =
        Collections.singletonMap("appSetting1", getAppSettings("appSetting1"));
    Map<String, AzureAppServiceConnectionString> connSettingsToAdd =
        Collections.singletonMap("appSetting1", getConnectionSettings("connSetting1"));
    Map<String, AzureAppServiceConnectionString> connSettingsToRemove =
        Collections.singletonMap("appSetting1", getConnectionSettings("connSetting1"));
    Map<String, AzureAppServiceApplicationSetting> dockerSettings = Collections.singletonMap("dockerSetting1",
        AzureAppServiceApplicationSetting.builder().name("dockerSetting1").value("dockerSetting1value").build());

    AzureAppServiceDockerDeploymentContext azureAppServiceDockerDeploymentContext =
        AzureAppServiceDockerDeploymentContext.builder()
            .imagePathAndTag(IMAGE_AND_TAG)
            .slotName(SLOT_NAME)
            .steadyStateTimeoutInMin(1)
            .logCallbackProvider(mockLogCallbackProvider)
            .dockerSettings(dockerSettings)
            .azureWebClientContext(azureWebClientContext)
            .appSettingsToAdd(appSettingsToAdd)
            .appSettingsToRemove(appSettingsToRemove)
            .connSettingsToAdd(connSettingsToAdd)
            .connSettingsToRemove(connSettingsToRemove)
            .build();

    Mono<Response<Void>> responseMono = Mono.just(new SimpleResponse<>(null, 200, null, null));
    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    doReturn(responseMono).when(deploymentSlot).stopAsync();
    doReturn(responseMono).when(deploymentSlot).startAsync();
    doReturn(Optional.of(deploymentSlot))
        .when(mockAzureWebClient)
        .getDeploymentSlotByName(azureWebClientContext, SLOT_NAME);
    doReturn(
        "2022-02-07T17:03:05.566Z INFO  - Initiating warm up request to container dev-harness-docker_0_a83fad5d for site dev-harness-docker"
            .getBytes())
        .when(deploymentSlot)
        .getContainerLogs();
    azureAppServiceDeploymentService.deployDockerImage(
        azureAppServiceDockerDeploymentContext, AzureAppServicePreDeploymentData.builder().build());

    verify(slotSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            eq(1L), eq(SLOT_STOPPING_STATUS_CHECK_INTERVAL), any(), eq(STOP_DEPLOYMENT_SLOT), any());
    verify(slotSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            eq(1L), eq(SLOT_STARTING_STATUS_CHECK_INTERVAL), any(), eq(START_DEPLOYMENT_SLOT), any());
    verify(mockAzureWebClient, times(1))
        .deleteDeploymentSlotAppSettings(azureWebClientContext, SLOT_NAME, appSettingsToRemove);
    verify(mockAzureWebClient, times(1))
        .updateDeploymentSlotAppSettings(azureWebClientContext, SLOT_NAME, appSettingsToAdd);
    verify(mockAzureWebClient, times(1))
        .deleteDeploymentSlotConnectionStrings(azureWebClientContext, SLOT_NAME, connSettingsToRemove);
    verify(mockAzureWebClient, times(1))
        .updateDeploymentSlotConnectionStrings(azureWebClientContext, SLOT_NAME, connSettingsToAdd);
    verify(mockAzureWebClient, times(1))
        .updateDeploymentSlotDockerSettings(azureWebClientContext, SLOT_NAME, dockerSettings);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testDeployDockerImageFailure() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove =
        Collections.singletonMap("appSetting2", getAppSettings("appSetting2"));
    Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd =
        Collections.singletonMap("appSetting1", getAppSettings("appSetting1"));
    Map<String, AzureAppServiceConnectionString> connSettingsToAdd =
        Collections.singletonMap("appSetting1", getConnectionSettings("connSetting1"));
    Map<String, AzureAppServiceConnectionString> connSettingsToRemove =
        Collections.singletonMap("appSetting1", getConnectionSettings("connSetting1"));
    Map<String, AzureAppServiceApplicationSetting> dockerSettings = Collections.singletonMap("dockerSetting1",
        AzureAppServiceApplicationSetting.builder().name("dockerSetting1").value("dockerSetting1value").build());

    AzureAppServiceDockerDeploymentContext azureAppServiceDockerDeploymentContext =
        AzureAppServiceDockerDeploymentContext.builder()
            .imagePathAndTag(IMAGE_AND_TAG)
            .slotName(SLOT_NAME)
            .steadyStateTimeoutInMin(1)
            .logCallbackProvider(mockLogCallbackProvider)
            .dockerSettings(dockerSettings)
            .azureWebClientContext(azureWebClientContext)
            .appSettingsToAdd(appSettingsToAdd)
            .appSettingsToRemove(appSettingsToRemove)
            .connSettingsToAdd(connSettingsToAdd)
            .connSettingsToRemove(connSettingsToRemove)
            .build();

    doAnswer(invocation -> { throw new Exception(); })
        .when(mockAzureWebClient)
        .deleteDeploymentSlotAppSettings(any(), any(), any());
    assertThatThrownBy(()
                           -> azureAppServiceDeploymentService.deployDockerImage(azureAppServiceDockerDeploymentContext,
                               AzureAppServicePreDeploymentData.builder().build()))
        .isInstanceOf(Exception.class);

    reset(mockAzureWebClient);
    doAnswer(invocation -> { throw new Exception(); }).when(mockAzureWebClient).stopDeploymentSlotAsync(any(), any());
    assertThatThrownBy(()
                           -> azureAppServiceDeploymentService.deployDockerImage(azureAppServiceDockerDeploymentContext,
                               AzureAppServicePreDeploymentData.builder().build()))
        .isInstanceOf(Exception.class);

    reset(mockAzureWebClient);
    doAnswer(invocation -> { throw new Exception(); }).when(mockAzureWebClient).startDeploymentSlotAsync(any(), any());
    assertThatThrownBy(()
                           -> azureAppServiceDeploymentService.deployDockerImage(azureAppServiceDockerDeploymentContext,
                               AzureAppServicePreDeploymentData.builder().build()))
        .isInstanceOf(Exception.class);

    reset(mockAzureWebClient);
    doAnswer(invocation -> { throw new Exception(); })
        .when(mockAzureWebClient)
        .deleteDeploymentSlotDockerSettings(any(), any());
    assertThatThrownBy(()
                           -> azureAppServiceDeploymentService.deployDockerImage(azureAppServiceDockerDeploymentContext,
                               AzureAppServicePreDeploymentData.builder().build()))
        .isInstanceOf(Exception.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testDeployDockerImageNoAppAndConnSettingsToAddOrRemove() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    AzureAppServiceDockerDeploymentContext azureAppServiceDockerDeploymentContext =
        AzureAppServiceDockerDeploymentContext.builder()
            .imagePathAndTag(IMAGE_AND_TAG)
            .slotName(SLOT_NAME)
            .steadyStateTimeoutInMin(1)
            .logCallbackProvider(mockLogCallbackProvider)
            .dockerSettings(new HashMap<>())
            .azureWebClientContext(azureWebClientContext)
            .build();

    Mono<Response<Void>> responseMono = Mono.just(new SimpleResponse<>(null, 200, null, null));
    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    doReturn(responseMono).when(deploymentSlot).stopAsync();
    doReturn(responseMono).when(deploymentSlot).startAsync();
    doReturn(Optional.of(deploymentSlot))
        .when(mockAzureWebClient)
        .getDeploymentSlotByName(azureWebClientContext, SLOT_NAME);
    doReturn(
        "2022-02-07T17:03:05.566Z INFO  - Initiating warm up request to container dev-harness-docker_0_a83fad5d for site dev-harness-docker"
            .getBytes())
        .when(deploymentSlot)
        .getContainerLogs();

    azureAppServiceDeploymentService.deployDockerImage(
        azureAppServiceDockerDeploymentContext, AzureAppServicePreDeploymentData.builder().build());

    verify(slotSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            eq(1L), eq(SLOT_STOPPING_STATUS_CHECK_INTERVAL), any(), eq(STOP_DEPLOYMENT_SLOT), any());
    verify(slotSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            eq(1L), eq(SLOT_STARTING_STATUS_CHECK_INTERVAL), any(), eq(START_DEPLOYMENT_SLOT), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRerouteProductionSlotTraffic() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();
    azureAppServiceDeploymentService.rerouteProductionSlotTraffic(
        azureWebClientContext, SLOT_NAME, 50, mockLogCallbackProvider);
    verify(mockAzureWebClient, times(1)).rerouteProductionSlotTraffic(azureWebClientContext, SLOT_NAME, 50);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void swapSlotsUsingCallback() {
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();
    AzureAppServiceDockerDeploymentContext azureAppServiceDockerDeploymentContext =
        AzureAppServiceDockerDeploymentContext.builder()
            .imagePathAndTag(IMAGE_AND_TAG)
            .slotName(SLOT_NAME)
            .steadyStateTimeoutInMin(1)
            .logCallbackProvider(mockLogCallbackProvider)
            .dockerSettings(new HashMap<>())
            .azureWebClientContext(azureWebClientContext)
            .build();

    azureAppServiceDeploymentService.swapSlotsUsingCallback(
        azureAppServiceDockerDeploymentContext, TARGET_SLOT_NAME, mockLogCallbackProvider);

    ArgumentCaptor<SlotStatusVerifier> statusVerifierArgument = ArgumentCaptor.forClass(SlotStatusVerifier.class);

    verify(slotSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            eq(1L), eq(SLOT_STOPPING_STATUS_CHECK_INTERVAL), any(), eq(SLOT_SWAP), statusVerifierArgument.capture());
    SlotStatusVerifier statusVerifier = statusVerifierArgument.getValue();
    assertThat(statusVerifier).isNotNull();
    assertThat(statusVerifier).isInstanceOf(SwapSlotStatusVerifier.class);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDetermineFileSuffixSeparator() {
    String path = "/some/file_123";
    int separator = azureAppServiceDeploymentService.determineSuffixSeparator(path);
    assertThat(separator).isEqualTo('_');
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDetermineFileSuffixSeparatorNoSuffix() {
    String path = "/some/file#123";
    int separator = azureAppServiceDeploymentService.determineSuffixSeparator(path);
    assertThat(separator).isEqualTo(-1);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDetermineFileSuffixSeparatorUnderscoreAndNum() {
    String path = "/some/file_name_1236";
    int separator = azureAppServiceDeploymentService.determineSuffixSeparator(path);
    assertThat(separator).isEqualTo('_');
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldDetermineFileSuffixSeparatorUnderscoreAndNumNotFromJenkins() {
    String path = "/some/file_name_somethingElse";
    int separator = azureAppServiceDeploymentService.determineSuffixSeparator(path);
    assertThat(separator).isEqualTo(-1);
  }

  private AzureWebClientContext getAzureWebClientContext() {
    return AzureWebClientContext.builder()
        .appName(APP_NAME)
        .resourceGroupName(RESOURCE_GROUP_NAME)
        .subscriptionId(SUBSCRIPTION_ID)
        .azureConfig(AzureConfig.builder().build())
        .build();
  }

  private AzureAppServiceConnectionString getConnectionSettings(String name) {
    return AzureAppServiceConnectionString.builder().name(name).value(name + "value").build();
  }

  private AzureAppServiceApplicationSetting getAppSettings(String name) {
    return AzureAppServiceApplicationSetting.builder().name(name).value(name + "value").build();
  }
}
