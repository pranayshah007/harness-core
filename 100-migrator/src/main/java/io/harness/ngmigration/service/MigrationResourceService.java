/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.InputDefaults;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.dto.ApplicationFilter;
import io.harness.ngmigration.dto.ConnectorFilter;
import io.harness.ngmigration.dto.Filter;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.SaveSummaryDTO;
import io.harness.ngmigration.dto.SecretFilter;
import io.harness.ngmigration.dto.SecretManagerFilter;
import io.harness.ngmigration.dto.ServiceFilter;
import io.harness.ngmigration.service.importer.AppImportService;
import io.harness.ngmigration.service.importer.ConnectorImportService;
import io.harness.ngmigration.service.importer.SecretManagerImportService;
import io.harness.ngmigration.service.importer.SecretsImportService;
import io.harness.ngmigration.service.importer.ServiceImportService;

import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.StreamingOutput;

@OwnedBy(HarnessTeam.CDC)
public class MigrationResourceService {
  @Inject private ConnectorImportService connectorImportService;
  @Inject private SecretManagerImportService secretManagerImportService;
  @Inject private SecretsImportService secretsImportService;
  @Inject private AppImportService appImportService;
  @Inject private ServiceImportService serviceImportService;
  @Inject private DiscoveryService discoveryService;

  private DiscoveryResult discover(String authToken, ImportDTO importDTO) {
    // Migrate referenced entities as well.
    importDTO.setMigrateReferencedEntities(true);
    Filter filter = importDTO.getFilter();
    if (filter instanceof ConnectorFilter) {
      return connectorImportService.discover(authToken, importDTO);
    }
    if (filter instanceof SecretManagerFilter) {
      return secretManagerImportService.discover(authToken, importDTO);
    }
    if (filter instanceof SecretFilter) {
      return secretsImportService.discover(authToken, importDTO);
    }
    if (filter instanceof ApplicationFilter) {
      return appImportService.discover(authToken, importDTO);
    }
    if (filter instanceof ServiceFilter) {
      return serviceImportService.discover(authToken, importDTO);
    }
    return DiscoveryResult.builder().build();
  }

  public SaveSummaryDTO save(String authToken, ImportDTO importDTO) {
    DiscoveryResult discoveryResult = discover(authToken, importDTO);
    return discoveryService.migrateEntity(authToken, getMigrationInput(importDTO), discoveryResult);
  }

  public StreamingOutput exportYaml(String authToken, ImportDTO importDTO) {
    return discoveryService.exportYamlFilesAsZip(getMigrationInput(importDTO), discover(authToken, importDTO));
  }

  private static MigrationInputDTO getMigrationInput(ImportDTO importDTO) {
    Map<NGMigrationEntityType, InputDefaults> defaults = new HashMap<>();
    Map<CgEntityId, BaseProvidedInput> overrides = new HashMap<>();
    Map<String, String> expressions = new HashMap<>();
    if (importDTO.getInputs() != null) {
      overrides = importDTO.getInputs().getOverrides();
      defaults = importDTO.getInputs().getDefaults();
      expressions = importDTO.getInputs().getExpressions();
    }
    return MigrationInputDTO.builder()
        .accountIdentifier(importDTO.getAccountIdentifier())
        .orgIdentifier(importDTO.getDestinationDetails().getOrgIdentifier())
        .projectIdentifier(importDTO.getDestinationDetails().getProjectIdentifier())
        .migrateReferencedEntities(importDTO.isMigrateReferencedEntities())
        .overrides(overrides)
        .defaults(defaults)
        .customExpressions(expressions)
        .build();
  }
}
