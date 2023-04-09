/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.importer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.dto.ApplicationFilter;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.service.DiscoveryService;

import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CDC)
public class AppImportService implements ImportService {
  @Inject DiscoveryService discoveryService;

  public DiscoveryResult discover(ImportDTO importConnectorDTO) {
    ApplicationFilter filter = (ApplicationFilter) importConnectorDTO.getFilter();
    String accountId = importConnectorDTO.getAccountIdentifier();
    String appId = filter.getAppId();
    return discoveryService.discover(accountId, appId, appId, NGMigrationEntityType.APPLICATION, null);
  }
}
