/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.service;

import static io.harness.idp.common.CommonUtils.readFileFromClassPath;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.configmanager.ConfigType;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.idp.configmanager.mappers.AppConfigMapper;
import io.harness.idp.configmanager.mappers.MergedAppConfigMapper;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.repositories.MergedAppConfigRepository;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.jackson.JsonNodeUtils;
import io.harness.spec.server.idp.v1.model.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @com.google.inject.Inject }))
public class ConfigManagerServiceImpl implements ConfigManagerService {
  @Inject @Named("env") private String env;
  private AppConfigRepository appConfigRepository;
  private MergedAppConfigRepository mergedAppConfigRepository;
  private K8sClient k8sClient;
  private NamespaceService namespaceService;
  private ConfigEnvVariablesService configEnvVariablesService;
  private BackstageEnvVariableService backstageEnvVariableService;
  private PluginsProxyInfoService pluginsProxyInfoService;

  private static final String PLUGIN_CONFIG_NOT_FOUND =
      "Plugin configs for plugin - %s is not present for account - %s";
  private static final String NO_PLUGIN_ENABLED_FOR_ACCOUNT = "No plugin is enabled for account - %s";
  private static final String BASE_APP_CONFIG_PATH = "baseappconfig.yaml";
  private static final String BASE_APP_CONFIG_PATH_QA = "baseappconfig-qa.yaml";
  private static final String BASE_APP_CONFIG_PATH_PRE_QA = "baseappconfig-preqa.yaml";
  private static final String BASE_APP_CONFIG_PATH_COMPLIANCE = "baseappconfig-compliance.yaml";

  private static final String CONFIG_DATA_NAME = "config";

  private static final String CONFIG_NAME = "backstage-override-config";

  private static final String INVALID_PLUGIN_CONFIG_PROVIDED = "Invalid config provided for Plugin id - %s";
  private static final String MERGED_APP_CONFIG_JSON_SCHEMA_PATH = "configs/json-schemas/merged-app-config-schema.json";

  private static final String INVALID_CONFIG_ID_PROVIDED = "Error in reading schema - Invalid config id provided - %s";

  private static final String INVALID_MERGED_APP_CONFIG_SCHEMA =
      "Invalid schema for merged app-config.yaml for account - %s";

  private static final long baseTimeStamp = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000;
  private static final String HARNESS_CI_CD_PLUGIN_IDENTIFIER = "harness-ci-cd";

  @Override
  public Map<String, Boolean> getAllPluginIdsMap(String accountIdentifier) {
    List<AppConfigEntity> allPluginConfig =
        appConfigRepository.findAllByAccountIdentifierAndConfigType(accountIdentifier, ConfigType.PLUGIN);
    return allPluginConfig.stream().collect(
        Collectors.toMap(AppConfigEntity::getConfigId, AppConfigEntity::getEnabled));
  }

  @Override
  public AppConfig getAppConfig(String accountIdentifier, String configId, ConfigType configType) {
    Optional<AppConfigEntity> config =
        appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(accountIdentifier, configId, configType);
    if (config.isEmpty()) {
      return null;
    }
    return config.map(AppConfigMapper::toDTO).get();
  }

  @Override
  public AppConfig saveConfigForAccount(AppConfig appConfig, String accountIdentifier, ConfigType configType)
      throws Exception {
    AppConfigEntity appConfigEntity = AppConfigMapper.fromDTO(appConfig, accountIdentifier);
    appConfigEntity.setConfigType(configType);
    appConfigEntity.setEnabledDisabledAt(System.currentTimeMillis());
    appConfigEntity.setEnabled(getEnabledFlagBasedOnConfigType(configType));

    List<ProxyHostDetail> pluginProxyHostDetails =
        pluginsProxyInfoService.insertProxyHostDetailsForPlugin(appConfig, accountIdentifier);

    List<BackstageEnvSecretVariable> backstageEnvSecretVariableList =
        configEnvVariablesService.insertConfigEnvVariables(appConfig, accountIdentifier);
    if (appConfig.getConfigId().equals(HARNESS_CI_CD_PLUGIN_IDENTIFIER)) {
      appConfigEntity.setEnabled(true);
    }
    AppConfigEntity insertedData = appConfigRepository.save(appConfigEntity);
    AppConfig returnedConfig = AppConfigMapper.toDTO(insertedData);
    returnedConfig.setEnvVariables(backstageEnvSecretVariableList);
    returnedConfig.setProxy(pluginProxyHostDetails);
    return returnedConfig;
  }

