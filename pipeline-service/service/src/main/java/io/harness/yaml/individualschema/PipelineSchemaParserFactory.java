/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.individualschema;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InternalServerErrorException;
import io.harness.utils.PipelineVersionConstants;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineSchemaParserFactory {
  @Inject PipelineSchemaParserV0 pipelineSchemaParserV0;
  @Inject PipelineSchemaParserV1 pipelineSchemaParserV1;

  public SchemaParserInterface getPipelineSchemaParser(String version) {
    AbstractStaticSchemaParser schemaParserInterface = null;
    if (PipelineVersionConstants.PIPELINE_VERSION_V0.getValue().equals(version)) {
      schemaParserInterface = pipelineSchemaParserV0;
    } else if (PipelineVersionConstants.PIPELINE_VERSION_V1.getValue().equals(version)) {
      schemaParserInterface = pipelineSchemaParserV1;
    } else {
      throw new InternalServerErrorException("Pipeline schema parser not available for version: " + version);
    }

    schemaParserInterface.initParser();
    return schemaParserInterface;
  }
}
