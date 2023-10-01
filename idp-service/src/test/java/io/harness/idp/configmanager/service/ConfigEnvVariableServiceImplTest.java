/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.service;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.Constants;
import io.harness.idp.configmanager.beans.entity.PluginConfigEnvVariablesEntity;
import io.harness.idp.configmanager.repositories.ConfigEnvVariablesRepository;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ConfigEnvVariableServiceImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock ConfigEnvVariablesRepository configEnvVariablesRepository;

  @Mock BackstageEnvVariableService backstageEnvVariableService;

  @InjectMocks ConfigEnvVariablesServiceImpl configEnvVariablesServiceImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_PLUGIN_ID = "test-plugin-id";
  private static final String TEST_ENV_NAME = "test-env-name";
  private static final String TEST_PLUGIN_NAME = "test-plugin-name";
  private static final String TEST_ID = "test-id";
  private static final String TEST_CONFIG = String.format("test-config ${%s}", TEST_ENV_NAME);
  private static final String TEST_SECRET_IDENTIFIER = "test-secret-id";
  private static final String TEST_CONFIG_NAME = "test-config-name";
  private static final String TEST_CONFIG_ID = "test-config-id";
  private static final String TEST_NON_RESERVED_ENV_NAME = "test-non-reserved-env-name";
  static final long TEST_UPDATED_TIME = 1681756035;
  static final long TEST_LAST_CREATED_AT_TIME = 1681756035;
  static final long TEST_ENABLED_DISABLED_AT = 1681756035;

  static final String TEST_ERROR_MESSAGE_RESERVED_KEYWORD =
      "[\"HARNESS_GITHUB_TOKEN - is reserved env variable name, please use some other env variable name\"]";

  static final String TEST_ERROR_MESSAGE_FOR_OVERLAPPING_ENV_NAME =
      "[\"test-env-name - is already used in plugin - test-plugin-name , please use some other env variable name\"]";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testInsertConfigEnvVariable() {
    BackstageEnvSecretVariable backstageEnvSecretVariable = getTestBackstageEnvSecretVariable();

    AppConfig appConfig = getTestAppConfig(backstageEnvSecretVariable);

    when(backstageEnvVariableService.createOrUpdate(any(), any()))
        .thenReturn(Collections.singletonList(backstageEnvSecretVariable));
    when(configEnvVariablesRepository.findAllByAccountIdentifierAndPluginId(any(), any()))
        .thenReturn(Collections.singletonList(getTestPluginConfigEnvVariablesEntity()));
    doNothing().when(configEnvVariablesRepository).deleteAllByAccountIdentifierAndPluginId(any(), any());
    doNothing().when(backstageEnvVariableService).deleteMultiUsingEnvNames(any(), any());
    List<BackstageEnvSecretVariable> returnList =
        configEnvVariablesServiceImpl.insertConfigEnvVariables(appConfig, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(returnList.get(0).getEnvName(), TEST_ENV_NAME);
    assertEquals(returnList.get(0).getHarnessSecretIdentifier(), TEST_SECRET_IDENTIFIER);

    // Test for empty env variables
    when(backstageEnvVariableService.createOrUpdate(any(), any())).thenReturn(Collections.emptyList());
    appConfig.setEnvVariables(Arrays.asList());
    returnList = configEnvVariablesServiceImpl.insertConfigEnvVariables(appConfig, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(returnList.size(), 0);

    // Test for reserved keyword
    backstageEnvSecretVariable.setEnvName(Constants.GITHUB_TOKEN);
    appConfig = getTestAppConfig(backstageEnvSecretVariable);
    Exception exception = null;
    try {
      returnList = configEnvVariablesServiceImpl.insertConfigEnvVariables(appConfig, TEST_ACCOUNT_IDENTIFIER);
    } catch (Exception e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(TEST_ERROR_MESSAGE_RESERVED_KEYWORD, exception.getMessage());

    // Test for overlapping env names
    when(configEnvVariablesRepository.findByAccountIdentifierAndEnvName(any(), any()))
        .thenReturn(getTestPluginConfigEnvVariablesEntity());
    backstageEnvSecretVariable.setEnvName(TEST_NON_RESERVED_ENV_NAME);
    appConfig = getTestAppConfig(backstageEnvSecretVariable);
    appConfig.setConfigId(TEST_CONFIG_ID);
    try {
      configEnvVariablesServiceImpl.updateConfigEnvVariables(appConfig, TEST_ACCOUNT_IDENTIFIER);
    } catch (Exception e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(TEST_ERROR_MESSAGE_FOR_OVERLAPPING_ENV_NAME, exception.getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateEnvVariable() {
    BackstageEnvSecretVariable backstageEnvSecretVariable = getTestBackstageEnvSecretVariable();

    AppConfig appConfig = getTestAppConfig(backstageEnvSecretVariable);

    when(backstageEnvVariableService.createOrUpdate(any(), any()))
        .thenReturn(Collections.singletonList(backstageEnvSecretVariable));
    when(configEnvVariablesRepository.findAllByAccountIdentifierAndPluginId(any(), any()))
        .thenReturn(Collections.singletonList(getTestPluginConfigEnvVariablesEntity()));
    doNothing().when(configEnvVariablesRepository).deleteAllByAccountIdentifierAndPluginId(any(), any());
    doNothing().when(backstageEnvVariableService).deleteMultiUsingEnvNames(any(), any());
    List<BackstageEnvSecretVariable> returnList =
        configEnvVariablesServiceImpl.updateConfigEnvVariables(appConfig, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(returnList.get(0).getEnvName(), TEST_ENV_NAME);
    assertEquals(returnList.get(0).getHarnessSecretIdentifier(), TEST_SECRET_IDENTIFIER);

    // Test for empty env variables
    when(backstageEnvVariableService.createOrUpdate(any(), any())).thenReturn(Collections.emptyList());
    appConfig.setEnvVariables(Arrays.asList());
    returnList = configEnvVariablesServiceImpl.updateConfigEnvVariables(appConfig, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(0, returnList.size());

    // Test for reserved keyword
    backstageEnvSecretVariable.setEnvName(Constants.GITHUB_TOKEN);
    appConfig = getTestAppConfig(backstageEnvSecretVariable);
    Exception exception = null;
    try {
      returnList = configEnvVariablesServiceImpl.updateConfigEnvVariables(appConfig, TEST_ACCOUNT_IDENTIFIER);
    } catch (Exception e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(TEST_ERROR_MESSAGE_RESERVED_KEYWORD, exception.getMessage());

    // Test for overlapping env names
    when(configEnvVariablesRepository.findByAccountIdentifierAndEnvName(any(), any()))
        .thenReturn(getTestPluginConfigEnvVariablesEntity());
    backstageEnvSecretVariable.setEnvName(TEST_NON_RESERVED_ENV_NAME);
    appConfig = getTestAppConfig(backstageEnvSecretVariable);
    appConfig.setConfigId(TEST_CONFIG_ID);
    try {
      configEnvVariablesServiceImpl.updateConfigEnvVariables(appConfig, TEST_ACCOUNT_IDENTIFIER);
    } catch (Exception e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(TEST_ERROR_MESSAGE_FOR_OVERLAPPING_ENV_NAME, exception.getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllEnvVariablesForAccountIdentifierAndMultiplePluginIds() {
    PluginConfigEnvVariablesEntity pluginConfigEnvVariablesEntity = getTestPluginConfigEnvVariablesEntity();
    when(configEnvVariablesRepository.getAllEnvVariablesForMultiplePluginIds(any(), any()))
        .thenReturn(Collections.singletonList(pluginConfigEnvVariablesEntity));
    List<String> returnedList =
        configEnvVariablesServiceImpl.getAllEnvVariablesForAccountIdentifierAndMultiplePluginIds(
            TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_PLUGIN_ID));
    assertEquals(returnedList.get(0), TEST_ENV_NAME);

    // check if no env variable is present
    when(configEnvVariablesRepository.getAllEnvVariablesForMultiplePluginIds(any(), any()))
        .thenReturn(Collections.emptyList());
    returnedList = configEnvVariablesServiceImpl.getAllEnvVariablesForAccountIdentifierAndMultiplePluginIds(
        TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_PLUGIN_ID));
    assertEquals(0, returnedList.size());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllEnvVariablesForAccountIdentifierAndPluginId() {
    PluginConfigEnvVariablesEntity pluginConfigEnvVariablesEntity = getTestPluginConfigEnvVariablesEntity();
    when(configEnvVariablesRepository.findAllByAccountIdentifierAndPluginId(any(), any()))
        .thenReturn(Collections.singletonList(pluginConfigEnvVariablesEntity));
    List<String> returnedList = configEnvVariablesServiceImpl.getAllEnvVariablesForAccountIdentifierAndPluginId(
        TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID);
    assertEquals(returnedList.get(0), TEST_ENV_NAME);

    // check if no env variable is present
    when(configEnvVariablesRepository.findAllByAccountIdentifierAndPluginId(any(), any()))
        .thenReturn(Collections.emptyList());
    returnedList = configEnvVariablesServiceImpl.getAllEnvVariablesForAccountIdentifierAndPluginId(
        TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID);
    assertEquals(0, returnedList.size());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEnvVariablesFromEntities() {
    List<String> rsultList = configEnvVariablesServiceImpl.getEnvVariablesFromEntities(
        Collections.singletonList(getTestPluginConfigEnvVariablesEntity()));
    assertEquals(rsultList.get(0), TEST_ENV_NAME);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testValidateConfigEnvVariables() {
    // valid case if env variable present in config is configured as well
    BackstageEnvSecretVariable backstageEnvSecretVariable = getTestBackstageEnvSecretVariable();
    AppConfig appConfig = getTestAppConfig(backstageEnvSecretVariable);
    configEnvVariablesServiceImpl.validateConfigEnvVariables(appConfig);

    // invalid case if env variable present in config is not configured.
    backstageEnvSecretVariable.setEnvName("Not-configured-env-name");
    appConfig = getTestAppConfig(backstageEnvSecretVariable);
    Exception exception = null;
    try {
      configEnvVariablesServiceImpl.validateConfigEnvVariables(appConfig);
    } catch (UnsupportedOperationException e) {
      exception = e;
    }
    assertNotNull(exception);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllEnvVariableNamesFromBackstageEnvSecretVariable() {
    Set<String> envVariableName = configEnvVariablesServiceImpl.getAllEnvVariableNamesFromBackstageEnvSecretVariable(
        Collections.singletonList(getTestBackstageEnvSecretVariable()));
    assertEquals(envVariableName.iterator().next(), TEST_ENV_NAME);
  }

  private BackstageEnvSecretVariable getTestBackstageEnvSecretVariable() {
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(TEST_ENV_NAME);
    backstageEnvSecretVariable.setCreated(TEST_LAST_CREATED_AT_TIME);
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    backstageEnvSecretVariable.setIdentifier(TEST_ID);
    backstageEnvSecretVariable.setUpdated(TEST_UPDATED_TIME);
    return backstageEnvSecretVariable;
  }

  private AppConfig getTestAppConfig(BackstageEnvSecretVariable backstageEnvSecretVariable) {
    AppConfig appConfig = new AppConfig();
    appConfig.setConfigId(TEST_PLUGIN_ID);
    appConfig.setConfigs(TEST_CONFIG);
    appConfig.setConfigName(TEST_CONFIG_NAME);
    appConfig.setCreated(TEST_LAST_CREATED_AT_TIME);
    appConfig.setEnabled(true);
    appConfig.setEnabledDisabledAt(TEST_ENABLED_DISABLED_AT);
    appConfig.setUpdated(TEST_UPDATED_TIME);
    appConfig.setEnvVariables(Arrays.asList(backstageEnvSecretVariable));
    return appConfig;
  }

  private PluginConfigEnvVariablesEntity getTestPluginConfigEnvVariablesEntity() {
    return PluginConfigEnvVariablesEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .pluginId(TEST_PLUGIN_ID)
        .pluginName(TEST_PLUGIN_NAME)
        .envName(TEST_ENV_NAME)
        .createdAt(TEST_LAST_CREATED_AT_TIME)
        .lastModifiedAt(TEST_LAST_CREATED_AT_TIME)
        .enabledDisabledAt(TEST_ENABLED_DISABLED_AT)
        .build();
  }
}
