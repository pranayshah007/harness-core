/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.MigratedEntityMapping;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretRequestWrapper;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.BaseInputDefinition;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.MigratorInputType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.connector.SecretFactory;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.remote.client.NGRestUtils;
import io.harness.secrets.SecretService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.serializer.JsonUtils;

import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class SecretMigrationService extends NgMigrationService {
  @Inject private SecretService secretService;
  @Inject private SecretNGManagerClient secretNGManagerClient;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    SecretDTOV2 secretYaml = ((SecretRequestWrapper) yamlFile.getYaml()).getSecret();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(NGMigrationEntityType.SECRET.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(secretYaml.getOrgIdentifier())
        .projectIdentifier(secretYaml.getProjectIdentifier())
        .identifier(secretYaml.getIdentifier())
        .scope(MigratorMappingService.getScope(secretYaml.getOrgIdentifier(), secretYaml.getProjectIdentifier()))
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(basicInfo.getAccountId(),
            secretYaml.getOrgIdentifier(), secretYaml.getProjectIdentifier(), secretYaml.getIdentifier()))
        .build();
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    EncryptedData encryptedData = (EncryptedData) entity;
    if (encryptedData == null) {
      return null;
    }
    String entityId = encryptedData.getUuid();
    CgEntityId connectorEntityId = CgEntityId.builder().type(NGMigrationEntityType.SECRET).id(entityId).build();
    CgEntityNode connectorNode = CgEntityNode.builder()
                                     .id(entityId)
                                     .type(NGMigrationEntityType.SECRET)
                                     .entityId(connectorEntityId)
                                     .entity(encryptedData)
                                     .build();
    Set<CgEntityId> children = new HashSet<>();
    children.add(CgEntityId.builder().type(NGMigrationEntityType.SECRET_MANAGER).id(encryptedData.getKmsId()).build());
    return DiscoveryNode.builder().children(children).entityNode(connectorNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(secretService.getSecretById(accountId, entityId).orElse(null));
  }

  @Override
  public NGMigrationStatus canMigrate(NGMigrationEntity entity) {
    return NGMigrationStatus.builder().status(true).build();
  }

  @Override
  public void migrate(String auth, NGClient ngClient, PmsClient pmsClient, MigrationInputDTO inputDTO,
      NGYamlFile yamlFile) throws IOException {
    SecretRequestWrapper secretRequestWrapper = (SecretRequestWrapper) yamlFile.getYaml();
    Response<ResponseDTO<SecretResponseWrapper>> resp =
        ngClient
            .createSecret(auth, inputDTO.getAccountIdentifier(), secretRequestWrapper.getSecret().getOrgIdentifier(),
                secretRequestWrapper.getSecret().getProjectIdentifier(), JsonUtils.asTree(yamlFile.getYaml()))
            .execute();
    log.info("Secret creation Response details {} {}", resp.code(), resp.message());
  }

  @Override
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities,
      NgEntityDetail ngEntityDetail) {
    EncryptedData encryptedData = (EncryptedData) entities.get(entityId).getEntity();
    List<NGYamlFile> files = new ArrayList<>();
    String identifier = MigratorUtility.generateIdentifier(encryptedData.getName());
    files.add(
        NGYamlFile.builder()
            .type(NGMigrationEntityType.SECRET)
            .filename("secret/" + encryptedData.getName() + ".yaml")
            .yaml(SecretRequestWrapper.builder()
                      .secret(SecretFactory.getSecret(inputDTO, identifier, encryptedData, entities, migratedEntities))
                      .build())
            .cgBasicInfo(CgBasicInfo.builder()
                             .id(encryptedData.getUuid())
                             .accountId(encryptedData.getAccountId())
                             .appId(null)
                             .type(NGMigrationEntityType.SECRET)
                             .build())
            .build());

    // TODO: make it more obvious that migratedEntities needs to be updated by having compile-time check
    migratedEntities.putIfAbsent(entityId,
        NgEntityDetail.builder()
            .identifier(identifier)
            .orgIdentifier(inputDTO.getOrgIdentifier())
            .projectIdentifier(inputDTO.getProjectIdentifier())
            .build());

    return files;
  }

  @Override
  protected YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
      SecretResponseWrapper response =
          NGRestUtils.getResponse(secretNGManagerClient.getSecret(ngEntityDetail.getIdentifier(), accountIdentifier,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier()));
      return response == null ? null : SecretRequestWrapper.builder().secret(response.getSecret()).build();
    } catch (InvalidRequestException ex) {
      log.error("Error when getting connector - ", ex);
      return null;
    } catch (Exception ex) {
      throw ex;
    }
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }

  @Override
  public BaseEntityInput generateInput(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {
    EncryptedData secretManagerConfig = (EncryptedData) entities.get(entityId).getEntity();
    return BaseEntityInput.builder()
        .migrationStatus(MigratorInputType.CREATE_NEW)
        .identifier(
            BaseInputDefinition.buildIdentifier(MigratorUtility.generateIdentifier(secretManagerConfig.getName())))
        .name(BaseInputDefinition.buildName(secretManagerConfig.getName()))
        .spec(null)
        .build();
  }
}
