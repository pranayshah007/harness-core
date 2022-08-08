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
import software.wings.service.intfc.AccountService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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
public class LdapGroupScheduledHandler implements Handler<LdapSettings> {
  private static final int POOL_SIZE = 8;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  PersistenceIterator<LdapSettings> iterator;
  @Inject private MorphiaPersistenceRequiredProvider<LdapSettings> persistenceProvider;
  @Inject private AccountService accountService;
  @Inject private LdapGroupSyncJobHelper ldapGroupSyncJobHelper;
  @Inject private FeatureFlagService featureFlagService;

  private static ExecutorService executor =
      Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("scheduled-ldap-handler").build());
  private static final ScheduledThreadPoolExecutor executorService = new ScheduledThreadPoolExecutor(
      POOL_SIZE, new ThreadFactoryBuilder().setNameFormat("Iterator-LdapGroupScheduledThread").build());

  public void registerIterators() {
    iterator = persistenceIteratorFactory.createIterator(LdapGroupScheduledHandler.class,
        MongoPersistenceIterator.<LdapSettings, MorphiaFilterExpander<LdapSettings>>builder()
            .mode(PersistenceIterator.ProcessMode.LOOP)
            .iteratorName("LdapGroupScheduled")
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
            .throttleInterval(ofSeconds(45)));

    executor.submit(() -> iterator.process());
  }

  public void wakeup() {
    if (iterator != null) {
      iterator.wakeup();
    }
  }

  @Override
  public void handle(LdapSettings settings) {
    ldapGroupSyncJobHelper.syncJob(settings);
  }
}
