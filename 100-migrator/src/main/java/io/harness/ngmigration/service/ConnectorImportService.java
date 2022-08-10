/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.dto.ImportConnectorDTO;

import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConnectorImportService {
  @Inject private SettingsService settingsService;
  @Inject DiscoveryService discoveryService;

  public List<NGYamlFile> importConnectors(String authToken, String accountId, ImportConnectorDTO importConnectorDTO) {
    List<String> settingIds;
    switch (importConnectorDTO.getMechanism()) {
      case ALL:
        settingIds = settingsService.getSettingIdsForAccount(accountId);
        break;
      case TYPE:
        settingIds =
            importConnectorDTO.getTypes()
                .stream()
                .flatMap(type -> settingsService.getGlobalSettingAttributesByType(accountId, type.name()).stream())
                .map(SettingAttribute::getUuid)
                .collect(Collectors.toList());
        break;
      case SPECIFIC:
        settingIds = importConnectorDTO.getIds();
        break;
      default:
        settingIds = new ArrayList<>();
    }
    DiscoveryResult discoveryResult = discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .entities(settingIds.stream()
                          .map(settingId
                              -> DiscoverEntityInput.builder()
                                     .entityId(settingId)
                                     .type(NGMigrationEntityType.CONNECTOR)
                                     .build())
                          .collect(Collectors.toList()))
            .exportImage(false)
            .build());
    return discoveryService.migrateEntity(authToken, importConnectorDTO, discoveryResult, false);
  }
}
