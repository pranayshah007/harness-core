/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static software.wings.utils.TimeUtils.isWeekend;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import software.wings.beans.DeletedEntity;
import software.wings.beans.DeletedEntity.DeletedEntityKeys;
import software.wings.beans.DeletedEntity.DeletedEntityType;
import software.wings.helpers.ext.account.DeleteAccountHelper;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeletedEntityHandler extends IteratorPumpModeHandler implements Handler<DeletedEntity> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<DeletedEntity> persistenceProvider;
  @Inject private DeleteAccountHelper deleteAccountHelper;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<DeletedEntity, MorphiaFilterExpander<DeletedEntity>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       DeletedEntityHandler.class,
                       MongoPersistenceIterator.<DeletedEntity, MorphiaFilterExpander<DeletedEntity>>builder()
                           .clazz(DeletedEntity.class)
                           .fieldName(DeletedEntityKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofMinutes(Integer.MAX_VALUE))
                           .acceptableExecutionTime(ofSeconds(120))
                           .persistenceProvider(persistenceProvider)
                           .handler(this)
                           .schedulingType(REGULAR)
                           .redistribute(true));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "DeletedEntityIterator";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(DeletedEntity entity) {
    // handle deleted entities only on weekend
    if (isWeekend()) {
      DeletedEntityType entityType = entity.getEntityType();
      if (entityType.equals(DeletedEntityType.ACCOUNT)) {
        deleteAccountHelper.handleDeletedAccount(entity);
      } else {
        log.error("Unknown entity type: {}", entityType);
      }
    }
  }
}
