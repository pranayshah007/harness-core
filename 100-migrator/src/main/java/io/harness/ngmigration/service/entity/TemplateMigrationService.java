/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;
import static software.wings.ngmigration.NGMigrationEntityType.TEMPLATE;

import io.harness.beans.MigratedEntityMapping;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.BaseInputDefinition;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.MigratorInputType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.connector.BaseConnector;
import io.harness.ngmigration.connector.ConnectorFactory;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.template.NgTemplateService;
import io.harness.ngmigration.template.TemplateFactory;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.JsonUtils;
import io.harness.template.beans.TemplateResponseDTO;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.YamlPipelineUtils;

import software.wings.beans.SettingAttribute;
import software.wings.beans.template.Template;
import software.wings.beans.template.command.HttpTemplate;
import software.wings.beans.template.command.ShellScriptTemplate;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
public class TemplateMigrationService extends NgMigrationService {
  @Inject TemplateService templateService;

  @Inject TemplateResourceClient templateResourceClient;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    throw new InvalidRequestException("Generate mapping is not supported for templates");
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    Template template = (Template) entity;
    Set<CgEntityId> children = new HashSet<>();
    CgEntityNode templateNode =
        CgEntityNode.builder()
            .appId(template.getAppId())
            .entity(template)
            .entityId(CgEntityId.builder().id(template.getUuid()).type(NGMigrationEntityType.TEMPLATE).build())
            .type(NGMigrationEntityType.TEMPLATE)
            .id(template.getUuid())
            .build();
    return DiscoveryNode.builder().children(children).entityNode(templateNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(templateService.get(entityId));
  }

  @Override
  public NGMigrationStatus canMigrate(NGMigrationEntity entity) {
    Template template = (Template) entity;
    if (template.getTemplateObject() instanceof HttpTemplate
        || template.getTemplateObject() instanceof ShellScriptTemplate) {
      return NGMigrationStatus.builder().status(true).build();
    }
    return NGMigrationStatus.builder()
        .status(false)
        .reasons(Collections.singletonList("Currently only shell script & http templates are supported"))
        .build();
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    if (yamlFile.isExists()) {
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(ImportError.builder()
                                                .message("Template was not migrated as it was already imported before")
                                                .entity(yamlFile.getCgBasicInfo())
                                                .build()))
          .build();
    }
    Response<ResponseDTO<ConnectorResponseDTO>> resp =
        templateClient
            .createTemplate(auth, inputDTO.getAccountIdentifier(), inputDTO.getOrgIdentifier(),
                inputDTO.getProjectIdentifier(), yamlFile.getYaml().toString())
            .execute();
    log.info("Template creation Response details {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  @Override
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities,
      NgEntityDetail ngEntityDetail) {
    Template template = (Template) entities.get(entityId).getEntity();
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, template.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(inputDTO.getOverrides(), entityId, name);
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);

    List<NGYamlFile> files = new ArrayList<>();
    NgTemplateService ngTemplateService = TemplateFactory.getTemplateService(template);
    if (template.getTemplateObject() instanceof HttpTemplate
        || template.getTemplateObject() instanceof ShellScriptTemplate) {
      NGYamlFile ngYamlFile = NGYamlFile.builder()
                                  .type(TEMPLATE)
                                  .filename("template/" + template.getName() + ".yaml")
                                  .yaml(ngTemplateService.getNgTemplateConfig(template))
                                  .ngEntityDetail(NgEntityDetail.builder()
                                                      .identifier(identifier)
                                                      .orgIdentifier(orgIdentifier)
                                                      .projectIdentifier(projectIdentifier)
                                                      .build())
                                  .cgBasicInfo(template.getCgBasicInfo())
                                  .build();
      files.add(ngYamlFile);
      migratedEntities.putIfAbsent(entityId, ngYamlFile);
      return files;
    }
    return new ArrayList<>();
  }

  @Override
  protected YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
      TemplateResponseDTO response = NGRestUtils.getResponse(templateResourceClient.get(ngEntityDetail.getIdentifier(),
          accountIdentifier, ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier(), "", false));
      return YamlPipelineUtils.read(response.getYaml(), NGTemplateConfig.class);
    } catch (Exception ex) {
      log.error("Error when getting template - ", ex);
      return null;
    }
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }

  @Override
  public BaseEntityInput generateInput(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {
    Template template = (Template) entities.get(entityId).getEntity();
    return BaseEntityInput.builder()
        .migrationStatus(MigratorInputType.CREATE_NEW)
        .identifier(BaseInputDefinition.buildIdentifier(MigratorUtility.generateIdentifier(template.getName())))
        .name(BaseInputDefinition.buildName(template.getName()))
        .scope(BaseInputDefinition.buildScope(Scope.PROJECT))
        .spec(null)
        .build();
  }
}
