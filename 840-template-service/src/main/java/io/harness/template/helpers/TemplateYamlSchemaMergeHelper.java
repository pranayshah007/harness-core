/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.pms.yaml.YAMLFieldNameConstants.DESCRIPTION;
import static io.harness.pms.yaml.YAMLFieldNameConstants.IDENTIFIER;
import static io.harness.pms.yaml.YAMLFieldNameConstants.NAME;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ORG_IDENTIFIER;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PROJECT_IDENTIFIER;
import static io.harness.pms.yaml.YAMLFieldNameConstants.TAGS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.TEMPLATE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.TYPE;

import io.harness.EntityType;
import io.harness.account.AccountClient;
import io.harness.beans.FeatureName;
import io.harness.jackson.JsonNodeUtils;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.remote.client.CGRestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TemplateYamlSchemaMergeHelper {
  public static void mergeYamlSchema(
      JsonNode templateSchema, JsonNode specSchema, EntityType entityType, TemplateEntityType templateEntityType) {
    JsonNode nGTemplateInfoConfig = templateSchema.get("definitions").get("NGTemplateInfoConfig");
    Set<String> keys = getKeysToRemoveFromTemplateSpec(templateEntityType);

    // TODO: create constants for these
    if (EntityType.PIPELINES.equals(entityType)) {
      // TODO: remove one of for those once we add them in the ticket CDS-.
      String pipelineSpecKey = specSchema.get("properties").get("pipeline").get("$ref").asText();
      JsonNodeUtils.upsertPropertyInObjectNode(
          nGTemplateInfoConfig.get("properties").get("spec"), "$ref", pipelineSpecKey);
      JsonNode refNode = getJsonNodeViaRef(pipelineSpecKey, specSchema);
      JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) refNode.get("properties"), keys);
      JsonNodeUtils.deletePropertiesInArrayNode((ArrayNode) refNode.get("required"), keys);
      JsonNodeUtils.merge(templateSchema.get("definitions"), specSchema.get("definitions"));
    } else {
      ObjectNode definitionSchema = (ObjectNode) templateSchema.get("definitions");
      definitionSchema.putIfAbsent("specNode", definitionSchema.get("JsonNode").deepCopy());
      JsonNodeUtils.upsertPropertyInObjectNode(
          nGTemplateInfoConfig.get("properties").get("spec"), "$ref", "#/definitions/specNode");
      JsonNode specJsonNode = templateSchema.get("definitions").get("specNode");
      if (templateEntityType.equals(TemplateEntityType.STEPGROUP_TEMPLATE)) {
        boolean isNewSchemaPath = false;
        Iterator<Map.Entry<String, JsonNode>> schemaFields = specSchema.fields();
        while (schemaFields.hasNext()) {
          Map.Entry<String, JsonNode> field = schemaFields.next();
          if (field.getKey().equals("$ref")) {
            isNewSchemaPath = true;
            break;
          }
        }
        if (isNewSchemaPath) {
          // specSchema.get("$ref") will be of format "#/definitions/nameSpace/StepGroupElementConfig"
          String ref = specSchema.get("$ref").asText();
          ref = ref.subSequence(2, ref.length()).toString();
          // refSplit will contain ["definitions", "nameSpace", "StepGroupElementConfig"].
          String[] refSplit = ref.split("/");
          JsonNode refNode = specSchema;
          for (String str : refSplit) {
            refNode = refNode.get(str);
          }
          JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) refNode.get("properties"), keys);
          JsonNodeUtils.deletePropertiesInArrayNode((ArrayNode) refNode.get("required"), keys);
        }
      } else {
        JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) specSchema.get("properties"), keys);
        JsonNodeUtils.deletePropertiesInArrayNode((ArrayNode) specSchema.get("required"), keys);
      }
      JsonNodeUtils.merge(specJsonNode, specSchema);
      JsonNodeUtils.merge(templateSchema.get("definitions"), specSchema.get("definitions"));
      JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) specJsonNode, "definitions");
    }
  }

  private static JsonNode getJsonNodeViaRef(String ref, JsonNode rootNode) {
    ref = ref.subSequence(2, ref.length()).toString();
    String[] orderKeys = ref.split("/");
    JsonNode refNode = rootNode;
    for (String str : orderKeys) {
      refNode = refNode.get(str);
    }
    return refNode;
  }

  private static Set<String> getKeysToRemoveFromTemplateSpec(TemplateEntityType templateEntityType) {
    switch (templateEntityType) {
      case STEPGROUP_TEMPLATE:
        return new HashSet<>(Arrays.asList(NAME, IDENTIFIER, DESCRIPTION, ORG_IDENTIFIER, PROJECT_IDENTIFIER, TYPE));
      case STAGE_TEMPLATE:
      case STEP_TEMPLATE:
        return new HashSet<>(Arrays.asList(NAME, IDENTIFIER, DESCRIPTION, ORG_IDENTIFIER, PROJECT_IDENTIFIER));
      case PIPELINE_TEMPLATE:
        return new HashSet<>(
            Arrays.asList(NAME, IDENTIFIER, DESCRIPTION, TYPE, TAGS, ORG_IDENTIFIER, PROJECT_IDENTIFIER, TEMPLATE));
      default:
        return new HashSet<>();
    }
  }

  public static boolean isFeatureFlagEnabled(
      FeatureName featureName, String accountIdentifier, AccountClient accountClient) {
    return CGRestUtils.getResponse(accountClient.isFeatureFlagEnabled(featureName.name(), accountIdentifier));
  }
}
