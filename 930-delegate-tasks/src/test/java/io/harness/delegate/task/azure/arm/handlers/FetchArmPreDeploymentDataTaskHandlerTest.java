/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm.handlers;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.azure.arm.AzureARMDeploymentService;
import io.harness.delegate.task.azure.arm.AzureARMTaskType;
import io.harness.delegate.task.azure.arm.AzureFetchArmPreDeploymentDataTaskParameters;
import io.harness.delegate.task.azure.arm.AzureFetchArmPreDeploymentDataTaskResponse;
import io.harness.delegate.task.azure.common.AzureConnectorMapper;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class FetchArmPreDeploymentDataTaskHandlerTest extends CategoryTest {
  public static final String CLIENT_ID = "CLIENT_ID";
  public static final String TENANT_ID = "TENANT_ID";
  public static final String KEY = "KEY";
  @Mock private AzureLogCallbackProvider mockLogStreamingTaskClient;
  @Mock private LogCallback mockLogCallback;
  @Mock private AzureARMDeploymentService azureARMDeploymentService;

  @Mock private AzureConnectorMapper azureConnectorMapper;
  @Spy @InjectMocks FetchArmPreDeploymentDataTaskHandler handler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(mockLogCallback).when(mockLogStreamingTaskClient).obtainLogCallback(anyString());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString());
    AzureConfig azureConfig = buildAzureConfig();
    doReturn(azureConfig).when(azureConnectorMapper).toAzureConfig(any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal() throws IOException, InterruptedException {
    String templateJson = "test template";
    AzureFetchArmPreDeploymentDataTaskParameters parameters = getAzureAzureFetchArmPreDeploymentDataTaskParameters();
    doReturn(templateJson).when(azureARMDeploymentService).exportExistingResourceGroupTemplate(any(), any());

    AzureFetchArmPreDeploymentDataTaskResponse response =
        (AzureFetchArmPreDeploymentDataTaskResponse) handler.executeTaskInternal(
            parameters, "delegateId", "taskId", mockLogStreamingTaskClient);

    verify(azureARMDeploymentService, times(1)).exportExistingResourceGroupTemplate(any(), any());
    assertThat(response.getAzureARMPreDeploymentData().getResourceGroup()).isEqualTo(parameters.getResourceGroupName());
    assertThat(response.getAzureARMPreDeploymentData().getSubscriptionId()).isEqualTo(parameters.getSubscriptionId());
    assertThat(response.getAzureARMPreDeploymentData().getResourceGroupTemplateJson()).isEqualTo(templateJson);
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalExceptionThrown() {
    AzureFetchArmPreDeploymentDataTaskParameters parameters = getAzureAzureFetchArmPreDeploymentDataTaskParameters();
    doThrow(new RuntimeException()).when(azureARMDeploymentService).exportExistingResourceGroupTemplate(any(), any());

    assertThatThrownBy(
        () -> handler.executeTaskInternal(parameters, "delegateId", "taskId", mockLogStreamingTaskClient))
        .isInstanceOf(RuntimeException.class);

    verify(azureARMDeploymentService, times(1)).exportExistingResourceGroupTemplate(any(), any());
    verify(mockLogCallback)
        .saveExecutionLog(contains("Error while fetch resource group template"), eq(LogLevel.ERROR),
            eq(CommandExecutionStatus.FAILURE));
  }

  private AzureConfig buildAzureConfig() {
    return AzureConfig.builder().clientId(CLIENT_ID).key(KEY.toCharArray()).tenantId(TENANT_ID).build();
  }

  private AzureFetchArmPreDeploymentDataTaskParameters getAzureAzureFetchArmPreDeploymentDataTaskParameters() {
    return AzureFetchArmPreDeploymentDataTaskParameters.builder()
        .accountId("ACCOUNT_ID")
        .connectorDTO(AzureConnectorDTO.builder().build())
        .taskType(AzureARMTaskType.ARM_DEPLOYMENT)
        .timeoutInMs(100000)
        .encryptedDataDetails(Collections.emptyList())
        .subscriptionId("SUBSCRIPTION_ID")
        .resourceGroupName("RESOURCE_GROUP_NAME")
        .build();
  }
}
