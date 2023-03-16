/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.importer;

import static software.wings.ngmigration.NGMigrationEntityType.WORKFLOW;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ResponseDTO;
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
import io.harness.ngmigration.service.MigrationTemplateUtils;
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
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.yaml.TemplateLinkConfig;
import io.harness.yaml.utils.JsonPipelineUtils;

import software.wings.beans.Base;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class WorkflowImportService implements ImportService {
  @Inject DiscoveryService discoveryService;
  @Inject HPersistence hPersistence;
  @Inject private MigrationTemplateUtils migrationTemplateUtils;
  @Inject @Named("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceClientConfig;

  public DiscoveryResult discover(String authToken, ImportDTO importConnectorDTO) {
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
    createWorkflowsAsPipeline(authToken, importDTO, discoveryResult, summaryDTO);
  }

  public void createWorkflowsAsPipeline(
      String authToken, ImportDTO importDTO, DiscoveryResult discoveryResult, SaveSummaryDTO summaryDTO) {
    MigrationInputDTO inputDTO = MigratorUtility.getMigrationInput(importDTO);
    List<MigratedDetails> stageTemplates = new ArrayList<>();
    stageTemplates.addAll(extractStageTemplates(summaryDTO.getNgYamlFiles(), summaryDTO.getAlreadyMigratedDetails()));
    stageTemplates.addAll(
        extractStageTemplates(summaryDTO.getNgYamlFiles(), summaryDTO.getSuccessfullyMigratedDetails()));

    for (MigratedDetails migratedDetails : stageTemplates) {
      CgEntityId cgEntityId =
          CgEntityId.builder().type(WORKFLOW).id(migratedDetails.getCgEntityDetail().getId()).build();
      if (discoveryResult.getEntities().containsKey(cgEntityId)) {
        Workflow workflow = (Workflow) discoveryResult.getEntities().get(cgEntityId).getEntity();
        PipelineConfig config = getPipelineConfig(workflow, inputDTO, migratedDetails.getNgEntityDetail());
        createPipeline(authToken, inputDTO, config);
      }
    }
  }

  private void createPipeline(String auth, MigrationInputDTO inputDTO, PipelineConfig pipelineConfig) {
    PmsClient pmsClient = MigratorUtility.getRestClient(pipelineServiceClientConfig, PmsClient.class);
    String yaml = YamlUtils.write(pipelineConfig);
    try {
      Response<ResponseDTO<PipelineSaveResponse>> resp =
          pmsClient
              .createPipeline(auth, inputDTO.getAccountIdentifier(), inputDTO.getOrgIdentifier(),
                  inputDTO.getProjectIdentifier(), RequestBody.create(MediaType.parse("application/yaml"), yaml))
              .execute();
      log.info("Workflow as pipeline creation Response details {} {}", resp.code(), resp.message());
      if (resp.code() >= 400) {
        log.info("Workflows as pipeline template is \n - {}", yaml);
      }
      if (resp.code() >= 200 && resp.code() < 300) {
        return;
      }
      log.info("The Yaml of the generated data was - {}", yaml);
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
      Workflow workflow, MigrationInputDTO inputDTO, NgEntityDetail ngEntityDetail) {
    String name = MigratorUtility.generateName(workflow.getName());
    String identifier = MigratorUtility.generateIdentifier(workflow.getName(), inputDTO.getIdentifierCaseFormat());
    Scope scope = Scope.PROJECT;
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    String description = String.format("Pipeline generated from a First Gen Workflow - %s", workflow.getName());

    TemplateLinkConfig templateLinkConfig = new TemplateLinkConfig();
    templateLinkConfig.setTemplateRef(MigratorUtility.getIdentifierWithScope(ngEntityDetail));
    templateLinkConfig.setTemplateInputs(
        migrationTemplateUtils.getTemplateInputs(ngEntityDetail, workflow.getAccountId()));

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
}
