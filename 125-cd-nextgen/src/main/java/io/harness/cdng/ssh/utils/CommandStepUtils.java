/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.ssh.CommandStepParameters;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.yaml.ParameterField;
import io.harness.shell.ScriptType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@UtilityClass
@Slf4j
public class CommandStepUtils {
  public static final String HARNESS_BACKUP_PATH = "HARNESS_BACKUP_PATH";
  public static final String HARNESS_RUNTIME_PATH = "HARNESS_RUNTIME_PATH";
  public static final String HARNESS_STAGING_PATH = "HARNESS_STAGING_PATH";
  public static final String WINDOWS_RUNTIME_PATH = "WINDOWS_RUNTIME_PATH";

  public static Map<String, String> getEnvironmentVariables(
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

  public static Map<String, String> getHarnessBuiltInEnvVariables(
      InfrastructureOutcome infrastructure, ServiceStepOutcome serviceOutcome) {
    Map<String, String> harnessEnvVariables = new LinkedHashMap<>();
    if (ServiceSpecType.SSH.equals(serviceOutcome.getType())) {
      String basePath = format("$HOME/%s/%s/%s", serviceOutcome.getType().toLowerCase(Locale.ENGLISH),
          serviceOutcome.getName(), infrastructure.getEnvironment().getName());

      harnessEnvVariables.putIfAbsent(HARNESS_BACKUP_PATH, basePath + "/backup");
      harnessEnvVariables.putIfAbsent(HARNESS_RUNTIME_PATH, basePath + "/runtime");
      harnessEnvVariables.putIfAbsent(HARNESS_STAGING_PATH, basePath + "/staging");
    } else {
      String basePath = "%USERPROFILE%"
          + format("\\%s\\%s\\%s", serviceOutcome.getType().toLowerCase(Locale.ENGLISH), serviceOutcome.getName(),
              infrastructure.getEnvironment().getName());

      harnessEnvVariables.putIfAbsent(WINDOWS_RUNTIME_PATH, basePath + "\\runtime");
    }
    return harnessEnvVariables;
  }

  public static String getWorkingDirectory(
      ParameterField<String> workingDirectory, @Nonnull ScriptType scriptType, boolean onDelegate) {
    if (workingDirectory != null && EmptyPredicate.isNotEmpty(workingDirectory.getValue())) {
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

  public static List<String> getOutputVariables(Map<String, Object> outputVariables) {
    if (EmptyPredicate.isEmpty(outputVariables)) {
      return emptyList();
    }

    List<String> outputVars = new ArrayList<>();
    outputVariables.forEach((key, val) -> {
      if (val instanceof ParameterField) {
        ParameterField<?> parameterFieldValue = (ParameterField<?>) val;
        if (parameterFieldValue.getValue() == null) {
          throw new InvalidRequestException(format("Output variable [%s] value found to be null", key));
        }
        outputVars.add(((ParameterField<?>) val).getValue().toString());
      } else if (val instanceof String) {
        outputVars.add((String) val);
      } else {
        log.error(
            format("Value other than String or ParameterField found for output variable [%s]. value: [%s]", key, val));
      }
    });
    return outputVars;
  }

  public static String getHost(CommandStepParameters commandStepParameters) {
    return ParameterFieldHelper.getParameterFieldValue(commandStepParameters.getHost());
  }
}
