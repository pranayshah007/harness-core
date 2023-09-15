/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.serializer.YamlUtils;

import com.amazonaws.event.ProgressListener;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class AsgContentParser {
  private static final ObjectMapper defaultMapper = new ObjectMapper();
  private YamlUtils yamlUtils = new YamlUtils();
  private static final ObjectMapper mapperWithFailOnUnknownPropertiesFalse = new ObjectMapper();

  static {
    defaultMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    mapperWithFailOnUnknownPropertiesFalse.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
  }

  static {
    mapperWithFailOnUnknownPropertiesFalse.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    final var simpleModule = new SimpleModule().addAbstractTypeMapping(ProgressListener.class, AsgDummy.class);
    mapperWithFailOnUnknownPropertiesFalse.registerModule(simpleModule);
    mapperWithFailOnUnknownPropertiesFalse.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
  }

  public <T> T parseJson(String json, Class<T> clazz, boolean failOnUnknownProperties) {
    try {
      ObjectMapper objectMapper = getMapper(failOnUnknownProperties);

      return objectMapper.readValue(json, clazz);
    } catch (IOException e) {
      throw new InvalidRequestException(format("Cannot parse json for class `%s` with error", clazz.getName()), e);
    }
  }

  public String toString(Object object, boolean failOnUnknownProperties) {
    try {
      ObjectMapper objectMapper = getMapper(failOnUnknownProperties);
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Cannot convert the request object to json due to following error:", e);
    }
  }

  private ObjectMapper getMapper(boolean failOnUnknownProperties) {
    if (failOnUnknownProperties) {
      return defaultMapper;
    } else {
      return mapperWithFailOnUnknownPropertiesFalse;
    }
  }
}
