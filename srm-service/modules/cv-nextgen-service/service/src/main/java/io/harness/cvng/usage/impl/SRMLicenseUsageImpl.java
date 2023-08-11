/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.usage.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.SERVICE_INSTANCES_QUERY_PROPERTY;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.usage.impl.resources.ActiveMonitoredServiceDTO;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.filesystem.FileIo;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.licensing.usage.params.DefaultPageableUsageRequestParams;
import io.harness.licensing.usage.params.PageableUsageRequestParams;
import io.harness.licensing.usage.params.UsageRequestParams;
import io.harness.licensing.usage.utils.PageableUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.CV)
@Singleton
@Slf4j
public class SRMLicenseUsageImpl implements LicenseUsageInterface<SRMLicenseUsageDTO, UsageRequestParams> {
  private static final String ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG = "Account Identifier cannot be null or empty";
  private static final String ACTIVE_SERVICE_MONITORED_CSV_REPORTS_TMP_DIR = "active-service-monitored-csv-reports";
  private static final int PAGE_SIZE_ACTIVE_SERVICE_MONITORED_DOWNLOAD_CSV = 500;

  private static final String[] ACTIVE_SERVICES_MONITORED_CSV_REPORT_HEADER =
      new String[] {"SERVICE", "ORGANIZATION ID", "PROJECT ID", "SERVICE ID", "MONITORED SERVICE COUNT"};
  @Inject private MonitoredServiceService monitoredServiceService;

  @Inject private NextGenService nextGenService;

  @Override
  public SRMLicenseUsageDTO getLicenseUsage(
      String accountIdentifier, ModuleType module, long timestamp, UsageRequestParams usageRequest) {
    Preconditions.checkArgument(timestamp > 0, format("Invalid timestamp %d while fetching LicenseUsages.", timestamp));
    Preconditions.checkArgument(ModuleType.CV == module || ModuleType.SRM == module,
        format("Invalid Module type %s provided", module.toString()));
    Preconditions.checkArgument(
        StringUtils.isNotBlank(accountIdentifier), "Account Identifier cannot be null or blank");

    long count = monitoredServiceService.countUniqueEnabledServices(accountIdentifier);

    return SRMLicenseUsageDTO.builder()
        .activeServices(UsageDataDTO.builder().count(count).displayName("Total active SRM services").build())
        .build();
  }

  @Override
  public Page<ActiveMonitoredServiceDTO> listLicenseUsage(
      String accountIdentifier, ModuleType module, long currentTsInMs, PageableUsageRequestParams usageRequest) {
    if (currentTsInMs <= 0) {
      throw new InvalidArgumentsException(
          format("Invalid timestamp %d while fetching SRM active services monitored", currentTsInMs));
    }
    if (ModuleType.SRM != module) {
      throw new InvalidArgumentsException(format("Invalid Module type %s provided, expected SRM", module.toString()));
    }
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException(ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG);
    }

    DefaultPageableUsageRequestParams defaultUsageRequestParams = (DefaultPageableUsageRequestParams) usageRequest;
    Pageable pageRequest = defaultUsageRequestParams.getPageRequest();
    ActiveServiceMonitoredFilterParams filterParams =
        (ActiveServiceMonitoredFilterParams) defaultUsageRequestParams.getFilterParams();

    ProjectParams projectParams = ProjectParams.builder().accountIdentifier(accountIdentifier).build();
    if (filterParams.getOrgIdentifier() != null && isNotEmpty(filterParams.getOrgIdentifier())) {
      projectParams.setOrgIdentifier(filterParams.getOrgIdentifier());
    }
    if (filterParams.getProjectIdentifier() != null && isNotEmpty(filterParams.getProjectIdentifier())) {
      projectParams.setProjectIdentifier(filterParams.getProjectIdentifier());
    }

    List<ActiveMonitoredServiceDTO> activeMonitoredServiceDTOList =
        monitoredServiceService.listActiveMonitoredServices(projectParams, filterParams.getServiceIdentifier());

