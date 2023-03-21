/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.service;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.AppConfigRequest;

import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public interface ConfigManagerService {
  Map<String, Boolean> getAllPluginIdsMap(String accountIdentifier);
  public AppConfig getPluginConfig(String accountIdentifier, String pluginId);

  AppConfig savePluginConfig(AppConfigRequest appConfigRequest, String accountIdentifier);

  AppConfig updatePluginConfig(AppConfigRequest appConfigRequest, String accountIdentifier);

  AppConfig togglePlugin(String accountIdentifier, String pluginName, Boolean isEnabled);

  MergedAppConfigEntity mergeAndSaveAppConfig(String accountIdentifier) throws Exception;
}