  @Override
  public AppConfig updateConfigForAccount(AppConfig appConfig, String accountIdentifier, ConfigType configType)
      throws Exception {
    AppConfigEntity appConfigEntity = AppConfigMapper.fromDTO(appConfig, accountIdentifier);
    appConfigEntity.setConfigType(configType);

    List<ProxyHostDetail> proxyHostDetailList =
        pluginsProxyInfoService.updateProxyHostDetailsForPlugin(appConfig, accountIdentifier);

    List<BackstageEnvSecretVariable> backstageEnvSecretVariableList =
        configEnvVariablesService.updateConfigEnvVariables(appConfig, accountIdentifier);
    AppConfigEntity updatedData = appConfigRepository.updateConfig(appConfigEntity, configType);
    if (updatedData == null) {
      throw new InvalidRequestException(format(PLUGIN_CONFIG_NOT_FOUND, appConfig.getConfigId(), accountIdentifier));
    }
    AppConfig returnedConfig = AppConfigMapper.toDTO(updatedData);
    returnedConfig.setEnvVariables(backstageEnvSecretVariableList);
    returnedConfig.setProxy(proxyHostDetailList);
    return returnedConfig;
  }

  @Override
  public AppConfig saveOrUpdateConfigForAccount(AppConfig appConfig, String accountIdentifier, ConfigType configType)
      throws Exception {
    if (appConfigRepository.findByAccountIdentifierAndConfigId(accountIdentifier, appConfig.getConfigId()) == null) {
      return saveConfigForAccount(appConfig, accountIdentifier, configType);
    }
    return updateConfigForAccount(appConfig, accountIdentifier, configType);
  }

  @Override
  public AppConfig toggleConfigForAccount(
      String accountIdentifier, String configId, Boolean isEnabled, ConfigType configType) throws ExecutionException {
    AppConfigEntity updatedData = null;
    Boolean createdNewConfig = false;

    if (isEnabled) {
      AppConfigEntity appConfigEntity =
          appConfigRepository.findByAccountIdentifierAndConfigId(accountIdentifier, configId);

      if (appConfigEntity == null) {
        long currentTime = System.currentTimeMillis();
        AppConfigEntity pluginWithNoConfig = AppConfigEntity.builder()
                                                 .accountIdentifier(accountIdentifier)
                                                 .configType(configType)
                                                 .configId(configId)
                                                 .enabled(isEnabled)
                                                 .createdAt(currentTime)
                                                 .lastModifiedAt(currentTime)
                                                 .enabledDisabledAt(currentTime)
                                                 .build();
        updatedData = appConfigRepository.save(pluginWithNoConfig);
        createdNewConfig = true;
      }
    }

    if (!createdNewConfig) {
      updatedData = appConfigRepository.updateConfigEnablement(accountIdentifier, configId, isEnabled, configType);
    }

    if (!isEnabled) {
      configEnvVariablesService.deleteConfigEnvVariables(accountIdentifier, configId);
      pluginsProxyInfoService.deleteProxyHostDetailsForPlugin(accountIdentifier, configId);
    }

    if (isPluginWithNoConfig(accountIdentifier, configId)) {
      createOrUpdateTimeStampEnvVariable(accountIdentifier);
    }

    if (updatedData == null) {
      throw new InvalidRequestException(format(PLUGIN_CONFIG_NOT_FOUND, configId, accountIdentifier));
    }
    return AppConfigMapper.toDTO(updatedData);
  }

