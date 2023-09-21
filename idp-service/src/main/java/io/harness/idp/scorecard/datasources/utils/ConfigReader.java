/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.utils;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.expression.common.ExpressionMode;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.scorecard.expression.IdpExpressionEvaluator;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ConfigReader {
  private static final String ENV_VARIABLE_REGEX_PATTERN = "\\$\\{([^}]+)}";
  private static final String APP_CONFIG_CONTEXT = "appConfig";
  @Inject BackstageEnvVariableService backstageEnvVariableService;
  @Inject ConfigManagerService configManagerService;

  public String fetchAllConfigs(String accountIdentifier) {
    try {
      return configManagerService.mergeAllAppConfigsForAccount(accountIdentifier);
    } catch (Exception e) {
      throw new InvalidRequestException(format("Could not fetch app-config for account Id - %s", accountIdentifier), e);
    }
  }

  public Object getConfigValues(String accountIdentifier, String yaml, String keyExpression) {
    Map<String, Object> yamlData = YamlUtils.loadYamlStringAsMap(yaml);
    IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(Map.of(APP_CONFIG_CONTEXT, yamlData));
    Object value = evaluator.evaluateExpression(keyExpression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    if (value == null) {
      log.info("Could not find the required data by evaluating expression for - {}", keyExpression);
      return null;
    }
    return getDecryptedValueIfNeeded(accountIdentifier, value);
  }

  private Object getDecryptedValueIfNeeded(String accountIdentifier, Object value) {
    if (!(value instanceof String)) {
      return value;
    }
    Pattern pattern = Pattern.compile(ENV_VARIABLE_REGEX_PATTERN);
    Matcher matcher = pattern.matcher((String) value);
    if (!matcher.find()) {
      return value;
    }
    String env = matcher.group(0);
    String envName = matcher.group(1);
    Optional<BackstageEnvVariable> envVariableOpt =
        backstageEnvVariableService.findByEnvNameAndAccountIdentifier(envName, accountIdentifier);
    if (envVariableOpt.isPresent() && envVariableOpt.get().getType().equals(BackstageEnvVariable.TypeEnum.SECRET)) {
      BackstageEnvSecretVariable secret = (BackstageEnvSecretVariable) envVariableOpt.get();
      String decryptedValue = backstageEnvVariableService.getDecryptedValue(
          envName, secret.getHarnessSecretIdentifier(), accountIdentifier);
      if (decryptedValue.isEmpty()) {
        throw new UnexpectedException(format("Could not get the decrypted value for secret: %s, used "
                + "by env: %s, in account: %s",
            secret.getHarnessSecretIdentifier(), envName, accountIdentifier));
      }
      value = ((String) value).replace(env, decryptedValue);
    }
    return value;
  }
}
