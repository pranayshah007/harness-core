/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.servicenow.utils;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.USER;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ServiceNowException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.servicenow.ServiceNowFieldAllowedValueNG;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowFieldSchemaNG;
import io.harness.servicenow.ServiceNowFieldTypeNG;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Contains utils to parse ServiceNowFieldNG from jsonNode.
 *
 * This implementation is subject to change depending on what schema fields are required.
 * */
@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class ServiceNowFieldNGUtils {
  public ServiceNowFieldNG parseServiceNowFieldNG(JsonNode node) {
    try {
      String key = JsonNodeUtils.mustGetString(node, "name");
      String name = JsonNodeUtils.getString(node, "label", key);
      boolean required = JsonNodeUtils.getBoolean(node, "mandatory", false);
      boolean isCustom = key.startsWith("u_");
      ServiceNowFieldSchemaNG schema = parseServiceNowFieldSchemaNG(node);
      List<ServiceNowFieldAllowedValueNG> allowedValues = addAllowedValues(node.get("choices"));

      return ServiceNowFieldNG.builder()
          .key(key)
          .name(name)
          .required(required)
          .isCustom(isCustom)
          .schema(schema)
          .allowedValues(allowedValues)
          .build();

    } catch (Exception ex) {
      log.warn("Error occurred while parsing ServiceNowFieldNG from {}", node, ex);
      throw new ServiceNowException(
          String.format("Error occurred while getting serviceNow fields: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }

  public ServiceNowFieldSchemaNG parseServiceNowFieldSchemaNG(JsonNode node) {
    try {
      String typeStr = JsonNodeUtils.getString(node, "type", "string");
      boolean array;
      ServiceNowFieldTypeNG type;

      if (typeStr.equals("choice")) {
        array = true;
        type = ServiceNowFieldTypeNG.OPTION;
        typeStr = JsonNodeUtils.getString(node, "internal_type", "string");
      } else {
        array = false;
        type = ServiceNowFieldTypeNG.fromTypeString(typeStr);
        if (isNull(type) || ServiceNowFieldTypeNG.UNKNOWN.equals(type)) {
          log.info("Invalid or null type obtained: {}, from typeStr: {}", type, typeStr);
          typeStr = "";
        }
      }

      // customType is not there
      return ServiceNowFieldSchemaNG.builder()
          .array(array)
          .type(type)
          .typeStr(typeStr)
          .customType(JsonNodeUtils.getString(node, "custom", null))
          .isMultilineText(JsonNodeUtils.getBoolean(node, "multitext", false))
          .build();
    } catch (Exception ex) {
      log.warn("Error occurred while parsing schema from {}", node, ex);
      throw new ServiceNowException(
          String.format("Error occurred while parsing field schema: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }

  public List<ServiceNowFieldAllowedValueNG> addAllowedValues(JsonNode node) {
    try {
      List<ServiceNowFieldAllowedValueNG> allowedValues = new ArrayList<>();
      if (isNull(node) || !node.isArray()) {
        return allowedValues;
      }

      ArrayNode allowedValuesNode = (ArrayNode) node;
      allowedValuesNode.forEach(av -> allowedValues.add(parseServiceNowFieldAllowedValueNG(av)));
      return allowedValues;
    } catch (Exception ex) {
      log.warn("Error occurred while parsing allowed values from {}", node, ex);
      throw new ServiceNowException(
          String.format("Error occurred while parsing allowed values: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }

  public List<JsonNode> parseServiceNowMetadataResponse(String jsonResponseAsString) {
    try {
      JsonNode columns = convertStringToJsonNode(jsonResponseAsString);
      List<JsonNode> fields = new ArrayList<>();
      for (JsonNode fieldObj : columns) {
        fields.add(fieldObj);
      }
      return fields;
    } catch (ServiceNowException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("Error occurred while reading json metadata response {}", jsonResponseAsString, ex);
      throw new ServiceNowException(
          String.format("Error occurred while reading json metadata response: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }

  private ServiceNowFieldAllowedValueNG parseServiceNowFieldAllowedValueNG(JsonNode node) {
    String id = JsonNodeUtils.getString(node, "value");
    String name = JsonNodeUtils.getString(node, "label");
    return ServiceNowFieldAllowedValueNG.builder().id(id).name(name).build();
  }

  private static JsonNode convertStringToJsonNode(String jsonString) {
    ObjectMapper objectMapper = new ObjectMapper();

    try {
      return objectMapper.readTree(jsonString);
    } catch (JsonProcessingException ex) {
      log.warn("Error occurred while reading json metadata response {}", jsonString, ex);
      throw new ServiceNowException(
          String.format("Error occurred while reading json metadata response: %s", ExceptionUtils.getMessage(ex)),
          SERVICENOW_ERROR, USER, ex);
    }
  }
}
