/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SchemaConstants.PIPELINE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.SPEC_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.STAGES_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.TEMPLATE_NODE;

import io.harness.EntityType;
import io.harness.TemplateServiceConfiguration;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.common.EntityTypeConstants;
import io.harness.encryption.Scope;
import io.harness.exception.JsonSchemaException;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pipeline.yamlschema.PipelineYamlSchemaServiceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.JsonUtils;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateYamlSchemaMergeHelper;
import io.harness.template.mappers.TemplateChildEntityTypeToEntityTypeMapper;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.client.YamlSchemaClient;
import io.harness.yaml.utils.JsonPipelineUtils;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDC)
public class NGTemplateSchemaServiceImpl implements NGTemplateSchemaService {
  private PipelineYamlSchemaServiceClient pipelineYamlSchemaServiceClient;
  @Inject TemplateServiceConfiguration templateServiceConfiguration;
  private final String TEMPLATE_JSON = "template.json";
  Map<String, YamlSchemaClient> yamlSchemaClientMapper;
  private YamlSchemaProvider yamlSchemaProvider;
  private YamlSchemaValidator yamlSchemaValidator;
  private AccountClient accountClient;
  Integer allowedParallelStages;
  private final String TEMPLATE_JSON_PATH = "static-schema/template.json";
  private final String PRE_QA = "stress";

  @Inject
  public NGTemplateSchemaServiceImpl(PipelineYamlSchemaServiceClient pipelineYamlSchemaServiceClient,
      Map<String, YamlSchemaClient> yamlSchemaClientMapper, YamlSchemaProvider yamlSchemaProvider,
      YamlSchemaValidator yamlSchemaValidator, AccountClient accountClient,
      @Named("allowedParallelStages") Integer allowedParallelStages) {
    this.pipelineYamlSchemaServiceClient = pipelineYamlSchemaServiceClient;
    this.yamlSchemaClientMapper = yamlSchemaClientMapper;
    this.yamlSchemaProvider = yamlSchemaProvider;
    this.yamlSchemaValidator = yamlSchemaValidator;
    this.accountClient = accountClient;
    this.allowedParallelStages = allowedParallelStages;
  }

  @Override
  public JsonNode getTemplateSchema(String accountIdentifier, String projectIdentifier, String orgIdentifier,
      Scope scope, String templateChildType, TemplateEntityType templateEntityType) {
    try {
      return getTemplateYamlSchemaInternal(
          accountIdentifier, projectIdentifier, orgIdentifier, scope, templateChildType, templateEntityType);
    } catch (Exception e) {
      log.error("[Template] Failed to get template yaml schema", e);
      throw new JsonSchemaException(e.getMessage());
    }
  }

  private JsonNode getTemplateYamlSchemaInternal(String accountIdentifier, String projectIdentifier,
      String orgIdentifier, Scope scope, String templateChildType, TemplateEntityType templateEntityType) {
    JsonNode templateSchema =
        yamlSchemaProvider.getYamlSchema(EntityType.TEMPLATE, orgIdentifier, projectIdentifier, scope);

    if (TemplateEntityType.PIPELINE_TEMPLATE.equals(templateEntityType)) {
      templateChildType = EntityTypeConstants.PIPELINES;
    } else if (TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE.equals(templateEntityType)) {
      templateChildType = EntityTypeConstants.TEMPLATE_CUSTOM_DEPLOYMENT;
    } else if (TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE.equals(templateEntityType)) {
      templateChildType = EntityTypeConstants.ARTIFACT_SOURCE_TEMPLATE;
    }

    if (templateChildType == null || !schemaValidationSupported(templateEntityType)) {
      return templateSchema;
    }

    EntityType entityType = TemplateChildEntityTypeToEntityTypeMapper.getInstance().getEntityType(templateChildType);

    String yamlGroup = getYamlGroup(templateEntityType);
    // TODO: add a handler here to fetch for schemas that we can't get from pipeline as discussed. and refactor
    JsonNode specSchema;

    if (TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE.equals(templateEntityType)
        || TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE.equals(templateEntityType)) {
      specSchema = NGRestUtils.getResponse(yamlSchemaClientMapper.get("cd").getEntityYaml(
          accountIdentifier, orgIdentifier, projectIdentifier, entityType, scope));
    } else {
      specSchema = NGRestUtils
                       .getResponse(pipelineYamlSchemaServiceClient.getYamlSchema(
                           accountIdentifier, orgIdentifier, projectIdentifier, yamlGroup, entityType, scope))
                       .getSchema();
    }

    TemplateYamlSchemaMergeHelper.mergeYamlSchema(templateSchema, specSchema, entityType, templateEntityType);
    return templateSchema;
  }

