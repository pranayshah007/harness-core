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

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineSchemaParserFactory {
  @Inject PipelineSchemaParserV0 pipelineSchemaParserV0;
  public final String PIPELINE_VERSION_V0 = "v0";

  public SchemaParserInterface getPipelineSchemaParser(String version) {
    switch (version) {
      case PIPELINE_VERSION_V0:
        pipelineSchemaParserV0.initParser();
        return pipelineSchemaParserV0;
      default:
        throw new InternalServerErrorException("Pipeline schema parser not available for version: " + version);
    }
  }
}
