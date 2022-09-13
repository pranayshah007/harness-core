/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm.handlers;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.arm.AzureARMBaseHelperImpl;
import io.harness.delegate.task.azure.arm.AzureARMDeploymentService;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGParameters.AzureARMTaskNGParametersBuilder;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGResponse;
import io.harness.delegate.task.azure.arm.AzureARMTaskType;
import io.harness.delegate.task.azure.arm.AzureResourceCreationBaseHelper;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureResourceCreationTaskNGResponse;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentManagementGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentResourceGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentSubscriptionContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentTenantContext;
import io.harness.delegate.task.azure.common.AzureConnectorMapper;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class AzureArmCreateTaskHandlerTest extends CategoryTest {
  public static final String CLIENT_ID = "CLIENT_ID";
  public static final String TENANT_ID = "TENANT_ID";
  public static final String KEY = "KEY";
  @Mock private AzureLogCallbackProvider mockLogStreamingTaskClient;
  @Mock private LogCallback mockLogCallback;
  @Mock private AzureARMDeploymentService azureARMDeploymentService;

  @Spy private AzureResourceCreationBaseHelper azureARMBaseHelper = new AzureARMBaseHelperImpl();
  @Mock private AzureConnectorMapper azureConnectorMapper;
  @Spy @InjectMocks AzureCreateArmResourceTaskHandler handler;

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
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalAtResourceGroupScope() throws IOException, InterruptedException {
    AzureResourceCreationTaskNGParameters parameters = getAzureARMTaskParametersAtResourceGroupScope();

    ArgumentCaptor<DeploymentResourceGroupContext> contextArgumentCaptor =
        ArgumentCaptor.forClass(DeploymentResourceGroupContext.class);
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureARMDeploymentService)
        .deployAtResourceGroupScope(any());

    AzureResourceCreationTaskNGResponse response =
        handler.executeTaskInternal(parameters, "delegateId", "taskId", mockLogStreamingTaskClient);
    verify(azureARMDeploymentService, times(1)).deployAtResourceGroupScope(contextArgumentCaptor.capture());

    DeploymentResourceGroupContext capturedDeploymentResourceGroupContext = contextArgumentCaptor.getValue();
    assertThat(capturedDeploymentResourceGroupContext).isNotNull();
    assertThat(capturedDeploymentResourceGroupContext.getAzureClientContext()).isNotNull();
    assertThat(capturedDeploymentResourceGroupContext.getAzureClientContext().getResourceGroupName())
        .isEqualTo("RESOURCE_GROUP_NAME");
    assertThat(capturedDeploymentResourceGroupContext.getAzureClientContext().getSubscriptionId())
        .isEqualTo("SUBSCRIPTION_ID");
    assertThat(capturedDeploymentResourceGroupContext.getDeploymentName()).isEqualTo("DEPLOYMENT_NAME");
    assertThat(capturedDeploymentResourceGroupContext.getTemplateJson()).contains("Microsoft.Storage/storageAccounts");
    assertThat(capturedDeploymentResourceGroupContext.getParametersJson()).contains("Standard_LRS");
    assertThat(capturedDeploymentResourceGroupContext.getMode()).isEqualTo(AzureDeploymentMode.INCREMENTAL);

    assertThat(response).isNotNull();
    assertThat(response).isInstanceOf(AzureResourceCreationTaskNGResponse.class);
    AzureARMTaskNGResponse armDeploymentResponse = (AzureARMTaskNGResponse) response;
    assertThat(armDeploymentResponse.getOutputs()).isNotEmpty();
    assertThat(armDeploymentResponse.getOutputs()).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalAtSubsctiptionScope() throws IOException, InterruptedException {
    AzureResourceCreationTaskNGParameters parameters = getAzureARMTaskParametersAtSubscriptionScope();

    ArgumentCaptor<DeploymentSubscriptionContext> contextArgumentCaptor =
        ArgumentCaptor.forClass(DeploymentSubscriptionContext.class);
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureARMDeploymentService)
        .deployAtSubscriptionScope(any());

    AzureResourceCreationTaskNGResponse response =
        handler.executeTaskInternal(parameters, "delegateId", "taskId", mockLogStreamingTaskClient);
    verify(azureARMDeploymentService, times(1)).deployAtSubscriptionScope(contextArgumentCaptor.capture());

    DeploymentSubscriptionContext contextArgumentCaptorValue = contextArgumentCaptor.getValue();
    assertThat(contextArgumentCaptorValue).isNotNull();
    assertThat(contextArgumentCaptorValue.getDeploymentDataLocation()).isEqualTo("DEPLOYMENT_DATA_LOCATION");
    assertThat(contextArgumentCaptorValue.getSubscriptionId()).isEqualTo("SUBSCRIPTION_ID");
    assertThat(contextArgumentCaptorValue.getDeploymentName()).isEqualTo("DEPLOYMENT_NAME");
    assertThat(contextArgumentCaptorValue.getTemplateJson()).contains("Microsoft.Storage/storageAccounts");
    assertThat(contextArgumentCaptorValue.getParametersJson()).contains("Standard_LRS");
    assertThat(contextArgumentCaptorValue.getMode()).isEqualTo(AzureDeploymentMode.INCREMENTAL);

    assertThat(response).isNotNull();
    assertThat(response).isInstanceOf(AzureResourceCreationTaskNGResponse.class);
    AzureARMTaskNGResponse armDeploymentResponse = (AzureARMTaskNGResponse) response;
    assertThat(armDeploymentResponse.getOutputs()).isNotEmpty();
    assertThat(armDeploymentResponse.getOutputs()).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalAtManagementScope() throws IOException, InterruptedException {
    AzureResourceCreationTaskNGParameters parameters = getAzureARMTaskParametersAtManagementGroupScope();

    ArgumentCaptor<DeploymentManagementGroupContext> contextArgumentCaptor =
        ArgumentCaptor.forClass(DeploymentManagementGroupContext.class);
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureARMDeploymentService)
        .deployAtManagementGroupScope(any());

    AzureResourceCreationTaskNGResponse response =
        handler.executeTaskInternal(parameters, "delegateId", "taskId", mockLogStreamingTaskClient);
    verify(azureARMDeploymentService, times(1)).deployAtManagementGroupScope(contextArgumentCaptor.capture());

    DeploymentManagementGroupContext contextArgumentCaptorValue = contextArgumentCaptor.getValue();
    assertThat(contextArgumentCaptorValue).isNotNull();
    assertThat(contextArgumentCaptorValue.getDeploymentDataLocation()).isEqualTo("DEPLOYMENT_DATA_LOCATION");
    assertThat(contextArgumentCaptorValue.getManagementGroupId()).isEqualTo("MANAGEMENT_GROUP_ID");
    assertThat(contextArgumentCaptorValue.getDeploymentName()).isEqualTo("DEPLOYMENT_NAME");
    assertThat(contextArgumentCaptorValue.getTemplateJson()).contains("Microsoft.Storage/storageAccounts");
    assertThat(contextArgumentCaptorValue.getParametersJson()).contains("Standard_LRS");
    assertThat(contextArgumentCaptorValue.getMode()).isEqualTo(AzureDeploymentMode.INCREMENTAL);

    assertThat(response).isNotNull();
    assertThat(response).isInstanceOf(AzureResourceCreationTaskNGResponse.class);
    AzureARMTaskNGResponse armDeploymentResponse = (AzureARMTaskNGResponse) response;
    assertThat(armDeploymentResponse.getOutputs()).isNotEmpty();
    assertThat(armDeploymentResponse.getOutputs()).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalAtTenantScope() throws IOException, InterruptedException {
    AzureResourceCreationTaskNGParameters parameters = getAzureARMTaskParametersAtTenantScope();

    ArgumentCaptor<DeploymentTenantContext> contextArgumentCaptor =
        ArgumentCaptor.forClass(DeploymentTenantContext.class);
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureARMDeploymentService)
        .deployAtTenantScope(any());

    AzureResourceCreationTaskNGResponse response =
        handler.executeTaskInternal(parameters, "delegateId", "taskId", mockLogStreamingTaskClient);
    verify(azureARMDeploymentService, times(1)).deployAtTenantScope(contextArgumentCaptor.capture());

    DeploymentTenantContext contextArgumentCaptorValue = contextArgumentCaptor.getValue();
    assertThat(contextArgumentCaptorValue).isNotNull();
    assertThat(contextArgumentCaptorValue.getDeploymentDataLocation()).isEqualTo("DEPLOYMENT_DATA_LOCATION");
    assertThat(contextArgumentCaptorValue.getDeploymentName()).isEqualTo("DEPLOYMENT_NAME");
    assertThat(contextArgumentCaptorValue.getTemplateJson()).contains("Microsoft.Storage/storageAccounts");
    assertThat(contextArgumentCaptorValue.getParametersJson()).contains("Standard_LRS");
    assertThat(contextArgumentCaptorValue.getMode()).isEqualTo(AzureDeploymentMode.INCREMENTAL);

    assertThat(response).isNotNull();
    assertThat(response).isInstanceOf(AzureResourceCreationTaskNGResponse.class);
    AzureARMTaskNGResponse armDeploymentResponse = (AzureARMTaskNGResponse) response;
    assertThat(armDeploymentResponse.getOutputs()).isNotEmpty();
    assertThat(armDeploymentResponse.getOutputs()).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalThrowExceptions() {
    AzureResourceCreationTaskNGParameters parameters = getAzureARMTaskParametersAtResourceGroupScope();
    doThrow(new InvalidRequestException("InvalidRequestException"))
        .when(azureARMDeploymentService)
        .deployAtResourceGroupScope(any());
    AzureResourceCreationTaskNGParameters finalParameters = parameters;
    assertThatThrownBy(
        () -> handler.executeTaskInternal(finalParameters, "delegateId", "taskId", mockLogStreamingTaskClient))
        .isInstanceOf(InvalidRequestException.class);

    verify(handler, times(1)).printDefaultFailureMsgForARMDeploymentUnits(any(), any(), any());

    parameters = getAzureARMTaskParametersAtSubscriptionScope();
    doThrow(new InvalidRequestException("InvalidRequestException"))
        .when(azureARMDeploymentService)
        .deployAtSubscriptionScope(any());
    assertThatThrownBy(
        () -> handler.executeTaskInternal(finalParameters, "delegateId", "taskId", mockLogStreamingTaskClient))
        .isInstanceOf(InvalidRequestException.class);

    verify(handler, times(2)).printDefaultFailureMsgForARMDeploymentUnits(any(), any(), any());

    parameters = getAzureARMTaskParametersAtManagementGroupScope();
    doThrow(new InvalidRequestException("InvalidRequestException"))
        .when(azureARMDeploymentService)
        .deployAtManagementGroupScope(any());
    assertThatThrownBy(
        () -> handler.executeTaskInternal(finalParameters, "delegateId", "taskId", mockLogStreamingTaskClient))
        .isInstanceOf(InvalidRequestException.class);

    verify(handler, times(3)).printDefaultFailureMsgForARMDeploymentUnits(any(), any(), any());

    parameters = getAzureARMTaskParametersAtTenantScope();
    doThrow(new InvalidRequestException("InvalidRequestException"))
        .when(azureARMDeploymentService)
        .deployAtTenantScope(any());
    assertThatThrownBy(
        () -> handler.executeTaskInternal(finalParameters, "delegateId", "taskId", mockLogStreamingTaskClient))
        .isInstanceOf(InvalidRequestException.class);

    verify(handler, times(4)).printDefaultFailureMsgForARMDeploymentUnits(any(), any(), any());
  }

  private AzureConfig buildAzureConfig() {
    return AzureConfig.builder().clientId(CLIENT_ID).key(KEY.toCharArray()).tenantId(TENANT_ID).build();
  }

  private AzureARMTaskNGParameters getAzureARMTaskParametersAtResourceGroupScope() {
    return getAzureARMDeploymentParametersBuilder()
        .scopeType(ARMScopeType.RESOURCE_GROUP)
        .subscriptionId("SUBSCRIPTION_ID")
        .resourceGroupName("RESOURCE_GROUP_NAME")
        .build();
  }

  private AzureARMTaskNGParameters getAzureARMTaskParametersAtSubscriptionScope() {
    return getAzureARMDeploymentParametersBuilder()
        .scopeType(ARMScopeType.SUBSCRIPTION)
        .subscriptionId("SUBSCRIPTION_ID")
        .build();
  }

  private AzureARMTaskNGParameters getAzureARMTaskParametersAtManagementGroupScope() {
    return getAzureARMDeploymentParametersBuilder()
        .scopeType(ARMScopeType.MANAGEMENT_GROUP)
        .managementGroupId("MANAGEMENT_GROUP_ID")
        .build();
  }

  private AzureARMTaskNGParameters getAzureARMTaskParametersAtTenantScope() {
    return getAzureARMDeploymentParametersBuilder().scopeType(ARMScopeType.TENANT).build();
  }

  private AzureARMTaskNGParametersBuilder getAzureARMDeploymentParametersBuilder() {
    String template = "{\n"
        + "  \"$schema\": \"https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#\",\n"
        + "  \"contentVersion\": \"1.0.0.0\",\n"
        + "  \"resources\": [\n"
        + "    {\n"
        + "      \"type\": \"Microsoft.Storage/storageAccounts\",\n"
        + "      \"apiVersion\": \"2019-04-01\",\n"
        + "      \"name\": \"{provide-unique-name}\",\n"
        + "      \"location\": \"eastus\",\n"
        + "      \"sku\": {\n"
        + "        \"name\": \"Standard_LRS\"\n"
        + "      },\n"
        + "      \"kind\": \"StorageV2\",\n"
        + "      \"properties\": {\n"
        + "        \"supportsHttpsTrafficOnly\": true\n"
        + "      }\n"
        + "    }\n"
        + "  ]\n"
        + "}";

    String parameters = "{\n"
        + "  \"location\": \"westus\",\n"
        + "  \"sku\": {\n"
        + "    \"name\": \"Standard_LRS\"\n"
        + "  },\n"
        + "  \"kind\": \"StorageV2\",\n"
        + "  \"properties\": {}\n"
        + "}";

    return AzureARMTaskNGParameters.builder()
        .accountId("ACCOUNT_ID")
        .connectorDTO(AzureConnectorDTO.builder().build())
        .taskType(AzureARMTaskType.ARM_DEPLOYMENT)
        .timeoutInMs(100000)
        .deploymentName("DEPLOYMENT_NAME")
        .deploymentDataLocation("DEPLOYMENT_DATA_LOCATION")
        .deploymentMode(AzureDeploymentMode.INCREMENTAL)
        .parametersBody(AppSettingsFile.create(parameters))
        .templateBody(AppSettingsFile.create(template))
        .encryptedDataDetails(Collections.emptyList());
  }
}
