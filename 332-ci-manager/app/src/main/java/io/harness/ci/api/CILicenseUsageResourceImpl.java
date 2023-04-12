/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.resources;

import static io.harness.filesystem.FileIo.deleteFileIfExists;
import static io.harness.licensing.usage.utils.PageableUtils.validateSort;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.licensing.api.ActiveDevelopersDTO;
import io.harness.beans.licensing.api.CIDevelopersFilterParams;
import io.harness.beans.licensing.api.CILicenseHistoryDTO;
import io.harness.beans.licensing.api.CILicenseType;
import io.harness.beans.licensing.api.CILicenseUsageResource;
import io.harness.licensing.CILicenseUsageImpl;
import io.harness.licensing.usage.params.DefaultPageableUsageRequestParams;
import io.harness.licensing.usage.utils.PageableUtils;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(HarnessTeam.CI)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class CILicenseUsageResourceImpl implements CILicenseUsageResource {
  private static final String DEVELOPER_ID = "identifier";
  private static final String LAST_BUILD = "lastBuild";
  private static final List<String> ACTIVE_DEVELOPERS_SORT_QUERY_PROPERTIES = List.of(DEVELOPER_ID, LAST_BUILD);
  private final CILicenseUsageImpl ciLicenseUsageService;

  public ResponseDTO<Page<ActiveDevelopersDTO>> ciLicenseUsage(String accountIdentifier, int page, int size,
      List<String> sort, long currentTsInMs, CIDevelopersFilterParams filterParams) {
    log.info("Getting CI licensing information");
    currentTsInMs = fixOptionalCurrentTs(currentTsInMs);
    Pageable pageRequest = PageableUtils.getPageRequest(page, size, sort, Sort.by(Sort.Direction.DESC, DEVELOPER_ID));
    validateSort(pageRequest.getSort(), ACTIVE_DEVELOPERS_SORT_QUERY_PROPERTIES);
    DefaultPageableUsageRequestParams requestParams =
        DefaultPageableUsageRequestParams.builder().filterParams(filterParams).pageRequest(pageRequest).build();

    return ResponseDTO.newResponse(
        ciLicenseUsageService.listLicenseUsage(accountIdentifier, ModuleType.CI, currentTsInMs, requestParams));
  }

  public Response downloadActiveDevelopersCSVReport(String accountIdentifier, long currentTsInMs) {
    currentTsInMs = fixOptionalCurrentTs(currentTsInMs);
    File file = ciLicenseUsageService.getLicenseUsageCSVReport(accountIdentifier, ModuleType.CI, currentTsInMs);

    return Response
        .ok(
            (StreamingOutput) output
            -> {
              Files.copy(file.toPath(), output);
              deleteFileIfExists(file.getPath());
            },
            APPLICATION_OCTET_STREAM)
        .header(
            "Content-Disposition", "attachment; filename=" + prepareCSVReportFileName(accountIdentifier, currentTsInMs))
        .build();
  }

  public ResponseDTO<Set<String>> listActiveDevelopers(String accountIdentifier, long currentTsInMs) {
    return ResponseDTO.newResponse(ciLicenseUsageService.listActiveDevelopers(accountIdentifier, currentTsInMs));
  }

  public ResponseDTO<CILicenseHistoryDTO> getLicenseHistoryUsage(
      String accountIdentifier, CILicenseType licenseType, CIDevelopersFilterParams filterParams) {
    return ResponseDTO.newResponse(
        ciLicenseUsageService.getLicenseHistoryUsage(accountIdentifier, licenseType, filterParams));
  }

  private static long fixOptionalCurrentTs(long currentTsMs) {
    return currentTsMs == 0 ? System.currentTimeMillis() : currentTsMs;
  }

  private static String prepareCSVReportFileName(String accountIdentifier, long currentTsInMs) {
    return format("%s-%s.csv", accountIdentifier, currentTsInMs);
  }
}
