/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.ngmigration.beans.SupportStatus.UNSUPPORTED;
import static io.harness.ngmigration.connector.ConnectorFactory.CONNECTOR_FACTORY_MAP;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ngmigration.beans.CustomSecretRequestWrapper;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGSkipDetail;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.TypeSummary;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.ConnectorSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.connector.BaseConnector;
import io.harness.ngmigration.connector.ConnectorFactory;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.JsonUtils;

import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class ConnectorMigrationService extends NgMigrationService {
  @Inject private SettingsService settingsService;
  @Inject private ConnectorResourceClient connectorResourceClient;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    ConnectorInfoDTO connectorInfo = ((ConnectorDTO) yamlFile.getYaml()).getConnectorInfo();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(CONNECTOR.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(connectorInfo.getOrgIdentifier())
        .projectIdentifier(connectorInfo.getProjectIdentifier())
        .identifier(connectorInfo.getIdentifier())
        .scope(MigratorMappingService.getScope(connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier()))
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(basicInfo.getAccountId(),
            connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier(), connectorInfo.getIdentifier()))
        .build();
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Map<String, Long> summaryByType = entities.stream()
                                          .map(entity -> (SettingAttribute) entity.getEntity())
                                          .collect(groupingBy(entity -> entity.getValue().getType(), counting()));

    Map<String, TypeSummary> summaryMap = new HashMap<>();
    summaryByType.forEach((key, value) -> {
      SupportStatus status = UNSUPPORTED;
      if (CONNECTOR_FACTORY_MAP.containsKey(SettingVariableTypes.valueOf(key))) {
        BaseConnector baseConnector = CONNECTOR_FACTORY_MAP.get(SettingVariableTypes.valueOf(key));
        status = baseConnector.getSupportStatus();
      }
      summaryMap.put(key, TypeSummary.builder().count(value).status(status).build());
    });

    return ConnectorSummary.builder()
        .count(entities.size())
        .typeSummary(summaryByType)
        .typesSummary(summaryMap)
        .build();
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    SettingAttribute settingAttribute = (SettingAttribute) entity;
    String entityId = settingAttribute.getUuid();
    CgEntityId connectorEntityId = CgEntityId.builder().type(CONNECTOR).id(entityId).build();
    CgEntityNode connectorNode = CgEntityNode.builder()
                                     .id(entityId)
                                     .type(CONNECTOR)
                                     .entityId(connectorEntityId)
                                     .entity(settingAttribute)
                                     .build();
    Set<CgEntityId> children = new HashSet<>();
    List<String> secrets = ConnectorFactory.getConnector(settingAttribute).getSecretIds(settingAttribute);
    if (EmptyPredicate.isNotEmpty(secrets)) {
      children.addAll(secrets.stream()
                          .filter(StringUtils::isNotBlank)
                          .map(secret -> CgEntityId.builder().id(secret).type(NGMigrationEntityType.SECRET).build())
                          .collect(Collectors.toList()));
    }
    List<String> connectorIds = ConnectorFactory.getConnector(settingAttribute).getConnectorIds(settingAttribute);
    if (EmptyPredicate.isNotEmpty(connectorIds)) {
      children.addAll(connectorIds.stream()
                          .filter(StringUtils::isNotBlank)
                          .map(connectorId -> CgEntityId.builder().id(connectorId).type(CONNECTOR).build())
                          .collect(Collectors.toList()));
    }
    return DiscoveryNode.builder().children(children).entityNode(connectorNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(settingsService.getByAccountAndId(accountId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    if (yamlFile.isExists()) {
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(ImportError.builder()
                                                .message("Connector was not migrated as it was already imported before")
                                                .entity(yamlFile.getCgBasicInfo())
                                                .build()))
          .build();
    }
    Response<ResponseDTO<ConnectorResponseDTO>> resp =
        ngClient.createConnector(auth, inputDTO.getAccountIdentifier(), JsonUtils.asTree(yamlFile.getYaml())).execute();
    log.info("Connector creation Response details {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    SettingAttribute settingAttribute = (SettingAttribute) entities.get(entityId).getEntity();
    Map<CgEntityId, Set<CgEntityId>> graph = migrationContext.getGraph();
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, settingAttribute.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(
        inputDTO.getOverrides(), entityId, name, inputDTO.getIdentifierCaseFormat());
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    NgEntityDetail ngEntityDetail = NgEntityDetail.builder()
                                        .identifier(identifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .build();
    List<NGYamlFile> files = new ArrayList<>();
    Set<CgEntityId> childEntities = graph.get(entityId);
    BaseConnector connectorImpl = ConnectorFactory.getConnector(settingAttribute);
    NGYamlFile yamlFile = null;
    try {
      if (connectorImpl.getSecretType() != null) {
        // We are overriding this because these are connectors in CG but in NG they are secrets.
        // So when we migrate we should infer them as secrets
        scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT, SECRET);
        projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
        orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
        ngEntityDetail = NgEntityDetail.builder()
                             .identifier(identifier)
                             .orgIdentifier(orgIdentifier)
                             .projectIdentifier(projectIdentifier)
                             .build();
        SecretSpecDTO secretSpecDTO = connectorImpl.getSecretSpecDTO(settingAttribute, migratedEntities);
        yamlFile = NGYamlFile.builder()
                       .type(NGMigrationEntityType.SECRET)
                       .filename("secret/" + name + ".yaml")
                       .yaml(CustomSecretRequestWrapper.builder()
                                 .secret(SecretDTOV2.builder()
                                             .identifier(identifier)
                                             .projectIdentifier(projectIdentifier)
                                             .orgIdentifier(orgIdentifier)
                                             .name(name)
                                             .spec(secretSpecDTO)
                                             .type(connectorImpl.getSecretType())
                                             .build())
                                 .build())
                       .ngEntityDetail(ngEntityDetail)
                       .cgBasicInfo(settingAttribute.getCgBasicInfo())
                       .build();
        return YamlGenerationDetails.builder().yamlFileList(files).build();
      }
      ConnectorType connectorType = connectorImpl.getConnectorType(settingAttribute);
      if (connectorType == null) {
        // TODO: @deepakputhraya
        return YamlGenerationDetails.builder()
            .skipDetails(Collections.singletonList(NGSkipDetail.builder()
                                                       .reason("Unsupported/Unrecognized connector in NG")
                                                       .cgBasicInfo(settingAttribute.getCgBasicInfo())
                                                       .type(entityId.getType())
                                                       .build()))
            .build();
      }
      yamlFile = NGYamlFile.builder()
                     .type(CONNECTOR)
                     .filename("connector/" + settingAttribute.getName() + ".yaml")
                     .yaml(ConnectorDTO.builder()
                               .connectorInfo(ConnectorInfoDTO.builder()
                                                  .name(name)
                                                  .identifier(identifier)
                                                  .description(null)
                                                  .tags(null)
                                                  .orgIdentifier(orgIdentifier)
                                                  .projectIdentifier(projectIdentifier)
                                                  .connectorType(connectorType)
                                                  .connectorConfig(connectorImpl.getConfigDTO(
                                                      settingAttribute, childEntities, migratedEntities))
                                                  .build())
                               .build())
                     .ngEntityDetail(ngEntityDetail)
                     .cgBasicInfo(settingAttribute.getCgBasicInfo())
                     .build();
      return YamlGenerationDetails.builder().yamlFileList(files).build();
    } finally {
      if (yamlFile != null) {
        files.add(yamlFile);
        migratedEntities.putIfAbsent(entityId, yamlFile);
      }
    }
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
      Optional<ConnectorDTO> response =
          NGRestUtils.getResponse(connectorResourceClient.get(ngEntityDetail.getIdentifier(), accountIdentifier,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier()));
      return response.orElse(null);
    } catch (Exception ex) {
      log.warn("Error when getting connector - ", ex);
      return null;
    }
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }
}
