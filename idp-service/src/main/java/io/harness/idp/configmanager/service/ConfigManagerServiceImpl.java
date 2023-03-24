/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.service;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.configmanager.ConfigType;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.idp.configmanager.mappers.AppConfigMapper;
import io.harness.idp.configmanager.mappers.MergedAppConfigMapper;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.repositories.MergedAppConfigRepository;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.jackson.JsonNodeUtils;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.AppConfigRequest;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @com.google.inject.Inject }))
public class ConfigManagerServiceImpl implements ConfigManagerService {
  private AppConfigRepository appConfigRepository;
  private MergedAppConfigRepository mergedAppConfigRepository;
  private K8sClient k8sClient;
  private NamespaceService namespaceService;
  private static final String PLUGIN_CONFIG_NOT_FOUND =
      "Plugin configs for plugin - %s is not present for account - %s";
  private static final String PLUGIN_SAVE_UNSUCCESSFUL =
      "Plugin config saving is unsuccessful for plugin - % in account - %s";
  private static final String NO_PLUGIN_ENABLED_FOR_ACCOUNT = "No plugin is enabled for account - %s";
  private static final String BASE_APP_CONFIG_PATH = "baseappconfig.yaml";

  private static final String CONFIG_DATA_NAME = "config";

  private static final String CONFIG_NAME = "app-config";

  @Override
  public Map<String, Boolean> getAllPluginIdsMap(String accountIdentifier) {
    List<AppConfigEntity> allPluginConfig =
        appConfigRepository.findAllByAccountIdentifierAndConfigType(accountIdentifier, ConfigType.PLUGIN);
    return allPluginConfig.stream().collect(
        Collectors.toMap(AppConfigEntity::getConfigId, AppConfigEntity::getEnabled));
  }

  @Override
  public AppConfig getPluginConfig(String accountIdentifier, String pluginId) {
    Optional<AppConfigEntity> pluginConfig = appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(
        accountIdentifier, pluginId, ConfigType.PLUGIN);
    if (pluginConfig.isEmpty()) {
      return null;
    }
    return pluginConfig.map(AppConfigMapper::toDTO).get();
  }

  @Override
  public AppConfig saveConfigForAccount(AppConfig appConfig, String accountIdentifier, ConfigType configType) {
    AppConfigEntity appConfigEntity = AppConfigMapper.fromDTO(appConfig, accountIdentifier);
    appConfigEntity.setConfigType(configType);
    appConfigEntity.setEnabledDisabledAt(System.currentTimeMillis());
    AppConfigEntity insertedData = appConfigRepository.save(appConfigEntity);
    return AppConfigMapper.toDTO(insertedData);
  }

  @Override
  public AppConfig updateConfigForAccount(
      AppConfigRequest appConfigRequest, String accountIdentifier, ConfigType configType) {
    AppConfig appConfig = appConfigRequest.getAppConfig();
    AppConfigEntity appConfigEntity = AppConfigMapper.fromDTO(appConfig, accountIdentifier);
    appConfigEntity.setConfigType(configType);
    AppConfigEntity updatedData = appConfigRepository.updateConfig(appConfigEntity, configType);
    if (updatedData == null) {
      throw new InvalidRequestException(format(PLUGIN_CONFIG_NOT_FOUND, appConfig.getConfigId(), accountIdentifier));
    }
    return AppConfigMapper.toDTO(updatedData);
  }

  @Override
  public AppConfig toggleConfigForAccount(
      String accountIdentifier, String configId, Boolean isEnabled, ConfigType configType) {
    AppConfigEntity updatedData =
        appConfigRepository.updateConfigEnablement(accountIdentifier, configId, isEnabled, configType);
    if (updatedData == null) {
      throw new InvalidRequestException(format(PLUGIN_CONFIG_NOT_FOUND, configId, accountIdentifier));
    }
    return AppConfigMapper.toDTO(updatedData);
  }

  @Override
  public MergedAppConfigEntity mergeAndSaveAppConfig(String accountIdentifier) throws Exception {
    String mergedAppConfig = mergeAllAppConfigsForAccount(accountIdentifier);
    updateConfigMap(accountIdentifier, mergedAppConfig);
    MergedAppConfigEntity mergedAppConfigEntity =
        MergedAppConfigMapper.getMergedAppConfigEntity(accountIdentifier, mergedAppConfig);
    return mergedAppConfigRepository.saveOrUpdate(mergedAppConfigEntity);
  }

  private String mergeAppConfigs(List<String> configs) throws Exception {
    String baseAppConfig = ConfigManagerUtils.readFile(BASE_APP_CONFIG_PATH);
    JsonNode baseConfig = ConfigManagerUtils.asJsonNode(baseAppConfig);
    Iterator<String> itr = configs.iterator();
    while (itr.hasNext()) {
      String config = itr.next();
      JsonNode pluginConfig = ConfigManagerUtils.asJsonNode(config);
      JsonNodeUtils.merge(baseConfig, pluginConfig);
      itr.remove();
    }
    return ConfigManagerUtils.asYaml(baseConfig.toString());
  }

  private String mergeAllAppConfigsForAccount(String accountIdentifier) throws Exception {
    List<String> enabledPluginConfigs = getAllEnabledConfigs(accountIdentifier);
    return mergeAppConfigs(enabledPluginConfigs);
  }

  private List<String> getAllEnabledConfigs(String accountIdentifier) {
    List<AppConfigEntity> allEnabledConfigEntity =
        appConfigRepository.findAllByAccountIdentifierAndEnabled(accountIdentifier, true);
    if (allEnabledConfigEntity.isEmpty()) {
      throw new InvalidRequestException(format(NO_PLUGIN_ENABLED_FOR_ACCOUNT, accountIdentifier));
    }
    return allEnabledConfigEntity.stream().map(entity -> entity.getConfigs()).collect(Collectors.toList());
  }
  private void updateConfigMap(String accountIdentifier, String appConfigYamlData) {
    Map<String, String> data = new HashMap<>();
    data.put(CONFIG_DATA_NAME, appConfigYamlData);
    String namespace = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier).getNamespace();
    k8sClient.updateConfigMapData(namespace, CONFIG_NAME, data, true);
    log.info(
        "Config map successfully created/updated for account - {} in namespace - {}", accountIdentifier, namespace);
  }
}
