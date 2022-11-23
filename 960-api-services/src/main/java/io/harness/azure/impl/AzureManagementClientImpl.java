/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.azure.model.AzureConstants.DEPLOYMENT_DOES_NOT_EXIST_MANAGEMENT_GROUP;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_DOES_NOT_EXIST_RESOURCE_GROUP;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_DOES_NOT_EXIST_SUBSCRIPTION;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_DOES_NOT_EXIST_TENANT;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_NAME_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.LOCATION_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.LOCATION_SET_AT_RESOURCE_GROUP_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.MANAGEMENT_GROUP_ID_BLANK_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.SUBSCRIPTION_ID_NULL_VALIDATION_MSG;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.azure.AzureClient;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.client.AzureBlueprintRestClient;
import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.client.AzureManagementRestClient;
import io.harness.azure.context.ARMDeploymentSteadyStateContext;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureARMRGTemplateExportOptions;
import io.harness.azure.model.AzureARMTemplate;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.management.ManagementGroupInfo;
import io.harness.azure.model.management.ManagementGroupListResult;
import io.harness.azure.model.tag.AzureListTagsResponse;
import io.harness.azure.model.tag.TagDetails;
import io.harness.azure.utility.AzureUtils;
import io.harness.exception.ngexception.AzureARMTaskException;
import io.harness.exception.runtime.azure.AzureARMManagementScopeException;
import io.harness.exception.runtime.azure.AzureARMResourceGroupScopeException;
import io.harness.exception.runtime.azure.AzureARMSubscriptionScopeException;
import io.harness.exception.runtime.azure.AzureARMTenantScopeException;
import io.harness.exception.runtime.azure.AzureARMValidationException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.serializer.JsonUtils;

import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.management.Region;
import com.azure.core.management.polling.PollResult;
import com.azure.core.util.polling.SyncPoller;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.resources.fluent.DeploymentOperationsClient;
import com.azure.resourcemanager.resources.fluent.DeploymentsClient;
import com.azure.resourcemanager.resources.fluent.ResourceManagementClient;
import com.azure.resourcemanager.resources.fluent.models.DeploymentExtendedInner;
import com.azure.resourcemanager.resources.fluent.models.DeploymentInner;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.azure.resourcemanager.resources.fluent.models.DeploymentValidateResultInner;
import com.azure.resourcemanager.resources.implementation.ResourceManagementClientBuilder;
import com.azure.resourcemanager.resources.models.Deployment;
import com.azure.resourcemanager.resources.models.DeploymentMode;
import com.azure.resourcemanager.resources.models.DeploymentProperties;
import com.azure.resourcemanager.resources.models.Location;
import com.azure.resourcemanager.resources.models.ResourceGroupExportTemplateOptions;
import com.azure.resourcemanager.resources.models.ScopedDeployment;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

@Singleton
@Slf4j
public class AzureManagementClientImpl extends AzureClient implements AzureManagementClient {
  private static String ERROR_TEXT = "Unexpected value:";

  @Override
  public List<String> listLocationsBySubscriptionId(AzureConfig azureConfig, String subscriptionId) {
    AzureEnvironmentType azureEnvironmentType = azureConfig.getAzureEnvironmentType() == null
        ? AzureEnvironmentType.AZURE
        : azureConfig.getAzureEnvironmentType();

    log.debug("Start listing location by subscriptionId {}", subscriptionId);
    return isNotBlank(subscriptionId) ? getAzureClient(azureConfig, subscriptionId)
                                            .subscriptions()
                                            .getById(subscriptionId)
                                            .listLocations()
                                            .stream()
                                            .map(Location::displayName)
                                            .collect(Collectors.toList())
                                      : getLocationsFromRegion(azureEnvironmentType);
  }

  private List<String> getLocationsFromRegion(AzureEnvironmentType azureEnvironmentType) {
    return Region.values()
        .stream()
        .filter(region
            -> (AzureEnvironmentType.AZURE_US_GOVERNMENT == azureEnvironmentType)
                == AzureUtils.AZURE_GOV_REGIONS_NAMES.contains(region.name()))
        .map(Region::label)
        .collect(Collectors.toList());
  }

