/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateTaskExpiryCheckIterator implements MongoPersistenceIterator.Handler<Delegate> {
  private static final long TASK_EXPIRY_TIMEOUT = 3L;
  private static final long TASK_EXPIRY_CHECK_INTERVAL_IN_MINUTES = 5L;

  @Inject private io.harness.iterator.PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<Delegate> persistenceProvider;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Inject private DelegateService delegateService;
  @Inject private FeatureFlagService featureFlagService;

  public void registerIterators(int threadPoolSize) {
    PumpExecutorOptions options = PumpExecutorOptions.builder()
                                      .interval(Duration.ofMinutes(TASK_EXPIRY_CHECK_INTERVAL_IN_MINUTES))
                                      .poolSize(threadPoolSize)
                                      .name("DelegateTaskExpiryCheck")
                                      .build();

    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(options, Delegate.class,
        MongoPersistenceIterator.<Delegate, MorphiaFilterExpander<Delegate>>builder()
            .clazz(Delegate.class)
            .fieldName(DelegateKeys.taskExpiryCheckNextIteration)
            .filterExpander(q
                -> q.field(DelegateKeys.status)
                       .equal(DelegateInstanceStatus.ENABLED)
                       .field(DelegateKeys.lastHeartBeat)
                       .lessThan(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(TASK_EXPIRY_TIMEOUT)))
            .targetInterval(Duration.ofMinutes(TASK_EXPIRY_CHECK_INTERVAL_IN_MINUTES))
            .acceptableNoAlertDelay(Duration.ofMinutes(TASK_EXPIRY_CHECK_INTERVAL_IN_MINUTES + 2))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(Delegate delegate) {
    if (isDelegateExpiryCheckDoneAlready(delegate)) {
      return;
    }
    log.info("Expiring all tasks for delegate [{}], accountId: [{}]", delegate.getUuid(), delegate.getAccountId());
    try (AutoLogContext ignore1 = new DelegateLogContext(delegate.getUuid(), OVERRIDE_ERROR);
         AccountLogContext ignore2 = new AccountLogContext(delegate.getAccountId(), OVERRIDE_ERROR)) {
      delegateTaskServiceClassic.markAllTasksFailedForDelegate(delegate.getAccountId(), delegate.getUuid());
      delegateService.updateLastExpiredEventHeartbeatTime(
          delegate.getLastHeartBeat(), delegate.getUuid(), delegate.getAccountId());
    }
  }

  private boolean isDelegateExpiryCheckDoneAlready(Delegate delegate) {
    return delegate.getLastExpiredEventHeartbeatTime() != null
        && delegate.getLastExpiredEventHeartbeatTime() == delegate.getLastHeartBeat();
  }
}
