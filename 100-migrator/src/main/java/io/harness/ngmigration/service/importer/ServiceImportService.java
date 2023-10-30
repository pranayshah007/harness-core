/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.importer;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.ServiceFilter;
import io.harness.ngmigration.service.DiscoveryService;

import software.wings.beans.Service;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.ServiceResourceService;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class ServiceImportService implements ImportService {
  @Inject DiscoveryService discoveryService;
  @Inject ServiceResourceService serviceResourceService;

  public DiscoveryResult discover(ImportDTO importDTO) {
    ServiceFilter filter = (ServiceFilter) importDTO.getFilter();
    String accountId = importDTO.getAccountIdentifier();
    String appId = filter.getAppId();

    List<String> serviceIds = filter.getIds();
    if (EmptyPredicate.isEmpty(serviceIds)) {
      List<Service> services = serviceResourceService.findServicesByAppInternal(appId);
      if (EmptyPredicate.isEmpty(services)) {
        throw new InvalidRequestException("No services found for given app");
      }
      serviceIds = services.stream().map(Service::getUuid).collect(Collectors.toList());
    }
    return discoveryService.discoverMulti(accountId,
        DiscoveryInput.builder()
            .entities(serviceIds.stream()
                          .map(id
                              -> DiscoverEntityInput.builder()
                                     .entityId(id)
                                     .appId(appId)
                                     .type(NGMigrationEntityType.SERVICE)
                                     .build())
                          .collect(Collectors.toList()))
            .exportImage(false)
            .build());
  }
}
