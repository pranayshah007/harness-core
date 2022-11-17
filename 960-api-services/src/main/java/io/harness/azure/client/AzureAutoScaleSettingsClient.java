/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.azure.model.AzureConfig;

import com.azure.resourcemanager.monitor.fluent.AutoscaleSettingsClient;
import com.azure.resourcemanager.monitor.models.AutoscaleProfile;
import com.azure.resourcemanager.monitor.models.AutoscaleSetting;
import com.azure.resourcemanager.monitor.models.ScaleCapacity;
import java.util.List;
import java.util.Optional;

public interface AzureAutoScaleSettingsClient {
  /**
   * Get Auto Scale Setting in JSON format
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param targetResourceId
   * @return
   */
  Optional<String> getAutoScaleSettingJSONByTargetResourceId(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String targetResourceId);

  /**
   * Attach auto scale settings to target resource
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param targetResourceId
   * @param autoScaleSettingResourceInnerJson
   * @param defaultProfileScaleCapacity
   */
  void attachAutoScaleSettingToTargetResourceId(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String targetResourceId, List<String> autoScaleSettingResourceInnerJson,
      ScaleCapacity defaultProfileScaleCapacity);
  /**
   * Get Auto Scale Setting
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param targetResourceId
   * @return
   */
  Optional<AutoscaleSetting> getAutoScaleSettingByTargetResourceId(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String targetResourceId);

  /**
   * Attach Auto Scale Setting to target resource
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param targetResourceId
   * @param autoScaleSettingResourceInnerJson
   */
  void attachAutoScaleSettingToTargetResourceId(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String targetResourceId, String autoScaleSettingResourceInnerJson);

  /**
   * Attach Auto Scale Setting to target resource
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param targetResourceId
   * @param autoScaleSettingResourceInnerJson
   * @param autoscaleSettingsClient
   */
  void attachAutoScaleSettingToTargetResourceId(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String targetResourceId, String autoScaleSettingResourceInnerJson,
      AutoscaleSettingsClient autoscaleSettingsClient);
  /**
   * Attach Auto Scale Setting to target resource
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param targetResourceId
   * @param autoScaleSettingResourceInnerJson
   * @param defaultProfileScaleCapacity
   */
  void attachAutoScaleSettingToTargetResourceId(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String targetResourceId, String autoScaleSettingResourceInnerJson,
      ScaleCapacity defaultProfileScaleCapacity);

  /**
   * Attach Auto Scale Setting to target resource
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param targetResourceId
   * @param autoScaleSettingResourceInnerJson
   * @param defaultProfileScaleCapacity
   * @param autoscaleSettingsClient
   */
  void attachAutoScaleSettingToTargetResourceId(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String targetResourceId, String autoScaleSettingResourceInnerJson,
      ScaleCapacity defaultProfileScaleCapacity, AutoscaleSettingsClient autoscaleSettingsClient);

  /**
   * Clear Auto Scale Setting on target resource
   *  @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param targetResourceId
   */
  void clearAutoScaleSettingOnTargetResourceId(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String targetResourceId);

  /**
   * Get default Auto Scale profile
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param targetResourceId
   * @return
   */
  Optional<AutoscaleProfile> getDefaultAutoScaleProfile(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String targetResourceId);

  /**
   * List Auto Scale Profiles on target resource
   *
   * @param azureConfig
   * @param subscriptionId
   * @param resourceGroupName
   * @param targetResourceId
   * @return
   */
  List<AutoscaleProfile> listAutoScaleProfilesByTargetResourceId(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String targetResourceId);

  /**
   * List Auto Scale Profiles in JSON format on target resource
   *
   * @param azureConfig
   * @param resourceGroupName
   * @param targetResourceId
   * @param subscriptionId
   * @return
   */
  List<String> listAutoScaleProfileJSONsByTargetResourceId(
      AzureConfig azureConfig, String resourceGroupName, String targetResourceId, String subscriptionId);
}
