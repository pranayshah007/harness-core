/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.dto.BaseImportDTO;
import io.harness.ngmigration.dto.ImportConnectorDTO;
import io.harness.ngmigration.dto.ImportSecretsDTO;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.StreamingOutput;

@OwnedBy(HarnessTeam.CDC)
public class MigrationResourceService {
  @Inject private ConnectorImportService connectorImportService;
  @Inject private SecretsImportService secretsImportService;
  @Inject private DiscoveryService discoveryService;

  public List<NGYamlFile> migrateCgEntityToNG(String authToken, BaseImportDTO importDTO) throws IllegalAccessException {
    // Migrate referenced entities as well.
    importDTO.setMigrateReferencedEntities(true);
    if (importDTO instanceof ImportConnectorDTO) {
      return connectorImportService.importConnectors(authToken, (ImportConnectorDTO) importDTO);
    }
    if (importDTO instanceof ImportSecretsDTO) {
      return secretsImportService.importConnectors(authToken, (ImportSecretsDTO) importDTO);
    }
    return new ArrayList<>();
  }

  public StreamingOutput exportYaml(String authToken, BaseImportDTO importDTO) throws IllegalAccessException {
    return discoveryService.createZip(migrateCgEntityToNG(authToken, importDTO));
  }
}
