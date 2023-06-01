/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.Constants;
import io.harness.idp.configmanager.ConfigType;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.plugin.mappers.AuthInfoMapper;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.AuthInfo;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class AuthInfoServiceImpl implements AuthInfoService {
  private BackstageEnvVariableService backstageEnvVariableService;
  private ConfigManagerService configManagerService;
  @Inject private NamespaceService namespaceService;
  private static final String INVALID_SCHEMA_FOR_AUTH = "Invalid json schema for auth config for account - %s";
  @Override
  public AuthInfo getAuthInfo(String authId, String harnessAccount) {
    List<String> envNames = getEnvNamesForAuthId(authId);
    NamespaceInfo namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(harnessAccount);
    List<BackstageEnvVariable> backstageEnvVariables =
        backstageEnvVariableService.findByEnvNamesAndAccountIdentifier(envNames, harnessAccount);
    return AuthInfoMapper.toDTO(namespaceInfo, backstageEnvVariables);
  }

  @Override
  public List<BackstageEnvVariable> saveAuthEnvVariables(
      String authId, List<BackstageEnvVariable> envVariables, String harnessAccount) throws Exception {
    List<BackstageEnvVariable> backstageEnvVariables =
        backstageEnvVariableService.createOrUpdate(envVariables, harnessAccount);
    createOrUpdateAppConfigForAuth(authId, harnessAccount);
    return backstageEnvVariables;
  }

  private void createOrUpdateAppConfigForAuth(String authId, String accountIdentifier) throws Exception {
    String authConfig = ConfigManagerUtils.getAuthConfig(authId);
    String authSchema = ConfigManagerUtils.getAuthConfigSchema(authId);
    if (!ConfigManagerUtils.isValidSchema(authConfig, authSchema)) {
      log.error(String.format(INVALID_SCHEMA_FOR_AUTH, accountIdentifier));
    }
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(authId);
    appConfig.setConfigs(authConfig);
    appConfig.setEnabled(true);

    configManagerService.saveOrUpdateConfigForAccount(appConfig, accountIdentifier, ConfigType.AUTH);
    configManagerService.mergeAndSaveAppConfig(accountIdentifier);

    log.info("Merging for auth config completed for authId - {}", authId);
  }

  private List<String> getEnvNamesForAuthId(String authId) {
    switch (authId) {
      case "github-auth":
        return Constants.GITHUB_AUTH_ENV_VARIABLES;
      case "google-auth":
        return Constants.GOOGLE_AUTH_ENV_VARIABLES;
      default:
        return null;
    }
  }
}
