/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.usage.impl;

import static io.harness.cd.CDLicenseType.SERVICES;
import static io.harness.cd.CDLicenseType.SERVICE_INSTANCES;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.DISPLAY_NAME;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.PERCENTILE;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.SERVICE_INSTANCE_LIMIT;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.TIME_PERIOD_IN_DAYS;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.ModuleType;
import io.harness.aggregates.AggregateNgServiceInstanceStats;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.NgServiceInfraInfoUtils;
import io.harness.cd.TimeScaleDAL;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.licensing.usage.beans.ReferenceDTO;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.licensing.usage.beans.cd.CDLicenseUsageDTO;
import io.harness.licensing.usage.beans.cd.ServiceInstanceUsageDTO;
import io.harness.licensing.usage.beans.cd.ServiceUsageDTO;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.CDUsageRequestParams;
import io.harness.timescaledb.tables.pojos.ServiceInfraInfo;
import io.harness.timescaledb.tables.pojos.Services;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.Record3;
import org.jooq.Table;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class CDLicenseUsageImpl implements LicenseUsageInterface<CDLicenseUsageDTO, CDUsageRequestParams> {
  @Inject TimeScaleDAL timeScaleDAL;

  @Override
  public CDLicenseUsageDTO getLicenseUsage(
      String accountIdentifier, ModuleType module, long timestamp, CDUsageRequestParams usageRequest) {
    Preconditions.checkArgument(timestamp > 0, format("Invalid timestamp %d while fetching LicenseUsages.", timestamp));
    Preconditions.checkArgument(ModuleType.CD == module, format("Invalid Module type %s provided", module.toString()));
    Preconditions.checkArgument(
        StringUtils.isNotBlank(accountIdentifier), "Account Identifier cannot be null or blank");

    long startInterval = getEpochMilliNDaysAgo(timestamp, TIME_PERIOD_IN_DAYS);
    List<ServiceInfraInfo> activeServiceList =
        timeScaleDAL.getDistinctServiceWithExecutionInTimeRange(accountIdentifier, startInterval, timestamp);

    if (CollectionUtils.isEmpty(activeServiceList)) {
      return getEmptyUsageData(accountIdentifier, module, usageRequest);
    }

    Table<Record3<String, String, String>> activeServiceIdentifiers =
        NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList);
    List<AggregateNgServiceInstanceStats> activeServiceWithInstanceCountList =
        timeScaleDAL.getServiceWith95PercentileServiceInstanceCount(
            accountIdentifier, PERCENTILE, startInterval, timestamp, activeServiceIdentifiers);

    Map<String, Map<String, Map<String, Pair<String, Long>>>> activeServicesNameAndInstanceCount =
        getServiceNamesMap(timeScaleDAL.getNamesForServiceIds(accountIdentifier, activeServiceIdentifiers),
            activeServiceWithInstanceCountList);

    switch (usageRequest.getCdLicenseType()) {
      case SERVICES:
        return ServiceUsageDTO.builder()
            .activeServices(getServicesUsage(activeServiceList, activeServicesNameAndInstanceCount))
            .serviceLicenses(getServicesUsageWithLicense(activeServiceList, activeServicesNameAndInstanceCount))
            .activeServiceInstances(
                getServicesUsageWithInstanceCount(activeServiceList, activeServicesNameAndInstanceCount))
            .cdLicenseType(SERVICES)
            .accountIdentifier(accountIdentifier)
            .module(module.getDisplayName())
            .build();
      case SERVICE_INSTANCES:
        return ServiceInstanceUsageDTO.builder()
            .activeServiceInstances(
                getServicesUsageWithInstanceCount(activeServiceList, activeServicesNameAndInstanceCount))
            .cdLicenseType(SERVICE_INSTANCES)
            .accountIdentifier(accountIdentifier)
            .module(module.getDisplayName())
            .build();
      default:
        throw new InvalidArgumentsException("Invalid License Type.", WingsException.USER);
    }
  }

  private CDLicenseUsageDTO getEmptyUsageData(
      String accountIdentifier, ModuleType module, CDUsageRequestParams usageRequest) {
    switch (usageRequest.getCdLicenseType()) {
      case SERVICES:
        return ServiceUsageDTO.builder()
            .activeServices(UsageDataDTO.builder().count(0).displayName(DISPLAY_NAME).references(emptyList()).build())
            .serviceLicenses(UsageDataDTO.builder().count(0).displayName(DISPLAY_NAME).references(emptyList()).build())
            .activeServiceInstances(
                UsageDataDTO.builder().count(0).displayName(DISPLAY_NAME).references(emptyList()).build())
            .cdLicenseType(SERVICES)
            .accountIdentifier(accountIdentifier)
            .module(module.getDisplayName())
            .build();
      case SERVICE_INSTANCES:
        return ServiceInstanceUsageDTO.builder()
            .activeServiceInstances(
                UsageDataDTO.builder().count(0).displayName(DISPLAY_NAME).references(emptyList()).build())
            .cdLicenseType(SERVICE_INSTANCES)
            .accountIdentifier(accountIdentifier)
            .module(module.getDisplayName())
            .build();
      default:
        throw new InvalidArgumentsException("Invalid License Type.", WingsException.USER);
    }
  }

  private UsageDataDTO getServicesUsageWithInstanceCount(List<ServiceInfraInfo> activeServices,
      Map<String, Map<String, Map<String, Pair<String, Long>>>> activeServicesNameAndInstanceCount) {
    UsageDataDTO usageData =
        UsageDataDTO.builder()
            .displayName(DISPLAY_NAME)
            .references(
                activeServices.parallelStream()
                    .map(activeService
                        -> ReferenceDTO.builder()
                               .accountIdentifier(activeService.getAccountid())
                               .orgIdentifier(activeService.getOrgidentifier())
                               .projectIdentifier(activeService.getProjectidentifier())
                               .identifier(activeService.getServiceId())
                               .name(fetchServiceName(activeServicesNameAndInstanceCount, activeService))
                               .count(fetchServiceInstanceCount(activeServicesNameAndInstanceCount, activeService))
                               .build())
                    .collect(Collectors.toList()))
            .build();

    usageData.setCount(usageData.getReferences().stream().mapToLong(ReferenceDTO::getCount).sum());
    return usageData;
  }

  private UsageDataDTO getServicesUsageWithLicense(List<ServiceInfraInfo> activeServices,
      Map<String, Map<String, Map<String, Pair<String, Long>>>> activeServicesNameAndInstanceCount) {
    UsageDataDTO usageData =
        UsageDataDTO.builder()
            .displayName(DISPLAY_NAME)
            .references(activeServices.parallelStream()
                            .map(activeService
                                -> ReferenceDTO.builder()
                                       .accountIdentifier(activeService.getAccountid())
                                       .orgIdentifier(activeService.getOrgidentifier())
                                       .projectIdentifier(activeService.getProjectidentifier())
                                       .identifier(activeService.getServiceId())
                                       .name(fetchServiceName(activeServicesNameAndInstanceCount, activeService))
                                       .count(computeLicenseConsumed(fetchServiceInstanceCount(
                                           activeServicesNameAndInstanceCount, activeService)))
                                       .build())
                            .collect(Collectors.toList()))
            .build();

    usageData.setCount(usageData.getReferences().stream().mapToLong(ReferenceDTO::getCount).sum());
    return usageData;
  }

  private long computeLicenseConsumed(long serviceInstanceCount) {
    if (serviceInstanceCount <= SERVICE_INSTANCE_LIMIT) {
      return 1;
    } else {
      return ((serviceInstanceCount - 1) / SERVICE_INSTANCE_LIMIT) + 1;
    }
  }

  private UsageDataDTO getServicesUsage(List<ServiceInfraInfo> activeServices,
      Map<String, Map<String, Map<String, Pair<String, Long>>>> activeServicesNameAndInstanceCount) {
    return UsageDataDTO.builder()
        .count(activeServices.size())
        .displayName(DISPLAY_NAME)
        .references(activeServices.parallelStream()
                        .map(activeService
                            -> ReferenceDTO.builder()
                                   .accountIdentifier(activeService.getAccountid())
                                   .orgIdentifier(activeService.getOrgidentifier())
                                   .projectIdentifier(activeService.getProjectidentifier())
                                   .identifier(activeService.getServiceId())
                                   .name(fetchServiceName(activeServicesNameAndInstanceCount, activeService))
                                   .build())
                        .collect(Collectors.toList()))
        .build();
  }

  private String fetchServiceName(
      Map<String, Map<String, Map<String, Pair<String, Long>>>> activeServicesNameAndInstanceCount,
      ServiceInfraInfo service) {
    if (!activeServicesNameAndInstanceCount.containsKey(service.getOrgidentifier())
        || !activeServicesNameAndInstanceCount.get(service.getOrgidentifier())
                .containsKey(service.getProjectidentifier())
        || !activeServicesNameAndInstanceCount.get(service.getOrgidentifier())
                .get(service.getProjectidentifier())
                .containsKey(service.getServiceId())) {
      return StringUtils.EMPTY;
    }

    return activeServicesNameAndInstanceCount.get(service.getOrgidentifier())
        .get(service.getProjectidentifier())
        .get(service.getServiceId())
        .getLeft();
  }

  private Long fetchServiceInstanceCount(
      Map<String, Map<String, Map<String, Pair<String, Long>>>> activeServicesNameAndInstanceCount,
      ServiceInfraInfo service) {
    if (!activeServicesNameAndInstanceCount.containsKey(service.getOrgidentifier())
        || !activeServicesNameAndInstanceCount.get(service.getOrgidentifier())
                .containsKey(service.getProjectidentifier())
        || !activeServicesNameAndInstanceCount.get(service.getOrgidentifier())
                .get(service.getProjectidentifier())
                .containsKey(service.getServiceId())) {
      return 0L;
    }

    return activeServicesNameAndInstanceCount.get(service.getOrgidentifier())
        .get(service.getProjectidentifier())
        .get(service.getServiceId())
        .getRight();
  }

  private Map<String, Map<String, Map<String, Pair<String, Long>>>> getServiceNamesMap(
      List<Services> serviceList, List<AggregateNgServiceInstanceStats> activeServiceWithInstanceCountList) {
    Map<String, Map<String, Map<String, Pair<String, Long>>>> serviceNamesMap = new HashMap<>();
    if (CollectionUtils.isEmpty(serviceList)) {
      return serviceNamesMap;
    }

    serviceList.forEach(service
        -> serviceNamesMap.computeIfAbsent(service.getOrgIdentifier(), key -> new HashMap<>())
               .computeIfAbsent(service.getProjectIdentifier(), key -> new HashMap<>())
               .computeIfAbsent(service.getIdentifier(), name -> Pair.of(service.getName(), 0L)));

    activeServiceWithInstanceCountList.forEach(serviceWithInstanceCount
        -> serviceNamesMap.get(serviceWithInstanceCount.getOrgid())
               .get(serviceWithInstanceCount.getProjectid())
               .put(serviceWithInstanceCount.getServiceid(),
                   Pair.of(serviceNamesMap.get(serviceWithInstanceCount.getOrgid())
                               .get(serviceWithInstanceCount.getProjectid())
                               .get(serviceWithInstanceCount.getServiceid())
                               .getLeft(),
                       serviceWithInstanceCount.getAggregateServiceInstanceCount())));

    return serviceNamesMap;
  }

  private long getEpochMilliNDaysAgo(long timestamp, int days) {
    return Instant.ofEpochMilli(timestamp).minus(Period.ofDays(days)).toEpochMilli();
  }
}
