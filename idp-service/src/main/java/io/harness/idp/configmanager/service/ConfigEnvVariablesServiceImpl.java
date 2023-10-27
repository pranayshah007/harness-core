/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.configmanager.beans.entity.PluginConfigEnvVariablesEntity;
import io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretCreateEvent;
import io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretDeleteEvent;
import io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretUpdateEvent;
import io.harness.idp.configmanager.mappers.ConfigEnvVariablesMapper;
import io.harness.idp.configmanager.repositories.ConfigEnvVariablesRepository;
import io.harness.idp.configmanager.utils.ReservedEnvVariables;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @com.google.inject.Inject }))
public class ConfigEnvVariablesServiceImpl implements ConfigEnvVariablesService {
  ConfigEnvVariablesRepository configEnvVariablesRepository;
  BackstageEnvVariableService backstageEnvVariableService;

  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  private static final String NO_ENV_VARIABLES_FOUND = "No env variables are added for given Plugins in account - {}";
  private static final String NO_ENV_VARIABLE_ASSOCIATED =
      "No env variables are associated with Plugin id - {} for account - {}";
  private static final String RESERVED_ENV_VARIABLE_MESSAGE =
      "%s - is reserved env variable name, please use some other env variable name";
  private static final String CONFLICTING_ENV_VARIABLE_MESSAGE =
      "%s - is already used in plugin - %s , please use some other env variable name";

  private static final String ENV_VARIABLE_NOT_CONFIGURED_ERROR_MESSAGE = "Please set value for %s";

