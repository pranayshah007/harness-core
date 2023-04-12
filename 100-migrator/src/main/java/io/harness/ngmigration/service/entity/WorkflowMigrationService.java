/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.ngmigration.NGMigrationEntityType.PIPELINE;
import static software.wings.ngmigration.NGMigrationEntityType.TEMPLATE;
import static software.wings.ngmigration.NGMigrationEntityType.WORKFLOW;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGSkipDetail;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.TypeSummary;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.WorkflowSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.step.StepMapperFactory;
import io.harness.ngmigration.service.workflow.WorkflowHandler;
import io.harness.ngmigration.service.workflow.WorkflowHandlerFactory;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.plancreator.stages.StageElementWrapperConfig;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.beans.TemplateWrapperResponseDTO;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.beans.yaml.NGTemplateInfoConfig;
import io.harness.template.remote.TemplateResourceClient;

import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.impl.yaml.handler.workflow.RollingWorkflowYamlHandler;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.WorkflowService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class WorkflowMigrationService extends NgMigrationService {
  public static final String VERSION = "v1";
  @Inject private InfraMigrationService infraMigrationService;
  @Inject private EnvironmentMigrationService environmentMigrationService;
  @Inject private ServiceMigrationService serviceMigrationService;
  @Inject private MonitoredServiceMigrationService monitoredServiceMigrationService;
  @Inject private WorkflowService workflowService;
  @Inject private RollingWorkflowYamlHandler rollingWorkflowYamlHandler;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private StepMapperFactory stepMapperFactory;
  @Inject private WorkflowHandlerFactory workflowHandlerFactory;
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private PipelineServiceClient pipelineServiceClient;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    String orgIdentifier;
    String projectIdentifier;
    String identifier;
    if (yamlFile.getYaml() instanceof PipelineConfig) {
      PipelineInfoConfig pipelineConfig = ((PipelineConfig) yamlFile.getYaml()).getPipelineInfoConfig();
      orgIdentifier = pipelineConfig.getOrgIdentifier();
      projectIdentifier = pipelineConfig.getProjectIdentifier();
      identifier = pipelineConfig.getIdentifier();
    } else {
      NGTemplateInfoConfig templateInfoConfig = ((NGTemplateConfig) yamlFile.getYaml()).getTemplateInfoConfig();
      orgIdentifier = templateInfoConfig.getOrgIdentifier();
      projectIdentifier = templateInfoConfig.getProjectIdentifier();
      identifier = templateInfoConfig.getIdentifier();
    }

    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(WORKFLOW.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(identifier)
        .scope(MigratorMappingService.getScope(orgIdentifier, projectIdentifier))
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(
            basicInfo.getAccountId(), orgIdentifier, projectIdentifier, identifier))
        .build();
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Map<String, Long> summaryByType =
        entities.stream()
            .map(entity -> (Workflow) entity.getEntity())
            .collect(groupingBy(entity -> entity.getOrchestration().getOrchestrationWorkflowType().name(), counting()));
    Map<String, Long> summaryByStepType = entities.stream()
                                              .flatMap(entity -> {
                                                Workflow workflow = (Workflow) entity.getEntity();
                                                WorkflowHandler workflowHandler =
                                                    workflowHandlerFactory.getWorkflowHandler(workflow);
                                                return MigratorUtility.getSteps(workflow).stream();
                                              })
                                              .collect(groupingBy(GraphNode::getType, counting()));
    Map<String, TypeSummary> stepTypeSummaryMap = new HashMap<>();
    summaryByStepType.forEach((key, value) -> {
      stepTypeSummaryMap.put(key,
          TypeSummary.builder()
              .count(value)
              .status(stepMapperFactory.getStepMapper(key).stepSupportStatus(GraphNode.builder().build()))
              .build());
    });
    Set<String> expressions =
        entities.stream()
            .flatMap(entity -> {
              Workflow workflow = (Workflow) entity.getEntity();
              WorkflowHandler workflowHandler = workflowHandlerFactory.getWorkflowHandler(workflow);
              return MigratorUtility.getSteps(workflow).stream();
            })
            .flatMap(step -> stepMapperFactory.getStepMapper(step.getType()).getExpressions(step).stream())
            .collect(Collectors.toSet());

    return WorkflowSummary.builder()
        .count(entities.size())
        .typeSummary(summaryByType)
        .stepTypeSummary(summaryByStepType)
        .stepsSummary(stepTypeSummaryMap)
        .expressions(expressions)
        .build();
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    Workflow workflow = (Workflow) entity;
    String entityId = workflow.getUuid();
    CgEntityId workflowEntityId = CgEntityId.builder().type(WORKFLOW).id(entityId).build();
    CgEntityNode workflowNode = CgEntityNode.builder()
                                    .id(entityId)
                                    .type(WORKFLOW)
                                    .appId(workflow.getAppId())
                                    .entityId(workflowEntityId)
                                    .entity(workflow)
                                    .build();

    Set<CgEntityId> children = new HashSet<>();
    List<CgEntityId> referencedEntities =
        workflowHandlerFactory.getWorkflowHandler(workflow).getReferencedEntities(stepMapperFactory, workflow);

    if (isNotEmpty(referencedEntities)) {
      children.addAll(referencedEntities);
    }
    return DiscoveryNode.builder().children(children).entityNode(workflowNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(workflowService.readWorkflow(appId, entityId));
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();

    if (isNotEmpty(inputDTO.getDefaults()) && inputDTO.getDefaults().containsKey(WORKFLOW)
        && inputDTO.getDefaults().get(WORKFLOW).isSkipMigration()) {
      return null;
    }
    Workflow workflow = (Workflow) entities.get(entityId).getEntity();
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, workflow.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(
        inputDTO.getOverrides(), entityId, name, inputDTO.getIdentifierCaseFormat());
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    String description = StringUtils.isBlank(workflow.getDescription()) ? "" : workflow.getDescription();

    WorkflowHandler workflowHandler = workflowHandlerFactory.getWorkflowHandler(workflow);
    List<GraphNode> steps = MigratorUtility.getSteps(workflow);

    if (EmptyPredicate.isEmpty(steps)) {
      steps = new ArrayList<>();
    }
    List<GraphNode> unsupportedSteps = steps.stream()
                                           .filter(step
                                               -> stepMapperFactory.getStepMapper(step.getType())
                                                      .stepSupportStatus(step)
                                                      .equals(SupportStatus.UNSUPPORTED))
                                           .collect(Collectors.toList());
    if (EmptyPredicate.isNotEmpty(unsupportedSteps)) {
      return YamlGenerationDetails.builder()
          .skipDetails(Collections.singletonList(
              NGSkipDetail.builder()
                  .type(entityId.getType())
                  .cgBasicInfo(workflow.getCgBasicInfo())
                  .reason(String.format("The workflow has unsupported steps types -> %s",
                      unsupportedSteps.stream().map(GraphNode::getType).distinct().collect(Collectors.joining(", "))))
                  .build()))
          .build();
    }

    List<NGYamlFile> files = new ArrayList<>();
    if (isNotEmpty(steps)) {
      List<NGYamlFile> additionalYamlFiles =
          steps.stream()
              .map(step -> stepMapperFactory.getStepMapper(step.getType()).getChildNGYamlFiles(inputDTO, step, name))
              .flatMap(List::stream)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
      files.addAll(additionalYamlFiles);
    }

    TemplateEntityType templateType = workflowHandler.getTemplateType(workflow);
    YamlDTO yamlDTO;
    NGMigrationEntityType ngType;
    if (templateType == TemplateEntityType.PIPELINE_TEMPLATE) {
      List<StageElementWrapperConfig> stages;
      try {
        stages = workflowHandler.asStages(migrationContext, workflow);
      } catch (Exception e) {
        return YamlGenerationDetails.builder()
            .yamlFileList(files)
            .skipDetails(Collections.singletonList(NGSkipDetail.builder()
                                                       .type(entityId.getType())
                                                       .cgBasicInfo(workflow.getCgBasicInfo())
                                                       .reason(e.getMessage())
                                                       .build()))
            .build();
      }
      yamlDTO = PipelineConfig.builder()
                    .pipelineInfoConfig(PipelineInfoConfig.builder()
                                            .identifier(identifier)
                                            .name(name)
                                            .description(ParameterField.createValueField(description))
                                            .projectIdentifier(projectIdentifier)
                                            .orgIdentifier(orgIdentifier)
                                            .stages(stages)
                                            .allowStageExecutions(true)
                                            .tags(MigratorUtility.getTags(workflow.getTagLinks()))
                                            .variables(workflowHandler.getVariables(migrationContext, workflow))
                                            .build())
                    .build();
      ngType = PIPELINE;
    } else {
      JsonNode templateSpec;
      try {
        templateSpec = workflowHandler.getTemplateSpec(migrationContext, workflow);
      } catch (Exception e) {
        log.error("Exception during migrating workflow ", e);
        return YamlGenerationDetails.builder()
            .yamlFileList(files)
            .skipDetails(Collections.singletonList(NGSkipDetail.builder()
                                                       .type(entityId.getType())
                                                       .cgBasicInfo(workflow.getCgBasicInfo())
                                                       .reason(e.getMessage())
                                                       .build()))
            .build();
      }
      if (templateSpec == null) {
        return YamlGenerationDetails.builder()
            .yamlFileList(files)
            .skipDetails(Collections.singletonList(
                NGSkipDetail.builder()
                    .type(entityId.getType())
                    .cgBasicInfo(workflow.getCgBasicInfo())
                    .reason(
                        "We could not generate a template/pipeline for the workflow. It could be because the workflow has steps that are no longer required in NG(e.g: Artifact Collection). For further assistance please reach out to Harness")
                    .build()))
            .build();
      }
      yamlDTO = NGTemplateConfig.builder()
                    .templateInfoConfig(NGTemplateInfoConfig.builder()
                                            .type(templateType)
                                            .identifier(identifier)
                                            .name(name)
                                            .description(ParameterField.createValueField(description))
                                            .projectIdentifier(projectIdentifier)
                                            .orgIdentifier(orgIdentifier)
                                            .versionLabel(VERSION)
                                            .spec(templateSpec)
                                            .tags(MigratorUtility.getTags(workflow.getTagLinks()))
                                            .build())
                    .build();
      ngType = TEMPLATE;
    }

    NGYamlFile ngYamlFile = NGYamlFile.builder()
                                .type(WORKFLOW)
                                .filename("workflows/" + name + ".yaml")
                                .yaml(yamlDTO)
                                .ngEntityDetail(NgEntityDetail.builder()
                                                    .entityType(ngType)
                                                    .identifier(identifier)
                                                    .orgIdentifier(orgIdentifier)
                                                    .projectIdentifier(projectIdentifier)
                                                    .build())
                                .cgBasicInfo(workflow.getCgBasicInfo())
                                .build();
    files.add(ngYamlFile);
    migratedEntities.putIfAbsent(entityId, ngYamlFile);
    return YamlGenerationDetails.builder().yamlFileList(files).build();
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    if (yamlFile.isExists()) {
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(ImportError.builder()
                                                .message("Workflow was not migrated as it was already imported before")
                                                .entity(yamlFile.getCgBasicInfo())
                                                .build()))
          .build();
    }
    String yaml = YamlUtils.write(yamlFile.getYaml());
    if (yamlFile.getYaml() instanceof PipelineConfig) {
      yaml = getYamlString(yamlFile);
      Response<ResponseDTO<PipelineSaveResponse>> resp =
          pmsClient
              .createPipeline(inputDTO.getDestinationAuthToken(), inputDTO.getDestinationAccountIdentifier(),
                  inputDTO.getOrgIdentifier(), inputDTO.getProjectIdentifier(),
                  RequestBody.create(MediaType.parse("application/yaml"), yaml))
              .execute();
      log.info("Workflow as pipeline creation Response details {} {}", resp.code(), resp.message());
      if (resp.code() >= 400) {
        log.info("Workflows as pipeline template is \n - {}", yaml);
      }
      return handleResp(yamlFile, resp);
    } else {
      Response<ResponseDTO<TemplateWrapperResponseDTO>> resp =
          templateClient
              .createTemplate(inputDTO.getDestinationAuthToken(), inputDTO.getDestinationAccountIdentifier(),
                  inputDTO.getOrgIdentifier(), inputDTO.getProjectIdentifier(),
                  RequestBody.create(MediaType.parse("application/yaml"), yaml))
              .execute();
      log.info("Workflow as template creation Response details {} {}", resp.code(), resp.message());
      if (resp.code() >= 400) {
        log.info("The WF template is \n - {}", yaml);
      }
      return handleResp(yamlFile, resp);
    }
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    Workflow workflow = (Workflow) cgEntityNode.getEntity();
    WorkflowHandler workflowHandler = workflowHandlerFactory.getWorkflowHandler(workflow);
    TemplateEntityType templateType = workflowHandler.getTemplateType(workflow);
    try {
      // Make API call to pipeline service if pipeline else it will be to template service
      if (templateType == TemplateEntityType.PIPELINE_TEMPLATE) {
        PMSPipelineResponseDTO response = NGRestUtils.getResponse(
            pipelineServiceClient.getPipelineByIdentifier(ngEntityDetail.getIdentifier(), accountIdentifier,
                ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier(), null, null, false));
        if (response == null || StringUtils.isBlank(response.getYamlPipeline())) {
          return null;
        }
        return YamlUtils.read(response.getYamlPipeline(), PipelineConfig.class);
      } else {
        TemplateResponseDTO response =
            NGRestUtils.getResponse(templateResourceClient.get(ngEntityDetail.getIdentifier(), accountIdentifier,
                ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier(), VERSION, false));
        if (response == null || StringUtils.isBlank(response.getYaml())) {
          return null;
        }
        return YamlUtils.read(response.getYaml(), NGTemplateConfig.class);
      }
    } catch (Exception ex) {
      log.warn("Error when getting workflow - ", ex);
      return null;
    }
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }
}
