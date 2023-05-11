/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.beans.entity.PluginConfigEnvVariablesEntity;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class ConfigEnvVariablesMapper {
  public List<PluginConfigEnvVariablesEntity> getEntitiesForEnvVariables(
      AppConfig appConfig, String accountIdentifier) {
    List<PluginConfigEnvVariablesEntity> resultList = new ArrayList<>();
    if (appConfig.getEnvVariables() == null) {
      return resultList;
    }
    List<BackstageEnvSecretVariable> envVariables = appConfig.getEnvVariables();
    for (BackstageEnvSecretVariable backstageEnvSecretVariable : envVariables) {
      resultList.add(PluginConfigEnvVariablesEntity.builder()
                         .envName(backstageEnvSecretVariable.getEnvName())
                         .pluginName(appConfig.getConfigName())
                         .pluginId(appConfig.getConfigId())
                         .accountIdentifier(accountIdentifier)
                         .enabledDisabledAt(System.currentTimeMillis())
                         .lastModifiedAt(System.currentTimeMillis())
                         .build());
    }
    return resultList;
  }
}
