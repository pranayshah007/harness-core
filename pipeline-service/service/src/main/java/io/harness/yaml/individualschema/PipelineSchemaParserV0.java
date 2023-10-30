/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.individualschema;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class PipelineSchemaParserV0 extends BasePipelineSchemaParser {
  static final String PIPELINE_DEFINITION_PATH = "#/definitions/pipeline/pipeline";
  static final String PIPELINE_V0 = "v0";

  void parseAndIngestSchema() {
    traverseNodeAndExtractAllRefsRecursively(
        JsonPipelineUtils.getJsonNodeByPath(rootSchemaJsonNode, PIPELINE_DEFINITION_PATH), PIPELINE_DEFINITION_PATH);

    // Initialise the resolved individual schema for all rootNodes. These can be stage, step, stepGroup or any other
    // nodes. `checkIfRootNodeAndAddIntoFqnToNodeMap` method has the rules to check if a node is rootNode or not.
    fqnToNodeMap.forEach((fqn, node) -> {
      if (node.isRootNode()) {
        initIndividualSchema(node.getObjectNode(),
            PipelineSchemaMetadata.builder()
                .nodeGroup(node.getNodeGroup())
                .nodeType(node.getNodeType())
                .nodeGroupDifferentiator(node.getNodeGroupDifferentiator())
                .build());
      }
    });
  }

  @Override
  String getYamlVersion() {
    return PIPELINE_V0;
  }
}