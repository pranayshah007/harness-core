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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
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
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.management.polling.PollResult;
import com.azure.core.management.profile.AzureProfile;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.PollerFlux;
import com.azure.core.util.polling.SyncPoller;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.resources.fluent.DeploymentsClient;
import com.azure.resourcemanager.resources.fluent.ResourceManagementClient;
import com.azure.resourcemanager.resources.fluent.models.DeploymentExtendedInner;
import com.azure.resourcemanager.resources.fluent.models.DeploymentInner;
import com.azure.resourcemanager.resources.fluent.models.DeploymentValidateResultInner;
import com.azure.resourcemanager.resources.fluentcore.model.Accepted;
import com.azure.resourcemanager.resources.fluentcore.utils.PagedConverter;
import com.azure.resourcemanager.resources.models.Deployment;
import com.azure.resourcemanager.resources.models.DeploymentExportResult;
import com.azure.resourcemanager.resources.models.DeploymentMode;
import com.azure.resourcemanager.resources.models.DeploymentPropertiesExtended;
import com.azure.resourcemanager.resources.models.Deployments;
import com.azure.resourcemanager.resources.models.Location;
import com.azure.resourcemanager.resources.models.ProvisioningState;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.azure.resourcemanager.resources.models.ResourceGroupExportResult;
import com.azure.resourcemanager.resources.models.ResourceGroupExportTemplateOptions;
import com.azure.resourcemanager.resources.models.ResourceGroups;
import com.azure.resourcemanager.resources.models.ScopedDeployment;
import com.azure.resourcemanager.resources.models.Subscription;
import com.azure.resourcemanager.resources.models.Subscriptions;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

public class AzureManagementClientImplTest extends CategoryTest {
  public static final String CLIENT_ID = "CLIENT_ID";
  public static final String TENANT_ID = "TENANT_ID";
  public static final String KEY = "KEY";

  private AzureResourceManager.Configurable configurable;
  private AzureResourceManager.Authenticated authenticated;
  private AzureResourceManager azure;

  @InjectMocks AzureManagementClientImpl azureManagementClient;

