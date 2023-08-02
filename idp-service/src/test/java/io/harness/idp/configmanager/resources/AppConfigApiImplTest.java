/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resources;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.configmanager.resource.AppConfigApiImpl;
import io.harness.idp.configmanager.service.ConfigEnvVariablesService;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigType;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.AppConfigRequest;
import io.harness.spec.server.idp.v1.model.AppConfigResponse;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class AppConfigApiImplTest extends CategoryTest {
  AutoCloseable openMocks;

  @Mock ConfigManagerService configManagerService;

  @Mock ConfigEnvVariablesService configEnvVariablesService;

  @InjectMocks AppConfigApiImpl appConfigApiImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  static final ConfigType TEST_PLUGIN_TYPE = ConfigType.PLUGIN;
  static final String TEST_PLUGIN_ID = "test-plugin-id";

  static final Boolean TEST_IS_ENABLED_FLAG = false;

  private static final String TEST_ENV_NAME = "test-env-name";
  private static final String TEST_ID = "test-id";
  private static final String TEST_CONFIG = String.format("test-config ${%s}", TEST_ENV_NAME);
  private static final String TEST_SECRET_IDENTIFIER = "test-secret-id";
  private static final String TEST_CONFIG_NAME = "test-config-name";
  static final long TEST_UPDATED_TIME = 1681756035;
  static final long TEST_LAST_CREATED_AT_TIME = 1681756035;
  static final long TEST_ENABLED_DISABLED_AT = 1681756035;

  static final String ERROR_MESSAGE_SAVE_OR_UPDATE = "Error : failed to save or update app config";

  static final String ERROR_MESSAGE_TOGGLE_PLUGIN = "Error : failed to save or update app config";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveOrUpdatePluginAppConfig() throws Exception {
    AppConfigRequest appConfigRequest = new AppConfigRequest();
    BackstageEnvSecretVariable backstageEnvSecretVariable = getTestBackstageEnvSecretVariable();
    AppConfig appConfig = getTestAppConfig(backstageEnvSecretVariable);
    appConfigRequest.setAppConfig(appConfig);
    when(configManagerService.saveUpdateAndMergeConfigForAccount(appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_TYPE))
        .thenReturn(appConfig);
    Response response = appConfigApiImpl.saveOrUpdatePluginAppConfig(appConfigRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(appConfig, ((AppConfigResponse) response.getEntity()).getAppConfig());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSaveOrUpdatePluginAppConfigError() throws Exception {
    AppConfigRequest appConfigRequest = new AppConfigRequest();
    BackstageEnvSecretVariable backstageEnvSecretVariable = getTestBackstageEnvSecretVariable();
    AppConfig appConfig = getTestAppConfig(backstageEnvSecretVariable);
    appConfigRequest.setAppConfig(appConfig);
    when(configManagerService.saveUpdateAndMergeConfigForAccount(appConfig, TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_TYPE))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE_SAVE_OR_UPDATE));
    Response response = appConfigApiImpl.saveOrUpdatePluginAppConfig(appConfigRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE_SAVE_OR_UPDATE, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testTogglePluginForAccount() throws ExecutionException {
    AppConfigRequest appConfigRequest = new AppConfigRequest();
    AppConfig appConfig = new AppConfig();
    appConfigRequest.setAppConfig(appConfig);
    when(configManagerService.toggleConfigForAccount(
             TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID, TEST_IS_ENABLED_FLAG, ConfigType.PLUGIN))
        .thenReturn(appConfig);
    Response response =
        appConfigApiImpl.togglePluginForAccount(TEST_PLUGIN_ID, TEST_IS_ENABLED_FLAG, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(appConfig, ((AppConfigResponse) response.getEntity()).getAppConfig());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testTogglePluginForAccountError() throws ExecutionException {
    AppConfigRequest appConfigRequest = new AppConfigRequest();
    AppConfig appConfig = new AppConfig();
    appConfigRequest.setAppConfig(appConfig);
    when(configManagerService.toggleConfigForAccount(
             TEST_ACCOUNT_IDENTIFIER, TEST_PLUGIN_ID, TEST_IS_ENABLED_FLAG, ConfigType.PLUGIN))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE_TOGGLE_PLUGIN));
    Response response =
        appConfigApiImpl.togglePluginForAccount(TEST_PLUGIN_ID, TEST_IS_ENABLED_FLAG, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE_TOGGLE_PLUGIN, ((ResponseMessage) response.getEntity()).getMessage());
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
}
