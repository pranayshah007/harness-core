/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh.utils;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.ssh.CommandStepParameters;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.utils.PhysicalDataCenterUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.security.SimpleEncryption;
import io.harness.shell.ScriptType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(CDP)
@UtilityClass
@Slf4j
public class CommandStepUtils {
  public static Map<String, String> mergeEnvironmentVariables(
      Map<String, Object> inputVariables, Map<String, String> builtInEnvVariables) {
    if (EmptyPredicate.isEmpty(inputVariables)) {
      return builtInEnvVariables;
    }
    Map<String, String> res = new LinkedHashMap<>();
    res.putAll(builtInEnvVariables);
    inputVariables.forEach((key, value) -> {
      if (value instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
        if (parameterFieldValue.getValue() == null) {
          throw new InvalidRequestException(format("Env. variable [%s] value found to be null", key));
        }
        res.put(key, parameterFieldValue.getValue().toString());
      } else if (value instanceof String) {
        res.put(key, (String) value);
      } else {
        log.error(
            format("Value other than String or ParameterField found for env. variable [%s]. value: [%s]", key, value));
      }
    });
    return res;
  }

  public static String getWorkingDirectory(
      ParameterField<String> workingDirectory, @Nonnull ScriptType scriptType, boolean onDelegate) {
    if (workingDirectory != null && isNotEmpty(workingDirectory.getValue())) {
      return workingDirectory.getValue();
    }
    String commandPath = null;
    if (scriptType == ScriptType.BASH) {
      commandPath = "/tmp";
    } else if (scriptType == ScriptType.POWERSHELL) {
      commandPath = "%TEMP%";
      if (onDelegate) {
        commandPath = "/tmp";
      }
    }
    return commandPath;
  }

  public static List<String> getOutputVariableValuesWithoutSecrets(
      Map<String, Object> outputVariables, Set<String> secretOutputVariableNames) {
    if (EmptyPredicate.isEmpty(outputVariables)) {
      return emptyList();
    }

    List<String> outputVarValues = new ArrayList<>();
    outputVariables.forEach((name, value) -> {
      if (EmptyPredicate.isEmpty(secretOutputVariableNames) || !secretOutputVariableNames.contains(name)) {
        if (value instanceof ParameterField) {
          ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
          if (parameterFieldValue.getValue() == null) {
            throw new InvalidRequestException(format("Output variable [%s] value is empty", name));
          }

          outputVarValues.add(((ParameterField<?>) value).getValue().toString());
        } else {
          log.error(format("Not found ParameterField value for output variable [%s]. value: [%s]", name, value));
        }
      }
    });
    return outputVarValues;
  }

  public static List<String> getSecretOutputVariableValues(
      Map<String, Object> outputVariables, Set<String> secretOutputVariableNames) {
    if (EmptyPredicate.isEmpty(outputVariables)) {
      return emptyList();
    }

    List<String> outputVarValues = new ArrayList<>(secretOutputVariableNames.size());
    outputVariables.forEach((name, value) -> {
      if (secretOutputVariableNames.contains(name)) {
        if (value instanceof ParameterField) {
          ParameterField<?> parameterFieldValue = (ParameterField<?>) value;
          if (parameterFieldValue.getValue() == null) {
            throw new InvalidRequestException(format("Output variable [%s] value is empty", name));
          }

          outputVarValues.add(((ParameterField<?>) value).getValue().toString());
        } else {
          log.error(format("Not found ParameterField value for output variable [%s]. value: [%s]", name, value));
        }
      }
    });
    return outputVarValues;
  }

  public static Map<String, String> prepareOutputVariables(Map<String, String> sweepingOutputEnvVariables,
      Map<String, Object> outputVariables, Set<String> secretOutputVariableNames) {
    if (EmptyPredicate.isEmpty(outputVariables) || EmptyPredicate.isEmpty(sweepingOutputEnvVariables)) {
      return Collections.emptyMap();
    }

    SimpleEncryption encryption = new SimpleEncryption();
    Map<String, String> resolvedOutputVariables = new HashMap<>();
    outputVariables.forEach((name, value) -> {
      if (value instanceof ParameterField) {
        Object variableNameToCollectOnDelegate = ((ParameterField<?>) value).getValue();
        // we are safe to use variableNameToCollectOnDelegate.toString() because variableNameToCollectOnDelegate can't
        // be null, see io.harness.yaml.utils.NGVariablesUtils#getMapOfVariablesWithoutSecretExpression
        String collectedVariableValueOnDelegate =
            sweepingOutputEnvVariables.get(variableNameToCollectOnDelegate.toString());
        if (isEmpty(collectedVariableValueOnDelegate)) {
          resolvedOutputVariables.put(name, collectedVariableValueOnDelegate);
          log.info(format("Not found delegate collected value for output variable [%s]. value: [%s]", name,
              variableNameToCollectOnDelegate));
        } else if (isSecretOutputVariable(secretOutputVariableNames, name)) {
          String encodedValue = EncodingUtils.encodeBase64(
              encryption.encrypt(collectedVariableValueOnDelegate.getBytes(StandardCharsets.UTF_8)));
          String finalValue = "${sweepingOutputSecrets.obtain(\"" + name + "\",\"" + encodedValue + "\")}";
          resolvedOutputVariables.put(name, finalValue);
        } else {
          resolvedOutputVariables.put(name, collectedVariableValueOnDelegate);
        }
      } else {
        log.error(format("Not found ParameterField value for output variable [%s]. value: [%s]", name, value));
      }
    });

    return resolvedOutputVariables;
  }

  private static boolean isSecretOutputVariable(Set<String> secretOutputVariableNames, String name) {
    return isNotEmpty(secretOutputVariableNames) && secretOutputVariableNames.contains(name);
  }

  public static String getHost(CommandStepParameters commandStepParameters) {
    String host = ParameterFieldHelper.getParameterFieldValue(commandStepParameters.getHost());
    return PhysicalDataCenterUtils.extractHostnameFromHost(host).orElseThrow(
        () -> new InvalidArgumentsException(format("Failed to resolve hostname: %s", host)));
  }
}
