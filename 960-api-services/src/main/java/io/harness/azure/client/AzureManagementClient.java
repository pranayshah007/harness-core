/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.azure.context.ARMDeploymentSteadyStateContext;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.AzureARMRGTemplateExportOptions;
import io.harness.azure.model.AzureARMTemplate;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.management.ManagementGroupInfo;
import io.harness.azure.model.tag.TagDetails;

import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.polling.PollResult;
import com.azure.core.util.polling.SyncPoller;
import com.azure.resourcemanager.resources.fluent.DeploymentsClient;
import com.azure.resourcemanager.resources.fluent.models.DeploymentExtendedInner;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.azure.resourcemanager.resources.fluent.models.DeploymentValidateResultInner;
import com.azure.resourcemanager.resources.models.Deployment;
import java.util.List;

public interface AzureManagementClient {
  /**
   * List Resource Groups names by Subscription Id.
   *
   * @param azureConfig
   * @param subscriptionId
   * @return
   */
  List<String> listLocationsBySubscriptionId(AzureConfig azureConfig, String subscriptionId);

  /**
   * List Management Group.
   *
   * @param azureConfig
   * @return
   */
  PagedFlux<ManagementGroupInfo> listManagementGroups(AzureConfig azureConfig);

  /**
   * Export template in JSON format by including comments or parameters with default values or both.
   *
   * @param context
   * @param rgExportOptions
   * @return
   */
  String exportResourceGroupTemplateJSON(AzureClientContext context, AzureARMRGTemplateExportOptions rgExportOptions);

  /**
   * Get deployment at subscription scope.
   *
   * @param context
   * @param deploymentName
   * @return
   */
  Deployment getDeploymentAtResourceGroup(AzureClientContext context, String deploymentName);

  /**
   * Validates whether the specified template is syntactically correct and will be accepted by Azure Resource Manager at
   * resource group scope.
   *
   * @param context
   * @param template
   * @return
   */
  DeploymentValidateResultInner validateDeploymentAtResourceGroupScope(
      AzureClientContext context, AzureARMTemplate template);

  /**
   * Validates whether the specified template is syntactically correct and will be accepted by Azure Resource Manager at
   * resource group scope.
   *
   * @param context
   * @param template
   * @param deploymentsClient
   * @return
   */
  DeploymentValidateResultInner validateDeploymentAtResourceGroupScope(
      AzureClientContext context, AzureARMTemplate template, DeploymentsClient deploymentsClient);

  /**
   * Start deployment at subscription scope.
   *
   * @param context
   * @param template
   * @return
   */
  SyncPoller<Void, Deployment> deployAtResourceGroupScope(AzureClientContext context, AzureARMTemplate template);

