/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.individualschema;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ng.core.template.TemplateEntityConstants.ARTIFACT_SOURCE_TEMPLATE_V1_TITLE;
import static io.harness.ng.core.template.TemplateEntityConstants.CUSTOM_DEPLOYMENT_TEMPLATE_V1_TITLE;
import static io.harness.ng.core.template.TemplateEntityConstants.DEFAULT_TEMPLATE_V1_TITLE;
import static io.harness.ng.core.template.TemplateEntityConstants.PIPELINE_TEMPLATE_V1_TITLE;
import static io.harness.ng.core.template.TemplateEntityConstants.SECRET_MANAGER_TEMPLATE_V1_TITLE;
import static io.harness.ng.core.template.TemplateEntityConstants.STAGE_TEMPLATE_V1_TITLE;
import static io.harness.ng.core.template.TemplateEntityConstants.STEPGROUP_TEMPLATE_V1_TITLE;
import static io.harness.ng.core.template.TemplateEntityConstants.STEP_TEMPLATE_V1_TITLE;
import static io.harness.yaml.schema.beans.SchemaConstants.ALL_OF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.SPEC_NODE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.jackson.JsonNodeUtils;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.template.utils.TemplateSchemaFetcher;
import io.harness.yaml.schema.beans.SchemaConstants;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Iterator;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class TemplateSchemaParserV1 extends BaseTemplateSchemaParser {
  @Inject TemplateSchemaFetcher templateSchemaFetcher;
  static final String TEMPLATE_DEFINITION_PATH = "definitions/template";
  static final String ONE_OF_REF_IN_TEMPLATE = "%s/allOf/0/then/properties/spec/oneOf";
  static final String SPEC_PATH = "%s/allOf/0/then/properties/spec";

  public static final String TEMPLATE_V1 = "v1";

  @Override
  void init() {
    JsonNode rootSchemaNode = templateSchemaFetcher.getStaticYamlSchema(TEMPLATE_V1);
    rootSchemaJsonNode = rootSchemaNode;
    // Populating the template schema in the nodeToResolvedSchemaMap with rootSchemaNode because we already have the
    // complete template schema so no need to calculate.
    nodeToResolvedSchemaMap.put(YAMLFieldNameConstants.TEMPLATE, (ObjectNode) rootSchemaNode);
    traverseNodeAndExtractAllRefsRecursively(rootSchemaJsonNode, "/#");
    findRootNodesAndInitialiseSchema();
  }

  private void findRootNodesAndInitialiseSchema() {
    JsonNode tamplatesJsonNode = JsonPipelineUtils.getJsonNodeByPath(rootSchemaJsonNode, TEMPLATE_DEFINITION_PATH);
    for (Iterator<Map.Entry<String, JsonNode>> it = tamplatesJsonNode.fields(); it.hasNext();) {
      Map.Entry<String, JsonNode> entryIterator = it.next();
      // groupObjectNode is the step/stage/stepGroup template schema node.
      ObjectNode groupObjectNode = (ObjectNode) entryIterator.getValue();
      // Initialising the group individual schema.
      updateAndInitIndividualSchema(groupObjectNode, entryIterator.getKey(), null);

      try {
        JsonNode oneOfArrayNode = JsonPipelineUtils.getJsonNodeByPath(
            groupObjectNode, String.format(ONE_OF_REF_IN_TEMPLATE, groupObjectNode.fieldNames().next()));
        if (oneOfArrayNode == null || !oneOfArrayNode.isArray()) {
          continue;
        }
        for (JsonNode ofOfElement : oneOfArrayNode) {
          JsonNode refNode = ofOfElement.get(SchemaConstants.REF_NODE);
          if (refNode != null && refNode.isTextual()) {
            createRootNodeForRefAndInitialiseSchema(groupObjectNode, refNode, entryIterator.getKey());
          }
        }
      } catch (Exception e) {
        log.error(
            String.format("Exception occurred in initialising individual schema for node %s", entryIterator.getKey()),
            e);
      }
    }
  }

  private void createRootNodeForRefAndInitialiseSchema(JsonNode schemaNode, JsonNode refNode, String nodeGroup) {
    // This individualSchemaNode will have all common properties across all child nodeTypes. And only the spec
    // property differs for different children. So we will generate the individual templates schema after modifying this
    // node.
    ObjectNode individualSchemaNode = schemaNode.deepCopy();
    String specPath = getSpecPath(nodeGroup);
    ObjectNode specNode = (ObjectNode) JsonPipelineUtils.getJsonNodeByPath(individualSchemaNode, specPath);
    // Removing the oneOf node. Because this oneOf contains list of all children(Step/Stages) and for any
    // individual child, we will set the spec to that specific ref.
    JsonNodeUtils.deletePropertiesInJsonNode(specNode, SchemaConstants.ONE_OF_NODE);
    specNode.set(SchemaConstants.REF_NODE, refNode);

    String nodeType = getTypeFromObjectNode((ObjectNode) getReferredJsonNodeByPath(refNode.asText()));
    updateAndInitIndividualSchema(individualSchemaNode, nodeGroup, nodeType);
  }

  // Create a new node with version,kind,required and spec value as refNode and init the individual schema
  private void updateAndInitIndividualSchema(ObjectNode individualSchemaNode, String nodeGroup, String nodeType) {
    ObjectNode finalSchema = rootSchemaJsonNode.deepCopy();
    finalSchema.remove(ALL_OF_NODE);
    finalSchema.remove(DEFINITIONS_NODE);
    ObjectNode propertiesNode = rootSchemaJsonNode.get("properties").deepCopy();
    propertiesNode.set(SPEC_NODE, individualSchemaNode.get(individualSchemaNode.fieldNames().next()));
    finalSchema.set("properties", propertiesNode);
    initIndividualSchema(finalSchema, TemplateSchemaMetadata.builder().nodeGroup(nodeGroup).nodeType(nodeType).build());
  }

  private String getSpecPath(String nodeGroup) {
    // using if-else over switch-case as we need to pass constant value in case.
    if (TemplateEntityType.STEP_TEMPLATE.getYamlTypeV1().equals(nodeGroup)) {
      return String.format(SPEC_PATH, STEP_TEMPLATE_V1_TITLE);
    } else if (TemplateEntityType.STAGE_TEMPLATE.getYamlTypeV1().equals(nodeGroup)) {
      return String.format(SPEC_PATH, STAGE_TEMPLATE_V1_TITLE);
    } else if (TemplateEntityType.ARTIFACT_SOURCE_TEMPLATE.getYamlTypeV1().equals(nodeGroup)) {
      return String.format(SPEC_PATH, ARTIFACT_SOURCE_TEMPLATE_V1_TITLE);
    } else if (TemplateEntityType.STEPGROUP_TEMPLATE.getYamlTypeV1().equals(nodeGroup)) {
      return String.format(SPEC_PATH, STEPGROUP_TEMPLATE_V1_TITLE);
    } else if (TemplateEntityType.PIPELINE_TEMPLATE.getYamlTypeV1().equals(nodeGroup)) {
      return String.format(SPEC_PATH, PIPELINE_TEMPLATE_V1_TITLE);
    } else if (TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE.getYamlTypeV1().equals(nodeGroup)) {
      return String.format(SPEC_PATH, CUSTOM_DEPLOYMENT_TEMPLATE_V1_TITLE);
    } else if (TemplateEntityType.SECRET_MANAGER_TEMPLATE.getYamlTypeV1().equals(nodeGroup)) {
      return String.format(SPEC_PATH, SECRET_MANAGER_TEMPLATE_V1_TITLE);
    } else {
      return String.format(SPEC_PATH, DEFAULT_TEMPLATE_V1_TITLE);
    }
  }
}