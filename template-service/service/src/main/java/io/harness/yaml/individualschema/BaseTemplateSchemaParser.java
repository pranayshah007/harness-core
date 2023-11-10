/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.individualschema;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGES;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEPS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Singleton;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public abstract class BaseTemplateSchemaParser extends AbstractStaticSchemaParser {
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
    String[] fqnParts = inputFieldMetadata.getFqnFromParentNode().split("\\.");
    PipelineSchemaRequest schemaRequest =
        PipelineSchemaRequest.builder()
            .individualSchemaMetadata(PipelineSchemaMetadata.builder()
                                          .nodeGroup(getFormattedNodeGroup(fqnParts[0]))
                                          .nodeType(inputFieldMetadata.getParentNodeType())
                                          .build())
            .build();

    return super.getFieldNode(inputFieldMetadata.getFieldName(), schemaRequest);
  }

  private String getFormattedNodeGroup(String nodeGroup) {
    if (STEPS.equals(nodeGroup)) {
      return STEP;
    }
    if (STAGES.equals(nodeGroup)) {
      return STAGE;
    }
    return nodeGroup;
  }
}