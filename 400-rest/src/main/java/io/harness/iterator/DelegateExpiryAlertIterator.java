/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.iterator;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.entities.NotificationEntity;
import io.harness.notification.entities.NotificationEvent;
import io.harness.notification.notificationclient.NotificationClient;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateExpiryAlertIterator
    extends IteratorPumpAndRedisModeHandler implements MongoPersistenceIterator.Handler<DelegateGroup> {
  @Inject private io.harness.iterator.PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<DelegateGroup> persistenceProvider;
  ;
  @Inject private NotificationClient notificationClient;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<DelegateGroup, MorphiaFilterExpander<DelegateGroup>>)
            persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions, DelegateGroup.class,
                MongoPersistenceIterator.<DelegateGroup, MorphiaFilterExpander<DelegateGroup>>builder()
                    .clazz(DelegateGroup.class)
                    .fieldName(DelegateGroupKeys.delegateExpiryAlertNextIteration)
                    .filterExpander(q
                        -> q.field(DelegateGroupKeys.delegatesExpireOn)
                               .lessThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45)))
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(Duration.ofMinutes(2))
                    .handler(this)
                    .schedulingType(REGULAR)
                    .persistenceProvider(persistenceProvider)
                    .redistribute(true));
  }

  @Override
  protected void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<DelegateGroup, MorphiaFilterExpander<DelegateGroup>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       DelegateGroup.class,
                       MongoPersistenceIterator.<DelegateGroup, MorphiaFilterExpander<DelegateGroup>>builder()
                           .clazz(DelegateGroup.class)
                           .fieldName(DelegateGroupKeys.delegateExpiryAlertNextIteration)
                           .filterExpander(q
                               -> q.field(DelegateGroupKeys.delegatesExpireOn)
                                      .lessThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)))
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(Duration.ofMinutes(2))
                           .handler(this)
                           .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "DelegateExpiryAlert";
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(DelegateGroup delegateGroup) {
    String notificationTriggerRequestId = generateUuid();
    String orgId = delegateGroup.getOwner() != null
        ? DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(delegateGroup.getOwner().getIdentifier())
        : "";
    String projectId = delegateGroup.getOwner() != null
        ? DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(delegateGroup.getOwner().getIdentifier())
        : "";

    Map<String, String> templateData = new HashMap<>();
    templateData.put("TEMPLATE_IDENTIFIER", "delegate_expired");
    templateData.put("DELEGATE_NAME", delegateGroup.getIdentifier());
    NotificationTriggerRequest.Builder notificationTriggerRequestBuilder =
        NotificationTriggerRequest.newBuilder()
            .setId(notificationTriggerRequestId)
            .setAccountId(delegateGroup.getAccountId())
            .setOrgId(orgId)
            .setProjectId(projectId)
            .setEventEntity(NotificationEntity.DELEGATE.name())
            .setEvent(NotificationEvent.DELEGATE_EXPIRED.name())
            .putAllTemplateData(templateData);
    log.info("Sending delegate expiry notification for {}", delegateGroup.getUuid());
    notificationClient.sendNotificationTrigger(notificationTriggerRequestBuilder.build());
  }
}
