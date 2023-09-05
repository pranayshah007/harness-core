/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.servicenow.beans.ServiceNowField;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public class ServiceNowStepUtils {
  public static final String SERVICENOW_WARNING_MESSAGE = "Field [%s] has invalid Service Now field value";
  public static final String NULL_STRING = "null";
  public Map<String, ParameterField<String>> processServiceNowFieldsList(List<ServiceNowField> fields) {
    if (fields == null) {
      return null;
    }

    Map<String, ParameterField<String>> fieldsMap = new HashMap<>();
    Set<String> duplicateFields = new HashSet<>();
    fields.forEach(field -> {
      if (fieldsMap.containsKey(field.getName())) {
        duplicateFields.add(field.getName());
      } else {
        fieldsMap.put(field.getName(), field.getValue());
      }
    });

    if (EmptyPredicate.isNotEmpty(duplicateFields)) {
      throw new InvalidRequestException(
          String.format("Duplicate ServiceNow fields: [%s]", String.join(", ", duplicateFields)));
    }
    return fieldsMap;
  }

  public Map<String, String> processServiceNowFieldsInSpec(
      Map<String, ParameterField<String>> fields, NGLogCallback ngLogCallback) {
    if (EmptyPredicate.isEmpty(fields)) {
      return Collections.emptyMap();
    }
    Map<String, String> finalMap = new HashMap<>();
    for (Map.Entry<String, ParameterField<String>> entry : fields.entrySet()) {
      if (EmptyPredicate.isEmpty(entry.getKey()) || ParameterField.isNull(entry.getValue())) {
        String warningMessage = String.format("ServiceNow field or value for [%s] is empty", entry.getKey());

        ngLogCallback.saveExecutionLog(LogHelper.color(warningMessage, LogColor.Yellow), LogLevel.WARN);
        continue;
      }
      if (entry.getValue().isExpression()) {
        String errorMessage = String.format(SERVICENOW_WARNING_MESSAGE, entry.getKey());
        if (ngLogCallback != null) {
          ngLogCallback.saveExecutionLog(LogHelper.color(errorMessage, LogColor.Red), LogLevel.ERROR);
        }
        throw new InvalidRequestException(errorMessage);
      }

      if (NULL_STRING.equals(entry.getValue().getValue())) {
        // Currently, unresolved expression are getting resolved as "null" string

        String warningMessage = String.format(SERVICENOW_WARNING_MESSAGE, entry.getKey());
        if (ngLogCallback != null) {
          ngLogCallback.saveExecutionLog(LogHelper.color(warningMessage, LogColor.Yellow), LogLevel.WARN);
        }
        log.error(warningMessage);
        continue;
      }

      if (entry.getValue().getValue() == null) {
        continue;
      }
      finalMap.put(entry.getKey(), entry.getValue().getValue());
    }
    return finalMap;
  }
}
