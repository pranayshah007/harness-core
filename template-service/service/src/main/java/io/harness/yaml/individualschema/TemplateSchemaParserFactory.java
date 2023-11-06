/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.individualschema;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InternalServerErrorException;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class TemplateSchemaParserFactory {
  @Inject TemplateSchemaParserV0 templateSchemaParserV0;
  @Inject TemplateSchemaParserV1 templateSchemaParserV1;
  private final String TEMPLATE_VERSION_V0 = "v0";
  private final String TEMPLATE_VERSION_V1 = "v1";

  public AbstractStaticSchemaParser getTemplateSchemaParser(String version) {
    switch (version) {
      case TEMPLATE_VERSION_V0:
        return templateSchemaParserV0;
      case TEMPLATE_VERSION_V1:
        return templateSchemaParserV1;
      default:
        throw new InternalServerErrorException("Template schema parser not available for version: " + version);
    }
  }
}
