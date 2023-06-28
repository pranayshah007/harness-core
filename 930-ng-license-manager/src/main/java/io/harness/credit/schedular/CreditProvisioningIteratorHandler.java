/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.credit.schedular;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR;
import static io.harness.yaml.schema.beans.SchemaNamespaceConstants.CI;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.CreditType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.beans.credits.CICreditDTO;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.entities.CICredit;
import io.harness.credit.entities.Credit;
import io.harness.credit.services.CreditService;
import io.harness.credit.utils.CreditStatus;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistentCronIterable;
import io.harness.iterator.PersistentIrregularIterable;
import io.harness.iterator.PersistentIterable;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.entities.modules.CIModuleLicense;
import io.harness.licensing.entities.modules.CIModuleLicense.CIModuleLicenseKeys;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense.ModuleLicenseKeys;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.mongo.iterator.provider.MorphiaPersistenceRequiredProvider;
import io.harness.mongo.iterator.provider.PersistenceProvider;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.NotImplementedException;

@Slf4j
@OwnedBy(HarnessTeam.GTM)
public class CreditProvisioningIteratorHandler implements Handler<CIModuleLicense> {
  protected static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofMinutes(1);
  protected static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(15);
  protected static final Duration TARGET_INTERVAL = ofSeconds(30);
  protected static final Duration INTERVAL = ofSeconds(30);

  private static final int QUANTITY = 2000;

  @Inject private final PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private final MorphiaPersistenceRequiredProvider<CIModuleLicense> persistenceProvider;
  @Inject protected CreditService creditService;

  @Inject
  public CreditProvisioningIteratorHandler(PersistenceIteratorFactory persistenceIteratorFactory,
      MorphiaPersistenceRequiredProvider<CIModuleLicense> persistenceProvider, CreditService creditService) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.persistenceProvider = persistenceProvider;
    this.creditService = creditService;
  }

  @Override
  public void handle(CIModuleLicense entity) {
    try {
      CreditDTO creditDTO = buildCreditDTO(entity);
      creditService.purchaseCredit(entity.getAccountIdentifier(), creditDTO);
    } catch (Exception ex) {
      log.error("Error while handling credit provisioning", ex);
    }
  }

  public void registerIterator(int threadPoolSize) {
    persistenceIteratorFactory.createLoopIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(this.getClass().getName())
            .poolSize(threadPoolSize)
            .interval(INTERVAL)
            .build(),
        CIModuleLicense.class,
        MongoPersistenceIterator.<CIModuleLicense, MorphiaFilterExpander<CIModuleLicense>>builder()
            .clazz(CIModuleLicense.class)
            .fieldName(CIModuleLicenseKeys.nextIterations)
            .targetInterval(TARGET_INTERVAL)
            .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
            .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
            .handler(this)
            .filterExpander(getFilterQuery())
            .schedulingType(IRREGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  protected MorphiaFilterExpander<CIModuleLicense> getFilterQuery() {
    return query -> query.field(ModuleLicenseKeys.status).equal(LicenseStatus.ACTIVE);
  }

  private static Calendar getStartOfNextMonth() {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
    calendar.set(Calendar.DAY_OF_MONTH, 1);
    calendar.set(Calendar.AM_PM, Calendar.AM);
    calendar.set(Calendar.HOUR, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.add(Calendar.MONTH, 1);
    return calendar;
  }

  private static Calendar getStartOfMonthFollowingNext() {
    Calendar calendar = getStartOfNextMonth();
    calendar.add(Calendar.MONTH, 1);
    return calendar;
  }

  private CreditDTO buildCreditDTO(ModuleLicense entity) {
    switch (entity.getModuleType().toString().toLowerCase()) {
      case CI:
        return CICreditDTO.builder()
            .accountIdentifier(entity.getAccountIdentifier())
            .creditStatus(CreditStatus.ACTIVE)
            .quantity(QUANTITY)
            .purchaseTime(getStartOfNextMonth().getTimeInMillis())
            .expiryTime(getStartOfMonthFollowingNext().getTimeInMillis())
            .creditType(CreditType.FREE)
            .moduleType(ModuleType.CI)
            .build();
      default:
        throw new NotImplementedException(
            String.format("Provisioning of credits for module %s has not been implemented.", entity.getModuleType()));
    }
  }
}
