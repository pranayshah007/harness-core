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
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.DISPLAY_NAME;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.SERVICE_INSTANCES_QUERY_PROPERTY;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.TIME_PERIOD_IN_DAYS;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.ModuleType;
import io.harness.aggregates.AggregateNgServiceInstanceStats;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cd.CDLicenseType;
import io.harness.cd.NgServiceInfraInfoUtils;
import io.harness.cd.TimeScaleDAL;
import io.harness.cdng.usage.CDLicenseUsageDAL;
import io.harness.cdng.usage.dto.LicenseDateUsageDTO;
import io.harness.cdng.usage.dto.LicenseDateUsageParams;
import io.harness.cdng.usage.mapper.ActiveServiceMapper;
import io.harness.cdng.usage.mapper.ServiceInstancesDateUsageMapper;
import io.harness.cdng.usage.pojos.ActiveService;
import io.harness.cdng.usage.pojos.ActiveServiceBase;
import io.harness.cdng.usage.pojos.ActiveServiceFetchData;
import io.harness.cdng.usage.pojos.ActiveServiceResponse;
import io.harness.cdng.usage.pojos.LicenseDateUsageFetchData;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.filesystem.FileIo;
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
import io.harness.licensing.usage.utils.PageableUtils;
import io.harness.timescaledb.tables.pojos.ServiceInfraInfo;
import io.harness.timescaledb.tables.pojos.Services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class CDLicenseUsageImpl implements LicenseUsageInterface<CDLicenseUsageDTO, CDUsageRequestParams> {
  private static final String ACTIVE_SERVICE_CSV_REPORTS_TMP_DIR = "active-service-csv-reports";
  private static final int PAGE_SIZE_ACTIVE_SERVICE_DOWNLOAD_CSV = 500;
  private static final String[] ACTIVE_SERVICES_CSV_REPORT_HEADER = new String[] {
      "SERVICE", "ORGANIZATIONS", "PROJECTS", "SERVICE ID", "SERVICE INSTANCES", "LAST DEPLOYED", "LICENSES CONSUMED"};
  private static final String ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG = "Account Identifier cannot be null or empty";

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
      String accountIdentifier, ModuleType module, long currentTsInMs, PageableUsageRequestParams usageRequestParams) {
    if (currentTsInMs <= 0) {
      throw new InvalidArgumentsException(
          format("Invalid timestamp %d while fetching CD active services", currentTsInMs));
    }
    if (ModuleType.CD != module) {
      throw new InvalidArgumentsException(format("Invalid Module type %s provided, expected CD", module.toString()));
    }
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException(ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG);
    }

    DefaultPageableUsageRequestParams defaultUsageRequestParams =
        (DefaultPageableUsageRequestParams) usageRequestParams;
    Pageable pageRequest = defaultUsageRequestParams.getPageRequest();
    ActiveServicesFilterParams filterParams = (ActiveServicesFilterParams) defaultUsageRequestParams.getFilterParams();
    Scope scope = getScope(accountIdentifier, filterParams);
    String serviceIdentifier = filterParams != null ? filterParams.getServiceIdentifier() : null;

    ActiveServiceFetchData activeServiceFetchData =
        buildActiveServiceFetchData(scope, serviceIdentifier, pageRequest, currentTsInMs);
    return listActiveServiceDTOs(activeServiceFetchData, pageRequest, currentTsInMs);
  }

  public LicenseDateUsageDTO getLicenseDateUsage(
      String accountIdentifier, LicenseDateUsageParams licenseDateUsageParams, CDLicenseType licenseType) {
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException(ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG);
    }
    if (licenseType == null) {
      throw new InvalidArgumentsException("CD license type cannot be null");
    }

    LicenseDateUsageFetchData licenseDateUsageFetchData =
        ServiceInstancesDateUsageMapper.buildServiceInstancesDateUsageFetchData(
            accountIdentifier, licenseDateUsageParams, licenseType);
    log.info("Start fetching license date usage, accountIdentifier: {}, fromDate: {}, toDate: {}, reportType: {}",
        accountIdentifier, licenseDateUsageFetchData.getFromDate(), licenseDateUsageFetchData.getToDate(),
        licenseDateUsageFetchData.getReportType());
    Map<String, Integer> licenseUsage = licenseUsageDAL.fetchLicenseDateUsage(licenseDateUsageFetchData);

    return LicenseDateUsageDTO.builder()
        .licenseUsage(licenseUsage)
        .reportType(licenseDateUsageFetchData.getReportType())
        .licenseType(licenseType)
        .build();
  }

  @Override
  public File getLicenseUsageCSVReport(String accountIdentifier, ModuleType moduleType, long reportEndTSInMS) {
    if (reportEndTSInMS <= 0) {
      throw new InvalidArgumentsException(
          format("Invalid timestamp %d while downloading CD active services report", reportEndTSInMS));
    }
    if (ModuleType.CD != moduleType) {
      throw new InvalidArgumentsException(
          format("Invalid Module type %s provided, expected CD", moduleType.toString()));
    }
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException(ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG);
    }

    Path accountCSVReportDir = createAccountCSVReportDirIfNotExist(accountIdentifier);
    Path csvReportFilePath = getAccountCSVReportFilePath(accountCSVReportDir, accountIdentifier, reportEndTSInMS);

    int page = 0;
    PageImpl<ActiveServiceDTO> activeServiceDTOS;
    do {
      Pageable pageRequest = PageableUtils.getPageRequest(page, PAGE_SIZE_ACTIVE_SERVICE_DOWNLOAD_CSV, emptyList(),
          Sort.by(Sort.Direction.DESC, SERVICE_INSTANCES_QUERY_PROPERTY));
      ActiveServiceFetchData activeServiceFetchData =
          buildActiveServiceFetchData(accountIdentifier, pageRequest, reportEndTSInMS);
      activeServiceDTOS = listActiveServiceDTOs(activeServiceFetchData, pageRequest, reportEndTSInMS);

      CSVFormat format = page == 0 ? CSVFormat.DEFAULT.withHeader(ACTIVE_SERVICES_CSV_REPORT_HEADER)
                                   : CSVFormat.DEFAULT.withSkipHeaderRecord();
      long printActiveServicesToCSVStartTime = System.currentTimeMillis();
      printActiveServicesToCSV(csvReportFilePath, activeServiceDTOS.getContent(), format);
      long printActiveServicesToCSVEndTime = System.currentTimeMillis() - printActiveServicesToCSVStartTime;
      log.info("Active services printed successfully to file: {}, page: {}, time taken in ms: {}", csvReportFilePath,
          page, printActiveServicesToCSVEndTime);
    } while (++page < activeServiceDTOS.getTotalPages());

    return csvReportFilePath.toFile();
  }

  private PageImpl<ActiveServiceDTO> listActiveServiceDTOs(
      ActiveServiceFetchData activeServiceFetchData, Pageable pageRequest, long currentTsInMs) {
    String accountIdentifier = activeServiceFetchData.getAccountIdentifier();
    log.info("Start fetching active services for accountIdentifier: {}, startTSInMs: {}, endTSInMs: {}",
        accountIdentifier, activeServiceFetchData.getStartTSInMs(), activeServiceFetchData.getEndTSInMs());
    long fetchActiveServiceQueryStartTime = System.currentTimeMillis();
    ActiveServiceResponse<List<ActiveServiceBase>> activeServiceBaseResponse =
        licenseUsageDAL.fetchActiveServices(activeServiceFetchData);
    long totalCountOfItems = activeServiceBaseResponse.getTotalCountOfItems();
    long fetchActiveServiceQueryEndTime = System.currentTimeMillis() - fetchActiveServiceQueryStartTime;
    log.info(
        "Active services fetched successfully for accountIdentifier: {}, number of fetched items: {}, time taken in ms: {}, total number of active services: {}",
        accountIdentifier, activeServiceBaseResponse.getActiveServiceItems().size(), fetchActiveServiceQueryEndTime,
        totalCountOfItems);

    log.info(
        "Start fetching active services names, org and project names for accountIdentifier: {}", accountIdentifier);
    long updateActiveServiceQueryStartTime = System.currentTimeMillis();
    List<ActiveService> activeServices = licenseUsageDAL.fetchActiveServicesNameOrgAndProjectName(
        accountIdentifier, activeServiceBaseResponse.getActiveServiceItems(), activeServiceFetchData.getSort());
    long updateActiveServiceQueryEndTime = System.currentTimeMillis() - updateActiveServiceQueryStartTime;
    log.info(
        "Active services names, org and project names fetched successfully for accountIdentifier: {}, time taken in ms: {}",
        accountIdentifier, updateActiveServiceQueryEndTime);

    List<ActiveServiceDTO> activeServiceDTOs =
        ActiveServiceMapper.toActiveServiceDTO(accountIdentifier, activeServices, currentTsInMs);
    return new PageImpl<>(activeServiceDTOs, pageRequest, totalCountOfItems);
  }

  private Path createAccountCSVReportDirIfNotExist(final String accountIdentifier) {
    Path activeServicesAccountCSVDir =
        Path.of(System.getProperty("java.io.tmpdir"), ACTIVE_SERVICE_CSV_REPORTS_TMP_DIR, accountIdentifier);
    try {
      FileIo.createDirectoryIfDoesNotExist(activeServicesAccountCSVDir);
      return activeServicesAccountCSVDir;
    } catch (IOException e) {
      throw new InvalidRequestException(
          format(
              "Unable to create active services account CSV report directory, path: %s", activeServicesAccountCSVDir),
          e);
    }
  }

  private Path getAccountCSVReportFilePath(Path accountDir, final String accountIdentifier, long reportTSInMs) {
    String fileName = format("%s-%s-%s.csv", accountIdentifier, reportTSInMs, UUIDGenerator.generateUuid());
    return Paths.get(accountDir.toString(), fileName);
  }

  private void printActiveServicesToCSV(
      Path csvReportFilePath, List<ActiveServiceDTO> activeServiceDTOs, CSVFormat format) {
    try (
        CSVPrinter printer = new CSVPrinter(
            Files.newBufferedWriter(csvReportFilePath, StandardOpenOption.APPEND, StandardOpenOption.CREATE), format)) {
      activeServiceDTOs.forEach(activeService -> {
        try {
          printer.printRecord(activeService.getName(), activeService.getOrgName(), activeService.getProjectName(),
              activeService.getIdentifier(), activeService.getInstanceCount(), activeService.getLastDeployed(),
              activeService.getLicensesConsumed());
        } catch (IOException e) {
          throw new InvalidRequestException(format("Unable to print CSV records to file: %s", csvReportFilePath), e);
        }
      });
    } catch (IOException e) {
      throw new InvalidRequestException(format("Unable to create CSV printer for file: %s", csvReportFilePath), e);
    }
  }

  private Scope getScope(@NotNull String accountIdentifier, @Nullable ActiveServicesFilterParams filterParams) {
    if (filterParams == null) {
      return Scope.of(accountIdentifier, null, null);
    }

    return Scope.of(accountIdentifier, filterParams.getOrgIdentifier(), filterParams.getProjectIdentifier());
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