  @Before
  public void before() throws Exception {
    MockitoAnnotations.openMocks(this);
    azure = mock(AzureResourceManager.class);
    configurable = mock(AzureResourceManager.Configurable.class);
    authenticated = mock(AzureResourceManager.Authenticated.class);

    MockedStatic<AzureResourceManager> azureMockStatic = mockStatic(AzureResourceManager.class);
    azureMockStatic.when(AzureResourceManager::configure).thenReturn(configurable);
    azureMockStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
        .thenReturn(authenticated);
    when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
    when(configurable.withHttpClient(any(HttpClient.class))).thenReturn(configurable);
    when(configurable.withRetryPolicy(any())).thenReturn(configurable);
    when(configurable.authenticate(any(), any())).thenReturn(authenticated);
    when(authenticated.withSubscription(anyString())).thenReturn(azure);
    when(authenticated.withDefaultSubscription()).thenReturn(azure);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testListLocationsByDefaultSubscription() {
    Location locationMock = mockLocation("West US");
    Subscription subscriptionMock = mockSubscription(null, Arrays.asList(locationMock));
    doReturn(subscriptionMock).when(azure).getCurrentSubscription();

    List<String> locations = azureManagementClient.listLocationsBySubscriptionId(getAzureConfig(), EMPTY);

    assertThat(locations).isNotNull();
    assertThat(locations.size()).isEqualTo(51);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testListLocationsBySubscription() {
    String subscriptionId = "SUBSCRIPTION_ID";

    Location locationMock = mockLocation("West US");
    Subscription subscriptionMock = mockSubscription(subscriptionId, Arrays.asList(locationMock));
    Subscriptions subscriptionsMock = mockSubscriptions(subscriptionMock);
    doReturn(subscriptionsMock).when(azure).subscriptions();

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

    ResourceGroupExportResult resourceGroupExportResultMock =
        mockResourceGroupExportResult(resourceGroupExportTemplate);
    ResourceGroup resourceGroupMock = mockResourceGroup(resourceGroupName,
        ResourceGroupExportTemplateOptions.INCLUDE_PARAMETER_DEFAULT_VALUE, resourceGroupExportResultMock);
    ResourceGroups resourceGroupsMock = mockResourceGroups(resourceGroupMock);
    doReturn(resourceGroupsMock).when(azure).resourceGroups();

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

    Deployment deploymentMock = mockDeployment("Accepted");
    Deployments deploymentsMock = mockDeployments(deploymentMock, Arrays.asList(deploymentMock));
    doReturn(deploymentMock).when(deploymentsMock).getByResourceGroup(resourceGroupName, deploymentName);
    doReturn(deploymentsMock).when(azure).deployments();

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

    DeploymentValidateResultInner result = mockDeploymentValidateResultInner(
        accountTemplateJSONAtResourceGroupScope, accountTemplateJSONParamsAtResourceGroupScope);

    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));

    doReturn(result)
        .when(deploymentsClientMock)
        .validate(eq(resourceGroupName), eq(deploymentName), any(DeploymentInner.class));

    DeploymentValidateResultInner deploymentValidateResult =
        azureManagementClient.validateDeploymentAtResourceGroupScope(
            azureClientContext, azureARMTemplate, deploymentsClientMock);

    assertThat(deploymentValidateResult).isNotNull();
    assertThat(deploymentValidateResult.error()).isNull();
    assertThat(deploymentValidateResult.properties()).isNotNull();
    assertThat(deploymentValidateResult.properties().parameters()).isNotNull();
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

    Deployment deploymentMock =
        mockDeployment(accountTemplateJSONAtResourceGroupScope, accountTemplateJSONParamsAtResourceGroupScope);
    Deployments mockDeployments = mockDeployments(deploymentMock, Arrays.asList(deploymentMock));
    Deployment.DefinitionStages.Blank mockBlank = mock(Deployment.DefinitionStages.Blank.class);
    Deployment.DefinitionStages.WithTemplate mockWithTemplate = mock(Deployment.DefinitionStages.WithTemplate.class);
    Deployment.DefinitionStages.WithParameters mockWithParameters =
        mock(Deployment.DefinitionStages.WithParameters.class);
    Deployment.DefinitionStages.WithMode mockWithMode = mock(Deployment.DefinitionStages.WithMode.class);
    Deployment.DefinitionStages.WithCreate mockWithCreate = mock(Deployment.DefinitionStages.WithCreate.class);

    doReturn(mockDeployments).when(azure).deployments();
    doReturn(mockBlank).when(mockDeployments).define(deploymentName);
    doReturn(mockWithTemplate).when(mockBlank).withExistingResourceGroup(resourceGroupName);
    doReturn(mockWithParameters).when(mockWithTemplate).withTemplate(accountTemplateJSONAtResourceGroupScope);
    doReturn(mockWithMode).when(mockWithParameters).withParameters(accountTemplateJSONParamsAtResourceGroupScope);
    doReturn(mockWithCreate).when(mockWithMode).withMode(mode);

    SyncPoller<Void, Deployment> deploymentSyncPollerMock = mock(SyncPoller.class);
    Accepted<Deployment> acceptedMock = mock(Accepted.class);
    doReturn(deploymentSyncPollerMock).when(acceptedMock).getSyncPoller();
    doReturn(deploymentMock).when(deploymentSyncPollerMock).getFinalResult();

    doReturn(acceptedMock).when(mockWithCreate).beginCreate();

    SyncPoller<Void, Deployment> deploymentSyncPoller =
        azureManagementClient.deployAtResourceGroupScope(azureClientContext, azureARMTemplate);

    assertThat(deploymentSyncPoller).isNotNull();
    Deployment deployment = deploymentSyncPoller.getFinalResult();
    assertThat(deployment.exportTemplate().template()).isNotNull();
    assertThat(deployment.exportTemplate().template().toString()).contains("Microsoft.Storage/storageAccounts");
    assertThat(deployment.parameters()).isNotNull();
    assertThat(deployment.parameters().toString()).contains("storageAccountType");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetDeploymentAtSubscriptionScope() {
    String subscriptionId = "SUBSCRIPTION_ID";
    String deploymentName = "DEPLOYMENT_NAME";

    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));

    DeploymentExtendedInner mockDeploymentExtendedInner =
        mockDeploymentExtendedInner(createNewRGAtSubscriptionScope, createNewRGAtSubscriptionScopeParameters);

