/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml.individualschema;

import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PipelineSchemaMetadata extends IndividualSchemaMetadata {
  private String nodeGroupDifferentiator; // It can be any differentiator for the nodeGroup/nodeType when there are
                                          // multiple nodeGroup/nodeType present under different paths. Example
                                          // stepGroupElementConfig would be different in cd and ci sages.
  private String nodeGroup; // Group of the node. eg; Step, Stage, stepGroup
  private String nodeType; // type of the node eg: Http, Custom, Deployment

  // Generate unique key for the given nodeType/group/nodeGroupDifferentiator. Here the nodeGroupDifferentiator will be
  // used to differentiate between nodes when they have same nodeGroup and nodeType ie: stageName when the nodeGroup is
  // `stepGroup` . This key will be used to store the calculated results in the map and for lookup.
  @Override
  String generateSchemaKey() {
    if (StepCategory.STEP_GROUP.name().equalsIgnoreCase(nodeGroup)) {
      return nodeGroupDifferentiator + "/" + nodeGroup;
    } else if (StepCategory.PIPELINE.name().equalsIgnoreCase(nodeGroup)) {
      return YAMLFieldNameConstants.PIPELINE;
    } else {
      return nodeGroup + "/" + nodeType;
    }
  }
}
