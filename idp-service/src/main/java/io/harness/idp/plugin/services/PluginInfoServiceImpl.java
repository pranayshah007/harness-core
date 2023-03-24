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
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.plugin.beans.PluginInfoEntity;
import io.harness.idp.plugin.mappers.PluginDetailedInfoMapper;
import io.harness.idp.plugin.mappers.PluginInfoMapper;
import io.harness.idp.plugin.repositories.PluginInfoRepository;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.PluginDetailedInfo;
import io.harness.spec.server.idp.v1.model.PluginInfo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PluginInfoServiceImpl implements PluginInfoService {
  private static final String CONFIGS_FOLER = "configs/";
  private static final String SCHEMA_FOLER = "schemas/";
  private static final String YAML_EXT = ".yaml";
  private PluginInfoRepository pluginInfoRepository;
  private ConfigManagerService configManagerService;
  @Override
  public List<PluginInfo> getAllPluginsInfo(String accountId) {
    List<PluginInfoEntity> plugins = (List<PluginInfoEntity>) pluginInfoRepository.findAll();
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
    AppConfig appConfig = configManagerService.getPluginConfig(harnessAccount, identifier);
    return PluginDetailedInfoMapper.toDTO(pluginInfoEntity.get(), appConfig);
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

  public void savePluginInfo(String identifier) throws Exception {
    String schema = FileUtils.readFile(SCHEMA_FOLER, identifier, YAML_EXT);
    String config = FileUtils.readFile(CONFIGS_FOLER, identifier, YAML_EXT);
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    PluginInfoEntity pluginInfoEntity = objectMapper.readValue(schema, PluginInfoEntity.class);
    pluginInfoEntity.setConfig(config);
    pluginInfoRepository.saveOrUpdate(pluginInfoEntity);
  }
}