  /**
   * Get deployment at subscription scope.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param deploymentName
   * @return
   */
  DeploymentExtendedInner getDeploymentAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, String deploymentName);

  /**
   * Get deployment at subscription scope.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param deploymentName
   * @param deploymentsClient
   * @return
   */
  DeploymentExtendedInner getDeploymentAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, String deploymentName, DeploymentsClient deploymentsClient);

  /**
   * Validates whether the specified template is syntactically correct and will be accepted by Azure Resource Manager at
   * subscription scope.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param template
   * @return
   */
  DeploymentValidateResultInner validateDeploymentAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, AzureARMTemplate template);

  /**
   * Validates whether the specified template is syntactically correct and will be accepted by Azure Resource Manager at
   * subscription scope.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param template
   * @param deploymentsClient
   * @return
   */
  DeploymentValidateResultInner validateDeploymentAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, AzureARMTemplate template, DeploymentsClient deploymentsClient);

  /**
   * Start deployment at subscription scope.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param template
   * @return
   */
  SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, AzureARMTemplate template);

  /**
   * Start deployment at subscription scope.
   *
   * @param azureConfig
   * @param subscriptionId
   * @param template
   * @param deploymentsClient
   * @return
   */
  SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtSubscriptionScope(
      AzureConfig azureConfig, String subscriptionId, AzureARMTemplate template, DeploymentsClient deploymentsClient);

  /**
   * Get deployment at management group scope.
   *
   * @param azureConfig
   * @param groupId
   * @param deploymentName
   * @return
   */
  DeploymentExtendedInner getDeploymentAtManagementScope(
      AzureConfig azureConfig, String groupId, String deploymentName);

  /**
   * Get deployment at management group scope.
   *
   * @param azureConfig
   * @param groupId
   * @param deploymentName
   * @param deploymentsClient
   * @return
   */
  DeploymentExtendedInner getDeploymentAtManagementScope(
      AzureConfig azureConfig, String groupId, String deploymentName, DeploymentsClient deploymentsClient);

  /**
   * Validates whether the specified template is syntactically correct and will be accepted by Azure Resource Manager at
   * management group scope.
   *
   * @param azureConfig
   * @param groupId
   * @param template
   * @return
   */
  DeploymentValidateResultInner validateDeploymentAtManagementGroupScope(
      AzureConfig azureConfig, String groupId, AzureARMTemplate template);

  /**
   * Validates whether the specified template is syntactically correct and will be accepted by Azure Resource Manager at
   * management group scope.
   *
   * @param azureConfig
   * @param groupId
   * @param template
   * @param deploymentsClient
   * @return
   */
  DeploymentValidateResultInner validateDeploymentAtManagementGroupScope(
      AzureConfig azureConfig, String groupId, AzureARMTemplate template, DeploymentsClient deploymentsClient);

  /**
   * Start deployment at management group scope.
   *
   * @param azureConfig
   * @param groupId
   * @param template
   * @return
   */
  SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtManagementGroupScope(
      AzureConfig azureConfig, String groupId, AzureARMTemplate template);

  /**
   * Start deployment at management group scope.
   *
   * @param azureConfig
   * @param groupId
   * @param template
   * @param deploymentsClient
   * @return
   */
  SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtManagementGroupScope(
      AzureConfig azureConfig, String groupId, AzureARMTemplate template, DeploymentsClient deploymentsClient);

  /**
   * Get deployment at tenant scope.
   *
   * @param azureConfig
   * @param deploymentName
   * @return
   */
  DeploymentExtendedInner getDeploymentAtTenantScope(AzureConfig azureConfig, String deploymentName);

  /**
   * Get deployment at tenant scope.
   *
   * @param azureConfig
   * @param deploymentName
   * @param deploymentsClient
   * @return
   */
  DeploymentExtendedInner getDeploymentAtTenantScope(
      AzureConfig azureConfig, String deploymentName, DeploymentsClient deploymentsClient);

  /**
   * Validates whether the specified template is syntactically correct and will be accepted by Azure Resource Manager at
   * tenant scope.
   *
   * @param azureConfig
   * @param template
   * @return
   */
  DeploymentValidateResultInner validateDeploymentAtTenantScope(AzureConfig azureConfig, AzureARMTemplate template);

  /**
   * Validates whether the specified template is syntactically correct and will be accepted by Azure Resource Manager at
   * tenant scope.
   *
   * @param azureConfig
   * @param template
   * @param deploymentsClient
   * @return
   */
  DeploymentValidateResultInner validateDeploymentAtTenantScope(
      AzureConfig azureConfig, AzureARMTemplate template, DeploymentsClient deploymentsClient);

  /**
   * Start deployment at tenant scope.
   *
   * @param azureConfig
   * @param template
   * @return
   */
  SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtTenantScope(
      AzureConfig azureConfig, AzureARMTemplate template);

  /**
   * Start deployment at tenant scope.
   *
   * @param azureConfig
   * @param template
   * @param deploymentsClient
   * @return
   */
  SyncPoller<PollResult<DeploymentExtendedInner>, DeploymentExtendedInner> deployAtTenantScope(
      AzureConfig azureConfig, AzureARMTemplate template, DeploymentsClient deploymentsClient);

  String getARMDeploymentStatus(ARMDeploymentSteadyStateContext context);

  String getARMDeploymentStatus(ARMDeploymentSteadyStateContext context, DeploymentsClient deploymentsClient);

  PagedIterable<DeploymentOperationInner> getDeploymentOperations(ARMDeploymentSteadyStateContext context);

  String getARMDeploymentOutputs(ARMDeploymentSteadyStateContext context);

  String getARMDeploymentOutputs(ARMDeploymentSteadyStateContext context, DeploymentsClient deploymentsClient);

  PagedFlux<TagDetails> listTags(AzureConfig azureConfig, String subscriptionId);
}
