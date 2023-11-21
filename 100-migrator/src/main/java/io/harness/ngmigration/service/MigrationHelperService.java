/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import static software.wings.ngmigration.NGMigrationEntityType.MANIFEST;
import static software.wings.ngmigration.NGMigrationEntityType.WORKFLOW;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.infrastructure.InfrastructureResourceClient;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.service.step.StepMapperFactory;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.persistence.HPersistence;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.service.remote.ServiceResourceClient;
import io.harness.template.remote.TemplateResourceClient;

import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.ServiceVariableKeys;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ApplicationManifest.ApplicationManifestKeys;
import software.wings.dl.WingsMongoPersistence;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.WorkflowService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.query.Sort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class MigrationHelperService {
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private HPersistence hPersistence;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject PipelineServiceClient pipelineServiceClient;
  @Inject ServiceResourceClient serviceResourceClient;
  @Inject InfrastructureResourceClient infrastructureResourceClient;
  @Inject @Named("ngClientConfig") private ServiceHttpClientConfig ngClientConfig;
  @Inject @Named("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceClientConfig;
  @Inject @Named("templateServiceClientConfig") private ServiceHttpClientConfig templateServiceClientConfig;
  @Inject StepMapperFactory stepMapperFactory;
  @Inject WorkflowService workflowService;
  @Inject WingsMongoPersistence wingsPersistence;

  public JsonNode getTemplateInputs(
      MigrationInputDTO inputDTO, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return getTemplateInputs(inputDTO, ngEntityDetail, accountIdentifier, "");
  }

  public void addOverrideRefs(
      String appId, String accountId, String envId, List<String> serviceIds, List<CgEntityId> children) {
    if (isNotEmpty(envId) && isNotEmpty(serviceIds)) {
      List<ServiceVariable> serviceVariables =
          hPersistence.createQuery(ServiceVariable.class)
              .filter(ServiceVariableKeys.appId, appId)
              .filter(ServiceVariableKeys.entityType, EntityType.SERVICE_TEMPLATE.name())
              .filter(ServiceVariableKeys.accountId, accountId)
              .filter(ServiceVariableKeys.envId, envId)
              .asList();

      if (EmptyPredicate.isNotEmpty(serviceVariables)) {
        for (ServiceVariable serviceVariable : serviceVariables) {
          ServiceTemplate serviceTemplate =
              serviceTemplateService.get(serviceVariable.getAppId(), serviceVariable.getTemplateId());
          if (serviceIds.contains(serviceTemplate.getServiceId())) {
            children.add(CgEntityId.builder()
                             .id(serviceVariable.getUuid())
                             .type(NGMigrationEntityType.SERVICE_VARIABLE)
                             .build());
          }
        }
      }

      List<ApplicationManifest> applicationManifests = hPersistence.createQuery(ApplicationManifest.class)
                                                           .filter(ApplicationKeys.appId, appId)
                                                           .field(ApplicationManifestKeys.envId)
                                                           .equal(envId)
                                                           .field(ApplicationManifestKeys.serviceId)
                                                           .in(serviceIds)
                                                           .asList();

      if (EmptyPredicate.isNotEmpty(applicationManifests)) {
        children.addAll(applicationManifests.stream()
                            .distinct()
                            .map(manifest -> CgEntityId.builder().id(manifest.getUuid()).type(MANIFEST).build())
                            .collect(Collectors.toList()));
      }
    }
  }

  public JsonNode getTemplateInputs(
      MigrationInputDTO inputDTO, NgEntityDetail ngEntityDetail, String accountIdentifier, String versionLabel) {
    TemplateResourceClient client =
        getClient(inputDTO, templateServiceClientConfig, this.templateResourceClient, TemplateResourceClient.class);
    try {
      String response =
          NGRestUtils.getResponse(client.getTemplateInputsYaml(ngEntityDetail.getIdentifier(), accountIdentifier,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier(), versionLabel, false));
      if (response == null || StringUtils.isBlank(response)) {
        return null;
      }
      return YamlUtils.read(response, JsonNode.class);
    } catch (Exception ex) {
      log.error("Error when getting workflow templates input - ", ex);
      return null;
    }
  }

  public String getPipelineInput(MigrationInputDTO inputDTO, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    PipelineServiceClient client =
        getClient(inputDTO, pipelineServiceClientConfig, this.pipelineServiceClient, PipelineServiceClient.class);
    try {
      InputSetTemplateResponseDTOPMS response =
          NGRestUtils.getResponse(client.getTemplateFromPipeline(accountIdentifier, inputDTO.getOrgIdentifier(),
              inputDTO.getProjectIdentifier(), ngEntityDetail.getIdentifier(),
              InputSetTemplateRequestDTO.builder().stageIdentifiers(Collections.emptyList()).build()));
      if (response == null) {
        return null;
      }
      return response.getInputSetTemplateYaml();
    } catch (Exception ex) {
      log.error("Error when getting template from pipeline - ", ex);
      return null;
    }
  }

  public JsonNode getServiceInput(MigrationInputDTO inputDTO, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    ServiceResourceClient client =
        getClient(inputDTO, ngClientConfig, this.serviceResourceClient, ServiceResourceClient.class);
    try {
      NGEntityTemplateResponseDTO response =
          NGRestUtils.getResponse(client.getServiceRuntimeInputs(ngEntityDetail.getIdentifier(), accountIdentifier,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier()));
      if (response == null || StringUtils.isBlank(response.getInputSetTemplateYaml())) {
        return null;
      }
      return YamlUtils.read(response.getInputSetTemplateYaml(), JsonNode.class);
    } catch (Exception ex) {
      log.error("Error when getting service templates input - ", ex);
      return null;
    }
  }

  public JsonNode getInfraInput(
      MigrationInputDTO inputDTO, String accountIdentifier, String envIdentifier, NgEntityDetail ngEntityDetail) {
    InfrastructureResourceClient client =
        getClient(inputDTO, ngClientConfig, this.infrastructureResourceClient, InfrastructureResourceClient.class);
    try {
      NGEntityTemplateResponseDTO response =
          NGRestUtils.getResponse(client.getInfrastructureInputs(accountIdentifier, ngEntityDetail.getOrgIdentifier(),
              ngEntityDetail.getProjectIdentifier(), envIdentifier, ngEntityDetail.getIdentifier()));
      if (response == null || StringUtils.isBlank(response.getInputSetTemplateYaml())) {
        return null;
      }
      return YamlUtils.read(response.getInputSetTemplateYaml(), JsonNode.class);
    } catch (Exception ex) {
      log.error("Error when getting infra templates inputs - ", ex);
      return null;
    }
  }

  private <T> T getClient(
      MigrationInputDTO inputDTO, ServiceHttpClientConfig httpClientConfig, T defaultClient, Class<T> clazz) {
    if (StringUtils.isNoneBlank(inputDTO.getDestinationGatewayUrl(), inputDTO.getDestinationAuthToken())) {
      return MigratorUtility.getRestClient(inputDTO, httpClientConfig, clazz);
    }
    return defaultClient;
  }

  public Map<String, Object> updateContextVariables(MigrationContext migrationContext,
      Map<CgEntityId, CgEntityNode> entities, InfrastructureDefinition infrastructureDefinition) {
    Map<String, Object> custom = new HashMap<>();
    updateFromLastExecution(custom, migrationContext, infrastructureDefinition);
    if (isEmpty(custom)) {
      entities.entrySet().stream().filter(entry -> WORKFLOW.equals(entry.getValue().getType())).forEach(entry -> {
        Workflow workflow = (Workflow) entry.getValue().getEntity();
        updateFromWorkflow(custom, migrationContext, workflow, infrastructureDefinition);
      });
    }
    return custom;
  }

  public Map<String, Object> updateContextVariables(
      MigrationContext migrationContext, Map<CgEntityId, CgEntityNode> entities, Environment environment) {
    Map<String, Object> custom = new HashMap<>();
    updateFromLastExecution(custom, migrationContext, environment);
    if (isEmpty(custom)) {
      entities.entrySet().stream().filter(entry -> WORKFLOW.equals(entry.getValue().getType())).forEach(entry -> {
        Workflow workflow = (Workflow) entry.getValue().getEntity();
        updateFromWorkflow(custom, migrationContext, workflow, environment);
      });
    }
    return custom;
  }

  private void updateFromLastExecution(Map<String, Object> custom, MigrationContext migrationContext,
      InfrastructureDefinition infrastructureDefinition) {
    WorkflowExecution workflowExecution = workflowService.getLastWorkflowExecutionByInfrastructure(
        migrationContext.getAccountId(), infrastructureDefinition.getAppId(), infrastructureDefinition.getUuid());
    if (workflowExecution != null) {
      Workflow workflow =
          workflowService.readWorkflow(infrastructureDefinition.getAppId(), workflowExecution.getWorkflowId());
      WorkflowMigrationContext wfContext = WorkflowMigrationContext.newInstance(migrationContext, workflow);
      if (workflow != null && wfContext != null) {
        CanaryOrchestrationWorkflow orchestrationWorkflow =
            (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
        if (orchestrationWorkflow != null) {
          List<WorkflowPhase> phases = orchestrationWorkflow.getWorkflowPhases();
          for (WorkflowPhase phase : phases) {
            processPhase(custom, migrationContext, wfContext, phase);
          }
        }
      }
    }
  }

  private void updateFromLastExecution(
      Map<String, Object> custom, MigrationContext migrationContext, Environment environment) {
    WorkflowExecution workflowExecution = wingsPersistence.createQuery(WorkflowExecution.class)
                                              .filter(WorkflowExecutionKeys.accountId, migrationContext.getAccountId())
                                              .field(WorkflowExecutionKeys.envIds)
                                              .contains(environment.getUuid())
                                              .filter(WorkflowExecutionKeys.appId, environment.getAppId())
                                              .filter(WorkflowExecutionKeys.workflowType, ORCHESTRATION)
                                              .order(Sort.descending(WorkflowExecutionKeys.createdAt))
                                              .get();
    if (workflowExecution != null) {
      Workflow workflow = workflowService.readWorkflow(workflowExecution.getAppId(), workflowExecution.getWorkflowId());
      WorkflowMigrationContext wfContext = WorkflowMigrationContext.newInstance(migrationContext, workflow);
      if (workflow != null && wfContext != null) {
        CanaryOrchestrationWorkflow orchestrationWorkflow =
            (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
        if (orchestrationWorkflow != null) {
          List<WorkflowPhase> phases = orchestrationWorkflow.getWorkflowPhases();
          for (WorkflowPhase phase : phases) {
            processPhase(custom, migrationContext, wfContext, phase);
          }
        }
      }
    }
  }
  private void updateFromWorkflow(Map<String, Object> custom, MigrationContext migrationContext, Workflow workflow,
      InfrastructureDefinition infrastructureDefinition) {
    WorkflowMigrationContext wfContext = WorkflowMigrationContext.newInstance(migrationContext, workflow);
    if (workflow != null && wfContext != null) {
      CanaryOrchestrationWorkflow orchestrationWorkflow =
          (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
      if (orchestrationWorkflow != null) {
        List<WorkflowPhase> phases = orchestrationWorkflow.getWorkflowPhases();
        for (WorkflowPhase phase : phases) {
          if (infrastructureDefinition.getUuid().equals(phase.getInfraDefinitionId())) {
            processPhase(custom, migrationContext, wfContext, phase);
          }
        }
      }
    }
  }

  private void updateFromWorkflow(
      Map<String, Object> custom, MigrationContext migrationContext, Workflow workflow, Environment environment) {
    WorkflowMigrationContext wfContext = WorkflowMigrationContext.newInstance(migrationContext, workflow);
    List<InfrastructureDefinition> infrastructureDefinitions = environment.getInfrastructureDefinitions();
    if (isNotEmpty(infrastructureDefinitions) && workflow != null && wfContext != null) {
      CanaryOrchestrationWorkflow orchestrationWorkflow =
          (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
      if (orchestrationWorkflow != null) {
        List<WorkflowPhase> phases = orchestrationWorkflow.getWorkflowPhases();
        for (WorkflowPhase phase : phases) {
          if (infrastructureDefinitions.stream()
                  .map(InfrastructureDefinition::getUuid)
                  .collect(Collectors.toSet())
                  .contains(phase.getInfraDefinitionId())) {
            processPhase(custom, migrationContext, wfContext, phase);
          }
        }
      }
    }
  }

  private void processPhase(Map<String, Object> custom, MigrationContext migrationContext,
      WorkflowMigrationContext wfContext, WorkflowPhase phase) {
    List<PhaseStep> phaseSteps = phase.getPhaseSteps();
    List<StepExpressionFunctor> expressionFunctorsFromCurrentPhase = new ArrayList<>();
    String prefix = phase.getName();

    phaseSteps.stream().filter(phaseStep -> isNotEmpty(phaseStep.getSteps())).forEach(phaseStep -> {
      List<GraphNode> steps = phaseStep.getSteps();
      String stepGroupName = prefix + "-" + phaseStep.getName();
      steps.forEach(stepYaml -> {
        List<StepExpressionFunctor> expressionFunctors = new ArrayList<>();
        StepMapper stepMapper = stepMapperFactory.getStepMapper(stepYaml.getType());
        expressionFunctors.addAll(stepMapper.getExpressionFunctor(wfContext, phase, stepGroupName, stepYaml));
        expressionFunctors.addAll(stepMapper.getExpressionFunctor(wfContext, phase, phaseStep, stepYaml));
        if (isNotEmpty(expressionFunctors)) {
          expressionFunctorsFromCurrentPhase.addAll(expressionFunctors);
        }
      });
    });

    List<StepExpressionFunctor> distinctExpressionsFromCurrentPhase =
        expressionFunctorsFromCurrentPhase.stream()
            .filter(functor -> !custom.containsKey(functor.getCgExpression()))
            .collect(Collectors.toList());

    if (isNotEmpty(distinctExpressionsFromCurrentPhase)) {
      wfContext.getStepExpressionFunctors().addAll(distinctExpressionsFromCurrentPhase);
      custom.putAll(MigratorUtility.getExpressions(
          phase, distinctExpressionsFromCurrentPhase, migrationContext.getInputDTO().getIdentifierCaseFormat()));
    }
  }
}
