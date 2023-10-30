/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.mapper;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.TIME_PERIOD_30_DAYS;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.cdng.usage.pojos.ActiveService;
import io.harness.cdng.usage.pojos.ActiveServiceFetchData;
import io.harness.cdng.usage.utils.LicenseUsageUtils;
import io.harness.licensing.usage.beans.cd.ActiveServiceDTO;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Pageable;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class ActiveServiceMapper {
  public static List<ActiveServiceDTO> toActiveServiceDTO(
      String accountIdentifier, List<ActiveService> activeServices, long currentTS) {
    return activeServices.stream()
        .map(activeServiceInfo
            -> ActiveServiceDTO.builder()
                   .accountIdentifier(accountIdentifier)
                   .orgIdentifier(activeServiceInfo.getOrgIdentifier())
                   .projectIdentifier(activeServiceInfo.getProjectIdentifier())
                   .identifier(activeServiceInfo.getIdentifier())
                   .name(activeServiceInfo.getName())
                   .orgName(activeServiceInfo.getOrgName())
                   .projectName(activeServiceInfo.getProjectName())
                   .instanceCount(activeServiceInfo.getInstanceCount())
                   .lastDeployed(activeServiceInfo.getLastDeployed())
                   .licensesConsumed(LicenseUsageUtils.computeLicenseConsumed(activeServiceInfo.getInstanceCount()))
                   .module(ModuleType.CD.getDisplayName())
                   .timestamp(currentTS)
                   .build())
        .collect(Collectors.toList());
  }

  public static ActiveServiceFetchData buildActiveServiceFetchData(
      Scope scope, String serviceIdentifier, Pageable pageRequest, long timestamp) {
    long startInterval = LicenseUsageUtils.getEpochMilliNDaysAgo(timestamp, TIME_PERIOD_30_DAYS);
    return ActiveServiceFetchData.builder()
        .accountIdentifier(scope.getAccountIdentifier())
        .orgIdentifier(scope.getOrgIdentifier())
        .projectIdentifier(scope.getProjectIdentifier())
        .serviceIdentifier(serviceIdentifier)
        .pageSize(pageRequest.getPageSize())
        .pageNumber(pageRequest.getPageNumber())
        .sort(pageRequest.getSort())
        .startTSInMs(startInterval)
        .endTSInMs(timestamp)
        .build();
  }

  public static ActiveServiceFetchData buildActiveServiceFetchData(
      String accountIdentifier, Pageable pageRequest, long currentTSInMS) {
    long startTSInMs = LicenseUsageUtils.getEpochMilliNDaysAgo(currentTSInMS, TIME_PERIOD_30_DAYS);
    return ActiveServiceFetchData.builder()
        .accountIdentifier(accountIdentifier)
        .pageSize(pageRequest.getPageSize())
        .pageNumber(pageRequest.getPageNumber())
        .sort(pageRequest.getSort())
        .startTSInMs(startTSInMs)
        .endTSInMs(currentTSInMS)
        .build();
  }
}
