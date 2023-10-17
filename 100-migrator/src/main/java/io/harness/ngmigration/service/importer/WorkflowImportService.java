/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.importer;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.service.NgMigrationService.getYamlStringV2;
import static io.harness.ngmigration.utils.NGMigrationConstants.INFRASTRUCTURE_DEFINITIONS;
import static io.harness.ngmigration.utils.NGMigrationConstants.INFRA_DEFINITION_ID;
import static io.harness.ngmigration.utils.NGMigrationConstants.RUNTIME_INPUT;
import static io.harness.ngmigration.utils.NGMigrationConstants.SERVICE_ID;
import static io.harness.ngmigration.utils.NGMigrationConstants.SERVICE_INPUTS;

import static software.wings.ngmigration.NGMigrationEntityType.WORKFLOW;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.StoreType;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.beans.InputDefaults;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.MigratedDetails;
import io.harness.ngmigration.dto.SaveSummaryDTO;
import io.harness.ngmigration.dto.WorkflowFilter;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.ngmigration.service.MigrationHelperService;
import io.harness.ngmigration.service.entity.PipelineMigrationService;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.persistence.HPersistence;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.plancreator.stages.StageElementWrapperConfig;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.serializer.JsonUtils;
import io.harness.steps.template.stage.TemplateStageNode;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.yaml.TemplateLinkConfig;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.Base;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.WorkflowPhase;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_MIGRATOR, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class WorkflowImportService implements ImportService {
  @Inject DiscoveryService discoveryService;
  @Inject HPersistence hPersistence;
  @Inject private MigrationHelperService migrationHelperService;
  @Inject @Named("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceClientConfig;

  public DiscoveryResult discover(ImportDTO importConnectorDTO) {
    WorkflowFilter filter = (WorkflowFilter) importConnectorDTO.getFilter();
    String accountId = importConnectorDTO.getAccountIdentifier();
    String appId = filter.getAppId();
    Set<String> workflowIds = filter.getWorkflowIds();

    if (EmptyPredicate.isEmpty(workflowIds)) {
      List<Workflow> workflowList = hPersistence.createQuery(Workflow.class)
                                        .filter(Workflow.ACCOUNT_ID_KEY, accountId)
                                        .filter(WorkflowKeys.appId, appId)
                                        .project(WorkflowKeys.uuid, true)
                                        .asList();
      if (EmptyPredicate.isNotEmpty(workflowList)) {
        workflowIds = workflowList.stream().map(Base::getUuid).collect(Collectors.toSet());
      }
    }
    if (EmptyPredicate.isEmpty(workflowIds)) {
      return null;
    }
    return discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .exportImage(false)
            .entities(workflowIds.stream()
                          .map(workflowId
                              -> DiscoverEntityInput.builder()
                                     .entityId(workflowId)
                                     .type(NGMigrationEntityType.WORKFLOW)
                                     .appId(appId)
                                     .build())
                          .collect(Collectors.toList()))
            .build());
  }

  public void postMigrationSteps(
      String authToken, ImportDTO importDTO, DiscoveryResult discoveryResult, SaveSummaryDTO summaryDTO) {
    if (!shouldCreateWorkflowAsPipeline(importDTO)) {
      return;
    }
    createWorkflowsAsPipeline(authToken, importDTO, discoveryResult, summaryDTO, new HashSet<>());
  }

  public void createWorkflowsAsPipeline(String authToken, ImportDTO importDTO, DiscoveryResult discoveryResult,
      SaveSummaryDTO summaryDTO, Set<String> workflowIds) {
    MigrationInputDTO inputDTO = MigratorUtility.getMigrationInput(authToken, importDTO);
    List<MigratedDetails> stageTemplates = new ArrayList<>();
    stageTemplates.addAll(extractStageTemplates(summaryDTO.getNgYamlFiles(), summaryDTO.getAlreadyMigratedDetails()));
    stageTemplates.addAll(
        extractStageTemplates(summaryDTO.getNgYamlFiles(), summaryDTO.getSuccessfullyMigratedDetails()));

    // If workflowIds are passed we want to create only those workflows as pipelines else we create all workflows as
    // pipelines
    if (EmptyPredicate.isNotEmpty(workflowIds)) {
      stageTemplates = stageTemplates.stream()
                           .filter(stageTemplate -> workflowIds.contains(stageTemplate.getCgEntityDetail().getId()))
                           .collect(Collectors.toList());
    }

    for (MigratedDetails migratedDetails : stageTemplates) {
      CgEntityId cgEntityId =
          CgEntityId.builder().type(WORKFLOW).id(migratedDetails.getCgEntityDetail().getId()).build();
      if (discoveryResult.getEntities().containsKey(cgEntityId)) {
        Workflow workflow = (Workflow) discoveryResult.getEntities().get(cgEntityId).getEntity();
        PipelineConfig config = getPipelineConfig(workflow, inputDTO, migratedDetails.getNgEntityDetail(), summaryDTO);
        createPipeline(inputDTO, config);
      }
    }
  }

  private void createPipeline(MigrationInputDTO inputDTO, PipelineConfig pipelineConfig) {
    PmsClient pmsClient = MigratorUtility.getRestClient(inputDTO, pipelineServiceClientConfig, PmsClient.class);

    try {
      String yaml = YamlUtils.writeYamlString(pipelineConfig);
      Response<ResponseDTO<PipelineSaveResponse>> resp =
          pmsClient
              .createPipeline(inputDTO.getDestinationAuthToken(), inputDTO.getDestinationAccountIdentifier(),
                  inputDTO.getOrgIdentifier(), inputDTO.getProjectIdentifier(),
                  RequestBody.create(MediaType.parse("application/yaml"), yaml), StoreType.INLINE)
              .execute();

      if (!(resp.code() >= 200 && resp.code() < 300)) {
        yaml = getYamlStringV2(pipelineConfig);
        resp = pmsClient
                   .createPipeline(inputDTO.getDestinationAuthToken(), inputDTO.getDestinationAccountIdentifier(),
                       inputDTO.getOrgIdentifier(), inputDTO.getProjectIdentifier(),
                       RequestBody.create(MediaType.parse("application/yaml"), yaml), StoreType.INLINE)
                   .execute();
      }

      log.info("Workflow as pipeline creation Response details {} {}", resp.code(), resp.message());
      if (resp.code() >= 400) {
        log.info("Workflows as pipeline template is \n - {}", NGYamlUtils.getYamlString(pipelineConfig));
      }
      if (resp.code() >= 200 && resp.code() < 300) {
        return;
      }
      log.info("The Yaml of the generated data was - {}", NGYamlUtils.getYamlString(pipelineConfig));
      Map<String, Object> error = null;
      error = JsonUtils.asObject(
          resp.errorBody() != null ? resp.errorBody().string() : "{}", new TypeReference<Map<String, Object>>() {});
      log.error(String.format(
          "There was error creating the workflow as pipeline. Response from NG - %s with error body errorBody -  %s",
          resp, error));
    } catch (Exception e) {
      log.error("Error creating the pipeline for workflow", e);
    }
  }

  private static List<MigratedDetails> extractStageTemplates(List<NGYamlFile> yamls, List<MigratedDetails> entities) {
    if (EmptyPredicate.isEmpty(entities) || EmptyPredicate.isEmpty(yamls)) {
      return Collections.emptyList();
    }
    Map<String, NGYamlFile> wfNg =
        yamls.stream()
            .filter(ngYamlFile -> ngYamlFile.getCgBasicInfo() != null)
            .filter(ngYamlFile -> WORKFLOW.equals(ngYamlFile.getCgBasicInfo().getType()))
            .collect(Collectors.toMap(yamlFile -> yamlFile.getCgBasicInfo().getId(), yamlFile -> yamlFile));

    return entities.stream()
        .filter(migratedDetails
            -> migratedDetails.getCgEntityDetail() != null && migratedDetails.getNgEntityDetail() != null)
        .filter(migratedDetails -> WORKFLOW.equals(migratedDetails.getCgEntityDetail().getType()))
        .filter(migratedDetails -> wfNg.containsKey(migratedDetails.getCgEntityDetail().getId()))
        .filter(migratedDetails
            -> wfNg.get(migratedDetails.getCgEntityDetail().getId()).getYaml() instanceof NGTemplateConfig)
        .collect(Collectors.toList());
  }

  private static boolean shouldCreateWorkflowAsPipeline(ImportDTO importDTO) {
    if (importDTO == null || importDTO.getInputs() == null
        || EmptyPredicate.isEmpty(importDTO.getInputs().getDefaults())) {
      return false;
    }
    InputDefaults inputDefaults = importDTO.getInputs().getDefaults().get(WORKFLOW);
    if (inputDefaults == null) {
      return false;
    }
    return Boolean.TRUE.equals(inputDefaults.getWorkflowAsPipeline());
  }

  private PipelineConfig getPipelineConfig(
      Workflow workflow, MigrationInputDTO inputDTO, NgEntityDetail ngEntityDetail, SaveSummaryDTO summaryDTO) {
    String name = MigratorUtility.generateName(workflow.getName());
    String identifier = MigratorUtility.generateIdentifier(workflow.getName(), inputDTO.getIdentifierCaseFormat());
    Scope scope = Scope.PROJECT;
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    String description = String.format("Pipeline generated from a First Gen Workflow - %s", workflow.getName());

    TemplateLinkConfig templateLinkConfig = new TemplateLinkConfig();
    templateLinkConfig.setTemplateRef(MigratorUtility.getIdentifierWithScope(ngEntityDetail));
    JsonNode templateInputs =
        migrationHelperService.getTemplateInputs(inputDTO, ngEntityDetail, inputDTO.getDestinationAccountIdentifier());

    if (templateInputs != null && "Deployment".equals(templateInputs.get("type").asText())) {
      fixServiceDetails(templateInputs, workflow, inputDTO, summaryDTO);
      fixEnvAndInfraDetails(templateInputs, workflow, inputDTO, summaryDTO);
    }

    templateLinkConfig.setTemplateInputs(templateInputs);
    TemplateStageNode templateStageNode = new TemplateStageNode();
    templateStageNode.setName(name);
    templateStageNode.setIdentifier(identifier);
    templateStageNode.setDescription(workflow.getDescription());
    templateStageNode.setTemplate(templateLinkConfig);

    StageElementWrapperConfig stage =
        StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(templateStageNode)).build();

    return PipelineConfig.builder()
        .pipelineInfoConfig(PipelineInfoConfig.builder()
                                .identifier(identifier)
                                .name(name)
                                .description(ParameterField.createValueField(description))
                                .projectIdentifier(projectIdentifier)
                                .orgIdentifier(orgIdentifier)
                                .stages(Collections.singletonList(stage))
                                .allowStageExecutions(true)
                                .build())
        .build();
  }

  private void fixEnvAndInfraDetails(
      JsonNode templateInputs, Workflow workflow, MigrationInputDTO inputDTO, SaveSummaryDTO summaryDTO) {
    String envRef = templateInputs.at("/spec/environment/environmentRef").asText();
    if (!RUNTIME_INPUT.equals(envRef)) {
      return;
    }

    Map<String, String> workflowVariables = getWorkflowVariables(workflow);
    String envId = getEnvId(workflow, workflowVariables);
    String stageEnvRef = getEnvRef(envId, summaryDTO);

    String infraId = getInfraId(workflow, workflowVariables);
    Pair<String, JsonNode> infraRefAndInput = getInfraRefAndInputs(infraId, stageEnvRef, summaryDTO, inputDTO);
    String stageInfraRef = infraRefAndInput.getKey();
    JsonNode infraInputs = infraRefAndInput.getValue();

    ObjectNode environment = (ObjectNode) templateInputs.get("spec").get("environment");
    environment.put("environmentRef", stageEnvRef);
    environment.remove("environmentInputs");
    if (infraInputs != null) {
      environment.set(INFRASTRUCTURE_DEFINITIONS, infraInputs);
    } else if (StringUtils.isNotBlank(stageInfraRef) && !RUNTIME_INPUT.equals(stageInfraRef)) {
      environment.set(
          INFRASTRUCTURE_DEFINITIONS, JsonPipelineUtils.readTree("[{\"identifier\": \"" + stageInfraRef + "\"}]"));
    }
  }

  private void fixServiceDetails(
      JsonNode templateInputs, Workflow workflow, MigrationInputDTO inputDTO, SaveSummaryDTO summaryDTO) {
    String serviceRef = templateInputs.at("/spec/service/serviceRef").asText();
    if (!RUNTIME_INPUT.equals(serviceRef)) {
      return;
    }

    Map<String, String> workflowVariables = getWorkflowVariables(workflow);
    String serviceId = getServiceId(workflow, workflowVariables);
    Pair<String, JsonNode> serviceRefAndInput = getServiceRefAndInputs(serviceId, summaryDTO, inputDTO);
    String stageServiceRef = serviceRefAndInput.getKey();
    JsonNode serviceInputs = serviceRefAndInput.getValue();

    if (!RUNTIME_INPUT.equals(stageServiceRef)) {
      ObjectNode service = (ObjectNode) templateInputs.get("spec").get("service");
      service.put("serviceRef", stageServiceRef);
      if (serviceInputs == null) {
        service.remove(SERVICE_INPUTS);
      } else {
        service.set(SERVICE_INPUTS, serviceInputs);
      }
    }
  }

  private Map<String, String> getWorkflowVariables(Workflow workflow) {
    List<Variable> userVariables = workflow.getOrchestrationWorkflow().getUserVariables();
    Map<String, String> workflowVariables = new HashMap<>();
    for (Variable var : userVariables) {
      workflowVariables.put(var.getName(), var.getValue());
    }
    return workflowVariables;
  }

  private String getServiceId(Workflow workflow, Map<String, String> workflowVariables) {
    if (workflow == null) {
      return null;
    }
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    if (isEmpty(orchestrationWorkflow.getWorkflowPhases())) {
      return null;
    }
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    String serviceExpression = PipelineMigrationService.getExpression(workflowPhase, SERVICE_ID);
    if (StringUtils.isBlank(serviceExpression)) {
      return workflowPhase.getServiceId();
    }
    String serviceId = workflowVariables.get(serviceExpression);
    if (StringUtils.isNotBlank(serviceId)) {
      return serviceId;
    }
    return null;
  }

  private String getEnvId(Workflow workflow, Map<String, String> workflowVariables) {
    if (workflow == null) {
      return null;
    }
    if (!workflow.isEnvTemplatized()) {
      return workflow.getEnvId();
    }
    String envExpression = workflow.fetchEnvTemplatizedName();
    String envId = workflowVariables.get(envExpression);
    if (StringUtils.isNotBlank(envId)) {
      return envId;
    }
    return null;
  }

  private String getInfraId(Workflow workflow, Map<String, String> workflowVariables) {
    if (workflow == null) {
      return null;
    }
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    if (isEmpty(orchestrationWorkflow.getWorkflowPhases())) {
      return null;
    }
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    String infraExpression = PipelineMigrationService.getExpression(workflowPhase, INFRA_DEFINITION_ID);
    if (StringUtils.isBlank(infraExpression)) {
      return workflowPhase.getInfraDefinitionId();
    }
    String infraId = workflowVariables.get(infraExpression);
    if (StringUtils.isNotBlank(infraId)) {
      return infraId;
    }
    return null;
  }

  private static String getExpression(WorkflowPhase workflowPhase, String field) {
    List<TemplateExpression> templateExpressions =
        ListUtils.defaultIfNull(workflowPhase.getTemplateExpressions(), new ArrayList<>());
    return templateExpressions.stream()
        .filter(te -> StringUtils.isNoneBlank(te.getExpression(), te.getFieldName()))
        .filter(te -> field.equals(te.getFieldName()))
        .map(TemplateExpression::getExpression)
        .filter(MigratorUtility::isExpression)
        .map(te -> te.substring(2, te.length() - 1))
        .findFirst()
        .orElse(null);
  }

  private Pair<String, JsonNode> getServiceRefAndInputs(
      String serviceId, SaveSummaryDTO summaryDTO, MigrationInputDTO inputDTO) {
    if (StringUtils.isBlank(serviceId) || MigratorUtility.isExpression(serviceId)) {
      return Pair.of(RUNTIME_INPUT, null);
    }

    String stageServiceRef = RUNTIME_INPUT;
    JsonNode serviceInputs = null;

    List<NgEntityDetail> serviceEntityNG = getMigratedEntity(summaryDTO, serviceId);
    if (isNotEmpty(serviceEntityNG)) {
      NgEntityDetail serviceDetails = serviceEntityNG.get(0);
      stageServiceRef = MigratorUtility.getIdentifierWithScope(serviceDetails);
      serviceInputs =
          migrationHelperService.getServiceInput(inputDTO, serviceDetails, inputDTO.getDestinationAccountIdentifier());
      if (serviceInputs != null) {
        serviceInputs = serviceInputs.get(SERVICE_INPUTS);
      }
    }
    return Pair.of(stageServiceRef, serviceInputs);
  }

  private String getEnvRef(String envId, SaveSummaryDTO summaryDTO) {
    if (StringUtils.isBlank(envId) || MigratorUtility.isExpression(envId)) {
      return RUNTIME_INPUT;
    }

    String stageEnvRef = RUNTIME_INPUT;
    List<NgEntityDetail> environmentEntityNG = getMigratedEntity(summaryDTO, envId);

    if (isNotEmpty(environmentEntityNG)) {
      NgEntityDetail environmentDetails = environmentEntityNG.get(0);
      stageEnvRef = MigratorUtility.getIdentifierWithScope(environmentDetails);
    }
    return stageEnvRef;
  }

  private Pair<String, JsonNode> getInfraRefAndInputs(
      String infraId, String stageEnvRef, SaveSummaryDTO summaryDTO, MigrationInputDTO inputDTO) {
    if (StringUtils.isBlank(infraId) || RUNTIME_INPUT.equals(stageEnvRef) || MigratorUtility.isExpression(infraId)) {
      return Pair.of(RUNTIME_INPUT, null);
    }

    String stageInfraRef = RUNTIME_INPUT;
    JsonNode infraInputs = null;

    List<NgEntityDetail> infraEntityNG = getMigratedEntity(summaryDTO, infraId);

    if (isNotEmpty(infraEntityNG)) {
      NgEntityDetail infraDetails = infraEntityNG.get(0);
      stageInfraRef = MigratorUtility.getIdentifierWithScope(infraDetails);
      infraInputs = migrationHelperService.getInfraInput(
          inputDTO, inputDTO.getDestinationAccountIdentifier(), stageEnvRef, infraDetails);
      if (infraInputs != null) {
        infraInputs = infraInputs.get(INFRASTRUCTURE_DEFINITIONS);
      }
    }
    return Pair.of(stageInfraRef, infraInputs);
  }

  private List<NgEntityDetail> getMigratedEntity(SaveSummaryDTO summaryDTO, String entityId) {
    List<NgEntityDetail> entityNG = summaryDTO.getAlreadyMigratedDetails()
                                        .stream()
                                        .filter(migratedEntity -> {
                                          CgBasicInfo cgEntityDetail = migratedEntity.getCgEntityDetail();
                                          return cgEntityDetail != null && entityId.equals(cgEntityDetail.getId());
                                        })
                                        .map(MigratedDetails::getNgEntityDetail)
                                        .collect(Collectors.toList());

    List<NgEntityDetail> successfullyMigratedList =
        summaryDTO.getSuccessfullyMigratedDetails()
            .stream()
            .filter(migratedEntity -> {
              CgBasicInfo cgEntityDetail = migratedEntity.getCgEntityDetail();
              return cgEntityDetail != null && entityId.equals(cgEntityDetail.getId());
            })
            .map(MigratedDetails::getNgEntityDetail)
            .collect(Collectors.toList());
    entityNG.addAll(successfullyMigratedList);

    return entityNG;
  }
}