  private String getYamlGroup(TemplateEntityType templateEntityType) {
    if (HarnessTeam.PIPELINE.equals(templateEntityType.getOwnerTeam())) {
      switch (templateEntityType) {
        case PIPELINE_TEMPLATE:
          return "PIPELINE";
        case STAGE_TEMPLATE:
          return "STAGE";
        case STEPGROUP_TEMPLATE:
          return "STEP_GROUP";
        case STEP_TEMPLATE:
          return "STEP";
        default:
          return null;
      }
    }
    return null;
  }

  private boolean schemaValidationSupported(TemplateEntityType templateEntityType) {
    switch (templateEntityType) {
      case PIPELINE_TEMPLATE:
      case STEP_TEMPLATE:
      case STEPGROUP_TEMPLATE:
      case STAGE_TEMPLATE:
      case CUSTOM_DEPLOYMENT_TEMPLATE:
      case ARTIFACT_SOURCE_TEMPLATE:
        return true;
      default:
        return false;
    }
  }

  public void validateYamlSchemaInternal(TemplateEntity templateEntity) {
    String accountIdentifier = templateEntity.getAccountIdentifier();
    String projectIdentifier = templateEntity.getProjectIdentifier();
    String orgIdentifier = templateEntity.getOrgIdentifier();
    String templateYaml = templateEntity.getYaml();
    long start = System.currentTimeMillis();
    if (TemplateYamlSchemaMergeHelper.isFeatureFlagEnabled(
            FeatureName.DISABLE_TEMPLATE_SCHEMA_VALIDATION, accountIdentifier, accountClient)) {
      return;
    }
    try {
      Scope scope = templateEntity.getTemplateScope() != null ? templateEntity.getTemplateScope()
          : projectIdentifier != null                         ? Scope.PROJECT
          : orgIdentifier != null                             ? Scope.ORG
                                                              : Scope.ACCOUNT;

      JsonNode schema;
      // Use Static schema if ff is enabled.
      if (TemplateYamlSchemaMergeHelper.isFeatureFlagEnabled(
              FeatureName.PIE_STATIC_YAML_SCHEMA, accountIdentifier, accountClient)) {
        schema = getStaticYamlSchema(accountIdentifier, orgIdentifier, projectIdentifier, templateEntity.getChildType(),
            templateEntity.getTemplateEntityType(), scope, "v0");
      } else {
        // Use traditional way of generating schema if ff is off
        schema = getTemplateSchema(accountIdentifier, projectIdentifier, orgIdentifier, scope,
            templateEntity.getChildType(), templateEntity.getTemplateEntityType());
      }
      String schemaString = JsonPipelineUtils.writeJsonString(schema);
      if (templateEntity.getTemplateEntityType().equals(TemplateEntityType.PIPELINE_TEMPLATE)) {
        String pathToJsonNode = TEMPLATE_NODE + "/" + SPEC_NODE + "/" + STAGES_NODE;
        yamlSchemaValidator.validate(templateYaml, schemaString,
            TemplateYamlSchemaMergeHelper.isFeatureFlagEnabled(
                FeatureName.DONT_RESTRICT_PARALLEL_STAGE_COUNT, accountIdentifier, accountClient),
            allowedParallelStages, pathToJsonNode);
      } else {
        yamlSchemaValidator.validate(
            templateYaml, schemaString, true, allowedParallelStages, PIPELINE_NODE + "/" + STAGES_NODE);
      }
    } catch (io.harness.yaml.validator.InvalidYamlException e) {
      log.info("[TEMPLATE_SCHEMA] Schema validation took total time {}ms", System.currentTimeMillis() - start);
      throw e;
    } catch (Exception ex) {
      log.error(ex.getMessage(), ex);
      YamlSchemaErrorWrapperDTO errorWrapperDTO =
          YamlSchemaErrorWrapperDTO.builder()
              .schemaErrors(Collections.singletonList(
                  YamlSchemaErrorDTO.builder().message(ex.getMessage()).fqn("$.pipeline").build()))
              .build();
      throw new io.harness.yaml.validator.InvalidYamlException(ex.getMessage(), ex, errorWrapperDTO, templateYaml);
    }
  }

