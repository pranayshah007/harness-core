/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.arm.deployment;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.context.ARMDeploymentSteadyStateContext;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.AzureARMTemplate;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.arm.ARMDeploymentSteadyStateChecker;
import io.harness.delegate.task.azure.arm.AzureARMDeploymentService;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentManagementGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentResourceGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentSubscriptionContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentTenantContext;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.microsoft.azure.management.resources.Deployment;
import com.microsoft.azure.management.resources.DeploymentPropertiesExtended;
import com.microsoft.azure.management.resources.ErrorResponse;
import com.microsoft.azure.management.resources.implementation.DeploymentExtendedInner;
import com.microsoft.azure.management.resources.implementation.DeploymentValidateResultInner;
import lombok.NonNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class AzureARMDeploymentServiceTest extends CategoryTest {
  public static final String CLIENT_ID = "CLIENT_ID";
  public static final String TENANT_ID = "TENANT_ID";
  public static final String KEY = "KEY";

  @Mock private AzureManagementClient azureManagementClient;
  @Mock private ARMDeploymentSteadyStateChecker deploymentSteadyStateChecker;
  @Mock private AzureLogCallbackProvider mockLogStreamingTaskClient;
  @Mock private LogCallback mockLogCallback;
  @Spy @InjectMocks AzureARMDeploymentService azureARMDeploymentService;

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
  public void testValidateTemplate() {
    String deploymentName = "DEPLOYMENT_NAME";
    AzureDeploymentMode mode = AzureDeploymentMode.INCREMENTAL;
    DeploymentResourceGroupContext context = DeploymentResourceGroupContext.builder()
                                                 .azureClientContext(getAzureClientContext())
                                                 .deploymentName(deploymentName)
                                                 .mode(mode)
                                                 .templateJson(basicTemplateJson)
                                                 .parametersJson(basicParametersJson)
                                                 .logStreamingTaskClient(mockLogStreamingTaskClient)
                                                 .build();

    DeploymentValidateResultInner mockDeploymentValidateResultInner = mock(DeploymentValidateResultInner.class);
    ErrorResponse mockErrorResponse = mockErrorResponse();
    doReturn(mockErrorResponse).when(mockDeploymentValidateResultInner).error();

    doReturn(mockDeploymentValidateResultInner)
        .when(azureManagementClient)
        .validateDeploymentAtResourceGroupScope(any(AzureClientContext.class), any(AzureARMTemplate.class));

    assertThatThrownBy(() -> azureARMDeploymentService.validateTemplate(context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Unable to deploy at resource group scope, deployment validation failed: Code: InvalidTemplate, Message: "
            + "Deployment template validation failed, Target:  /providers/Microsoft.Management/resource_id\n");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeployAtResourceGroupScope() {
    String deploymentName = "DEPLOYMENT_NAME";
    AzureDeploymentMode mode = AzureDeploymentMode.INCREMENTAL;
    DeploymentResourceGroupContext context = DeploymentResourceGroupContext.builder()
                                                 .azureClientContext(getAzureClientContext())
                                                 .deploymentName(deploymentName)
                                                 .mode(mode)
                                                 .templateJson(basicTemplateJson)
                                                 .parametersJson(basicParametersJson)
                                                 .logStreamingTaskClient(mockLogStreamingTaskClient)
                                                 .build();

    DeploymentValidateResultInner mockDeploymentValidateResultInner = mock(DeploymentValidateResultInner.class);
    Deployment mockDeployment = mock(Deployment.class);

    doReturn(mockDeploymentValidateResultInner)
        .when(azureManagementClient)
        .validateDeploymentAtResourceGroupScope(any(AzureClientContext.class), any(AzureARMTemplate.class));
    doReturn(mockDeployment)
        .when(azureManagementClient)
        .deployAtResourceGroupScope(any(AzureClientContext.class), any(AzureARMTemplate.class));
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureManagementClient)
        .getARMDeploymentOutputs(any(ARMDeploymentSteadyStateContext.class));

    String outputs = azureARMDeploymentService.deployAtResourceGroupScope(context);

    assertThat(outputs).isNotEmpty();
    assertThat(outputs).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeployAtSubscriptionScopeWithValidationError() {
    String deploymentName = "DEPLOYMENT_NAME";
    String subscriptionId = "SUBSCRIPTION_ID";
    AzureDeploymentMode mode = AzureDeploymentMode.INCREMENTAL;
    DeploymentSubscriptionContext context = DeploymentSubscriptionContext.builder()
                                                .subscriptionId(subscriptionId)
                                                .azureConfig(getAzureConfig())
                                                .deploymentName(deploymentName)
                                                .mode(mode)
                                                .templateJson(basicTemplateJson)
                                                .parametersJson(basicParametersJson)
                                                .logStreamingTaskClient(mockLogStreamingTaskClient)
                                                .build();

    DeploymentValidateResultInner mockDeploymentValidateResultInner = mock(DeploymentValidateResultInner.class);
    ErrorResponse mockErrorResponse = mockErrorResponse();
    doReturn(mockErrorResponse).when(mockDeploymentValidateResultInner).error();

    doReturn(mockDeploymentValidateResultInner)
        .when(azureManagementClient)
        .validateDeploymentAtSubscriptionScope(any(AzureConfig.class), eq(subscriptionId), any(AzureARMTemplate.class));

    assertThatThrownBy(() -> azureARMDeploymentService.deployAtSubscriptionScope(context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Unable to deploy at subscription scope, deployment validation failed: Code: InvalidTemplate, Message: "
            + "Deployment template validation failed, Target:  /providers/Microsoft.Management/resource_id\n");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeployAtSubscriptionScope() {
    String deploymentName = "DEPLOYMENT_NAME";
    String subscriptionId = "SUBSCRIPTION_ID";
    AzureDeploymentMode mode = AzureDeploymentMode.INCREMENTAL;
    DeploymentSubscriptionContext context = DeploymentSubscriptionContext.builder()
                                                .subscriptionId(subscriptionId)
                                                .azureConfig(getAzureConfig())
                                                .deploymentName(deploymentName)
                                                .mode(mode)
                                                .templateJson(basicTemplateJson)
                                                .parametersJson(basicParametersJson)
                                                .logStreamingTaskClient(mockLogStreamingTaskClient)
                                                .build();

    DeploymentValidateResultInner mockDeploymentValidateResultInner = mock(DeploymentValidateResultInner.class);
    DeploymentExtendedInner mockDeploymentExtendedInner = mock(DeploymentExtendedInner.class);
    DeploymentPropertiesExtended properties = mock(DeploymentPropertiesExtended.class);

    doReturn(mockDeploymentValidateResultInner)
        .when(azureManagementClient)
        .validateDeploymentAtSubscriptionScope(any(AzureConfig.class), eq(subscriptionId), any(AzureARMTemplate.class));
    doReturn(mockDeploymentExtendedInner)
        .when(azureManagementClient)
        .deployAtSubscriptionScope(any(AzureConfig.class), eq(subscriptionId), any(AzureARMTemplate.class));
    doReturn(properties).when(mockDeploymentExtendedInner).properties();
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureManagementClient)
        .getARMDeploymentOutputs(any(ARMDeploymentSteadyStateContext.class));

    String outputs = azureARMDeploymentService.deployAtSubscriptionScope(context);

    assertThat(outputs).isNotEmpty();
    assertThat(outputs).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeployAtManagementGroupScopeWithValidationError() {
    String deploymentName = "DEPLOYMENT_NAME";
    String groupId = "GROUP_ID";
    AzureDeploymentMode mode = AzureDeploymentMode.INCREMENTAL;
    DeploymentManagementGroupContext context = DeploymentManagementGroupContext.builder()
                                                   .managementGroupId(groupId)
                                                   .azureConfig(getAzureConfig())
                                                   .deploymentName(deploymentName)
                                                   .mode(mode)
                                                   .templateJson(basicTemplateJson)
                                                   .parametersJson(basicParametersJson)
                                                   .logStreamingTaskClient(mockLogStreamingTaskClient)
                                                   .build();

    DeploymentValidateResultInner mockDeploymentValidateResultInner = mock(DeploymentValidateResultInner.class);
    ErrorResponse mockErrorResponse = mockErrorResponse();
    doReturn(mockErrorResponse).when(mockDeploymentValidateResultInner).error();

    doReturn(mockDeploymentValidateResultInner)
        .when(azureManagementClient)
        .validateDeploymentAtManagementGroupScope(any(AzureConfig.class), eq(groupId), any(AzureARMTemplate.class));

    assertThatThrownBy(() -> azureARMDeploymentService.deployAtManagementGroupScope(context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Unable to deploy at management group scope, deployment validation failed: Code: InvalidTemplate, Message: "
            + "Deployment template validation failed, Target:  /providers/Microsoft.Management/resource_id\n");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeployAtManagementGroupScope() {
    String deploymentName = "DEPLOYMENT_NAME";
    String groupId = "GROUP_ID";
    AzureDeploymentMode mode = AzureDeploymentMode.INCREMENTAL;
    DeploymentManagementGroupContext context = DeploymentManagementGroupContext.builder()
                                                   .managementGroupId(groupId)
                                                   .azureConfig(getAzureConfig())
                                                   .deploymentName(deploymentName)
                                                   .mode(mode)
                                                   .templateJson(basicTemplateJson)
                                                   .parametersJson(basicParametersJson)
                                                   .logStreamingTaskClient(mockLogStreamingTaskClient)
                                                   .build();

    DeploymentValidateResultInner mockDeploymentValidateResultInner = mock(DeploymentValidateResultInner.class);
    DeploymentExtendedInner mockDeploymentExtendedInner = mock(DeploymentExtendedInner.class);
    DeploymentPropertiesExtended properties = mock(DeploymentPropertiesExtended.class);

    doReturn(mockDeploymentValidateResultInner)
        .when(azureManagementClient)
        .validateDeploymentAtManagementGroupScope(any(AzureConfig.class), eq(groupId), any(AzureARMTemplate.class));
    doReturn(mockDeploymentExtendedInner)
        .when(azureManagementClient)
        .deployAtManagementGroupScope(any(AzureConfig.class), eq(groupId), any(AzureARMTemplate.class));
    doReturn(properties).when(mockDeploymentExtendedInner).properties();
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureManagementClient)
        .getARMDeploymentOutputs(any(ARMDeploymentSteadyStateContext.class));

    String outputs = azureARMDeploymentService.deployAtManagementGroupScope(context);

    assertThat(outputs).isNotEmpty();
    assertThat(outputs).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeployAtTenantScopeWithValidationError() {
    String deploymentName = "DEPLOYMENT_NAME";
    AzureDeploymentMode mode = AzureDeploymentMode.INCREMENTAL;
    DeploymentTenantContext context = DeploymentTenantContext.builder()
                                          .azureConfig(getAzureConfig())
                                          .deploymentName(deploymentName)
                                          .mode(mode)
                                          .templateJson(basicTemplateJson)
                                          .parametersJson(basicParametersJson)
                                          .logStreamingTaskClient(mockLogStreamingTaskClient)
                                          .build();

    DeploymentValidateResultInner mockDeploymentValidateResultInner = mock(DeploymentValidateResultInner.class);
    ErrorResponse mockErrorResponse = mockErrorResponse();
    doReturn(mockErrorResponse).when(mockDeploymentValidateResultInner).error();

    doReturn(mockDeploymentValidateResultInner)
        .when(azureManagementClient)
        .validateDeploymentAtTenantScope(any(AzureConfig.class), any(AzureARMTemplate.class));

    assertThatThrownBy(() -> azureARMDeploymentService.deployAtTenantScope(context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unable to deploy at tenant scope, deployment validation failed: Code: InvalidTemplate, Message: "
            + "Deployment template validation failed, Target:  /providers/Microsoft.Management/resource_id\n");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeployAtTenantScope() {
    String deploymentName = "DEPLOYMENT_NAME";
    AzureDeploymentMode mode = AzureDeploymentMode.INCREMENTAL;
    DeploymentTenantContext context = DeploymentTenantContext.builder()
                                          .azureConfig(getAzureConfig())
                                          .deploymentName(deploymentName)
                                          .mode(mode)
                                          .templateJson(basicTemplateJson)
                                          .parametersJson(basicParametersJson)
                                          .logStreamingTaskClient(mockLogStreamingTaskClient)
                                          .build();

    DeploymentValidateResultInner mockDeploymentValidateResultInner = mock(DeploymentValidateResultInner.class);
    DeploymentExtendedInner mockDeploymentExtendedInner = mock(DeploymentExtendedInner.class);
    DeploymentPropertiesExtended properties = mock(DeploymentPropertiesExtended.class);

    doReturn(mockDeploymentValidateResultInner)
        .when(azureManagementClient)
        .validateDeploymentAtTenantScope(any(AzureConfig.class), any(AzureARMTemplate.class));
    doReturn(mockDeploymentExtendedInner)
        .when(azureManagementClient)
        .deployAtTenantScope(any(AzureConfig.class), any(AzureARMTemplate.class));
    doReturn(properties).when(mockDeploymentExtendedInner).properties();
    doReturn("{propertyName={type=String, value=propertyValue}}")
        .when(azureManagementClient)
        .getARMDeploymentOutputs(any(ARMDeploymentSteadyStateContext.class));

    String outputs = azureARMDeploymentService.deployAtTenantScope(context);

    assertThat(outputs).isNotEmpty();
    assertThat(outputs).isEqualTo("{propertyName={type=String, value=propertyValue}}");
  }

  private ErrorResponse mockErrorResponse() {
    ErrorResponse mockErrorResponse = mock(ErrorResponse.class);
    doReturn("InvalidTemplate").when(mockErrorResponse).code();
    doReturn("Deployment template validation failed").when(mockErrorResponse).message();
    doReturn(" /providers/Microsoft.Management/resource_id").when(mockErrorResponse).target();
    return mockErrorResponse;
  }

  private AzureClientContext getAzureClientContext() {
    @NonNull String subscriptionId = "SUBSCRIPTION_ID";
    @NonNull String resourceGroupName = "RESOURCE_GROUP_NAME";
    return new AzureClientContext(getAzureConfig(), subscriptionId, resourceGroupName);
  }

  private AzureConfig getAzureConfig() {
    return AzureConfig.builder().tenantId(TENANT_ID).clientId(CLIENT_ID).key(KEY.toCharArray()).build();
  }

  private final String basicTemplateJson = "{\n"
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

  private final String basicParametersJson = "{\n"
      + "  \"location\": \"westus\",\n"
      + "  \"sku\": {\n"
      + "    \"name\": \"Standard_LRS\"\n"
      + "  },\n"
      + "  \"kind\": \"StorageV2\",\n"
      + "  \"properties\": {}\n"
      + "}";
}
