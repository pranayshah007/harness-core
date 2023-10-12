/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.individualschema;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.template.utils.TemplateSchemaFetcher;
import io.harness.yaml.schema.beans.SchemaConstants;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class TemplateSchemaParserV0 extends AbstractStaticSchemaParser {
  @Inject TemplateSchemaFetcher templateSchemaFetcher;
  static final String TEMPLATE_DEFINITION_PATH = "definitions/template";
  static final String ONE_OF_REF_IN_TEMPLATE = "template/properties/spec/oneOf";

  static final String TEMPLATE_VO = "v0";

  @Override
  void init() {
    JsonNode rootSchemaNode = templateSchemaFetcher.getStaticYamlSchema(TEMPLATE_VO);
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
      initIndividualSchema(groupObjectNode, TemplateSchemaMetadata.builder().nodeGroup(entryIterator.getKey()).build());

      try {
        // This oneOfArrayNode has all the individual nodeType refs for any nodeGroup.
        JsonNode oneOfArrayNode = JsonPipelineUtils.getJsonNodeByPath(groupObjectNode, ONE_OF_REF_IN_TEMPLATE);
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
    moveTemplateNodeInsideProperties(individualSchemaNode);

    ObjectNode specNode =
        (ObjectNode) JsonPipelineUtils.getJsonNodeByPath(individualSchemaNode, "properties/template/properties/spec");
    // Removing the oneOf node. Because this oneOf contains list of all children(Step/Stages) and for any
    // individual child, we will set the spec to that specific ref.
    JsonNodeUtils.deletePropertiesInJsonNode(specNode, SchemaConstants.ONE_OF_NODE);
    specNode.set(SchemaConstants.REF_NODE, refNode);

    String nodeType = getTypeFromObjectNode((ObjectNode) getReferredJsonNodeByPath(refNode.asText()));
    initIndividualSchema(
        individualSchemaNode, TemplateSchemaMetadata.builder().nodeGroup(nodeGroup).nodeType(nodeType).build());
  }

  // Moved template field inside the properties. Example: {\"template\":\"val\"} ->
  // {\"properties\":{\"template\":\"val\"}}
  private void moveTemplateNodeInsideProperties(ObjectNode individualSchemaNode) {
    individualSchemaNode.set(SchemaConstants.TYPE_NODE, TextNode.valueOf(SchemaConstants.OBJECT_TYPE_NODE));
    // Creating an empty properties node.
    individualSchemaNode.set(SchemaConstants.PROPERTIES_NODE, JsonPipelineUtils.readTree("{}"));
    // Setting the template field inside the properties node.
    ((ObjectNode) individualSchemaNode.get(SchemaConstants.PROPERTIES_NODE))
        .set(SchemaConstants.TEMPLATE_NODE, individualSchemaNode.get(SchemaConstants.TEMPLATE_NODE));
    // Deleting the template field from the outside.
    JsonNodeUtils.deletePropertiesInJsonNode(individualSchemaNode, SchemaConstants.TEMPLATE_NODE);
  }
  @Override
  void checkIfRootNodeAndAddIntoFqnToNodeMap(String currentFqn, String childNodeRefValue, ObjectNode objectNode) {}

  @Override
  IndividualSchemaGenContext getIndividualSchemaGenContext() {
    return IndividualSchemaGenContext.builder()
        .rootSchemaNode(rootSchemaJsonNode)
        .resolvedFqnSet(new HashSet<>())
        .build();
  }

  @Override
  public JsonNode getFieldNode(InputFieldMetadata inputFieldMetadata) {
    // TODO
    return null;
  }
}