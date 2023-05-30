/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.credit.schedular;

import static io.harness.TelemetryConstants.SEGMENT_DUMMY_ACCOUNT_PREFIX;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.telemetry.Destination.ALL;

import static java.time.Duration.ofHours;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.entities.CICredit;
import io.harness.credit.entities.Credit.CreditsKeys;
import io.harness.credit.services.CreditService;
import io.harness.credit.utils.CreditStatus;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.GTM)
@Slf4j
public class SendProvisionedCICreditsToSegmentHandler extends CreditExpiryIteratorHandler<CICredit> {
  private static final Duration INTERVAL = ofHours(24);
  private static final String GROUP_TYPE = "group_type";
  private static final String GROUP_ID = "group_id";
  private static final String ACCOUNT = "Account";
  private static final String CI_BUILD_CREDITS_PROVISIONED = "ci_build_credits_provisioned";
  private final TelemetryReporter telemetryReporter;

  @Inject
  public SendProvisionedCICreditsToSegmentHandler(PersistenceIteratorFactory persistenceIteratorFactory,
      MorphiaPersistenceProvider<CICredit> persistenceProvider, CreditService creditService,
      TelemetryReporter telemetryReporter) {
    super(persistenceIteratorFactory, persistenceProvider, creditService);
    this.telemetryReporter = telemetryReporter;
  }

  @Override
  public void handle(CICredit ciCredit) {
    String userId = SEGMENT_DUMMY_ACCOUNT_PREFIX + ciCredit.getAccountIdentifier();
    HashMap<String, Object> map = new HashMap<>();
    map.put(GROUP_TYPE, ACCOUNT);
    map.put(GROUP_ID, ciCredit.getAccountIdentifier());
    map.put(CI_BUILD_CREDITS_PROVISIONED, ciCredit.getQuantity());

    try {
      telemetryReporter.sendGroupEvent(ciCredit.getAccountIdentifier(), userId, map,
          Collections.singletonMap(ALL, true), TelemetryOption.builder().sendForCommunity(true).build());
      log.info("Successfully sent ci_build_credits_provisioned telemetry event for account {}",
          ciCredit.getAccountIdentifier());
    } catch (Exception ex) {
      log.error("Failed to send ci_build_credits_provisioned telemetry event for account {}",
          ciCredit.getAccountIdentifier(), ex);
    }
  }

  public void registerIterator(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(this.getClass().getName())
            .poolSize(threadPoolSize)
            .interval(INTERVAL)
            .build(),
        CICredit.class,
        MongoPersistenceIterator.<CICredit, MorphiaFilterExpander<CICredit>>builder()
            .clazz(CICredit.class)
            .fieldName(CreditsKeys.creditsSendToSegmentIteration)
            .targetInterval(TARGET_INTERVAL)
            .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
            .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
            .handler(this)
            .filterExpander(getFilterQuery())
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  protected MorphiaFilterExpander<CICredit> getFilterQuery() {
    return query -> query.field(CreditsKeys.creditStatus).equal(CreditStatus.ACTIVE);
  }
}
