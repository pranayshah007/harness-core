/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datasources.utils;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.*;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import java.util.Optional;
import org.apache.commons.math3.util.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class ConfigReaderTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock BackstageEnvVariableService backstageEnvVariableService;
  @Mock ConfigManagerService configManagerService;
  @InjectMocks ConfigReader configReader;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  private static final String TEST_ACCOUNT_IDENTIFIER = "test-accountIdentifier";

  static final String TEST_VALID_MERGED_APP_CONFIG = "---\n"
      + "proxy:\n"
      + "  /harness/prod:\n"
      + "    target: https://app.harness.io/\n"
      + "    pathRewrite:\n"
      + "      /api/proxy/harness/prod/?: /\n"
      + "    allowedHeaders:\n"
      + "    - authorization\n"
      + "  /harness/scorecard:\n"
      + "    target: https://app.harness.io/\n"
      + "    pathRewrite:\n"
      + "      /api/proxy/harness/scorecard/?: /\n"
      + "    allowedHeaders:\n"
      + "    - authorization\n"
      + "    - Harness-Account\n"
      + "kafka:\n"
      + "  clientId: backstage\n"
      + "  clusters:\n"
      + "  - name: cluster\n"
      + "    dashboardUrl: https://akhq.io/\n"
      + "    brokers:\n"
      + "    - localhost:9092\n";

  static final String ERROR_FETCH_CONFIG_FOR_ACCOUNT =
      "Could not fetch app-config for account Id - test-accountIdentifier";

  static final String TEST_VALID_INTEGRATION_CONFIG = "integrations:\n"
      + "  github:\n"
      + "    - host: HOST_VALUE\n"
      + "      apiBaseUrl: API_BASE_URL\n"
      + "      token: ${HARNESS_GITHUB_TOKEN}";

  private static final String ERROR_MESSAGE_FOR_DECRYPTED_VALUE_EMPTY =
      "Could not get the decrypted value for secret: test-secret-identifier, used by env: HARNESS_GITHUB_TOKEN, in account: test-accountIdentifier";

  private static final String TEST_ENV_VARIABLE_SECRET_NAME = "HARNESS_GITHUB_TOKEN";
  private static final String TEST_ENV_VARIABLE_SECRET_IDENTIFIER = " test-identifier-value";
  private static final String TEST_DECRYPTED_VALUE_FOR_SECRET = "test-decrypted-value";

  private static final String TEST_SECRET_IDENTIFIER = "test-secret-identifier";
  private static final String TARGET_TOKEN_EXPRESSION_KEY = "appConfig.integrations.github.0.token";

  private static final String TARGET_TOKEN_INVALID_EXPRESSION_EXPRESSION_KEY =
      "appConfig.integrations.gitschub.0.token";

  private static final String TEST_CONFIG_ENV_VARIABLE_NAME = "test-config-env-variable-name";
  private static final String TEST_CONFIG_ENV_VARIABLE_IDENTIFIER = "test-config-env-variable-identifier";
  private static final String TEST_CONFIG_ENV_VARIABLE_VALUE = "test-env-variable-value";
  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFetchAllConfigs() throws Exception {
    when(configManagerService.mergeAllAppConfigsForAccount(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(TEST_VALID_MERGED_APP_CONFIG);
    String returnedString = configReader.fetchAllConfigs(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(TEST_VALID_MERGED_APP_CONFIG, returnedString);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFetchAllConfigsException() throws Exception {
    when(configManagerService.mergeAllAppConfigsForAccount(TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_FETCH_CONFIG_FOR_ACCOUNT));
    Exception exception = null;
    try {
      String returnedString = configReader.fetchAllConfigs(TEST_ACCOUNT_IDENTIFIER);
    } catch (Exception e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(exception.getMessage(), ERROR_FETCH_CONFIG_FOR_ACCOUNT);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetConfigValues() {
    // valid case for env secret variable
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(TEST_ENV_VARIABLE_SECRET_NAME);
    backstageEnvSecretVariable.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    backstageEnvSecretVariable.setIdentifier(TEST_ENV_VARIABLE_SECRET_IDENTIFIER);
    backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);

    when(backstageEnvVariableService.findByEnvNameAndAccountIdentifier(
             TEST_ENV_VARIABLE_SECRET_NAME, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(backstageEnvSecretVariable));
    when(backstageEnvVariableService.getDecryptedValueAndLastModifiedTime(
             TEST_ENV_VARIABLE_SECRET_NAME, TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(new Pair(TEST_DECRYPTED_VALUE_FOR_SECRET, 0l));

    Object returnedValue = configReader.getConfigValues(
        TEST_ACCOUNT_IDENTIFIER, TEST_VALID_INTEGRATION_CONFIG, TARGET_TOKEN_EXPRESSION_KEY);
    assertEquals(TEST_DECRYPTED_VALUE_FOR_SECRET, (String) returnedValue);

    // for invalid expression case
    returnedValue = configReader.getConfigValues(
        TEST_ACCOUNT_IDENTIFIER, TEST_VALID_INTEGRATION_CONFIG, TARGET_TOKEN_INVALID_EXPRESSION_EXPRESSION_KEY);
    assertNull(returnedValue);

    // if decrypted value is null asserting for exception.
    Exception exception = null;
    when(backstageEnvVariableService.getDecryptedValueAndLastModifiedTime(
             TEST_ENV_VARIABLE_SECRET_NAME, TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(new Pair("", 0l));
    try {
      returnedValue = configReader.getConfigValues(
          TEST_ACCOUNT_IDENTIFIER, TEST_VALID_INTEGRATION_CONFIG, TARGET_TOKEN_EXPRESSION_KEY);
    } catch (Exception e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(ERROR_MESSAGE_FOR_DECRYPTED_VALUE_EMPTY, exception.getMessage());

    // valid case for env config variable
    BackstageEnvConfigVariable backstageEnvConfigVariable = new BackstageEnvConfigVariable();
    backstageEnvConfigVariable.setEnvName(TEST_CONFIG_ENV_VARIABLE_NAME);
    backstageEnvConfigVariable.setValue(TEST_CONFIG_ENV_VARIABLE_VALUE);
    backstageEnvConfigVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);
    backstageEnvConfigVariable.setIdentifier(TEST_CONFIG_ENV_VARIABLE_IDENTIFIER);

    when(backstageEnvVariableService.findByEnvNameAndAccountIdentifier(
             TEST_ENV_VARIABLE_SECRET_NAME, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(backstageEnvConfigVariable));
    returnedValue = configReader.getConfigValues(
        TEST_ACCOUNT_IDENTIFIER, TEST_VALID_INTEGRATION_CONFIG, TARGET_TOKEN_EXPRESSION_KEY);
    assertEquals(TEST_CONFIG_ENV_VARIABLE_VALUE, (String) returnedValue);
  }
}