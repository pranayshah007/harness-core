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
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.configmanager.beans.entity.PluginsProxyInfoEntity;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostCreateEvent;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostDeleteEvent;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostUpdateEvent;
import io.harness.idp.configmanager.mappers.PluginsProxyInfoMapper;
import io.harness.idp.configmanager.repositories.PluginsProxyInfoRepository;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.json.JSONObject;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @com.google.inject.Inject }))
public class PluginsProxyInfoServiceImpl implements PluginsProxyInfoService {
  private PluginsProxyInfoRepository pluginsProxyInfoRepository;
  private DelegateSelectorsCache delegateSelectorsCache;
  private ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  private static final String ERROR_MESSAGE_FOR_PROXY =
      "Host - %s is already used in plugin - %s, please configure it from configurations page.";

  private static final String NO_PROXY_HOST_ASSOCIATED_VARIABLE_ASSOCIATED =
      "No proxy hosts are associated with Plugin id - {} for account - {}";

  @Override
  public List<ProxyHostDetail> insertProxyHostDetailsForPlugin(
      AppConfig appConfig, String accountIdentifier, ConfigType configType) {
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities = getPluginProxyInfoEntities(appConfig, accountIdentifier);
    if (!ConfigType.PLUGIN.equals(configType)) {
      return Collections.emptyList();
    }

    List<String> errorMessageForProxyDetails = getErrorMessageIfHostIsAlreadyInUse(accountIdentifier, appConfig);
    if (!errorMessageForProxyDetails.isEmpty()) {
      throw new InvalidRequestException(new Gson().toJson(errorMessageForProxyDetails));
    }

    // deleting old proxy host details
    List<PluginsProxyInfoEntity> existingPluginProxies =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, appConfig.getConfigId());
    JSONObject hostProxyMap = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);

    if (!existingPluginProxies.isEmpty()) {
      removeHostsFromMapAndCache(accountIdentifier, existingPluginProxies, hostProxyMap);
      pluginsProxyInfoRepository.deleteAllByAccountIdentifierAndPluginId(accountIdentifier, appConfig.getConfigId());
    }

    // add new proxy host details
    return insertNewlyCreatedHostProxy(accountIdentifier, pluginsProxyInfoEntities);
  }

  @Override
  public List<ProxyHostDetail> updateProxyHostDetailsForPlugin(
      AppConfig appConfig, String accountIdentifier, ConfigType configType) {
    if (appConfig.getProxy().isEmpty()) {
      log.info(NO_PROXY_HOST_ASSOCIATED_VARIABLE_ASSOCIATED, appConfig.getConfigId(), accountIdentifier);
    }

    List<String> errorMessageForProxyDetails = getErrorMessageIfHostIsAlreadyInUse(accountIdentifier, appConfig);
    if (!errorMessageForProxyDetails.isEmpty()) {
      throw new InvalidRequestException(new Gson().toJson(errorMessageForProxyDetails));
    }

    List<PluginsProxyInfoEntity> oldPluginProxyInfoEntities =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, appConfig.getConfigId());

    Map<String, PluginsProxyInfoEntity> oldPluginProxyInfoEntityMap = oldPluginProxyInfoEntities.stream().collect(
        Collectors.toMap(PluginsProxyInfoEntity::getId, Function.identity()));

    List<PluginsProxyInfoEntity> proxyHostDetailsToUpdate = getPluginProxyInfoEntities(appConfig, accountIdentifier);

    // newly added proxy host details
    List<PluginsProxyInfoEntity> newlyAddedProxyHostsDetails =
        proxyHostDetailsToUpdate.stream()
            .filter(proxyHostDetail -> proxyHostDetail.getId() == null)
            .collect(Collectors.toList());

    // removing the newly added proxy host from the list for the update case
    proxyHostDetailsToUpdate.removeAll(newlyAddedProxyHostsDetails);

    // delete all the older created proxy host not in use
    deleteOlderProxyHostNotInUse(accountIdentifier, oldPluginProxyInfoEntities, proxyHostDetailsToUpdate);

    List<ProxyHostDetail> returnList = new ArrayList<>();

    // insert newly created hosts
    returnList.addAll(insertNewlyCreatedHostProxy(accountIdentifier, newlyAddedProxyHostsDetails));

    // update case
    returnList.addAll(
        updateExistingProxyHosts(accountIdentifier, proxyHostDetailsToUpdate, oldPluginProxyInfoEntityMap));

    return returnList;
  }

  @Override
  public void deleteProxyHostDetailsForPlugin(String accountIdentifier, String pluginId) {
    List<PluginsProxyInfoEntity> existingPluginProxies =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, pluginId);
    if (!existingPluginProxies.isEmpty()) {
      Set<String> hostsToBeRemoved =
          existingPluginProxies.stream().map(PluginsProxyInfoEntity::getHost).collect(Collectors.toSet());
      delegateSelectorsCache.remove(accountIdentifier, hostsToBeRemoved);
      proxyEnvVariableServiceWrapper.removeFromHostProxyEnvVariable(accountIdentifier, hostsToBeRemoved);
      pluginsProxyInfoRepository.deleteAllByAccountIdentifierAndPluginId(accountIdentifier, pluginId);
    }
  }

  @Override
  public List<ProxyHostDetail> getProxyHostDetailsForMultiplePluginIds(
      String accountIdentifier, List<String> pluginIds) {
    List<PluginsProxyInfoEntity> pluginProxyHostDetailsForPlugins =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginIds(accountIdentifier, pluginIds);
    return getPluginProxyHostDetailsFromEntities(pluginProxyHostDetailsForPlugins);
  }

  @Override
  public List<ProxyHostDetail> updateProxyHostDetailsForHostValues(
      List<ProxyHostDetail> proxyHostDetails, String accountIdentifier) {
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities = new ArrayList<>();
    for (ProxyHostDetail proxyHostDetail : proxyHostDetails) {
      PluginsProxyInfoEntity pluginsProxyInfoEntity =
          pluginsProxyInfoRepository.updatePluginProxyInfo(proxyHostDetail, accountIdentifier);
      if (pluginsProxyInfoEntity != null) {
        pluginsProxyInfoEntities.add(pluginsProxyInfoEntity);
      }
    }
    return getPluginProxyHostDetailsFromEntities(pluginsProxyInfoEntities);
  }

  @Override
  public List<ProxyHostDetail> getProxyHostDetailsForPluginId(String accountIdentifier, String pluginId) {
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, pluginId);
    return getPluginProxyHostDetailsFromEntities(pluginsProxyInfoEntities);
  }

  @VisibleForTesting
  List<String> getErrorMessageIfHostIsAlreadyInUse(String accountIdentifier, AppConfig appConfig) {
    List<ProxyHostDetail> proxyDetails = appConfig.getProxy();
    List<String> errorMessage = new ArrayList<>();
    for (ProxyHostDetail proxyHostDetail : proxyDetails) {
      PluginsProxyInfoEntity pluginsProxyInfoEntity =
          pluginsProxyInfoRepository.findByAccountIdentifierAndHost(accountIdentifier, proxyHostDetail.getHost());
      if (pluginsProxyInfoEntity != null && !pluginsProxyInfoEntity.getPluginId().equals(appConfig.getConfigId())) {
        errorMessage.add(
            String.format(ERROR_MESSAGE_FOR_PROXY, proxyHostDetail.getHost(), pluginsProxyInfoEntity.getPluginId()));
      }
    }
    return errorMessage;
  }

  @VisibleForTesting
  List<PluginsProxyInfoEntity> getPluginProxyInfoEntities(AppConfig appConfig, String accountIdentifier) {
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities = new ArrayList<>();
    if (appConfig.getProxy() == null) {
      return pluginsProxyInfoEntities;
    }
    for (ProxyHostDetail proxyHostDetail : appConfig.getProxy()) {
      pluginsProxyInfoEntities.add(PluginsProxyInfoEntity.builder()
                                       .id(proxyHostDetail.getIdentifier())
                                       .accountIdentifier(accountIdentifier)
                                       .pluginId(appConfig.getConfigId())
                                       .createdAt(System.currentTimeMillis())
                                       .lastModifiedAt(System.currentTimeMillis())
                                       .host(proxyHostDetail.getHost())
                                       .proxy(proxyHostDetail.isProxy())
                                       .delegateSelectors(proxyHostDetail.getSelectors())
                                       .build());
    }
    return pluginsProxyInfoEntities;
  }

  private List<ProxyHostDetail> getPluginProxyHostDetailsFromEntities(
      List<PluginsProxyInfoEntity> pluginsProxyInfoEntities) {
    List<ProxyHostDetail> returnList = new ArrayList<>();
    for (PluginsProxyInfoEntity pluginsProxyInfoEntity : pluginsProxyInfoEntities) {
      ProxyHostDetail proxyHostDetail = new ProxyHostDetail();
      proxyHostDetail.setIdentifier(pluginsProxyInfoEntity.getId());
      proxyHostDetail.setPluginId(pluginsProxyInfoEntity.getPluginId());
      proxyHostDetail.setHost(pluginsProxyInfoEntity.getHost());
      proxyHostDetail.setProxy(pluginsProxyInfoEntity.getProxy());
      proxyHostDetail.setSelectors(pluginsProxyInfoEntity.getDelegateSelectors());
      returnList.add(proxyHostDetail);
    }
    return returnList;
  }

  private void deleteOlderProxyHostNotInUse(String accountIdentifier,
      List<PluginsProxyInfoEntity> oldProxyHostDetailEntities, List<PluginsProxyInfoEntity> proxyHostDetailsToUpdate) {
    // remove the proxy host details from oldProxyHostDetails that are present in newProxyHostDetails
    oldProxyHostDetailEntities.removeIf(proxyInfoEntity
        -> proxyHostDetailsToUpdate.stream().anyMatch(proxyHost -> proxyHost.getId().equals(proxyInfoEntity.getId())));

    // Deleting the older created proxy host details that are deleted from UI.
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      if (!oldProxyHostDetailEntities.isEmpty()) {
        log.info("Deleted proxy details - {}", oldProxyHostDetailEntities);
        JSONObject hostProxyMap = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);
        JSONObject originalHostProxyMap = new JSONObject(hostProxyMap.toString());

        removeHostsFromMapAndCache(accountIdentifier, oldProxyHostDetailEntities, hostProxyMap);

        List<String> proxyHostDetailsIdsToBeDeleted =
            oldProxyHostDetailEntities.stream().map(PluginsProxyInfoEntity::getId).collect(Collectors.toList());
        pluginsProxyInfoRepository.deleteAllByAccountIdentifierAndIdIn(
            accountIdentifier, proxyHostDetailsIdsToBeDeleted);

        for (PluginsProxyInfoEntity oldProxyHostDetail : oldProxyHostDetailEntities) {
          outboxService.save(
              new ProxyHostDeleteEvent(accountIdentifier, PluginsProxyInfoMapper.toDto(oldProxyHostDetail)));
        }

        if (!originalHostProxyMap.similar(hostProxyMap)) {
          proxyEnvVariableServiceWrapper.setHostProxyMap(accountIdentifier, hostProxyMap);
        }
      }
      return true;
    }));
  }

  private List<ProxyHostDetail> insertNewlyCreatedHostProxy(
      String accountIdentifier, List<PluginsProxyInfoEntity> pluginsProxyInfoEntities) {
    JSONObject hostProxyMap = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);
    JSONObject originalHostProxyMap = new JSONObject(hostProxyMap.toString());

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      List<ProxyHostDetail> returnList = new ArrayList<>();

      for (PluginsProxyInfoEntity pluginsProxyInfoEntity : pluginsProxyInfoEntities) {
        if (pluginsProxyInfoEntity.getProxy()) {
          putHostInMapAndCache(accountIdentifier, pluginsProxyInfoEntity, hostProxyMap);
        }
        ProxyHostDetail proxyHostDetail = PluginsProxyInfoMapper.toDto(pluginsProxyInfoEntity);
        outboxService.save(new ProxyHostCreateEvent(accountIdentifier, proxyHostDetail));
      }

      List<PluginsProxyInfoEntity> savedProxyHosts =
          (List<PluginsProxyInfoEntity>) pluginsProxyInfoRepository.saveAll(pluginsProxyInfoEntities);
      for (PluginsProxyInfoEntity pluginsProxyInfoEntity : savedProxyHosts) {
        returnList.add(PluginsProxyInfoMapper.toDto(pluginsProxyInfoEntity));
      }

      if (!originalHostProxyMap.similar(hostProxyMap)) {
        proxyEnvVariableServiceWrapper.setHostProxyMap(accountIdentifier, hostProxyMap);
      }
      return returnList;
    }));
  }

  private List<ProxyHostDetail> updateExistingProxyHosts(String accountIdentifier,
      List<PluginsProxyInfoEntity> proxyHostDetailsToUpdate,
      Map<String, PluginsProxyInfoEntity> oldPluginProxyInfoEntityMap) {
    List<ProxyHostDetail> returnList = new ArrayList<>();

    JSONObject hostProxyMap = proxyEnvVariableServiceWrapper.getHostProxyMap(accountIdentifier);
    JSONObject originalHostProxyMap = new JSONObject(hostProxyMap.toString());

    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      log.info("Updated proxy hosts  - {}", proxyHostDetailsToUpdate);

      for (PluginsProxyInfoEntity pluginsProxyInfoEntity : proxyHostDetailsToUpdate) {
        String newEnvVariableIdentifier = pluginsProxyInfoEntity.getId();
        if (!pluginsProxyInfoEntity.getHost().equals(
                oldPluginProxyInfoEntityMap.get(newEnvVariableIdentifier).getHost())
            || !pluginsProxyInfoEntity.getDelegateSelectors().equals(
                oldPluginProxyInfoEntityMap.get(newEnvVariableIdentifier).getDelegateSelectors())) {
          removeHostsFromMapAndCache(accountIdentifier,
              Collections.singletonList(oldPluginProxyInfoEntityMap.get(newEnvVariableIdentifier)), hostProxyMap);
          putHostInMapAndCache(accountIdentifier, pluginsProxyInfoEntity, hostProxyMap);
          outboxService.save(
              new ProxyHostUpdateEvent(accountIdentifier, PluginsProxyInfoMapper.toDto(pluginsProxyInfoEntity),
                  PluginsProxyInfoMapper.toDto(oldPluginProxyInfoEntityMap.get(newEnvVariableIdentifier))));
        }
        returnList.add(PluginsProxyInfoMapper.toDto(pluginsProxyInfoRepository.updatePluginProxyInfo(
            PluginsProxyInfoMapper.toDto(pluginsProxyInfoEntity), accountIdentifier)));
      }

      if (!originalHostProxyMap.similar(hostProxyMap)) {
        proxyEnvVariableServiceWrapper.setHostProxyMap(accountIdentifier, hostProxyMap);
      }

      return true;
    }));

    return returnList;
  }

  private void removeHostsFromMapAndCache(
      String accountIdentifier, List<PluginsProxyInfoEntity> pluginsProxyInfoEntities, JSONObject hostProxyMap) {
    for (PluginsProxyInfoEntity pluginsProxyInfoEntity : pluginsProxyInfoEntities) {
      hostProxyMap.remove(pluginsProxyInfoEntity.getHost());
      Set<String> hostsToBeRemoved =
          pluginsProxyInfoEntities.stream().map(PluginsProxyInfoEntity::getHost).collect(Collectors.toSet());
      delegateSelectorsCache.remove(accountIdentifier, hostsToBeRemoved);
    }
  }

  private void putHostInMapAndCache(
      String accountIdentifier, PluginsProxyInfoEntity pluginsProxyInfoEntity, JSONObject hostProxyMap) {
    hostProxyMap.put(pluginsProxyInfoEntity.getHost(), true);
    delegateSelectorsCache.put(accountIdentifier, pluginsProxyInfoEntity.getHost(),
        new HashSet<>(pluginsProxyInfoEntity.getDelegateSelectors()));
  }
}
