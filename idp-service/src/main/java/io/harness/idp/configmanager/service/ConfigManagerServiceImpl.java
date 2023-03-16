/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.service;

import static java.lang.String.format;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.io.Resources;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.mappers.AppConfigMapper;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.jackson.JsonNodeUtils;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.AppConfigRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
  private static final String NO_PLUGIN_ENABLED_FOR_ACCOUNT = "No plugin is enabled for account - %s";

  private static final String BASE_APP_CONFIG_PATH="baseappconfig.yaml";

  @Override
  public AppConfig getPluginConfig(String accountIdentifier, String pluginId) {
    Optional<AppConfigEntity> pluginConfig =
        appConfigRepository.findByAccountIdentifierAndPluginId(accountIdentifier, pluginId);
    if (pluginConfig.isEmpty()) {
      throw new InvalidRequestException(format(PLUGIN_CONFIG_NOT_FOUND, pluginId, accountIdentifier));
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

  @Override
  public Map getPluginEnablementForAccount(String accountIdentifier){
    List<AppConfigEntity> allPluginConfig = appConfigRepository.findAllByAccountIdentifier(accountIdentifier);
    Map map = allPluginConfig.stream().collect(Collectors.toMap(AppConfigEntity::getPluginId, AppConfigEntity::getEnabled));
    return map;
  }

  @Override
  public List<String> getAllEnabledPluginConfigs(String accountIdentifier){
    List<AppConfigEntity> allEnabledPluginEntity = appConfigRepository.findAllByAccountIdentifierAndEnabled(accountIdentifier, true);
    if(allEnabledPluginEntity.isEmpty()){
      throw new InvalidRequestException(format(NO_PLUGIN_ENABLED_FOR_ACCOUNT, accountIdentifier));
    }
    List<String> allEnabledPlugin=allEnabledPluginEntity.stream().map(entity -> entity.getConfigs()).collect(Collectors.toList());
    return allEnabledPlugin;
  }

  @Override
  public String mergeAppConfigs(List<String> configs) throws Exception{
    String baseAppConfig = readFile(BASE_APP_CONFIG_PATH);
    JsonNode baseConfig = asJsonNode(baseAppConfig);
    Iterator itr = configs.iterator();
    while (itr.hasNext()){
      String config = itr.next().toString();
      JsonNode pluginConfig = asJsonNode(config);
      baseConfig = JsonNodeUtils.merge(baseConfig, pluginConfig);
      itr.remove();
    }
    return asYaml(baseConfig.toString());
  }


  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  public String asYaml(String jsonString) throws JsonProcessingException, IOException {
    // parse JSON
    JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
    // save it as YAML
    String jsonAsYaml = new YAMLMapper().configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true).writeValueAsString(jsonNodeTree);
    return jsonAsYaml;
  }


  public JsonNode asJsonNode(String yamlString) throws JsonProcessingException, IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNode = mapper.readTree(yamlString);
    return jsonNode;
  }

}
