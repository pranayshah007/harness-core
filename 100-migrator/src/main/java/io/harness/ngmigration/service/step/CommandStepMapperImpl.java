/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ngmigration.utils.NGMigrationConstants.RUNTIME_INPUT;
import static io.harness.ngmigration.utils.NGMigrationConstants.SERVICE_COMMAND_TEMPLATE_SEPARATOR;
import static io.harness.ngmigration.utils.NGMigrationConstants.UNKNOWN_SERVICE;

import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.workflow.WorkflowHandler;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.steps.template.TemplateStepNode;

import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.State;
import software.wings.sm.states.CommandState;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CommandStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.SSH;
  }

  @Override
  public List<CgEntityId> getReferencedEntities(
      String accountId, Workflow workflow, GraphNode graphNode, Map<String, String> stepIdToServiceIdMap) {
    String templateId = graphNode.getTemplateUuid();
    if (StringUtils.isNotBlank(templateId)) {
      return Collections.singletonList(
          CgEntityId.builder().id(templateId).type(NGMigrationEntityType.TEMPLATE).build());
    } else {
      String commandName = (String) graphNode.getProperties().get("commandName");
      String serviceId = stepIdToServiceIdMap.getOrDefault(graphNode.getId(), UNKNOWN_SERVICE);
      return Collections.singletonList(CgEntityId.builder()
                                           .id(serviceId + SERVICE_COMMAND_TEMPLATE_SEPARATOR + commandName)
                                           .type(NGMigrationEntityType.SERVICE_COMMAND_TEMPLATE)
                                           .build());
    }
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.COMMAND;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    CommandState state = new CommandState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public TemplateStepNode getTemplateSpec(MigrationContext migrationContext, WorkflowMigrationContext context,
      WorkflowPhase phase, PhaseStep phaseStep, GraphNode graphNode, String skipCondition) {
    String templateId = graphNode.getTemplateUuid();
    if (isEmpty(templateId)) {
      WorkflowHandler workflowHandler = workflowHandlerFactory.getWorkflowHandler(context.getWorkflow());
      Map<String, String> stepIdToServiceIdMap = workflowHandler.getStepIdToServiceIdMap(context.getWorkflow());
      String commandName = (String) graphNode.getProperties().get("commandName");
      String serviceId = stepIdToServiceIdMap.getOrDefault(graphNode.getId(), UNKNOWN_SERVICE);
      NGYamlFile template =
          context.getMigratedEntities().get(CgEntityId.builder()
                                                .id(serviceId + SERVICE_COMMAND_TEMPLATE_SEPARATOR + commandName)
                                                .type(NGMigrationEntityType.SERVICE_COMMAND_TEMPLATE)
                                                .build());
      return getTemplateStepNode(migrationContext, context, phase, phaseStep, graphNode, template, skipCondition);
    } else {
      return defaultTemplateSpecMapper(migrationContext, context, phase, phaseStep, graphNode, skipCondition);
    }
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    throw new InvalidRequestException("Should not reach here");
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    String templateId1 = stepYaml1.getTemplateUuid();
    String templateId2 = stepYaml2.getTemplateUuid();
    return StringUtils.isNoneBlank(templateId2, templateId1) && StringUtils.equals(templateId1, templateId2);
  }

  @Override
  public boolean loopingSupported() {
    return true;
  }

  @Override
  public void overrideTemplateInputs(MigrationContext migrationContext, WorkflowMigrationContext context,
      WorkflowPhase phase, GraphNode graphNode, NGYamlFile templateFile, JsonNode templateInputs) {
    CommandState state = new CommandState(graphNode.getName());
    boolean shouldRunOnDelegate = state.isExecuteOnDelegate();
    JsonNode onDelegate = templateInputs.at("/spec/onDelegate");
    if (onDelegate instanceof TextNode) {
      if (RUNTIME_INPUT.equals(onDelegate.asText())) {
        ((ObjectNode) templateInputs.get("spec")).putPOJO("onDelegate", shouldRunOnDelegate);
      }
    }
    // Fix delegate selectors in the workflow
    overrideTemplateDelegateSelectorInputs(templateInputs, state.getDelegateSelectors());
  }
}
