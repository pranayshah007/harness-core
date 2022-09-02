/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentManagementGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentResourceGroupContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentSubscriptionContext;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentTenantContext;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class AzureARMBaseHelperImplTest extends CategoryTest {
  @InjectMocks private AzureARMBaseHelperImpl azureARMBaseHelperImpl;
  @Mock private AzureLogCallbackProvider mockLogStreamingTaskClient;
  @Mock private LogCallback mockLogCallback;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(mockLogCallback).when(mockLogStreamingTaskClient).obtainLogCallback(anyString());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString());
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testToDeploymentResourceGroupContext() {
    AzureConfig azureConfig = AzureConfig.builder()
                                  .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED)
                                  .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                  .build();
    AzureARMTaskNGParameters taskParams = AzureARMTaskNGParameters.builder()
                                              .accountId("accountId")
                                              .taskType(AzureARMTaskType.ARM_DEPLOYMENT)
                                              .timeoutInMs(10000)
                                              .encryptedDataDetails(Collections.emptyList())
                                              .connectorDTO(AzureConnectorDTO.builder().build())
                                              .scopeType(ARMScopeType.RESOURCE_GROUP)
                                              .subscriptionId("subscriptionId")
                                              .resourceGroupName("resourceGroupName")
                                              .deploymentMode(AzureDeploymentMode.INCREMENTAL)
                                              .deploymentName("deploymentName")
                                              .templateBody("templateBody")
                                              .parametersBody("parameters")
                                              .build();

    DeploymentResourceGroupContext context =
        azureARMBaseHelperImpl.toDeploymentResourceGroupContext(taskParams, azureConfig, mockLogStreamingTaskClient);
    assertThat(context.getParametersJson()).isEqualTo("parameters");
    assertThat(context.getDeploymentName()).isEqualTo("deploymentName");
    assertThat(context.getTemplateJson()).isEqualTo("templateBody");
    assertThat(context.getAzureClientContext().getResourceGroupName()).isEqualTo("resourceGroupName");
    assertThat(context.getAzureClientContext().getSubscriptionId()).isEqualTo("subscriptionId");
    assertThat(context.getMode()).isEqualTo(AzureDeploymentMode.INCREMENTAL);
    assertThat(context.getAzureClientContext().getAzureConfig().getAzureAuthenticationType())
        .isEqualTo(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void toDeploymentManagementGroupContext() {
    AzureConfig azureConfig = AzureConfig.builder()
                                  .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED)
                                  .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                  .build();
    AzureARMTaskNGParameters taskParams = AzureARMTaskNGParameters.builder()
                                              .accountId("accountId")
                                              .taskType(AzureARMTaskType.ARM_DEPLOYMENT)
                                              .encryptedDataDetails(Collections.emptyList())
                                              .timeoutInMs(10000)

                                              .connectorDTO(AzureConnectorDTO.builder().build())
                                              .scopeType(ARMScopeType.MANAGEMENT_GROUP)
                                              .subscriptionId("subscriptionId")
                                              .managementGroupId("managementGroupId")
                                              .deploymentMode(AzureDeploymentMode.INCREMENTAL)
                                              .deploymentName("deploymentName")
                                              .templateBody("templateBody")
                                              .parametersBody("parameters")
                                              .deploymentDataLocation("deploymentDataLocation")
                                              .build();

    DeploymentManagementGroupContext context =
        azureARMBaseHelperImpl.toDeploymentManagementGroupContext(taskParams, azureConfig, mockLogStreamingTaskClient);
    assertThat(context.getParametersJson()).isEqualTo("parameters");
    assertThat(context.getDeploymentName()).isEqualTo("deploymentName");
    assertThat(context.getTemplateJson()).isEqualTo("templateBody");
    assertThat(context.getDeploymentDataLocation()).isEqualTo("deploymentDataLocation");
    assertThat(context.getManagementGroupId()).isEqualTo("managementGroupId");
    assertThat(context.getAzureConfig().getAzureAuthenticationType())
        .isEqualTo(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED);
    assertThat(context.getMode()).isEqualTo(AzureDeploymentMode.INCREMENTAL);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testToDeploymentSubscriptionContext() {
    AzureConfig azureConfig = AzureConfig.builder()
                                  .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED)
                                  .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                  .build();
    AzureARMTaskNGParameters taskParams = AzureARMTaskNGParameters.builder()
                                              .accountId("accountId")
                                              .taskType(AzureARMTaskType.ARM_DEPLOYMENT)
                                              .encryptedDataDetails(Collections.emptyList())
                                              .timeoutInMs(10000)

                                              .connectorDTO(AzureConnectorDTO.builder().build())
                                              .scopeType(ARMScopeType.SUBSCRIPTION)
                                              .subscriptionId("subscriptionId")
                                              .managementGroupId("managementGroupId")
                                              .deploymentMode(AzureDeploymentMode.INCREMENTAL)
                                              .deploymentName("deploymentName")
                                              .templateBody("templateBody")
                                              .parametersBody("parameters")
                                              .deploymentDataLocation("deploymentDataLocation")
                                              .build();

    DeploymentSubscriptionContext context =
        azureARMBaseHelperImpl.toDeploymentSubscriptionContext(taskParams, azureConfig, mockLogStreamingTaskClient);
    assertThat(context.getParametersJson()).isEqualTo("parameters");
    assertThat(context.getDeploymentName()).isEqualTo("deploymentName");
    assertThat(context.getTemplateJson()).isEqualTo("templateBody");
    assertThat(context.getSubscriptionId()).isEqualTo("subscriptionId");
    assertThat(context.getDeploymentDataLocation()).isEqualTo("deploymentDataLocation");
    assertThat(context.getAzureConfig().getAzureAuthenticationType())
        .isEqualTo(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED);
    assertThat(context.getMode()).isEqualTo(AzureDeploymentMode.INCREMENTAL);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testToTenantContext() {
    AzureConfig azureConfig = AzureConfig.builder()
                                  .azureAuthenticationType(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED)
                                  .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                  .build();
    AzureARMTaskNGParameters taskParams = AzureARMTaskNGParameters.builder()
                                              .accountId("accountId")
                                              .taskType(AzureARMTaskType.ARM_DEPLOYMENT)
                                              .encryptedDataDetails(Collections.emptyList())
                                              .timeoutInMs(10000)
                                              .connectorDTO(AzureConnectorDTO.builder().build())
                                              .scopeType(ARMScopeType.TENANT)
                                              .subscriptionId("subscriptionId")
                                              .managementGroupId("managementGroupId")
                                              .deploymentMode(AzureDeploymentMode.INCREMENTAL)
                                              .deploymentName("deploymentName")
                                              .templateBody("templateBody")
                                              .parametersBody("parameters")
                                              .deploymentDataLocation("deploymentDataLocation")
                                              .build();

    DeploymentTenantContext context =
        azureARMBaseHelperImpl.toDeploymentTenantContext(taskParams, azureConfig, mockLogStreamingTaskClient);
    assertThat(context.getParametersJson()).isEqualTo("parameters");
    assertThat(context.getDeploymentName()).isEqualTo("deploymentName");
    assertThat(context.getTemplateJson()).isEqualTo("templateBody");
    assertThat(context.getDeploymentDataLocation()).isEqualTo("deploymentDataLocation");
    assertThat(context.getAzureConfig().getAzureAuthenticationType())
        .isEqualTo(AzureAuthenticationType.MANAGED_IDENTITY_USER_ASSIGNED);
    assertThat(context.getMode()).isEqualTo(AzureDeploymentMode.INCREMENTAL);
  }
}
