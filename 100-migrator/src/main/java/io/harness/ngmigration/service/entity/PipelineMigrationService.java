/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.ngmigration.NGMigrationEntityType.PIPELINE;
import static software.wings.ngmigration.NGMigrationEntityType.WORKFLOW;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGSkipDetail;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.service.MigrationTemplateUtils;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.step.ApprovalStepMapperImpl;
import io.harness.ngmigration.service.workflow.WorkflowHandler;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.plancreator.stages.StageElementWrapperConfig;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.steps.approval.stage.ApprovalStageConfig;
import io.harness.steps.approval.stage.ApprovalStageNode;
import io.harness.steps.pipelinestage.PipelineStageConfig;
import io.harness.steps.pipelinestage.PipelineStageNode;
import io.harness.steps.template.stage.TemplateStageNode;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.yaml.TemplateLinkConfig;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.PipelineService;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class PipelineMigrationService extends NgMigrationService {
  @Inject private PipelineService pipelineService;
  @Inject private WorkflowMigrationService workflowMigrationService;
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private MigrationTemplateUtils migrationTemplateUtils;
  @Inject PipelineServiceClient pipelineServiceClient;
  @Inject ApprovalStepMapperImpl approvalStepMapper;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    PipelineInfoConfig pipelineInfoConfig = ((PipelineConfig) yamlFile.getYaml()).getPipelineInfoConfig();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(NGMigrationEntityType.PIPELINE.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(pipelineInfoConfig.getOrgIdentifier())
        .projectIdentifier(pipelineInfoConfig.getProjectIdentifier())
        .identifier(pipelineInfoConfig.getIdentifier())
        .scope(MigratorMappingService.getScope(
            pipelineInfoConfig.getOrgIdentifier(), pipelineInfoConfig.getProjectIdentifier()))
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(basicInfo.getAccountId(),
            pipelineInfoConfig.getOrgIdentifier(), pipelineInfoConfig.getProjectIdentifier(),
            pipelineInfoConfig.getIdentifier()))
        .build();
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    Pipeline pipeline = (Pipeline) entity;
    String entityId = pipeline.getUuid();
    CgEntityId pipelineEntityId = CgEntityId.builder().type(NGMigrationEntityType.PIPELINE).id(entityId).build();
    CgEntityNode pipelineNode = CgEntityNode.builder()
                                    .id(entityId)
                                    .appId(pipeline.getAppId())
                                    .type(NGMigrationEntityType.PIPELINE)
                                    .entityId(pipelineEntityId)
                                    .entity(pipeline)
                                    .build();

    Set<CgEntityId> children = new HashSet<>();
    if (isNotEmpty(pipeline.getPipelineStages())) {
      List<PipelineStage> stages = pipeline.getPipelineStages();
      stages.stream().flatMap(stage -> stage.getPipelineStageElements().stream()).forEach(stageElement -> {
        // Handle Approval State
        if (StateType.ENV_STATE.name().equals(stageElement.getType())) {
          String workflowId = (String) stageElement.getProperties().get("workflowId");
          if (isNotEmpty(workflowId)) {
            children.add(CgEntityId.builder().type(NGMigrationEntityType.WORKFLOW).id(workflowId).build());
          }
        }
      });
    }

    return DiscoveryNode.builder().children(children).entityNode(pipelineNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    Pipeline pipeline = pipelineService.getPipeline(appId, entityId);
    if (pipeline == null) {
      throw new InvalidRequestException(
          format("Pipeline with id:[%s] in application with id:[%s] doesn't exist", entityId, appId));
    }
    return discover(pipeline);
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    try {
      String yaml = YamlUtils.write(yamlFile.getYaml());
      Response<ResponseDTO<PipelineSaveResponse>> resp =
          pmsClient
              .createPipeline(auth, inputDTO.getAccountIdentifier(), inputDTO.getOrgIdentifier(),
                  inputDTO.getProjectIdentifier(), RequestBody.create(MediaType.parse("application/yaml"), yaml))
              .execute();
      log.info("Pipeline creation Response details {} {}", resp.code(), resp.message());
      if (resp.code() >= 400) {
        log.info("Pipeline generated is \n - {}", yaml);
      }
      return handleResp(yamlFile, resp);
    } catch (Exception ex) {
      log.error("Pipeline creation failed - ", ex);
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(ImportError.builder()
                                                .message("There was an error creating the pipeline")
                                                .entity(yamlFile.getCgBasicInfo())
                                                .build()))
          .build();
    }
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    if (EmptyPredicate.isNotEmpty(inputDTO.getDefaults()) && inputDTO.getDefaults().containsKey(PIPELINE)
        && inputDTO.getDefaults().get(PIPELINE).isSkipMigration()) {
      return null;
    }
    Pipeline pipeline = (Pipeline) entities.get(entityId).getEntity();
    if (EmptyPredicate.isEmpty(pipeline.getPipelineStages())) {
      // TODO: @deepakputhraya
      return null;
    }

    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, pipeline.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(inputDTO.getOverrides(), entityId, name);
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    String description = StringUtils.isBlank(pipeline.getDescription()) ? "" : pipeline.getDescription();

    List<StageElementWrapperConfig> ngStages = new ArrayList<>();

    for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
      for (PipelineStageElement stageElement : pipelineStage.getPipelineStageElements()) {
        StageElementWrapperConfig stage = null;
        if (StateType.ENV_STATE.name().equals(stageElement.getType())) {
          String workflowId = (String) stageElement.getProperties().get("workflowId");
          if (isNotEmpty(workflowId)) {
            NGSkipDetail skipDetail = getSkipDetailForWorkflowStage(pipeline, stageElement, migratedEntities);
            if (skipDetail != null) {
              return YamlGenerationDetails.builder().skipDetails(Collections.singletonList(skipDetail)).build();
            }
            stage = buildWorkflowStage(pipeline.getAccountId(), stageElement, migratedEntities);
          }
        } else {
          stage = buildApprovalStage(stageElement);
        }
        // If the stage cannot be migrated then we skip building the pipeline.
        if (stage == null) {
          // TODO @Deepakputhraya
          return null;
        }
        ngStages.add(stage);
      }
    }

    if (EmptyPredicate.isEmpty(ngStages)) {
      return YamlGenerationDetails.builder()
          .skipDetails(Collections.singletonList(NGSkipDetail.builder()
                                                     .reason("The constructed pipeline had no stages")
                                                     .cgBasicInfo(pipeline.getCgBasicInfo())
                                                     .type(PIPELINE)
                                                     .build()))
          .build();
    }

    List<NGYamlFile> files = new ArrayList<>();
    NGYamlFile ngYamlFile =
        NGYamlFile.builder()
            .type(PIPELINE)
            .filename("pipelines/" + name + ".yaml")
            .yaml(PipelineConfig.builder()
                      .pipelineInfoConfig(PipelineInfoConfig.builder()
                                              .identifier(identifier)
                                              .name(name)
                                              .description(ParameterField.createValueField(description))
                                              .projectIdentifier(projectIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .stages(ngStages)
                                              .allowStageExecutions(true)
                                              .build())
                      .build())
            .ngEntityDetail(NgEntityDetail.builder()
                                .identifier(identifier)
                                .orgIdentifier(orgIdentifier)
                                .projectIdentifier(projectIdentifier)
                                .build())
            .cgBasicInfo(pipeline.getCgBasicInfo())
            .build();
    files.add(ngYamlFile);
    migratedEntities.putIfAbsent(entityId, ngYamlFile);
    return YamlGenerationDetails.builder().yamlFileList(files).build();
  }

  private StageElementWrapperConfig buildApprovalStage(PipelineStageElement stageElement) {
    AbstractStepNode stepNode = approvalStepMapper.getSpec(stageElement);
    ExecutionWrapperConfig stepWrapper =
        ExecutionWrapperConfig.builder().step(JsonPipelineUtils.asTree(stepNode)).build();

    ApprovalStageNode approvalStageNode = new ApprovalStageNode();
    approvalStageNode.setName(stageElement.getName());
    approvalStageNode.setIdentifier(MigratorUtility.generateIdentifier(stageElement.getName()));
    approvalStageNode.setApprovalStageConfig(
        ApprovalStageConfig.builder()
            .execution(ExecutionElementConfig.builder().steps(Collections.singletonList(stepWrapper)).build())
            .build());
    approvalStageNode.setFailureStrategies(WorkflowHandler.getDefaultFailureStrategy());

    return StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(approvalStageNode)).build();
  }

  private NGSkipDetail getSkipDetailForWorkflowStage(
      Pipeline pipeline, PipelineStageElement stageElement, Map<CgEntityId, NGYamlFile> migratedEntities) {
    // TODO: Handle Skip condition
    String workflowId = stageElement.getProperties().get("workflowId").toString();
    // Throw error if the stage is using canary or multi WFs.
    NGYamlFile wfTemplate = migratedEntities.get(CgEntityId.builder().id(workflowId).type(WORKFLOW).build());
    if (wfTemplate == null) {
      log.warn("The workflow was not migrated, aborting pipeline migration {}", workflowId);
      return NGSkipDetail.builder()
          .reason("The workflow used as a stage was not migrated")
          .cgBasicInfo(pipeline.getCgBasicInfo())
          .type(PIPELINE)
          .build();
    }

    if (wfTemplate.getYaml() instanceof NGTemplateConfig) {
      NGTemplateConfig wfTemplateConfig = (NGTemplateConfig) wfTemplate.getYaml();
      if (TemplateEntityType.PIPELINE_TEMPLATE.equals(wfTemplateConfig.getTemplateInfoConfig().getType())) {
        log.warn("Cannot link a multi-service WFs as they are created as pipeline templates");
        return NGSkipDetail.builder()
            .reason("A multi-service workflow is linked to this pipeline.")
            .cgBasicInfo(pipeline.getCgBasicInfo())
            .type(PIPELINE)
            .build();
      }
    }
    return null;
  }

  private StageElementWrapperConfig buildWorkflowStage(
      String accountId, PipelineStageElement stageElement, Map<CgEntityId, NGYamlFile> migratedEntities) {
    // TODO: Handle Skip condition
    String workflowId = stageElement.getProperties().get("workflowId").toString();
    // Throw error if the stage is using canary or multi WFs.
    NGYamlFile wfTemplate = migratedEntities.get(CgEntityId.builder().id(workflowId).type(WORKFLOW).build());
    if (wfTemplate == null) {
      log.error("The workflow was not migrated, aborting pipeline migration {}", workflowId);
      return null;
    }

    if (wfTemplate.getYaml() instanceof PipelineConfig) {
      PipelineInfoConfig pipelineConfig = ((PipelineConfig) wfTemplate.getYaml()).getPipelineInfoConfig();
      PipelineStageConfig pipelineStageConfig = PipelineStageConfig.builder()
                                                    .pipeline(pipelineConfig.getIdentifier())
                                                    .project(pipelineConfig.getProjectIdentifier())
                                                    .org(pipelineConfig.getOrgIdentifier())
                                                    .build();
      PipelineStageNode stageNode = new PipelineStageNode();
      stageNode.setName(stageElement.getName());
      stageNode.setIdentifier(MigratorUtility.generateIdentifier(stageElement.getName()));
      stageNode.setDescription(ParameterField.createValueField(""));
      stageNode.setPipelineStageConfig(pipelineStageConfig);

      return StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(stageNode)).build();
    }

    NGTemplateConfig wfTemplateConfig = (NGTemplateConfig) wfTemplate.getYaml();
    if (TemplateEntityType.PIPELINE_TEMPLATE.equals(wfTemplateConfig.getTemplateInfoConfig().getType())) {
      log.warn("Cannot link a multi-service WFs as they are created as pipeline templates");
      return null;
    }

    TemplateLinkConfig templateLinkConfig = new TemplateLinkConfig();
    templateLinkConfig.setTemplateRef(MigratorUtility.getIdentifierWithScope(wfTemplate.getNgEntityDetail()));
    templateLinkConfig.setTemplateInputs(
        migrationTemplateUtils.getTemplateInputs(wfTemplate.getNgEntityDetail(), accountId));

    TemplateStageNode templateStageNode = new TemplateStageNode();
    templateStageNode.setName(stageElement.getName());
    templateStageNode.setIdentifier(MigratorUtility.generateIdentifier(stageElement.getName()));
    templateStageNode.setDescription("");
    templateStageNode.setTemplate(templateLinkConfig);

    return StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(templateStageNode)).build();
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
      PMSPipelineResponseDTO response = NGRestUtils.getResponse(
          pipelineServiceClient.getPipelineByIdentifier(ngEntityDetail.getIdentifier(), accountIdentifier,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier(), null, null, false));
      if (response == null || StringUtils.isBlank(response.getYamlPipeline())) {
        return null;
      }
      return YamlUtils.read(response.getYamlPipeline(), PipelineConfig.class);
    } catch (Exception ex) {
      log.warn("Error when getting pipeline - ", ex);
      return null;
    }
  }

  @Override
  protected boolean isNGEntityExists() {
    // To avoid migrating Pipelines to NG.
    return true;
  }
}
