/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.verification;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cvng.beans.MonitoredServiceDataSourceType;
import io.harness.cvng.cdng.beans.AutoVerificationJobSpec;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.cdng.beans.DefaultMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceSpecType;
import io.harness.cvng.cdng.beans.TemplateMonitoredServiceSpec;
import io.harness.cvng.core.beans.CVVerifyStepNode;
import io.harness.cvng.core.beans.StepSpecTypeConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.monitoredservice.bean.CGMonitoredServiceEntity;
import io.harness.ngmigration.monitoredservice.healthsource.HealthSourceGeneratorFactory;
import io.harness.ngmigration.monitoredservice.utils.MonitoredServiceEntityToMonitoredServiceMapper;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngmigration.utils.NGMigrationConstants;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.State;
import software.wings.sm.states.AbstractLogAnalysisState;
import software.wings.sm.states.AbstractMetricAnalysisState;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public abstract class VerificationBaseService extends StepMapper {
  private static String CONNECTOR_ID_FIELD = "analysisServerConfigId";
  private static String TEMPLATE_INPUT = "type: \"Application\"\n"
      + "serviceRef: \"<+service.identifier>\"\n"
      + "environmentRef: \"<+env.identifier>\"\n"
      + "sources:\n"
      + "  healthSources:\n"
      + "  - identifier: \"{$hsIdentifier}\"\n"
      + "    type: \"{$hsType}\"\n"
      + "    spec:\n"
      + "      connectorRef: \"{$hsConnectorRef}\"";
  @Inject MonitoredServiceEntityToMonitoredServiceMapper monitoredServiceEntityToMonitoredServiceMapper;
  @Inject HealthSourceGeneratorFactory healthSourceGeneratorFactory;
  @Inject WorkflowService workflowService;

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.MANUAL_EFFORT;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.VERIFY;
  }

  public List<CgEntityId> getReferencedEntities(
      String accountId, Workflow workflow, GraphNode graphNode, Map<String, String> stepIdToServiceIdMap) {
    List<CgEntityId> referencedEntities =
        new ArrayList<>(super.getReferencedEntities(accountId, workflow, graphNode, stepIdToServiceIdMap));
    if (monitoredServiceEntityToMonitoredServiceMapper.isMigrationSupported(graphNode)) {
      CGMonitoredServiceEntity cgMonitoredServiceEntity = getMonitoredServiceEntity(graphNode, workflow);
      referencedEntities.add(CgEntityId.builder()
                                 .id(cgMonitoredServiceEntity.getId())
                                 .type(NGMigrationEntityType.MONITORED_SERVICE_TEMPLATE)
                                 .build());
      if (graphNode.getProperties() != null
          && EmptyPredicate.isNotEmpty((String) graphNode.getProperties().get(CONNECTOR_ID_FIELD))) {
        referencedEntities.add(CgEntityId.builder()
                                   .type(NGMigrationEntityType.CONNECTOR)
                                   .id(graphNode.getProperties().get(CONNECTOR_ID_FIELD).toString())
                                   .build());
      }
    }
    return referencedEntities;
  }

  protected AbstractStepNode getVerifySpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    State baseState = getState(graphNode);
    ParameterField<String> sensitivity = MigratorUtility.RUNTIME_INPUT;
    ParameterField<String> duration = MigratorUtility.RUNTIME_INPUT;

    if (baseState instanceof AbstractLogAnalysisState) {
      AbstractLogAnalysisState state = (AbstractLogAnalysisState) baseState;
      sensitivity = ParameterField.createValueField(state.getAnalysisTolerance().name());
      duration = ParameterField.createValueField(state.getTimeDuration() + "m");
    }
    if (baseState instanceof AbstractMetricAnalysisState) {
      AbstractMetricAnalysisState state = (AbstractMetricAnalysisState) baseState;
      sensitivity = ParameterField.createValueField(state.getAnalysisTolerance().name());
      duration = ParameterField.createValueField(state.getTimeDuration() + "m");
    }
    CVVerifyStepNode verifyStepNode = new CVVerifyStepNode();
    baseSetup(graphNode, verifyStepNode, context.getIdentifierCaseFormat());
    verifyStepNode.setVerifyStepInfo(
        CVNGStepInfo.builder()
            .spec(AutoVerificationJobSpec.builder()
                      .sensitivity(sensitivity)
                      .duration(duration)
                      .deploymentTag(ParameterField.createValueField("<+artifacts.primary.tag>"))
                      .build())
            .type("Auto")
            .monitoredService(getMonitoredServiceNode(migrationContext, context, graphNode))
            .build());
    return verifyStepNode;
  }

  @SneakyThrows
  private MonitoredServiceNode getMonitoredServiceNode(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    if (monitoredServiceEntityToMonitoredServiceMapper.isMigrationSupported(graphNode)) {
      String connectorId = null;
      if (graphNode.getProperties() != null && graphNode.getProperties().get("analysisServerConfigId") != null) {
        connectorId = graphNode.getProperties().get("analysisServerConfigId").toString();
      }

      // Need from DB as phases are modified
      Workflow workflow =
          workflowService.readWorkflow(context.getWorkflow().getAppId(), context.getWorkflow().getUuid());
      CGMonitoredServiceEntity cgMonitoredServiceEntity = getMonitoredServiceEntity(graphNode, workflow);

      NgEntityDetail ngEntityDetail = migrationContext.getMigratedEntities()
                                          .get(CgEntityId.builder()
                                                   .type(NGMigrationEntityType.MONITORED_SERVICE_TEMPLATE)
                                                   .id(cgMonitoredServiceEntity.getId())
                                                   .build())
                                          .getNgEntityDetail();
      String templateInput =
          StringUtils.replaceEach(TEMPLATE_INPUT, new String[] {"{$hsIdentifier}", "{$hsType}", "{$hsConnectorRef}"},
              new String[] {MigratorUtility.generateIdentifier(graphNode.getName(), CaseFormat.LOWER_CASE),
                  MonitoredServiceDataSourceType
                      .getDataSourceType(healthSourceGeneratorFactory.getHealthSourceGenerator(graphNode.getType())
                                             .get()
                                             .getDataSourceType(graphNode))
                      .getDisplayName(),
                  MigratorUtility.getIdentifierWithScopeDefaults(context.getMigratedEntities(), connectorId,
                      NGMigrationEntityType.CONNECTOR, NGMigrationConstants.RUNTIME_INPUT)});
      return MonitoredServiceNode.builder()
          .type(MonitoredServiceSpecType.TEMPLATE.getName())
          .spec(TemplateMonitoredServiceSpec.builder()
                    .monitoredServiceTemplateRef(ParameterField.createValueField(ngEntityDetail.getIdentifier()))
                    .versionLabel("v1")
                    .templateInputs(YamlUtils.read(templateInput, JsonNode.class))
                    .build())
          .build();
    } else {
      return MonitoredServiceNode.builder()
          .type(MonitoredServiceSpecType.DEFAULT.getName())
          .spec(DefaultMonitoredServiceSpec.builder().build())
          .build();
    }
  }

  @Nullable
  public static CGMonitoredServiceEntity getMonitoredServiceEntity(GraphNode graphNode, Workflow workflow) {
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    if (orchestrationWorkflow != null) {
      List<WorkflowPhase> phases = orchestrationWorkflow.getWorkflowPhases();
      if (isNotEmpty(phases)) {
        for (WorkflowPhase phase : phases) {
          List<PhaseStep> phaseSteps = phase.getPhaseSteps();
          for (PhaseStep phaseStep : phaseSteps) {
            List<GraphNode> steps = phaseStep.getSteps();
            if (isNotEmpty(steps)) {
              Optional<GraphNode> first = steps.stream().filter(gn -> gn.getId().equals(graphNode.getId())).findFirst();
              if (first.isPresent()) {
                return CGMonitoredServiceEntity.builder()
                    .workflow(workflow)
                    .stepNode(first.get())
                    .phase(phase)
                    .phaseStep(phaseStep)
                    .build();
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public static CGMonitoredServiceEntity getMonitoredServiceEntity(String entityId, Workflow workflow) {
    List<GraphNode> steps = MigratorUtility.getSteps(workflow);
    GraphNode graphNode = steps.stream().filter(node -> node.getId().equals(entityId)).findFirst().get();
    return getMonitoredServiceEntity(graphNode, workflow);
  }
}
