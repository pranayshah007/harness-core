/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.exception.InvalidRequestException;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.WorkflowStepSupportStatus;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.sm.State;

import java.util.Map;

public class UnsupportedStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(GraphNode stepYaml) {
    throw new InvalidRequestException(
        String.format("Unsupported step - %s of type %s", stepYaml.getName(), stepYaml.getType()));
  }

  @Override
  public State getState(GraphNode stepYaml) {
    return null;
  }

  @Override
  public AbstractStepNode getSpec(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, GraphNode graphNode) {
    throw new InvalidRequestException(
        String.format("Unsupported step - %s of type %s", graphNode.getName(), graphNode.getType()));
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }

  @Override
  public WorkflowStepSupportStatus stepSupportStatus(GraphNode graphNode) {
    return WorkflowStepSupportStatus.UNSUPPORTED;
  }
}
