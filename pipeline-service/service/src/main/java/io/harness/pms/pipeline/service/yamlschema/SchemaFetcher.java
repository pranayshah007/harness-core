/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import static io.harness.pms.yaml.YAMLFieldNameConstants.PIPELINE;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.PipelineServiceConfiguration;
import io.harness.SchemaCacheKey;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.pms.pipeline.service.yamlschema.cache.PartialSchemaDTOWrapperValue;
import io.harness.pms.pipeline.service.yamlschema.cache.SchemaCacheUtils;
import io.harness.pms.pipeline.service.yamlschema.cache.YamlSchemaDetailsWrapperValue;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.individualschema.AbstractStaticSchemaParser;
import io.harness.pms.yaml.individualschema.PipelineSchemaMetadata;
import io.harness.pms.yaml.individualschema.PipelineSchemaRequest;
import io.harness.pms.yaml.individualschema.StaticSchemaParserFactory;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.YamlSchemaDetailsWrapper;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import javax.cache.Cache;
import javax.cache.CacheException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class SchemaFetcher {
  private static final Duration THRESHOLD_PROCESS_DURATION = Duration.ofSeconds(5);
  public static final String PREQA = "stress";
  @Inject @Named("schemaDetailsCache") Cache<SchemaCacheKey, YamlSchemaDetailsWrapperValue> schemaDetailsCache;
  @Inject @Named("partialSchemaCache") Cache<SchemaCacheKey, PartialSchemaDTOWrapperValue> schemaCache;
  @Inject private SchemaGetterFactory schemaGetterFactory;
  @Inject private StaticSchemaParserFactory staticSchemaParserFactory;

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

  private final String PRE_QA = "stress";

  /**
   * Schema is taken from cache, so every modification will affect cache value.
   * In order to avoid that, we do deep copy of cached object
   */
  @Nullable
  public List<PartialSchemaDTO> fetchSchema(
      String accountId, ModuleType moduleType, List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    log.info("[PMS] Fetching schema for {}", moduleType.name());
    long startTs = System.currentTimeMillis();
    try {
      SchemaCacheKey schemaCacheKey = SchemaCacheKey.builder().moduleType(moduleType).build();

      if (schemaCache.containsKey(schemaCacheKey)) {
        log.info("[PMS_SCHEMA] Fetching schema for {} from cache for account {}", moduleType.name(), accountId);
        return SchemaCacheUtils.getPartialSchemaDTOList(schemaCache.get(schemaCacheKey));
      }

      SchemaGetter schemaGetter = schemaGetterFactory.obtainGetter(accountId, moduleType);
      List<PartialSchemaDTO> partialSchemaDTOS = schemaGetter.getSchema(yamlSchemaWithDetailsList);
      try {
        schemaCache.put(schemaCacheKey, SchemaCacheUtils.getPartialSchemaWrapperValue(partialSchemaDTOS));
      } catch (CacheException e) {
        log.error("Unable to set schema data into cache", e);
      }

      log.info("[PMS] Successfully fetched schema for {} for account {}", moduleType.name(), accountId);
      logWarnIfExceedsThreshold(moduleType, startTs);

      return partialSchemaDTOS;
    } catch (Exception e) {
      log.warn(format("[PMS] Unable to get %s schema", moduleType.name()), e);
      return null;
    }
  }

  public YamlSchemaDetailsWrapper fetchSchemaDetail(String accountId, ModuleType moduleType) {
    try {
      SchemaCacheKey schemaCacheKey = SchemaCacheKey.builder().moduleType(moduleType).build();
      if (schemaDetailsCache.containsKey(schemaCacheKey)) {
        log.info(
            "[PMS_SCHEMA] Fetching schema information for {} from cache for account {}", moduleType.name(), accountId);
        return SchemaCacheUtils.toYamlSchemaDetailsWrapper(schemaDetailsCache.get(schemaCacheKey));
      }

      long start = System.currentTimeMillis();
      SchemaGetter schemaGetter = schemaGetterFactory.obtainGetter(accountId, moduleType);
      YamlSchemaDetailsWrapper yamlSchemaDetailsWrapper = schemaGetter.getSchemaDetails();
      log.info("[PMS_SCHEMA] Fetching schema information for {} from remote for account {} with time took {}ms",
          moduleType.name(), accountId, System.currentTimeMillis() - start);
      try {
        schemaDetailsCache.put(schemaCacheKey, SchemaCacheUtils.toYamlSchemaDetailCacheValue(yamlSchemaDetailsWrapper));
      } catch (CacheException e) {
        log.error("Unable to set schema detail data into cache", e);
      }
      return yamlSchemaDetailsWrapper;
    } catch (Exception e) {
      log.warn(format("[PMS_SCHEMA] Unable to get %s schema information", moduleType.name()), e);
      return null;
    }
  }

  public void invalidateAllCache() {
    log.info("[PMS] Invalidating yaml schema cache");
    schemaCache.clear();
    schemaDetailsCache.clear();
    log.info("[PMS] Yaml schema cache was successfully invalidated");
  }

  private void logWarnIfExceedsThreshold(ModuleType moduleType, long startTs) {
    Duration processDuration = Duration.ofMillis(System.currentTimeMillis() - startTs);
    if (THRESHOLD_PROCESS_DURATION.compareTo(processDuration) < 0) {
      log.warn("[PMS] Fetching schema for {} service took {}s which is more than threshold of {}s", moduleType.name(),
          processDuration.getSeconds(), THRESHOLD_PROCESS_DURATION.getSeconds());
    }
  }

  // TODO: introduce cache while fetching step schema
  public JsonNode fetchStepYamlSchema(String accountId, String orgIdentifier, String projectIdentifier, Scope scope,
      EntityType entityType, String yamlGroup, List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    SchemaGetter schemaGetter = schemaGetterFactory.obtainGetter(accountId, entityType.getEntityProduct());
    return schemaGetter.fetchStepYamlSchema(
        orgIdentifier, projectIdentifier, scope, entityType, yamlGroup, yamlSchemaWithDetailsList);
  }

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

  public ObjectNode getIndividualSchema(String nodeGroup, String nodeType, String nodeGroupDifferentiator) {
    JsonNode jsonNode;
    try {
      jsonNode = getPipelineStaticSchema("v0");
    } catch (IOException ex) {
      log.error("Not able to read json from {} path", PIPELINE_JSON_PATH_V0);
      throw new InvalidRequestException(String.format("Not able to read json from %s path", PIPELINE_JSON_PATH_V0));
    }
    AbstractStaticSchemaParser abstractStaticSchemaParser =
        staticSchemaParserFactory.getParser(PIPELINE, PipelineVersion.V0, jsonNode);
    return abstractStaticSchemaParser.getIndividualSchema(
        PipelineSchemaRequest.builder()
            .individualSchemaMetadata(PipelineSchemaMetadata.builder()
                                          .nodeGroup(nodeGroup)
                                          .nodeGroupDifferentiator(nodeGroupDifferentiator)
                                          .nodeType(nodeType)
                                          .build())
            .build());
  }

  private JsonNode fetchFile(String filePath) throws IOException {
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
