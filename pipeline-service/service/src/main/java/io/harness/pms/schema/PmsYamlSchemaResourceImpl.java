/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.schema;

import static io.harness.EntityType.PIPELINES;
import static io.harness.EntityType.TEMPLATE;
import static io.harness.EntityType.TRIGGERS;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.configuration.DeployVariant;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.service.NGTriggerYamlSchemaService;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.yaml.SchemaErrorResponse;
import io.harness.pms.yaml.YamlSchemaResponse;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.schema.YamlSchemaResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotSupportedException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
@OwnedBy(PIPELINE)
public class PmsYamlSchemaResourceImpl implements YamlSchemaResource, PmsYamlSchemaResource {
  private final PMSYamlSchemaService pmsYamlSchemaService;
  private final NGTriggerYamlSchemaService ngTriggerYamlSchemaService;

  private final String deployMode = System.getenv().get("DEPLOY_MODE");
  private final String PIPELINE_JSON_PATH = "static-schema/pipeline.json";
  private final String TEMPLATE_JSON_PATH = "static-schema/template.json";

  public ResponseDTO<JsonNode> getYamlSchema(@NotNull EntityType entityType, String projectIdentifier,
      String orgIdentifier, Scope scope, String identifier, @NotNull String accountIdentifier) {
    JsonNode schema = null;
    if (entityType == PIPELINES) {
      schema = pmsYamlSchemaService.getPipelineYamlSchema(accountIdentifier, projectIdentifier, orgIdentifier, scope);
    } else if (entityType == TRIGGERS) {
      schema = ngTriggerYamlSchemaService.getTriggerYamlSchema(projectIdentifier, orgIdentifier, identifier, scope);
    } else {
      throw new NotSupportedException(String.format("Entity type %s is not supported", entityType.getYamlName()));
    }

    return ResponseDTO.newResponse(schema);
  }

  @Override
  public ResponseDTO<JsonNode> getStaticYamlSchema(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, EntityType entityType, Scope scope, String version) {
    String env = System.getenv("ENV");
    /*
    Currently static schema is not supported for community and onPrem env.
     */
    if (!validateIfStaticSchemaRequired(entityType, env)) {
      return getStaticYamlSchemaFromResource(
          accountIdentifier, projectIdentifier, orgIdentifier, identifier, entityType, scope);
    }

    JsonNode staticJson = pmsYamlSchemaService.getStaticSchema(
        accountIdentifier, projectIdentifier, orgIdentifier, identifier, entityType, scope, version);

    // return static json if not empty or return the Pojo Schema
    return staticJson != null
        ? ResponseDTO.newResponse(staticJson)
        : getYamlSchema(entityType, projectIdentifier, orgIdentifier, scope, identifier, accountIdentifier);
  }

  private ResponseDTO<JsonNode> getStaticYamlSchemaFromResource(String accountIdentifier, String projectIdentifier,
      String orgIdentifier, String identifier, EntityType entityType, Scope scope) {
    String filePath;
    switch (entityType) {
      case PIPELINES:
        filePath = PIPELINE_JSON_PATH;
        break;
      case TEMPLATE:
        filePath = TEMPLATE_JSON_PATH;
        break;
      default:
        return getYamlSchema(entityType, projectIdentifier, orgIdentifier, scope, identifier, accountIdentifier);
    }

    try {
      return ResponseDTO.newResponse(fetchFile(filePath));
    } catch (IOException ex) {
      log.error("Not able to read json from {} path", filePath);
    }
    return getYamlSchema(entityType, projectIdentifier, orgIdentifier, scope, identifier, accountIdentifier);
  }
  public JsonNode fetchFile(String filePath) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String staticJson =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filePath)), StandardCharsets.UTF_8);
    return JsonUtils.asObject(staticJson, JsonNode.class);
  }

  private boolean validateIfStaticSchemaRequired(EntityType entityType, String env) {
    // static schema is not supported for empty env or on-prem env. In entity type currently its supported only for
    // Pipelines or Template
    if (isEmpty(env) || validateOnPremOrCommunityEdition() || (entityType != PIPELINES && entityType != TEMPLATE)) {
      return false;
    }
    return true;
  }

  private boolean validateOnPremOrCommunityEdition() {
    // On Prem Env check.
    if ("ONPREM".equals(deployMode) || "KUBERNETES_ONPREM".equals(deployMode)) {
      return true;
    }

    // Validating if current deployment is of community edition
    if (DeployVariant.isCommunity(System.getenv().get(DEPLOY_VERSION))) {
      return true;
    }

    return false;
  }

  public ResponseDTO<Boolean> invalidateYamlSchemaCache() {
    pmsYamlSchemaService.invalidateAllCache();
    return ResponseDTO.newResponse(true);
  }

  public ResponseDTO<io.harness.pms.yaml.YamlSchemaResponse> getIndividualYamlSchema(@NotNull String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String yamlGroup, EntityType stepEntityType, Scope scope) {
    // TODO(Brijesh): write logic to handle empty schema when ff or feature restriction is off.
    JsonNode schema = pmsYamlSchemaService.getIndividualYamlSchema(
        accountIdentifier, orgIdentifier, projectIdentifier, scope, stepEntityType, yamlGroup);
    return ResponseDTO.newResponse(
        YamlSchemaResponse.builder().schema(schema).schemaErrorResponse(SchemaErrorResponse.builder().build()).build());
  }

  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  public ResponseDTO<PipelineConfig> dummyApiForSwaggerSchemaCheck() {
    log.info("Get pipeline");
    return ResponseDTO.newResponse(PipelineConfig.builder().build());
  }
}
