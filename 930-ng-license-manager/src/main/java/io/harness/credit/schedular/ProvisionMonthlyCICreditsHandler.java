/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.credit.schedular;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.CreditType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.beans.credits.CICreditDTO;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.services.CreditService;
import io.harness.credit.utils.CreditStatus;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.entities.modules.CIModuleLicense;
import io.harness.licensing.entities.modules.CIModuleLicense.CIModuleLicenseKeys;
import io.harness.licensing.entities.modules.ModuleLicense.ModuleLicenseKeys;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Calendar;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.GTM)
@Slf4j
public class ProvisionMonthlyCICreditsHandler implements Handler<CIModuleLicense> {
  private static final Duration INTERVAL = ofHours(3);
  private static final Duration TARGET_INTERVAL = ofHours(8);
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofMinutes(60);
  private static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(15);
  private static final int FREE_CREDITS_QUANTITY = 2000;
  private static final String GMT_TIMEZONE = "GMT";

  @Inject private final PersistenceIteratorFactory persistenceIteratorFactory;
  protected final MorphiaPersistenceProvider<CIModuleLicense> persistenceProvider;
  @Inject protected CreditService creditService;

  @Inject
  public ProvisionMonthlyCICreditsHandler(PersistenceIteratorFactory persistenceIteratorFactory,
      MorphiaPersistenceProvider<CIModuleLicense> persistenceProvider, CreditService creditService) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.persistenceProvider = persistenceProvider;
    this.creditService = creditService;
  }

  public void registerIterators(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(this.getClass().getName())
            .poolSize(threadPoolSize)
            .interval(INTERVAL)
            .build(),
        CIModuleLicense.class,
        MongoPersistenceIterator.<CIModuleLicense, MorphiaFilterExpander<CIModuleLicense>>builder()
            .clazz(CIModuleLicense.class)
            .fieldName(CIModuleLicenseKeys.provisionMonthlyCICreditsIteration)
            .targetInterval(TARGET_INTERVAL)
            .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
            .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
            .handler(this)
            .filterExpander(getFilterQuery())
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  private MorphiaFilterExpander<CIModuleLicense> getFilterQuery() {
    return query
        -> query.field(ModuleLicenseKeys.moduleType)
               .equal(ModuleType.CI)
               .field(ModuleLicenseKeys.status)
               .equal(LicenseStatus.ACTIVE);
  }

  @Override
  public void handle(CIModuleLicense ciModuleLicense) {
    if (isFirstDayOfTheMonth() && !isCreditAlreadyProvisionedForCurrentMonth(ciModuleLicense)) {
      try {
        CreditDTO creditDTO = buildCreditDTO(ciModuleLicense);
        creditService.purchaseCredit(ciModuleLicense.getAccountIdentifier(), creditDTO);
        log.info("Successfully provisioned monthly free CI build credits for account: "
            + ciModuleLicense.getAccountIdentifier());
      } catch (Exception ex) {
        log.error("Error occurred while provisioning monthly free CI build credits for account: "
                + ciModuleLicense.getAccountIdentifier(),
            ex);
      }
    }
  }

  private boolean isCreditAlreadyProvisionedForCurrentMonth(CIModuleLicense ciModuleLicense) {
    List<CreditDTO> creditDTOS =
        creditService.getCredits(ciModuleLicense.getAccountIdentifier(), CreditType.FREE, CreditStatus.ACTIVE);
    if (creditDTOS.isEmpty()) {
      return false;
    }

    return Calendar.getInstance().get(Calendar.MONTH) == getMonthOfPurchaseOfLatestCredit(creditDTOS);
  }

  private int getMonthOfPurchaseOfLatestCredit(List<CreditDTO> creditDTOS) {
    long latestPurchaseTime = creditDTOS.get(0).getPurchaseTime();
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(latestPurchaseTime);

    return c.get(Calendar.MONTH);
  }

  private boolean isFirstDayOfTheMonth() {
    return Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == 1;
  }

  private CreditDTO buildCreditDTO(CIModuleLicense ciModuleLicense) {
    return CICreditDTO.builder()
        .accountIdentifier(ciModuleLicense.getAccountIdentifier())
        .creditStatus(CreditStatus.ACTIVE)
        .quantity(FREE_CREDITS_QUANTITY)
        .purchaseTime(System.currentTimeMillis())
        .expiryTime(getStartOfNextMonth().getTimeInMillis())
        .creditType(CreditType.FREE)
        .moduleType(ModuleType.CI)
        .build();
  }

  private static Calendar getStartOfNextMonth() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.MONTH, 1);
    calendar.set(Calendar.DAY_OF_MONTH, 1);
    calendar.set(Calendar.AM_PM, Calendar.AM);
    calendar.set(Calendar.HOUR, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    return calendar;
  }
}