    return new PageImpl<>(activeMonitoredServiceDTOList, pageRequest, activeMonitoredServiceDTOList.size());
  }

  @Override
  public File getLicenseUsageCSVReport(String accountIdentifier, ModuleType module, long currentTsInMs) {
    if (currentTsInMs <= 0) {
      throw new InvalidArgumentsException(
          format("Invalid timestamp %d while downloading SRM active services monitored", currentTsInMs));
    }
    if (ModuleType.SRM != module) {
      throw new InvalidArgumentsException(format("Invalid Module type %s provided, expected SRM", module.toString()));
    }
    if (isEmpty(accountIdentifier)) {
      throw new InvalidArgumentsException(ACCOUNT_IDENTIFIER_BLANK_ERROR_MSG);
    }

    Path accountCSVReportDir = createAccountCSVReportDirIfNotExist(accountIdentifier);
    Path csvReportFilePath = getAccountCSVReportFilePath(accountCSVReportDir, accountIdentifier, currentTsInMs);

    int page = 0;
    PageImpl<ActiveServiceMonitoredDTO> activeServiceMonitoredDTOS;

    do {
      Pageable pageRequest = PageableUtils.getPageRequest(page, PAGE_SIZE_ACTIVE_SERVICE_MONITORED_DOWNLOAD_CSV,
          emptyList(), Sort.by(Sort.Direction.DESC, SERVICE_INSTANCES_QUERY_PROPERTY));
      List<ActiveServiceMonitoredDTO> activeServiceMonitoredDTOList =
          monitoredServiceService.listActiveServiceMonitored(
              ProjectParams.builder().accountIdentifier(accountIdentifier).build());

      activeServiceMonitoredDTOS =
          new PageImpl<>(activeServiceMonitoredDTOList, pageRequest, activeServiceMonitoredDTOList.size());

      CSVFormat format = page == 0 ? CSVFormat.DEFAULT.withHeader(ACTIVE_SERVICES_MONITORED_CSV_REPORT_HEADER)
                                   : CSVFormat.DEFAULT.withSkipHeaderRecord();
      long printActiveServicesToCSVStartTime = System.currentTimeMillis();
      printActiveServicesToCSV(csvReportFilePath, activeServiceMonitoredDTOS.getContent(), format);
      long printActiveServicesToCSVEndTime = System.currentTimeMillis() - printActiveServicesToCSVStartTime;
      log.info("Active services printed successfully to file: {}, page: {}, time taken in ms: {}", csvReportFilePath,
          page, printActiveServicesToCSVEndTime);
    } while (++page < activeServiceMonitoredDTOS.getTotalPages());

    return csvReportFilePath.toFile();
  }

  private Path getAccountCSVReportFilePath(Path accountDir, final String accountIdentifier, long currentTsInMs) {
    String fileName = format("%s-%s-%s.csv", accountIdentifier, currentTsInMs, UUIDGenerator.generateUuid());
    return Paths.get(accountDir.toString(), fileName);
  }

  private Path createAccountCSVReportDirIfNotExist(final String accountIdentifier) {
    Path activeServicesMonitoredAccountCSVDir =
        Path.of(System.getProperty("java.io.tmpdir"), ACTIVE_SERVICE_MONITORED_CSV_REPORTS_TMP_DIR, accountIdentifier);
    try {
      FileIo.createDirectoryIfDoesNotExist(activeServicesMonitoredAccountCSVDir);
      return activeServicesMonitoredAccountCSVDir;
    } catch (IOException e) {
      throw new InvalidRequestException(
          format("Unable to create active services monitored account CSV report directory, path: %s",
              activeServicesMonitoredAccountCSVDir),
          e);
    }
  }

  private void printActiveServicesToCSV(
      Path csvReportFilePath, List<ActiveServiceMonitoredDTO> activeServiceMonitoredDTOs, CSVFormat format) {
    try (
        CSVPrinter printer = new CSVPrinter(
            Files.newBufferedWriter(csvReportFilePath, StandardOpenOption.APPEND, StandardOpenOption.CREATE), format)) {
      activeServiceMonitoredDTOs.forEach(activeServiceMonitoredDTO -> {
        try {
          printer.printRecord(activeServiceMonitoredDTO.getName(), activeServiceMonitoredDTO.getOrgIdentifier(),
              activeServiceMonitoredDTO.getProjectIdentifier(), activeServiceMonitoredDTO.getIdentifier(),
              activeServiceMonitoredDTO.getMonitoredServiceCount());
        } catch (IOException e) {
          throw new InvalidRequestException(format("Unable to print CSV records to file: %s", csvReportFilePath), e);
        }
      });
    } catch (IOException e) {
      throw new InvalidRequestException(format("Unable to create CSV printer for file: %s", csvReportFilePath), e);
    }
  }
}
