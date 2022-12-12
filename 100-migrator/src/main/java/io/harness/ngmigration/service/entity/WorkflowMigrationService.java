/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.ngmigration.NGMigrationEntityType.WORKFLOW;
import static software.wings.sm.StepType.K8S_DEPLOYMENT_ROLLING;
import static software.wings.sm.StepType.SHELL_SCRIPT;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.WorkflowSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.step.StepMapperFactory;
import io.harness.ngmigration.service.workflow.WorkflowHandler;
import io.harness.ngmigration.service.workflow.WorkflowHandlerFactory;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.beans.yaml.NGTemplateInfoConfig;

import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.service.impl.yaml.handler.workflow.RollingWorkflowYamlHandler;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StepType;
import software.wings.yaml.workflow.RollingWorkflowYaml;
import software.wings.yaml.workflow.StepYaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.fabric8.utils.Lists;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
  @Inject private InfraMigrationService infraMigrationService;
  @Inject private EnvironmentMigrationService environmentMigrationService;
  @Inject private ServiceMigrationService serviceMigrationService;
  @Inject private WorkflowService workflowService;
  @Inject private RollingWorkflowYamlHandler rollingWorkflowYamlHandler;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private StepMapperFactory stepMapperFactory;
  @Inject private WorkflowHandlerFactory workflowHandlerFactory;

  private static final List<String> SUPPORTED_STEPS = Lists.newArrayList(K8S_DEPLOYMENT_ROLLING, SHELL_SCRIPT)
                                                          .stream()
                                                          .map(StepType::name)
                                                          .collect(Collectors.toList());

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    // TODO: @deepak
    return null;
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
    Map<String, Long> summaryByStepTyp = entities.stream()
                                             .flatMap(entity -> {
                                               Workflow workflow = (Workflow) entity.getEntity();
                                               WorkflowHandler workflowHandler =
                                                   workflowHandlerFactory.getWorkflowHandler(workflow);
                                               return workflowHandler.getSteps(workflow).stream();
                                             })
                                             .collect(groupingBy(GraphNode::getType, counting()));

    return WorkflowSummary.builder()
        .count(entities.size())
        .typeSummary(summaryByType)
        .stepTypeSummary(summaryByStepTyp)
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
    //    if (EmptyPredicate.isNotEmpty(workflow.getServices())) {
    //      Set<CgEntityId> set = new HashSet<>();
    //      for (Service service : workflow.getServices()) {
    //        CgEntityId build = CgEntityId.builder().type(SERVICE).id(service.getUuid()).build();
    //        set.add(build);
    //        List<ApplicationManifest> applicationManifests =
    //            applicationManifestService.listAppManifests(workflow.getAppId(), service.getUuid());
    //        if (isNotEmpty(applicationManifests)) {
    //          applicationManifests.stream()
    //              .map(applicationManifest ->
    //              CgEntityId.builder().id(applicationManifest.getUuid()).type(MANIFEST).build())
    //              .forEach(children::add);
    //        }
    //      }
    //      children.addAll(set);
    //    }
    //
    //    if (EmptyPredicate.isNotEmpty(workflow.getEnvId())) {
    //      children.add(CgEntityId.builder().type(ENVIRONMENT).id(workflow.getEnvId()).build());
    //      List<ApplicationManifest> applicationManifests =
    //          applicationManifestService.getAllByEnvId(workflow.getAppId(), workflow.getEnvId());
    //      if (isNotEmpty(applicationManifests)) {
    //        Set<String> serviceIds = CollectionUtils.emptyIfNull(workflow.getServices())
    //                                     .stream()
    //                                     .map(Service::getUuid)
    //                                     .collect(Collectors.toSet());
    //        for (ApplicationManifest applicationManifest : applicationManifests) {
    //          if (applicationManifest.getServiceId() == null ||
    //          serviceIds.contains(applicationManifest.getServiceId())) {
    //            CgEntityId build = CgEntityId.builder().id(applicationManifest.getUuid()).type(MANIFEST).build();
    //            children.add(build);
    //          }
    //        }
    //      }
    //    }
    //    if (EmptyPredicate.isNotEmpty(workflow.getInfraDefinitionId())) {
    //      children.add(CgEntityId.builder().type(INFRA).id(workflow.getInfraDefinitionId()).build());
    //    }
    return DiscoveryNode.builder().children(children).entityNode(workflowNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(workflowService.readWorkflow(appId, entityId));
  }

  @Override
  public NGMigrationStatus canMigrate(NGMigrationEntity entity) {
    Workflow workflow = (Workflow) entity;
    OrchestrationWorkflowType workflowType = workflow.getOrchestration().getOrchestrationWorkflowType();
    if (OrchestrationWorkflowType.ROLLING.equals(workflowType)) {
      RollingWorkflowYaml rollingWorkflowYaml = rollingWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
      if (EmptyPredicate.isEmpty(rollingWorkflowYaml.getPhases())) {
        return NGMigrationStatus.builder()
            .status(false)
            .reasons(Collections.singletonList(String.format("No phases in workflow %s", workflow.getName())))
            .build();
      }
      List<WorkflowPhase.Yaml> phases = rollingWorkflowYaml.getPhases();
      List<StepYaml> stepYamls = phases.stream()
                                     .filter(phase -> isNotEmpty(phase.getPhaseSteps()))
                                     .flatMap(phase -> phase.getPhaseSteps().stream())
                                     .filter(phaseStep -> isNotEmpty(phaseStep.getSteps()))
                                     .flatMap(phaseStep -> phaseStep.getSteps().stream())
                                     .collect(Collectors.toList());
      if (isEmpty(stepYamls)) {
        return NGMigrationStatus.builder()
            .status(false)
            .reasons(Collections.singletonList(String.format("No steps in workflow %s", workflow.getName())))
            .build();
      }
      List<String> errorReasons = new ArrayList<>();
      stepYamls.stream()
          .filter(stepYaml -> !SUPPORTED_STEPS.contains(stepYaml.getType()))
          .forEach(stepYaml
              -> errorReasons.add(
                  String.format("%s step of %s step type in workflow %s is not supported with migration",
                      stepYaml.getName(), stepYaml.getType(), workflow.getName())));
      boolean possible = errorReasons.isEmpty();
      return NGMigrationStatus.builder().status(possible).reasons(errorReasons).build();
    }
    return NGMigrationStatus.builder()
        .status(false)
        .reasons(Collections.singletonList(
            String.format("Workflow %s of type %s is not supported with migration", workflow.getName(), workflowType)))
        .build();
  }

  @Override
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    if (EmptyPredicate.isNotEmpty(inputDTO.getDefaults()) && inputDTO.getDefaults().containsKey(WORKFLOW)
        && inputDTO.getDefaults().get(WORKFLOW).isSkipMigration()) {
      return Collections.emptyList();
    }
    Workflow workflow = (Workflow) entities.get(entityId).getEntity();
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, workflow.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(inputDTO.getOverrides(), entityId, name);
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    String description = StringUtils.isBlank(workflow.getDescription()) ? "" : workflow.getDescription();

    WorkflowHandler workflowHandler = workflowHandlerFactory.getWorkflowHandler(workflow);

    JsonNode templateSpec = workflowHandler.getTemplateSpec(workflow);
    if (templateSpec == null) {
      return Collections.emptyList();
    }

    List<NGYamlFile> files = new ArrayList<>();
    NGYamlFile ngYamlFile =
        NGYamlFile.builder()
            .type(WORKFLOW)
            .filename("workflows/" + name + ".yaml")
            .yaml(NGTemplateConfig.builder()
                      .templateInfoConfig(NGTemplateInfoConfig.builder()
                                              .type(workflowHandler.getTemplateType(workflow))
                                              .identifier(identifier)
                                              .name(name)
                                              .description(ParameterField.createValueField(description))
                                              .projectIdentifier(projectIdentifier)
                                              .orgIdentifier(orgIdentifier)
                                              .versionLabel("v1")
                                              .spec(templateSpec)
                                              .build())
                      .build())
            .ngEntityDetail(NgEntityDetail.builder()
                                .identifier(identifier)
                                .orgIdentifier(orgIdentifier)
                                .projectIdentifier(projectIdentifier)
                                .build())
            .cgBasicInfo(workflow.getCgBasicInfo())
            .build();
    files.add(ngYamlFile);
    migratedEntities.putIfAbsent(entityId, ngYamlFile);
    return files;
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    if (yamlFile.isExists()) {
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(ImportError.builder()
                                                .message("Workflow was not migrated as it was already imported before")
                                                .entity(yamlFile.getCgBasicInfo())
                                                .build()))
          .build();
    }
    String yaml = YamlUtils.write(yamlFile.getYaml());
    Response<ResponseDTO<ConnectorResponseDTO>> resp =
        templateClient
            .createTemplate(auth, inputDTO.getAccountIdentifier(), inputDTO.getOrgIdentifier(),
                inputDTO.getProjectIdentifier(), RequestBody.create(MediaType.parse("application/yaml"), yaml))
            .execute();
    log.info("Workflow creation Response details {} {}", resp.code(), resp.message());
    if (resp.code() >= 400) {
      log.info("The WF template is \n - {}", yaml);
    }
    return handleResp(yamlFile, resp);
  }

  @Override
  protected YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }
}
