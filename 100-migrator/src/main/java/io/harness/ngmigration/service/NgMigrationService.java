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
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.persistence.NameAccess;
import io.harness.serializer.JsonUtils;

import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationStatus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

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

  public String getYamlString(NGYamlFile yamlFile) {
    return NGYamlUtils.getYamlString(yamlFile.getYaml());
  }

  public NGMigrationStatus canMigrate(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {
    return canMigrate(entities.get(entityId).getEntity());
  }

  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    return null;
  }

  public abstract List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities);

  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean canMigrateAll) {
    return canMigrateAll;
  }

  public List<NGYamlFile> getYaml(MigrationInputDTO inputDTO, CgEntityId root, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    if (!isNGEntityExists() || !canMigrate(entityId, root, inputDTO.isMigrateReferencedEntities())) {
      return new ArrayList<>();
    }
    CgEntityNode cgEntityNode = entities.get(entityId);
    // TODO: CG Basic Info should be part of CGEntityNode
    CgBasicInfo cgBasicInfo = cgEntityNode.getEntity().getCgBasicInfo();
    NgEntityDetail ngEntityDetail = getNGEntityDetail(inputDTO, entities, entityId);
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
        return generateYaml(inputDTO, entities, graph, entityId, migratedEntities);
      } else {
        ngYamlFile.setExists(true);
        ngYamlFile.setYaml(yamlDTO);
        migratedEntities.put(entityId, ngYamlFile);
        return Arrays.asList(ngYamlFile);
      }
    } else {
      return generateYaml(inputDTO, entities, graph, entityId, migratedEntities);
    }
  }

  private NgEntityDetail getNGEntityDetail(
      MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities, CgEntityId entityId) {
    CgEntityNode cgEntityNode = entities.get(entityId);
    String name = "";
    if (cgEntityNode.getEntity() instanceof NameAccess) {
      name = ((NameAccess) cgEntityNode.getEntity()).getName();
    }
    name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, name);
    String identifier = MigratorUtility.generateIdentifierDefaultName(inputDTO.getOverrides(), entityId, name);
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
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

  protected <T> MigrationImportSummaryDTO handleResp(NGYamlFile yamlFile, Response<ResponseDTO<T>> resp)
      throws IOException {
    if (resp.code() >= 200 && resp.code() < 300) {
      return MigrationImportSummaryDTO.builder().success(true).errors(Collections.emptyList()).build();
    }
    Map<String, Object> error = JsonUtils.asObject(
        resp.errorBody() != null ? resp.errorBody().string() : "{}", new TypeReference<Map<String, Object>>() {});
    log.error(String.format("There was error creating the %s. Response from NG - %s with error body errorBody -  %s",
        yamlFile.getType(), resp, error));
    return MigrationImportSummaryDTO.builder()
        .errors(Collections.singletonList(
            ImportError.builder()
                .message(error.containsKey("message")
                        ? error.get("message").toString()
                        : String.format("There was an error creating the %s", yamlFile.getType()))
                .entity(yamlFile.getCgBasicInfo())
                .build()))
        .build();
  }
}
