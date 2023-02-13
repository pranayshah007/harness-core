/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.beans.WorkflowStepSupportStatus;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.service.MigrationTemplateUtils;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.template.TemplateStepNode;
import io.harness.template.yaml.TemplateLinkConfig;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.WorkflowPhase;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.State;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public abstract class StepMapper {
  @Inject MigrationTemplateUtils migrationTemplateUtils;

  public List<CgEntityId> getReferencedEntities(GraphNode graphNode) {
    return Collections.emptyList();
  }

  public abstract String getStepType(GraphNode stepYaml);

  public abstract State getState(GraphNode stepYaml);

  public List<StepExpressionFunctor> getExpressionFunctor(
      WorkflowMigrationContext context, WorkflowPhase phase, PhaseStep phaseStep, GraphNode graphNode) {
    return Collections.emptyList();
  }

  public abstract AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode);

  public Set<String> getExpressions(GraphNode graphNode) {
    return Collections.emptySet();
  }

  public TemplateStepNode getTemplateSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    return null;
  }

  public TemplateStepNode defaultTemplateSpecMapper(WorkflowMigrationContext context, GraphNode graphNode) {
    String templateId = graphNode.getTemplateUuid();
    if (StringUtils.isBlank(templateId)) {
      return null;
    }
    NGYamlFile template = context.getMigratedEntities().get(
        CgEntityId.builder().id(templateId).type(NGMigrationEntityType.TEMPLATE).build());
    if (template == null) {
      log.warn("Found a step with template ID but not found in migrated context. Workflow ID - {} & Step - {}",
          context.getWorkflow().getUuid(), graphNode.getName());
      throw new InvalidRequestException(
          String.format("The template used for step %s was not migrated", graphNode.getName()));
    }
    TemplateLinkConfig templateLinkConfig = new TemplateLinkConfig();
    templateLinkConfig.setTemplateRef(MigratorUtility.getIdentifierWithScope(template.getNgEntityDetail()));
    templateLinkConfig.setTemplateInputs(
        migrationTemplateUtils.getTemplateInputs(template, context.getWorkflow().getAccountId()));

    TemplateStepNode templateStepNode = new TemplateStepNode();
    templateStepNode.setIdentifier(MigratorUtility.generateIdentifier(graphNode.getName()));
    templateStepNode.setName(MigratorUtility.generateName(graphNode.getName()));
    templateStepNode.setDescription(getDescription(graphNode));
    templateStepNode.setTemplate(templateLinkConfig);
    return templateStepNode;
  }

  public abstract boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2);

  public ParameterField<Timeout> getTimeout(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);

    String timeoutString = "10m";
    if (properties.containsKey("timeoutMillis") && properties.get("timeoutMillis") != null) {
      long t = Long.parseLong(properties.get("timeoutMillis").toString()) / 1000;
      if (t > 60) {
        timeoutString = (t / 60) + "m";
      } else {
        timeoutString = t + "s";
      }
    }
    return ParameterField.createValueField(Timeout.builder().timeoutString(timeoutString).build());
  }

  public ParameterField<Timeout> getTimeout(State state) {
    return MigratorUtility.getTimeout(state.getTimeoutMillis());
  }

  public String getDescription(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    return properties.getOrDefault("description", "").toString();
  }

  public Map<String, Object> getProperties(GraphNode stepYaml) {
    return CollectionUtils.emptyIfNull(stepYaml.getProperties());
  }

  public void baseSetup(GraphNode graphNode, AbstractStepNode stepNode) {
    stepNode.setIdentifier(MigratorUtility.generateIdentifier(graphNode.getName()));
    stepNode.setName(MigratorUtility.generateName(graphNode.getName()));
    stepNode.setDescription(getDescription(graphNode));
    if (stepNode instanceof PmsAbstractStepNode) {
      PmsAbstractStepNode pmsAbstractStepNode = (PmsAbstractStepNode) stepNode;
      pmsAbstractStepNode.setTimeout(getTimeout(graphNode));
    }
    if (stepNode instanceof CdAbstractStepNode) {
      CdAbstractStepNode cdAbstractStepNode = (CdAbstractStepNode) stepNode;
      cdAbstractStepNode.setTimeout(getTimeout(graphNode));
    }
  }

  public void baseSetup(State state, AbstractStepNode stepNode) {
    stepNode.setIdentifier(MigratorUtility.generateIdentifier(state.getName()));
    stepNode.setName(MigratorUtility.generateName(state.getName()));
    if (stepNode instanceof PmsAbstractStepNode) {
      PmsAbstractStepNode pmsAbstractStepNode = (PmsAbstractStepNode) stepNode;
      pmsAbstractStepNode.setTimeout(getTimeout(state));
    }
    if (stepNode instanceof CdAbstractStepNode) {
      CdAbstractStepNode cdAbstractStepNode = (CdAbstractStepNode) stepNode;
      cdAbstractStepNode.setTimeout(getTimeout(state));
    }
  }

  public abstract WorkflowStepSupportStatus stepSupportStatus(GraphNode graphNode);

  public List<NGYamlFile> getChildNGYamlFiles(MigrationInputDTO inputDTO, GraphNode graphNode, String name) {
    return new ArrayList<>();
  }

  public boolean loopingSupported() {
    return false;
  }
}
