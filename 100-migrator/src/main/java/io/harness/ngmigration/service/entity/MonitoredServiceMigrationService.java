/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static software.wings.ngmigration.NGMigrationEntityType.MONITORED_SERVICE_TEMPLATE;

import io.harness.beans.MigratedEntityMapping;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.monitoredservice.bean.CGMonitoredServiceEntity;
import io.harness.ngmigration.monitoredservice.utils.MonitoredServiceEntityToMonitoredServiceMapper;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.utils.MigratorUtility;
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
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@Slf4j
public class MonitoredServiceMigrationService extends NgMigrationService {
  @Inject private TemplateResourceClient templateResourceClient;

  @Inject private WorkflowService workflowService;

  @Inject private MonitoredServiceEntityToMonitoredServiceMapper monitoredServiceEntityToMonitoredServiceMapper;

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
        .entityType(MONITORED_SERVICE_TEMPLATE.name())
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
  public DiscoveryNode discover(NGMigrationEntity entity) {
    CGMonitoredServiceEntity CGMonitoredServiceEntity = (CGMonitoredServiceEntity) entity;
    CgEntityNode node =
        CgEntityNode.builder()
            .appId(CGMonitoredServiceEntity.getWorkflow().getAppId())
            .entity(CGMonitoredServiceEntity)
            .entityId(
                CgEntityId.builder().id(CGMonitoredServiceEntity.getId()).type(MONITORED_SERVICE_TEMPLATE).build())
            .type(MONITORED_SERVICE_TEMPLATE)
            .id(CGMonitoredServiceEntity.getId())
            .build();
    return DiscoveryNode.builder().entityNode(node).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    String workflowId = CGMonitoredServiceEntity.getWorkflowIdFromMonitoredServiceId(entityId);
    String stepId = CGMonitoredServiceEntity.getStepIdFromMonitoredServiceId(entityId);
    Workflow workflow = workflowService.readWorkflow(appId, workflowId);
    GraphNode graphNode =
        MigratorUtility.getSteps(workflow).stream().filter(gn -> gn.getId().equals(stepId)).findFirst().get();
    return discover(CGMonitoredServiceEntity.builder().workflow(workflow).stepNode(graphNode).build());
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    CGMonitoredServiceEntity cgMonitoredServiceEntity =
        (CGMonitoredServiceEntity) migrationContext.getEntities().get(entityId).getEntity();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, cgMonitoredServiceEntity.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(
        inputDTO.getOverrides(), entityId, name, inputDTO.getIdentifierCaseFormat());
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    String description = "Monitored Service migrated from First Gen";
    MigratorExpressionUtils.render(migrationContext, cgMonitoredServiceEntity, inputDTO.getCustomExpressions());

    if (monitoredServiceEntityToMonitoredServiceMapper.isMigrationSupported(cgMonitoredServiceEntity.getStepNode())) {
      List<NGYamlFile> files = new ArrayList<>();
      NGYamlFile ngYamlFile =
          NGYamlFile.builder()
              .type(MONITORED_SERVICE_TEMPLATE)
              .filename("monitoredservice/" + cgMonitoredServiceEntity.getName() + ".yaml")
              .yaml(NGTemplateConfig.builder()
                        .templateInfoConfig(
                            NGTemplateInfoConfig.builder()
                                .type(TemplateEntityType.MONITORED_SERVICE_TEMPLATE)
                                .identifier(identifier)
                                .name(name)
                                .description(ParameterField.createValueField(description))
                                .projectIdentifier(projectIdentifier)
                                .orgIdentifier(orgIdentifier)
                                .versionLabel("v1")
                                .spec(monitoredServiceEntityToMonitoredServiceMapper.getMonitoredServiceJsonNode(
                                    cgMonitoredServiceEntity.getStepNode()))
                                .build())
                        .build())
              .ngEntityDetail(NgEntityDetail.builder()
                                  .entityType(MONITORED_SERVICE_TEMPLATE)
                                  .identifier(identifier)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .build())
              .cgBasicInfo(cgMonitoredServiceEntity.getCgBasicInfo())
              .build();
      files.add(ngYamlFile);
      migratedEntities.putIfAbsent(entityId, ngYamlFile);
      return YamlGenerationDetails.builder().yamlFileList(files).build();
    }
    return null;
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    Response<ResponseDTO<TemplateWrapperResponseDTO>> resp =
        templateClient
            .createTemplate(inputDTO.getDestinationAuthToken(), inputDTO.getDestinationAccountIdentifier(),
                inputDTO.getOrgIdentifier(), inputDTO.getProjectIdentifier(),
                RequestBody.create(MediaType.parse("application/yaml"), YamlUtils.write(yamlFile.getYaml())))
            .execute();
    log.info("Template creation Response details for Monitored Service {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
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
