/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JsonUtils {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                                                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                                        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                                                        .registerModule(new Jdk8Module())
                                                        .registerModule(new GuavaModule())
                                                        .registerModule(new JavaTimeModule());
  ;

  public static <T> T asObject(final String json, final Class<T> clazz) {
    try {
      return OBJECT_MAPPER.readValue(json, clazz);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static String asJson(final Object value) {
    try {
      return OBJECT_MAPPER.writeValueAsString(value);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static String asPrettyJson(final Object object) throws JsonProcessingException {
    return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
  }
}
