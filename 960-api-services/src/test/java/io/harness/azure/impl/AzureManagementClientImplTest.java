/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.azure.model.AzureConstants.DEPLOYMENT_DOES_NOT_EXIST_MANAGEMENT_GROUP;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_DOES_NOT_EXIST_RESOURCE_GROUP;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_DOES_NOT_EXIST_SUBSCRIPTION;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_DOES_NOT_EXIST_TENANT;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.azure.AzureClient;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.context.ARMDeploymentSteadyStateContext;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureARMRGTemplateExportOptions;
import io.harness.azure.model.AzureARMTemplate;
import io.harness.azure.model.AzureARMTemplate.AzureARMTemplateBuilder;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.category.element.UnitTests;
import io.harness.exception.runtime.azure.AzureARMManagementScopeException;
import io.harness.exception.runtime.azure.AzureARMResourceGroupScopeException;
import io.harness.exception.runtime.azure.AzureARMSubscriptionScopeException;
import io.harness.exception.runtime.azure.AzureARMTenantScopeException;
import io.harness.network.Http;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.util.concurrent.TimeLimiter;
import com.microsoft.azure.Page;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.Deployment;
import com.microsoft.azure.management.resources.DeploymentMode;
import com.microsoft.azure.management.resources.DeploymentPropertiesExtended;
import com.microsoft.azure.management.resources.Deployments;
import com.microsoft.azure.management.resources.Location;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.ResourceGroupExportResult;
import com.microsoft.azure.management.resources.ResourceGroupExportTemplateOptions;
import com.microsoft.azure.management.resources.ResourceGroups;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.management.resources.Subscriptions;
import com.microsoft.azure.management.resources.implementation.DeploymentExtendedInner;
import com.microsoft.azure.management.resources.implementation.DeploymentInner;
import com.microsoft.azure.management.resources.implementation.DeploymentValidateResultInner;
import com.microsoft.azure.management.resources.implementation.DeploymentsInner;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;
import com.microsoft.azure.management.resources.implementation.ResourceManager;
import com.microsoft.rest.LogLevel;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Azure.class, AzureClient.class, Http.class, ResourceManager.class, TimeLimiter.class})
@PowerMockIgnore({"javax.security.*", "javax.net.*"})
public class AzureManagementClientImplTest extends CategoryTest {
  public static final String CLIENT_ID = "CLIENT_ID";
  public static final String TENANT_ID = "TENANT_ID";
  public static final String KEY = "KEY";

  @Mock private Azure.Configurable configurable;
  @Mock private Azure.Authenticated authenticated;
  @Mock private Azure azure;

  @InjectMocks AzureManagementClientImpl azureManagementClient;

