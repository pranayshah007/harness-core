/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.webapp.taskhandler;

import static io.harness.azure.model.AzureConstants.SLOT_TRAFFIC_PERCENTAGE;
import static io.harness.azure.model.AzureConstants.SOURCE_SLOT_NAME_BLANK_ERROR_MSG;
import static io.harness.azure.model.AzureConstants.TARGET_SLOT_NAME_BLANK_ERROR_MSG;
import static io.harness.azure.model.AzureConstants.WEB_APP_NAME_BLANK_ERROR_MSG;
import static io.harness.rule.OwnerRule.IVAN;

import static com.cronutils.utils.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceResourceUtilities;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDeploymentContext;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppSwapSlotsParameters;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.delegate.task.azure.common.AzureLogCallbackProviderFactory;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureWebAppSlotSwapTaskHandlerTest extends WingsBaseTest {
  public static final String SUBSCRIPTION_ID = "subscriptionId";
  public static final String RESOURCE_GROUP_NAME = "resourceGroupName";
  public static final String SOURCE_SLOT_NAME = "sourceSlotName";
  public static final String TARGET_SLOT_NAME = "targetSlotName";
  public static final String WEB_APP_NAME = "webAppName";

  @Mock private ILogStreamingTaskClient mockLogStreamingTaskClient;
  @Mock private LogCallback mockLogCallback;
  @Mock private AzureLogCallbackProvider mockLogCallbackProvider;
  @Mock private AzureLogCallbackProviderFactory mockLogCallbackProviderFactory;
  @Mock private AzureAppServiceDeploymentService azureAppServiceDeploymentService;
  @Spy protected AzureAppServiceResourceUtilities azureAppServiceResourceUtilities;
  @Spy @InjectMocks AzureWebAppSlotSwapTaskHandler slotSwapTaskHandler;

  @Before
  public void setup() {
    doReturn(mockLogCallback).when(mockLogStreamingTaskClient).obtainLogCallback(anyString());
    doReturn(mockLogCallbackProvider).when(mockLogCallbackProviderFactory).createCg(mockLogStreamingTaskClient);
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal() {
    AzureWebAppSwapSlotsParameters azureWebAppSlotSwapParameters = buildAzureWebAppSlotSwapParameters();
    AzureConfig azureConfig = buildAzureConfig();

    ArgumentCaptor<String> targetSlotCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<AzureAppServiceDeploymentContext> deploymentContextArgumentCaptor =
        ArgumentCaptor.forClass(AzureAppServiceDeploymentContext.class);

    AzureAppServiceTaskResponse azureAppServiceTaskResponse =
        slotSwapTaskHandler.executeTaskInternal(azureWebAppSlotSwapParameters, azureConfig, mockLogStreamingTaskClient);
    assertThat(azureAppServiceTaskResponse).isNotNull();

    verify(azureAppServiceDeploymentService)
        .swapSlotsUsingCallback(deploymentContextArgumentCaptor.capture(), targetSlotCaptor.capture(), any());
    assertThat(targetSlotCaptor.getValue()).isEqualTo(TARGET_SLOT_NAME);
    assertThat(deploymentContextArgumentCaptor.getValue().getSlotName()).isEqualTo(SOURCE_SLOT_NAME);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithBlankWebAppNameTaskParam() {
    AzureWebAppSwapSlotsParameters azureWebAppSlotSwapParameters = buildAzureWebAppSlotSwapParameters();
    AzureConfig azureConfig = buildAzureConfig();

    azureWebAppSlotSwapParameters.setAppName(EMPTY);

    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(()
                        -> slotSwapTaskHandler.executeTaskInternal(
                            azureWebAppSlotSwapParameters, azureConfig, mockLogStreamingTaskClient))
        .withMessageContaining(WEB_APP_NAME_BLANK_ERROR_MSG);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithBlankSourceSlotNameTaskParam() {
    AzureWebAppSwapSlotsParameters azureWebAppSlotSwapParameters = buildAzureWebAppSlotSwapParameters();
    AzureConfig azureConfig = buildAzureConfig();

    azureWebAppSlotSwapParameters.setSourceSlotName(EMPTY);

    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(()
                        -> slotSwapTaskHandler.executeTaskInternal(
                            azureWebAppSlotSwapParameters, azureConfig, mockLogStreamingTaskClient))
        .withMessageContaining(SOURCE_SLOT_NAME_BLANK_ERROR_MSG);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithBlankTargetSlotNameTaskParam() {
    AzureWebAppSwapSlotsParameters azureWebAppSlotSwapParameters = buildAzureWebAppSlotSwapParameters();
    AzureConfig azureConfig = buildAzureConfig();

    azureWebAppSlotSwapParameters.setTargetSlotName(EMPTY);

    assertThatExceptionOfType(InvalidArgumentsException.class)
        .isThrownBy(()
                        -> slotSwapTaskHandler.executeTaskInternal(
                            azureWebAppSlotSwapParameters, azureConfig, mockLogStreamingTaskClient))
        .withMessageContaining(TARGET_SLOT_NAME_BLANK_ERROR_MSG);
  }

  private AzureWebAppSwapSlotsParameters buildAzureWebAppSlotSwapParameters() {
    return AzureWebAppSwapSlotsParameters.builder()
        .appId("appId")
        .accountId("accountId")
        .activityId("activityId")
        .subscriptionId(SUBSCRIPTION_ID)
        .resourceGroupName(RESOURCE_GROUP_NAME)
        .timeoutIntervalInMin(15)
        .commandName(SLOT_TRAFFIC_PERCENTAGE)
        .webAppName(WEB_APP_NAME)
        .sourceSlotName(SOURCE_SLOT_NAME)
        .targetSlotName(TARGET_SLOT_NAME)
        .preDeploymentData(AzureAppServicePreDeploymentData.builder().build())
        .build();
  }

  private AzureConfig buildAzureConfig() {
    return AzureConfig.builder().clientId("clientId").key("key".toCharArray()).tenantId("tenantId").build();
  }
}
