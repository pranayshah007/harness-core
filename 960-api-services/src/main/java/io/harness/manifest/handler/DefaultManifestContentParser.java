/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.manifest.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgDummy;
import io.harness.exception.InvalidRequestException;

import com.amazonaws.event.ProgressListener;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class DefaultManifestContentParser {
  private static final ObjectMapper mapper = new ObjectMapper();

  static {
    mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    final var simpleModule = new SimpleModule().addAbstractTypeMapping(ProgressListener.class, AsgDummy.class);
    mapper.registerModule(simpleModule);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  public <T> T parseJson(String json, Class<T> clazz) {
    try {
      return mapper.readValue(json, clazz);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Cannot parse json with error", e);
    }
  }
}
