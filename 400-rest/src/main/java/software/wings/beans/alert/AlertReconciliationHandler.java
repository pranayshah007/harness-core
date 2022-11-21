/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.alert;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;

import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.persistence.HPersistence;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.alerts.AlertStatus;
import software.wings.beans.alert.Alert.AlertKeys;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AssignDelegateService;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AlertReconciliationHandler extends IteratorPumpModeHandler implements Handler<Alert> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;

  @Inject private AssignDelegateService assignDelegateService;
  @Inject private AlertService alertService;

  @Inject private HPersistence persistence;
  @Inject private MorphiaPersistenceProvider<Alert> persistenceProvider;
  @Inject private AccountService accountService;

  @Override
  protected void createAndStartIterator(PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Alert, MorphiaFilterExpander<Alert>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       AlertReconciliationHandler.class,
                       MongoPersistenceIterator.<Alert, MorphiaFilterExpander<Alert>>builder()
                           .clazz(Alert.class)
                           .fieldName(AlertKeys.alertReconciliation_nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofMinutes(5))
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .filterExpander(query
                               -> query.filter(AlertKeys.alertReconciliation_needed, Boolean.TRUE)
                                      .field(AlertKeys.status)
                                      .notEqual(AlertStatus.Closed))
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "AlertReconciliation";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(Alert alert) {
    persistence.update(
        alert, persistence.createUpdateOperations(Alert.class).unset(AlertKeys.alertReconciliation_needed));
  }
}
