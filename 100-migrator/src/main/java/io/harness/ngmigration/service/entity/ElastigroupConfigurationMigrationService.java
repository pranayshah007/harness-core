/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.ngmigration.NGMigrationEntityType.ELASTIGROUP_CONFIGURATION;
import static software.wings.ngmigration.NGMigrationEntityType.WORKFLOW;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.expressions.step.StepExpressionFunctor;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.service.step.StepMapperFactory;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngmigration.utils.SecretRefUtils;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.PhaseStep;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowPhase;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ElastigroupConfigurationMigrationService extends NgMigrationService {
  @Inject InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private SecretRefUtils secretRefUtils;

  @Inject private StepMapperFactory stepMapperFactory;

  @Inject private WorkflowService workflowService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    return null;
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    InfrastructureDefinition infrastructureDefinition = (InfrastructureDefinition) entity;
    CgEntityId cgEntityId = CgEntityId.builder()
                                .id(infrastructureDefinition.getUuid())
                                .type(NGMigrationEntityType.ELASTIGROUP_CONFIGURATION)
                                .build();
    CgEntityNode cgEntityNode = CgEntityNode.builder()
                                    .entityId(cgEntityId)
                                    .entity(infrastructureDefinition)
                                    .appId(infrastructureDefinition.getAppId())
                                    .id(infrastructureDefinition.getUuid())
                                    .type(NGMigrationEntityType.ELASTIGROUP_CONFIGURATION)
                                    .build();
    Set<CgEntityId> children = new HashSet<>();
    children.addAll(secretRefUtils.getSecretRefFromExpressions(
        infrastructureDefinition.getAccountId(), MigratorExpressionUtils.getExpressions(infrastructureDefinition)));
    return DiscoveryNode.builder().entityNode(cgEntityNode).children(children).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(infrastructureDefinitionService.get(appId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    return migrateFile(ngClient, inputDTO, yamlFile);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    InfrastructureDefinition infrastructureDefinition = (InfrastructureDefinition) entities.get(entityId).getEntity();
    Map<String, Object> custom = updateContextVariables(migrationContext, entities, infrastructureDefinition);
    MigratorExpressionUtils.render(migrationContext, infrastructureDefinition, custom);
    NGYamlFile yamlFile = getYamlFile(infrastructureDefinition, inputDTO);
    if (yamlFile == null) {
      return null;
    }
    return YamlGenerationDetails.builder().yamlFileList(Collections.singletonList(yamlFile)).build();
  }

  private NGYamlFile getYamlFile(InfrastructureDefinition infrastructureDefinition, MigrationInputDTO inputDTO) {
    AwsAmiInfrastructure infrastructure = (AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure();
    if (StringUtils.isBlank(infrastructure.getSpotinstElastiGroupJson())) {
      return null;
    }
    byte[] fileContent = infrastructure.getSpotinstElastiGroupJson().getBytes(StandardCharsets.UTF_8);
    return getYamlFile(inputDTO, infrastructureDefinition, fileContent, infrastructureDefinition.getName());
  }

  private static NGYamlFile getYamlFile(
      MigrationInputDTO inputDTO, InfrastructureDefinition infrastructureDefinition, byte[] content, String infraName) {
    if (isEmpty(content)) {
      return null;
    }
    String prefix = infraName + ' ';
    String fileUsage = FileUsage.CONFIG.name();
    String projectIdentifier = MigratorUtility.getProjectIdentifier(Scope.PROJECT, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(Scope.PROJECT, inputDTO);
    String identifier = MigratorUtility.generateManifestIdentifier(
        prefix + "ElastigroupConfigurationSpec", inputDTO.getIdentifierCaseFormat());
    String name = identifier + ".json";
    return NGYamlFile.builder()
        .type(NGMigrationEntityType.ELASTIGROUP_CONFIGURATION)
        .filename(null)
        .yaml(FileYamlDTO.builder()
                  .identifier(identifier)
                  .fileUsage(fileUsage)
                  .name(name)
                  .content(new String(content))
                  .rootIdentifier("Root")
                  .depth(Integer.MAX_VALUE)
                  .filePath("")
                  .orgIdentifier(orgIdentifier)
                  .projectIdentifier(projectIdentifier)
                  .build())
        .ngEntityDetail(NgEntityDetail.builder()
                            .entityType(NGMigrationEntityType.FILE_STORE)
                            .identifier(identifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build())
        .cgBasicInfo(CgBasicInfo.builder()
                         .id(infrastructureDefinition.getUuid())
                         .name(infrastructureDefinition.getName())
                         .type(ELASTIGROUP_CONFIGURATION)
                         .appId(infrastructureDefinition.getAppId())
                         .accountId(infrastructureDefinition.getAccountId())
                         .build())
        .build();
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists(MigrationContext migrationContext) {
    return true;
  }

  public List<ElastigroupConfiguration> getElastigroupConfigurations(
      MigrationContext migrationContext, Set<CgEntityId> infraSpecIds) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    if (isEmpty(infraSpecIds)) {
      return new ArrayList<>();
    }
    List<ElastigroupConfiguration> elastigroupConfigurations = new ArrayList<>();
    for (CgEntityId configEntityId : infraSpecIds) {
      CgEntityNode configNode = entities.get(configEntityId);
      if (configNode != null) {
        InfrastructureDefinition infrastructureDefinition = (InfrastructureDefinition) configNode.getEntity();
        MigratorExpressionUtils.render(migrationContext, infrastructureDefinition, inputDTO.getCustomExpressions());
        NGYamlFile file = getYamlFile(infrastructureDefinition, inputDTO);
        if (file != null) {
          elastigroupConfigurations.add(getConfigFileWrapper(file));
        }
      }
    }
    return elastigroupConfigurations;
  }

  @Override
  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean migrateAll) {
    return migrateAll || root.getType() == NGMigrationEntityType.INFRA;
  }

  private static ElastigroupConfiguration getConfigFileWrapper(NGYamlFile file) {
    ParameterField<List<String>> files;
    files = MigratorUtility.getFileStorePaths(Collections.singletonList(file));
    return ElastigroupConfiguration.builder()
        .store(StoreConfigWrapper.builder()
                   .type(StoreConfigType.HARNESS)
                   .spec(HarnessStore.builder().files(files).build())
                   .build())
        .build();
  }

  private Map<String, Object> updateContextVariables(MigrationContext migrationContext,
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

  private void processPhase(Map<String, Object> custom, MigrationContext migrationContext,
      WorkflowMigrationContext wfContext, WorkflowPhase phase) {
    List<PhaseStep> phaseSteps = phase.getPhaseSteps();
    List<StepExpressionFunctor> expressionFunctorsFromCurrentPhase = new ArrayList<>();
    String prefix = phase.getName();

    phaseSteps.stream().filter(phaseStep -> isNotEmpty(phaseStep.getSteps())).forEach(phaseStep -> {
      List<GraphNode> steps = phaseStep.getSteps();
      String stepGroupName = prefix + "-" + phaseStep.getName();
      steps.forEach(stepYaml -> {
        StepMapper stepMapper = stepMapperFactory.getStepMapper(stepYaml.getType());
        List<StepExpressionFunctor> expressionFunctors =
            stepMapper.getExpressionFunctor(wfContext, phase, stepGroupName, stepYaml);
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
