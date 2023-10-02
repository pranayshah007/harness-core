/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.utils;

import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.SchemaFieldConstants;
import io.harness.serializer.AnnotationAwareJsonSubtypeResolver;
import io.harness.serializer.JsonSubtypeResolver;
import io.harness.serializer.jackson.PipelineJacksonModule;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.serializer.jackson.NGHarnessJacksonModule;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * JsonPipelineUtils is used to convert arbitrary class from yaml file.
 * ObjectMapper is preconfigured for yaml parsing. It uses custom
 * {@link JsonSubtypeResolver} which is responsible for scanning entire code base
 * and registering classes that are extending base interfaces or abstract classes defined in this framework
 * End user will typically extend interface and define {@link com.fasterxml.jackson.annotation.JsonTypeName} annotation
 * with proper type name. This way framework is decoupled from concrete implementations.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@Slf4j
public class JsonPipelineUtils {
  private final String EMPTY_STRING = "";
  private static final ObjectMapper mapper;

  static {
    mapper = new ObjectMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    mapper.setSubtypeResolver(AnnotationAwareJsonSubtypeResolver.newInstance(mapper.getSubtypeResolver()));
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new NGHarnessJacksonModule());
    mapper.registerModule(new PipelineJacksonModule());
  }

  /**
   * Read.
   *
   * @param <T>  the generic type
   * @param yaml the yaml
   * @param cls  the cls
   * @return the t
   * @throws JsonParseException   the json parse exception
   * @throws JsonMappingException the json mapping exception
   * @throws IOException          Signals that an I/O exception has occurred.
   */
  public <T> T read(String yaml, Class<T> cls) throws IOException {
    return mapper.readValue(yaml, cls);
  }

  /**
   * Read.
   *
   * @param <T>  the generic type
   * @param yaml the yaml
   * @param cls  the cls
   * @return the t
   * @throws JsonParseException   the json parse exception
   * @throws JsonMappingException the json mapping exception
   * @throws IOException          Signals that an I/O exception has occurred.
   */
  public <T> T read(URL yaml, Class<T> cls) throws IOException {
    return mapper.readValue(yaml, cls);
  }

  /**
   * Write Json String
   *
   * @param value Java Object
   * @return String
   * @throws JsonProcessingException Signals the Json parsing exception
   */
  public String writeJsonString(Object value) throws JsonProcessingException {
    return mapper.writeValueAsString(value);
  }

  public String getJsonString(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Couldn't convert object to Yaml", e);
    }
  }

  public ObjectMapper getMapper() {
    return mapper;
  }

  public String writeYamlString(Object value) throws IOException {
    String jsonString = JsonPipelineUtils.writeJsonString(value);
    JsonNode jsonNode = JsonPipelineUtils.getMapper().readTree(jsonString);
    return YamlPipelineUtils.writeYamlString(jsonNode);
  }

  // use this method only when you want only non-empty values in jsonNode else use asTreeUsingDefaultObjectMapper
  public static JsonNode asTree(Object obj) {
    return mapper.valueToTree(obj);
  }

  public static JsonNode asTreeUsingDefaultObjectMapper(Object obj) {
    return NG_DEFAULT_OBJECT_MAPPER.valueToTree(obj);
  }

  /**
   * Converts object to jsonNode for advanced processing.
   *
   * @param json         String
   * @return the json node
   */
  public static JsonNode readTree(String json) {
    try {
      return NG_DEFAULT_OBJECT_MAPPER.readTree(json);
    } catch (Exception e) {
      for (StackTraceElement elem : e.getStackTrace()) {
        log.error("Trace: {}", elem);
      }
      throw new InvalidRequestException(e.getMessage());
    }
  }

  public static Map<String, Object> jsonNodeToMap(JsonNode node) {
    return NG_DEFAULT_OBJECT_MAPPER.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {});
  }
  public boolean isPresent(JsonNode jsonNode, SchemaFieldConstants field) {
    return get(jsonNode, field) != null;
  }

  public boolean isPresent(JsonNode jsonNode, String field) {
    return get(jsonNode, field) != null;
  }

  public String getText(JsonNode jsonNode, SchemaFieldConstants field) {
    return get(jsonNode, field).asText();
  }
  public String getText(JsonNode jsonNode, String field) {
    return get(jsonNode, field).asText();
  }

  public String getTextOrEmpty(JsonNode jsonNode, SchemaFieldConstants field) {
    if (isPresent(jsonNode, field)) {
      return getText(jsonNode, field);
    }
    return EMPTY_STRING;
  }

  public ArrayNode getArrayNode(JsonNode jsonNode, SchemaFieldConstants field) {
    return (ArrayNode) get(jsonNode, field);
  }

  public JsonNode get(JsonNode jsonNode, SchemaFieldConstants field) {
    return get(jsonNode, field.name());
  }

  public JsonNode get(JsonNode jsonNode, String field) {
    return jsonNode.get(field);
  }

  public boolean isStringTypeField(JsonNode jsonNode, SchemaFieldConstants field) {
    return isStringTypeField(jsonNode, field.name());
  }

  public boolean isStringTypeField(JsonNode jsonNode, String field) {
    return checkNodeType(jsonNode, field, JsonNodeType.STRING);
  }

  public boolean isObjectTypeField(JsonNode jsonNode, String field) {
    return checkNodeType(jsonNode, field, JsonNodeType.OBJECT);
  }

  public boolean isArrayNodeField(JsonNode jsonNode, String fieldName) {
    return checkNodeType(jsonNode, fieldName, JsonNodeType.ARRAY);
  }

  private boolean checkNodeType(JsonNode jsonNode, String field, JsonNodeType jsonNodeType) {
    if (isPresent(jsonNode, field)) {
      return jsonNode.get(field).getNodeType() == jsonNodeType;
    }
    return false;
  }

  public JsonNode getJsonNodeByPath(JsonNode jsonNode, String path) {
    String[] pathComponents = path.split("/");
    int i = 0;
    if (pathComponents[0].equals("#") && pathComponents.length > 1 && DEFINITIONS_NODE.equals(pathComponents[1])) {
      i++;
    }
    for (; i < pathComponents.length; i++) {
      if (jsonNode == null) {
        break;
      }
      jsonNode = jsonNode.get(pathComponents[i]);
    }
    return jsonNode;
  }
}
