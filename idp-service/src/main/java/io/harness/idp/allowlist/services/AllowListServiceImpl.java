/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.allowlist.services;

import static io.harness.idp.configmanager.utils.ConfigManagerUtils.asJsonNode;
import static io.harness.idp.configmanager.utils.ConfigManagerUtils.asYaml;
import static io.harness.idp.configmanager.utils.ConfigManagerUtils.isValidSchema;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.configmanager.ConfigType;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.HostInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class AllowListServiceImpl implements AllowListService {
  private ConfigManagerService configManagerService;
  private static final String ALLOW_LIST_CONFIG_FILE = "configs/allowlist/allow-list.yaml";
  private static final String ALLOW_LIST_JSON_SCHEMA_FILE = "configs/allowlist/allow-list-json-schema.json";
  private static final String INVALID_SCHEMA_FOR_ALLOW_LIST =
      "Invalid json schema for allow list config for account - %s";
  private static final String ALLOW_LIST = "allow-list";
  private static final String READING_PROPERTY = "reading";
  private static final String ALLOW_PROPERTY = "allow";

  @Override
  public List<HostInfo> getAllowList(String harnessAccount) throws Exception {
    AppConfig appConfig = configManagerService.getAppConfig(harnessAccount, ALLOW_LIST, ConfigType.BACKEND);
    if (appConfig != null) {
      JsonNode jsonNode = asJsonNode(appConfig.getConfigs());
      JsonNode readingNode = ConfigManagerUtils.getNodeByName(jsonNode, READING_PROPERTY);
      return Arrays.asList(YamlUtils.read(readingNode.get(ALLOW_PROPERTY).toString(), HostInfo[].class));
    }
    return new ArrayList<>();
  }

  @Override
  public List<HostInfo> saveAllowList(List<HostInfo> hostInfoList, String harnessAccount) throws Exception {
    JsonNode allowListNode = asJsonNode(YamlUtils.writeObjectAsYaml(hostInfoList));
    String yamlString = CommonUtils.readFileFromClassPath(ALLOW_LIST_CONFIG_FILE);
    JsonNode rootNode = asJsonNode(yamlString);
    JsonNode readingNode = ConfigManagerUtils.getNodeByName(rootNode, READING_PROPERTY);
    ((ObjectNode) readingNode).put(ALLOW_PROPERTY, allowListNode);
    String config = asYaml(rootNode.toString());
    createOrUpdateAllowListAppConfig(config, harnessAccount);
    return hostInfoList;
  }

  private void createOrUpdateAllowListAppConfig(String config, String accountIdentifier) throws Exception {
    String schema = CommonUtils.readFileFromClassPath(ALLOW_LIST_JSON_SCHEMA_FILE);
    if (!isValidSchema(config, schema)) {
      throw new InvalidRequestException(String.format(INVALID_SCHEMA_FOR_ALLOW_LIST, accountIdentifier));
    }
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(ALLOW_LIST);
    appConfig.setConfigs(config);
    appConfig.setEnabled(true);

    configManagerService.saveOrUpdateConfigForAccount(appConfig, accountIdentifier, ConfigType.BACKEND);
    configManagerService.mergeAndSaveAppConfig(accountIdentifier);

    log.info("Merging for allow list config completed for accountId - {}", accountIdentifier);
  }
}
