/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.jira;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.jira.beans.JiraField;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class JiraStepUtils {
  public Map<String, ParameterField<String>> processJiraFieldsList(List<JiraField> fields) {
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
          String.format("Duplicate jira fields: [%s]", String.join(", ", duplicateFields)));
    }
    return fieldsMap;
  }

  public Map<String, String> processJiraFieldsInParameters(Map<String, ParameterField<String>> fields) {
    if (EmptyPredicate.isEmpty(fields)) {
      return Collections.emptyMap();
    }

    Map<String, String> finalMap = new HashMap<>();
    for (Map.Entry<String, ParameterField<String>> entry : fields.entrySet()) {
      if (EmptyPredicate.isEmpty(entry.getKey()) || ParameterField.isNull(entry.getValue())) {
        continue;
      }
      if (entry.getValue().isExpression()) {
        throw new InvalidRequestException(String.format("Field [%s] has invalid jira field value", entry.getKey()));
      }
      if (entry.getValue().getValue() == null) {
        continue;
      }
      finalMap.put(entry.getKey(), entry.getValue().getValue());
    }
    return finalMap;
  }
}
