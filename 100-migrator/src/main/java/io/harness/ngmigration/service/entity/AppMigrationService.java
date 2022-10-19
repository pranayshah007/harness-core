/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.encryption.Scope.PROJECT;

import static software.wings.ngmigration.NGMigrationEntityType.APPLICATION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.persistence.HPersistence;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.JsonUtils;

import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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

  @Inject private MigratorExpressionUtils migratorExpressionUtils;

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

    // For now we will not discover pipelines.
    //    List<Pipeline> pipelines = hPersistence.createQuery(Pipeline.class)
    //                                   .filter(PipelineKeys.accountId, application.getAccountId())
    //                                   .filter(PipelineKeys.appId, appId)
    //                                   .project(PipelineKeys.uuid, true)
    //                                   .asList();
    //    children.addAll(pipelines.stream()
    //                        .map(Pipeline::getUuid)
    //                        .distinct()
    //                        .map(id -> CgEntityId.builder().id(id).type(NGMigrationEntityType.PIPELINE).build())
    //                        .collect(Collectors.toSet()));

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
  public NGMigrationStatus canMigrate(NGMigrationEntity entity) {
    return NGMigrationStatus.builder().status(true).build();
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
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
              .put("fixedValue", (String) migratorExpressionUtils.render(value, inputDTO.getCustomExpressions()))
              .build();
      Map<String, Object> variable =
          ImmutableMap.<String, Object>builder()
              .put(YAMLFieldNameConstants.NAME, name)
              .put(YAMLFieldNameConstants.IDENTIFIER, MigratorUtility.generateIdentifier(name))
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

  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    Application application = (Application) entities.get(entityId).getEntity();
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, application.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(inputDTO.getOverrides(), entityId, name);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(PROJECT, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(PROJECT, inputDTO);
    return Collections.singletonList(
        NGYamlFile.builder()
            .filename(String.format("application/%s/%s.yaml", application.getAppId(), application.getName()))
            .type(APPLICATION)
            .ngEntityDetail(NgEntityDetail.builder()
                                .identifier(identifier)
                                .orgIdentifier(orgIdentifier)
                                .projectIdentifier(projectIdentifier)
                                .build())
            .cgBasicInfo(application.getCgBasicInfo())
            .build());
  }

  @Override
  protected YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }

  @Override
  public BaseEntityInput generateInput(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {
    return null;
  }
}
