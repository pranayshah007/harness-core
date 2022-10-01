/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.dto.ConnectorFilter;
import io.harness.ngmigration.dto.Filter;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.SaveSummaryDTO;
import io.harness.ngmigration.dto.SecretFilter;

import software.wings.ngmigration.DiscoveryResult;

import com.google.inject.Inject;
import javax.ws.rs.core.StreamingOutput;

@OwnedBy(HarnessTeam.CDC)
public class MigrationResourceService {
  @Inject private ConnectorImportService connectorImportService;
  @Inject private SecretsImportService secretsImportService;
  @Inject private DiscoveryService discoveryService;

  private DiscoveryResult discover(String authToken, ImportDTO importDTO) {
    // Migrate referenced entities as well.
    importDTO.setMigrateReferencedEntities(true);
    Filter filter = importDTO.getFilter();
    if (filter instanceof ConnectorFilter) {
      return connectorImportService.discover(authToken, importDTO);
    }
    if (filter instanceof SecretFilter) {
      return secretsImportService.discover(authToken, importDTO);
    }
    return DiscoveryResult.builder().build();
  }

  public SaveSummaryDTO save(String authToken, ImportDTO importDTO) {
    DiscoveryResult discoveryResult = discover(authToken, importDTO);
    discoveryService.migrateEntity(authToken, getMigrationInput(importDTO), discoveryResult, false);
    // TODO: Create summary from migrated entites
    return SaveSummaryDTO.builder().build();
  }

  public StreamingOutput exportYaml(String authToken, ImportDTO importDTO) {
    return discoveryService.exportYamlFilesAsZip(getMigrationInput(importDTO), discover(authToken, importDTO));
  }

  private static MigrationInputDTO getMigrationInput(ImportDTO importDTO) {
    return MigrationInputDTO.builder()
        .accountIdentifier(importDTO.getAccountIdentifier())
        .orgIdentifier(importDTO.getDestinationDetails().getOrgIdentifier())
        .projectIdentifier(importDTO.getDestinationDetails().getProjectIdentifier())
        .migrateReferencedEntities(importDTO.isMigrateReferencedEntities())
        .build();
  }

  public void save(String authToken, DiscoveryResult discoveryResult, MigrationInputDTO inputDTO) {
    discoveryService.migrateEntity(authToken, inputDTO, discoveryResult, true);
  }
}
