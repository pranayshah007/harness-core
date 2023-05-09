/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import static io.harness.ngmigration.utils.NGMigrationConstants.RUNTIME_INPUT;

import static software.wings.ngmigration.NGMigrationEntityType.INFRA_PROVISIONER;

import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.service.MigrationTemplateUtils;
import io.harness.ngmigration.service.workflow.WorkflowHandler;
import io.harness.ngmigration.service.workflow.WorkflowHandlerFactory;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngmigration.utils.NGMigrationConstants;
import io.harness.ngmigration.utils.SecretRefUtils;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.template.TemplateStepNode;
import io.harness.template.yaml.TemplateLinkConfig;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.GraphNode;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.State;
import software.wings.sm.states.mixin.SweepingOutputStateMixin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@Slf4j
public abstract class StepMapper {
  @Inject MigrationTemplateUtils migrationTemplateUtils;
  @Inject WorkflowHandlerFactory workflowHandlerFactory;
  @Inject SecretRefUtils secretRefUtils;

  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return null;
  }

  public List<CgEntityId> getReferencedEntities(
      String accountId, Workflow workflow, GraphNode graphNode, Map<String, String> stepIdToServiceIdMap) {
    return secretRefUtils.getSecretRefFromExpressions(accountId, getExpressions(graphNode));
  }

  public abstract String getStepType(GraphNode stepYaml);

  public abstract State getState(GraphNode stepYaml);

  public String getSweepingOutputName(GraphNode graphNode) {
    State state = getState(graphNode);
    if (state instanceof SweepingOutputStateMixin) {
      return ((SweepingOutputStateMixin) state).getSweepingOutputName();
    }
    return null;
  }

  public List<StepExpressionFunctor> getExpressionFunctor(
      WorkflowMigrationContext context, WorkflowPhase phase, PhaseStep phaseStep, GraphNode graphNode) {
    return Collections.emptyList();
  }

  public abstract AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode);

  public Set<String> getExpressions(GraphNode graphNode) {
    Map<String, Object> properties = graphNode.getProperties();
    return MigratorExpressionUtils.getExpressions(properties);
  }

  public TemplateStepNode getTemplateSpec(MigrationContext migrationContext, WorkflowMigrationContext context,
      WorkflowPhase phase, PhaseStep phaseStep, GraphNode graphNode, String skipCondition) {
    return null;
  }

  public TemplateStepNode defaultTemplateSpecMapper(MigrationContext migrationContext, WorkflowMigrationContext context,
      WorkflowPhase phase, PhaseStep phaseStep, GraphNode graphNode, String skipCondition) {
    String templateId = graphNode.getTemplateUuid();
    NGYamlFile template;
    if (StringUtils.isBlank(templateId)) {
      return null;
    } else {
      template = context.getMigratedEntities().get(
          CgEntityId.builder().id(templateId).type(NGMigrationEntityType.TEMPLATE).build());
    }
    return getTemplateStepNode(migrationContext, context, phase, phaseStep, graphNode, template, skipCondition);
  }

  @NotNull
  TemplateStepNode getTemplateStepNode(MigrationContext migrationContext, WorkflowMigrationContext context,
      WorkflowPhase phase, PhaseStep phaseStep, GraphNode graphNode, NGYamlFile template, String skipCondition) {
    if (template == null) {
      log.warn("Found a step with template ID but not found in migrated context. Workflow ID - {} & Step - {}",
          context.getWorkflow().getUuid(), graphNode.getName());
      throw new InvalidRequestException(
          String.format("The template used for step %s was not migrated", graphNode.getName()));
    }

    JsonNode templateInputs = migrationTemplateUtils.getTemplateInputs(
        template.getNgEntityDetail(), migrationContext.getInputDTO().getDestinationAccountIdentifier());
    if (templateInputs != null) {
      baseOverrideTemplateInputs(phaseStep, graphNode, templateInputs, skipCondition);
      overrideTemplateInputs(migrationContext, context, phase, graphNode, template, templateInputs);
    }
    TemplateLinkConfig templateLinkConfig = new TemplateLinkConfig();
    templateLinkConfig.setTemplateRef(MigratorUtility.getIdentifierWithScope(template.getNgEntityDetail()));
    templateLinkConfig.setTemplateInputs(templateInputs);

    TemplateStepNode templateStepNode = new TemplateStepNode();
    templateStepNode.setIdentifier(
        MigratorUtility.generateIdentifier(graphNode.getName(), context.getIdentifierCaseFormat()));
    templateStepNode.setName(MigratorUtility.generateName(graphNode.getName()));
    templateStepNode.setDescription(getDescription(graphNode));
    templateStepNode.setTemplate(templateLinkConfig);
    return templateStepNode;
  }

  void baseOverrideTemplateInputs(PhaseStep phaseStep, GraphNode step, JsonNode templateInputs, String skipCondition) {
    String newSkip = StringUtils.isBlank(skipCondition) ? "true" : skipCondition;
    JsonNode failureStrategies = templateInputs.get("failureStrategies");
    if (failureStrategies != null) {
      List<FailureStrategyConfig> strategies =
          ListUtils.emptyIfNull(WorkflowHandler.getFailureStrategies(phaseStep, step));
      ((ObjectNode) templateInputs).set("failureStrategies", JsonPipelineUtils.asTree(strategies));
    }
    JsonNode condition = templateInputs.get("when");
    if (condition != null) {
      ((ObjectNode) condition).put("condition", newSkip);
    }
  }

  public void overrideTemplateInputs(MigrationContext migrationContext, WorkflowMigrationContext context,
      WorkflowPhase phase, GraphNode graphNode, NGYamlFile templateFile, JsonNode templateInputs) {}

  public abstract boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2);

  public ParameterField<Timeout> getTimeout(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);

    String timeoutString = "10m";
    if (properties.containsKey("timeoutMillis") && properties.get("timeoutMillis") != null) {
      timeoutString = MigratorUtility.toTimeoutString(Long.parseLong(properties.get("timeoutMillis").toString()));
    }
    Object str = properties.getOrDefault("stateTimeoutInMinutes", null);
    if ((str instanceof Integer || str instanceof String) && StringUtils.isNotBlank(String.valueOf(str))) {
      timeoutString = str + "m";
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

  public void baseSetup(GraphNode graphNode, AbstractStepNode stepNode, CaseFormat caseFormat) {
    stepNode.setIdentifier(MigratorUtility.generateIdentifier(graphNode.getName(), caseFormat));
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

  public void baseSetup(State state, AbstractStepNode stepNode, CaseFormat caseFormat) {
    stepNode.setIdentifier(MigratorUtility.generateIdentifier(state.getName(), caseFormat));
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

  public abstract SupportStatus stepSupportStatus(GraphNode graphNode);

  public List<NGYamlFile> getChildNGYamlFiles(MigrationInputDTO inputDTO, GraphNode graphNode, String name) {
    return new ArrayList<>();
  }

  public boolean loopingSupported() {
    return false;
  }

  protected void overrideTemplateDelegateSelectorInputs(JsonNode templateInputs, List<String> delegateSelectors) {
    JsonNode delSelectors = templateInputs.at("/spec/delegateSelectors");
    if (delSelectors instanceof TextNode) {
      String selectors = delSelectors.asText();
      if (RUNTIME_INPUT.equals(selectors)) {
        ((ObjectNode) templateInputs.get("spec"))
            .putPOJO("delegateSelectors", ListUtils.emptyIfNull(delegateSelectors));
      }
    }
  }

  protected ParameterField<String> getConnectorRef(WorkflowMigrationContext context, String connectorId) {
    String connectorRef = NGMigrationConstants.RUNTIME_INPUT;
    if (!context.isTemplatizeStepParams()) {
      connectorRef = MigratorUtility.getIdentifierWithScopeDefaults(context.getMigratedEntities(), connectorId,
          NGMigrationEntityType.CONNECTOR, NGMigrationConstants.RUNTIME_INPUT);
    }
    return ParameterField.createValueField(connectorRef);
  }

  protected ParameterField<String> getConnectorRef(MigrationContext context, String connectorId) {
    String connectorRef = NGMigrationConstants.RUNTIME_INPUT;
    if (!context.isTemplatizeStepParams()) {
      connectorRef = MigratorUtility.getIdentifierWithScopeDefaults(context.getMigratedEntities(), connectorId,
          NGMigrationEntityType.CONNECTOR, NGMigrationConstants.RUNTIME_INPUT);
    }
    return ParameterField.createValueField(connectorRef);
  }

  protected ParameterField<String> getProvisionerIdentifier(MigrationContext context, String provisionerId) {
    Map<CgEntityId, CgEntityNode> entities = context.getEntities();
    CgEntityId provisioner = CgEntityId.builder().id(provisionerId).type(INFRA_PROVISIONER).build();
    if (!entities.containsKey(provisioner)) {
      return MigratorUtility.RUNTIME_INPUT;
    }
    InfrastructureProvisioner infraProv = (InfrastructureProvisioner) entities.get(provisioner).getEntity();
    if (infraProv == null || StringUtils.isBlank(infraProv.getName())) {
      return MigratorUtility.RUNTIME_INPUT;
    }
    return ParameterField.createValueField(
        MigratorUtility.generateIdentifier(infraProv.getName(), CaseFormat.CAMEL_CASE));
  }
}
