/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.importer;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.SecretFilter;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.persistence.HPersistence;

import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
public class SecretsImportService implements ImportService {
  @Inject private HPersistence hPersistence;
  @Inject DiscoveryService discoveryService;

  public DiscoveryResult discover(ImportDTO importDTO) {
    SecretFilter filter = (SecretFilter) importDTO.getFilter();
    String accountId = importDTO.getAccountIdentifier();
    List<String> secretIds = new ArrayList<>();
    switch (filter.getImportType()) {
      case ALL:
        List<EncryptedData> encryptedDataList = hPersistence.createQuery(EncryptedData.class)
                                                    .filter(EncryptedDataKeys.accountId, accountId)
                                                    .filter(EncryptedDataKeys.hideFromListing, false)
                                                    .asList();
        if (EmptyPredicate.isNotEmpty(encryptedDataList)) {
          secretIds = encryptedDataList.stream().map(EncryptedData::getUuid).collect(Collectors.toList());
        }
        break;
      case SPECIFIC:
        secretIds = filter.getIds();
        break;
      default:
        secretIds = new ArrayList<>();
    }
    return discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .entities(
                secretIds.stream()
                    .map(settingId
                        -> DiscoverEntityInput.builder().entityId(settingId).type(NGMigrationEntityType.SECRET).build())
                    .collect(Collectors.toList()))
            .exportImage(false)
            .build());
  }
}
