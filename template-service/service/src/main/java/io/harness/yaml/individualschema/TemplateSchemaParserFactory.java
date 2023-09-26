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
public class TemplateSchemaParserFactory {
  @Inject TemplateSchemaParserV0 templateSchemaParserV0;
  private final String TEMPLATE_VERSION_V0 = "v0";

  public AbstractStaticSchemaParser getTemplateSchemaParser(String version) {
    switch (version) {
      case TEMPLATE_VERSION_V0:
        return templateSchemaParserV0;
      default:
        throw new InternalServerErrorException("Template schema parser not available for version: " + version);
    }
  }
}
