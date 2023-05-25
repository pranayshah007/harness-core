/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.yaml;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.serializer.AnnotationAwareJsonSubtypeResolver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.serializer.jackson.EdgeCaseRegexModule;
import io.serializer.jackson.NGHarnessJacksonModule;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDC)
public class CDYamlUtils {
  private final ObjectMapper mapper;

  static {
    mapper = new ObjectMapper(new YAMLFactory()
                                  .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                  .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                                  .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS));
    mapper.registerModule(new EdgeCaseRegexModule());
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
   * @param value the object to convert to yaml
   * @return "--null" in case, value is null else the yaml string
   */
  public String writeYamlString(Object value) {
    try {
      return writeString(value).replaceFirst("---\n", "");
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Couldn't convert object to Yaml");
    }
  }

  public static String getYamlString(Object object, List<YAMLGenerator.Feature> featureToEnable,
      List<YAMLGenerator.Feature> featureToDisable) throws JsonProcessingException {
    YAMLFactory yamlFactory = new YAMLFactory()
                                  .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                  .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                                  .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS);

    if (isNotEmpty(featureToEnable)) {
      for (YAMLGenerator.Feature feature : featureToEnable) {
        yamlFactory.enable(feature);
      }
    }
    if (isNotEmpty(featureToDisable)) {
      for (YAMLGenerator.Feature feature : featureToDisable) {
        yamlFactory.disable(feature);
      }
    }

    ObjectMapper localMapper = new ObjectMapper(yamlFactory);
    localMapper.registerModule(new EdgeCaseRegexModule());
    localMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    localMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    localMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    localMapper.setSubtypeResolver(AnnotationAwareJsonSubtypeResolver.newInstance(localMapper.getSubtypeResolver()));
    localMapper.registerModule(new Jdk8Module());
    localMapper.registerModule(new GuavaModule());
    localMapper.registerModule(new JavaTimeModule());
    localMapper.registerModule(new NGHarnessJacksonModule());
    return writeString(object, localMapper);
  }

  private String writeString(Object value, ObjectMapper objectMapper) throws JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
