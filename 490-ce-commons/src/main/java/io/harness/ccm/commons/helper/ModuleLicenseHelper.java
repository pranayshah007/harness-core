/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Maps;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;

@Singleton
@Slf4j
@OwnedBy(CE)
public class ModuleLicenseHelper {
  @Inject private NgLicenseHttpClient ngLicenseHttpClient;

  private AccountLicenseDTO getAccountLicensesDTO(@NonNull final String accountId) {
    AccountLicenseDTO accountLicenseDTO = null;
    try {
      final Call<ResponseDTO<AccountLicenseDTO>> accountLicensesCall =
          ngLicenseHttpClient.getAccountLicensesDTO(accountId);
      accountLicenseDTO = NGRestUtils.getResponse(accountLicensesCall);
    } catch (final Exception exception) {
      log.error("Failed to fetch License data for account: {}", accountId);
    }
    return accountLicenseDTO;
  }

  private LicensesWithSummaryDTO getLicenseSummary(@NonNull final String accountId) {
    LicensesWithSummaryDTO license = null;
    try {
      final Call<ResponseDTO<LicensesWithSummaryDTO>> licenseCall =
          ngLicenseHttpClient.getLicenseSummary(accountId, ModuleType.CE.toString());
      license = NGRestUtils.getResponse(licenseCall);
    } catch (final Exception exception) {
      log.error("Failed to fetch License data for account: {}", accountId);
    }
    return license;
  }

  public boolean isFreeEditionModuleLicense(@NonNull final String accountId) {
    boolean isFreeEditionModuleLicense = false;
    final AccountLicenseDTO accountLicenseDTO = getAccountLicensesDTO(accountId);
    if (Objects.nonNull(accountLicenseDTO) && !Maps.isNullOrEmpty(accountLicenseDTO.getAllModuleLicenses())) {
      for (final Map.Entry<ModuleType, List<ModuleLicenseDTO>> entry :
          accountLicenseDTO.getAllModuleLicenses().entrySet()) {
        if (ModuleType.CE == entry.getKey() && Objects.nonNull(entry.getValue())) {
          final List<ModuleLicenseDTO> moduleLicenseDTOs = entry.getValue();
          final Optional<ModuleLicenseDTO> firstModuleLicenseDTO =
              moduleLicenseDTOs.stream()
                  .filter(moduleLicenseDTO -> moduleLicenseDTO.getStatus() == LicenseStatus.ACTIVE)
                  .findFirst();
          if (firstModuleLicenseDTO.isPresent()) {
            isFreeEditionModuleLicense = firstModuleLicenseDTO.get().getEdition() == Edition.FREE;
          } else if (!moduleLicenseDTOs.isEmpty()) {
            isFreeEditionModuleLicense =
                moduleLicenseDTOs.get(moduleLicenseDTOs.size() - 1).getEdition() == Edition.FREE;
          }
          break;
        }
      }
    }
    return isFreeEditionModuleLicense;
  }

  public boolean isEnterpriseEditionModuleLicense(@NonNull final String accountId) {
    final LicensesWithSummaryDTO license = getLicenseSummary(accountId);
    return Objects.nonNull(license) && license.getEdition().equals(Edition.ENTERPRISE);
  }
}