  @Override
  public List<BackstageEnvSecretVariable> insertConfigEnvVariables(AppConfig appConfig, String accountIdentifier) {
    List<PluginConfigEnvVariablesEntity> configVariables =
        ConfigEnvVariablesMapper.getEntitiesForEnvVariables(appConfig, appConfig.getEnvVariables(), accountIdentifier);
    List<String> errorMessagesForEnvVariables = getErrorMessagesForEnvVariables(appConfig, accountIdentifier);
    if (!errorMessagesForEnvVariables.isEmpty()) {
      throw new InvalidRequestException(new Gson().toJson(errorMessagesForEnvVariables));
    }

    // Deleting older crated env secret variables
    deleteConfigEnvVariables(accountIdentifier, appConfig.getConfigId());

    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      configEnvVariablesRepository.saveAll(configVariables);
      for (BackstageEnvSecretVariable backstageEnvSecretVariable : appConfig.getEnvVariables()) {
        outboxService.save(new BackstageEnvSecretCreateEvent(accountIdentifier, backstageEnvSecretVariable));
      }
      return true;
    }));

    // creating secrets on the namespace of backstage and storing in DB
    List<BackstageEnvVariable> backstageEnvVariableList =
        getListOfBackstageEnvSecretVariable(appConfig.getEnvVariables());
    List<BackstageEnvVariable> backstageEnvVariables =
        backstageEnvVariableService.createOrUpdate(backstageEnvVariableList, accountIdentifier);
    List<BackstageEnvSecretVariable> returnList = new ArrayList<>();
    for (BackstageEnvVariable backstageEnvVariable : backstageEnvVariables) {
      returnList.add((BackstageEnvSecretVariable) backstageEnvVariable);
    }
    return returnList;
  }

  @Override
  public List<BackstageEnvSecretVariable> updateConfigEnvVariables(AppConfig appConfig, String accountIdentifier) {
    if (appConfig.getEnvVariables().isEmpty()) {
      log.info(NO_ENV_VARIABLE_ASSOCIATED, appConfig.getConfigId(), accountIdentifier);
    }
    List<String> errorMessagesForEnvVariables = getErrorMessagesForEnvVariables(appConfig, accountIdentifier);
    if (!errorMessagesForEnvVariables.isEmpty()) {
      throw new InvalidRequestException(new Gson().toJson(errorMessagesForEnvVariables));
    }

    List<PluginConfigEnvVariablesEntity> oldEnvVariables =
        configEnvVariablesRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, appConfig.getConfigId());

    List<BackstageEnvSecretVariable> oldBackstageEnvVariables =
        backstageEnvVariableService.getAllSecretIdentifierForMultipleEnvVariablesInAccount(accountIdentifier,
            oldEnvVariables.stream().map(PluginConfigEnvVariablesEntity::getEnvName).collect(Collectors.toList()));

    Map<String, BackstageEnvSecretVariable> oldBackstageEnvVariableMap = oldBackstageEnvVariables.stream().collect(
        Collectors.toMap(BackstageEnvSecretVariable::getIdentifier, Function.identity()));

    List<BackstageEnvSecretVariable> newBackstageEnvVariables = appConfig.getEnvVariables();

    // newly added env variables
    List<BackstageEnvSecretVariable> newlyAddedEnvSecretVariables =
        newBackstageEnvVariables.stream()
            .filter(backstageEnvSecretVariable -> backstageEnvSecretVariable.getIdentifier() == null)
            .collect(Collectors.toList());

    // removing the newly added env variables from the list for the update case
    newBackstageEnvVariables.removeAll(newlyAddedEnvSecretVariables);

    // delete all the older created env variables not in use
    deleteOlderEnvVariablesNotInUse(accountIdentifier, appConfig, oldBackstageEnvVariables, newBackstageEnvVariables);

    List<BackstageEnvSecretVariable> returnList = new ArrayList<>();

    // inserting newly added env variables
    returnList.addAll(insertNewlyCreatedEnvVariables(accountIdentifier, newlyAddedEnvSecretVariables, appConfig));

    // update cases
    returnList.addAll(
        updateExistingEnvVariables(accountIdentifier, newBackstageEnvVariables, appConfig, oldBackstageEnvVariableMap));

    return returnList;
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
    if (!pluginsEnvVariablesEntity.isEmpty()) {
      configEnvVariablesRepository.deleteAllByAccountIdentifierAndPluginId(accountIdentifier, configId);
      backstageEnvVariableService.deleteMultiUsingEnvNames(
          getEnvVariablesFromEntities(pluginsEnvVariablesEntity), accountIdentifier);
    }
  }

  @Override
  public List<String> getAllEnvVariablesForAccountIdentifierAndPluginId(String accountIdentifier, String pluginId) {
    List<PluginConfigEnvVariablesEntity> pluginsEnvVariablesEntity =
        configEnvVariablesRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, pluginId);
    return pluginsEnvVariablesEntity.stream()
        .map(PluginConfigEnvVariablesEntity::getEnvName)
        .collect(Collectors.toList());
  }

  @Override
  public void validateConfigEnvVariables(AppConfig appConfig) {
    // regex for fetching text present in ${..}
    Pattern pattern = Pattern.compile("\\$\\{(.*?)\\}");

    Matcher matcher = pattern.matcher(appConfig.getConfigs());

    Set<String> fetchedEnvVariablesFromConfig = new HashSet<>();

    while (matcher.find()) {
      fetchedEnvVariablesFromConfig.add(matcher.group(1));
    }
    Set<String> configuredEnvVariables =
        getAllEnvVariableNamesFromBackstageEnvSecretVariable(appConfig.getEnvVariables());

    Set<String> notConfiguredEnvVariables = Sets.difference(fetchedEnvVariablesFromConfig, configuredEnvVariables);

    if (!notConfiguredEnvVariables.isEmpty()) {
      throw new UnsupportedOperationException(
          String.format(ENV_VARIABLE_NOT_CONFIGURED_ERROR_MESSAGE, String.join(", ", notConfiguredEnvVariables)));
    }
  }

  @VisibleForTesting
  Set<String> getAllEnvVariableNamesFromBackstageEnvSecretVariable(
      List<BackstageEnvSecretVariable> backstageEnvSecretVariables) {
    return backstageEnvSecretVariables.stream().map(entity -> entity.getEnvName()).collect(Collectors.toSet());
  }

  private List<BackstageEnvVariable> getListOfBackstageEnvSecretVariable(
      List<BackstageEnvSecretVariable> appConfigEnvVariables) {
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

  @VisibleForTesting
  List<String> getEnvVariablesFromEntities(List<PluginConfigEnvVariablesEntity> pluginConfigEnvVariablesEntities) {
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

  private void deleteOlderEnvVariablesNotInUse(String accountIdentifier, AppConfig appConfig,
      List<BackstageEnvSecretVariable> oldBackstageEnvVariables,
      List<BackstageEnvSecretVariable> newBackstageEnvVariables) {
    // remove the env variables from oldBackstageEnvVariables that are present in newBackstageEnvVariables
    oldBackstageEnvVariables.removeIf(backstageEnvSecretVariable
        -> newBackstageEnvVariables.stream().anyMatch(
            backstageEnv -> backstageEnv.getIdentifier().equals(backstageEnvSecretVariable.getIdentifier())));

    // Deleting the older created env variables that are deleted from UI.

    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      // removing all the mappings
      configEnvVariablesRepository.deleteAllByAccountIdentifierAndPluginId(accountIdentifier, appConfig.getConfigId());

      if (!oldBackstageEnvVariables.isEmpty()) {
        log.info("Deleted env variables - {}", oldBackstageEnvVariables);
        List<String> toBeDeletedEnvVariables =
            oldBackstageEnvVariables.stream().map(BackstageEnvSecretVariable::getEnvName).collect(Collectors.toList());
        backstageEnvVariableService.deleteMultiUsingEnvNames(toBeDeletedEnvVariables, accountIdentifier);
        for (BackstageEnvSecretVariable oldEnvVariable : oldBackstageEnvVariables) {
          outboxService.save(new BackstageEnvSecretDeleteEvent(accountIdentifier, oldEnvVariable));
        }
      }

      return true;
    }));
  }

  private List<BackstageEnvSecretVariable> insertNewlyCreatedEnvVariables(
      String accountIdentifier, List<BackstageEnvSecretVariable> newlyAddedEnvSecretVariables, AppConfig appConfig) {
    // inserting newly added env variables
    List<PluginConfigEnvVariablesEntity> newlyAddedConfigEnvVariables =
        ConfigEnvVariablesMapper.getEntitiesForEnvVariables(appConfig, newlyAddedEnvSecretVariables, accountIdentifier);

    List<BackstageEnvSecretVariable> returnList = new ArrayList<>();
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      log.info("Newly added env variables  - {}", newlyAddedEnvSecretVariables);
      configEnvVariablesRepository.saveAll(newlyAddedConfigEnvVariables);

      for (BackstageEnvSecretVariable backstageEnvSecretVariable : newlyAddedEnvSecretVariables) {
        outboxService.save(new BackstageEnvSecretCreateEvent(accountIdentifier, backstageEnvSecretVariable));
      }

      List<BackstageEnvVariable> newlyAddedEnvVariablesToSave =
          getListOfBackstageEnvSecretVariable(newlyAddedEnvSecretVariables);
      List<BackstageEnvVariable> newlyAddedEnvVariablesSaved =
          backstageEnvVariableService.createOrUpdate(newlyAddedEnvVariablesToSave, accountIdentifier);

      for (BackstageEnvVariable backstageEnvVariable : newlyAddedEnvVariablesSaved) {
        returnList.add((BackstageEnvSecretVariable) backstageEnvVariable);
      }

      return true;
    }));

    return returnList;
  }

  private List<BackstageEnvSecretVariable> updateExistingEnvVariables(String accountIdentifier,
      List<BackstageEnvSecretVariable> newBackstageEnvVariables, AppConfig appConfig,
      Map<String, BackstageEnvSecretVariable> oldBackstageEnvVariableMap) {
    List<PluginConfigEnvVariablesEntity> updatedConfigEnvVariables =
        ConfigEnvVariablesMapper.getEntitiesForEnvVariables(appConfig, newBackstageEnvVariables, accountIdentifier);
    List<BackstageEnvSecretVariable> returnList = new ArrayList<>();

    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      configEnvVariablesRepository.saveAll(updatedConfigEnvVariables);

      log.info("Updated env variables  - {}", updatedConfigEnvVariables);

      for (BackstageEnvSecretVariable newBackstageEnvSecretVariable : newBackstageEnvVariables) {
        String newEnvVariableIdentifier = newBackstageEnvSecretVariable.getIdentifier();
        if (!newBackstageEnvSecretVariable.getEnvName().equals(
                oldBackstageEnvVariableMap.get(newEnvVariableIdentifier).getEnvName())
            || !newBackstageEnvSecretVariable.getHarnessSecretIdentifier().equals(
                oldBackstageEnvVariableMap.get(newEnvVariableIdentifier).getHarnessSecretIdentifier())) {
          outboxService.save(new BackstageEnvSecretUpdateEvent(accountIdentifier, newBackstageEnvSecretVariable,
              oldBackstageEnvVariableMap.get(newBackstageEnvSecretVariable.getIdentifier())));
        }
      }

      List<BackstageEnvVariable> envVariablesToUpdate = getListOfBackstageEnvSecretVariable(newBackstageEnvVariables);
      List<BackstageEnvVariable> envVariablesUpdated =
          backstageEnvVariableService.createOrUpdate(envVariablesToUpdate, accountIdentifier);

      for (BackstageEnvVariable backstageEnvVariable : envVariablesUpdated) {
        returnList.add((BackstageEnvSecretVariable) backstageEnvVariable);
      }
      return true;
    }));

    return returnList;
  }
}
