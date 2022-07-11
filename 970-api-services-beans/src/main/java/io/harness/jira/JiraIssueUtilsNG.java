/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.JiraClientException;

import com.google.common.base.Splitter;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class JiraIssueUtilsNG {
  public List<String> splitByComma(String value) {
    List<String> values = new ArrayList<>();
    if (isBlank(value)) {
      return values;
    }

    for (String s : Splitter.on(JiraConstantsNG.COMMA_SPLIT_PATTERN).trimResults().split(value)) {
      if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
        String str = s.substring(1, s.length() - 1).trim();
        values.add(str.replaceAll("\"\"", "\""));
      } else {
        values.add(s);
      }
    }
    return values;
  }

  public String prepareIssueUrl(String baseUrl, String key) {
    try {
      URL issueUrl = new URL(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "browse/" + key);
      return issueUrl.toString();
    } catch (MalformedURLException e) {
      throw new JiraClientException(String.format("Invalid jira base url: %s", baseUrl), true);
    }
  }

  public void updateFieldValues(Map<String, Object> currFieldValues, Map<String, JiraFieldNG> issueTypeFields,
      Map<String, String> fields, boolean checkRequiredFields) {
    if (issueTypeFields == null) {
      issueTypeFields = new HashMap<>();
    }
    if (fields == null) {
      fields = new HashMap<>();
    }

    Map<String, JiraFieldNG> finalIssueTypeFields = issueTypeFields;

    fields = parseFieldsForCGCalls(finalIssueTypeFields, fields);

    Set<String> invalidFields =
        fields.keySet().stream().filter(k -> !finalIssueTypeFields.containsKey(k)).collect(Collectors.toSet());
    if (EmptyPredicate.isNotEmpty(invalidFields)) {
      throw new JiraClientException(
          String.format("Some fields are invalid for this jira issue type: %s", String.join(", ", invalidFields)),
          true);
    }

    Map<String, String> finalFields = fields;
    if (checkRequiredFields) {
      Set<String> requiredFieldsNotPresent =
          issueTypeFields.entrySet()
              .stream()
              // TECHDEBIT: remove the USER validation after support for user type fields
              .filter(
                  e -> e.getValue().isRequired() && !e.getValue().getSchema().getType().equals(JiraFieldTypeNG.USER))
              .map(Map.Entry::getKey)
              .filter(f -> !finalFields.containsKey(f))
              .collect(Collectors.toSet());
      if (EmptyPredicate.isNotEmpty(requiredFieldsNotPresent)) {
        throw new JiraClientException(String.format("Some required fields for this jira issue type are missing: %s",
                                          String.join(", ", requiredFieldsNotPresent)),
            true);
      }
    }

    Set<String> fieldKeys = new HashSet<>(fields.keySet());

    // Remove time tracking user facing fields from fieldKeys. If they are present do special handling for them.
    fieldKeys.remove(JiraConstantsNG.ORIGINAL_ESTIMATE_NAME);
    fieldKeys.remove(JiraConstantsNG.REMAINING_ESTIMATE_NAME);
    addTimeTrackingField(currFieldValues, fields);

    fieldKeys.forEach(key -> addKey(currFieldValues, key, finalIssueTypeFields.get(key), finalFields.get(key)));
  }

  private Map<String, String> parseFieldsForCGCalls(
      Map<String, JiraFieldNG> finalIssueTypeFields, Map<String, String> fields) {
    Map<String, String> fieldIdsMapToName =
        finalIssueTypeFields.entrySet().stream().collect(Collectors.toMap(e -> e.getValue().getKey(), e -> e.getKey()));
    return fields.entrySet().stream().collect(Collectors.toMap(field -> {
      if (fieldIdsMapToName.containsKey(field.getKey()) && !finalIssueTypeFields.containsKey(field.getKey())) {
        return fieldIdsMapToName.get(field.getKey());
      }
      return field.getKey();
    }, Map.Entry::getValue));
  }

  private void addTimeTrackingField(Map<String, Object> currFieldValues, Map<String, String> fields) {
    String originalEstimate = fields.get(JiraConstantsNG.ORIGINAL_ESTIMATE_NAME);
    String remainingEstimate = fields.get(JiraConstantsNG.REMAINING_ESTIMATE_NAME);
    if (EmptyPredicate.isEmpty(originalEstimate) && EmptyPredicate.isEmpty(remainingEstimate)) {
      return;
    }

    currFieldValues.put(
        JiraConstantsNG.TIME_TRACKING_KEY, new JiraTimeTrackingFieldNG(originalEstimate, remainingEstimate));
  }

  private void addKey(Map<String, Object> currFieldValues, String key, JiraFieldNG field, String value) {
    if (key == null || field == null || EmptyPredicate.isEmpty(value)) {
      return;
    }

    if (!field.getSchema().isArray()) {
      Object finalValue = convertToFinalValue(field, key, value);
      if (finalValue != null) {
        currFieldValues.put(field.getKey(), finalValue);
      }
      return;
    }

    List<String> values = splitByComma(value);
    currFieldValues.put(field.getKey(),
        values.stream()
            .map(v -> convertToFinalValue(field, key, v))
            .filter(Objects::nonNull)
            .collect(Collectors.toList()));
  }

  private Object convertToFinalValue(JiraFieldNG field, String name, String value) {
    switch (field.getSchema().getType()) {
      case USER:
        return new JiraFieldUserPickerNG(value);
      case STRING:
        return value;
      case NUMBER:
        Long lVal = tryParseLong(value);
        if (lVal != null) {
          return lVal;
        }
        return parseDouble(name, value);
      case DATE:
        return parseDate(name, value);
      case DATETIME:
        return parseDateTime(name, value);
      case OPTION:
        return convertOptionToFinalValue(field, name, value);
      default:
        throw new JiraClientException(String.format("Unsupported field type: %s", field.getSchema().getType()), true);
    }
  }

  private Object convertOptionToFinalValue(JiraFieldNG field, String name, String value) {
    List<JiraFieldAllowedValueNG> allowedValuesList =
        field.getAllowedValues() == null ? Collections.emptyList() : field.getAllowedValues();
    Optional<JiraFieldAllowedValueNG> allowedValues =
        allowedValuesList.stream().filter(av -> av.matchesValue(value)).findFirst();
    if (!allowedValues.isPresent()) {
      throw new JiraClientException(
          String.format("Value [%s] is not allowed for field [%s]. Allowed values: [%s]", value, name,
              allowedValuesList.stream().map(JiraFieldAllowedValueNG::displayValue).collect(Collectors.joining(", "))),
          true);
    }
    return allowedValues.get();
  }

  private Long tryParseLong(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private Double parseDouble(String name, String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException ignored) {
      throw new JiraClientException(String.format("Invalid number value for field [%s]", name), true);
    }
  }

  private String parseDate(String name, String value) {
    for (DateTimeFormatter formatter : JiraConstantsNG.DATE_FORMATTERS) {
      try {
        return LocalDate.parse(value, formatter).format(JiraConstantsNG.DATE_FORMATTER);
      } catch (DateTimeParseException ignored) {
        // ignored
      }
    }
    throw new JiraClientException(String.format("Invalid date value for field [%s]", name), true);
  }

  private String parseDateTime(String name, String value) {
    for (DateTimeFormatter formatter : JiraConstantsNG.DATETIME_FORMATTERS) {
      try {
        return ZonedDateTime.parse(value, formatter).format(JiraConstantsNG.DATETIME_FORMATTER);
      } catch (DateTimeParseException ignored) {
        // ignored
      }
    }
    throw new JiraClientException(String.format("Invalid datetime value for field [%s]", name), true);
  }
}