  @Override
  public PagedFlux<ManagementGroupInfo> listManagementGroups(final AzureConfig azureConfig) {
    return new PagedFlux(()
                             -> listManagementSinglePageAsync(azureConfig),
        nextLink -> listManagementNextSinglePageAsync(azureConfig, String.valueOf(nextLink)));
  }

  public Mono<PagedResponse<ManagementGroupInfo>> listManagementSinglePageAsync(final AzureConfig azureConfig) {
    return executePagedRequest(getAzureManagementRestClient(azureConfig.getAzureEnvironmentType())
                                   .listManagementGroups(getAzureBearerAuthToken(azureConfig)),
        ManagementGroupListResult.class);
  }

  public Mono<PagedResponse<ManagementGroupInfo>> listManagementNextSinglePageAsync(
      final AzureConfig azureConfig, final String nextPageLink) {
    if (isBlank(nextPageLink)) {
      return Mono.empty();
    }

    return executePagedRequest(getAzureManagementRestClient(azureConfig.getAzureEnvironmentType())
                                   .listManagementGroupsNextPage(getAzureBearerAuthToken(azureConfig), nextPageLink,
                                       AzureBlueprintRestClient.APP_VERSION),
        ManagementGroupListResult.class);
  }

  @Override
  public String exportResourceGroupTemplateJSON(
      AzureClientContext context, AzureARMRGTemplateExportOptions rgExportOptions) {
    String resourceGroupName = context.getResourceGroupName();
    String subscriptionId = context.getSubscriptionId();
    ResourceGroupExportTemplateOptions resourceGroupExportTemplateOptions =
        ResourceGroupExportTemplateOptions.valueOf(rgExportOptions.name());

    if (isBlank(resourceGroupName)) {
      throw new AzureARMResourceGroupScopeException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(subscriptionId)) {
      throw new AzureARMResourceGroupScopeException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClientByContext(context);
    log.debug(
        "Start exporting template at resource group, subscriptionId: {},  resourceGroupName: {}, resourceGroupExportTemplateOptions: {}",
        subscriptionId, resourceGroupName, resourceGroupExportTemplateOptions);

    return azure.resourceGroups()
        .getByName(resourceGroupName)
        .exportTemplate(resourceGroupExportTemplateOptions)
        .templateJson();
  }

  @Override
  public Deployment getDeploymentAtResourceGroup(AzureClientContext context, String deploymentName) {
    String resourceGroupName = context.getResourceGroupName();
    String subscriptionId = context.getSubscriptionId();

    if (isBlank(resourceGroupName)) {
      throw new AzureARMResourceGroupScopeException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(subscriptionId)) {
      throw new AzureARMResourceGroupScopeException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG);
    }
    if (isBlank(deploymentName)) {
      throw new AzureARMResourceGroupScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClientByContext(context);

    log.debug(
        "Start getting deployment info at resource group scope, deploymentName: {}, subscriptionId: {},  resourceGroupName: {}",
        deploymentName, subscriptionId, resourceGroupName);
    return azure.deployments().getByResourceGroup(resourceGroupName, deploymentName);
  }

  @Override
  public DeploymentValidateResultInner validateDeploymentAtResourceGroupScope(
      AzureClientContext context, AzureARMTemplate template) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(context).getDeployments();
    return validateDeploymentAtResourceGroupScope(context, template, deploymentsClient);
  }

  @Override
  public DeploymentValidateResultInner validateDeploymentAtResourceGroupScope(
      AzureClientContext context, AzureARMTemplate template, DeploymentsClient deploymentsClient) {
    String resourceGroupName = context.getResourceGroupName();
    String subscriptionId = context.getSubscriptionId();
    String deploymentName = template.getDeploymentName();

    if (isBlank(resourceGroupName)) {
      throw new AzureARMResourceGroupScopeException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(subscriptionId)) {
      throw new AzureARMResourceGroupScopeException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG);
    }
    if (isBlank(deploymentName)) {
      throw new AzureARMResourceGroupScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isNotBlank(template.getLocation())) {
      throw new AzureARMResourceGroupScopeException(LOCATION_SET_AT_RESOURCE_GROUP_VALIDATION_MSG);
    }

    DeploymentProperties properties = getDeploymentProperties(template);
    DeploymentInner parameters = new DeploymentInner();
    parameters.withProperties(properties);

