/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.verification;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.plancreator.steps.AbstractStepNode;

import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.State;
import software.wings.sm.states.SplunkV2State;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class SplunkV2StepMapperImpl extends VerificationBaseService {
  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    SplunkV2State state = new SplunkV2State(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  public List<CgEntityId> getReferencedEntities(
      String accountId, Workflow workflow, GraphNode graphNode, Map<String, String> stepIdToServiceIdMap) {
    List<CgEntityId> referencedEntities = new ArrayList<>();
    referencedEntities.addAll(super.getReferencedEntities(accountId, workflow, graphNode, stepIdToServiceIdMap));
    SplunkV2State state = (SplunkV2State) getState(graphNode);
    if (StringUtils.isNotBlank(state.getAnalysisServerConfigId())) {
      referencedEntities.add(
          CgEntityId.builder().id(state.getAnalysisServerConfigId()).type(NGMigrationEntityType.CONNECTOR).build());
    }
    return referencedEntities;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    return getVerifySpec(migrationContext, context, graphNode);
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return true;
  }
}
