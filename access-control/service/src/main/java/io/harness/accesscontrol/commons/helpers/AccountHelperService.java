package io.harness.accesscontrol.commons.helpers;

import io.harness.ModuleType;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.persistence.HPersistence;
import io.harness.utils.RestCallToNGManagerClientUtils;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;

@Slf4j
public class AccountHelperService {
  @Inject private HPersistence persistence;
  @Inject private NgLicenseHttpClient ngLicenseHttpClient;

  public List<String> getCeEnabledNgAccounts() {
    long expiryTime = Instant.now().minus(15, ChronoUnit.DAYS).toEpochMilli();
    try {
      Call<ResponseDTO<List<ModuleLicenseDTO>>> moduleLicensesByModuleType =
          ngLicenseHttpClient.getModuleLicensesByModuleType(ModuleType.CE, expiryTime);
      List<ModuleLicenseDTO> ceEnabledLicenses = RestCallToNGManagerClientUtils.execute(moduleLicensesByModuleType);
      return ceEnabledLicenses.stream().map(ModuleLicenseDTO::getAccountIdentifier).collect(Collectors.toList());
    } catch (Exception ex) {
      log.error("Exception in account shard ", ex);
    }
    return Collections.emptyList();
  }
}