  public JsonNode getStaticYamlSchema(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateChildType, TemplateEntityType entityType, Scope scope, String version) {
    String env = System.getenv("ENV");
    log.info(String.format("Current Environment :- %s ", env));
    if (PRE_QA.equals(env)) {
      String fileUrl = calculateFileURL(entityType, version);
      try {
        // Read the JSON file as JsonNode
        log.info(String.format("Fetching static schema with file URL %s ", fileUrl));
        return JsonPipelineUtils.getMapper().readTree(new URL(fileUrl));
      } catch (Exception ex) {
        log.error(String.format("Not able to read template schema file from %s path for stress env", fileUrl));
      }
    }
    log.info("Fetching static schema from resource file");
    return getStaticYamlSchemaFromResource(
        accountIdentifier, projectIdentifier, orgIdentifier, templateChildType, entityType, scope);
  }

  /*
  Based on environment and entityType, URL is created. For qa/stress branch is quality-assurance, for all other
  supported env branch will be master
  */
  public String calculateFileURL(TemplateEntityType entityType, String version) {
    String fileURL = templateServiceConfiguration.getStaticSchemaFileURL();

    String entityTypeJson = "";
    switch (entityType) {
      case STEP_TEMPLATE:
      case STAGE_TEMPLATE:
      case STEPGROUP_TEMPLATE:
      case PIPELINE_TEMPLATE:
      case CUSTOM_DEPLOYMENT_TEMPLATE:
      case ARTIFACT_SOURCE_TEMPLATE:
        entityTypeJson = TEMPLATE_JSON;
        break;
      default:
        entityTypeJson = TEMPLATE_JSON;
        log.error("Code should never reach here {}", entityType);
    }

    return String.format(fileURL, version, entityTypeJson);
  }

  public JsonNode fetchFile(String filePath) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String staticJson =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filePath)), StandardCharsets.UTF_8);
    return JsonUtils.asObject(staticJson, JsonNode.class);
  }
  private JsonNode getStaticYamlSchemaFromResource(String accountIdentifier, String projectIdentifier,
      String orgIdentifier, String templateChildType, TemplateEntityType entityType, Scope scope) {
    String filePath;
    switch (entityType) {
      case STEP_TEMPLATE:
      case STEPGROUP_TEMPLATE:
      case PIPELINE_TEMPLATE:
      case STAGE_TEMPLATE:
      case MONITORED_SERVICE_TEMPLATE:
      case SECRET_MANAGER_TEMPLATE:
      case ARTIFACT_SOURCE_TEMPLATE:
      case CUSTOM_DEPLOYMENT_TEMPLATE:
        filePath = TEMPLATE_JSON_PATH;
        break;
      default:
        return getTemplateSchema(
            accountIdentifier, projectIdentifier, orgIdentifier, scope, templateChildType, entityType);
    }

    try {
      return fetchFile(filePath);
    } catch (IOException ex) {
      log.error("Not able to read json from {} path", filePath);
    }
    return getTemplateSchema(accountIdentifier, projectIdentifier, orgIdentifier, scope, templateChildType, entityType);
  }
}
