/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.importer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.dto.EnvironmentFilter;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.persistence.HPersistence;

import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentImportService implements ImportService {
  @Inject DiscoveryService discoveryService;
  @Inject HPersistence hPersistence;

  public DiscoveryResult discover(ImportDTO importDTO) {
    EnvironmentFilter filter = (EnvironmentFilter) importDTO.getFilter();
    String accountId = importDTO.getAccountIdentifier();
    String appId = filter.getAppId();

    List<String> environmentIds = filter.getIds();
    if (EmptyPredicate.isEmpty(environmentIds)) {
      List<Environment> environments = hPersistence.createQuery(Environment.class)
                                           .filter(Environment.ACCOUNT_ID_KEY, accountId)
                                           .filter(EnvironmentKeys.appId, appId)
                                           .project(EnvironmentKeys.uuid, true)
                                           .asList();
      if (EmptyPredicate.isNotEmpty(environments)) {
        environmentIds = environments.stream().map(Environment::getUuid).collect(Collectors.toList());
      }
    }
    if (EmptyPredicate.isEmpty(environmentIds)) {
      return null;
    }

    return discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .entities(environmentIds.stream()
                          .map(id
                              -> DiscoverEntityInput.builder()
                                     .entityId(id)
                                     .appId(appId)
                                     .type(NGMigrationEntityType.ENVIRONMENT)
                                     .build())
                          .collect(Collectors.toList()))
            .exportImage(false)
            .build());
  }
}
