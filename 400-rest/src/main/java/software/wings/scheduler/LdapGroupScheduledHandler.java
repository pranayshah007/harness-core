/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceRequiredProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.SSOSettings.SSOSettingsKeys;
import software.wings.beans.sso.SSOType;
import software.wings.security.authentication.NgLdapGroupSyncEventPublisher;
import software.wings.service.intfc.AccountService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class LdapGroupScheduledHandler extends IteratorLoopModeHandler implements Handler<LdapSettings> {
  private static final int POOL_SIZE = 8;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceRequiredProvider<LdapSettings> persistenceProvider;
  @Inject private AccountService accountService;
  @Inject private LdapGroupSyncJobHelper ldapGroupSyncJobHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private NgLdapGroupSyncEventPublisher ngLdapGroupSyncEventPublisher;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration throttleInterval) {
    executor =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("scheduled-ldap-handler").build());
    ExecutorService executorService = new ScheduledThreadPoolExecutor(executorOptions.getPoolSize(),
        new ThreadFactoryBuilder().setNameFormat("Iterator-LdapGroupScheduledThread").build());
    iterator = (MongoPersistenceIterator<LdapSettings, MorphiaFilterExpander<LdapSettings>>)
                   persistenceIteratorFactory.createIterator(LdapGroupScheduledHandler.class,
                       MongoPersistenceIterator.<LdapSettings, MorphiaFilterExpander<LdapSettings>>builder()
                           .mode(PersistenceIterator.ProcessMode.LOOP)
                           .iteratorName(iteratorName)
                           .clazz(LdapSettings.class)
                           .fieldName(SSOSettingsKeys.nextIterations)
                           .acceptableNoAlertDelay(ofSeconds(60))
                           .maximumDelayForCheck(ofHours(6))
                           .executorService(executorService)
                           .semaphore(new Semaphore(10))
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .persistenceProvider(persistenceProvider)
                           .schedulingType(IRREGULAR_SKIP_MISSED)
                           .filterExpander(query
                               -> query.field(SSOSettingsKeys.type)
                                      .equal(SSOType.LDAP)
                                      .field(SSOSettingsKeys.nextIterations)
                                      .exists()
                                      .field(SSOSettingsKeys.nextIterations)
                                      .notEqual(null)
                                      .field(SSOSettingsKeys.nextIterations)
                                      .notEqual(Collections.emptyList()))
                           .throttleInterval(throttleInterval));

    executor.submit(() -> iterator.process());
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "LdapGroupScheduled";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  public void wakeup() {
    if (iterator != null) {
      iterator.wakeup();
    }
  }

  @Override
  public void handle(LdapSettings settings) {
    ldapGroupSyncJobHelper.syncJob(settings);
    if (!settings.isDisabled()) {
      processForNG(settings);
    }
  }

  private void processForNG(LdapSettings settings) {
    publishEventToNG(settings);
  }

  private void publishEventToNG(LdapSettings settings) {
    try {
      log.info(
          "EVENT_LDAP_GROUP_SYNC: Publishing event from CG CronScheduler for NG LDAP Group Sync for account {} and SSO {} ",
          settings.getAccountId(), settings.getUuid());
      ngLdapGroupSyncEventPublisher.publishLdapGroupSyncEvent(settings.getAccountId(), settings.getUuid());

    } catch (Exception e) {
      log.error("EVENT_LDAP_GROUP_SYNC: Exception in publishing event for LDAP Group Sync for account {} and SSO {} ",
          settings.getAccountId(), settings.getUuid(), e);
    }
  }
}
