/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl;

import static io.harness.azure.model.AzureConstants.RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.TARGET_RESOURCE_ID_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.VMSS_AUTOSCALE_SUFIX;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.AzureClient;
import io.harness.azure.client.AzureAutoScaleSettingsClient;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import com.azure.core.http.rest.PagedIterable;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.monitor.fluent.AutoscaleSettingsClient;
import com.azure.resourcemanager.monitor.fluent.MonitorClient;
import com.azure.resourcemanager.monitor.fluent.models.AutoscaleProfileInner;
import com.azure.resourcemanager.monitor.fluent.models.AutoscaleSettingResourceInner;
import com.azure.resourcemanager.monitor.implementation.MonitorClientBuilder;
import com.azure.resourcemanager.monitor.models.AutoscaleProfile;
import com.azure.resourcemanager.monitor.models.AutoscaleSetting;
import com.azure.resourcemanager.monitor.models.ScaleCapacity;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class AzureAutoScaleSettingsClientImpl extends AzureClient implements AzureAutoScaleSettingsClient {
  @Override
  public Optional<String> getAutoScaleSettingJSONByTargetResourceId(
      AzureConfig azureConfig, String subscriptionId, final String resourceGroupName, final String targetResourceId) {
    Optional<AutoscaleSetting> autoScaleSettingOp =
        getAutoScaleSettingByTargetResourceId(azureConfig, subscriptionId, resourceGroupName, targetResourceId);

    if (autoScaleSettingOp.isPresent()) {
      AutoscaleSetting autoscaleSetting = autoScaleSettingOp.get();
      AutoscaleSettingResourceInner autoscaleSettingResourceInner = autoscaleSetting.innerModel();

      log.debug(
          "Start serializing AutosScaleSettingResourceInner by subscriptionId: {}, resourceGroupName: {}, targetResourceId: {}",
          subscriptionId, resourceGroupName, targetResourceId);
      try {
        return Optional.of(serializerAdapter.serialize(autoscaleSettingResourceInner, serializerEncoding));
      } catch (IOException e) {
        throw new InvalidRequestException(
            format(
                "Unable to serialize AutoScaleSetting, subscriptionId: %s, resourceGroupName: %s, targetResourceId: %s",
                subscriptionId, resourceGroupName, targetResourceId),
            ExceptionMessageSanitizer.sanitizeException(e), USER);
      }
    } else {
      return Optional.empty();
    }
  }

  @Override
  public void attachAutoScaleSettingToTargetResourceId(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String targetResourceId, List<String> autoScaleSettingResourceInnerJson,
      ScaleCapacity defaultProfileScaleCapacity) {
    if (isNull(autoScaleSettingResourceInnerJson) || autoScaleSettingResourceInnerJson.isEmpty()) {
      return;
    }

    for (String autoScaleSetting : autoScaleSettingResourceInnerJson) {
      attachAutoScaleSettingToTargetResourceId(azureConfig, subscriptionId, resourceGroupName, targetResourceId,
          autoScaleSetting, defaultProfileScaleCapacity);
    }
  }

  @Override
  public Optional<AutoscaleSetting> getAutoScaleSettingByTargetResourceId(AzureConfig azureConfig,
      final String subscriptionId, final String resourceGroupName, final String targetResourceId) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(targetResourceId)) {
      throw new IllegalArgumentException(TARGET_RESOURCE_ID_NULL_VALIDATION_MSG);
    }

    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);

    log.debug("Start listing AutosScale settings by subscriptionId: {}, resourceGroupName: {}, targetResourceId: {}",
        subscriptionId, resourceGroupName, targetResourceId);
    PagedIterable<AutoscaleSetting> autosScaleSettings =
        azure.autoscaleSettings().listByResourceGroup(resourceGroupName);
    for (AutoscaleSetting autoScaleSetting : autosScaleSettings) {
      if (autoScaleSetting.autoscaleEnabled() && targetResourceId.equals(autoScaleSetting.targetResourceId())) {
        return Optional.of(autoScaleSetting);
      }
    }
    return Optional.empty();
  }

  @Override
  public void attachAutoScaleSettingToTargetResourceId(AzureConfig azureConfig, String subscriptionId,
      final String resourceGroupName, final String targetResourceId, final String autoScaleSettingResourceInnerJson) {
    AutoscaleSettingsClient autoscaleSettingsClient =
        getMonitorClient(azureConfig, subscriptionId).getAutoscaleSettings();
    attachAutoScaleSettingToTargetResourceId(azureConfig, subscriptionId, resourceGroupName, targetResourceId,
        autoScaleSettingResourceInnerJson, autoscaleSettingsClient);
  }

  @Override
  public void attachAutoScaleSettingToTargetResourceId(AzureConfig azureConfig, String subscriptionId,
      final String resourceGroupName, final String targetResourceId, final String autoScaleSettingResourceInnerJson,
      AutoscaleSettingsClient autoscaleSettingsClient) {
    attachAutoScaleSettingToTargetResourceId(azureConfig, subscriptionId, resourceGroupName, targetResourceId,
        autoScaleSettingResourceInnerJson, null, autoscaleSettingsClient);
  }

  @Override
  public void attachAutoScaleSettingToTargetResourceId(AzureConfig azureConfig, final String subscriptionId,
      final String resourceGroupName, final String targetResourceId, final String autoScaleSettingResourceInnerJson,
      ScaleCapacity defaultProfileScaleCapacity) {
    AutoscaleSettingsClient autoscaleSettingsClient =
        getMonitorClient(azureConfig, subscriptionId).getAutoscaleSettings();
    attachAutoScaleSettingToTargetResourceId(azureConfig, subscriptionId, resourceGroupName, targetResourceId,
        autoScaleSettingResourceInnerJson, null, autoscaleSettingsClient);
  }

  @Override
  public void attachAutoScaleSettingToTargetResourceId(AzureConfig azureConfig, final String subscriptionId,
      final String resourceGroupName, final String targetResourceId, final String autoScaleSettingResourceInnerJson,
      ScaleCapacity defaultProfileScaleCapacity, AutoscaleSettingsClient autoscaleSettingsClient) {
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }
    if (isBlank(resourceGroupName)) {
      throw new IllegalArgumentException(RESOURCE_GROUP_NAME_NULL_VALIDATION_MSG);
    }

    if (isBlank(targetResourceId)) {
      throw new IllegalArgumentException(TARGET_RESOURCE_ID_NULL_VALIDATION_MSG);
    }

    if (isBlank(autoScaleSettingResourceInnerJson)) {
      throw new IllegalArgumentException(AzureConstants.AUTOSCALE_SETTINGS_RESOURCE_JSON_NULL_VALIDATION_MSG);
    }

    AutoscaleSettingResourceInner autoScaleSettingResourceInner;
    try {
      log.debug(
          "Start deserialize AutosScaleSettingResourceInner by subscriptionId: {}, resourceGroupName: {}, targetResourceId: {}",
          subscriptionId, resourceGroupName, targetResourceId);
      autoScaleSettingResourceInner = serializerAdapter.deserialize(
          autoScaleSettingResourceInnerJson, AutoscaleSettingResourceInner.class, serializerEncoding);
    } catch (IOException e) {
      throw new InvalidRequestException(
          format("Unable to deserialize AutoScaleSettingResourceInner, subscriptionId: %s, resourceGroupName: %s, "
                  + "targetResourceId: %s, autoScaleSettingResourceInnerJson: %s",
              subscriptionId, resourceGroupName, targetResourceId, autoScaleSettingResourceInnerJson),
          ExceptionMessageSanitizer.sanitizeException(e), USER);
    }

    String newCustomAutoScalingSettingsName = getNewCustomAutoScalingSettingsName(targetResourceId);

    autoScaleSettingResourceInner.withTargetResourceUri(targetResourceId);
    autoScaleSettingResourceInner.withNamePropertiesName(newCustomAutoScalingSettingsName);

    // set min, max and desired capacity on default AutoScaleProfile
    setDefaultAutoScaleProfileCapacity(defaultProfileScaleCapacity, autoScaleSettingResourceInner);
    setRulesMetricResourceUri(autoScaleSettingResourceInner, targetResourceId);

    log.debug(
        "Start creating or updating AutosScaleSetting by subscriptionId: {}, resourceGroupName: {}, targetResourceId: {}, newCustomAutoScalingSettingsName: {}",
        subscriptionId, resourceGroupName, targetResourceId, newCustomAutoScalingSettingsName);

    autoscaleSettingsClient.createOrUpdateAsync(
        resourceGroupName, newCustomAutoScalingSettingsName, autoScaleSettingResourceInner);
  }

  private String getNewCustomAutoScalingSettingsName(String targetResourceId) {
    return targetResourceId.substring(targetResourceId.lastIndexOf('/') + 1).concat(VMSS_AUTOSCALE_SUFIX);
  }

  private void setDefaultAutoScaleProfileCapacity(
      ScaleCapacity defaultProfileScaleCapacity, AutoscaleSettingResourceInner autoScaleSettingResourceInner) {
    if (isNull(defaultProfileScaleCapacity) || isNull(autoScaleSettingResourceInner)) {
      return;
    }

    autoScaleSettingResourceInner.profiles()
        .stream()
        .filter(java.util.Objects::nonNull)
        .filter(profile -> AzureResourceUtility.isDefaultAutoScaleProfile(profile.name()))
        .findFirst()
        .map(AutoscaleProfileInner::capacity)
        .ifPresent(capacity -> {
          capacity.withMaximum(defaultProfileScaleCapacity.maximum());
          capacity.withMinimum(defaultProfileScaleCapacity.minimum());
          capacity.withDefaultProperty(defaultProfileScaleCapacity.defaultProperty());
        });
  }

  private void setRulesMetricResourceUri(
      AutoscaleSettingResourceInner autoScaleSettingResourceInner, String targetResourceUri) {
    if (isNull(autoScaleSettingResourceInner)) {
      return;
    }

    autoScaleSettingResourceInner.profiles()
        .stream()
        .map(AutoscaleProfileInner::rules)
        .flatMap(Collection::stream)
        .forEach(scaleRuleInner -> scaleRuleInner.metricTrigger().withMetricResourceUri(targetResourceUri));
  }

  @Override
  public void clearAutoScaleSettingOnTargetResourceId(
      AzureConfig azureConfig, String subscriptionId, final String resourceGroupName, final String targetResourceId) {
    AzureResourceManager azure = getAzureClient(azureConfig, subscriptionId);

    Optional<AutoscaleSetting> autoScaleSettingOp =
        getAutoScaleSettingByTargetResourceId(azureConfig, subscriptionId, resourceGroupName, targetResourceId);

    if (autoScaleSettingOp.isPresent()) {
      AutoscaleSetting autoscaleSetting = autoScaleSettingOp.get();

      log.debug("Start deleting AutosScaleSetting by subscriptionId: {}, resourceGroupName: {}, targetResourceId: {}",
          subscriptionId, resourceGroupName, targetResourceId);
      azure.autoscaleSettings().deleteById(autoscaleSetting.id());
    } else {
      log.info("There is no AutoScaleSettings attached on, subscriptionId: {}, targetResourceId: {}, "
              + "resourceGroupName: {}",
          subscriptionId, targetResourceId, resourceGroupName);
    }
  }

  @Override
  public Optional<AutoscaleProfile> getDefaultAutoScaleProfile(
      AzureConfig azureConfig, String subscriptionId, final String resourceGroupName, final String targetResourceId) {
    Optional<AutoscaleSetting> autoScaleSettingOp =
        getAutoScaleSettingByTargetResourceId(azureConfig, subscriptionId, resourceGroupName, targetResourceId);

    if (autoScaleSettingOp.isPresent()) {
      AutoscaleSetting autoscaleSetting = autoScaleSettingOp.get();
      return autoscaleSetting.profiles()
          .entrySet()
          .stream()
          .filter(profileEntry -> AzureResourceUtility.isDefaultAutoScaleProfile(profileEntry.getKey()))
          .map(Map.Entry::getValue)
          .findFirst();
    } else {
      return Optional.empty();
    }
  }

  @Override
  public List<AutoscaleProfile> listAutoScaleProfilesByTargetResourceId(
      AzureConfig azureConfig, String subscriptionId, final String resourceGroupName, final String targetResourceId) {
    Optional<AutoscaleSetting> autoScaleSettingOp =
        getAutoScaleSettingByTargetResourceId(azureConfig, subscriptionId, resourceGroupName, targetResourceId);

    if (autoScaleSettingOp.isPresent()) {
      AutoscaleSetting autoscaleSetting = autoScaleSettingOp.get();
      return new ArrayList<>(autoscaleSetting.profiles().values());
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public List<String> listAutoScaleProfileJSONsByTargetResourceId(
      AzureConfig azureConfig, final String resourceGroupName, final String targetResourceId, String subscriptionId) {
    Optional<AutoscaleSetting> autoScaleSettingOp =
        getAutoScaleSettingByTargetResourceId(azureConfig, subscriptionId, resourceGroupName, targetResourceId);

    if (autoScaleSettingOp.isPresent()) {
      AutoscaleSetting autoscaleSetting = autoScaleSettingOp.get();
      AutoscaleSettingResourceInner autoScaleSettingInner = autoscaleSetting.innerModel();

      log.debug("Start listing AutosScale profiles by subscriptionId: {}, resourceGroupName: {}, targetResourceId: {}",
          subscriptionId, resourceGroupName, targetResourceId);
      return autoScaleSettingInner.profiles()
          .stream()
          .map(profile -> {
            try {
              return serializerAdapter.serialize(profile, serializerEncoding);
            } catch (IOException e) {
              throw new InvalidRequestException(
                  format("Unable to serialize AutoScaleProfile, subscriptionId: %s, resourceGroupName: %s, "
                          + "targetResourceId: %s",
                      subscriptionId, resourceGroupName, targetResourceId),
                  ExceptionMessageSanitizer.sanitizeException(e), USER);
            }
          })
          .collect(Collectors.toList());

    } else {
      return Collections.emptyList();
    }
  }

  protected MonitorClient getMonitorClient(AzureClientContext context) {
    return getMonitorClient(context.getAzureConfig(), context.getSubscriptionId());
  }

  protected MonitorClient getMonitorClient(AzureConfig azureConfig) {
    return getMonitorClient(azureConfig, null);
  }

  protected MonitorClient getMonitorClient(AzureConfig azureConfig, String subscriptionId) {
    return new MonitorClientBuilder()
        .subscriptionId(subscriptionId)
        .pipeline(getAzureHttpPipeline(azureConfig, subscriptionId))
        .buildClient();
  }
}
