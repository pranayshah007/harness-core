/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.service;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.ConfigType;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.AppConfigRequest;
import io.harness.spec.server.idp.v1.model.MergedPluginConfigs;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public interface ConfigManagerService {
  Map<String, Boolean> getAllPluginIdsMap(String accountIdentifier);
  public AppConfig getPluginConfig(String accountIdentifier, String pluginId);

  AppConfig saveConfigForAccount(AppConfig appConfig, String accountIdentifier, ConfigType configType) throws Exception;

  AppConfig saveOrUpdateConfigForAccount(AppConfig appConfig, String accountIdentifier, ConfigType configType)
      throws Exception;

  AppConfig updateConfigForAccount(AppConfig appConfig, String accountIdentifier, ConfigType configType)
      throws Exception;

  AppConfig toggleConfigForAccount(String accountIdentifier, String configId, Boolean isEnabled, ConfigType configType);

  MergedAppConfigEntity mergeAndSaveAppConfig(String accountIdentifier) throws Exception;

  MergedPluginConfigs mergeEnabledPluginConfigsForAccount(String accountIdentifier) throws Exception;

  List<AppConfigEntity> deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo();
  String mergeAllAppConfigsForAccount(String account) throws Exception;

  void updateConfigMap(String accountIdentifier, String appConfigYamlData, String configName);

  void validateSchemaForPlugin(String config, String configId) throws Exception;

  Boolean isPluginWithNoConfig(String accountIdentifier, String configId);
}