    log.debug(
        "Start deployment validation at resource group scope, deploymentName: {}, subscriptionId: {},  resourceGroupName: {}",
        deploymentName, subscriptionId, resourceGroupName);

    return deploymentsClient.validate(resourceGroupName, deploymentName, parameters);
  }

  @Override
  public SyncPoller<Void, Deployment> deployAtResourceGroupScope(
      AzureClientContext context, AzureARMTemplate template) {
    String resourceGroupName = context.getResourceGroupName();
    String subscriptionId = context.getSubscriptionId();
    String deploymentName = template.getDeploymentName();
    DeploymentMode deploymentMode = DeploymentMode.fromString(template.getDeploymentMode().name());

    if (isBlank(resourceGroupName)) {
      throw new AzureARMResourceGroupScopeException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(subscriptionId)) {
      throw new AzureARMResourceGroupScopeException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG);
    }
    if (isBlank(deploymentName)) {
      throw new AzureARMResourceGroupScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isNotBlank(template.getLocation())) {
      throw new AzureARMResourceGroupScopeException(LOCATION_SET_AT_RESOURCE_GROUP_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClientByContext(context);
    try {
      log.debug(
          "Start deploying at resource group scope, deploymentName: {}, subscriptionId: {},  resourceGroupName: {}, deploymentMode: {}",
          deploymentName, subscriptionId, resourceGroupName, deploymentMode);
      return azure.deployments()
          .define(deploymentName)
          .withExistingResourceGroup(resourceGroupName)
          .withTemplate(template.getTemplateJSON())
          .withParameters(template.getParametersJSON())
          .withMode(deploymentMode)
          .beginCreate()
          .getSyncPoller();
    } catch (IOException e) {
      String errorMessage = format(
          "Error occurred while deploying at resource group scope, deploymentName: %s, subscriptionId: %s,  resourceGroupName: %s, deploymentMode: %s",
          deploymentName, subscriptionId, resourceGroupName, deploymentMode);
      throw new AzureARMResourceGroupScopeException(errorMessage, ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  @Override
  public DeploymentExtendedInner getDeploymentAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, String deploymentName) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(azureConfig, subscriptionId).getDeployments();
    return getDeploymentAtSubscriptionScope(azureConfig, subscriptionId, deploymentName, deploymentsClient);
  }

  @Override
  public DeploymentExtendedInner getDeploymentAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, String deploymentName, DeploymentsClient deploymentsClient) {
    if (isBlank(subscriptionId)) {
      throw new AzureARMSubscriptionScopeException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG);
    }
    if (isBlank(deploymentName)) {
      throw new AzureARMSubscriptionScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }

    log.debug("Start getting deployment info at subscription scope, deploymentName: {}, subscriptionId: {}",
        deploymentName, subscriptionId);

    return deploymentsClient.getAtSubscriptionScope(deploymentName);
  }

  @Override
  public DeploymentValidateResultInner validateDeploymentAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, AzureARMTemplate template) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(azureConfig, subscriptionId).getDeployments();
    return validateDeploymentAtSubscriptionScope(azureConfig, subscriptionId, template, deploymentsClient);
  }

  @Override
  public DeploymentValidateResultInner validateDeploymentAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, AzureARMTemplate template, DeploymentsClient deploymentsClient) {
    String deploymentName = template.getDeploymentName();
    if (isBlank(subscriptionId)) {
      throw new AzureARMSubscriptionScopeException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG);
    }
    if (isBlank(deploymentName)) {
      throw new AzureARMSubscriptionScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(template.getLocation())) {
      throw new AzureARMSubscriptionScopeException(LOCATION_BLANK_VALIDATION_MSG);
    }

    DeploymentProperties properties = getDeploymentProperties(template);
    DeploymentInner parameters = new DeploymentInner();
    parameters.withLocation(template.getLocation());
    parameters.withProperties(properties);

    log.debug("Start deployment validation at subscription scope, deploymentName: {}, subscriptionId: {}",
        deploymentName, subscriptionId);

    return deploymentsClient.validateAtSubscriptionScope(deploymentName, parameters);
  }

  @Override
  public SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, AzureARMTemplate template) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(azureConfig, subscriptionId).getDeployments();
    return deployAtSubscriptionScope(azureConfig, subscriptionId, template, deploymentsClient);
  }

  @Override
  public SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, AzureARMTemplate template, DeploymentsClient deploymentsClient) {
    String deploymentName = template.getDeploymentName();
    DeploymentMode deploymentMode = DeploymentMode.fromString(template.getDeploymentMode().name());

    if (isBlank(subscriptionId)) {
      throw new AzureARMSubscriptionScopeException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG);
    }
    if (isBlank(deploymentName)) {
      throw new AzureARMSubscriptionScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(template.getLocation())) {
      throw new AzureARMSubscriptionScopeException(LOCATION_BLANK_VALIDATION_MSG);
    }

    DeploymentProperties properties = getDeploymentProperties(template);
    DeploymentInner parameters = new DeploymentInner();
    parameters.withLocation(template.getLocation());
    parameters.withProperties(properties);

    log.debug("Start deploying at subscription scope, deploymentName: {}, subscriptionId: {}, deploymentMode: {}",
        deploymentName, subscriptionId, deploymentMode);

    return deploymentsClient.beginCreateOrUpdateAtSubscriptionScopeAsync(deploymentName, parameters).getSyncPoller();
  }

  @Override
  public DeploymentExtendedInner getDeploymentAtManagementScope(
      AzureConfig azureConfig, String groupId, String deploymentName) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(azureConfig).getDeployments();
    return getDeploymentAtManagementScope(azureConfig, groupId, deploymentName, deploymentsClient);
  }

  @Override
  public DeploymentExtendedInner getDeploymentAtManagementScope(
      AzureConfig azureConfig, String groupId, String deploymentName, DeploymentsClient deploymentsClient) {
    if (isBlank(deploymentName)) {
      throw new AzureARMManagementScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(groupId)) {
      throw new AzureARMManagementScopeException(MANAGEMENT_GROUP_ID_BLANK_VALIDATION_MSG);
    }

    log.debug("Start getting deployment info at management group scope, deploymentName: {}, groupId: {}",
        deploymentName, groupId);

    return deploymentsClient.getAtManagementGroupScope(groupId, deploymentName);
  }

  @Override
  public DeploymentValidateResultInner validateDeploymentAtManagementGroupScope(
      AzureConfig azureConfig, String groupId, AzureARMTemplate template) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(azureConfig).getDeployments();
    return validateDeploymentAtManagementGroupScope(azureConfig, groupId, template, deploymentsClient);
  }

  @Override
  public DeploymentValidateResultInner validateDeploymentAtManagementGroupScope(
      AzureConfig azureConfig, String groupId, AzureARMTemplate template, DeploymentsClient deploymentsClient) {
    String deploymentName = template.getDeploymentName();

    if (isBlank(deploymentName)) {
      throw new AzureARMManagementScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(groupId)) {
      throw new AzureARMManagementScopeException(MANAGEMENT_GROUP_ID_BLANK_VALIDATION_MSG);
    }
    if (isBlank(template.getLocation())) {
      throw new AzureARMManagementScopeException(LOCATION_BLANK_VALIDATION_MSG);
    }

    DeploymentProperties properties = getDeploymentProperties(template);
    ScopedDeployment scopedDeployment = getScopedDeployment(template.getLocation(), properties);

    log.debug("Start deployment validation at management group scope, deploymentName: {}, groupId: {}", deploymentName,
        groupId);

    return deploymentsClient.validateAtManagementGroupScope(groupId, deploymentName, scopedDeployment);
  }

  @Override
  public SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtManagementGroupScope(
      AzureConfig azureConfig, String groupId, AzureARMTemplate template) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(azureConfig).getDeployments();
    return deployAtManagementGroupScope(azureConfig, groupId, template, deploymentsClient);
  }

  @Override
  public SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtManagementGroupScope(
      AzureConfig azureConfig, String groupId, AzureARMTemplate template, DeploymentsClient deploymentsClient) {
    String deploymentName = template.getDeploymentName();
    DeploymentMode deploymentMode = DeploymentMode.fromString(template.getDeploymentMode().name());

    if (isBlank(deploymentName)) {
      throw new AzureARMManagementScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(groupId)) {
      throw new AzureARMManagementScopeException(MANAGEMENT_GROUP_ID_BLANK_VALIDATION_MSG);
    }
    if (isBlank(template.getLocation())) {
      throw new AzureARMManagementScopeException(LOCATION_BLANK_VALIDATION_MSG);
    }

    DeploymentProperties properties = getDeploymentProperties(template);
    ScopedDeployment scopedDeployment = getScopedDeployment(template.getLocation(), properties);

    log.debug("Start deploying at management group scope, deploymentName: {}, groupId: {}, deploymentMode: {}",
        deploymentName, groupId, deploymentMode);

    return deploymentsClient.beginCreateOrUpdateAtManagementGroupScopeAsync(groupId, deploymentName, scopedDeployment)
        .getSyncPoller();
  }

  @Override
  public DeploymentExtendedInner getDeploymentAtTenantScope(AzureConfig azureConfig, String deploymentName) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(azureConfig).getDeployments();
    return getDeploymentAtTenantScope(azureConfig, deploymentName, deploymentsClient);
  }

  @Override
  public DeploymentExtendedInner getDeploymentAtTenantScope(
      AzureConfig azureConfig, String deploymentName, DeploymentsClient deploymentsClient) {
    if (isBlank(deploymentName)) {
      throw new AzureARMTenantScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }

    log.debug("Start getting deployment info at tenant scope, deploymentName: {}", deploymentName);

    return deploymentsClient.getAtTenantScope(deploymentName);
  }

  @Override
  public DeploymentValidateResultInner validateDeploymentAtTenantScope(
      AzureConfig azureConfig, AzureARMTemplate template) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(azureConfig).getDeployments();
    return validateDeploymentAtTenantScope(azureConfig, template, deploymentsClient);
  }

  @Override
  public DeploymentValidateResultInner validateDeploymentAtTenantScope(
      AzureConfig azureConfig, AzureARMTemplate template, DeploymentsClient deploymentsClient) {
    String deploymentName = template.getDeploymentName();

    if (isBlank(deploymentName)) {
      throw new AzureARMTenantScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(template.getLocation())) {
      throw new AzureARMTenantScopeException(LOCATION_BLANK_VALIDATION_MSG);
    }

    DeploymentProperties properties = getDeploymentProperties(template);
    ScopedDeployment scopedDeployment = getScopedDeployment(template.getLocation(), properties);

    log.debug("Start deployment validation at tenant scope, deploymentName: {}, tenantId: {}", deploymentName,
        azureConfig.getTenantId());
    return deploymentsClient.validateAtTenantScope(deploymentName, scopedDeployment);
  }

  @Override
  public SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtTenantScope(
      AzureConfig azureConfig, AzureARMTemplate template) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(azureConfig).getDeployments();
    return deployAtTenantScope(azureConfig, template, deploymentsClient);
  }

  @Override
  public SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtTenantScope(
      AzureConfig azureConfig, AzureARMTemplate template, DeploymentsClient deploymentsClient) {
    String deploymentName = template.getDeploymentName();
    DeploymentMode deploymentMode = DeploymentMode.fromString(template.getDeploymentMode().name());

    if (isBlank(deploymentName)) {
      throw new AzureARMTenantScopeException(DEPLOYMENT_NAME_BLANK_VALIDATION_MSG);
    }
    if (isBlank(template.getLocation())) {
      throw new AzureARMTenantScopeException(LOCATION_BLANK_VALIDATION_MSG);
    }

    DeploymentProperties properties = getDeploymentProperties(template);
    ScopedDeployment scopedDeployment = getScopedDeployment(template.getLocation(), properties);

    log.debug("Start deploying at tenant scope, deploymentName: {}, tenantId: {}, deploymentMode: {}", deploymentName,
        azureConfig.getTenantId(), deploymentMode);
    return deploymentsClient.beginCreateOrUpdateAtTenantScopeAsync(deploymentName, scopedDeployment).getSyncPoller();
  }

  @Override
  public String getARMDeploymentStatus(ARMDeploymentSteadyStateContext context) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(context).getDeployments();
    return getARMDeploymentStatus(context, deploymentsClient);
  }

  @Override
  public String getARMDeploymentStatus(ARMDeploymentSteadyStateContext context, DeploymentsClient deploymentsClient) {
    DeploymentExtendedInner extendedInner = getDeploymentExtenderInner(context, deploymentsClient);
    return extendedInner.properties().provisioningState().toString();
  }

  private DeploymentExtendedInner getDeploymentExtenderInner(
      ARMDeploymentSteadyStateContext context, DeploymentsClient deploymentsClient) {
    DeploymentExtendedInner extendedInner;
    String deploymentName = context.getDeploymentName();

    switch (context.getScopeType()) {
      case RESOURCE_GROUP:
        String resourceGroup = context.getResourceGroup();
        if (!deploymentsClient.checkExistence(resourceGroup, deploymentName)) {
          throw new AzureARMResourceGroupScopeException(
              String.format(DEPLOYMENT_DOES_NOT_EXIST_RESOURCE_GROUP, deploymentName, resourceGroup));
        }

        extendedInner = deploymentsClient.getByResourceGroup(resourceGroup, deploymentName);
        break;

      case SUBSCRIPTION:
        if (!deploymentsClient.checkExistenceAtSubscriptionScope(deploymentName)) {
          throw new AzureARMSubscriptionScopeException(
              String.format(DEPLOYMENT_DOES_NOT_EXIST_SUBSCRIPTION, deploymentName, context.getSubscriptionId()));
        }
        extendedInner = deploymentsClient.getAtSubscriptionScope(deploymentName);
        break;

      case MANAGEMENT_GROUP:
        String managementGroupId = context.getManagementGroupId();
        if (!deploymentsClient.checkExistenceAtManagementGroupScope(managementGroupId, deploymentName)) {
          throw new AzureARMManagementScopeException(
              String.format(DEPLOYMENT_DOES_NOT_EXIST_MANAGEMENT_GROUP, deploymentName, managementGroupId));
        }

        extendedInner = deploymentsClient.getAtManagementGroupScope(managementGroupId, deploymentName);
        break;

      case TENANT:
        if (!deploymentsClient.checkExistenceAtTenantScope(deploymentName)) {
          throw new AzureARMTenantScopeException(
              String.format(DEPLOYMENT_DOES_NOT_EXIST_TENANT, deploymentName, context.getTenantId()));
        }
        extendedInner = deploymentsClient.getAtTenantScope(deploymentName);
        break;

      default:
        throw new AzureARMValidationException(format("%s %s", ERROR_TEXT, context.getScopeType()));
    }
    return extendedInner;
  }

  @Override
  public PagedIterable<DeploymentOperationInner> getDeploymentOperations(ARMDeploymentSteadyStateContext context) {
    PagedIterable<DeploymentOperationInner> operationInners;
    String deploymentName = context.getDeploymentName();

    DeploymentOperationsClient deploymentOperationsClient =
        getResourceManagementClient(context).getDeploymentOperations();

    switch (context.getScopeType()) {
      case RESOURCE_GROUP:
        operationInners = deploymentOperationsClient.listByResourceGroup(context.getResourceGroup(), deploymentName);
        break;

      case SUBSCRIPTION:
        operationInners = deploymentOperationsClient.listAtSubscriptionScope(deploymentName);
        break;

      case MANAGEMENT_GROUP:
        operationInners =
            deploymentOperationsClient.listAtManagementGroupScope(context.getManagementGroupId(), deploymentName);
        break;

      case TENANT:
        operationInners = deploymentOperationsClient.listAtTenantScope(deploymentName);
        break;

      default:
        throw new AzureARMTaskException(format("%s %s", ERROR_TEXT, context.getScopeType()));
    }
    return operationInners;
  }

  @Override
  public String getARMDeploymentOutputs(ARMDeploymentSteadyStateContext context) {
    DeploymentsClient deploymentsClient = getResourceManagementClient(context).getDeployments();
    return getARMDeploymentOutputs(context, deploymentsClient);
  }

  @Override
  public String getARMDeploymentOutputs(ARMDeploymentSteadyStateContext context, DeploymentsClient deploymentsClient) {
    String deploymentName = context.getDeploymentName();

    DeploymentExtendedInner extendedInner;
    switch (context.getScopeType()) {
      case RESOURCE_GROUP:
        extendedInner = deploymentsClient.getByResourceGroup(context.getResourceGroup(), deploymentName);
        break;
      case SUBSCRIPTION:
        extendedInner = deploymentsClient.getAtSubscriptionScope(deploymentName);
        break;
      case MANAGEMENT_GROUP:
        extendedInner = deploymentsClient.getAtManagementGroupScope(context.getManagementGroupId(), deploymentName);
        break;
      case TENANT:
        extendedInner = deploymentsClient.getAtTenantScope(deploymentName);
        break;
      default:
        throw new AzureARMTaskException(format("%s %s", ERROR_TEXT, context.getScopeType()));
    }
    Object outputs = extendedInner.properties().outputs();
    return outputs != null ? JsonUtils.asJson(outputs) : "";
  }

  @NotNull
  private DeploymentProperties getDeploymentProperties(AzureARMTemplate template) {
    DeploymentMode deploymentMode = DeploymentMode.fromString(template.getDeploymentMode().name());
    DeploymentProperties properties = new DeploymentProperties();
    properties.withMode(deploymentMode);
    properties.withTemplate(JsonUtils.readTree(template.getTemplateJSON()));
    properties.withParameters(JsonUtils.readTree(template.getParametersJSON()));
    return properties;
  }

  @NotNull
  private ScopedDeployment getScopedDeployment(String location, DeploymentProperties properties) {
    return new ScopedDeployment().withLocation(location).withProperties(properties);
  }

  @Override
  public PagedFlux<TagDetails> listTags(AzureConfig azureConfig, String subscriptionId) {
    return new PagedFlux(()
                             -> listTagsSinglePageAsync(azureConfig, subscriptionId),
        nextLink -> listTagsNextSinglePageAsync(azureConfig, String.valueOf(nextLink)));
  }

  private Mono<PagedResponse<TagDetails>> listTagsSinglePageAsync(
      final AzureConfig azureConfig, String subscriptionId) {
    if (isBlank(subscriptionId)) {
      return Mono.error(new IllegalArgumentException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG));
    }

    return executePagedRequest(getAzureManagementRestClient(azureConfig.getAzureEnvironmentType())
                                   .listTags(getAzureBearerAuthToken(azureConfig), subscriptionId),
        AzureListTagsResponse.class);
  }

  private Mono<PagedResponse<TagDetails>> listTagsNextSinglePageAsync(
      final AzureConfig azureConfig, final String nextPageLink) {
    if (isBlank(nextPageLink)) {
      return Mono.empty();
    }

    return executePagedRequest(getAzureManagementRestClient(azureConfig.getAzureEnvironmentType())
                                   .listTagsNextPage(getAzureBearerAuthToken(azureConfig), nextPageLink,
                                       AzureManagementRestClient.APP_VERSION),
        AzureListTagsResponse.class);
  }

  protected ResourceManagementClient getResourceManagementClient(ARMDeploymentSteadyStateContext context) {
    ARMScopeType scopeType = context.getScopeType();
    ResourceManagementClientBuilder resourceManagementClientBuilder = new ResourceManagementClientBuilder();

    if (ARMScopeType.RESOURCE_GROUP == scopeType || ARMScopeType.SUBSCRIPTION == scopeType) {
      resourceManagementClientBuilder.subscriptionId(context.getSubscriptionId());
    }
    resourceManagementClientBuilder.pipeline(
        getAzureHttpPipeline(context.getAzureConfig(), context.getSubscriptionId()));

    return resourceManagementClientBuilder.buildClient();
  }

  protected ResourceManagementClient getResourceManagementClient(AzureClientContext context) {
    return getResourceManagementClient(context.getAzureConfig(), context.getSubscriptionId());
  }

  protected ResourceManagementClient getResourceManagementClient(AzureConfig azureConfig) {
    return getResourceManagementClient(azureConfig, null);
  }

  protected ResourceManagementClient getResourceManagementClient(AzureConfig azureConfig, String subscriptionId) {
    return new ResourceManagementClientBuilder()
        .subscriptionId(subscriptionId)
        .pipeline(getAzureHttpPipeline(azureConfig, subscriptionId))
        .buildClient();
  }
}
