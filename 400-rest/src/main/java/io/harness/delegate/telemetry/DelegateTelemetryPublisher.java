/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.telemetry;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.delegate.utils.DelegateTelemetryConstants.COUNT_OF_CONNECTED_DELEGATES;
import static io.harness.delegate.utils.DelegateTelemetryConstants.COUNT_OF_REGISTERED_DELEGATES;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.telemetry.Destination.AMPLITUDE;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;

import io.harness.TelemetryConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;
import io.harness.workers.background.AccountLevelEntityProcessController;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(DEL)
public class DelegateTelemetryPublisher implements Handler<Account> {
  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private static final String ACCOUNT = "Account";

  private final PersistenceIteratorFactory persistenceIteratorFactory;
  private final MorphiaPersistenceProvider<Account> persistenceProvider;
  private final DelegateService delegateService;
  private final TelemetryReporter telemetryReporter;
  private final AccountService accountService;

  @Inject
  public DelegateTelemetryPublisher(PersistenceIteratorFactory persistenceIteratorFactory,
      MorphiaPersistenceProvider<Account> persistenceProvider, DelegateService delegateService,
      TelemetryReporter telemetryReporter, AccountService accountService) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.persistenceProvider = persistenceProvider;
    this.delegateService = delegateService;
    this.telemetryReporter = telemetryReporter;
    this.accountService = accountService;
  }

  public void registerIterators() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("DelegateTelemetryPublisherIteration")
            .poolSize(1)
            .interval(ofMinutes(10))
            .build(),
        DelegateTelemetryPublisher.class,
        MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
            .clazz(Account.class)
            .fieldName(AccountKeys.delegateTelemetryPublisherIteration)
            .targetInterval(ofHours(24))
            .acceptableNoAlertDelay(ofMinutes(4))
            .acceptableExecutionTime(ofMinutes(2))
            .handler(this)
            .entityProcessController(new AccountLevelEntityProcessController(accountService))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(Account account) {
    try (AutoLogContext ignore0 = new AccountLogContext(account.getUuid(), OVERRIDE_ERROR)) {
      String accountId = account.getUuid();
      log.info("DelegateTelemetryPublisher recordTelemetry execute started for account {}.", accountId);
      try {
        if (EmptyPredicate.isNotEmpty(accountId) && !accountId.equals(GLOBAL_ACCOUNT_ID)) {
          sendTelemetryGroupEvents(accountId);
          log.info("Scheduled DelegateTelemetryPublisher event sent for account {} !", accountId);
        } else {
          log.info("There is no Account found!. Can not send scheduled DelegateTelemetryPublisher event.");
        }
      } catch (Exception e) {
        log.error("DelegateTelemetryPublisher recordTelemetry execute failed for account {} .", accountId, e);
      } finally {
        log.info("DelegateTelemetryPublisher recordTelemetry execute finished for account {} .", accountId);
      }
    }
  }

  private void sendTelemetryGroupEvents(String accountId) {
    HashMap<String, Object> map = new HashMap<>();

    map.put("group_type", ACCOUNT);
    map.put("group_id", accountId);
    map.put(COUNT_OF_CONNECTED_DELEGATES, delegateService.getCountOfConnectedDelegates(accountId));
    map.put(COUNT_OF_REGISTERED_DELEGATES, delegateService.getCountOfRegisteredDelegates(accountId));
    telemetryReporter.sendGroupEvent(accountId, null, map, Collections.singletonMap(AMPLITUDE, true),
        TelemetryOption.builder().sendForCommunity(true).build());
  }

  public void sendTelemetryTrackEvents(String accountId, String delegateType, boolean isNg, String eventName) {
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("NG", isNg);
    properties.put("Type", delegateType);
    Optional<UserPrincipal> userPrincipal = getUserPrincipalFromSourcePrincipal();
    telemetryReporter.sendTrackEvent(eventName,
        userPrincipal.isPresent() ? userPrincipal.get().getEmail()
                                  : TelemetryConstants.SEGMENT_DUMMY_ACCOUNT_PREFIX + accountId,
        accountId, properties, null, Category.GLOBAL, TelemetryOption.builder().sendForCommunity(false).build());
  }

  private Optional<UserPrincipal> getUserPrincipalFromSourcePrincipal() {
    Principal principal = SourcePrincipalContextBuilder.getSourcePrincipal();
    UserPrincipal userPrincipal = (UserPrincipal) principal;
    return Optional.ofNullable(userPrincipal);
  }
}