  @Before
  public void before() throws Exception {
    ApplicationTokenCredentials tokenCredentials = mock(ApplicationTokenCredentials.class);
    PowerMockito.whenNew(ApplicationTokenCredentials.class).withAnyArguments().thenReturn(tokenCredentials);
    when(tokenCredentials.getToken(anyString())).thenReturn("tokenValue");
    PowerMockito.mockStatic(Azure.class);
    when(Azure.configure()).thenReturn(configurable);
    when(configurable.withLogLevel(Matchers.any(LogLevel.class))).thenReturn(configurable);
    when(configurable.authenticate(Matchers.any(ApplicationTokenCredentials.class))).thenReturn(authenticated);
    when(authenticated.withSubscription(anyString())).thenReturn(azure);
    when(authenticated.withDefaultSubscription()).thenReturn(azure);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testListLocationsByDefaultSubscription() {
    Subscription mockSubscription = mock(Subscription.class);
    Location mockLocation = mock(Location.class);
    PagedList<Location> listLocations = getPageList();
    listLocations.add(mockLocation);

    doReturn(mockSubscription).when(azure).getCurrentSubscription();
    doReturn(listLocations).when(mockSubscription).listLocations();
    doReturn("West US").when(mockLocation).displayName();

    List<String> locations = azureManagementClient.listLocationsBySubscriptionId(getAzureConfig(), EMPTY);

    assertThat(locations).isNotNull();
    assertThat(locations.size()).isEqualTo(46);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testListLocationsBySubscription() {
    String subscriptionId = "SUBSCRIPTION_ID";

    Subscription mockSubscription = mock(Subscription.class);
    Subscriptions mockSubscriptions = mock(Subscriptions.class);
    Location mockLocation = mock(Location.class);
    PagedList<Location> listLocations = getPageList();
    listLocations.add(mockLocation);

    doReturn(mockSubscriptions).when(azure).subscriptions();
    doReturn(mockSubscription).when(mockSubscriptions).getById(subscriptionId);
    doReturn(listLocations).when(mockSubscription).listLocations();
    doReturn("West US").when(mockLocation).displayName();

    List<String> locations = azureManagementClient.listLocationsBySubscriptionId(getAzureConfig(), subscriptionId);

    assertThat(locations).isNotNull();
    assertThat(locations.size()).isEqualTo(1);
    assertThat(locations.get(0)).isEqualTo("West US");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testExportResourceGroupTemplateJSON() {
    String subscriptionId = "SUBSCRIPTION_ID";
    String resourceGroupName = "RESOURCE_GROUP_NAME";
    AzureARMRGTemplateExportOptions includeParameterDefaultValue =
        AzureARMRGTemplateExportOptions.INCLUDE_PARAMETER_DEFAULT_VALUE;

    ResourceGroups mockResourceGroups = mock(ResourceGroups.class);
    ResourceGroup mockResourceGroup = mock(ResourceGroup.class);
    ResourceGroupExportResult mockResourceGroupExportResult = mock(ResourceGroupExportResult.class);
    doReturn(mockResourceGroups).when(azure).resourceGroups();
    doReturn(mockResourceGroup).when(mockResourceGroups).getByName(resourceGroupName);
    doReturn(mockResourceGroupExportResult)
        .when(mockResourceGroup)
        .exportTemplate(ResourceGroupExportTemplateOptions.INCLUDE_PARAMETER_DEFAULT_VALUE);
    doReturn(resourceGroupExportTemplate).when(mockResourceGroupExportResult).templateJson();

    AzureClientContext azureClientContext = getAzureClientContext(subscriptionId, resourceGroupName);

    String exportedJSON =
        azureManagementClient.exportResourceGroupTemplateJSON(azureClientContext, includeParameterDefaultValue);

    assertThat(exportedJSON).isNotEmpty();
    assertThat(exportedJSON).contains("Microsoft.Storage/storageAccounts");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetDeploymentAtResourceGroup() {
    String subscriptionId = "SUBSCRIPTION_ID";
    String resourceGroupName = "RESOURCE_GROUP_NAME";
    String deploymentName = "DEPLOYMENT_NAME";

    Deployments mockDeployments = mock(Deployments.class);
    Deployment mockDeployment = mock(Deployment.class);
    doReturn(mockDeployments).when(azure).deployments();
    doReturn(mockDeployment).when(mockDeployments).getByResourceGroup(resourceGroupName, deploymentName);
    doReturn("Accepted").when(mockDeployment).provisioningState();

    AzureClientContext azureClientContext = getAzureClientContext(subscriptionId, resourceGroupName);
    Deployment deployment = azureManagementClient.getDeploymentAtResourceGroup(azureClientContext, deploymentName);

    assertThat(deployment).isNotNull();
    assertThat(deployment.provisioningState()).isEqualTo("Accepted");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testValidateDeploymentAtResourceGroupScope() {
    String subscriptionId = "SUBSCRIPTION_ID";
    String resourceGroupName = "RESOURCE_GROUP_NAME";
    String deploymentName = "DEPLOYMENT_NAME";
    AzureClientContext azureClientContext = getAzureClientContext(subscriptionId, resourceGroupName);
    AzureARMTemplate azureARMTemplate = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder(),
        accountTemplateJSONAtResourceGroupScope, accountTemplateJSONParamsAtResourceGroupScope);

    DeploymentsInner mockDeploymentsInner = mockDeploymentsInner();
    DeploymentValidateResultInner result = mockDeploymentValidateResultInner(
        accountTemplateJSONAtResourceGroupScope, accountTemplateJSONParamsAtResourceGroupScope);
    doReturn(result)
        .when(mockDeploymentsInner)
        .validate(eq(resourceGroupName), eq(deploymentName), any(DeploymentInner.class));

    DeploymentValidateResultInner deploymentValidateResult =
        azureManagementClient.validateDeploymentAtResourceGroupScope(azureClientContext, azureARMTemplate);

    assertThat(deploymentValidateResult).isNotNull();
    assertThat(deploymentValidateResult.error()).isNull();
    assertThat(deploymentValidateResult.properties()).isNotNull();
    assertThat(deploymentValidateResult.properties().template().toString())
        .contains("Microsoft.Storage/storageAccounts");
    assertThat(deploymentValidateResult.properties().parameters().toString()).contains("storageAccountType");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testDeployAtResourceGroupScope() throws IOException {
    String subscriptionId = "SUBSCRIPTION_ID";
    String resourceGroupName = "RESOURCE_GROUP_NAME";
    String deploymentName = "DEPLOYMENT_NAME";
    DeploymentMode mode = DeploymentMode.INCREMENTAL;
    AzureClientContext azureClientContext = getAzureClientContext(subscriptionId, resourceGroupName);
    AzureARMTemplate azureARMTemplate = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder(),
        accountTemplateJSONAtResourceGroupScope, accountTemplateJSONParamsAtResourceGroupScope);

    Deployments mockDeployments = mock(Deployments.class);
    Deployment.DefinitionStages.Blank mockBlank = mock(Deployment.DefinitionStages.Blank.class);
    Deployment.DefinitionStages.WithTemplate mockWithTemplate = mock(Deployment.DefinitionStages.WithTemplate.class);
    Deployment.DefinitionStages.WithParameters mockWithParameters =
        mock(Deployment.DefinitionStages.WithParameters.class);
    Deployment.DefinitionStages.WithMode mockWithMode = mock(Deployment.DefinitionStages.WithMode.class);
    Deployment.DefinitionStages.WithCreate mockWithCreate = mock(Deployment.DefinitionStages.WithCreate.class);
    Deployment mockDeployment =
        mockDeployment(accountTemplateJSONAtResourceGroupScope, accountTemplateJSONParamsAtResourceGroupScope);

    doReturn(mockDeployments).when(azure).deployments();
    doReturn(mockBlank).when(mockDeployments).define(deploymentName);
    doReturn(mockWithTemplate).when(mockBlank).withExistingResourceGroup(resourceGroupName);
    doReturn(mockWithParameters).when(mockWithTemplate).withTemplate(accountTemplateJSONAtResourceGroupScope);
    doReturn(mockWithMode).when(mockWithParameters).withParameters(accountTemplateJSONParamsAtResourceGroupScope);
    doReturn(mockWithCreate).when(mockWithMode).withMode(mode);
    doReturn(mockDeployment).when(mockWithCreate).beginCreate();

    Deployment deployment = azureManagementClient.deployAtResourceGroupScope(azureClientContext, azureARMTemplate);

    assertThat(deployment).isNotNull();
    assertThat(deployment.template()).isNotNull();
    assertThat(deployment.template().toString()).contains("Microsoft.Storage/storageAccounts");
    assertThat(deployment.parameters()).isNotNull();
    assertThat(deployment.parameters().toString()).contains("storageAccountType");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetDeploymentAtSubscriptionScope() {
    String subscriptionId = "SUBSCRIPTION_ID";
    String deploymentName = "DEPLOYMENT_NAME";

    DeploymentsInner mockDeploymentsInner = mockDeploymentsInner();
    DeploymentExtendedInner mockDeploymentExtendedInner =
        mockDeploymentExtendedInner(createNewRGAtSubscriptionScope, createNewRGAtSubscriptionScopeParameters);

    doReturn(mockDeploymentExtendedInner).when(mockDeploymentsInner).getAtSubscriptionScope(deploymentName);

    DeploymentExtendedInner deployment =
        azureManagementClient.getDeploymentAtSubscriptionScope(getAzureConfig(), subscriptionId, deploymentName);

    assertThat(deployment).isNotNull();
    assertThat(deployment.location()).isNotNull();
    assertThat(deployment.location()).contains("West US");
    assertThat(deployment.properties()).isNotNull();
    assertThat(deployment.properties().template().toString()).contains("Microsoft.Resources/resourceGroups");
    assertThat(deployment.properties().parameters().toString()).contains("rgName");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testValidateDeploymentAtSubscriptionScope() {
    String subscriptionId = "SUBSCRIPTION_ID";
    String deploymentName = "DEPLOYMENT_NAME";

    DeploymentsInner mockDeploymentsInner = mockDeploymentsInner();
    DeploymentValidateResultInner result = mockDeploymentValidateResultInner(
        accountTemplateJSONAtResourceGroupScope, accountTemplateJSONParamsAtResourceGroupScope);
    doReturn(result)
        .when(mockDeploymentsInner)
        .validateAtSubscriptionScope(eq(deploymentName), any(DeploymentInner.class));

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        createNewRGAtSubscriptionScope, createNewRGAtSubscriptionScopeParameters);

    DeploymentValidateResultInner deploymentValidateResult =
        azureManagementClient.validateDeploymentAtSubscriptionScope(getAzureConfig(), subscriptionId, template);

    assertThat(deploymentValidateResult).isNotNull();
    assertThat(deploymentValidateResult.error()).isNull();
    assertThat(deploymentValidateResult.properties()).isNotNull();
    assertThat(deploymentValidateResult.properties().template().toString())
        .contains("Microsoft.Storage/storageAccounts");
    assertThat(deploymentValidateResult.properties().parameters().toString()).contains("storageAccountType");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testDeployAtSubscriptionScope() {
    String subscriptionId = "SUBSCRIPTION_ID";
    String deploymentName = "DEPLOYMENT_NAME";

    DeploymentsInner mockDeploymentsInner = mockDeploymentsInner();
    DeploymentExtendedInner mockDeploymentExtendedInner =
        mockDeploymentExtendedInner(createNewRGAtSubscriptionScope, createNewRGAtSubscriptionScopeParameters);

    doReturn(mockDeploymentExtendedInner)
        .when(mockDeploymentsInner)
        .beginCreateOrUpdateAtSubscriptionScope(eq(deploymentName), any(DeploymentInner.class));

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        createNewRGAtSubscriptionScope, createNewRGAtSubscriptionScopeParameters);

    DeploymentExtendedInner deployment =
        azureManagementClient.deployAtSubscriptionScope(getAzureConfig(), subscriptionId, template);

    assertThat(deployment).isNotNull();
    assertThat(deployment.location()).isNotNull();
    assertThat(deployment.location()).contains("West US");
    assertThat(deployment.properties()).isNotNull();
    assertThat(deployment.properties().template().toString()).contains("Microsoft.Resources/resourceGroups");
    assertThat(deployment.properties().parameters().toString()).contains("rgName");
    assertThat(deployment.properties().outputs().toString())
        .isEqualTo("{storageAccountName={type=String, value=devarmtemplatessdn}}");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetDeploymentAtManagementScope() {
    String deploymentName = "DEPLOYMENT_NAME";
    String groupId = "GROUP_ID";

    DeploymentsInner mockDeploymentsInner = mockDeploymentsInner();
    DeploymentExtendedInner mockDeploymentExtendedInner = mockDeploymentExtendedInner(
        policyAssignmentsAtManagementGroupScope, policyAssignmentsAtManagementGroupScopeParams);

    doReturn(mockDeploymentExtendedInner).when(mockDeploymentsInner).getAtManagementGroupScope(groupId, deploymentName);

    DeploymentExtendedInner deployment =
        azureManagementClient.getDeploymentAtManagementScope(getAzureConfig(), groupId, deploymentName);

    assertThat(deployment).isNotNull();
    assertThat(deployment.location()).isNotNull();
    assertThat(deployment.location()).contains("West US");
    assertThat(deployment.properties()).isNotNull();
    assertThat(deployment.properties().template().toString()).contains("Microsoft.Authorization/policyDefinitions");
    assertThat(deployment.properties().parameters().toString()).contains("targetMG");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testValidateTemplateAtManagementGroupScope() {
    String deploymentName = "DEPLOYMENT_NAME";
    String groupId = "GROUP_ID";

    DeploymentsInner mockDeploymentsInner = mockDeploymentsInner();
    DeploymentValidateResultInner result = mockDeploymentValidateResultInner(
        policyAssignmentsAtManagementGroupScope, policyAssignmentsAtManagementGroupScopeParams);
    doReturn(result)
        .when(mockDeploymentsInner)
        .validateAtManagementGroupScope(eq(groupId), eq(deploymentName), any(DeploymentInner.class));

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        policyAssignmentsAtManagementGroupScope, policyAssignmentsAtManagementGroupScopeParams);

    DeploymentValidateResultInner deploymentValidateResult =
        azureManagementClient.validateDeploymentAtManagementGroupScope(getAzureConfig(), groupId, template);

    assertThat(deploymentValidateResult).isNotNull();
    assertThat(deploymentValidateResult.error()).isNull();
    assertThat(deploymentValidateResult.properties()).isNotNull();
    assertThat(deploymentValidateResult.properties().template().toString())
        .contains("Microsoft.Authorization/policyDefinitions");
    assertThat(deploymentValidateResult.properties().parameters().toString()).contains("targetMG");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testDeployAtManagementGroupScope() {
    String deploymentName = "DEPLOYMENT_NAME";
    String groupId = "GROUP_ID";

    DeploymentsInner mockDeploymentsInner = mockDeploymentsInner();
    DeploymentExtendedInner mockDeploymentExtendedInner = mockDeploymentExtendedInner(
        policyAssignmentsAtManagementGroupScope, policyAssignmentsAtManagementGroupScopeParams);

    doReturn(mockDeploymentExtendedInner)
        .when(mockDeploymentsInner)
        .beginCreateOrUpdateAtManagementGroupScope(eq("GROUP_ID"), eq(deploymentName), any(DeploymentInner.class));

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        policyAssignmentsAtManagementGroupScope, policyAssignmentsAtManagementGroupScopeParams);

    DeploymentExtendedInner deployment =
        azureManagementClient.deployAtManagementGroupScope(getAzureConfig(), groupId, template);

    assertThat(deployment).isNotNull();
    assertThat(deployment.location()).isNotNull();
    assertThat(deployment.location()).contains("West US");
    assertThat(deployment.properties()).isNotNull();
    assertThat(deployment.properties().template().toString()).contains("Microsoft.Authorization/policyDefinitions");
    assertThat(deployment.properties().parameters().toString()).contains("targetMG");
    assertThat(deployment.properties().outputs().toString())
        .isEqualTo("{storageAccountName={type=String, value=devarmtemplatessdn}}");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetDeploymentAtTenant() {
    String deploymentName = "DEPLOYMENT_NAME";

    DeploymentsInner mockDeploymentsInner = mockDeploymentsInner();
    DeploymentExtendedInner mockDeploymentExtendedInner =
        mockDeploymentExtendedInner(createNewMGAtTenantScope, createNewMGAtTenantScopeParams);

    doReturn(mockDeploymentExtendedInner).when(mockDeploymentsInner).getAtTenantScope(deploymentName);

    DeploymentExtendedInner deployment =
        azureManagementClient.getDeploymentAtTenantScope(getAzureConfig(), deploymentName);

    assertThat(deployment).isNotNull();
    assertThat(deployment.location()).isNotNull();
    assertThat(deployment.location()).contains("West US");
    assertThat(deployment.properties()).isNotNull();
    assertThat(deployment.properties().template().toString()).contains("Microsoft.Management/managementGroups");
    assertThat(deployment.properties().parameters().toString()).contains("mgName");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testValidateTemplateAtTenant() {
    String deploymentName = "DEPLOYMENT_NAME";

    DeploymentsInner mockDeploymentsInner = mockDeploymentsInner();
    DeploymentValidateResultInner result =
        mockDeploymentValidateResultInner(createNewMGAtTenantScope, createNewMGAtTenantScopeParams);
    doReturn(result).when(mockDeploymentsInner).validateAtTenantScope(eq(deploymentName), any(DeploymentInner.class));

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        createNewMGAtTenantScope, createNewMGAtTenantScopeParams);

    DeploymentValidateResultInner deploymentValidateResult =
        azureManagementClient.validateDeploymentAtTenantScope(getAzureConfig(), template);

    assertThat(deploymentValidateResult).isNotNull();
    assertThat(deploymentValidateResult.error()).isNull();
    assertThat(deploymentValidateResult.properties()).isNotNull();
    assertThat(deploymentValidateResult.properties().template().toString())
        .contains("Microsoft.Management/managementGroups");
    assertThat(deploymentValidateResult.properties().parameters().toString()).contains("mgName");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testDeployAtTenant() {
    String deploymentName = "DEPLOYMENT_NAME";

    DeploymentsInner mockDeploymentsInner = mockDeploymentsInner();
    DeploymentExtendedInner mockDeploymentExtendedInner =
        mockDeploymentExtendedInner(createNewMGAtTenantScope, createNewMGAtTenantScopeParams);

    doReturn(mockDeploymentExtendedInner)
        .when(mockDeploymentsInner)
        .beginCreateOrUpdateAtTenantScope(eq(deploymentName), any(DeploymentInner.class));

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        createNewMGAtTenantScope, createNewMGAtTenantScopeParams);

    DeploymentExtendedInner deployment = azureManagementClient.deployAtTenantScope(getAzureConfig(), template);

    assertThat(deployment).isNotNull();
    assertThat(deployment.location()).isNotNull();
    assertThat(deployment.location()).contains("West US");
    assertThat(deployment.properties()).isNotNull();
    assertThat(deployment.properties().template().toString()).contains("Microsoft.Management/managementGroups");
    assertThat(deployment.properties().parameters().toString()).contains("mgName");
    assertThat(deployment.properties().outputs().toString())
        .isEqualTo("{storageAccountName={type=String, value=devarmtemplatessdn}}");
  }

  @Test
  @Owner(developers = OwnerRule.ANIL)
  @Category(UnitTests.class)
  public void testGetARMDeploymentStatus() {
    String resourceGroup = "arm-rg";
    String subscriptionId = "subId";
    String managementGroup = "managementGroup";
    String tenantId = "tenantId";
    String deploymentName = "deploy";
    ARMDeploymentSteadyStateContext context = ARMDeploymentSteadyStateContext.builder()
                                                  .resourceGroup(resourceGroup)
                                                  .scopeType(ARMScopeType.RESOURCE_GROUP)
                                                  .azureConfig(getAzureConfig())
                                                  .subscriptionId(subscriptionId)
                                                  .deploymentName(deploymentName)
                                                  .build();

    DeploymentsInner mockDeploymentsInner = mockDeploymentsInner();
    DeploymentExtendedInner deploymentExtendedInner = mockDeploymentExtendedInnerForStatus();

    // resource group scope
    doReturn(true).when(mockDeploymentsInner).checkExistence(eq(resourceGroup), eq(deploymentName));
    doReturn(deploymentExtendedInner)
        .when(mockDeploymentsInner)
        .getByResourceGroup(eq(resourceGroup), eq(deploymentName));
    String deploymentStatus = azureManagementClient.getARMDeploymentStatus(context);
    assertThat(deploymentStatus.equalsIgnoreCase("Succeeded")).isTrue();
    doReturn(false).when(mockDeploymentsInner).checkExistence(eq(resourceGroup), eq(deploymentName));
    assertThatThrownBy(() -> azureManagementClient.getARMDeploymentStatus(context))
        .isInstanceOf(AzureARMResourceGroupScopeException.class)
        .hasMessageContaining(String.format(DEPLOYMENT_DOES_NOT_EXIST_RESOURCE_GROUP, deploymentName, resourceGroup));

    // subscription scope
    context.setScopeType(ARMScopeType.SUBSCRIPTION);
    doReturn(true).when(mockDeploymentsInner).checkExistenceAtSubscriptionScope(eq(deploymentName));
    doReturn(deploymentExtendedInner).when(mockDeploymentsInner).getAtSubscriptionScope(eq(deploymentName));
    deploymentStatus = azureManagementClient.getARMDeploymentStatus(context);
    assertThat(deploymentStatus.equalsIgnoreCase("Succeeded")).isTrue();
    doReturn(false).when(mockDeploymentsInner).checkExistenceAtSubscriptionScope(eq(deploymentName));
    assertThatThrownBy(() -> azureManagementClient.getARMDeploymentStatus(context))
        .isInstanceOf(AzureARMSubscriptionScopeException.class)
        .hasMessageContaining(String.format(DEPLOYMENT_DOES_NOT_EXIST_SUBSCRIPTION, deploymentName, subscriptionId));

    // management group scope
    context.setScopeType(ARMScopeType.MANAGEMENT_GROUP);
    context.setManagementGroupId(managementGroup);
    doReturn(true)
        .when(mockDeploymentsInner)
        .checkExistenceAtManagementGroupScope(eq(managementGroup), eq(deploymentName));
    doReturn(deploymentExtendedInner)
        .when(mockDeploymentsInner)
        .getAtManagementGroupScope(eq(managementGroup), eq(deploymentName));
    deploymentStatus = azureManagementClient.getARMDeploymentStatus(context);
    assertThat(deploymentStatus.equalsIgnoreCase("Succeeded")).isTrue();
    doReturn(false)
        .when(mockDeploymentsInner)
        .checkExistenceAtManagementGroupScope(eq(managementGroup), eq(deploymentName));
    assertThatThrownBy(() -> azureManagementClient.getARMDeploymentStatus(context))
        .isInstanceOf(AzureARMManagementScopeException.class)
        .hasMessageContaining(
            String.format(DEPLOYMENT_DOES_NOT_EXIST_MANAGEMENT_GROUP, deploymentName, managementGroup));

    // tenant scope
    context.setScopeType(ARMScopeType.TENANT);
    context.setTenantId(tenantId);
    doReturn(true).when(mockDeploymentsInner).checkExistenceAtTenantScope(eq(deploymentName));
    doReturn(deploymentExtendedInner).when(mockDeploymentsInner).getAtTenantScope(eq(deploymentName));
    deploymentStatus = azureManagementClient.getARMDeploymentStatus(context);
    assertThat(deploymentStatus.equalsIgnoreCase("Succeeded")).isTrue();
    doReturn(false).when(mockDeploymentsInner).checkExistenceAtTenantScope(eq(deploymentName));
    assertThatThrownBy(() -> azureManagementClient.getARMDeploymentStatus(context))
        .isInstanceOf(AzureARMTenantScopeException.class)
        .hasMessageContaining(String.format(DEPLOYMENT_DOES_NOT_EXIST_TENANT, deploymentName, tenantId));
  }

  @Test
  @Owner(developers = OwnerRule.ANIL)
  @Category(UnitTests.class)
  public void testGetARMDeploymentOutputs() {
    String resourceGroup = "arm-rg";
    String subscriptionId = "subId";
    String managementGroup = "managementGroup";
    String deploymentName = "deploy";

    ARMDeploymentSteadyStateContext context = ARMDeploymentSteadyStateContext.builder()
                                                  .resourceGroup(resourceGroup)
                                                  .scopeType(ARMScopeType.RESOURCE_GROUP)
                                                  .azureConfig(getAzureConfig())
                                                  .subscriptionId(subscriptionId)
                                                  .deploymentName(deploymentName)
                                                  .build();

    DeploymentsInner deploymentsInner = mockDeploymentsInner();
    DeploymentExtendedInner extendedInner = mockDeploymentExtendedInner("", "");

    doReturn(extendedInner).when(deploymentsInner).getByResourceGroup(eq(resourceGroup), eq(deploymentName));
    String armDeploymentOutputs = azureManagementClient.getARMDeploymentOutputs(context);
    assertThat(armDeploymentOutputs).isNotEmpty();

    doReturn(extendedInner).when(deploymentsInner).getAtSubscriptionScope(eq(deploymentName));
    armDeploymentOutputs = azureManagementClient.getARMDeploymentOutputs(context);
    assertThat(armDeploymentOutputs).isNotEmpty();

    doReturn(extendedInner).when(deploymentsInner).getAtManagementGroupScope(eq(managementGroup), eq(deploymentName));
    armDeploymentOutputs = azureManagementClient.getARMDeploymentOutputs(context);
    assertThat(armDeploymentOutputs).isNotEmpty();

    doReturn(extendedInner).when(deploymentsInner).getAtTenantScope(eq(deploymentName));
    armDeploymentOutputs = azureManagementClient.getARMDeploymentOutputs(context);
    assertThat(armDeploymentOutputs).isNotEmpty();
  }

  private DeploymentExtendedInner mockDeploymentExtendedInnerForStatus() {
    DeploymentExtendedInner mockDeploymentExtendedInner = mock(DeploymentExtendedInner.class);
    DeploymentPropertiesExtended deploymentPropertiesExtended = mock(DeploymentPropertiesExtended.class);
    doReturn(deploymentPropertiesExtended).when(mockDeploymentExtendedInner).properties();
    doReturn("Succeeded").when(deploymentPropertiesExtended).provisioningState();
    return mockDeploymentExtendedInner;
  }

  @NotNull
  private DeploymentExtendedInner mockDeploymentExtendedInner(String templateJSON, String parametersJSON) {
    DeploymentExtendedInner mockDeploymentExtendedInner = mock(DeploymentExtendedInner.class);
    doReturn("West US").when(mockDeploymentExtendedInner).location();

    DeploymentPropertiesExtended properties = new DeploymentPropertiesExtended();
    properties.withTemplate(templateJSON);
    properties.withParameters(parametersJSON);
    properties.withOutputs("{storageAccountName={type=String, value=devarmtemplatessdn}}");
    doReturn(properties).when(mockDeploymentExtendedInner).properties();

    return mockDeploymentExtendedInner;
  }

  @NotNull
  private AzureClientContext getAzureClientContext(String subscriptionId, String resourceGroupName) {
    return new AzureClientContext(getAzureConfig(), subscriptionId, resourceGroupName);
  }

  @NotNull
  private Deployment mockDeployment(String templateJSON, String parametersJSON) {
    Deployment mockDeployment = mock(Deployment.class);
    doReturn(templateJSON).when(mockDeployment).template();
    doReturn(parametersJSON).when(mockDeployment).parameters();
    doReturn("{storageAccountName={type=String, value=devarmtemplatessdn}}").when(mockDeployment).outputs();
    return mockDeployment;
  }

  @NotNull
  private DeploymentValidateResultInner mockDeploymentValidateResultInner(String templateJSON, String parametersJSON) {
    DeploymentValidateResultInner result = new DeploymentValidateResultInner();
    DeploymentPropertiesExtended properties = new DeploymentPropertiesExtended();
    properties.withTemplate(templateJSON);
    properties.withParameters(parametersJSON);
    result.withProperties(properties);
    return result;
  }

  private DeploymentsInner mockDeploymentsInner() {
    Deployments mockDeployments = mock(Deployments.class);
    ResourceManager mockResourceManager = PowerMockito.mock(ResourceManager.class);
    ResourceManagementClientImpl mockManagementClient = mock(ResourceManagementClientImpl.class);
    DeploymentsInner mockDeploymentsInner = mock(DeploymentsInner.class);

    doReturn(mockDeployments).when(azure).deployments();
    doReturn(mockResourceManager).when(mockDeployments).manager();
    doReturn(mockManagementClient).when(mockResourceManager).inner();
    doReturn(mockDeploymentsInner).when(mockManagementClient).deployments();
    return mockDeploymentsInner;
  }

  private AzureARMTemplate getAzureARMTemplate(
      String deploymentName, AzureARMTemplateBuilder builder, String templateJSON, String parametersJSON) {
    return builder.templateJSON(templateJSON)
        .deploymentMode(AzureDeploymentMode.INCREMENTAL)
        .deploymentName(deploymentName)
        .parametersJSON(parametersJSON)
        .build();
  }

  private AzureConfig getAzureConfig() {
    return AzureConfig.builder()
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .key(KEY.toCharArray())
        .clientId(CLIENT_ID)
        .tenantId(TENANT_ID)
        .build();
  }

  @NotNull
  public <T> PagedList<T> getPageList() {
    return new PagedList<T>() {
      @Override
      public Page<T> nextPage(String s) {
        return new Page<T>() {
          @Override
          public String nextPageLink() {
            return null;
          }
          @Override
          public List<T> items() {
            return null;
          }
        };
      }
    };
  }

  String resourceGroupExportTemplate = "{\n"
      + "    \"$schema\": \"https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#\",\n"
      + "    \"contentVersion\": \"1.0.0.0\",\n"
      + "    \"parameters\": {\n"
      + "        \"storageAccounts_rgarmatsubscopeoutsdn_name\": {\n"
      + "            \"defaultValue\": \"rgarmatsubscopeoutsdn\",\n"
      + "            \"type\": \"String\"\n"
      + "        }\n"
      + "    },\n"
      + "    \"variables\": {},\n"
      + "    \"resources\": [\n"
      + "        {\n"
      + "            \"type\": \"Microsoft.Storage/storageAccounts\",\n"
      + "            \"apiVersion\": \"2020-08-01-preview\",\n"
      + "            \"name\": \"[parameters('storageAccounts_rgarmatsubscopeoutsdn_name')]\",\n"
      + "            \"location\": \"eastus\",\n"
      + "            \"sku\": {\n"
      + "                \"name\": \"Standard_LRS\",\n"
      + "                \"tier\": \"Standard\"\n"
      + "            },\n"
      + "            \"kind\": \"StorageV2\",\n"
      + "            \"properties\": {\n"
      + "                \"networkAcls\": {\n"
      + "                    \"bypass\": \"AzureServices\",\n"
      + "                    \"virtualNetworkRules\": [],\n"
      + "                    \"ipRules\": [],\n"
      + "                    \"defaultAction\": \"Allow\"\n"
      + "                },\n"
      + "                \"supportsHttpsTrafficOnly\": false,\n"
      + "                \"encryption\": {\n"
      + "                    \"services\": {\n"
      + "                        \"file\": {\n"
      + "                            \"keyType\": \"Account\",\n"
      + "                            \"enabled\": true\n"
      + "                        },\n"
      + "                        \"blob\": {\n"
      + "                            \"keyType\": \"Account\",\n"
      + "                            \"enabled\": true\n"
      + "                        }\n"
      + "                    },\n"
      + "                    \"keySource\": \"Microsoft.Storage\"\n"
      + "                },\n"
      + "                \"accessTier\": \"Hot\"\n"
      + "            }\n"
      + "        }\n"
      + "    ]\n"
      + "}";

  String createNewMGAtTenantScope = "{\n"
      + "    \"$schema\": \"https://schema.management.azure.com/schemas/2019-08-01/tenantDeploymentTemplate.json#\",\n"
      + "    \"contentVersion\": \"1.0.0.0\",\n"
      + "    \"parameters\": {\n"
      + "      \"mgName\": {\n"
      + "        \"type\": \"string\",\n"
      + "        \"defaultValue\": \"[concat('mg-', uniqueString(newGuid()))]\"\n"
      + "      }\n"
      + "    },\n"
      + "    \"resources\": [\n"
      + "      {\n"
      + "        \"type\": \"Microsoft.Management/managementGroups\",\n"
      + "        \"apiVersion\": \"2020-02-01\",\n"
      + "        \"name\": \"[parameters('mgName')]\",\n"
      + "        \"properties\": {\n"
      + "        }\n"
      + "      }\n"
      + "    ]\n"
      + "  }\n";

  String createNewMGAtTenantScopeParams = "{\n"
      + "    \"mgName\": {\n"
      + "      \"value\": \"new-group-name\"\n"
      + "    }\n"
      + "  }";

  String createNewRGAtSubscriptionScope = "{\n"
      + "  \"$schema\": \"https://schema.management.azure.com/schemas/2018-05-01/subscriptionDeploymentTemplate.json#\",\n"
      + "  \"contentVersion\": \"1.0.0.0\",\n"
      + "  \"parameters\": {\n"
      + "    \"rgName\": {\n"
      + "      \"type\": \"string\",\n"
      + "      \"metadata\": {\n"
      + "        \"description\": \"Name of the resourceGroup to create\"\n"
      + "      }\n"
      + "    },\n"
      + "    \"rgLocation\": {\n"
      + "      \"type\": \"string\",\n"
      + "      \"defaultValue\": \"[deployment().location]\",\n"
      + "      \"metadata\": {\n"
      + "        \"description\": \"Location for the resourceGroup\"\n"
      + "      }\n"
      + "    }\n"
      + "  },\n"
      + "  \"resources\": [\n"
      + "    {\n"
      + "      \"type\": \"Microsoft.Resources/resourceGroups\",\n"
      + "      \"apiVersion\": \"2020-06-01\",\n"
      + "      \"name\": \"[parameters('rgName')]\",\n"
      + "      \"location\": \"[parameters('rgLocation')]\",\n"
      + "      \"tags\": {\n"
      + "        \"Note\": \"subscription level deployment\"\n"
      + "      },\n"
      + "      \"properties\": {}\n"
      + "    }\n"
      + "  ],\n"
      + "    \"outputs\": {\n"
      + "        \"output\": {\n"
      + "            \"type\": \"string\",\n"
      + "            \"value\": \"[parameters('rgName')]\"\n"
      + "        }\n"
      + "    }\n"
      + "}";

  String createNewRGAtSubscriptionScopeParameters = "{\n"
      + "    \"rgName\": {\n"
      + "      \"value\": \"rg-arm-at-sub-scope-out\"\n"
      + "    }"
      + "}";

  String policyAssignmentsAtManagementGroupScope = "{\n"
      + "  \"$schema\": \"https://schema.management.azure.com/schemas/2019-08-01/managementGroupDeploymentTemplate.json#\",\n"
      + "  \"contentVersion\": \"1.0.0.0\",\n"
      + "  \"parameters\": {\n"
      + "    \"targetMG\": {\n"
      + "      \"type\": \"string\",\n"
      + "      \"metadata\": {\n"
      + "        \"description\": \"Target Management Group\"\n"
      + "      }\n"
      + "    },\n"
      + "    \"allowedLocations\": {\n"
      + "      \"type\": \"array\",\n"
      + "      \"defaultValue\": [\n"
      + "        \"eastus\",\n"
      + "        \"westus\",\n"
      + "        \"centralus\"\n"
      + "      ],\n"
      + "      \"metadata\": {\n"
      + "        \"description\": \"An array of the allowed locations, all other locations will be denied by the created policy.\"\n"
      + "      }\n"
      + "    }\n"
      + "  },\n"
      + "  \"variables\": {\n"
      + "    \"mgScope\": \"[tenantResourceId('Microsoft.Management/managementGroups', parameters('targetMG'))]\",\n"
      + "    \"policyDefinition\": \"LocationRestriction\"\n"
      + "  },\n"
      + "  \"resources\": [\n"
      + "    {\n"
      + "      \"type\": \"Microsoft.Authorization/policyDefinitions\",\n"
      + "      \"name\": \"[variables('policyDefinition')]\",\n"
      + "      \"apiVersion\": \"2019-09-01\",\n"
      + "      \"properties\": {\n"
      + "        \"policyType\": \"Custom\",\n"
      + "        \"mode\": \"All\",\n"
      + "        \"parameters\": {\n"
      + "        },\n"
      + "        \"policyRule\": {\n"
      + "          \"if\": {\n"
      + "            \"not\": {\n"
      + "              \"field\": \"location\",\n"
      + "              \"in\": \"[parameters('allowedLocations')]\"\n"
      + "            }\n"
      + "          },\n"
      + "          \"then\": {\n"
      + "            \"effect\": \"deny\"\n"
      + "          }\n"
      + "        }\n"
      + "      }\n"
      + "    },\n"
      + "    {\n"
      + "      \"type\": \"Microsoft.Authorization/policyAssignments\",\n"
      + "      \"name\": \"location-lock\",\n"
      + "      \"apiVersion\": \"2019-09-01\",\n"
      + "      \"dependsOn\": [\n"
      + "        \"[variables('policyDefinition')]\"\n"
      + "      ],\n"
      + "      \"properties\": {\n"
      + "        \"scope\": \"[variables('mgScope')]\",\n"
      + "        \"policyDefinitionId\": \"[extensionResourceId(variables('mgScope'), 'Microsoft.Authorization/policyDefinitions', variables('policyDefinition'))]\"\n"
      + "      }\n"
      + "    }\n"
      + "  ]\n"
      + "}";

  String policyAssignmentsAtManagementGroupScopeParams = "{\n"
      + "    \"targetMG\": {\n"
      + "      \"value\": \"mg-testing1\"\n"
      + "    }\n"
      + "  }";

  String accountTemplateJSONAtResourceGroupScope = "{\n"
      + "    \"$schema\": \"https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#\",\n"
      + "    \"contentVersion\": \"1.0.0.0\",\n"
      + "    \"parameters\": {\n"
      + "      \"storageAccountType\": {\n"
      + "        \"type\": \"string\",\n"
      + "        \"defaultValue\": \"Standard_GRS\",\n"
      + "        \"allowedValues\": [\n"
      + "          \"Standard_LRS\",\n"
      + "          \"Standard_GRS\",\n"
      + "          \"Standard_ZRS\",\n"
      + "          \"Premium_LRS\"\n"
      + "        ],\n"
      + "        \"metadata\": {\n"
      + "          \"description\": \"Storage Account type\"\n"
      + "        }\n"
      + "      },\n"
      + "      \"location\": {\n"
      + "        \"type\": \"string\",\n"
      + "        \"defaultValue\": \"[resourceGroup().location]\",\n"
      + "        \"metadata\": {\n"
      + "          \"description\": \"Location for all resources.\"\n"
      + "        }\n"
      + "      }\n"
      + "    },\n"
      + "    \"variables\": {\n"
      + "      \"storageAccountName\": \"[concat(replace(toLower(resourceGroup().name),'-',''), 'sdn')]\"\n"
      + "    },\n"
      + "    \"resources\": [\n"
      + "      {\n"
      + "        \"type\": \"Microsoft.Storage/storageAccounts\",\n"
      + "        \"name\": \"[variables('storageAccountName')]\",\n"
      + "        \"location\": \"[parameters('location')]\",\n"
      + "        \"apiVersion\": \"2018-07-01\",\n"
      + "        \"sku\": {\n"
      + "          \"name\": \"[parameters('storageAccountType')]\"\n"
      + "        },\n"
      + "        \"kind\": \"StorageV2\",\n"
      + "        \"properties\": {}\n"
      + "      }\n"
      + "    ],\n"
      + "    \"outputs\": {\n"
      + "      \"storageAccountName\": {\n"
      + "        \"type\": \"string\",\n"
      + "        \"value\": \"[variables('storageAccountName')]\"\n"
      + "      }\n"
      + "    }\n"
      + "  }";

  String accountTemplateJSONParamsAtResourceGroupScope = "{\n"
      + "        \"storageAccountType\": {\n"
      + "            \"value\": \"Standard_LRS\"\n"
      + "        },\n"
      + "        \"location\": {\n"
      + "            \"value\": \"East US\"\n"
      + "        }\n"
      + "    }";
}
