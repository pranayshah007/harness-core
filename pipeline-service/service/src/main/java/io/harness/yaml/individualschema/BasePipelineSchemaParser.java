/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.individualschema;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.yaml.schema.beans.SchemaConstants;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public abstract class BasePipelineSchemaParser extends AbstractStaticSchemaParser {
  @Inject SchemaFetcher schemaFetcher;

  abstract void parseAndIngestSchema();
  abstract String getYamlVersion();

  abstract ObjectNodeWithMetadata getRootStageNode(String currentFqn, ObjectNode objectNode);
  abstract ObjectNodeWithMetadata getRootStepNode(String currentFqn, ObjectNode objectNode);

  @Override
  void init() {
    JsonNode rootSchemaNode = schemaFetcher.fetchPipelineStaticYamlSchema(getYamlVersion());
    rootSchemaJsonNode = rootSchemaNode;
    // Populating the pipeline schema in the nodeToResolvedSchemaMap with rootSchemaNode because we already have the
    // complete pipeline schema so no need to calculate.
    nodeToResolvedSchemaMap.put(YAMLFieldNameConstants.PIPELINE, (ObjectNode) rootSchemaNode);
    parseAndIngestSchema();
  }

  @Override
  public JsonNode getFieldNode(InputFieldMetadata inputFieldMetadata) {
    String[] fqnParts = inputFieldMetadata.getFqnFromParentNode().split("\\.");
    PipelineSchemaRequest pipelineSchemaRequest =
        PipelineSchemaRequest.builder()
            .individualSchemaMetadata(PipelineSchemaMetadata.builder()
                                          .nodeGroup(getFormattedNodeGroup(fqnParts[0]))
                                          .nodeType(inputFieldMetadata.getParentNodeType())
                                          .build())
            .build();

    return super.getFieldNode(inputFieldMetadata.getFieldName(), pipelineSchemaRequest);
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
    ObjectNodeWithMetadata stepGroupNode = getRootStepGroupNode(currentFqn, objectNode);
    if (stepGroupNode != null) {
      fqnToNodeMap.put(childNodeRefValue, stepGroupNode);
      return;
    }
    ObjectNodeWithMetadata strategyNode = getRootStrategyNode(currentFqn, objectNode);
    if (strategyNode != null) {
      fqnToNodeMap.put(childNodeRefValue, strategyNode);
      return;
    }
  }

  private ObjectNodeWithMetadata getRootStrategyNode(String currentFqn, ObjectNode objectNode) {
    String StrategyNodeName = StrategyConfig.class.getSimpleName();
    if (StrategyNodeName.equals(JsonPipelineUtils.getText(objectNode, "title"))) {
      return ObjectNodeWithMetadata.builder()
          .isRootNode(true)
          .nodeGroup(StepCategory.STRATEGY.name().toLowerCase())
          .nodeType(StepCategory.STRATEGY.name().toLowerCase())
          .objectNode(objectNode)
          .build();
    }
    return null;
  }

  private ObjectNodeWithMetadata getRootStepGroupNode(String currentFqn, ObjectNode objectNode) {
    String stepGroupNodeName = StepGroupElementConfig.class.getSimpleName();
    if (stepGroupNodeName.equals(JsonPipelineUtils.getText(objectNode, SchemaConstants.TITLE))) {
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

  @Override
  IndividualSchemaGenContext getIndividualSchemaGenContext() {
    return IndividualSchemaGenContext.builder()
        .rootSchemaNode(rootSchemaJsonNode)
        .resolvedFqnSet(new HashSet<>())
        .build();
  }

  private String getFormattedNodeGroup(String nodeGroup) {
    if ("steps".equals(nodeGroup)) {
      return "step";
    }
    if ("stages".equals(nodeGroup)) {
      return "stage";
    }
    return nodeGroup;
  }
}
