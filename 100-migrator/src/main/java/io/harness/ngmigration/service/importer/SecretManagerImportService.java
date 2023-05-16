/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.importer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.SecretManagerFilter;
import io.harness.ngmigration.secrets.SecretFactory;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.secretmanagers.SecretManagerConfigService;

import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class SecretManagerImportService implements ImportService {
  @Inject private SecretManagerConfigService secretManagerConfigService;
  @Inject DiscoveryService discoveryService;
  @Inject private SecretFactory secretFactory;

  public DiscoveryResult discover(ImportDTO importConnectorDTO) {
    SecretManagerFilter filter = (SecretManagerFilter) importConnectorDTO.getFilter();
    String accountId = importConnectorDTO.getAccountIdentifier();
    List<String> secretManagerIds;
    switch (filter.getImportType()) {
      case ALL:
        // Note: All here means all the connectors we support today
        secretManagerIds = secretManagerConfigService.listSecretManagers(accountId, false)
                               .stream()
                               .filter(secretManagerConfig -> {
                                 try {
                                   secretFactory.getSecretMigrator(secretManagerConfig);
                                   return true;
                                 } catch (Exception e) {
                                   log.warn("Unsupported secret manager", e);
                                   return false;
                                 }
                               })
                               .map(SecretManagerConfig::getUuid)
                               .collect(Collectors.toList());
        break;
      case TYPE:
        secretManagerIds =
            secretManagerConfigService.listSecretManagers(accountId, false)
                .stream()
                .filter(secretManagerConfig -> filter.getTypes().contains(secretManagerConfig.getEncryptionType()))
                .filter(secretManagerConfig -> {
                  try {
                    SecretFactory.getConnectorType(secretManagerConfig);
                    return true;
                  } catch (Exception e) {
                    log.warn("Unsupported secret manager", e);
                    return false;
                  }
                })
                .map(SecretManagerConfig::getUuid)
                .collect(Collectors.toList());
        break;
      case SPECIFIC:
        secretManagerIds = filter.getIds();
        break;
      default:
        secretManagerIds = new ArrayList<>();
    }
    return discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .entities(secretManagerIds.stream()
                          .map(secretManagerId
                              -> DiscoverEntityInput.builder()
                                     .entityId(secretManagerId)
                                     .type(NGMigrationEntityType.SECRET_MANAGER)
                                     .build())
                          .collect(Collectors.toList()))
            .exportImage(false)
            .build());
  }
}
