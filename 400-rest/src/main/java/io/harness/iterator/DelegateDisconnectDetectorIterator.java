/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.notification.NotificationEntity;
import io.harness.notification.NotificationEvent;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.notificationserviceclient.intfc.NotificationServiceClient;
import io.harness.observer.Subject;

import software.wings.service.impl.DelegateDao;
import software.wings.service.impl.DelegateObserver;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateDisconnectDetectorIterator
    extends IteratorPumpAndRedisModeHandler implements MongoPersistenceIterator.Handler<Delegate> {
  private static final long DELEGATE_DISCONNECT_TIMEOUT = 1L;
  private static final long DELEGATE_EXPIRY_CHECK_MINUTES = 1L;

  @Inject private io.harness.iterator.PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<Delegate> persistenceProvider;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Inject private DelegateService delegateService;
  @Inject private DelegateDao delegateDao;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private NotificationServiceClient notificationServiceClient;

  @Inject @Getter private Subject<DelegateObserver> subject = new Subject<>();

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Delegate, MorphiaFilterExpander<Delegate>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions, Delegate.class,
                       MongoPersistenceIterator.<Delegate, MorphiaFilterExpander<Delegate>>builder()
                           .clazz(Delegate.class)
                           .fieldName(DelegateKeys.delegateDisconnectDetectorNextIteration)
                           .filterExpander(q
                               -> q.field(DelegateKeys.lastHeartBeat)
                                      .lessThan(System.currentTimeMillis()
                                          - TimeUnit.MINUTES.toMillis(DELEGATE_DISCONNECT_TIMEOUT)))
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
    iterator =
        (MongoPersistenceIterator<Delegate, MorphiaFilterExpander<Delegate>>)
            persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions, Delegate.class,
                MongoPersistenceIterator.<Delegate, MorphiaFilterExpander<Delegate>>builder()
                    .clazz(Delegate.class)
                    .fieldName(DelegateKeys.delegateDisconnectDetectorNextIteration)
                    .filterExpander(q
                        -> q.field(DelegateKeys.lastHeartBeat)
                               .lessThan(
                                   System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(DELEGATE_DISCONNECT_TIMEOUT)))
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(Duration.ofMinutes(DELEGATE_EXPIRY_CHECK_MINUTES + 2))
                    .handler(this)
                    .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "DelegateDisconnectDetector";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(Delegate delegate) {
    if (isDelegateExpiryCheckDoneAlready(delegate)) {
      return;
    }
    log.info(
        "Delegate detected as disconnected delegate id {}, host name {}", delegate.getUuid(), delegate.getHostName());
    try (AutoLogContext ignore1 = new DelegateLogContext(delegate.getUuid(), OVERRIDE_ERROR);
         AccountLogContext ignore2 = new AccountLogContext(delegate.getAccountId(), OVERRIDE_ERROR)) {
      // trigger disconnect event which marks started delegate task as expired and PT's as unassigned
      delegateService.onDelegateDisconnected(delegate.getAccountId(), delegate.getUuid());
      // mark delegate as disconnected
      delegateDao.delegateDisconnected(delegate.getAccountId(), delegate.getUuid());
      delegateService.updateLastExpiredEventHeartbeatTime(
          delegate.getLastHeartBeat(), delegate.getUuid(), delegate.getAccountId());
      triggerNotificationRequest(delegate);
    }
  }

  private boolean isDelegateExpiryCheckDoneAlready(Delegate delegate) {
    return delegate.getLastExpiredEventHeartbeatTime() != null
        && delegate.getLastExpiredEventHeartbeatTime() == delegate.getLastHeartBeat();
  }

  private void triggerNotificationRequest(Delegate delegate) {
    String notificationTriggerRequestId = generateUuid();
    String orgId = delegate.getOwner() != null
        ? DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(delegate.getOwner().getIdentifier())
        : "";
    String projectId = delegate.getOwner() != null
        ? DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(delegate.getOwner().getIdentifier())
        : "";

    Map<String, String> templateData = new HashMap<>();
    templateData.put("DELEGATE_HOST", delegate.getDelegateGroupName());
    templateData.put("DELEGATE_NAME", delegate.getHostName());
    templateData.put("TEMPLATE_IDENTIFIER", "email_test");
    NotificationTriggerRequest.Builder notificationTriggerRequestBuilder =
        NotificationTriggerRequest.newBuilder()
            .setId(notificationTriggerRequestId)
            .setAccountId(delegate.getAccountId())
            .setOrgId(orgId)
            .setProjectId(projectId)
            .setEventEntity(NotificationEntity.DELEGATE.name())
            .setEvent(NotificationEvent.DELEGATE_DOWN.name())
            .putAllTemplateData(templateData);
    log.info("Sending delegate disconnect notifictaion for {}", delegate.getUuid());
    notificationServiceClient.sendNotification(notificationTriggerRequestBuilder.build());
  }
}
