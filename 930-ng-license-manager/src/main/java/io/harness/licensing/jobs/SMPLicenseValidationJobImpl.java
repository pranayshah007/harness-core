/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.jobs;

import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.licensing.mappers.LicenseObjectConverter;
import io.harness.repositories.ModuleLicenseRepository;
import io.harness.smp.license.models.SMPLicense;
import io.harness.smp.license.v1.LicenseValidator;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SMPLicenseValidationJobImpl implements SMPLicenseValidationJob {
  private final LicenseValidator licenseValidator;
  private final LicenseObjectConverter licenseObjectConverter;
  private final ScheduledExecutorService executorService;
  private final ModuleLicenseRepository moduleLicenseRepository;
  private long lastValidTimeMs;

  @Inject
  public SMPLicenseValidationJobImpl(LicenseValidator licenseValidator, LicenseObjectConverter licenseObjectConverter,
      @Named("SMP_EXECUTOR_SERVICE") ScheduledExecutorService executorService,
      ModuleLicenseRepository moduleLicenseRepository) {
    this.licenseValidator = licenseValidator;
    this.licenseObjectConverter = licenseObjectConverter;
    this.executorService = executorService;
    this.moduleLicenseRepository = moduleLicenseRepository;
    this.lastValidTimeMs = 0;
  }

  @Override
  public void scheduleValidation(String accountIdentifier, String licenseSign, int frequencyInMinutes) {
    executorService.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        List<ModuleLicense> moduleLicenses = moduleLicenseRepository.findByAccountIdentifier(accountIdentifier);
        SMPLicense smpLicense = createSMPLicense(moduleLicenses);
        boolean licenseMatch = licenseValidator.verifySign(smpLicense, licenseSign);
        if (licenseMatch) {
          log.info("SMP License is valid");
          lastValidTimeMs = System.currentTimeMillis();
        } else {
          if (System.currentTimeMillis() - lastValidTimeMs > 259500000) {
            log.error("License validation is failing for past 3 days. Either do helm upgrade"
                + " with correct license file or contact harness for support");
          }
        }
      }

      private SMPLicense createSMPLicense(List<ModuleLicense> moduleLicenses) {
        if (Objects.isNull(moduleLicenses)) {
          return SMPLicense.builder().moduleLicenses(new ArrayList<>()).build();
        }
        return SMPLicense.builder()
            .moduleLicenses(moduleLicenses.stream()
                                .map(licenseObjectConverter::toDTO)
                                .map(m -> (ModuleLicenseDTO) m)
                                .collect(Collectors.toList()))
            .build();
      }
    }, frequencyInMinutes, frequencyInMinutes, TimeUnit.MINUTES);
  }
}
