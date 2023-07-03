/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.beans.entity.MergedAppConfigEntity;
import io.harness.idp.configmanager.repositories.AppConfigRepository;
import io.harness.idp.configmanager.repositories.MergedAppConfigRepository;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.*;
import io.harness.springdata.TransactionHelper;

import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

@OwnedBy(HarnessTeam.IDP)
public class ConfigManagerServiceImplTest extends CategoryTest {
  AutoCloseable openMocks;

  @Mock private AppConfigRepository appConfigRepository;

  @Mock MergedAppConfigRepository mergedAppConfigRepository;

  @Mock private ConfigEnvVariablesService configEnvVariablesService;

  @Mock private K8sClient k8sClient;

  @Mock private BackstageEnvVariableService backstageEnvVariableService;
  @Mock private NamespaceService namespaceService;
  @Mock private PluginsProxyInfoService pluginsProxyInfoService;
  @Mock private TransactionHelper transactionHelper;
  String env = "prod";
  ConfigManagerServiceImpl configManagerServiceImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    configManagerServiceImpl =
        new ConfigManagerServiceImpl(env, appConfigRepository, mergedAppConfigRepository, k8sClient, namespaceService,
            configEnvVariablesService, backstageEnvVariableService, pluginsProxyInfoService, transactionHelper);
  }

  static final String TEST_ID = "test_id";
  static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  static final ConfigType TEST_PLUGIN_CONFIG_TYPE = ConfigType.PLUGIN;
  static final String TEST_CONFIG_ID = "kafka";
  static final String TEST_HARNESS_CI_CD_PLUGIN_IDENTIFIER = "harness-ci-cd";
  static final String TEST_CONFIG_NAME = "test-config-name";
  static final String TEST_HARNESS_CI_CD_PLUGIN_NAME = "Harness CI/CD";
  static final String TEST_CONFIG_VALUE =
      "kafka:\n  clientId: backstage\n  clusters:\n    - name: cluster\n      dashboardUrl: https://akhq.io/\n      brokers:\n        - localhost:9092";

  static final String TEST_INVALID_CONFIG_VALUE =
      "kafk2da:\n  clie23dntId: backstage\n  clusters:\n    - name: cluster\n      dashboardUrl: https://akhq.io/\n      brokers:\n        - localhost:9092";
  static final String TEST_HARNESS_CI_CD_PLUGIN_CONFIG =
      "proxy:\n  '/harness/prod':\n    target: 'https://app.harness.io/'\n    pathRewrite:\n      '/api2/proxy/harness/prod/?': '/'\n    allowedHeaders:\n      - authorization\n";
  static final Boolean TEST_ENABLED = true;
  static final long TEST_CREATED_AT_TIME = 1681756034;
  static final long TEST_LAST_MODIFIED_AT_TIME = 1681756035;
  static final long TEST_ENABLED_DISABLED_AT_TIME = 1681756036;
  static final String TEST_SECRET_ID = "test-secret-id";
  static final String TEST_SECRET_ENV_NAME = "test-env-name";

  static final String TEST_VALID_MERGED_APP_CONFIG = "---\n"
      + "proxy:\n"
      + "  /harness/prod:\n"
      + "    target: https://app.harness.io/\n"
      + "    pathRewrite:\n"
      + "      /api/proxy/harness/prod/?: /\n"
      + "    allowedHeaders:\n"
      + "    - authorization\n"
      + "kafka:\n"
      + "  clientId: backstage\n"
      + "  clusters:\n"
      + "  - name: cluster\n"
      + "    dashboardUrl: https://akhq.io/\n"
      + "    brokers:\n"
      + "    - localhost:9092\n";

  static final String TEST_INVALID_MERGED_APP_CONFIG = "---\n"
      + "proxerhehy:\n"
      + "  /harness/prod:\n"
      + "    target: https://app.harness.io/\n"
      + "    pathRewrite:\n"
      + "      /api/proxy/harness/prod/?: /\n"
      + "    allowedHeaders:\n"
      + "    - authorization\n";
  static final String TEST_NAMESPACE_FOR_ACCOUNT = "test-namespace";

  static final String TEST_EXPECTED_CONFIG_VALUE_AFTER_MERGE = "---\n"
      + "kafka:\n"
      + "  clientId: backstage\n"
      + "  clusters:\n"
      + "  - name: cluster\n"
      + "    dashboardUrl: https://akhq.io/\n"
      + "    brokers:\n"
      + "    - localhost:9092\n";

  static final String TEST_PROXY_HOST_VALUE = "TEST_PROXY_HOST_VALUE";
  static final Boolean TEST_PROXY_BOOLEAN_VALUE = true;
  static final String TEST_PROXY_DELEGATE_SELECTOR_DELEGATE = "TEST_DELEGATE_SELECTOR";

  static final String TEST_INVALID_CONFIG_ID = "test-invalid-config-id";
  private final String TEST_ERROR_READING_SCHEMA =
      "Error in reading schema - Invalid config id provided - test-invalid-config-id";
  private final String TEST_ERROR_FOR_INVALID_CONFIG = "Invalid config provided for Plugin id - kafka";
  static final List<String> TEST_PROXY_DELEGATE_SELECTOR =
      Collections.singletonList(TEST_PROXY_DELEGATE_SELECTOR_DELEGATE);
  static final String TEST_HOST_VALUE = "test_host_value";

  static final String TEST_VALID_INTEGRATION_CONFIG = "integrations:\n"
      + "  github:\n"
      + "    - host: HOST_VALUE\n"
      + "      apiBaseUrl: API_BASE_URL\n"
      + "      token: ${HARNESS_GITHUB_TOKEN}";

  static final String TEST_INVALID_INTEGRATION_CONFIG = "inwetegrations:\n"
      + "  gawfwqeithub:\n"
      + "    - host: HOST_VALUE\n"
      + "      apiBaseUrl: API_BASE_URL\n"
      + "      token: ${HARNESS_GITHUB_TOKEN}";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetPluginConfig() {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity();
    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(
             TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, TEST_PLUGIN_CONFIG_TYPE))
        .thenReturn(Optional.empty());
    AppConfig appConfig =
        configManagerServiceImpl.getAppConfig(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, ConfigType.PLUGIN);
    assertNull(appConfig);

    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(
             TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, TEST_PLUGIN_CONFIG_TYPE))
        .thenReturn(Optional.of(appConfigEntity));
    appConfig = configManagerServiceImpl.getAppConfig(TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, ConfigType.PLUGIN);
    assertNotNull(appConfig);
    assertEquals(appConfig.getConfigId(), TEST_CONFIG_ID);
    assertEquals(appConfig.getConfigName(), TEST_CONFIG_NAME);
  }

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
  public void testSaveConfigForAccount() throws Exception {
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
    backstageEnvSecretVariable.envName(TEST_SECRET_ENV_NAME);
    when(configEnvVariablesService.insertConfigEnvVariables(any(AppConfig.class), any(String.class)))
        .thenReturn(Arrays.asList(backstageEnvSecretVariable));
    when(pluginsProxyInfoService.insertProxyHostDetailsForPlugin(any(), any()))
        .thenReturn(Collections.singletonList(new ProxyHostDetail()));
    when(appConfigRepository.save(any(AppConfigEntity.class))).thenReturn(getTestAppConfigEntity());
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigs(TEST_CONFIG_VALUE);
    appConfig.setProxy(getTestProxyHostDetails());
    AppConfig savedAppConfig =
        configManagerServiceImpl.saveConfigForAccount(appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE);
    List<BackstageEnvSecretVariable> returnedBackstageEnvVariable = savedAppConfig.getEnvVariables();
    assertEquals(returnedBackstageEnvVariable.get(0).getEnvName(), TEST_SECRET_ENV_NAME);
    assertEquals(returnedBackstageEnvVariable.get(0).getHarnessSecretIdentifier(), TEST_SECRET_ID);
    assertEquals(savedAppConfig.getConfigName(), TEST_CONFIG_NAME);
    assertEquals(savedAppConfig.getConfigId(), TEST_CONFIG_ID);

    // Handling for harness-ci-cd plugin
    AppConfigEntity harnessCiCdPluginEntity = getTestAppConfigEntity();
    harnessCiCdPluginEntity.setEnabled(true);
    harnessCiCdPluginEntity.setConfigId(TEST_HARNESS_CI_CD_PLUGIN_IDENTIFIER);
    harnessCiCdPluginEntity.setConfigName(TEST_HARNESS_CI_CD_PLUGIN_NAME);
    harnessCiCdPluginEntity.setConfigs(TEST_HARNESS_CI_CD_PLUGIN_CONFIG);
    when(appConfigRepository.save(any(AppConfigEntity.class))).thenReturn(harnessCiCdPluginEntity);
    appConfig = new AppConfig();
    appConfig.setConfigId(TEST_HARNESS_CI_CD_PLUGIN_IDENTIFIER);
    appConfig.setConfigName(TEST_HARNESS_CI_CD_PLUGIN_NAME);
    appConfig.setConfigs(TEST_HARNESS_CI_CD_PLUGIN_CONFIG);
    savedAppConfig =
        configManagerServiceImpl.saveConfigForAccount(appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE);
    assertEquals(true, savedAppConfig.isEnabled().booleanValue());
    assertEquals(TEST_HARNESS_CI_CD_PLUGIN_IDENTIFIER, savedAppConfig.getConfigId());
    assertEquals(TEST_HARNESS_CI_CD_PLUGIN_NAME, savedAppConfig.getConfigName());
    assertEquals(TEST_HARNESS_CI_CD_PLUGIN_CONFIG, savedAppConfig.getConfigs());
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
    when(pluginsProxyInfoService.updateProxyHostDetailsForPlugin(any(), any()))
        .thenReturn(Collections.singletonList(new ProxyHostDetail()));
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

    // invalid merged yaml case
    appConfigEntity.setConfigs(TEST_INVALID_CONFIG_VALUE);
    when(appConfigRepository.findAllByAccountIdentifierAndEnabled(TEST_ACCOUNT_IDENTIFIER, TEST_ENABLED))
        .thenReturn(Arrays.asList(appConfigEntity));

    Exception exception = null;
    try {
      configManagerServiceImpl.mergeAndSaveAppConfig(TEST_ACCOUNT_IDENTIFIER);
    } catch (Exception e) {
      exception = e;
    }
    assertNotNull(exception);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMergeEnabledPluginConfigsForAccount() throws Exception {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity();
    when(appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
             TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, TEST_ENABLED))
        .thenReturn(Arrays.asList(appConfigEntity, appConfigEntity));
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

    // check if no plugin is enabled with configs for an account

    appConfigEntity = getTestAppConfigEntity();
    appConfigEntity.setConfigs(null);
    when(appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
             TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, TEST_ENABLED))
        .thenReturn(Collections.singletonList(appConfigEntity));
    when(configEnvVariablesService.getAllEnvVariablesForAccountIdentifierAndMultiplePluginIds(
             TEST_ACCOUNT_IDENTIFIER, Arrays.asList(TEST_CONFIG_ID)))
        .thenReturn(Collections.emptyList());
    when(backstageEnvVariableService.getAllSecretIdentifierForMultipleEnvVariablesInAccount(
             any(String.class), anyList()))
        .thenReturn(Collections.emptyList());
    mergedPluginConfigs = configManagerServiceImpl.mergeEnabledPluginConfigsForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertNull(mergedPluginConfigs.getConfig());
    assertEquals(mergedPluginConfigs.getEnvVariables().size(), 0);

    // check if no plugin is enabled for an account

    when(appConfigRepository.findAllByAccountIdentifierAndConfigTypeAndEnabled(
             TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_CONFIG_TYPE, TEST_ENABLED))
        .thenReturn(Collections.emptyList());
    mergedPluginConfigs = configManagerServiceImpl.mergeEnabledPluginConfigsForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertNull(mergedPluginConfigs.getConfig());
    assertEquals(mergedPluginConfigs.getEnvVariables().size(), 0);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveUpdateAndMergeConfigForAccount() {
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigName(TEST_CONFIG_NAME);
    appConfig.setConfigId(TEST_CONFIG_ID);
    appConfig.setConfigs(TEST_CONFIG_VALUE);
    when(transactionHelper.performTransaction(any())).thenReturn(appConfig);
    AppConfig returnedConfig = configManagerServiceImpl.saveUpdateAndMergeConfigForAccount(
        appConfig, TEST_ACCOUNT_IDENTIFIER, ConfigType.PLUGIN);
    assertNotNull(returnedConfig);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testDeleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo() {
    when(appConfigRepository.deleteDisabledPluginsConfigBasedOnTimestampsForEnabledDisabledTime(any(Long.class)))
        .thenReturn(Collections.singletonList(getTestAppConfigEntity()));
    List<AppConfigEntity> appConfigEntities =
        configManagerServiceImpl.deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo();
    assertEquals(appConfigEntities.get(0).getConfigId(), TEST_CONFIG_ID);
    assertEquals(appConfigEntities.get(0).getConfigName(), TEST_CONFIG_NAME);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testValidateSchemaForPlugin() {
    Exception exception = null;
    try {
      configManagerServiceImpl.validateSchemaForPlugin(TEST_CONFIG_VALUE, TEST_INVALID_CONFIG_ID);
    } catch (Exception e) {
      exception = e;
    }
    assertEquals(TEST_ERROR_READING_SCHEMA, exception.getMessage());

    try {
      configManagerServiceImpl.validateSchemaForPlugin(TEST_INVALID_CONFIG_VALUE, TEST_CONFIG_ID);
    } catch (Exception e) {
      exception = e;
    }
    assertEquals(TEST_ERROR_FOR_INVALID_CONFIG, exception.getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateOrUpdateTimeStampEnvVariable() {
    configManagerServiceImpl.createOrUpdateTimeStampEnvVariable(TEST_ACCOUNT_IDENTIFIER);
    verify(backstageEnvVariableService, times(1)).createOrUpdate(any(), any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveAndMergeAppConfigForGitIntegrations() throws Exception {
    ConnectorInfoDTO connectorInfoDTO = new ConnectorInfoDTO();
    connectorInfoDTO.setConnectorType(ConnectorType.GITHUB);
    MockedStatic<GitIntegrationUtils> mockRestStatic = Mockito.mockStatic(GitIntegrationUtils.class);
    mockRestStatic.when(() -> GitIntegrationUtils.getHostForConnector(any())).thenReturn(TEST_HOST_VALUE);
    when(transactionHelper.performTransaction(any())).thenReturn(new AppConfig());
    configManagerServiceImpl.saveAndMergeAppConfigForGitIntegrations(
        TEST_ACCOUNT_IDENTIFIER, connectorInfoDTO, TEST_VALID_INTEGRATION_CONFIG, ConnectorType.GITHUB.toString());

    // for invalid case
    configManagerServiceImpl.saveAndMergeAppConfigForGitIntegrations(
        TEST_ACCOUNT_IDENTIFIER, connectorInfoDTO, TEST_INVALID_INTEGRATION_CONFIG, ConnectorType.GITHUB.toString());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToggleConfigForAccount() throws Exception {
    AppConfigEntity appConfigEntity = getTestAppConfigEntity();
    when(appConfigRepository.updateConfigEnablement(any(), any(), any(), any())).thenReturn(null);
    when(appConfigRepository.findByAccountIdentifierAndConfigIdAndConfigType(any(), any(), any()))
        .thenReturn(Optional.of(appConfigEntity));
    doNothing().when(configEnvVariablesService).deleteConfigEnvVariables(any(), any());
    doNothing().when(pluginsProxyInfoService).deleteProxyHostDetailsForPlugin(any(), any());
    Exception exception = null;
    try {
      configManagerServiceImpl.toggleConfigForAccount(
          TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, false, TEST_PLUGIN_CONFIG_TYPE);
    } catch (InvalidRequestException e) {
      exception = e;
    }
    assertNotNull(exception);

    when(appConfigRepository.findByAccountIdentifierAndConfigId(any(), any())).thenReturn(appConfigEntity);
    when(appConfigRepository.save(any())).thenReturn(appConfigEntity);
    when(appConfigRepository.updateConfigEnablement(any(), any(), any(), any())).thenReturn(appConfigEntity);
    AppConfig returnedAppConfig = configManagerServiceImpl.toggleConfigForAccount(
        TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, true, TEST_PLUGIN_CONFIG_TYPE);
    assertEquals(returnedAppConfig.getConfigId(), TEST_CONFIG_ID);
    assertEquals(returnedAppConfig.getConfigs(), TEST_CONFIG_VALUE);
    assertEquals(returnedAppConfig.getConfigName(), TEST_CONFIG_NAME);

    when(appConfigRepository.findByAccountIdentifierAndConfigId(any(), any())).thenReturn(null);
    when(appConfigRepository.save(any())).thenReturn(appConfigEntity);
    when(appConfigRepository.updateConfigEnablement(any(), any(), any(), any())).thenReturn(appConfigEntity);
    returnedAppConfig = configManagerServiceImpl.toggleConfigForAccount(
        TEST_ACCOUNT_IDENTIFIER, TEST_CONFIG_ID, true, TEST_PLUGIN_CONFIG_TYPE);
    assertEquals(returnedAppConfig.getConfigId(), TEST_CONFIG_ID);
    assertEquals(returnedAppConfig.getConfigs(), TEST_CONFIG_VALUE);
    assertEquals(returnedAppConfig.getConfigName(), TEST_CONFIG_NAME);
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

  private List<ProxyHostDetail> getTestProxyHostDetails() {
    ProxyHostDetail proxyHostDetail = new ProxyHostDetail();
    proxyHostDetail.setHost(TEST_PROXY_HOST_VALUE);
    proxyHostDetail.setProxy(TEST_PROXY_BOOLEAN_VALUE);
    proxyHostDetail.setSelectors(TEST_PROXY_DELEGATE_SELECTOR);
    return Collections.singletonList(proxyHostDetail);
  }
}
