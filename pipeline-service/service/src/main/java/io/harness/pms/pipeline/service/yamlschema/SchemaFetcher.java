/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.PipelineServiceConfiguration;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.ResponseTimeRecorder;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class SchemaFetcher {
  public static final String PREQA = "stress";
  private JsonNode pipelineStaticSchemaV0 = null;
  private JsonNode pipelineStaticSchemaV1 = null;

  private JsonNode triggerStaticSchema = null;

  private final String PIPELINE_JSON_PATH_V0 = "static-schema/v0/pipeline.json";
  private final String PIPELINE_JSON_PATH_V1 = "static-schema/v1/pipeline.json";
  private final String TRIGGER_JSON_PATH_V0 = "static-schema/v0/trigger.json";

  @Inject PipelineServiceConfiguration pipelineServiceConfiguration;

  private final String PIPELINE_JSON = "pipeline.json";
  private final String TRIGGER_JSON = "trigger.json";
  private final String VERSION_V0 = "v0";
  private final String VERSION_V1 = "v1";

  public JsonNode fetchPipelineStaticYamlSchema(String version) {
    log.info("[PMS] Fetching static schema");
    try (ResponseTimeRecorder ignore = new ResponseTimeRecorder("Fetching Pipeline Static Schema")) {
      /*
        Fetches schema from Github repo in case of PRE_QA Env.
        If pipelineStaticSchema is null, then we read it from the pipeline.json resource file and set it to
        pipelineStaticSchema and return pipelineStaticSchema.
        uses pipelineStaticSchemaV0 for v0 schema and pipelineStaticSchemaV1 for v1 schema.
      */
      return getPipelineStaticSchema(version);
    } catch (InvalidRequestException exception) {
      throw exception;
    } catch (Exception e) {
      log.warn("[PMS] Unable to get pipeline static schema", e);
      throw new InvalidRequestException(String.format("Not able to read json from %s path",
                                            "v0".equals(version) ? PIPELINE_JSON_PATH_V0 : PIPELINE_JSON_PATH_V1),
          e);
    }
  }

  private JsonNode getPipelineStaticSchema(String version) throws IOException {
    String env = System.getenv("ENV");
    if (PREQA.equals(env)) {
      return fetchSchemaFromRepo(EntityType.PIPELINES, version);
    }
    if (version.equals(VERSION_V0)) {
      if (null == pipelineStaticSchemaV0) {
        pipelineStaticSchemaV0 = fetchFile(PIPELINE_JSON_PATH_V0);
      }
      return pipelineStaticSchemaV0;
    } else if (version.equals(VERSION_V1)) {
      if (null == pipelineStaticSchemaV1) {
        pipelineStaticSchemaV1 = fetchFile(PIPELINE_JSON_PATH_V1);
      }
      return pipelineStaticSchemaV1;
    } else {
      throw new InvalidRequestException(
          String.format("[PMS] Incorrect version [%s] of Pipeline Schema passed, Valid values are [v0, v1]", version));
    }
  }

  public JsonNode fetchTriggerStaticYamlSchema() {
    log.info("[PMS] Fetching static schema");
    try (ResponseTimeRecorder ignore = new ResponseTimeRecorder("Fetching Trigger Static Schema")) {
      /*
        Fetches schema from Github repo in case of PRE_QA Env.
        If triggerStaticSchema is null, then we read it from the trigger.json resource file and set it to
        triggerStaticSchema and return triggerStaticSchema.
      */
      return getTriggerStaticSchema();
    } catch (Exception e) {
      log.warn("[PMS] Unable to get trigger static schema", e);
      throw new InvalidRequestException(String.format("Not able to read json from %s path", TRIGGER_JSON_PATH_V0), e);
    }
  }

  private JsonNode getTriggerStaticSchema() throws IOException {
    String env = System.getenv("ENV");
    if (PREQA.equals(env)) {
      return fetchSchemaFromRepo(EntityType.TRIGGERS, "v0");
    }
    if (null == triggerStaticSchema) {
      triggerStaticSchema = fetchFile(TRIGGER_JSON_PATH_V0);
    }
    return triggerStaticSchema;
  }

  JsonNode fetchFile(String filePath) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String staticJson =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filePath)), StandardCharsets.UTF_8);
    return JsonUtils.asObject(staticJson, JsonNode.class);
  }

  public JsonNode fetchSchemaFromRepo(EntityType entityType, String version) throws IOException {
    String staticYamlRepoUrl = calculateFileURL(entityType, version);
    try {
      return JsonPipelineUtils.getMapper().readTree(new URL(staticYamlRepoUrl));

    } catch (IOException e) {
      log.error("Could not able to read schema for url {} ", staticYamlRepoUrl);
    }

    return fetchFile(PIPELINE_JSON_PATH_V0);
  }

  private String calculateFileURL(EntityType entityType, String version) {
    String fileURL = pipelineServiceConfiguration.getStaticSchemaFileURL();

    String entityTypeJson = "";
    switch (entityType) {
      case PIPELINES:
        entityTypeJson = PIPELINE_JSON;
        break;
      case TRIGGERS:
        entityTypeJson = TRIGGER_JSON;
        break;
      default:
        entityTypeJson = PIPELINE_JSON;
        log.error("Code should never reach here {}", entityType);
    }

    return format(fileURL, version, entityTypeJson);
  }
}
