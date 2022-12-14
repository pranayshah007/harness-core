/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.usage.impl;

import static io.harness.cd.CDLicenseType.SERVICES;
import static io.harness.cd.CDLicenseType.SERVICE_INSTANCES;
import static io.harness.cdng.usage.mapper.ActiveServiceMapper.buildActiveServiceFetchData;
import static io.harness.cdng.usage.utils.LicenseUsageUtils.computeLicenseConsumed;
import static io.harness.cdng.usage.utils.LicenseUsageUtils.getEpochMilliNDaysAgo;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.DEFAULT_FILTER_PROPERTIES_VALUE;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.DISPLAY_NAME;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.TIME_PERIOD_IN_DAYS;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.ModuleType;
import io.harness.aggregates.AggregateNgServiceInstanceStats;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cd.NgServiceInfraInfoUtils;
import io.harness.cd.TimeScaleDAL;
import io.harness.cdng.usage.CDLicenseUsageDAL;
import io.harness.cdng.usage.mapper.ActiveServiceMapper;
import io.harness.cdng.usage.pojos.ActiveService;
import io.harness.cdng.usage.pojos.ActiveServiceBase;
import io.harness.cdng.usage.pojos.ActiveServiceFetchData;
import io.harness.cdng.usage.pojos.ActiveServiceResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.licensing.usage.beans.ReferenceDTO;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.licensing.usage.beans.cd.ActiveServiceDTO;
import io.harness.licensing.usage.beans.cd.CDLicenseUsageDTO;
import io.harness.licensing.usage.beans.cd.ServiceInstanceUsageDTO;
import io.harness.licensing.usage.beans.cd.ServiceUsageDTO;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.CDUsageRequestParams;
import io.harness.licensing.usage.params.DefaultPageableUsageRequestParams;
import io.harness.licensing.usage.params.PageableUsageRequestParams;
import io.harness.licensing.usage.params.filter.ActiveServicesFilterParams;
import io.harness.timescaledb.tables.pojos.ServiceInfraInfo;
import io.harness.timescaledb.tables.pojos.Services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class CDLicenseUsageImpl implements LicenseUsageInterface<CDLicenseUsageDTO, CDUsageRequestParams> {
  @Inject TimeScaleDAL timeScaleDAL;
  @Inject CDLicenseUsageDAL licenseUsageDAL;

  private final Cache<String, CDLicenseUsageDTO> serviceLicenseCache =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6L)).maximumSize(600L).build();

  private final Cache<String, CDLicenseUsageDTO> serviceInstanceLicenseCache =
      Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6L)).maximumSize(600L).build();

  @Override
  public CDLicenseUsageDTO getLicenseUsage(
      String accountIdentifier, ModuleType module, long timestamp, CDUsageRequestParams usageRequest) {
    Preconditions.checkArgument(timestamp > 0, format("Invalid timestamp %d while fetching LicenseUsages.", timestamp));
    Preconditions.checkArgument(ModuleType.CD == module, format("Invalid Module type %s provided", module.toString()));
    Preconditions.checkArgument(isNotBlank(accountIdentifier), "Account Identifier cannot be null or blank");

    if (SERVICES.equals(usageRequest.getCdLicenseType())) {
      return serviceLicenseCache.get(
          accountIdentifier, accountId -> getActiveServicesLicenseUsage(accountId, module, timestamp, usageRequest));
    } else if (SERVICE_INSTANCES.equals(usageRequest.getCdLicenseType())) {
      return serviceInstanceLicenseCache.get(
          accountIdentifier, accountId -> getServiceInstancesLicenseUsage(accountId, module));
    } else {
      throw new InvalidArgumentsException("Invalid License Type.", WingsException.USER);
    }
  }

  @Override
  public Page<ActiveServiceDTO> listLicenseUsage(
      String accountIdentifier, ModuleType module, long currentTS, PageableUsageRequestParams usageRequestParams) {
    Preconditions.checkArgument(
        currentTS > 0, format("Invalid timestamp %d while fetching licence usage for CD", currentTS));
    Preconditions.checkArgument(
        ModuleType.CD == module, format("Invalid Module type %s provided, expected CD", module.toString()));
    Preconditions.checkArgument(isNotBlank(accountIdentifier), "Account Identifier cannot be null or empty");
    DefaultPageableUsageRequestParams defaultUsageRequestParams =
        (DefaultPageableUsageRequestParams) usageRequestParams;
    Pageable pageRequest = defaultUsageRequestParams.getPageRequest();
    ActiveServicesFilterParams filterParams = (ActiveServicesFilterParams) defaultUsageRequestParams.getFilterParams();
    // the following filter calls are needed because ng_instance_stats and service_infra_info tables do not contain
    // names only identifiers
    Scope scope = getScopeFromFilterOrgAndProjectNames(accountIdentifier, filterParams);
    String serviceIdentifier = getServiceIdentifierByFilterServiceName(scope, filterParams.getServiceName());

    ActiveServiceFetchData activeServiceFetchData =
        buildActiveServiceFetchData(scope, serviceIdentifier, pageRequest, currentTS);
    log.info("Start fetching active services for accountIdentifier: {}, startTSInMs: {}, endTSInMs: {}",
        accountIdentifier, activeServiceFetchData.getStartTSInMs(), activeServiceFetchData.getEndTSInMs());
    long fetchActiveServiceQueryStartTime = System.currentTimeMillis();
    ActiveServiceResponse<List<ActiveServiceBase>> activeServiceBaseResponse =
        licenseUsageDAL.fetchActiveServices(activeServiceFetchData);
    long fetchActiveServiceQueryEndTime = System.currentTimeMillis() - fetchActiveServiceQueryStartTime;
    log.info(
        "Active services fetched successfully for accountIdentifier: {}, number of fetched items: {}, time taken in ms: {}",
        accountIdentifier, activeServiceBaseResponse.getActiveServiceItems().size(), fetchActiveServiceQueryEndTime);

    log.info(
        "Start fetching active services names, org and project names for accountIdentifier: {}", accountIdentifier);
    long updateActiveServiceQueryStartTime = System.currentTimeMillis();
    List<ActiveService> activeServices = licenseUsageDAL.fetchActiveServicesNameOrgAndProjectName(
        accountIdentifier, activeServiceBaseResponse.getActiveServiceItems());
    long updateActiveServiceQueryEndTime = System.currentTimeMillis() - updateActiveServiceQueryStartTime;
    log.info(
        "Active services names, org and project names fetched successfully for accountIdentifier: {}, time taken in ms: {}",
        accountIdentifier, updateActiveServiceQueryEndTime);

    List<ActiveServiceDTO> activeServiceDTOs =
        ActiveServiceMapper.toActiveServiceDTO(accountIdentifier, activeServices, currentTS);
    return new PageImpl<>(activeServiceDTOs, pageRequest, activeServiceBaseResponse.getTotalCountOfItems());
  }

  private Scope getScopeFromFilterOrgAndProjectNames(
      @NotNull final String accountIdentifier, ActiveServicesFilterParams filterParams) {
    String orgIdentifier = null;
    String projectIdentifier = null;
    if (isNotEmpty(filterParams.getOrgName()) && !DEFAULT_FILTER_PROPERTIES_VALUE.equals(filterParams.getOrgName())) {
      // TODO - getOrgIdentifier by account id and org name (will be done as part of CDS-47336)
      throw new NotImplementedException("Not implemented filtering by org name");
    }

    if (!DEFAULT_FILTER_PROPERTIES_VALUE.equals(filterParams.getProjectName())
        && isNotEmpty(filterParams.getProjectName())) {
      // TODO - getProjectIdentifier by account id, org name and project name (will be done as part of CDS-47336)
      throw new NotImplementedException("Not implemented filtering by project name");
    }

    return Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  private String getServiceIdentifierByFilterServiceName(Scope scope, final String serviceName) {
    String serviceIdentifier = null;
    if (!DEFAULT_FILTER_PROPERTIES_VALUE.equals(serviceName) && isNotEmpty(serviceName)) {
      // TODO - getOrgIdentifier by account id, org name and project name (will be done as part of CDS-47336)
      throw new NotImplementedException("Not implemented filtering by service name");
    }

    return serviceIdentifier;
  }

  private ServiceInstanceUsageDTO getServiceInstancesLicenseUsage(String accountIdentifier, ModuleType module) {
    long serviceInstances = licenseUsageDAL.fetchServiceInstancesOver30Days(accountIdentifier);
    List<AggregateServiceUsageInfo> instanceCountsPerService =
        licenseUsageDAL.fetchInstancesPerServiceOver30Days(accountIdentifier);
    if (CollectionUtils.isEmpty(instanceCountsPerService)) {
      return ServiceInstanceUsageDTO.builder()
          .activeServiceInstances(
              UsageDataDTO.builder().count(serviceInstances).displayName(DISPLAY_NAME).references(emptyList()).build())
          .cdLicenseType(SERVICE_INSTANCES)
          .accountIdentifier(accountIdentifier)
          .module(module.getDisplayName())
          .build();
    }

    List<Services> serviceNames = timeScaleDAL.getNamesForServiceIds(
        accountIdentifier, NgServiceInfraInfoUtils.getOrgProjectServiceTable(instanceCountsPerService));
    return ServiceInstanceUsageDTO.builder()
        .activeServiceInstances(UsageDataDTO.builder()
                                    .count(serviceInstances)
                                    .displayName(DISPLAY_NAME)
                                    .references(buildInstancesPerServiceReferences(
                                        accountIdentifier, instanceCountsPerService, serviceNames))
                                    .build())
        .cdLicenseType(SERVICE_INSTANCES)
        .accountIdentifier(accountIdentifier)
        .module(module.getDisplayName())
        .build();
  }

  private CDLicenseUsageDTO getActiveServicesLicenseUsage(
      String accountIdentifier, ModuleType module, long timestamp, CDUsageRequestParams usageRequest) {
    long startInterval = getEpochMilliNDaysAgo(timestamp, TIME_PERIOD_IN_DAYS);
    List<ServiceInfraInfo> activeServiceList =
        timeScaleDAL.getDistinctServiceWithExecutionInTimeRange(accountIdentifier, startInterval, timestamp);

    if (CollectionUtils.isEmpty(activeServiceList)) {
      return getEmptyUsageData(accountIdentifier, module, usageRequest);
    }

    List<Services> serviceNames = timeScaleDAL.getNamesForServiceIds(
        accountIdentifier, NgServiceInfraInfoUtils.getOrgProjectServiceTable(activeServiceList));
    List<AggregateServiceUsageInfo> instanceCountsPerService =
        licenseUsageDAL.fetchInstancesPerServiceOver30Days(accountIdentifier);

    List<AggregateNgServiceInstanceStats> instancesForActiveService =
        instanceCountsPerService.parallelStream()
            .filter(serviceInstanceCounts
                -> activeServiceList.parallelStream().anyMatch(activeService
                    -> activeService.getOrgidentifier().equals(serviceInstanceCounts.getOrgidentifier())
                        && activeService.getProjectidentifier().equals(serviceInstanceCounts.getProjectidentifier())
                        && activeService.getServiceId().equals(serviceInstanceCounts.getServiceId())))
            .map(serviceInstanceCounts
                -> new AggregateNgServiceInstanceStats(serviceInstanceCounts.getOrgidentifier(),
                    serviceInstanceCounts.getProjectidentifier(), serviceInstanceCounts.getServiceId(),
                    serviceInstanceCounts.getServiceInstanceCount()))
            .collect(Collectors.toList());

    Map<String, Map<String, Map<String, Pair<String, Long>>>> activeServicesNameAndInstanceCount =
        getServiceNamesMap(serviceNames, instancesForActiveService);

    return ServiceUsageDTO.builder()
        .activeServices(getServicesUsage(activeServiceList, activeServicesNameAndInstanceCount))
        .serviceLicenses(getServicesUsageWithLicense(activeServiceList, activeServicesNameAndInstanceCount))
        .activeServiceInstances(
            getServicesUsageWithInstanceCount(activeServiceList, activeServicesNameAndInstanceCount))
        .cdLicenseType(SERVICES)
        .accountIdentifier(accountIdentifier)
        .module(module.getDisplayName())
        .build();
  }

  private List<ReferenceDTO> buildInstancesPerServiceReferences(
      String accountId, List<AggregateServiceUsageInfo> instanceCountsPerService, List<Services> serviceNames) {
    return instanceCountsPerService.parallelStream()
        .map(serviceInstanceUsage -> {
          return ReferenceDTO.builder()
              .accountIdentifier(accountId)
              .orgIdentifier(serviceInstanceUsage.getOrgidentifier())
              .projectIdentifier(serviceInstanceUsage.getProjectidentifier())
              .identifier(serviceInstanceUsage.getServiceId())
              .count(serviceInstanceUsage.getServiceInstanceCount())
              .name(findServiceName(serviceInstanceUsage.getOrgidentifier(),
                  serviceInstanceUsage.getProjectidentifier(), serviceInstanceUsage.getServiceId(), serviceNames))
              .build();
        })
        .collect(Collectors.toList());
  }

  private String findServiceName(
      String orgIdentifier, String projectIdentifier, String serviceId, List<Services> serviceNames) {
    Optional<Services> optionalService = serviceNames.parallelStream()
                                             .filter(service
                                                 -> service.getOrgIdentifier().equals(orgIdentifier)
                                                     && service.getProjectIdentifier().equals(projectIdentifier)
                                                     && service.getIdentifier().equals(serviceId))
                                             .findFirst();

    return optionalService.isPresent() ? optionalService.get().getName() : StringUtils.EMPTY;
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
        -> serviceNamesMap.computeIfAbsent(serviceWithInstanceCount.getOrgid(), key -> new HashMap<>())
               .computeIfAbsent(serviceWithInstanceCount.getProjectid(), key -> new HashMap<>())
               .computeIfAbsent(serviceWithInstanceCount.getServiceid(), key -> Pair.of(StringUtils.EMPTY, 0L)));

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
}
