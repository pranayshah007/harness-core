/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml.individualschema;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.yaml.schema.beans.SchemaConstants;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Singleton;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class PipelineSchemaParserV0 extends AbstractStaticSchemaParser {
  static final String PIPELINE_DEFINITION_PATH = "#/definitions/pipeline/pipeline";

  static final String STEP_NODE_REF_SUFFIX_UNDER_EXECUTIONS_WRAPPER_CONFIG = "ExecutionWrapperConfig/step/oneOf";
  static final String STAGES_NODE_ONE_OF_PATH = "#/definitions/pipeline/stages/stages/stage/oneOf";

  @Override
  void init(JsonNode rootSchemaNode) {
    // Populating the pipeline schema in the nodeToResolvedSchemaMap with rootSchemaNode because we already have the
    // complete pipeline schema so no need to calculate.
    nodeToResolvedSchemaMap.put(YAMLFieldNameConstants.PIPELINE, (ObjectNode) rootSchemaNode);
    parseAndIngestSchema();
  }

  public void parseAndIngestSchema() {
    traverseNodeAndExtractAllRefsRecursively(
        JsonPipelineUtils.getJsonNodeByPath(rootSchemaJsonNode, PIPELINE_DEFINITION_PATH), PIPELINE_DEFINITION_PATH);

    // Initialise the resolved individual schema for all rootNodes. These can be stage, step, stepGroup or any other
    // nodes. `checkIfRootNodeAndAddIntoFqnToNodeMap` method has the rules to check if a node is rootNode or not.
    fqnToNodeMap.forEach((fqn, node) -> {
      if (node.isRootNode()) {
        initIndividualSchema(node.getObjectNode(),
            PipelineSchemaRequest.builder()
                .nodeGroup(node.getNodeGroup())
                .nodeType(node.getNodeType())
                .nodeGroupDifferentiator(node.getNodeGroupDifferentiator())
                .build());
      }
    });
  }

  @Override
  String getApplicableNodePath(String parentNode, String nodeType) {
    return parentNode + (EmptyPredicate.isNotEmpty(nodeType) ? "/" : "") + nodeType;
  }

  @Override
  void checkIfRootNodeAndAddIntoFqnToNodeMap(String currentFqn, String childNodeRefValue, ObjectNode objectNode) {
    ObjectNodeWithMetadata stepNode = getRootStepNode(currentFqn, objectNode);
    if (stepNode != null) {
      fqnToNodeMap.put(childNodeRefValue, stepNode);
      return;
    }
    ObjectNodeWithMetadata stageNode = getRootStageNode(currentFqn, objectNode);
    if (stageNode != null) {
      fqnToNodeMap.put(childNodeRefValue, stageNode);
      return;
    }
    ObjectNodeWithMetadata stepGroupNode = getRootStageGroupNode(currentFqn, objectNode);
    if (stepGroupNode != null) {
      fqnToNodeMap.put(childNodeRefValue, stepGroupNode);
      return;
    }
  }

  private ObjectNodeWithMetadata getRootStageNode(String currentFqn, ObjectNode objectNode) {
    if (currentFqn.endsWith(STAGES_NODE_ONE_OF_PATH)) {
      String stageType = getTypeFromObjectNode(objectNode);
      if (EmptyPredicate.isNotEmpty(stageType)) {
        return ObjectNodeWithMetadata.builder()
            .isRootNode(true)
            .nodeGroup(StepCategory.STAGE.name().toLowerCase())
            .nodeType(stageType)
            .objectNode(objectNode)
            .build();
      }
    }
    return null;
  }

  private ObjectNodeWithMetadata getRootStepNode(String currentFqn, ObjectNode objectNode) {
    if (currentFqn.endsWith(STEP_NODE_REF_SUFFIX_UNDER_EXECUTIONS_WRAPPER_CONFIG)) {
      String stepType = getTypeFromObjectNode(objectNode);
      if (EmptyPredicate.isNotEmpty(stepType)) {
        return ObjectNodeWithMetadata.builder()
            .isRootNode(true)
            .nodeGroup(StepCategory.STEP.name().toLowerCase())
            .nodeType(stepType)
            .objectNode(objectNode)
            .build();
      }
    }
    return null;
  }

  private ObjectNodeWithMetadata getRootStageGroupNode(String currentFqn, ObjectNode objectNode) {
    String stepGroupNodeName = StepGroupElementConfig.class.getSimpleName();
    if (stepGroupNodeName.equals(JsonPipelineUtils.getText(objectNode, "title"))) {
      String[] fqnComponents = currentFqn.split("/");
      if (fqnComponents.length > 5) {
        // The currentFqn for the stepGroup node follows the pattern
        // "#/definitions/pipeline/stages/stageName/ExecutionWrapperConfig/stepGroup" so 4 index will be stage name.
        String stageName = fqnComponents[4];
        return ObjectNodeWithMetadata.builder()
            .isRootNode(true)
            .nodeGroup(StepCategory.STEP_GROUP.name().toLowerCase())
            .nodeGroupDifferentiator(stageName)
            .objectNode(objectNode)
            .build();
      }
    }
    return null;
  }

  private String getTypeFromObjectNode(ObjectNode objectNode) {
    if (JsonPipelineUtils.isPresent(objectNode, SchemaConstants.PROPERTIES_NODE)
        && objectNode.get(SchemaConstants.PROPERTIES_NODE).get(SchemaConstants.TYPE_NODE) != null) {
      JsonNode typeEnumArray =
          objectNode.get(SchemaConstants.PROPERTIES_NODE).get(SchemaConstants.TYPE_NODE).get(SchemaConstants.ENUM_NODE);
      if (typeEnumArray != null && typeEnumArray.size() > 0) {
        return typeEnumArray.get(0).asText();
      }
    }
    return null;
  }

  @Override
  IndividualSchemaGenContext getIndividualSchemaGenContext() {
    return IndividualSchemaGenContext.builder()
        .rootSchemaNode(rootSchemaJsonNode)
        .resolvedFqnSet(new HashSet<>())
        .build();
  }

  // Generate unique key for the given nodeType/group/stageName. This key will be used to store the calculated results
  // in the map and for lookup.
  @Override
  String generateSchemaKey(IndividualSchemaRequest individualSchemaMetadata) {
    PipelineSchemaRequest schemaMetadata = (PipelineSchemaRequest) individualSchemaMetadata;
    if (StepCategory.STEP_GROUP.name().equalsIgnoreCase(schemaMetadata.getNodeGroup())) {
      return schemaMetadata.getNodeGroupDifferentiator() + "/" + schemaMetadata.getNodeGroup();
    } else if (StepCategory.PIPELINE.name().equalsIgnoreCase(schemaMetadata.getNodeGroup())) {
      return YAMLFieldNameConstants.PIPELINE;
    } else {
      return schemaMetadata.getNodeGroup() + "/" + schemaMetadata.getNodeType();
    }
  }
}