  @Override
  public MergedAppConfigEntity mergeAndSaveAppConfig(String accountIdentifier) throws Exception {
    String mergedAppConfig = mergeAllAppConfigsForAccount(accountIdentifier);
    if (!ConfigManagerUtils.isValidSchema(mergedAppConfig, readFileFromClassPath(MERGED_APP_CONFIG_JSON_SCHEMA_PATH))) {
      throw new InvalidRequestException(String.format(INVALID_MERGED_APP_CONFIG_SCHEMA, accountIdentifier));
    }
    updateConfigMap(accountIdentifier, mergedAppConfig, CONFIG_NAME);
    MergedAppConfigEntity mergedAppConfigEntity =
        MergedAppConfigMapper.getMergedAppConfigEntity(accountIdentifier, mergedAppConfig);
    return mergedAppConfigRepository.saveOrUpdate(mergedAppConfigEntity);
  }

  @Override
  public MergedPluginConfigs mergeEnabledPluginConfigsForAccount(String accountIdentifier) throws Exception {
    MergedPluginConfigs mergedPluginConfigs = new MergedPluginConfigs();
    List<String> allEnabledPluginConfigs = getAllEnabledPluginConfigs(accountIdentifier);
    boolean isAllEnabledPluginsWithNoConfig = allEnabledPluginConfigs.stream().allMatch(config -> config == null);
    if (allEnabledPluginConfigs.isEmpty() || isAllEnabledPluginsWithNoConfig) {
      log.info(String.format(NO_PLUGIN_ENABLED_FOR_ACCOUNT, accountIdentifier));
      return mergedPluginConfigs;
    }
    Iterator<String> itr = allEnabledPluginConfigs.iterator();
    String config = itr.next();
    itr.remove();
    JsonNode mergedPluginConfig = ConfigManagerUtils.asJsonNode(config);
    while (itr.hasNext()) {
      config = itr.next();
      if (config != null) {
        JsonNode pluginConfig = ConfigManagerUtils.asJsonNode(config);
        JsonNodeUtils.merge(mergedPluginConfig, pluginConfig);
        itr.remove();
      }
    }

    // fetching the env variables and corresponding secret identifier used while enabling the plugin
    List<String> enabledPluginIdsForAccount = getAllEnabledPluginIds(accountIdentifier);
    List<String> envVariablesForEnabledPlugins =
        getAllEnvVariablesForMultiplePluginIds(accountIdentifier, enabledPluginIdsForAccount);
    List<BackstageEnvSecretVariable> envVariableAndSecretList =
        backstageEnvVariableService.getAllSecretIdentifierForMultipleEnvVariablesInAccount(
            accountIdentifier, envVariablesForEnabledPlugins);

    List<ProxyHostDetail> proxyHostDetailForEnabledPlugins =
        pluginsProxyInfoService.getProxyHostDetailsForMultiplePluginIds(accountIdentifier, enabledPluginIdsForAccount);

    return mergedPluginConfigs.config(ConfigManagerUtils.asYaml(mergedPluginConfig.toString()))
        .envVariables(envVariableAndSecretList)
        .proxy(proxyHostDetailForEnabledPlugins);
  }

  @Override
  public List<AppConfigEntity> deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo() {
    return appConfigRepository.deleteDisabledPluginsConfigBasedOnTimestampsForEnabledDisabledTime(baseTimeStamp);
  }

  private String mergeAppConfigs(List<String> configs) throws Exception {
    String baseAppConfigPath = getBaseAppConfigPath();
    log.info("Base config path - {} for env - {}: ", baseAppConfigPath, env);
    String baseAppConfig = readFileFromClassPath(baseAppConfigPath);
    JsonNode baseConfig = ConfigManagerUtils.asJsonNode(baseAppConfig);
    Iterator<String> itr = configs.iterator();
    while (itr.hasNext()) {
      String config = itr.next();
      if (config != null) {
        JsonNode pluginConfig = ConfigManagerUtils.asJsonNode(config);
        JsonNodeUtils.merge(baseConfig, pluginConfig);
        itr.remove();
      }
    }
    return ConfigManagerUtils.asYaml(baseConfig.toString());
  }

  @Override
  public String mergeAllAppConfigsForAccount(String accountIdentifier) throws Exception {
    List<String> enabledPluginConfigs = getAllEnabledConfigs(accountIdentifier);
    return mergeAppConfigs(enabledPluginConfigs);
  }

