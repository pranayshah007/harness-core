package io.harness.iterator;

import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.FeatureName.DELEGATE_TASK_CAPACITY_CHECK;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_REBROADCAST;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.utils.DelegateLogContextHelper;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.observer.Subject;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.version.VersionInfoManager;

import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.service.impl.DelegateDao;
import software.wings.service.impl.DelegateObserver;
import software.wings.service.impl.DelegateTaskBroadcastHelper;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class DelegateTaskRebroadcastIterator
    extends IteratorPumpAndRedisModeHandler implements MongoPersistenceIterator.Handler<DelegateTask> {
  private static final long DELEGATE_DISCONNECT_TIMEOUT = 5L;
  private static final long DELEGATE_EXPIRY_CHECK_MINUTES = 1L;

  @Inject private io.harness.iterator.PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<DelegateTask> persistenceProvider;
  @Inject private Clock clock;
  @Inject private HPersistence persistence;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private DelegateSelectionLogsService delegateSelectionLogsService;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private DelegateTaskBroadcastHelper broadcastHelper;

  private static long BROADCAST_INTERVAL = TimeUnit.MINUTES.toMillis(1);
  private static int MAX_BROADCAST_ROUND = 3;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    long now = clock.millis();
    iterator =
        (MongoPersistenceIterator<DelegateTask, MorphiaFilterExpander<DelegateTask>>)
            persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions, DelegateTask.class,
                MongoPersistenceIterator.<DelegateTask, MorphiaFilterExpander<DelegateTask>>builder()
                    .clazz(DelegateTask.class)
                    .fieldName(DelegateTaskKeys.delegateTaskFailIteration)
                    .filterExpander(q
                        -> q.field(DelegateTaskKeys.status)
                               .equal(QUEUED)
                               .field(DelegateTaskKeys.nextBroadcast)
                               .lessThan(now)
                               .field(DelegateTaskKeys.expiry)
                               .greaterThan(now)
                               .field(DelegateTaskKeys.broadcastRound)
                               .lessThan(MAX_BROADCAST_ROUND)
                               .field(DelegateTaskKeys.delegateId)
                               .doesNotExist())
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(Duration.ofMinutes(DELEGATE_EXPIRY_CHECK_MINUTES + 2))
                    .handler(this)
                    .schedulingType(REGULAR)
                    .persistenceProvider(persistenceProvider)
                    .redistribute(true));
  }

  @Override
  protected void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    long now = clock.millis();
    iterator = (MongoPersistenceIterator<DelegateTask, MorphiaFilterExpander<DelegateTask>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       DelegateTask.class,
                       MongoPersistenceIterator.<DelegateTask, MorphiaFilterExpander<DelegateTask>>builder()
                           .clazz(DelegateTask.class)
                           .fieldName(DelegateTask.DelegateTaskKeys.delegateTaskFailIteration)
                           .targetInterval(targetInterval)
                           .filterExpander(q
                               -> q.field(DelegateTaskKeys.status)
                                      .equal(QUEUED)
                                      .field(DelegateTaskKeys.nextBroadcast)
                                      .lessThan(now)
                                      .field(DelegateTaskKeys.expiry)
                                      .greaterThan(now)
                                      .field(DelegateTaskKeys.broadcastRound)
                                      .lessThan(MAX_BROADCAST_ROUND)
                                      .field(DelegateTaskKeys.delegateId)
                                      .doesNotExist())
                           .acceptableNoAlertDelay(Duration.ofMinutes(DELEGATE_EXPIRY_CHECK_MINUTES + 2))
                           .handler(this)
                           .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "DelegateTaskRebroadcastIterator";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(DelegateTask delegateTask) {
    LinkedList<String> eligibleDelegatesList = delegateTask.getEligibleToExecuteDelegateIds();

    if (isEmpty(eligibleDelegatesList)) {
      log.info("No eligible delegates for task {}", delegateTask.getUuid());
      return;
    }

    // add connected eligible delegates to broadcast list. Also rotate the eligibleDelegatesList list
    List<String> broadcastToDelegates = Lists.newArrayList();
    int broadcastLimit = Math.min(eligibleDelegatesList.size(), 10);

    Iterator<String> delegateIdIterator = eligibleDelegatesList.iterator();

    while (delegateIdIterator.hasNext() && broadcastLimit > broadcastToDelegates.size()) {
      String delegateId = eligibleDelegatesList.removeFirst();
      broadcastToDelegates.add(delegateId);
      eligibleDelegatesList.addLast(delegateId);
    }
    long nextInterval = TimeUnit.SECONDS.toMillis(5);
    int broadcastRoundCount = delegateTask.getBroadcastRound();
    Set<String> alreadyTriedDelegates =
        Optional.ofNullable(delegateTask.getAlreadyTriedDelegates()).orElse(Sets.newHashSet());

    // if all delegates got one round of rebroadcast, then increase broadcast interval & broadcastRound
    if (alreadyTriedDelegates.containsAll(delegateTask.getEligibleToExecuteDelegateIds())) {
      alreadyTriedDelegates.clear();
      broadcastRoundCount++;
      nextInterval = (long) broadcastRoundCount * BROADCAST_INTERVAL;
      if (featureFlagService.isEnabled(DELEGATE_TASK_CAPACITY_CHECK, delegateTask.getAccountId())) {
        // If this FF is enabled then reset broadcastRoundCount to 0
        // as we want to keep rebroadcasting until the task expires.
        // The nextInterval for rebroadcast in this case will always be 1 minute.
        broadcastRoundCount = 0;
      }
    }
    alreadyTriedDelegates.addAll(broadcastToDelegates);

    long now = clock.millis();
    UpdateOperations<DelegateTask> updateOperations =
        persistence.createUpdateOperations(DelegateTask.class)
            .set(DelegateTaskKeys.lastBroadcastAt, now)
            .set(DelegateTaskKeys.broadcastCount, delegateTask.getBroadcastCount() + 1)
            .set(DelegateTaskKeys.eligibleToExecuteDelegateIds, eligibleDelegatesList)
            .set(DelegateTaskKeys.nextBroadcast, now + nextInterval)
            .set(DelegateTaskKeys.alreadyTriedDelegates, alreadyTriedDelegates)
            .set(DelegateTaskKeys.broadcastRound, broadcastRoundCount);

    persistence.update(persistence.createQuery(DelegateTask.class)
                           .filter(DelegateTaskKeys.uuid, delegateTask.getUuid())
                           .filter(DelegateTaskKeys.broadcastCount, delegateTask.getBroadcastCount()),
        updateOperations);

    // update failed, means this was broadcast by some other manager
    if (delegateTask == null) {
      log.warn("Cannot find delegate task, update failed on broadcast");
      return;
    }
    delegateTask.setBroadcastToDelegateIds(broadcastToDelegates);
    delegateSelectionLogsService.logBroadcastToDelegate(Sets.newHashSet(broadcastToDelegates), delegateTask);

    if (delegateTask.getTaskDataV2() != null) {
      rebroadcastDelegateTaskUsingTaskDataV2(delegateTask);
    } else {
      rebroadcastDelegateTaskUsingTaskData(delegateTask);
    }
  }

  private void rebroadcastDelegateTaskUsingTaskData(DelegateTask delegateTask) {
    try (AutoLogContext ignore1 = DelegateLogContextHelper.getLogContext(delegateTask);
         AutoLogContext ignore2 = new AccountLogContext(delegateTask.getAccountId(), OVERRIDE_ERROR)) {
      log.info("ST: Rebroadcast queued task id {} on broadcast attempt: {} on round {} to {} ", delegateTask.getUuid(),
          delegateTask.getBroadcastCount(), delegateTask.getBroadcastRound(), delegateTask.getBroadcastToDelegateIds());
      delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_REBROADCAST);
      broadcastHelper.rebroadcastDelegateTask(delegateTask);
    }
  }

  private void rebroadcastDelegateTaskUsingTaskDataV2(DelegateTask delegateTask) {
    try (AutoLogContext ignore1 = DelegateLogContextHelper.getLogContext(delegateTask);
         AutoLogContext ignore2 = new AccountLogContext(delegateTask.getAccountId(), OVERRIDE_ERROR)) {
      log.info("ST: Rebroadcast queued task id {} on broadcast attempt: {} on round {} to {} ", delegateTask.getUuid(),
          delegateTask.getBroadcastCount(), delegateTask.getBroadcastRound(), delegateTask.getBroadcastToDelegateIds());
      delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_REBROADCAST);
      broadcastHelper.rebroadcastDelegateTaskV2(delegateTask);
    }
  }
}
