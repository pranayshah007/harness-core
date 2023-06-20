/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.ngmigration.utils.NGMigrationConstants.RUNTIME_INPUT;

import static software.wings.ngmigration.NGMigrationEntityType.TEMPLATE;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.beans.MigratedEntityMapping;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.TemplateSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.template.NgTemplateService;
import io.harness.ngmigration.template.TemplateFactory;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngmigration.utils.SecretRefUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.JsonUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.TemplateWrapperResponseDTO;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.resources.beans.yaml.NGTemplateInfoConfig;

import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateType;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.template.TemplateService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
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
public class TemplateMigrationService extends NgMigrationService {
  @Inject TemplateService templateService;
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private SecretRefUtils secretRefUtils;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    NGTemplateInfoConfig templateInfoConfig = ((NGTemplateConfig) yamlFile.getYaml()).getTemplateInfoConfig();
    String orgIdentifier = yamlFile.getNgEntityDetail().getOrgIdentifier();
    String projectIdentifier = yamlFile.getNgEntityDetail().getProjectIdentifier();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(TEMPLATE.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(templateInfoConfig.getIdentifier())
        .scope(MigratorMappingService.getScope(orgIdentifier, projectIdentifier))
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(
            basicInfo.getAccountId(), orgIdentifier, projectIdentifier, templateInfoConfig.getIdentifier()))
        .build();
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Map<String, Long> summaryByType = entities.stream()
                                          .map(entity -> (Template) entity.getEntity())
                                          .collect(groupingBy(Template::getType, counting()));
    Set<String> expressions =
        entities.stream()
            .map(entity -> (Template) entity.getEntity())
            .flatMap(template -> TemplateFactory.getTemplateService(template).getExpressions(template).stream())
            .collect(Collectors.toSet());
    return TemplateSummary.builder().count(entities.size()).typeSummary(summaryByType).expressions(expressions).build();
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
    Set<String> expressions = TemplateFactory.getTemplateService(template).getExpressions(template);
    List<CgEntityId> secretRefs = secretRefUtils.getSecretRefFromExpressions(template.getAccountId(), expressions);
    if (EmptyPredicate.isNotEmpty(secretRefs)) {
      children.addAll(secretRefs);
    }
    return DiscoveryNode.builder().children(children).entityNode(templateNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(templateService.get(entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    Response<ResponseDTO<TemplateWrapperResponseDTO>> resp =
        templateClient
            .createTemplate(inputDTO.getDestinationAuthToken(), inputDTO.getDestinationAccountIdentifier(),
                inputDTO.getOrgIdentifier(), inputDTO.getProjectIdentifier(),
                RequestBody.create(MediaType.parse("application/yaml"), YamlUtils.writeYamlString(yamlFile.getYaml())),
                StoreType.INLINE)
            .execute();
    log.info("Template creation Response details {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    Template template = (Template) entities.get(entityId).getEntity();
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, template.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(
        inputDTO.getOverrides(), entityId, name, inputDTO.getIdentifierCaseFormat());
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    String description = StringUtils.isBlank(template.getDescription()) ? "" : template.getDescription();
    MigratorExpressionUtils.render(migrationContext, template, inputDTO.getCustomExpressions());

    NgTemplateService ngTemplateService = TemplateFactory.getTemplateService(template);
    JsonNode spec =
        ngTemplateService.getNgTemplateConfigSpec(migrationContext, template, orgIdentifier, projectIdentifier);
    if (ngTemplateService.isMigrationSupported() && spec != null) {
      List<NGYamlFile> files = new ArrayList<>();
      NGYamlFile ngYamlFile =
          NGYamlFile.builder()
              .type(TEMPLATE)
              .filename("template/" + template.getName() + ".yaml")
              .yaml(NGTemplateConfig.builder()
                        .templateInfoConfig(NGTemplateInfoConfig.builder()
                                                .type(ngTemplateService.getTemplateEntityType())
                                                .identifier(identifier)
                                                .name(name)
                                                .description(ParameterField.createValueField(description))
                                                .projectIdentifier(projectIdentifier)
                                                .orgIdentifier(orgIdentifier)
                                                .versionLabel("v" + template.getVersion().toString())
                                                .spec(getSpec(spec, template))
                                                .build())
                        .build())
              .ngEntityDetail(NgEntityDetail.builder()
                                  .entityType(TEMPLATE)
                                  .identifier(identifier)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .build())
              .cgBasicInfo(template.getCgBasicInfo())
              .build();
      files.add(ngYamlFile);
      migratedEntities.putIfAbsent(entityId, ngYamlFile);
      return YamlGenerationDetails.builder().yamlFileList(files).build();
    }
    return null;
  }

  private JsonNode getSpec(JsonNode configSpec, Template template) {
    NgTemplateService ngTemplateService = TemplateFactory.getTemplateService(template);
    if (TemplateType.CUSTOM_DEPLOYMENT_TYPE.name().equals(template.getType())) {
      return configSpec;
    } else {
      return JsonUtils.asTree(ImmutableMap.<String, Object>builder()
                                  .put("spec", configSpec)
                                  .put("type", ngTemplateService.getNgTemplateStepName(template))
                                  .put("timeout", ngTemplateService.getTimeoutString(template))
                                  .put("failureStrategies", RUNTIME_INPUT)
                                  .put("when",
                                      ImmutableMap.<String, String>builder()
                                          .put("stageStatus", "Success")
                                          .put("condition", RUNTIME_INPUT)
                                          .build())
                                  .build());
    }
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
      // Note: We are passing versionLabel as `null` because we do not know the version label.
      // It will return a stable version by default.
      TemplateResponseDTO response = NGRestUtils.getResponse(templateResourceClient.get(ngEntityDetail.getIdentifier(),
          accountIdentifier, ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier(), null, false));
      if (response == null || StringUtils.isBlank(response.getYaml())) {
        return null;
      }
      return YamlUtils.read(response.getYaml(), NGTemplateConfig.class);
    } catch (Exception ex) {
      log.warn("Error when getting templates - ", ex);
      return null;
    }
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }
}
