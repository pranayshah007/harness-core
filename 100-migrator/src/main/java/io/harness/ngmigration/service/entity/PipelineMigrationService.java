/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.NGMigrationConstants.RUNTIME_INPUT;

import static software.wings.ngmigration.NGMigrationEntityType.ENVIRONMENT;
import static software.wings.ngmigration.NGMigrationEntityType.PIPELINE;
import static software.wings.ngmigration.NGMigrationEntityType.SERVICE;
import static software.wings.ngmigration.NGMigrationEntityType.WORKFLOW;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ngmigration.beans.MigrationContext;
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
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.MigrationTemplateUtils;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.step.ApprovalStepMapperImpl;
import io.harness.ngmigration.service.workflow.WorkflowHandler;
import io.harness.ngmigration.utils.CaseFormat;
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
import io.harness.when.beans.StageWhenCondition;
import io.harness.when.beans.WhenConditionStatus;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
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
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class PipelineMigrationService extends NgMigrationService {
  private static final Pattern VARIABLE_PATTERN = Pattern.compile("[a-zA-Z_]+\\w*");

  @Inject private PipelineService pipelineService;
  @Inject private WorkflowMigrationService workflowMigrationService;
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private MigrationTemplateUtils migrationTemplateUtils;
  @Inject PipelineServiceClient pipelineServiceClient;
  @Inject ApprovalStepMapperImpl approvalStepMapper;
  @Inject private WorkflowService workflowService;

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
            Workflow workflow = workflowService.readWorkflow(pipeline.getAppId(), workflowId);
            if (workflow != null) {
              Map<String, String> workflowVariables = MapUtils.emptyIfNull(stageElement.getWorkflowVariables());
              Map<String, Variable> userVariables =
                  ListUtils.emptyIfNull(workflow.getOrchestration().getUserVariables())
                      .stream()
                      .collect(Collectors.toMap(Variable::getName, Function.identity()));

              children.addAll(
                  workflowVariables.entrySet()
                      .stream()
                      .filter(entry -> userVariables.get(entry.getKey()) != null)
                      .filter(entry -> userVariables.get(entry.getKey()).obtainEntityType() != null)
                      .filter(entry
                          -> Lists.newArrayList(EntityType.SERVICE, EntityType.ENVIRONMENT)
                                 .contains(userVariables.get(entry.getKey()).obtainEntityType()))
                      .map(entry
                          -> CgEntityId.builder()
                                 .id(entry.getValue())
                                 .type(userVariables.get(entry.getKey()).obtainEntityType() != EntityType.SERVICE
                                         ? ENVIRONMENT
                                         : SERVICE)
                                 .build())
                      .collect(Collectors.toList()));
            }
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
      String yaml = getYamlString(yamlFile);
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
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    if (EmptyPredicate.isNotEmpty(inputDTO.getDefaults()) && inputDTO.getDefaults().containsKey(PIPELINE)
        && inputDTO.getDefaults().get(PIPELINE).isSkipMigration()) {
      return null;
    }
    Pipeline pipeline = (Pipeline) entities.get(entityId).getEntity();
    if (EmptyPredicate.isEmpty(pipeline.getPipelineStages())) {
      return YamlGenerationDetails.builder()
          .skipDetails(Collections.singletonList(NGSkipDetail.builder()
                                                     .reason("The pipeline has no stages")
                                                     .cgBasicInfo(pipeline.getCgBasicInfo())
                                                     .type(PIPELINE)
                                                     .build()))
          .build();
    }

    MigratorExpressionUtils.render(migrationContext, pipeline, new HashMap<>());
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, pipeline.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(
        inputDTO.getOverrides(), entityId, name, inputDTO.getIdentifierCaseFormat());
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    String description = StringUtils.isBlank(pipeline.getDescription()) ? "" : pipeline.getDescription();

    List<StageElementWrapperConfig> ngStages = new ArrayList<>();
    List<StageElementWrapperConfig> parallelStages = null;
    List<NGVariable> pipelineVariables = getPipelineVariables(migrationContext, pipeline);
    for (int i = 0; i < pipeline.getPipelineStages().size(); ++i) {
      PipelineStage pipelineStage = pipeline.getPipelineStages().get(i);
      if (!isPartOfParallelStage(pipeline.getPipelineStages(), i)) {
        if (EmptyPredicate.isNotEmpty(parallelStages)) {
          ngStages.add(StageElementWrapperConfig.builder().parallel(JsonPipelineUtils.asTree(parallelStages)).build());
        }
        parallelStages = null;
      } else if (parallelStages == null) {
        parallelStages = new ArrayList<>();
      }
      for (PipelineStageElement stageElement : pipelineStage.getPipelineStageElements()) {
        StageElementWrapperConfig stage = null;
        if (StateType.ENV_STATE.name().equals(stageElement.getType())) {
          String workflowId = (String) stageElement.getProperties().get("workflowId");
          if (isNotEmpty(workflowId)) {
            NGSkipDetail skipDetail = getSkipDetailForWorkflowStage(pipeline, stageElement, migratedEntities);
            if (skipDetail != null) {
              return YamlGenerationDetails.builder().skipDetails(Collections.singletonList(skipDetail)).build();
            }
            stage = buildWorkflowStage(
                migrationContext, pipeline.getAccountId(), stageElement, inputDTO.getIdentifierCaseFormat());
          }
        } else {
          stage = buildApprovalStage(migrationContext, stageElement, inputDTO.getIdentifierCaseFormat());
        }
        // If the stage cannot be migrated then we skip building the pipeline.
        if (stage == null) {
          return YamlGenerationDetails.builder()
              .skipDetails(Collections.singletonList(
                  NGSkipDetail.builder()
                      .reason(String.format("Could not migrate one of the stages in the pipeline. Stage name - %s",
                          stageElement.getName()))
                      .cgBasicInfo(pipeline.getCgBasicInfo())
                      .type(PIPELINE)
                      .build()))
              .build();
        }
        Objects.requireNonNullElse(parallelStages, ngStages).add(stage);
      }
    }
    if (EmptyPredicate.isNotEmpty(parallelStages)) {
      ngStages.add(StageElementWrapperConfig.builder().parallel(JsonPipelineUtils.asTree(parallelStages)).build());
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
                                              .variables(pipelineVariables)
                                              .tags(MigratorUtility.getTags(pipeline.getTagLinks()))
                                              .build())
                      .build())
            .ngEntityDetail(NgEntityDetail.builder()
                                .entityType(PIPELINE)
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

  private boolean isPartOfParallelStage(List<PipelineStage> stages, int index) {
    PipelineStage currentStage = stages.get(index);
    if (currentStage.isParallel()) {
      return true;
    }
    if (index + 1 < stages.size()) {
      PipelineStage nextStage = stages.get(index + 1);
      return nextStage.isParallel();
    }
    return false;
  }

  private StageElementWrapperConfig buildApprovalStage(
      MigrationContext migrationContext, PipelineStageElement stageElement, CaseFormat caseFormat) {
    AbstractStepNode stepNode = approvalStepMapper.getSpec(stageElement, caseFormat);
    ExecutionWrapperConfig stepWrapper =
        ExecutionWrapperConfig.builder().step(JsonPipelineUtils.asTree(stepNode)).build();

    ApprovalStageNode approvalStageNode = new ApprovalStageNode();
    approvalStageNode.setName(MigratorUtility.generateName(stageElement.getName()));
    approvalStageNode.setIdentifier(MigratorUtility.generateIdentifier(stageElement.getName(), caseFormat));
    approvalStageNode.setApprovalStageConfig(
        ApprovalStageConfig.builder()
            .execution(ExecutionElementConfig.builder().steps(Collections.singletonList(stepWrapper)).build())
            .build());
    approvalStageNode.setFailureStrategies(WorkflowHandler.getDefaultFailureStrategy());

    Map<String, Object> properties = MapUtils.emptyIfNull(stageElement.getProperties());
    String assertion = (String) properties.get("disableAssertion");
    if (StringUtils.isNotBlank(assertion)) {
      assertion = (String) MigratorExpressionUtils.render(migrationContext, assertion, new HashMap<>());
      approvalStageNode.setWhen(ParameterField.createValueField(StageWhenCondition.builder()
                                                                    .pipelineStatus(WhenConditionStatus.SUCCESS)
                                                                    .condition(WorkflowHandler.wrapNot(assertion))
                                                                    .build()));
    }
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

  private List<NGVariable> getPipelineVariables(MigrationContext migrationContext, Pipeline pipeline) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    if (EmptyPredicate.isEmpty(pipeline.getPipelineStages())) {
      return new ArrayList<>();
    }

    List<PipelineStageElement> stageElements =
        pipeline.getPipelineStages()
            .stream()
            .filter(ps -> ps != null && EmptyPredicate.isNotEmpty(ps.getPipelineStageElements()))
            .flatMap(ps -> ps.getPipelineStageElements().stream())
            .filter(ps -> EmptyPredicate.isNotEmpty(ps.getWorkflowVariables()))
            .filter(ps -> StateType.ENV_STATE.name().equals(ps.getType()))
            .collect(Collectors.toList());

    List<String> toSkip = new ArrayList<>();
    Map<String, Variable> workflowVariables = new HashMap<>();
    for (PipelineStageElement stageElement : stageElements) {
      String workflowId = stageElement.getProperties().get("workflowId").toString();
      CgEntityId workflowEntityId = CgEntityId.builder().id(workflowId).type(WORKFLOW).build();
      if (entities.containsKey(workflowEntityId)) {
        Workflow workflow = (Workflow) entities.get(workflowEntityId).getEntity();
        CanaryOrchestrationWorkflow orchestrationWorkflow =
            (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
        if (EmptyPredicate.isEmpty(orchestrationWorkflow.getWorkflowPhases())) {
          continue;
        }
        WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
        String serviceExpression = getExpression(workflowPhase, "serviceId");
        String env = workflow.getEnvId();
        String infra = getExpression(workflowPhase, "infraDefinitionId");
        if (StringUtils.isNotBlank(serviceExpression)) {
          toSkip.add(serviceExpression);
        }
        if (StringUtils.isNotBlank(env)) {
          toSkip.add(env);
        }
        if (StringUtils.isNotBlank(infra)) {
          toSkip.add(infra);
        }
        if (EmptyPredicate.isNotEmpty(orchestrationWorkflow.getUserVariables())) {
          workflowVariables.putAll(orchestrationWorkflow.getUserVariables().stream().collect(
              Collectors.toMap(Variable::getName, Function.identity())));
        }
      }
    }

    Map<String, NGVariable> pipelineVariables = new HashMap<>();
    stageElements.stream()
        .map(PipelineStageElement::getWorkflowVariables)
        .flatMap(variables -> variables.entrySet().stream())
        .filter(entry -> StringUtils.isNotBlank(entry.getValue()) && isExpression(entry.getValue()))
        .filter(entry -> !toSkip.contains(entry.getKey()))
        .forEach(entry -> {
          String pipelineVar = entry.getValue().substring(2, entry.getValue().length() - 1);
          Variable wfVar = workflowVariables.get(entry.getKey());
          if (VARIABLE_PATTERN.matcher(pipelineVar).matches() && wfVar != null) {
            pipelineVariables.put(pipelineVar,
                StringNGVariable.builder()
                    .type(NGVariableType.STRING)
                    .required(wfVar.isMandatory())
                    .defaultValue(wfVar.getValue())
                    .name(pipelineVar)
                    .value(WorkflowHandler.getVariable(wfVar))
                    .build());
          }
        });

    return Lists.newArrayList(pipelineVariables.values());
  }

  private StageElementWrapperConfig buildWorkflowStage(
      MigrationContext migrationContext, String accountId, PipelineStageElement stageElement, CaseFormat caseFormat) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    // TODO: Handle Skip condition
    String workflowId = stageElement.getProperties().get("workflowId").toString();
    // Throw error if the stage is using canary or multi WFs.
    CgEntityId workflowEntityId = CgEntityId.builder().id(workflowId).type(WORKFLOW).build();
    if (!migratedEntities.containsKey(workflowEntityId) || !entities.containsKey(workflowEntityId)) {
      log.error("The workflow was not migrated, aborting pipeline migration {}", workflowId);
      return null;
    }

    NGYamlFile wfTemplate = migratedEntities.get(workflowEntityId);
    Workflow workflow = (Workflow) entities.get(workflowEntityId).getEntity();

    String stageServiceRef = RUNTIME_INPUT;
    JsonNode serviceInputs = null;
    String serviceId = getServiceId(workflow, stageElement);
    if (StringUtils.isNotBlank(serviceId)) {
      CgEntityId serviceEntityId = CgEntityId.builder().id(serviceId).type(SERVICE).build();
      if (migratedEntities.containsKey(serviceEntityId)) {
        NgEntityDetail serviceDetails = migratedEntities.get(serviceEntityId).getNgEntityDetail();
        stageServiceRef = MigratorUtility.getIdentifierWithScope(serviceDetails);
        serviceInputs = migrationTemplateUtils.getServiceInput(serviceDetails, accountId);
        if (serviceInputs != null) {
          serviceInputs = serviceInputs.get("serviceInputs");
        }
      }
    }

    String stageEnvRef = RUNTIME_INPUT;
    String envId = getEnvId(workflow, stageElement);
    if (StringUtils.isNotBlank(envId)) {
      CgEntityId envEntityId = CgEntityId.builder().id(envId).type(ENVIRONMENT).build();
      if (migratedEntities.containsKey(envEntityId)) {
        stageEnvRef = MigratorUtility.getIdentifierWithScope(migratedEntities.get(envEntityId).getNgEntityDetail());
      }
    }

    if (wfTemplate.getYaml() instanceof PipelineConfig) {
      PipelineInfoConfig pipelineConfig = ((PipelineConfig) wfTemplate.getYaml()).getPipelineInfoConfig();
      PipelineStageConfig pipelineStageConfig = PipelineStageConfig.builder()
                                                    .pipeline(pipelineConfig.getIdentifier())
                                                    .project(pipelineConfig.getProjectIdentifier())
                                                    .org(pipelineConfig.getOrgIdentifier())
                                                    .build();
      PipelineStageNode stageNode = new PipelineStageNode();
      stageNode.setName(MigratorUtility.generateName(stageElement.getName()));
      stageNode.setIdentifier(MigratorUtility.generateIdentifier(stageElement.getName(), caseFormat));
      stageNode.setDescription(ParameterField.createValueField(""));
      stageNode.setPipelineStageConfig(pipelineStageConfig);

      return StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(stageNode)).build();
    }

    NGTemplateConfig wfTemplateConfig = (NGTemplateConfig) wfTemplate.getYaml();
    if (TemplateEntityType.PIPELINE_TEMPLATE.equals(wfTemplateConfig.getTemplateInfoConfig().getType())) {
      log.warn("Cannot link a multi-service WFs as they are created as pipeline templates");
      return null;
    }

    JsonNode templateInputs = migrationTemplateUtils.getTemplateInputs(wfTemplate.getNgEntityDetail(), accountId);

    Map<String, String> workflowVariables = stageElement.getWorkflowVariables();
    // Set common runtime inputs
    if (templateInputs != null) {
      String whenInput = templateInputs.at("/when/condition").asText();
      if (RUNTIME_INPUT.equals(whenInput)) {
        String when = "true";
        Map<String, Object> properties = stageElement.getProperties();
        if (EmptyPredicate.isNotEmpty(properties) && properties.containsKey("disabled")) {
          boolean disabled = (Boolean) properties.get("disabled");
          if (Boolean.TRUE.equals(disabled)) {
            when = "false";
          }
        }
        if (EmptyPredicate.isNotEmpty(properties) && properties.containsKey("disableAssertion")) {
          String assertion = (String) properties.get("disableAssertion");
          if (StringUtils.isNotBlank(assertion)) {
            assertion = (String) MigratorExpressionUtils.render(migrationContext, assertion, new HashMap<>());
            when = WorkflowHandler.wrapNot(assertion).getValue();
          }
          ObjectNode whenNode = (ObjectNode) templateInputs.get("when");
          whenNode.put("condition", when);
        }
      }
      ArrayNode variablesArray = (ArrayNode) templateInputs.get("variables");
      if (EmptyPredicate.isNotEmpty(workflowVariables) && !EmptyPredicate.isEmpty(variablesArray)) {
        for (JsonNode node : variablesArray) {
          String key = node.get("name").asText();
          String value = workflowVariables.get(key);
          if (isExpression(value)) {
            String pipelineVar = value.substring(2, value.length() - 1);
            if (VARIABLE_PATTERN.matcher(pipelineVar).matches()) {
              ((ObjectNode) node).put("value", "<+pipeline.variables." + pipelineVar + ">");
            }
          }
          if (!isExpression(value) && StringUtils.isNotBlank(value)) {
            ((ObjectNode) node).put("value", value);
          }
        }
      }
    }

    // Set Deployment specific runtime inputs
    if (templateInputs != null && "Deployment".equals(templateInputs.get("type").asText())) {
      String serviceRef = templateInputs.at("/spec/service/serviceRef").asText();
      if (RUNTIME_INPUT.equals(serviceRef) && !RUNTIME_INPUT.equals(stageServiceRef)) {
        ObjectNode service = (ObjectNode) templateInputs.get("spec").get("service");
        service.put("serviceRef", stageServiceRef);
        if (serviceInputs == null) {
          service.remove("serviceInputs");
        } else {
          service.set("serviceInputs", serviceInputs);
        }
      }
      String envRef = templateInputs.at("/spec/environment/environmentRef").asText();
      if (RUNTIME_INPUT.equals(envRef)) {
        ObjectNode environment = (ObjectNode) templateInputs.get("spec").get("environment");
        environment.put("environmentRef", stageEnvRef);
        environment.remove("environmentInputs");
      }
    }
    TemplateLinkConfig templateLinkConfig = new TemplateLinkConfig();
    templateLinkConfig.setTemplateRef(MigratorUtility.getIdentifierWithScope(wfTemplate.getNgEntityDetail()));
    templateLinkConfig.setTemplateInputs(templateInputs);

    TemplateStageNode templateStageNode = new TemplateStageNode();
    templateStageNode.setName(MigratorUtility.generateName(stageElement.getName()));
    templateStageNode.setIdentifier(MigratorUtility.generateIdentifier(stageElement.getName(), caseFormat));
    templateStageNode.setDescription("");
    templateStageNode.setTemplate(templateLinkConfig);

    return StageElementWrapperConfig.builder().stage(JsonPipelineUtils.asTree(templateStageNode)).build();
  }

  private String getServiceId(Workflow workflow, PipelineStageElement stageElement) {
    if (workflow == null || EmptyPredicate.isEmpty(workflow.getServices())) {
      return null;
    }
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    if (orchestrationWorkflow.getOrchestrationWorkflowType().equals(OrchestrationWorkflowType.MULTI_SERVICE)
        || EmptyPredicate.isEmpty(orchestrationWorkflow.getWorkflowPhases())) {
      return null;
    }
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    String serviceExpression = getExpression(workflowPhase, "serviceId");
    Map<String, String> workflowVariables = MapUtils.emptyIfNull(stageElement.getWorkflowVariables());
    if (StringUtils.isBlank(serviceExpression)) {
      return workflowPhase.getServiceId();
    }
    String serviceId = workflowVariables.get(serviceExpression);
    if (StringUtils.isNotBlank(serviceId) && !isExpression(serviceId)) {
      return serviceId;
    }
    return null;
  }

  private String getEnvId(Workflow workflow, PipelineStageElement stageElement) {
    if (workflow == null) {
      return null;
    }
    if (!workflow.isEnvTemplatized()) {
      return workflow.getEnvId();
    }
    String envExpression = workflow.fetchEnvTemplatizedName();
    Map<String, String> workflowVariables = MapUtils.emptyIfNull(stageElement.getWorkflowVariables());
    String envId = workflowVariables.get(envExpression);
    if (StringUtils.isNotBlank(envId) && !isExpression(envId)) {
      return envId;
    }
    return null;
  }

  private String getInfra(Workflow workflow, PipelineStageElement stageElement) {
    if (workflow == null) {
      return null;
    }
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    if (EmptyPredicate.isEmpty(orchestrationWorkflow.getWorkflowPhases())
        || orchestrationWorkflow.getWorkflowPhases().size() > 1) {
      return null;
    }
    WorkflowPhase workflowPhase = orchestrationWorkflow.getWorkflowPhases().get(0);
    String infraExpression = getExpression(workflowPhase, "infraDefinitionId");
    Map<String, String> workflowVariables = MapUtils.emptyIfNull(stageElement.getWorkflowVariables());
    if (StringUtils.isBlank(infraExpression)) {
      return workflowPhase.getInfraDefinitionId();
    }
    String infraId = workflowVariables.get(infraExpression);
    if (StringUtils.isNotBlank(infraId) && !isExpression(infraId)) {
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
        .filter(PipelineMigrationService::isExpression)
        .map(te -> te.substring(2, te.length() - 1))
        .findFirst()
        .orElse(null);
  }

  private static boolean isExpression(String str) {
    if (StringUtils.isBlank(str)) {
      return false;
    }
    return str.startsWith("${") && str.endsWith("}");
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
