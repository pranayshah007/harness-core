/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static io.harness.annotations.dev.HarnessTeam.DX;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.YamlDTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(DX)
public class NGYamlUtils {
  private static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false)
          .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
          .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
          .enable(SerializationFeature.INDENT_OUTPUT);

  public static String getYamlString(YamlDTO yamlObject, ObjectMapper objectMapper) {
    if (yamlObject == null) {
      return null;
    }
    String yamlString;
    try {
      ObjectMapper internalMapper = objectMapper.copy();
      internalMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
      internalMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
      internalMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
      internalMapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);
      final JsonNode jsonNode = internalMapper.valueToTree(yamlObject);
      yamlString = YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException(
          String.format("Cannot create yaml from YamlObject %s", yamlObject.toString()), e);
    }
    yamlString = yamlString.replaceFirst("---\n", "");
    return yamlString;
  }

  public static String getYamlString(YamlDTO yamlObject) {
    return getYamlString(yamlObject, NG_DEFAULT_OBJECT_MAPPER);
  }
}
