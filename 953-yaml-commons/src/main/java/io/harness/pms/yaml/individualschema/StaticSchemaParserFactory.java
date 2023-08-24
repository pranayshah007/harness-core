
/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml.individualschema;

import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class StaticSchemaParserFactory {
  @Inject PipelineSchemaParserV0 pipelineSchemaParserV0;
  @Inject TemplateSchemaParserV0 templateSchemaParserV0;
  public AbstractStaticSchemaParser getParser(String schemaType, String version, JsonNode rootSchema) {
    if (YAMLFieldNameConstants.PIPELINE.equals(schemaType) && PipelineVersion.V0.equals(version)) {
      return pipelineSchemaParserV0.getInstance(rootSchema);
    } else if (YAMLFieldNameConstants.TEMPLATE.equals(schemaType) && PipelineVersion.V0.equals(version)) {
      return templateSchemaParserV0.getInstance(rootSchema);
    }
    throw new InvalidRequestException(
        String.format("Schema parser is not registered for the schemaType: %s with version: %s", schemaType, version));
  }
}