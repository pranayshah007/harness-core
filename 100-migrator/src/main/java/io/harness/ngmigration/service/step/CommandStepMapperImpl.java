/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.WorkflowStepSupportStatus;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.steps.template.TemplateStepNode;

import software.wings.beans.GraphNode;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.State;
import software.wings.sm.states.CommandState;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class CommandStepMapperImpl implements StepMapper {
  @Override
  public WorkflowStepSupportStatus stepSupportStatus(GraphNode graphNode) {
    String templateId = graphNode.getTemplateUuid();
    if (StringUtils.isBlank(templateId)) {
      return WorkflowStepSupportStatus.UNSUPPORTED;
    }
    return WorkflowStepSupportStatus.SUPPORTED;
  }

  @Override
  public List<CgEntityId> getReferencedEntities(GraphNode graphNode) {
    String templateId = graphNode.getTemplateUuid();
    if (StringUtils.isNotBlank(templateId)) {
      return Collections.singletonList(
          CgEntityId.builder().id(templateId).type(NGMigrationEntityType.TEMPLATE).build());
    }
    return Collections.emptyList();
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.COMMAND;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    CommandState state = new CommandState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public TemplateStepNode getTemplateSpec(Map<CgEntityId, NGYamlFile> migratedEntities, GraphNode graphNode) {
    return defaultTemplateSpecMapper(migratedEntities, graphNode);
  }

  @Override
  public AbstractStepNode getSpec(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities, GraphNode graphNode) {
    throw new InvalidRequestException("Only templatized command steps are currently supported");
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    String templateId1 = stepYaml1.getTemplateUuid();
    String templateId2 = stepYaml2.getTemplateUuid();
    return StringUtils.isNoneBlank(templateId2, templateId1) && StringUtils.equals(templateId1, templateId2);
  }
}