  private List<String> getAllEnabledConfigs(String accountIdentifier) {
    List<AppConfigEntity> allEnabledConfigEntity =
        appConfigRepository.findAllByAccountIdentifierAndEnabled(accountIdentifier, true);
    if (allEnabledConfigEntity.isEmpty()) {
      log.info(format(NO_PLUGIN_ENABLED_FOR_ACCOUNT, accountIdentifier));
    }
    return allEnabledConfigEntity.stream().map(entity -> entity.getConfigs()).collect(Collectors.toList());
  }

  @Override
  public void updateConfigMap(String accountIdentifier, String appConfigYamlData, String configName) {
    Map<String, String> data = new HashMap<>();
    data.put(CONFIG_DATA_NAME, appConfigYamlData);
    String namespace = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier).getNamespace();
    k8sClient.updateConfigMapData(namespace, configName, data, true);
    log.info(
        "Config map successfully created/updated for account - {} in namespace - {}", accountIdentifier, namespace);
  }

  @Override
  public Boolean isPluginWithNoConfig(String accountIdentifier, String configId) {
    return appConfigRepository
               .findByAccountIdentifierAndConfigIdAndConfigType(accountIdentifier, configId, ConfigType.PLUGIN)
               .get()
               .getConfigs()
        == null;
  }

  public void validateSchemaForPlugin(String config, String configId) throws Exception {
    String pluginSchema = ConfigManagerUtils.getPluginConfigSchema(configId);
    if (pluginSchema == null) {
      throw new UnsupportedOperationException(String.format(INVALID_CONFIG_ID_PROVIDED, configId));
    }
    if (!ConfigManagerUtils.isValidSchema(config, pluginSchema)) {
      throw new InvalidRequestException(String.format(INVALID_PLUGIN_CONFIG_PROVIDED, configId));
    }
  }

  private List<AppConfigEntity> getAllEnabledPlugins(String accountIdentifier) {
    List<AppConfigEntity> allEnabledPluginConfigEntity =
        appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
            accountIdentifier, ConfigType.PLUGIN, true);
    if (allEnabledPluginConfigEntity.isEmpty()) {
      log.info(format(NO_PLUGIN_ENABLED_FOR_ACCOUNT, accountIdentifier));
    }
    return allEnabledPluginConfigEntity;
  }

  private List<String> getAllEnabledPluginConfigs(String accountIdentifier) {
    return getAllEnabledPlugins(accountIdentifier)
        .stream()
        .map(entity -> entity.getConfigs())
        .collect(Collectors.toList());
  }

  private List<String> getAllEnabledPluginIds(String accountIdentifier) {
    return getAllEnabledPlugins(accountIdentifier)
        .stream()
        .map(entity -> entity.getConfigId())
        .collect(Collectors.toList());
  }

  private List<String> getAllEnvVariablesForMultiplePluginIds(String accountIdentifier, List<String> pluginIds) {
    return configEnvVariablesService.getAllEnvVariablesForAccountIdentifierAndMultiplePluginIds(
        accountIdentifier, pluginIds);
  }

  private Boolean getEnabledFlagBasedOnConfigType(ConfigType configType) {
    if (configType.equals(ConfigType.PLUGIN)) {
      return false;
    }
    return true;
  }

  private void createOrUpdateTimeStampEnvVariable(String accountIdentifier) {
    BackstageEnvVariable timeStampEnvVariable = new BackstageEnvConfigVariable()
                                                    .value(String.valueOf(System.currentTimeMillis()))
                                                    .envName(Constants.LAST_UPDATED_TIMESTAMP_FOR_PLUGIN_WITH_NO_CONFIG)
                                                    .type(BackstageEnvVariable.TypeEnum.CONFIG);
    backstageEnvVariableService.createOrUpdate(Collections.singletonList(timeStampEnvVariable), accountIdentifier);
  }

  private String getBaseAppConfigPath() {
    switch (env) {
      case "qa":
        return BASE_APP_CONFIG_PATH_QA;
      case "stress":
        return BASE_APP_CONFIG_PATH_PRE_QA;
      case "compliance":
        return BASE_APP_CONFIG_PATH_COMPLIANCE;
      default:
        return BASE_APP_CONFIG_PATH;
    }
  }
}
