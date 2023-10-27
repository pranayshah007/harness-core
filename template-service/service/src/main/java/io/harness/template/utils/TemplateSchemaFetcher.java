/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.utils;
import io.harness.TemplateServiceConfiguration;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class TemplateSchemaFetcher {
  private JsonNode templateStaticSchemaJsonNodeV0 = null;
  private JsonNode templateStaticSchemaJsonNodeV1 = null;
  private final String TEMPLATE_JSON_PATH_V0 = "static-schema/v0/template.json";
  private final String TEMPLATE_JSON_PATH_V1 = "static-schema/v1/template.json";
  @Inject TemplateServiceConfiguration templateServiceConfiguration;

  private final String PRE_QA = "stress";
  private final String TEMPLATE_VO = "v0";
  private final String TEMPLATE_V1 = "v1";
  private final String TEMPLATE_JSON = "template.json";

  public JsonNode getStaticYamlSchema(String version) {
    String env = System.getenv("ENV");
    log.info(String.format("Current Environment :- %s ", env));
    if (PRE_QA.equals(env)) {
      String fileUrl = calculateFileURL(version);
      try {
        // Read the JSON file as JsonNode
        log.info(String.format("Fetching static schema with file URL %s ", fileUrl));
        return JsonPipelineUtils.getMapper().readTree(new URL(fileUrl));
      } catch (Exception ex) {
        log.error(String.format("Not able to read template schema file from %s path for stress env", fileUrl));
      }
    }
    log.info("Fetching static schema from resource file");
    return getStaticYamlSchemaFromResource(version);
  }

  private JsonNode getStaticYamlSchemaFromResource(String version) {
    try {
      /*
        if templateStaticSchemaJsonNode is null then we fetch schema from template.json and set it to
        templateStaticSchemaJsonNode else directly return templateStaticSchemaJsonNode
      */
      switch (version) {
        case TEMPLATE_VO:
          if (null == templateStaticSchemaJsonNodeV0) {
            templateStaticSchemaJsonNodeV0 = fetchFile(TEMPLATE_JSON_PATH_V0);
          }
          return templateStaticSchemaJsonNodeV0;
        case TEMPLATE_V1:
          if (null == templateStaticSchemaJsonNodeV1) {
            templateStaticSchemaJsonNodeV1 = fetchFile(TEMPLATE_JSON_PATH_V1);
          }
          return templateStaticSchemaJsonNodeV1;
        default:
          throw new InvalidRequestException(
              String.format("Incorrect version [%s] of Template Schema passed, Valid values are [v0, v1]", version));
      }
    } catch (IOException ex) {
      log.error(String.format("Not able to read json from %s path",
          version.equals(TEMPLATE_VO) ? TEMPLATE_JSON_PATH_V0 : TEMPLATE_JSON_PATH_V1));
    }
    // returning null will call the traditional getYamlSchema method in the parent function.
    return null;
  }

  private JsonNode fetchFile(String filePath) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String staticJson =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filePath)), StandardCharsets.UTF_8);
    return JsonUtils.asObject(staticJson, JsonNode.class);
  }

  private String calculateFileURL(String version) {
    String fileURL = templateServiceConfiguration.getStaticSchemaFileURL();
    return String.format(fileURL, version, TEMPLATE_JSON);
  }
}
