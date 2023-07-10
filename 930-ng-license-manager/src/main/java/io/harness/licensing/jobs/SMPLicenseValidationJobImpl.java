/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.jobs;

import io.harness.smp.license.models.SMPLicense;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SMPLicenseValidationJobImpl implements SMPLicenseValidationJob {
  private final ScheduledExecutorService executorService;
  private final SMPLicenseValidationTaskFactory taskFactoryProvider;

  @Inject
  public SMPLicenseValidationJobImpl(@Named("SMP_EXECUTOR_SERVICE") ScheduledExecutorService executorService,
      SMPLicenseValidationTaskFactory taskFactoryProvider) {
    this.executorService = executorService;
    this.taskFactoryProvider = taskFactoryProvider;
  }

  @Override
  public void scheduleValidation(String accountIdentifier, SMPLicense smpLicense, int frequencyInMinutes,
      Function<String, SMPLicense> licenseProvider) {
    Runnable task = taskFactoryProvider.create(accountIdentifier, smpLicense, licenseProvider);
    executorService.scheduleAtFixedRate(task, frequencyInMinutes, frequencyInMinutes, TimeUnit.MINUTES);
  }
}
