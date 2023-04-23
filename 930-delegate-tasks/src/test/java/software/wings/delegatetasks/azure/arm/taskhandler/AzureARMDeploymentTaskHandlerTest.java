/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.arm.taskhandler;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.arm.AzureARMDeploymentService;
import io.harness.delegate.task.azure.arm.AzureARMTaskParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskResponse;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentManagementGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentResourceGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentSubscriptionContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentTenantContext;
import io.harness.delegate.task.azure.arm.request.AzureARMDeploymentParameters;
import io.harness.delegate.task.azure.arm.request.AzureARMDeploymentParameters.AzureARMDeploymentParametersBuilder;
import io.harness.delegate.task.azure.arm.response.AzureARMDeploymentResponse;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class AzureARMDeploymentTaskHandlerTest extends CategoryTest {
  public static final String CLIENT_ID = "CLIENT_ID";
  public static final String TENANT_ID = "TENANT_ID";
  public static final String KEY = "KEY";

  @Mock private AzureLogCallbackProvider mockLogStreamingTaskClient;
  @Mock private LogCallback mockLogCallback;
  @Mock private AzureARMDeploymentService azureARMDeploymentService;

  @Spy @InjectMocks AzureARMDeploymentTaskHandler azureARMDeploymentTaskHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(mockLogCallback).when(mockLogStreamingTaskClient).obtainLogCallback(anyString());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalAtResourceGroupScope() {
    AzureConfig azureConfig = buildAzureConfig();

    AzureARMTaskParameters azureARMTaskParameters = getAzureARMTaskParametersAtResourceGroupScope();

    ArgumentCaptor<DeploymentResourceGroupContext> deploymentContextCaptor =
        ArgumentCaptor.forClass(DeploymentResourceGroupContext.class);
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureARMDeploymentService)
        .deployAtResourceGroupScope(any());

    AzureARMTaskResponse azureARMTaskResponse = azureARMDeploymentTaskHandler.executeTaskInternal(
        azureARMTaskParameters, azureConfig, mockLogStreamingTaskClient);

    verify(azureARMDeploymentService, times(1)).deployAtResourceGroupScope(deploymentContextCaptor.capture());

    DeploymentResourceGroupContext capturedDeploymentResourceGroupContext = deploymentContextCaptor.getValue();
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

    assertThat(azureARMTaskResponse).isNotNull();
    assertThat(azureARMTaskResponse).isInstanceOf(AzureARMDeploymentResponse.class);
    AzureARMDeploymentResponse deploymentResponse = (AzureARMDeploymentResponse) azureARMTaskResponse;
    assertThat(deploymentResponse.getOutputs()).isNotEmpty();
    assertThat(deploymentResponse.getOutputs()).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalAtSubscriptionScope() {
    AzureConfig azureConfig = buildAzureConfig();

    AzureARMTaskParameters azureARMTaskParameters = getAzureARMTaskParametersAtSubscriptionScope();

    ArgumentCaptor<DeploymentSubscriptionContext> deploymentContextCaptor =
        ArgumentCaptor.forClass(DeploymentSubscriptionContext.class);
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureARMDeploymentService)
        .deployAtSubscriptionScope(any());

    AzureARMTaskResponse azureARMTaskResponse = azureARMDeploymentTaskHandler.executeTaskInternal(
        azureARMTaskParameters, azureConfig, mockLogStreamingTaskClient);

    verify(azureARMDeploymentService, times(1)).deployAtSubscriptionScope(deploymentContextCaptor.capture());

    DeploymentSubscriptionContext capturedDeploymentSubscriptionContext = deploymentContextCaptor.getValue();
    assertThat(capturedDeploymentSubscriptionContext).isNotNull();
    assertThat(capturedDeploymentSubscriptionContext.getDeploymentDataLocation()).isEqualTo("LOCATION");
    assertThat(capturedDeploymentSubscriptionContext.getSubscriptionId()).isEqualTo("SUBSCRIPTION_ID");
    assertThat(capturedDeploymentSubscriptionContext.getDeploymentName()).isEqualTo("DEPLOYMENT_NAME");
    assertThat(capturedDeploymentSubscriptionContext.getTemplateJson()).contains("Microsoft.Storage/storageAccounts");
    assertThat(capturedDeploymentSubscriptionContext.getParametersJson()).contains("Standard_LRS");
    assertThat(capturedDeploymentSubscriptionContext.getMode()).isEqualTo(AzureDeploymentMode.INCREMENTAL);

    assertThat(azureARMTaskResponse).isNotNull();
    assertThat(azureARMTaskResponse).isInstanceOf(AzureARMDeploymentResponse.class);
    AzureARMDeploymentResponse deploymentResponse = (AzureARMDeploymentResponse) azureARMTaskResponse;
    assertThat(deploymentResponse.getOutputs()).isNotEmpty();
    assertThat(deploymentResponse.getOutputs()).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalAtManagementGroupScope() {
    AzureConfig azureConfig = buildAzureConfig();

    AzureARMTaskParameters azureARMTaskParameters = getAzureARMTaskParametersAtManagementGroupScope();

    ArgumentCaptor<DeploymentManagementGroupContext> deploymentContextCaptor =
        ArgumentCaptor.forClass(DeploymentManagementGroupContext.class);
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureARMDeploymentService)
        .deployAtManagementGroupScope(any());

    AzureARMTaskResponse azureARMTaskResponse = azureARMDeploymentTaskHandler.executeTaskInternal(
        azureARMTaskParameters, azureConfig, mockLogStreamingTaskClient);

    verify(azureARMDeploymentService, times(1)).deployAtManagementGroupScope(deploymentContextCaptor.capture());

    DeploymentManagementGroupContext capturedDeploymentManagementGroupContext = deploymentContextCaptor.getValue();
    assertThat(capturedDeploymentManagementGroupContext).isNotNull();
    assertThat(capturedDeploymentManagementGroupContext.getDeploymentDataLocation()).isEqualTo("LOCATION");
    assertThat(capturedDeploymentManagementGroupContext.getManagementGroupId()).isEqualTo("GROUP_ID");
    assertThat(capturedDeploymentManagementGroupContext.getDeploymentName()).isEqualTo("DEPLOYMENT_NAME");
    assertThat(capturedDeploymentManagementGroupContext.getTemplateJson())
        .contains("Microsoft.Storage/storageAccounts");
    assertThat(capturedDeploymentManagementGroupContext.getParametersJson()).contains("Standard_LRS");
    assertThat(capturedDeploymentManagementGroupContext.getMode()).isEqualTo(AzureDeploymentMode.INCREMENTAL);

    assertThat(azureARMTaskResponse).isNotNull();
    assertThat(azureARMTaskResponse).isInstanceOf(AzureARMDeploymentResponse.class);
    AzureARMDeploymentResponse deploymentResponse = (AzureARMDeploymentResponse) azureARMTaskResponse;
    assertThat(deploymentResponse.getOutputs()).isNotEmpty();
    assertThat(deploymentResponse.getOutputs()).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalAtTenantScope() {
    AzureConfig azureConfig = buildAzureConfig();

    AzureARMTaskParameters azureARMTaskParameters = getAzureARMTaskParametersAtTenantScope();

    ArgumentCaptor<DeploymentTenantContext> deploymentContextCaptor =
        ArgumentCaptor.forClass(DeploymentTenantContext.class);
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureARMDeploymentService)
        .deployAtTenantScope(any());

    AzureARMTaskResponse azureARMTaskResponse = azureARMDeploymentTaskHandler.executeTaskInternal(
        azureARMTaskParameters, azureConfig, mockLogStreamingTaskClient);

    verify(azureARMDeploymentService, times(1)).deployAtTenantScope(deploymentContextCaptor.capture());

    DeploymentTenantContext capturedDeploymentTenantContext = deploymentContextCaptor.getValue();

    assertThat(capturedDeploymentTenantContext).isNotNull();
    assertThat(capturedDeploymentTenantContext.getDeploymentDataLocation()).isEqualTo("LOCATION");
    assertThat(capturedDeploymentTenantContext.getDeploymentName()).isEqualTo("DEPLOYMENT_NAME");
    assertThat(capturedDeploymentTenantContext.getTemplateJson()).contains("Microsoft.Storage/storageAccounts");
    assertThat(capturedDeploymentTenantContext.getParametersJson()).contains("Standard_LRS");
    assertThat(capturedDeploymentTenantContext.getMode()).isEqualTo(AzureDeploymentMode.INCREMENTAL);

    assertThat(azureARMTaskResponse).isNotNull();
    assertThat(azureARMTaskResponse).isInstanceOf(AzureARMDeploymentResponse.class);
    AzureARMDeploymentResponse deploymentResponse = (AzureARMDeploymentResponse) azureARMTaskResponse;
    assertThat(deploymentResponse.getOutputs()).isNotEmpty();
    assertThat(deploymentResponse.getOutputs()).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  private AzureARMTaskParameters getAzureARMTaskParametersAtResourceGroupScope() {
    return getAzureARMDeploymentParametersBuilder()
        .deploymentScope(ARMScopeType.RESOURCE_GROUP)
        .subscriptionId("SUBSCRIPTION_ID")
        .resourceGroupName("RESOURCE_GROUP_NAME")
        .build();
  }

  private AzureARMTaskParameters getAzureARMTaskParametersAtSubscriptionScope() {
    return getAzureARMDeploymentParametersBuilder()
        .deploymentScope(ARMScopeType.SUBSCRIPTION)
        .subscriptionId("SUBSCRIPTION_ID")
        .build();
  }

  private AzureARMTaskParameters getAzureARMTaskParametersAtManagementGroupScope() {
    return getAzureARMDeploymentParametersBuilder()
        .deploymentScope(ARMScopeType.MANAGEMENT_GROUP)
        .managementGroupId("GROUP_ID")
        .build();
  }

  private AzureARMTaskParameters getAzureARMTaskParametersAtTenantScope() {
    return getAzureARMDeploymentParametersBuilder().deploymentScope(ARMScopeType.TENANT).build();
  }

  private AzureARMDeploymentParametersBuilder getAzureARMDeploymentParametersBuilder() {
    String basicTemplateJson = "{\n"
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

    String basicParametersJson = "{\n"
        + "  \"location\": \"westus\",\n"
        + "  \"sku\": {\n"
        + "    \"name\": \"Standard_LRS\"\n"
        + "  },\n"
        + "  \"kind\": \"StorageV2\",\n"
        + "  \"properties\": {}\n"
        + "}";

    return AzureARMDeploymentParameters.builder()
        .accountId("ACCOUNT_ID")
        .activityId("ACTIVITY_ID")
        .appId("APP_ID")
        .deploymentDataLocation("LOCATION")
        .deploymentMode(AzureDeploymentMode.INCREMENTAL)
        .deploymentName("DEPLOYMENT_NAME")
        .timeoutIntervalInMin(10)
        .templateJson(basicTemplateJson)
        .parametersJson(basicParametersJson);
  }

  private AzureConfig buildAzureConfig() {
    return AzureConfig.builder().clientId(CLIENT_ID).key(KEY.toCharArray()).tenantId(TENANT_ID).build();
  }
}
