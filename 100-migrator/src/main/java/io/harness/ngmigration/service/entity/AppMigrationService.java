/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.encryption.Scope.PROJECT;

import static software.wings.ngmigration.NGMigrationEntityType.APPLICATION;
import static software.wings.ngmigration.NGMigrationEntityType.INFRA_PROVISIONER;
import static software.wings.ngmigration.NGMigrationEntityType.MANIFEST;
import static software.wings.ngmigration.NGMigrationEntityType.TRIGGER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.beans.SearchFilter.Operator;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.importer.TemplateImportService;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.persistence.HPersistence;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.JsonUtils;

import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.EntityType;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.ServiceVariableKeys;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ApplicationManifest.ApplicationManifestKeys;
import software.wings.beans.template.Template;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceVariableService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class AppMigrationService extends NgMigrationService {
  @Inject private AppService appService;
  @Inject private HPersistence hPersistence;
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ServiceVariableService serviceVariableService;
  @Inject private TemplateImportService templateService;

  @Inject InfrastructureProvisionerService infrastructureProvisionerService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    return null;
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    Application application = (Application) entity;
    String appId = application.getUuid();

    Set<CgEntityId> children = new HashSet<>();

    List<Pipeline> pipelines = hPersistence.createQuery(Pipeline.class)
                                   .filter(PipelineKeys.accountId, application.getAccountId())
                                   .filter(PipelineKeys.appId, appId)
                                   .project(PipelineKeys.uuid, true)
                                   .asList();
    children.addAll(pipelines.stream()
                        .map(Pipeline::getUuid)
                        .distinct()
                        .map(id -> CgEntityId.builder().id(id).type(NGMigrationEntityType.PIPELINE).build())
                        .collect(Collectors.toSet()));

    List<Workflow> workflows = hPersistence.createQuery(Workflow.class)
                                   .filter(WorkflowKeys.accountId, application.getAccountId())
                                   .filter(WorkflowKeys.appId, appId)
                                   .project(WorkflowKeys.uuid, true)
                                   .asList();
    children.addAll(workflows.stream()
                        .map(Workflow::getUuid)
                        .distinct()
                        .map(id -> CgEntityId.builder().id(id).type(NGMigrationEntityType.WORKFLOW).build())
                        .collect(Collectors.toSet()));

    List<Service> services = serviceResourceService.findServicesByAppInternal(appId);
    if (EmptyPredicate.isNotEmpty(services)) {
      children.addAll(services.stream()
                          .map(Service::getUuid)
                          .distinct()
                          .map(id -> CgEntityId.builder().id(id).type(NGMigrationEntityType.SERVICE).build())
                          .collect(Collectors.toSet()));
    }

    List<String> environments = environmentService.getEnvIdsByApp(appId);
    if (EmptyPredicate.isNotEmpty(environments)) {
      children.addAll(environments.stream()
                          .distinct()
                          .map(id -> CgEntityId.builder().id(id).type(NGMigrationEntityType.ENVIRONMENT).build())
                          .collect(Collectors.toSet()));
    }

    List<Template> templates = templateService.getTemplatesList(application.getAccountId(), appId, null);
    if (EmptyPredicate.isNotEmpty(templates)) {
      children.addAll(
          templates.stream()
              .distinct()
              .map(template -> CgEntityId.builder().id(template.getUuid()).type(NGMigrationEntityType.TEMPLATE).build())
              .collect(Collectors.toSet()));
    }
    // We are filtering based on service template because service variables & environment types are handled with
    // Environment migration. These variables depend on both service & environment to be migrated.
    List<ServiceVariable> serviceVariables = serviceVariableService.list(
        aPageRequest()
            .addFilter(ServiceVariableKeys.appId, Operator.EQ, appId)
            .addFilter(ServiceVariableKeys.entityType, Operator.EQ, EntityType.SERVICE_TEMPLATE.name())
            .build());
    if (EmptyPredicate.isNotEmpty(serviceVariables)) {
      children.addAll(serviceVariables.stream()
                          .distinct()
                          .map(serviceVariable
                              -> CgEntityId.builder()
                                     .id(serviceVariable.getUuid())
                                     .type(NGMigrationEntityType.SERVICE_VARIABLE)
                                     .build())
                          .collect(Collectors.toList()));
    }

    List<ApplicationManifest> applicationManifests = hPersistence.createQuery(ApplicationManifest.class)
                                                         .filter(ApplicationKeys.appId, appId)
                                                         .field(ApplicationManifestKeys.serviceId)
                                                         .notEqual(null)
                                                         .field(ApplicationManifestKeys.envId)
                                                         .notEqual(null)
                                                         .asList();

    if (EmptyPredicate.isNotEmpty(applicationManifests)) {
      children.addAll(applicationManifests.stream()
                          .distinct()
                          .map(manifest -> CgEntityId.builder().id(manifest.getUuid()).type(MANIFEST).build())
                          .collect(Collectors.toList()));
    }

    // Infra Provisioners
    List<InfrastructureProvisioner> infrastructureProvisioners =
        hPersistence.createQuery(InfrastructureProvisioner.class)
            .filter(InfrastructureProvisioner.APP_ID, appId)
            .filter(InfrastructureProvisioner.ACCOUNT_ID_KEY, application.getAccountId())
            .asList();
    if (EmptyPredicate.isNotEmpty(infrastructureProvisioners)) {
      children.addAll(
          infrastructureProvisioners.stream()
              .distinct()
              .map(provisioner -> CgEntityId.builder().id(provisioner.getUuid()).type(INFRA_PROVISIONER).build())
              .collect(Collectors.toList()));
    }

    List<Trigger> triggers = hPersistence.createQuery(Trigger.class)
                                 .filter(TriggerKeys.appId, appId)
                                 .filter(TriggerKeys.accountId, application.getAccountId())
                                 .asList();
    if (EmptyPredicate.isNotEmpty(triggers)) {
      children.addAll(triggers.stream()
                          .distinct()
                          .map(trigger -> CgEntityId.builder().id(trigger.getUuid()).type(TRIGGER).build())
                          .collect(Collectors.toList()));
    }

    return DiscoveryNode.builder()
        .entityNode(CgEntityNode.builder()
                        .id(appId)
                        .appId(appId)
                        .type(NGMigrationEntityType.APPLICATION)
                        .entity(entity)
                        .entityId(CgEntityId.builder().type(NGMigrationEntityType.APPLICATION).id(appId).build())
                        .build())
        .children(children)
        .build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(appService.get(entityId));
  }
  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    Application application = appService.getApplicationWithDefaults(yamlFile.getCgBasicInfo().getAppId());

    if (EmptyPredicate.isEmpty(application.getDefaults())) {
      return MigrationImportSummaryDTO.builder().success(true).errors(Collections.emptyList()).build();
    }

    String projectIdentifier = MigratorUtility.getProjectIdentifier(PROJECT, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(PROJECT, inputDTO);

    boolean variableMigrationSuccess = true;
    List<ImportError> variableMigrationErrors = new ArrayList<>();
    for (Map.Entry<String, String> entry : application.getDefaults().entrySet()) {
      String name = entry.getKey();
      String value = entry.getValue();
      Map<String, String> variableSpec =
          ImmutableMap.<String, String>builder()
              .put("valueType", "FIXED")
              .put("fixedValue",
                  (String) MigratorExpressionUtils.render(new HashMap<>(), new HashMap<>(), value,
                      inputDTO.getCustomExpressions(), inputDTO.getIdentifierCaseFormat()))
              .build();
      Map<String, Object> variable =
          ImmutableMap.<String, Object>builder()
              .put(YAMLFieldNameConstants.NAME, name)
              .put(YAMLFieldNameConstants.IDENTIFIER,
                  MigratorUtility.generateIdentifier(name, inputDTO.getIdentifierCaseFormat()))
              .put(YAMLFieldNameConstants.ORG_IDENTIFIER, orgIdentifier)
              .put(YAMLFieldNameConstants.PROJECT_IDENTIFIER, projectIdentifier)
              .put(YAMLFieldNameConstants.TYPE, "String")
              .put(YAMLFieldNameConstants.SPEC, variableSpec)
              .build();
      Response<ResponseDTO<ConnectorResponseDTO>> resp = null;
      resp = ngClient
                 .createVariable(auth, inputDTO.getAccountIdentifier(),
                     JsonUtils.asTree(Collections.singletonMap("variable", variable)))
                 .execute();
      MigrationImportSummaryDTO variableMigrationSummaryDTO = handleResp(yamlFile, resp);
      variableMigrationSuccess = variableMigrationSuccess && variableMigrationSummaryDTO.isSuccess();
      variableMigrationErrors.addAll(variableMigrationSummaryDTO.getErrors());
      log.info("Application default creation for {} Response details {} {}", name, resp.code(), resp.message());
    }
    return MigrationImportSummaryDTO.builder()
        .success(variableMigrationSuccess)
        .errors(variableMigrationErrors)
        .build();
  }

  public YamlGenerationDetails generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    Application application = (Application) entities.get(entityId).getEntity();
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, application.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(
        inputDTO.getOverrides(), entityId, name, inputDTO.getIdentifierCaseFormat());
    String projectIdentifier = MigratorUtility.getProjectIdentifier(PROJECT, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(PROJECT, inputDTO);
    return YamlGenerationDetails.builder()
        .yamlFileList(Collections.singletonList(
            NGYamlFile.builder()
                .filename(String.format("application/%s/%s.yaml", application.getAppId(), application.getName()))
                .type(APPLICATION)
                .ngEntityDetail(NgEntityDetail.builder()
                                    .identifier(identifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build())
                .cgBasicInfo(application.getCgBasicInfo())
                .build()))
        .build();
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }
}
