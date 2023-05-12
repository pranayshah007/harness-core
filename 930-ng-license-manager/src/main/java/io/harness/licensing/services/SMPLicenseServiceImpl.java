/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.services;

import static io.harness.licensing.LicenseModule.LICENSE_CACHE_NAMESPACE;

import io.harness.account.services.AccountService;
import io.harness.ccm.license.remote.CeLicenseClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.SMPEncLicenseDTO;
import io.harness.licensing.beans.modules.SMPLicenseRequestDTO;
import io.harness.licensing.beans.modules.SMPValidationResultDTO;
import io.harness.licensing.checks.LicenseComplianceResolver;
import io.harness.licensing.interfaces.ModuleLicenseInterface;
import io.harness.licensing.jobs.SMPLicenseValidationJob;
import io.harness.licensing.mappers.LicenseObjectConverter;
import io.harness.licensing.mappers.SMPLicenseMapper;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.repositories.ModuleLicenseRepository;
import io.harness.smp.license.models.AccountInfo;
import io.harness.smp.license.models.SMPLicense;
import io.harness.smp.license.models.SMPLicenseEnc;
import io.harness.smp.license.models.SMPLicenseValidationResult;
import io.harness.smp.license.v1.LicenseGenerator;
import io.harness.smp.license.v1.LicenseValidator;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Objects;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class SMPLicenseServiceImpl extends DefaultLicenseServiceImpl {
  private final SMPLicenseValidationJob licenseValidationJob;

  @Inject
  public SMPLicenseServiceImpl(ModuleLicenseRepository moduleLicenseRepository,
      LicenseObjectConverter licenseObjectConverter, ModuleLicenseInterface licenseInterface,
      AccountService accountService, TelemetryReporter telemetryReporter, CeLicenseClient ceLicenseClient,
      LicenseComplianceResolver licenseComplianceResolver, @Named(LICENSE_CACHE_NAMESPACE) Cache<String, List> cache,
      LicenseGenerator licenseGenerator, LicenseValidator licenseValidator, SMPLicenseMapper smpLicenseMapper,
      SMPLicenseValidationJob licenseValidationJob) {
    super(moduleLicenseRepository, licenseObjectConverter, licenseInterface, accountService, telemetryReporter,
        ceLicenseClient, licenseComplianceResolver, cache, licenseGenerator, licenseValidator, smpLicenseMapper);
    this.licenseValidationJob = licenseValidationJob;
  }

  @Override
  public SMPValidationResultDTO validateSMPLicense(SMPEncLicenseDTO licenseDTO) {
    throw new UnsupportedOperationException("API not available on Self Managed Platform");
  }

  @Override
  public SMPEncLicenseDTO generateSMPLicense(String accountId, SMPLicenseRequestDTO licenseRequest) {
    throw new UnsupportedOperationException("API not available on Self Managed Platform");
  }

  @Override
  public void applySMPLicense(SMPEncLicenseDTO licenseDTO) {
    log.info("SMP License value: {}", licenseDTO.getEncryptedLicense());
    SMPLicenseEnc smpLicenseEnc = smpLicenseMapper.toSMPLicenseEnc(licenseDTO);
    SMPLicenseValidationResult validationResult = licenseValidator.validate(smpLicenseEnc, licenseDTO.isDecrypt());
    if (validationResult.isValid() && !Objects.isNull(validationResult.getSmpLicense())) {
      SMPLicense smpLicense = validationResult.getSmpLicense();
      AccountDTO accountDTO = createAccountIfNotPresent(smpLicense);
      createOrUpdateModuleLicenses(smpLicense.getModuleLicenses(), accountDTO);
      // start validation job with 1 day interval
      String licenseSign = licenseValidator.extractSign(smpLicenseEnc);
      licenseValidationJob.scheduleValidation(accountDTO.getIdentifier(), licenseSign, 60, this::createSmpLicense);
    } else {
      log.error("SMP License Validation Failed");
      throw new InvalidRequestException("Invalid license provided for intallation. Please provide the correct license");
    }
  }

  private AccountDTO createAccountIfNotPresent(SMPLicense license) {
    AccountDTO accountDTO = accountService.getOnPremAccount();
    // account creation happens only for new installations
    if (accountDTO == null) {
      if (Objects.isNull(license.getLicenseMeta()) || Objects.isNull(license.getLicenseMeta().getAccountDTO())
          || StringUtils.isEmpty(license.getLicenseMeta().getAccountDTO().getName())) {
        log.error("No account name present in license and no on-prem account found");
        throw new InvalidRequestException("No account name present in license and no on-prem account found");
      } else {
        accountDTO = mapToAccountDTO(license.getLicenseMeta().getAccountDTO());
        accountDTO = accountService.createAccount(accountDTO);
      }
    }
    return accountDTO;
  }

  private void createOrUpdateModuleLicenses(List<ModuleLicenseDTO> moduleLicenseDTOS, AccountDTO accountDTO) {
    AccountLicenseDTO accountLicenseDTO = getAccountLicense(accountDTO.getIdentifier());
    for (ModuleLicenseDTO moduleLicenseDTO : moduleLicenseDTOS) {
      moduleLicenseDTO.setAccountIdentifier(accountDTO.getIdentifier());
      List<ModuleLicenseDTO> moduleLicenseDTOsFromDb =
          accountLicenseDTO.getAllModuleLicenses().get(moduleLicenseDTO.getModuleType());
      if (EmptyPredicate.isNotEmpty(moduleLicenseDTOsFromDb)) {
        ModuleLicenseDTO existingLicense = moduleLicenseDTOsFromDb.get(0);
        moduleLicenseDTO.setId(existingLicense.getId());
        updateModuleLicense(moduleLicenseDTO);
      } else {
        // if not set null explicitly, record with empty "" id is created post 784xx
        moduleLicenseDTO.setId(null);
        createModuleLicense(moduleLicenseDTO);
      }
    }
  }

  private AccountDTO mapToAccountDTO(AccountInfo accountInfo) {
    return AccountDTO.builder()
        .companyName(accountInfo.getCompanyName())
        .name(accountInfo.getName())
        .defaultExperience(DefaultExperience.NG)
        .authenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
        .isNextGenEnabled(true)
        .isProductLed(true)
        .build();
  }
}