    doReturn(mockDeploymentExtendedInner).when(deploymentsClientMock).getAtSubscriptionScope(deploymentName);

    DeploymentExtendedInner deployment = azureManagementClient.getDeploymentAtSubscriptionScope(
        getAzureConfig(), subscriptionId, deploymentName, deploymentsClientMock);

    assertThat(deployment).isNotNull();
    assertThat(deployment.location()).isNotNull();
    assertThat(deployment.location()).contains("West US");
    assertThat(deployment.properties()).isNotNull();
    assertThat(deployment.properties().parameters().toString()).contains("rgName");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testValidateDeploymentAtSubscriptionScope() {
    String subscriptionId = "SUBSCRIPTION_ID";
    String deploymentName = "DEPLOYMENT_NAME";
    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));

    DeploymentValidateResultInner result = mockDeploymentValidateResultInner(
        accountTemplateJSONAtResourceGroupScope, accountTemplateJSONParamsAtResourceGroupScope);
    doReturn(result)
        .when(deploymentsClientMock)
        .validateAtSubscriptionScope(eq(deploymentName), any(DeploymentInner.class));

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        createNewRGAtSubscriptionScope, createNewRGAtSubscriptionScopeParameters);

    DeploymentValidateResultInner deploymentValidateResult =
        azureManagementClient.validateDeploymentAtSubscriptionScope(
            getAzureConfig(), subscriptionId, template, deploymentsClientMock);

    assertThat(deploymentValidateResult).isNotNull();
    assertThat(deploymentValidateResult.error()).isNull();
    assertThat(deploymentValidateResult.properties()).isNotNull();
    assertThat(deploymentValidateResult.properties().parameters().toString()).contains("storageAccountType");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testDeployAtSubscriptionScope() {
    String subscriptionId = "SUBSCRIPTION_ID";
    String deploymentName = "DEPLOYMENT_NAME";
    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));

    PollResult<DeploymentExtendedInner> pollResultMock = mock(PollResult.class);

    DeploymentExtendedInner mockDeploymentExtendedInner =
        mockDeploymentExtendedInner(createNewRGAtSubscriptionScope, createNewRGAtSubscriptionScopeParameters);

    doReturn(mockDeploymentExtendedInner).when(pollResultMock).getValue();

    LongRunningOperationStatus longRunningOperationStatus = LongRunningOperationStatus.SUCCESSFULLY_COMPLETED;
    PollResponse<PollResult<DeploymentExtendedInner>> pollResponseMock = mock(PollResponse.class);
    doReturn(longRunningOperationStatus).when(pollResponseMock).getStatus();
    doReturn(pollResultMock).when(pollResponseMock).getValue();

    SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPollerMock = mock(SyncPoller.class);
    doReturn(pollResponseMock).when(syncPollerMock).waitForCompletion();

    PollerFlux<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> pollerFluxMock = mock(PollerFlux.class);
    doReturn(pollerFluxMock)
        .when(deploymentsClientMock)
        .beginCreateOrUpdateAtSubscriptionScopeAsync(eq(deploymentName), any(DeploymentInner.class));
    doReturn(syncPollerMock).when(pollerFluxMock).getSyncPoller();

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        createNewRGAtSubscriptionScope, createNewRGAtSubscriptionScopeParameters);

    SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPoller =
        azureManagementClient.deployAtSubscriptionScope(
            getAzureConfig(), subscriptionId, template, deploymentsClientMock);

    PollResponse<PollResult<DeploymentExtendedInner>> pollResponse = syncPoller.waitForCompletion();
    LongRunningOperationStatus status = pollResponse.getStatus();
    DeploymentExtendedInner deploymentExtendedInner = pollResponse.getValue().getValue();

    assertThat(status).isEqualTo(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED);
    assertThat(deploymentExtendedInner).isNotNull();
    assertThat(deploymentExtendedInner.location()).isNotNull();
    assertThat(deploymentExtendedInner.location()).contains("West US");
    assertThat(deploymentExtendedInner.properties()).isNotNull();
    assertThat(deploymentExtendedInner.properties().parameters().toString()).contains("rgName");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetDeploymentAtManagementScope() {
    String deploymentName = "DEPLOYMENT_NAME";
    String groupId = "GROUP_ID";
    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));

    DeploymentExtendedInner mockDeploymentExtendedInner = mockDeploymentExtendedInner(
        policyAssignmentsAtManagementGroupScope, policyAssignmentsAtManagementGroupScopeParams);

    doReturn(mockDeploymentExtendedInner)
        .when(deploymentsClientMock)
        .getAtManagementGroupScope(groupId, deploymentName);

    DeploymentExtendedInner deployment = azureManagementClient.getDeploymentAtManagementScope(
        getAzureConfig(), groupId, deploymentName, deploymentsClientMock);

    assertThat(deployment).isNotNull();
    assertThat(deployment.location()).isNotNull();
    assertThat(deployment.location()).contains("West US");
    assertThat(deployment.properties()).isNotNull();
    assertThat(deployment.properties().parameters().toString()).contains("targetMG");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testValidateTemplateAtManagementGroupScope() {
    String deploymentName = "DEPLOYMENT_NAME";
    String groupId = "GROUP_ID";
    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));

    DeploymentValidateResultInner result = mockDeploymentValidateResultInner(
        policyAssignmentsAtManagementGroupScope, policyAssignmentsAtManagementGroupScopeParams);
    doReturn(result)
        .when(deploymentsClientMock)
        .validateAtManagementGroupScope(eq(groupId), eq(deploymentName), any(ScopedDeployment.class));

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        policyAssignmentsAtManagementGroupScope, policyAssignmentsAtManagementGroupScopeParams);

    DeploymentValidateResultInner deploymentValidateResult =
        azureManagementClient.validateDeploymentAtManagementGroupScope(
            getAzureConfig(), groupId, template, deploymentsClientMock);

    assertThat(deploymentValidateResult).isNotNull();
    assertThat(deploymentValidateResult.error()).isNull();
    assertThat(deploymentValidateResult.properties()).isNotNull();
    assertThat(deploymentValidateResult.properties().parameters().toString()).contains("targetMG");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testDeployAtManagementGroupScope() {
    String deploymentName = "DEPLOYMENT_NAME";
    String groupId = "GROUP_ID";
    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));

    PollResult<DeploymentExtendedInner> pollResultMock = mock(PollResult.class);

    DeploymentExtendedInner mockDeploymentExtendedInner = mockDeploymentExtendedInner(
        policyAssignmentsAtManagementGroupScope, policyAssignmentsAtManagementGroupScopeParams);

    doReturn(mockDeploymentExtendedInner).when(pollResultMock).getValue();

    LongRunningOperationStatus longRunningOperationStatus = LongRunningOperationStatus.SUCCESSFULLY_COMPLETED;
    PollResponse<PollResult<DeploymentExtendedInner>> pollResponseMock = mock(PollResponse.class);
    doReturn(longRunningOperationStatus).when(pollResponseMock).getStatus();
    doReturn(pollResultMock).when(pollResponseMock).getValue();

    SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPollerMock = mock(SyncPoller.class);
    doReturn(pollResponseMock).when(syncPollerMock).waitForCompletion();

    PollerFlux<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> pollerFluxMock = mock(PollerFlux.class);
    doReturn(pollerFluxMock)
        .when(deploymentsClientMock)
        .beginCreateOrUpdateAtManagementGroupScopeAsync(
            eq("GROUP_ID"), eq(deploymentName), any(ScopedDeployment.class));
    doReturn(syncPollerMock).when(pollerFluxMock).getSyncPoller();

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        policyAssignmentsAtManagementGroupScope, policyAssignmentsAtManagementGroupScopeParams);

    SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPoller =
        azureManagementClient.deployAtManagementGroupScope(getAzureConfig(), groupId, template, deploymentsClientMock);

    PollResponse<PollResult<DeploymentExtendedInner>> pollResponse = syncPoller.waitForCompletion();
    LongRunningOperationStatus status = pollResponse.getStatus();
    DeploymentExtendedInner deploymentExtendedInner = pollResponse.getValue().getValue();

    assertThat(status).isEqualTo(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED);
    assertThat(deploymentExtendedInner).isNotNull();
    assertThat(deploymentExtendedInner.location()).isNotNull();
    assertThat(deploymentExtendedInner.location()).contains("West US");
    assertThat(deploymentExtendedInner.properties()).isNotNull();
    assertThat(deploymentExtendedInner.properties().parameters().toString()).contains("targetMG");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetDeploymentAtTenant() {
    String deploymentName = "DEPLOYMENT_NAME";
    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));

    DeploymentExtendedInner mockDeploymentExtendedInner =
        mockDeploymentExtendedInner(createNewMGAtTenantScope, createNewMGAtTenantScopeParams);

    doReturn(mockDeploymentExtendedInner).when(deploymentsClientMock).getAtTenantScope(deploymentName);

    DeploymentExtendedInner deployment =
        azureManagementClient.getDeploymentAtTenantScope(getAzureConfig(), deploymentName, deploymentsClientMock);

    assertThat(deployment).isNotNull();
    assertThat(deployment.location()).isNotNull();
    assertThat(deployment.location()).contains("West US");
    assertThat(deployment.properties()).isNotNull();
    assertThat(deployment.properties().parameters().toString()).contains("mgName");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testValidateTemplateAtTenant() {
    String deploymentName = "DEPLOYMENT_NAME";
    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));

    DeploymentValidateResultInner result =
        mockDeploymentValidateResultInner(createNewMGAtTenantScope, createNewMGAtTenantScopeParams);
    doReturn(result).when(deploymentsClientMock).validateAtTenantScope(eq(deploymentName), any(ScopedDeployment.class));

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        createNewMGAtTenantScope, createNewMGAtTenantScopeParams);

    DeploymentValidateResultInner deploymentValidateResult =
        azureManagementClient.validateDeploymentAtTenantScope(getAzureConfig(), template, deploymentsClientMock);

    assertThat(deploymentValidateResult).isNotNull();
    assertThat(deploymentValidateResult.error()).isNull();
    assertThat(deploymentValidateResult.properties()).isNotNull();
    assertThat(deploymentValidateResult.properties().parameters().toString()).contains("mgName");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testDeployAtTenant() {
    String deploymentName = "DEPLOYMENT_NAME";
    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));

    PollResult<DeploymentExtendedInner> pollResultMock = mock(PollResult.class);

    DeploymentExtendedInner mockDeploymentExtendedInner =
        mockDeploymentExtendedInner(createNewMGAtTenantScope, createNewMGAtTenantScopeParams);
    doReturn(mockDeploymentExtendedInner).when(pollResultMock).getValue();

    LongRunningOperationStatus longRunningOperationStatus = LongRunningOperationStatus.SUCCESSFULLY_COMPLETED;
    PollResponse<PollResult<DeploymentExtendedInner>> pollResponseMock = mock(PollResponse.class);
    doReturn(longRunningOperationStatus).when(pollResponseMock).getStatus();
    doReturn(pollResultMock).when(pollResponseMock).getValue();

    SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPollerMock = mock(SyncPoller.class);
    doReturn(pollResponseMock).when(syncPollerMock).waitForCompletion();

    PollerFlux<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> pollerFluxMock = mock(PollerFlux.class);
    doReturn(pollerFluxMock)
        .when(deploymentsClientMock)
        .beginCreateOrUpdateAtTenantScopeAsync(eq(deploymentName), any(ScopedDeployment.class));
    doReturn(syncPollerMock).when(pollerFluxMock).getSyncPoller();

    AzureARMTemplate template = getAzureARMTemplate(deploymentName, AzureARMTemplate.builder().location("East US"),
        createNewMGAtTenantScope, createNewMGAtTenantScopeParams);

    SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> syncPoller =
        azureManagementClient.deployAtTenantScope(getAzureConfig(), template, deploymentsClientMock);

    PollResponse<PollResult<DeploymentExtendedInner>> pollResponse = syncPoller.waitForCompletion();
    LongRunningOperationStatus status = pollResponse.getStatus();
    DeploymentExtendedInner deploymentExtendedInner = pollResponse.getValue().getValue();

    assertThat(status).isEqualTo(LongRunningOperationStatus.SUCCESSFULLY_COMPLETED);
    assertThat(deploymentExtendedInner).isNotNull();
    assertThat(deploymentExtendedInner.location()).isNotNull();
    assertThat(deploymentExtendedInner.location()).contains("West US");
    assertThat(deploymentExtendedInner.properties()).isNotNull();
    assertThat(deploymentExtendedInner.properties().parameters().toString()).contains("mgName");
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
    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));
    ARMDeploymentSteadyStateContext context = ARMDeploymentSteadyStateContext.builder()
                                                  .resourceGroup(resourceGroup)
                                                  .scopeType(ARMScopeType.RESOURCE_GROUP)
                                                  .azureConfig(getAzureConfig())
                                                  .subscriptionId(subscriptionId)
                                                  .deploymentName(deploymentName)
                                                  .build();

    DeploymentExtendedInner deploymentExtendedInner = mockDeploymentExtendedInnerForStatus();

    // resource group scope
    doReturn(true).when(deploymentsClientMock).checkExistence(eq(resourceGroup), eq(deploymentName));
    doReturn(deploymentExtendedInner)
        .when(deploymentsClientMock)
        .getByResourceGroup(eq(resourceGroup), eq(deploymentName));
    String deploymentStatus = azureManagementClient.getARMDeploymentStatus(context, deploymentsClientMock);
    assertThat(deploymentStatus.equalsIgnoreCase("Succeeded")).isTrue();
    doReturn(false).when(deploymentsClientMock).checkExistence(eq(resourceGroup), eq(deploymentName));
    assertThatThrownBy(() -> azureManagementClient.getARMDeploymentStatus(context, deploymentsClientMock))
        .isInstanceOf(AzureARMResourceGroupScopeException.class)
        .hasMessageContaining(String.format(DEPLOYMENT_DOES_NOT_EXIST_RESOURCE_GROUP, deploymentName, resourceGroup));

    // subscription scope
    context.setScopeType(ARMScopeType.SUBSCRIPTION);
    doReturn(true).when(deploymentsClientMock).checkExistenceAtSubscriptionScope(eq(deploymentName));
    doReturn(deploymentExtendedInner).when(deploymentsClientMock).getAtSubscriptionScope(eq(deploymentName));
    deploymentStatus = azureManagementClient.getARMDeploymentStatus(context, deploymentsClientMock);
    assertThat(deploymentStatus.equalsIgnoreCase("Succeeded")).isTrue();
    doReturn(false).when(deploymentsClientMock).checkExistenceAtSubscriptionScope(eq(deploymentName));
    assertThatThrownBy(() -> azureManagementClient.getARMDeploymentStatus(context, deploymentsClientMock))
        .isInstanceOf(AzureARMSubscriptionScopeException.class)
        .hasMessageContaining(String.format(DEPLOYMENT_DOES_NOT_EXIST_SUBSCRIPTION, deploymentName, subscriptionId));

    // management group scope
    context.setScopeType(ARMScopeType.MANAGEMENT_GROUP);
    context.setManagementGroupId(managementGroup);
    doReturn(true)
        .when(deploymentsClientMock)
        .checkExistenceAtManagementGroupScope(eq(managementGroup), eq(deploymentName));
    doReturn(deploymentExtendedInner)
        .when(deploymentsClientMock)
        .getAtManagementGroupScope(eq(managementGroup), eq(deploymentName));
    deploymentStatus = azureManagementClient.getARMDeploymentStatus(context, deploymentsClientMock);
    assertThat(deploymentStatus.equalsIgnoreCase("Succeeded")).isTrue();
    doReturn(false)
        .when(deploymentsClientMock)
        .checkExistenceAtManagementGroupScope(eq(managementGroup), eq(deploymentName));
    assertThatThrownBy(() -> azureManagementClient.getARMDeploymentStatus(context, deploymentsClientMock))
        .isInstanceOf(AzureARMManagementScopeException.class)
        .hasMessageContaining(
            String.format(DEPLOYMENT_DOES_NOT_EXIST_MANAGEMENT_GROUP, deploymentName, managementGroup));

    // tenant scope
    context.setScopeType(ARMScopeType.TENANT);
    context.setTenantId(tenantId);
    doReturn(true).when(deploymentsClientMock).checkExistenceAtTenantScope(eq(deploymentName));
    doReturn(deploymentExtendedInner).when(deploymentsClientMock).getAtTenantScope(eq(deploymentName));
    deploymentStatus = azureManagementClient.getARMDeploymentStatus(context, deploymentsClientMock);
    assertThat(deploymentStatus.equalsIgnoreCase("Succeeded")).isTrue();
    doReturn(false).when(deploymentsClientMock).checkExistenceAtTenantScope(eq(deploymentName));
    assertThatThrownBy(() -> azureManagementClient.getARMDeploymentStatus(context, deploymentsClientMock))
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
    DeploymentsClient deploymentsClientMock = mockDeploymentsClient(mock(ResourceManagementClient.class));
    ARMDeploymentSteadyStateContext context = ARMDeploymentSteadyStateContext.builder()
                                                  .resourceGroup(resourceGroup)
                                                  .scopeType(ARMScopeType.RESOURCE_GROUP)
                                                  .azureConfig(getAzureConfig())
                                                  .subscriptionId(subscriptionId)
                                                  .deploymentName(deploymentName)
                                                  .build();

    DeploymentExtendedInner extendedInner = mockDeploymentExtendedInner("", "");

    doReturn(extendedInner).when(deploymentsClientMock).getByResourceGroup(eq(resourceGroup), eq(deploymentName));
    String armDeploymentOutputs = azureManagementClient.getARMDeploymentOutputs(context, deploymentsClientMock);
    assertThat(armDeploymentOutputs).isNotEmpty();

    doReturn(extendedInner).when(deploymentsClientMock).getAtSubscriptionScope(eq(deploymentName));
    armDeploymentOutputs = azureManagementClient.getARMDeploymentOutputs(context, deploymentsClientMock);
    assertThat(armDeploymentOutputs).isNotEmpty();

    doReturn(extendedInner)
        .when(deploymentsClientMock)
        .getAtManagementGroupScope(eq(managementGroup), eq(deploymentName));
    armDeploymentOutputs = azureManagementClient.getARMDeploymentOutputs(context, deploymentsClientMock);
    assertThat(armDeploymentOutputs).isNotEmpty();

    doReturn(extendedInner).when(deploymentsClientMock).getAtTenantScope(eq(deploymentName));
    armDeploymentOutputs = azureManagementClient.getARMDeploymentOutputs(context, deploymentsClientMock);
    assertThat(armDeploymentOutputs).isNotEmpty();
  }

  private Deployments mockDeployments(Deployment deployment, List<Deployment> deployments) {
    Deployments deploymentsMock = mock(Deployments.class);

    if (deployment != null) {
      doReturn(deployment).when(deploymentsMock).getByResourceGroup(any(), any());
    }

    if (deployments != null) {
      Response list = new SimpleResponse(null, 200, null, deployments);
      doReturn(getPagedIterable(list)).when(deploymentsMock).list();
    }

    return deploymentsMock;
  }

  private Deployment mockDeployment(String provisioningState) {
    Deployment deploymentMock = mock(Deployment.class);

    if (provisioningState != null) {
      doReturn(provisioningState).when(deploymentMock).provisioningState();
    }

    return deploymentMock;
  }

  private ResourceGroups mockResourceGroups(ResourceGroup resourceGroupMock) {
    ResourceGroups resourceGroupsMock = mock(ResourceGroups.class);

    if (resourceGroupsMock != null) {
      doReturn(resourceGroupMock).when(resourceGroupsMock).getByName(any());
    }

    return resourceGroupsMock;
  }

  private ResourceGroup mockResourceGroup(String name, ResourceGroupExportTemplateOptions exportTemplateOptions,
      ResourceGroupExportResult resourceGroupExportResultMock) {
    ResourceGroup resourceGroupMock = mock(ResourceGroup.class);

    if (name != null) {
      doReturn(name).when(resourceGroupMock).name();
    }

    if (exportTemplateOptions != null && resourceGroupExportResultMock != null) {
      doReturn(resourceGroupExportResultMock).when(resourceGroupMock).exportTemplate(exportTemplateOptions);
    }

    return resourceGroupMock;
  }

  private ResourceGroupExportResult mockResourceGroupExportResult(String templateJson) {
    ResourceGroupExportResult resourceGroupExportResultMock = mock(ResourceGroupExportResult.class);

    if (templateJson != null) {
      doReturn(templateJson).when(resourceGroupExportResultMock).templateJson();
    }

    return resourceGroupExportResultMock;
  }

  private Subscriptions mockSubscriptions(Subscription subscriptionMock) {
    Subscriptions subscriptionsMock = mock(Subscriptions.class);

    if (subscriptionsMock != null) {
      doReturn(subscriptionMock).when(subscriptionsMock).getById(any());
    }

    return subscriptionsMock;
  }

  private Subscription mockSubscription(String id, List<Location> locations) {
    Subscription subscriptionMock = mock(Subscription.class);

    if (id != null) {
      doReturn(id).when(subscriptionMock).subscriptionId();
    }

    if (locations != null) {
      Response list = new SimpleResponse(null, 200, null, locations);
      doReturn(getPagedIterable(list)).when(subscriptionMock).listLocations();
    }

    return subscriptionMock;
  }

  private Location mockLocation(String displayName) {
    Location locationMock = mock(Location.class);

    if (displayName != null) {
      doReturn(displayName).when(locationMock).displayName();
    }

    return locationMock;
  }

  private DeploymentExtendedInner mockDeploymentExtendedInnerForStatus() {
    DeploymentExtendedInner mockDeploymentExtendedInner = mock(DeploymentExtendedInner.class);
    DeploymentPropertiesExtended deploymentPropertiesExtended = mock(DeploymentPropertiesExtended.class);
    doReturn(deploymentPropertiesExtended).when(mockDeploymentExtendedInner).properties();
    ProvisioningState provisioningStateMock = mock(ProvisioningState.class);
    doReturn("Succeeded").when(provisioningStateMock).toString();
    doReturn(provisioningStateMock).when(deploymentPropertiesExtended).provisioningState();
    return mockDeploymentExtendedInner;
  }

  @NotNull
  private DeploymentExtendedInner mockDeploymentExtendedInner(String templateJSON, String parametersJSON) {
    DeploymentExtendedInner mockDeploymentExtendedInner = mock(DeploymentExtendedInner.class);
    doReturn("West US").when(mockDeploymentExtendedInner).location();

    DeploymentPropertiesExtended mockDeploymentPropertiesExtended = mock(DeploymentPropertiesExtended.class);
    doReturn(parametersJSON).when(mockDeploymentPropertiesExtended).parameters();
    doReturn("{storageAccountName={type=String, value=devarmtemplatessdn}}")
        .when(mockDeploymentPropertiesExtended)
        .outputs();
    doReturn(mockDeploymentPropertiesExtended).when(mockDeploymentExtendedInner).properties();

    return mockDeploymentExtendedInner;
  }

  @NotNull
  private AzureClientContext getAzureClientContext(String subscriptionId, String resourceGroupName) {
    return new AzureClientContext(getAzureConfig(), subscriptionId, resourceGroupName);
  }

  @NotNull
  private Deployment mockDeployment(String templateJSON, String parametersJSON) {
    Deployment mockDeployment = mock(Deployment.class);
    DeploymentExportResult mockDeploymentExportResult = mock(DeploymentExportResult.class);
    doReturn(templateJSON).when(mockDeploymentExportResult).template();
    doReturn(mockDeploymentExportResult).when(mockDeployment).exportTemplate();
    doReturn(parametersJSON).when(mockDeployment).parameters();
    doReturn("{storageAccountName={type=String, value=devarmtemplatessdn}}").when(mockDeployment).outputs();
    return mockDeployment;
  }

  @NotNull
  private DeploymentValidateResultInner mockDeploymentValidateResultInner(String templateJSON, String parametersJSON) {
    DeploymentPropertiesExtended deploymentPropertiesExtendedMock = mock(DeploymentPropertiesExtended.class);
    doReturn(parametersJSON).when(deploymentPropertiesExtendedMock).parameters();

    DeploymentValidateResultInner deploymentValidateResultInnerMock = mock(DeploymentValidateResultInner.class);
    doReturn(deploymentPropertiesExtendedMock).when(deploymentValidateResultInnerMock).properties();

    return deploymentValidateResultInnerMock;
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
  public <T> PagedIterable<T> getPagedIterable(Response<List<T>> response) {
    return new PagedIterable<T>(PagedConverter.convertListToPagedFlux(Mono.just(response)));
  }

  private DeploymentsClient mockDeploymentsClient(ResourceManagementClient resourceManagementClientMock) {
    DeploymentsClient deploymentsClient = mock(DeploymentsClient.class);
    when(resourceManagementClientMock.getDeployments()).thenReturn(deploymentsClient);
    return deploymentsClient;
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
