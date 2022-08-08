/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.beans.SecretManagerConfig;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.BaseInputDefinition;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.MigratorInputType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.SecretManagerSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.connector.SecretFactory;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.encryption.EncryptionType;

import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class SecretManagerMigrationService extends NgMigrationService {
  @Inject private SecretManager secretManager;
  @Inject private ConnectorResourceClient connectorResourceClient;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    return null;
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Map<String, Long> typeSummary = entities.stream()
                                        .map(entity -> ((SecretManagerConfig) entity.getEntity()).getEncryptionType())
                                        .collect(groupingBy(EncryptionType::name, counting()));
    return new SecretManagerSummary(entities.size(), typeSummary);
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    SecretManagerConfig managerConfig = (SecretManagerConfig) entity;
    String entityId = managerConfig.getUuid();
    CgEntityId managerEntityId = CgEntityId.builder().type(NGMigrationEntityType.SECRET_MANAGER).id(entityId).build();
    CgEntityNode secretManagerNode = CgEntityNode.builder()
                                         .id(entityId)
                                         .type(NGMigrationEntityType.SECRET_MANAGER)
                                         .entityId(managerEntityId)
                                         .entity(managerConfig)
                                         .build();
    Set<CgEntityId> children = new HashSet<>();
    return DiscoveryNode.builder().children(children).entityNode(secretManagerNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(secretManager.getSecretManager(accountId, entityId));
  }

  @Override
  public NGMigrationStatus canMigrate(NGMigrationEntity entity) {
    return NGMigrationStatus.builder().status(true).build();
  }

  @Override
  public void migrate(String auth, NGClient ngClient, PmsClient pmsClient, MigrationInputDTO inputDTO,
      NGYamlFile yamlFile) throws IOException {}

  @Override
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities,
      NgEntityDetail ngEntityDetail) {
    SecretManagerConfig secretManagerConfig = (SecretManagerConfig) entities.get(entityId).getEntity();
    String name = secretManagerConfig.getName();
    String identifier = MigratorUtility.generateIdentifier(secretManagerConfig.getName());
    String projectIdentifier = null;
    String orgIdentifier = null;
    Scope scope =
        MigratorUtility.getDefaultScope(inputDTO.getDefaults(), NGMigrationEntityType.SECRET_MANAGER, Scope.PROJECT);
    if (inputDTO.getInputs() != null && inputDTO.getInputs().containsKey(entityId)) {
      // TODO: @deepakputhraya We should handle if the connector needs to be reused.
      BaseProvidedInput input = inputDTO.getInputs().get(entityId);
      identifier = StringUtils.isNotBlank(input.getIdentifier()) ? input.getIdentifier() : identifier;
      name = StringUtils.isNotBlank(input.getIdentifier()) ? input.getName() : name;
      if (input.getScope() != null) {
        scope = input.getScope();
      }
    }
    if (Scope.PROJECT.equals(scope)) {
      projectIdentifier = inputDTO.getProjectIdentifier();
      orgIdentifier = inputDTO.getOrgIdentifier();
    }
    if (Scope.ORG.equals(scope)) {
      orgIdentifier = inputDTO.getOrgIdentifier();
    }

    List<NGYamlFile> files = new ArrayList<>();
    files.add(NGYamlFile.builder()
                  .filename("connector/" + name + ".yaml")
                  .yaml(ConnectorDTO.builder()
                            .connectorInfo(ConnectorInfoDTO.builder()
                                               .name(name)
                                               .identifier(identifier)
                                               .description(null)
                                               .tags(null)
                                               .orgIdentifier(orgIdentifier)
                                               .projectIdentifier(projectIdentifier)
                                               .connectorType(SecretFactory.getConnectorType(secretManagerConfig))
                                               .connectorConfig(SecretFactory.getConfigDTO(secretManagerConfig))
                                               .build())
                            .build())
                  .type(NGMigrationEntityType.SECRET_MANAGER)
                  .cgBasicInfo(CgBasicInfo.builder()
                                   .accountId(secretManagerConfig.getAccountId())
                                   .appId(null)
                                   .id(secretManagerConfig.getUuid())
                                   .type(NGMigrationEntityType.SECRET_MANAGER)
                                   .build())
                  .build());

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
      Optional<ConnectorDTO> response =
          NGRestUtils.getResponse(connectorResourceClient.get(ngEntityDetail.getIdentifier(), accountIdentifier,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier()));
      return response.orElse(null);
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
    SecretManagerConfig secretManagerConfig = (SecretManagerConfig) entities.get(entityId).getEntity();
    return BaseEntityInput.builder()
        .migrationStatus(MigratorInputType.CREATE_NEW)
        .identifier(
            BaseInputDefinition.buildIdentifier(MigratorUtility.generateIdentifier(secretManagerConfig.getName())))
        .name(BaseInputDefinition.buildName(secretManagerConfig.getName()))
        .spec(null)
        .build();
  }
}
