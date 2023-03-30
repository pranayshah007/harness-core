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
import io.harness.exception.InvalidRequestException;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.ServiceFilter;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.ngmigration.service.servicev2.ServiceV2Factory;

import software.wings.beans.Service;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.ServiceResourceService;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDC)
public class ServiceImportService implements ImportService {
  @Inject DiscoveryService discoveryService;
  @Inject ServiceResourceService serviceResourceService;

  public DiscoveryResult discover(String authToken, ImportDTO importConnectorDTO) {
    ServiceFilter filter = (ServiceFilter) importConnectorDTO.getFilter();
    String accountId = importConnectorDTO.getAccountIdentifier();
    String appId = filter.getAppId();

    List<Service> services = serviceResourceService.findServicesByAppInternal(appId);
    if (EmptyPredicate.isEmpty(services)) {
      throw new InvalidRequestException("No services found for given app");
    }
    return discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .entities(services.stream()
                          .filter(service -> ServiceV2Factory.getService2Mapper(service, false).isMigrationSupported())
                          .map(service
                              -> DiscoverEntityInput.builder()
                                     .entityId(service.getUuid())
                                     .appId(appId)
                                     .type(NGMigrationEntityType.SERVICE)
                                     .build())
                          .collect(Collectors.toList()))
            .exportImage(false)
            .build());
  }
}
