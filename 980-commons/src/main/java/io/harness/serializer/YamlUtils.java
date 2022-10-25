/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;

public class YamlUtils {
  private final ObjectMapper mapper;

  /**
   * Instantiates a new yaml utils.
   */
  public YamlUtils() {
    mapper = new ObjectMapper(new YAMLFactory());
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.registerModule(new JavaTimeModule());
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
  public <T> T read(String yaml, Class<T> cls) throws JsonParseException, JsonMappingException, IOException {
    return mapper.readValue(yaml, cls);
  }

  /**
   * Read.
   *
   * @param <T>           the generic type
   * @param yaml          the yaml
   * @param typeReference the type reference
   * @return the t
   * @throws JsonParseException   the json parse exception
   * @throws JsonMappingException the json mapping exception
   * @throws IOException          Signals that an I/O exception has occurred.
   */
  public <T> T read(String yaml, TypeReference<T> typeReference)
      throws JsonParseException, JsonMappingException, IOException {
    return mapper.readValue(yaml, typeReference);
  }
}
