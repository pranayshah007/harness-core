/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.configmanager.ReservedEnvVariables;
import io.harness.idp.configmanager.beans.entity.PluginConfigEnvVariablesEntity;
import io.harness.idp.configmanager.mappers.ConfigEnvVariablesMapper;
import io.harness.idp.configmanager.repositories.ConfigEnvVariablesRepository;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @com.google.inject.Inject }))
public class ConfigEnvVariablesServiceImpl implements ConfigEnvVariablesService {
  ConfigEnvVariablesRepository configEnvVariablesRepository;
  BackstageEnvVariableService backstageEnvVariableService;

  private static final String NO_ENV_VARIABLES_FOUND = "No env variables are added for given Plugins in account - {}";
  private static final String NO_ENV_VARIABLE_ASSOCIATED =
      "No env variables are associated with Plugin id - {} for account - {}";
  private static final String RESERVED_ENV_VARIABLE_MESSAGE =
      "%s - is reserved env variable name, please use some other env variable name";
  private static final String CONFLICTING_ENV_VARIABLE_MESSAGE =
      "%s - is already used in plugin - %s , please use some other env variable name";

  @Override
  public List<BackstageEnvSecretVariable> insertConfigEnvVariables(AppConfig appConfig, String accountIdentifier)
      throws Exception {
    List<PluginConfigEnvVariablesEntity> configVariables =
        ConfigEnvVariablesMapper.getEntitiesForEnvVariables(appConfig, accountIdentifier);
    if (configVariables.isEmpty()) {
      log.info(NO_ENV_VARIABLE_ASSOCIATED, appConfig.getConfigId(), accountIdentifier);
      return new ArrayList<>();
    }
    List<String> errorMessagesForEnvVariables = getErrorMessagesForEnvVariables(appConfig, accountIdentifier);
    if (!errorMessagesForEnvVariables.isEmpty()) {
      throw new InvalidRequestException(new Gson().toJson(errorMessagesForEnvVariables));
    }

    configEnvVariablesRepository.saveAll(configVariables);

    // creating secrets on the namespace of backstage and storing in DB
    List<BackstageEnvVariable> backstageEnvVariableList = getListOfBackstageEnvSecretVariable(appConfig);
    List<BackstageEnvVariable> backstageEnvVariables =
        backstageEnvVariableService.createMulti(backstageEnvVariableList, accountIdentifier);
    List<BackstageEnvSecretVariable> returnList = new ArrayList<>();
    for (BackstageEnvVariable backstageEnvVariable : backstageEnvVariables) {
      returnList.add((BackstageEnvSecretVariable) backstageEnvVariable);
    }
    return returnList;
  }

  @Override
  public List<BackstageEnvSecretVariable> updateConfigEnvVariables(AppConfig appConfig, String accountIdentifier)
      throws Exception {
    List<PluginConfigEnvVariablesEntity> configVariables =
        ConfigEnvVariablesMapper.getEntitiesForEnvVariables(appConfig, accountIdentifier);
    if (configVariables.isEmpty()) {
      log.info(NO_ENV_VARIABLE_ASSOCIATED, appConfig.getConfigId(), accountIdentifier);
    }
    List<String> errorMessagesForEnvVariables = getErrorMessagesForEnvVariables(appConfig, accountIdentifier);
    if (!errorMessagesForEnvVariables.isEmpty()) {
      throw new InvalidRequestException(new Gson().toJson(errorMessagesForEnvVariables));
    }

    // Deleting older crated env secret variables
    deleteConfigEnvVariables(accountIdentifier, appConfig.getConfigId());

    // creating new updated env variables
    return insertConfigEnvVariables(appConfig, accountIdentifier);
  }

  @Override
  public List<String> getAllEnvVariablesForAccountIdentifierAndMultiplePluginIds(
      String accountIdentifier, List<String> pluginIds) {
    List<PluginConfigEnvVariablesEntity> envVariableForPlugins =
        configEnvVariablesRepository.getAllEnvVariablesForMultiplePluginIds(accountIdentifier, pluginIds);
    if (envVariableForPlugins.isEmpty()) {
      log.info(NO_ENV_VARIABLES_FOUND, accountIdentifier);
    }
    return envVariableForPlugins.stream().map(entity -> entity.getEnvName()).collect(Collectors.toList());
  }

  @Override
  public void deleteConfigEnvVariables(String accountIdentifier, String configId) {
    List<PluginConfigEnvVariablesEntity> pluginsEnvVariablesEntity =
        configEnvVariablesRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, configId);
    configEnvVariablesRepository.deleteAllByPluginId(configId);
    backstageEnvVariableService.deleteMultiUsingEnvNames(
        getEnvVariablesFromEntities(pluginsEnvVariablesEntity), accountIdentifier);
  }

  private List<BackstageEnvVariable> getListOfBackstageEnvSecretVariable(AppConfig appConfig) {
    List<BackstageEnvSecretVariable> appConfigEnvVariables = appConfig.getEnvVariables();
    List<BackstageEnvVariable> resultList = new ArrayList<>();
    for (BackstageEnvSecretVariable backstageEnvSecretVariable : appConfigEnvVariables) {
      backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
      long currentTime = System.currentTimeMillis();
      backstageEnvSecretVariable.setCreated(currentTime);
      backstageEnvSecretVariable.setUpdated(currentTime);
      resultList.add(backstageEnvSecretVariable);
    }
    return resultList;
  }

  private List<String> getEnvVariablesFromEntities(
      List<PluginConfigEnvVariablesEntity> pluginConfigEnvVariablesEntities) {
    List<String> resultList = new ArrayList<>();
    for (PluginConfigEnvVariablesEntity pluginConfigEnvVariablesEntity : pluginConfigEnvVariablesEntities) {
      resultList.add(pluginConfigEnvVariablesEntity.getEnvName());
    }
    return resultList;
  }

  private List<String> getErrorMessagesForEnvVariables(AppConfig appConfig, String accountIdentifier) {
    List<BackstageEnvSecretVariable> listEnvVariables = appConfig.getEnvVariables();
    List<String> messageList = new ArrayList<>();
    for (BackstageEnvSecretVariable backstageEnvSecretVariable : listEnvVariables) {
      String envName = backstageEnvSecretVariable.getEnvName();
      if (isReservedEnvVariable(envName)) {
        messageList.add(String.format(RESERVED_ENV_VARIABLE_MESSAGE, envName));
      } else {
        PluginConfigEnvVariablesEntity pluginConfigEnvVariablesEntity =
            configEnvVariablesRepository.findByAccountIdentifierAndEnvName(accountIdentifier, envName);
        if (pluginConfigEnvVariablesEntity != null
            && !pluginConfigEnvVariablesEntity.getPluginId().equals(appConfig.getConfigId())) {
          messageList.add(String.format(CONFLICTING_ENV_VARIABLE_MESSAGE, pluginConfigEnvVariablesEntity.getEnvName(),
              pluginConfigEnvVariablesEntity.getPluginName()));
        }
      }
    }
    return messageList;
  }

  private Boolean isReservedEnvVariable(String envVariableName) {
    return ReservedEnvVariables.reservedEnvVariables.contains(envVariableName);
  }
}
