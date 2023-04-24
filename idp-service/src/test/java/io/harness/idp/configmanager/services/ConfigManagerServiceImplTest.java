/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.services;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.configmanager.ConfigType;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.repositories.MergedAppConfigRepository;
import io.harness.idp.configmanager.service.ConfigEnvVariablesService;
import io.harness.idp.configmanager.service.ConfigManagerServiceImpl;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.MergedPluginConfigs;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ConfigManagerServiceImplTest extends CategoryTest {
  AutoCloseable openMocks;

  @Mock private AppConfigRepository appConfigRepository;

  @Mock MergedAppConfigRepository mergedAppConfigRepository;

  @Mock private ConfigEnvVariablesService configEnvVariablesService;

  @Mock private K8sClient k8sClient;

  @Mock private BackstageEnvVariableService backstageEnvVariableService;

  @Mock private NamespaceService namespaceService;

  @InjectMocks ConfigManagerServiceImpl configManagerServiceImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  static final String TEST_ID = "test_id";
  static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  static final ConfigType TEST_PLUGIN_CONFIG_TYPE = ConfigType.PLUGIN;
  static final String TEST_CONFIG_ID = "kafka";
  static final String TEST_CONFIG_NAME = "test-config-name";
  static final String TEST_CONFIG_VALUE =
      "kafka:\n  clientId: backstage\n  clusters:\n    - name: cluster\n      dashboardUrl: https://akhq.io/\n      brokers:\n        - localhost:9092";
  static final Boolean TEST_ENABLED = true;
  static final long TEST_CREATED_AT_TIME = 1681756034;
  static final long TEST_LAST_MODIFIED_AT_TIME = 1681756035;
  static final long TEST_ENABLED_DISABLED_AT_TIME = 1681756036;
  static final String TEST_SECRET_ID = "test-secret-id";
  static final String TEST_SECRET_ENV_NAME = "test-env-name";

  static final String TEST_VALID_MERGED_APP_CONFIG =
      "---\nproxy:\n  /harness/prod:\n    target: https://app.harness.io/\nkafka:\n  clientId: backstage\n  clusters:\n  - name: cluster\n    dashboardUrl: https://akhq.io/\n    brokers:\n    - localhost:9092\n";
  static final String TEST_NAMESPACE_FOR_ACCOUNT = "test-namespace";

  static final String TEST_EXPECTED_CONFIG_VALUE_AFTER_MERGE = "---\n"
      + "kafka:\n"
      + "  clientId: backstage\n"
      + "  clusters:\n"
      + "  - name: cluster\n"
      + "    dashboardUrl: https://akhq.io/\n"
      + "    brokers:\n"
      + "    - localhost:9092\n";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllPluginIdsMap() {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity();
    when(appConfigRepository.findAllByAccountIdentifierAndConfigType(TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE))
        .thenReturn(Arrays.asList(appConfigEntity));
    Map<String, Boolean> pluginIdMap = configManagerServiceImpl.getAllPluginIdsMap(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(pluginIdMap.get(TEST_CONFIG_ID), TEST_ENABLED);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetPluginConfig() {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity();
    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(
             TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, TEST_PLUGIN_CONFIG_TYPE))
        .thenReturn(Optional.empty());
    AppConfig appConfig = configManagerServiceImpl.getPluginConfig(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID);
    assertNull(appConfig);

    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(
             TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, TEST_PLUGIN_CONFIG_TYPE))
        .thenReturn(Optional.of(appConfigEntity));
    appConfig = configManagerServiceImpl.getPluginConfig(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID);
    assertNotNull(appConfig);
    assertEquals(appConfig.getConfigId(), TEST_CONFIG_ID);
    assertEquals(appConfig.getConfigName(), TEST_CONFIG_NAME);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveConfigForAccount() throws Exception {
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
    backstageEnvSecretVariable.envName(TEST_SECRET_ENV_NAME);
    when(configEnvVariablesService.insertConfigEnvVariables(any(AppConfig.class), any(String.class)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    when(appConfigRepository.save(any(AppConfigEntity.class))).thenReturn(getTestAppConfigEntity());
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigs(TEST_CONFIG_VALUE);
    AppConfig savedAppConfig =
        configManagerServiceImpl.saveConfigForAccount(appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE);
    List<BackstageEnvSecretVariable> returnedBackstageEnvVariable = savedAppConfig.getEnvVariables();
    assertEquals(returnedBackstageEnvVariable.get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(returnedBackstageEnvVariable.get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);
    assertEquals(savedAppConfig.getConfigName(), TEST_CONFIG_NAME);
    assertEquals(savedAppConfig.getConfigId(), TEST_CONFIG_ID);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateConfigForAccount() throws Exception {
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
    backstageEnvSecretVariable.envName(TEST_SECRET_ENV_NAME);
    when(configEnvVariablesService.updateConfigEnvVariables(any(AppConfig.class), any(String.class)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    when(appConfigRepository.updateConfig(any(AppConfigEntity.class), any(ConfigType.class))).thenReturn(null);
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigs(TEST_CONFIG_VALUE);

    Exception exception = null;
    try {
      configManagerServiceImpl.updateConfigForAccount(appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE);
    } catch (InvalidRequestException e) {
      exception = e;
    }
    assertNotNull(exception);

    when(appConfigRepository.updateConfig(any(AppConfigEntity.class), any(ConfigType.class)))
        .thenReturn(getTestAppConfigEntity());
    AppConfig updatedConfig =
        configManagerServiceImpl.updateConfigForAccount(appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE);
    List<BackstageEnvSecretVariable> returnedBackstageEnvVariable = updatedConfig.getEnvVariables();

    assertEquals(returnedBackstageEnvVariable.get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(returnedBackstageEnvVariable.get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);
    assertEquals(updatedConfig.getConfigName(), TEST_CONFIG_NAME);
    assertEquals(updatedConfig.getConfigId(), TEST_CONFIG_ID);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveOrUpdateConfigForAccount() throws Exception {
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
    backstageEnvSecretVariable.envName(TEST_SECRET_ENV_NAME);
    when(configEnvVariablesService.insertConfigEnvVariables(any(AppConfig.class), any(String.class)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    when(appConfigRepository.save(any(AppConfigEntity.class))).thenReturn(getTestAppConfigEntity());

    when(appConfigRepository.findByAccountIdentifierAndConfigId(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID))
        .thenReturn(null);
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigs(TEST_CONFIG_VALUE);
    AppConfig savedAppConfig = configManagerServiceImpl.saveOrUpdateConfigForAccount(
        appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE);
    List<BackstageEnvSecretVariable> returnedBackstageEnvVariable = savedAppConfig.getEnvVariables();
    assertEquals(returnedBackstageEnvVariable.get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(returnedBackstageEnvVariable.get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);
    assertEquals(savedAppConfig.getConfigName(), TEST_CONFIG_NAME);
    assertEquals(savedAppConfig.getConfigId(), TEST_CONFIG_ID);

    when(appConfigRepository.findByAccountIdentifierAndConfigId(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID))
        .thenReturn(getTestAppConfigEntity());
    when(appConfigRepository.updateConfig(any(AppConfigEntity.class), any(ConfigType.class)))
        .thenReturn(getTestAppConfigEntity());
    when(configEnvVariablesService.updateConfigEnvVariables(any(AppConfig.class), any(String.class)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    AppConfig updatedConfig = configManagerServiceImpl.saveOrUpdateConfigForAccount(
        appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE);
    returnedBackstageEnvVariable = updatedConfig.getEnvVariables();
    assertEquals(returnedBackstageEnvVariable.get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(returnedBackstageEnvVariable.get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);
    assertEquals(updatedConfig.getConfigName(), TEST_CONFIG_NAME);
    assertEquals(updatedConfig.getConfigId(), TEST_CONFIG_ID);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMergeAllAppConfigsForAccount() throws Exception {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity();
    when(appConfigRepository.findAllByAccountIdentifierAndEnabled(TEST_ACCOUNT_IDENTIFIER, TEST_ENABLED))
        .thenReturn(Arrays.asList(appConfigEntity));
    String mergedConfig = configManagerServiceImpl.mergeAllAppConfigsForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(TEST_VALID_MERGED_APP_CONFIG, mergedConfig);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMergeAndSaveAppConfig() throws Exception {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity();
    when(appConfigRepository.findAllByAccountIdentifierAndEnabled(TEST_ACCOUNT_IDENTIFIER, TEST_ENABLED))
        .thenReturn(Arrays.asList(appConfigEntity));
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(TEST_NAMESPACE_FOR_ACCOUNT);
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(namespaceInfo);
    when(k8sClient.updateConfigMapData(any(String.class), any(String.class), any(Map.class), any(Boolean.class)))
        .thenReturn(null);
    MergedAppConfigEntity mergedAppConfigEntityValue =
        MergedAppConfigEntity.builder().config(TEST_VALID_MERGED_APP_CONFIG).build();
    when(mergedAppConfigRepository.saveOrUpdate(any(MergedAppConfigEntity.class)))
        .thenReturn(mergedAppConfigEntityValue);
    MergedAppConfigEntity mergedAppConfigEntity =
        configManagerServiceImpl.mergeAndSaveAppConfig(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(mergedAppConfigEntity.getConfig(), TEST_VALID_MERGED_APP_CONFIG);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMergeEnabledPluginConfigsForAccount() throws Exception {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity();
    when(appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
             TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, TEST_ENABLED))
        .thenReturn(Arrays.asList(appConfigEntity));
    when(configEnvVariablesService.getAllEnvVariablesForAccountIdentifierAndMultiplePluginIds(
             TEST_ACCOUNT_IDENTIFIER, Arrays.asList(TEST_CONFIG_ID)))
        .thenReturn(Arrays.asList(TEST_SECRET_ENV_NAME));
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(TEST_SECRET_ENV_NAME);
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
    when(backstageEnvVariableService.getAllSecretIdentifierForMultipleEnvVariablesInAccount(
             any(String.class), anyList()))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    MergedPluginConfigs mergedPluginConfigs =
        configManagerServiceImpl.mergeEnabledPluginConfigsForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(mergedPluginConfigs.getConfig(), TEST_EXPECTED_CONFIG_VALUE_AFTER_MERGE);
    assertEquals(mergedPluginConfigs.getEnvVariables().get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(mergedPluginConfigs.getEnvVariables().get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);
  }

  private AppConfigEntity getTestAppConfigEntity() {
    return AppConfigEntity.builder()
        .id(TEST_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .configType(TEST_PLUGIN_CONFIG_TYPE)
        .configId(TEST_CONFIG_ID)
        .configName(TEST_CONFIG_NAME)
        .configs(TEST_CONFIG_VALUE)
        .enabled(TEST_ENABLED)
        .createdAt(TEST_CREATED_AT_TIME)
        .lastModifiedAt(TEST_LAST_MODIFIED_AT_TIME)
        .enabledDisabledAt(TEST_ENABLED_DISABLED_AT_TIME)
        .build();
  }
}
