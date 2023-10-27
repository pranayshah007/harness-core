/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.service.impl;

import static io.harness.idp.license.usage.dto.IDPActiveDevelopersDTO.fromActiveDevelopersEntity;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.license.usage.dto.IDPActiveDevelopersDTO;
import io.harness.idp.license.usage.dto.IDPLicenseUsageDTO;
import io.harness.idp.license.usage.entities.ActiveDevelopersEntity;
import io.harness.idp.license.usage.repositories.ActiveDevelopersRepository;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.DefaultPageableUsageRequestParams;
import io.harness.licensing.usage.params.PageableUsageRequestParams;
import io.harness.licensing.usage.params.UsageRequestParams;
import io.harness.licensing.usage.params.filter.ActiveDevelopersFilterParams;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IDPLicenseUsageImpl implements LicenseUsageInterface<IDPLicenseUsageDTO, UsageRequestParams> {
  @Inject private ActiveDevelopersRepository activeDevelopersRepository;

  @Override
  public IDPLicenseUsageDTO getLicenseUsage(
      String accountIdentifier, ModuleType module, long timestamp, UsageRequestParams usageRequest) {
    return IDPLicenseUsageDTO.builder()
        .accountIdentifier(accountIdentifier)
        .module(module.getDisplayName())
        .timestamp(System.currentTimeMillis())
        .activeDevelopers(UsageDataDTO.builder()
                              .displayName("Total active IDP users")
                              .count(activeDevelopersRepository.findByAccountIdentifier(accountIdentifier).size())
                              .build())
        .build();
  }

  @Override
  public Page<IDPActiveDevelopersDTO> listLicenseUsage(
      String accountIdentifier, ModuleType module, long currentTS, PageableUsageRequestParams usageRequest) {
    DefaultPageableUsageRequestParams defaultUsageRequestParams = (DefaultPageableUsageRequestParams) usageRequest;
    Pageable pageRequest = defaultUsageRequestParams.getPageRequest();
    ActiveDevelopersFilterParams filter = (ActiveDevelopersFilterParams) defaultUsageRequestParams.getFilterParams();
    String userIdentifierFilter = filter.getUserIdentifier();
    final List<ActiveDevelopersEntity> activeDevelopersEntities = new ArrayList<>();
    if (StringUtils.isNotEmpty(userIdentifierFilter)) {
      Optional<ActiveDevelopersEntity> activeDevelopersEntityOptional =
          activeDevelopersRepository.findByAccountIdentifierAndUserIdentifier(accountIdentifier, userIdentifierFilter);
      activeDevelopersEntityOptional.ifPresent(activeDevelopersEntities::add);
    } else {
      activeDevelopersEntities.addAll(activeDevelopersRepository.findByAccountIdentifier(accountIdentifier));
    }
    List<ActiveDevelopersEntity> sortedActiveDevelopersEntities;
    if (pageRequest.getSort().getOrderFor("lastAccessedAt").getDirection().isDescending()) {
      sortedActiveDevelopersEntities =
          activeDevelopersEntities.stream()
              .sorted(Comparator.comparing(ActiveDevelopersEntity::getLastAccessedAt).reversed())
              .collect(Collectors.toList());
    } else {
      sortedActiveDevelopersEntities = activeDevelopersEntities.stream()
                                           .sorted(Comparator.comparing(ActiveDevelopersEntity::getLastAccessedAt))
                                           .collect(Collectors.toList());
    }
    List<IDPActiveDevelopersDTO> activeDevelopers = new ArrayList<>();
    sortedActiveDevelopersEntities.forEach(
        activeDevelopersEntity -> activeDevelopers.add(fromActiveDevelopersEntity(activeDevelopersEntity)));
    return PageUtils.getPage(activeDevelopers, pageRequest.getPageNumber(), pageRequest.getPageSize());
  }

  @Override
  public File getLicenseUsageCSVReport(String accountIdentifier, ModuleType moduleType, long currentTsInMs) {
    return null;
  }
}
