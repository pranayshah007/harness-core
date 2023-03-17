/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.service;

import static java.lang.String.format;

import io.harness.exception.InvalidRequestException;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.mappers.AppConfigMapper;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.AppConfigRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @com.google.inject.Inject }))
public class ConfigManagerServiceImpl implements ConfigManagerService {
  private AppConfigRepository appConfigRepository;

  private static final String PLUGIN_CONFIG_NOT_FOUND =
      "Plugin configs for plugin - %s is not present for account - %s";
  private static final String PLUGIN_SAVE_UNSUCCESSFUL =
      "Plugin config saving is unsuccessful for plugin - % in account - %s";

  @Override
  public Map<String, Boolean> getAllPluginIdsMap(String accountIdentifier) {
    List<AppConfigEntity> allPluginConfig = appConfigRepository.findAllByAccountIdentifier(accountIdentifier);
    return allPluginConfig.stream().collect(
        Collectors.toMap(AppConfigEntity::getPluginId, AppConfigEntity::getEnabled));
  }

  @Override
  public AppConfig getPluginConfig(String accountIdentifier, String pluginId) {
    Optional<AppConfigEntity> pluginConfig =
        appConfigRepository.findByAccountIdentifierAndPluginId(accountIdentifier, pluginId);
    if (pluginConfig.isEmpty()) {
      return null;
    }
    return pluginConfig.map(AppConfigMapper::toDTO).get();
  }

  @Override
  public AppConfig savePluginConfig(AppConfigRequest appConfigRequest, String accountIdentifier) {
    AppConfig appConfig = appConfigRequest.getAppConfig();
    AppConfigEntity appConfigEntity = AppConfigMapper.fromDTO(appConfig, accountIdentifier);
    appConfigEntity.setEnabledDisabledAt(System.currentTimeMillis());
    AppConfigEntity insertedData = appConfigRepository.save(appConfigEntity);
    return AppConfigMapper.toDTO(insertedData);
  }

  @Override
  public AppConfig updatePluginConfig(AppConfigRequest appConfigRequest, String accountIdentifier) {
    AppConfig appConfig = appConfigRequest.getAppConfig();
    AppConfigEntity appConfigEntity = AppConfigMapper.fromDTO(appConfig, accountIdentifier);
    AppConfigEntity updatedData = appConfigRepository.updateConfig(appConfigEntity);
    if (updatedData == null) {
      throw new InvalidRequestException(format(PLUGIN_CONFIG_NOT_FOUND, appConfig.getPluginId(), accountIdentifier));
    }
    return AppConfigMapper.toDTO(updatedData);
  }

  @Override
  public AppConfig togglePlugin(String accountIdentifier, String pluginName, Boolean isEnabled) {
    AppConfigEntity updatedData = appConfigRepository.updatePluginEnablement(accountIdentifier, pluginName, isEnabled);
    if (updatedData == null) {
      throw new InvalidRequestException(format(PLUGIN_CONFIG_NOT_FOUND, pluginName, accountIdentifier));
    }
    return AppConfigMapper.toDTO(updatedData);
  }
}
