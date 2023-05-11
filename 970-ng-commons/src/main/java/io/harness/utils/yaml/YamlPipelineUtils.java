/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.exception.InvalidRequestException;
import io.harness.serializer.AnnotationAwareJsonSubtypeResolver;
import io.harness.serializer.JsonSubtypeResolver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.serializer.jackson.NGHarnessJacksonModule;
import java.io.IOException;
import java.net.URL;
import lombok.experimental.UtilityClass;

/**
 * YamlPipelineUtils is used to convert arbitrary class from yaml file.
 * ObjectMapper is preconfigured for yaml parsing. It uses custom
 * {@link JsonSubtypeResolver} which is responsible for scanning entire code base
 * and registering classes that are extending base interfaces or abstract classes defined in this framworkd
 * End user will typically extend interface and define {@link com.fasterxml.jackson.annotation.JsonTypeName} annotation
 * with proper type name. This way framework is decoupled from concrete implementations.
 */
@UtilityClass
public class YamlPipelineUtils {
  private final ObjectMapper mapper;

  static {
    mapper = new ObjectMapper(new YAMLFactory());
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapper.setSubtypeResolver(AnnotationAwareJsonSubtypeResolver.newInstance(mapper.getSubtypeResolver()));
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new NGHarnessJacksonModule());
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

  public String writeString(Object value) throws JsonProcessingException {
    return mapper.writeValueAsString(value);
  }

  public ObjectMapper getMapper() {
    return mapper;
  }

  public String getYamlString(Object value) throws JsonProcessingException {
    return writeString(value).replaceFirst("---\n", "");
  }

  /***
   * @param value
   * @return "--null" in case, value is null else the yaml string
   */
  public String writeYamlString(Object value) {
    try {
      return writeString(value).replaceFirst("---\n", "");
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Couldn't convert object to Yaml");
    }
  }
}
