/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.credit.schedular;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR;

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
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Calendar;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@OwnedBy(HarnessTeam.GTM)
public class CreditProvisioningIteratorHandler implements Handler<CIModuleLicense> {
  protected static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofMinutes(1);
  protected static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(30);
  protected static final Duration TARGET_INTERVAL = ofMinutes(10);
  protected static final Duration INTERVAL = ofMinutes(10);

  private static final int FREE_CREDITS_QUANTITY = 2000;

  @Inject private final PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private final MongoTemplate mongoTemplate;
  @Inject protected CreditService creditService;

  @Inject
  public CreditProvisioningIteratorHandler(
      PersistenceIteratorFactory persistenceIteratorFactory, MongoTemplate mongoTemplate, CreditService creditService) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
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
        MongoPersistenceIterator.<CIModuleLicense, SpringFilterExpander>builder()
            .clazz(CIModuleLicense.class)
            .fieldName(CIModuleLicenseKeys.nextIterations)
            .targetInterval(TARGET_INTERVAL)
            .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
            .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
            .handler(this)
            .filterExpander(getFilterQuery())
            .schedulingType(IRREGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  protected SpringFilterExpander getFilterQuery() {
    return query
        -> query.addCriteria(new Criteria()
                                 .and(ModuleLicenseKeys.status)
                                 .is(LicenseStatus.ACTIVE)
                                 .and(ModuleLicenseKeys.moduleType)
                                 .is(ModuleType.CI));
  }

  private static Calendar getStartOfNextMonth() {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
    calendar.set(Calendar.DAY_OF_MONTH, 1);
    calendar.set(Calendar.AM_PM, Calendar.AM);
    calendar.set(Calendar.HOUR, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.add(Calendar.MONTH, 1);
    return calendar;
  }

  private CreditDTO buildCreditDTO(CIModuleLicense entity) {
    return CICreditDTO.builder()
        .accountIdentifier(entity.getAccountIdentifier())
        .creditStatus(CreditStatus.ACTIVE)
        .quantity(FREE_CREDITS_QUANTITY)
        .purchaseTime(System.currentTimeMillis())
        .expiryTime(getStartOfNextMonth().getTimeInMillis())
        .creditType(CreditType.FREE)
        .moduleType(ModuleType.CI)
        .build();
  }
}
