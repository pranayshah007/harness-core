/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.services;

import io.harness.ModuleType;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.EditionActionDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SMPDecLicenseDTO;
import io.harness.licensing.beans.modules.SMPEncLicenseDTO;
import io.harness.licensing.beans.modules.StartTrialDTO;
import io.harness.licensing.beans.response.CheckExpiryResultDTO;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LicenseService extends LicenseCrudService {
  ModuleLicenseDTO startFreeLicense(String accountIdentifier, ModuleType moduleType, String referer);

  ModuleLicenseDTO startCommunityLicense(String accountIdentifier, ModuleType moduleType);

  ModuleLicenseDTO startTrialLicense(String accountIdentifier, StartTrialDTO startTrialRequestDTO, String referer);

  ModuleLicenseDTO extendTrialLicense(String accountIdentifier, StartTrialDTO startTrialRequestDTO);

  CheckExpiryResultDTO checkExpiry(String accountIdentifier);

  void softDelete(String accountIdentifier);

  LicensesWithSummaryDTO getLicenseSummary(String accountIdentifier, ModuleType moduleType);

  Edition calculateAccountEdition(String accountIdentifier);

  Map<Edition, Set<EditionActionDTO>> getEditionActions(String accountIdentifier, ModuleType moduleType);

  Map<ModuleType, Long> getLastUpdatedAtMap(String accountIdentifier);

  List<ModuleLicenseDTO> getAllModuleLicences(String accountIdentifier);

  SMPDecLicenseDTO validateSMPLicense(SMPEncLicenseDTO licenseDTO);

  SMPEncLicenseDTO generateSMPLicense(String accountId);

  void applySMPLicense(SMPEncLicenseDTO licenseDTO);

}
