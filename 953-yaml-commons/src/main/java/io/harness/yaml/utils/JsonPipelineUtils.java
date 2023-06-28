/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.utils;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.exception.InvalidRequestException;
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
@UtilityClass
@Slf4j
public class JsonPipelineUtils {
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

  public static JsonNode asTree(Object obj) {
    return mapper.valueToTree(obj);
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
}
