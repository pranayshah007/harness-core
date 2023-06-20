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
import io.harness.idp.common.delegateselectors.cache.DelegateSelectorsCache;
import io.harness.idp.configmanager.beans.entity.PluginsProxyInfoEntity;
import io.harness.idp.configmanager.repositories.PluginsProxyInfoRepository;
import io.harness.idp.proxy.envvariable.ProxyEnvVariableUtils;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @com.google.inject.Inject }))
public class PluginsProxyInfoServiceImpl implements PluginsProxyInfoService {
  private PluginsProxyInfoRepository pluginsProxyInfoRepository;
  private DelegateSelectorsCache delegateSelectorsCache;
  private ProxyEnvVariableUtils proxyEnvVariableUtils;

  private static final String ERROR_MESSAGE_FOR_PROXY =
      "Host - %s is already used in plugin - %s, please configure it from configurations page.";

  private static final String NO_PROXY_HOST_ASSOCIATED_VARIABLE_ASSOCIATED =
      "No proxy hosts are associated with Plugin id - {} for account - {}";

  @Override
  public List<ProxyHostDetail> insertProxyHostDetailsForPlugin(AppConfig appConfig, String accountIdentifier)
      throws ExecutionException {
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities = getPluginProxyInfoEntities(appConfig, accountIdentifier);
    List<String> errorMessageForProxyDetails = getErrorMessageIfHostIsAlreadyInUse(accountIdentifier, appConfig);
    if (!errorMessageForProxyDetails.isEmpty()) {
      throw new InvalidRequestException(new Gson().toJson(errorMessageForProxyDetails));
    }

    // deleting older proxy host details
    deleteProxyHostDetailsForPlugin(accountIdentifier, appConfig.getConfigId());

    List<PluginsProxyInfoEntity> savedPluginProxyDetails =
        (List<PluginsProxyInfoEntity>) pluginsProxyInfoRepository.saveAll(pluginsProxyInfoEntities);
    updateDelegateSelectorsCache(accountIdentifier, pluginsProxyInfoEntities);
    updateHostProxyEnvVariable(accountIdentifier, pluginsProxyInfoEntities);

    return getPluginProxyHostDetailsFromEntities(savedPluginProxyDetails);
  }

  @Override
  public List<ProxyHostDetail> updateProxyHostDetailsForPlugin(AppConfig appConfig, String accountIdentifier)
      throws ExecutionException {
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities = getPluginProxyInfoEntities(appConfig, accountIdentifier);
    if (pluginsProxyInfoEntities.isEmpty()) {
      log.info(String.format(NO_PROXY_HOST_ASSOCIATED_VARIABLE_ASSOCIATED, appConfig.getConfigId(), accountIdentifier));
    }

    List<String> errorMessageForProxyDetails = getErrorMessageIfHostIsAlreadyInUse(accountIdentifier, appConfig);
    if (!errorMessageForProxyDetails.isEmpty()) {
      throw new InvalidRequestException(new Gson().toJson(errorMessageForProxyDetails));
    }

    // create new updated proxy host values
    return insertProxyHostDetailsForPlugin(appConfig, accountIdentifier);
  }

  @Override
  public void deleteProxyHostDetailsForPlugin(String accountIdentifier, String pluginId) throws ExecutionException {
    List<PluginsProxyInfoEntity> existingPluginProxies =
        pluginsProxyInfoRepository.findAllByAccountIdentifierAndPluginId(accountIdentifier, pluginId);
    if (!existingPluginProxies.isEmpty()) {
      Set<String> hostsToBeRemoved =
          existingPluginProxies.stream().map(PluginsProxyInfoEntity::getHost).collect(Collectors.toSet());
      delegateSelectorsCache.remove(accountIdentifier, hostsToBeRemoved);
      proxyEnvVariableUtils.removeFromHostProxyEnvVariable(accountIdentifier, hostsToBeRemoved);
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

  private List<String> getErrorMessageIfHostIsAlreadyInUse(String accountIdentifier, AppConfig appConfig) {
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

  private List<PluginsProxyInfoEntity> getPluginProxyInfoEntities(AppConfig appConfig, String accountIdentifier) {
    List<PluginsProxyInfoEntity> pluginsProxyInfoEntities = new ArrayList<>();
    if (appConfig.getProxy() == null) {
      return pluginsProxyInfoEntities;
    }
    for (ProxyHostDetail proxyHostDetail : appConfig.getProxy()) {
      pluginsProxyInfoEntities.add(PluginsProxyInfoEntity.builder()
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
      proxyHostDetail.setHost(pluginsProxyInfoEntity.getHost());
      proxyHostDetail.setProxy(pluginsProxyInfoEntity.getProxy());
      proxyHostDetail.setSelectors(pluginsProxyInfoEntity.getDelegateSelectors());
      returnList.add(proxyHostDetail);
    }
    return returnList;
  }

  private void updateDelegateSelectorsCache(String accountIdentifier, List<PluginsProxyInfoEntity> proxies)
      throws ExecutionException {
    for (PluginsProxyInfoEntity proxy : proxies) {
      if (proxy.getProxy()) {
        delegateSelectorsCache.put(accountIdentifier, proxy.getHost(), new HashSet<>(proxy.getDelegateSelectors()));
      }
    }
  }
  private void updateHostProxyEnvVariable(String accountIdentifier, List<PluginsProxyInfoEntity> proxies) {
    Map<String, Boolean> hostProxyMap = new HashMap<>();
    for (PluginsProxyInfoEntity proxy : proxies) {
      if (proxy.getProxy()) {
        hostProxyMap.put(proxy.getHost(), true);
      }
    }
    proxyEnvVariableUtils.createOrUpdateHostProxyEnvVariable(accountIdentifier, hostProxyMap);
  }
}
