/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.annotations.dev.HarnessModule._970_RBAC_CORE;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpModeHandler;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistentIterable;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import software.wings.beans.security.AccessRequest;
import software.wings.beans.security.AccessRequest.AccessRequestKeys;
import software.wings.service.intfc.AccessRequestService;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.time.Duration;

@OwnedBy(HarnessTeam.PL)
@TargetModule(_970_RBAC_CORE)
public class AccessRequestHandler extends IteratorPumpModeHandler implements Handler<AccessRequest> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private AccessRequestService accessRequestService;
  @Inject private MorphiaPersistenceProvider<AccessRequest> persistenceProvider;
  @Inject private AccountService accountService;
  private PersistenceIterator<PersistentIterable> accessRequestHandler;

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<AccessRequest, MorphiaFilterExpander<AccessRequest>>)
            persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions, AccessRequest.class,
                MongoPersistenceIterator.<AccessRequest, MorphiaFilterExpander<AccessRequest>>builder()
                    .clazz(AccessRequest.class)
                    .filterExpander(query -> query.filter(AccessRequestKeys.accessActive, Boolean.TRUE))
                    .fieldName(AccessRequestKeys.nextIteration)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ofSeconds(15))
                    .acceptableExecutionTime(ofSeconds(10))
                    .handler(this)
                    .schedulingType(REGULAR)
                    .persistenceProvider(persistenceProvider)
                    .redistribute(true));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "AccessRequestHandler";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(AccessRequest accessRequest) {
    accessRequestService.checkAndUpdateAccessRequests(accessRequest);
  }
}
