/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.common.FileUtils;
import io.harness.idp.configmanager.ConfigType;
import io.harness.idp.configmanager.service.ConfigEnvVariablesService;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.service.PluginsProxyInfoService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.plugin.beans.PluginInfoEntity;
import io.harness.idp.plugin.beans.PluginRequestEntity;
import io.harness.idp.plugin.mappers.PluginDetailedInfoMapper;
import io.harness.idp.plugin.mappers.PluginInfoMapper;
import io.harness.idp.plugin.mappers.PluginRequestMapper;
import io.harness.idp.plugin.repositories.PluginInfoRepository;
import io.harness.idp.plugin.repositories.PluginRequestRepository;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginInfo;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;
import io.harness.spec.server.idp.v1.model.RequestPlugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PluginInfoServiceImpl implements PluginInfoService {
  private static final String METADATA_FOLDER = "metadata/";
  private static final String YAML_EXT = ".yaml";
  private PluginInfoRepository pluginInfoRepository;
  private PluginRequestRepository pluginRequestRepository;
  private ConfigManagerService configManagerService;
  private ConfigEnvVariablesService configEnvVariablesService;
  private BackstageEnvVariableService backstageEnvVariableService;
  private PluginsProxyInfoService pluginsProxyInfoService;
  @Inject @Named("env") private String env;
  @Override
  public List<PluginInfo> getAllPluginsInfo(String accountId) {
    List<PluginInfoEntity> plugins = pluginInfoRepository.findByIdentifierIn(Constants.pluginIds);
    List<PluginInfo> pluginDTOs = new ArrayList<>();

    Map<String, Boolean> map = configManagerService.getAllPluginIdsMap(accountId);
    plugins.forEach(pluginInfoEntity -> {
      boolean isEnabled =
          map.containsKey(pluginInfoEntity.getIdentifier()) && map.get(pluginInfoEntity.getIdentifier());
      pluginDTOs.add(PluginInfoMapper.toDTO(pluginInfoEntity, isEnabled));
    });
    return pluginDTOs;
  }

  @Override
  public PluginDetailedInfo getPluginDetailedInfo(String identifier, String harnessAccount) {
    Optional<PluginInfoEntity> pluginInfoEntity = pluginInfoRepository.findByIdentifier(identifier);
    if (pluginInfoEntity.isEmpty()) {
      throw new InvalidRequestException(String.format("Plugin Info not found for pluginId [%s]", identifier));
    }
    PluginInfoEntity pluginEntity = pluginInfoEntity.get();
    AppConfig appConfig = configManagerService.getAppConfig(harnessAccount, identifier, ConfigType.PLUGIN);
    List<BackstageEnvSecretVariable> backstageEnvSecretVariables = new ArrayList<>();
    if (appConfig != null) {
      List<String> envNames =
          configEnvVariablesService.getAllEnvVariablesForAccountIdentifierAndPluginId(harnessAccount, identifier);
      if (CollectionUtils.isNotEmpty(envNames)) {
        backstageEnvSecretVariables =
            backstageEnvVariableService.getAllSecretIdentifierForMultipleEnvVariablesInAccount(
                harnessAccount, envNames);
      }
    } else if (pluginEntity.getEnvVariables() != null) {
      for (String envVariable : pluginEntity.getEnvVariables()) {
        BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
        backstageEnvSecretVariable.setEnvName(envVariable);
        backstageEnvSecretVariable.setHarnessSecretIdentifier(null);
        backstageEnvSecretVariables.add(backstageEnvSecretVariable);
      }
    }
    if (pluginEntity.getIdentifier().equals("harness-ci-cd") && appConfig == null) {
      pluginEntity.setConfig(ConfigManagerUtils.getHarnessCiCdAppConfig(env));
    }
    List<ProxyHostDetail> proxyHostDetails =
        pluginsProxyInfoService.getProxyHostDetailsForPluginId(harnessAccount, identifier);
    return PluginDetailedInfoMapper.toDTO(pluginEntity, appConfig, backstageEnvSecretVariables, proxyHostDetails);
  }

  @Override
  public void saveAllPluginInfo() {
    Constants.pluginIds.forEach(id -> {
      try {
        savePluginInfo(id);
      } catch (Exception e) {
        String errorMessage = String.format("Error occurred while saving plugin details for pluginId: [%s]", id);
        log.error(errorMessage, e);
      }
    });
  }

  @Override
  public void deleteAllPluginInfo() {
    pluginInfoRepository.deleteAll();
  }

  @Override
  public RequestPlugin savePluginRequest(String harnessAccount, RequestPlugin pluginRequest) {
    PluginRequestEntity pluginRequestEntity = PluginRequestMapper.fromDTO(harnessAccount, pluginRequest);
    pluginRequestRepository.save(pluginRequestEntity);
    return PluginRequestMapper.toDTO(pluginRequestEntity);
  }

  @Override
  public Page<PluginRequestEntity> getPluginRequests(String harnessAccount, int page, int limit) {
    Criteria criteria = createCriteriaForGetPluginRequests(harnessAccount);
    Pageable pageable = PageRequest.of(page, limit);
    return pluginRequestRepository.findAll(criteria, pageable);
  }

  public void savePluginInfo(String identifier) throws Exception {
    String schema = FileUtils.readFile(METADATA_FOLDER, identifier, YAML_EXT);
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    PluginInfoEntity pluginInfoEntity = objectMapper.readValue(schema, PluginInfoEntity.class);
    pluginInfoRepository.saveOrUpdate(pluginInfoEntity);
  }

  private Criteria createCriteriaForGetPluginRequests(String harnessAccount) {
    Criteria criteria = new Criteria();
    criteria.and(PluginRequestEntity.PluginRequestKeys.accountIdentifier).is(harnessAccount);
    return criteria;
  }
}
