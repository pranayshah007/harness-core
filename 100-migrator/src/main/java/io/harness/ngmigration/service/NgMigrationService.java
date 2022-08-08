/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.beans.MigratedEntityMapping;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.persistence.NameAccess;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public abstract class NgMigrationService {
  @Inject MigratorMappingService migratorMappingService;

  public abstract MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile);

  public abstract DiscoveryNode discover(NGMigrationEntity entity);

  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    return new BaseSummary(entities.size());
  }

  public abstract DiscoveryNode discover(String accountId, String appId, String entityId);

  public abstract NGMigrationStatus canMigrate(NGMigrationEntity entity);

  public NGMigrationStatus canMigrate(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {
    return canMigrate(entities.get(entityId).getEntity());
  }

  public abstract void migrate(String auth, NGClient ngClient, PmsClient pmsClient, MigrationInputDTO inputDTO,
      NGYamlFile yamlFile) throws IOException;

  public abstract List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities,
      NgEntityDetail ngEntityDetail);

  public List<NGYamlFile> getYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities) {
    if (!isNGEntityExists()) {
      return new ArrayList<>();
    }
    CgEntityNode cgEntityNode = entities.get(entityId);
    // TODO: CG Basic Info should be part of CGEntityNode
    CgBasicInfo cgBasicInfo = CgBasicInfo.builder()
                                  .accountId(inputDTO.getAccountIdentifier())
                                  .appId(cgEntityNode.getAppId())
                                  .id(entityId.getId())
                                  .type(entityId.getType())
                                  .build();
    NgEntityDetail ngEntityDetail = getNGEntityDetail(inputDTO, entities, graph, entityId, migratedEntities);
    boolean mappingExist = migratorMappingService.doesMappingExist(cgBasicInfo, ngEntityDetail);
    NGYamlFile ngYamlFile =
        NGYamlFile.builder()
            .ngEntityDetail(ngEntityDetail)
            .cgBasicInfo(cgBasicInfo)
            .filename(entityId.getType().getYamlFolderName() + "/" + ngEntityDetail.getIdentifier() + ".yaml")
            .type(entityId.getType())
            .build();
    if (mappingExist) {
      YamlDTO yamlDTO = null;
      try {
        yamlDTO = getNGEntity(ngEntityDetail, inputDTO.getAccountIdentifier());
      } catch (Exception ex) {
        log.error("Failed to retrieve NG Entity. ", ex);
      }
      if (yamlDTO == null) {
        // Deleted
        return generateYaml(inputDTO, entities, graph, entityId, migratedEntities, ngEntityDetail);
      } else {
        ngYamlFile.setExists(true);
        ngYamlFile.setYaml(yamlDTO);
        migratedEntities.put(entityId, ngEntityDetail);
        return Arrays.asList(ngYamlFile);
      }
    } else {
      return generateYaml(inputDTO, entities, graph, entityId, migratedEntities, ngEntityDetail);
    }
  }

  private NgEntityDetail getNGEntityDetail(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities) {
    CgEntityNode cgEntityNode = entities.get(entityId);
    String identifier = null;
    if (cgEntityNode.getEntity() instanceof NameAccess) {
      identifier = MigratorUtility.generateIdentifier(((NameAccess) cgEntityNode.getEntity()).getName());
    }
    String projectIdentifier = null;
    String orgIdentifier = null;
    Scope scope =
        MigratorUtility.getDefaultScope(inputDTO.getDefaults(), NGMigrationEntityType.CONNECTOR, Scope.PROJECT);
    if (inputDTO.getInputs() != null && inputDTO.getInputs().containsKey(entityId)) {
      BaseProvidedInput input = inputDTO.getInputs().get(entityId);
      identifier = StringUtils.isNotBlank(input.getIdentifier()) ? input.getIdentifier() : identifier;
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
    return NgEntityDetail.builder()
        .identifier(identifier)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .build();
  }

  protected abstract YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier);

  protected abstract boolean isNGEntityExists();

  public abstract BaseEntityInput generateInput(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId);
}
