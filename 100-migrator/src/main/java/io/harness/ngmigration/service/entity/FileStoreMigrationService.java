/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.service.NgMigrationService;

import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.RequestBody;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class FileStoreMigrationService extends NgMigrationService {
  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    return null;
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    return null;
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    return null;
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return null;
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    FileYamlDTO fileYamlDTO = (FileYamlDTO) yamlFile.getYaml();
    RequestBody identifier = RequestBody.create(TEXT_PLAIN, fileYamlDTO.getIdentifier());
    RequestBody name = RequestBody.create(TEXT_PLAIN, fileYamlDTO.getName());
    RequestBody type = RequestBody.create(TEXT_PLAIN, "FOLDER");
    RequestBody parentIdentifier = RequestBody.create(TEXT_PLAIN, fileYamlDTO.getRootIdentifier());

    Response<ResponseDTO<FileDTO>> resp;
    try {
      resp = ngClient
                 .createFolder(auth, inputDTO.getAccountIdentifier(), inputDTO.getOrgIdentifier(),
                     inputDTO.getProjectIdentifier(), name, identifier, type, parentIdentifier)
                 .execute();
      log.info("Folder creation Response details {} {}", resp.code(), resp.message());
    } catch (IOException e) {
      log.error("Failed to create folder", e);
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(ImportError.builder()
                                                .message("There was an error creating the folder in file store")
                                                .entity(yamlFile.getCgBasicInfo())
                                                .build()))
          .build();
    }
    return handleResp(yamlFile, resp);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    return null;
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

  @Override
  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean migrateAll) {
    return true;
  }
}
