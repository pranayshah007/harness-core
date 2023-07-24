/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helm;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@UtilityClass
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class HelmChartYamlMapper {
  private final ObjectMapper yamlObjectMapper =
      new ObjectMapper(new YAMLFactory())
          // prevent failure due to non backward compatibility changes in Chart.yaml schema
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
          .disable(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS)
          .disable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
          .disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)
          .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
          .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
          .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
          .addHandler(new DeserializationProblemHandler() {
            @Override
            public Object handleUnexpectedToken(
                DeserializationContext ctxt, JavaType targetType, JsonToken t, JsonParser p, String failureMsg) {
              log.warn("Unexpected token while deserializing Chart.yaml: {}", failureMsg);
              return null;
            }
          });

  public HelmChartYaml deserialize(InputStream helmChartYamlStream) throws IOException {
    return yamlObjectMapper.readValue(helmChartYamlStream, HelmChartYaml.class);
  }
}
