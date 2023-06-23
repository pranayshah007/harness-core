/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.services;

import static io.harness.idp.common.Constants.AUTH_GITHUB_ENTERPRISE_INSTANCE_URL;
import static io.harness.idp.configmanager.utils.ConfigManagerUtils.asJsonNode;
import static io.harness.idp.configmanager.utils.ConfigManagerUtils.asYaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.Constants;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.plugin.mappers.AuthInfoMapper;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.AuthInfo;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class AuthInfoServiceImpl implements AuthInfoService {
  private BackstageEnvVariableService backstageEnvVariableService;
  private ConfigManagerService configManagerService;
  @Inject private NamespaceService namespaceService;
  private static final String INVALID_SCHEMA_FOR_AUTH = "Invalid json schema for auth config for account - %s";
  private static final Map<String, String> GITHUB_OPTIONAL_VARIABLES =
      Map.of(AUTH_GITHUB_ENTERPRISE_INSTANCE_URL, "enterpriseInstanceUrl");

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
    backstageEnvVariableService.deleteMultiUsingEnvNames(getEnvNamesForAuthId(authId), harnessAccount);
    List<BackstageEnvVariable> backstageEnvVariables =
        backstageEnvVariableService.createOrUpdate(envVariables, harnessAccount);
    createOrUpdateAppConfigForAuth(authId, harnessAccount, backstageEnvVariables);
    return backstageEnvVariables;
  }

  private void createOrUpdateAppConfigForAuth(
      String authId, String accountIdentifier, List<BackstageEnvVariable> envVariables) throws Exception {
    JsonNode rootNode = asJsonNode(ConfigManagerUtils.getAuthConfig(authId));
    insertOptionalConfig(authId, rootNode, envVariables);
    String authConfig = asYaml(rootNode.toString());
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
      case Constants.GITHUB_AUTH:
        return Constants.GITHUB_AUTH_ENV_VARIABLES;
      case Constants.GOOGLE_AUTH:
        return Constants.GOOGLE_AUTH_ENV_VARIABLES;
      default:
        return null;
    }
  }

  private void insertOptionalConfig(String authId, JsonNode rootNode, List<BackstageEnvVariable> envVariables) {
    if (authId.equals(Constants.GITHUB_AUTH)) {
      for (BackstageEnvVariable envVariable : envVariables) {
        if (GITHUB_OPTIONAL_VARIABLES.containsKey(envVariable.getEnvName())
            && StringUtils.isNotEmpty(((BackstageEnvConfigVariable) envVariable).getValue())) {
          JsonNode targetNode = ConfigManagerUtils.getNodeByName(rootNode, "development");
          ((ObjectNode) targetNode)
              .put(GITHUB_OPTIONAL_VARIABLES.get(envVariable.getEnvName()), "${" + envVariable.getEnvName() + "}");
        }
      }
    }
  }
}
