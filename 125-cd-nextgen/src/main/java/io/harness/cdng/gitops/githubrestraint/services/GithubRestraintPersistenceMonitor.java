/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.githubrestraint.services;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.NgIteratorConfig;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitopsprovider.entity.GithubRestraintInstance;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(GITOPS)
@Slf4j
public class GithubRestraintPersistenceMonitor implements Handler<GithubRestraintInstance> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private GithubRestraintInstanceService githubRestraintInstanceService;

  public void registerIterators(NgIteratorConfig config) {
    // TODO: This has no point this is just here as a back up. If at all we need to implement this we can expose an
    // internal API from PMS for nodeExecution status check lets not do that in first cut

    //    PersistenceIteratorFactory.PumpExecutorOptions executorOptions =
    //        PersistenceIteratorFactory.PumpExecutorOptions.builder()
    //            .name("GithubRestraintInstance-Monitor")
    //            .poolSize(config.getThreadPoolSize())
    //            .interval(ofSeconds(config.getTargetIntervalInSeconds()))
    //            .build();
    //    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
    //        GithubRestraintPersistenceMonitor.class,
    //        MongoPersistenceIterator.<GithubRestraintInstance, SpringFilterExpander>builder()
    //            .clazz(GithubRestraintInstance.class)
    //            .fieldName(GithubRestraintInstanceKeys.nextIteration)
    //            .filterExpander(q -> q.addCriteria(where(GithubRestraintInstanceKeys.state).in(ACTIVE, BLOCKED)))
    //            .targetInterval(ofSeconds(30))
    //            .acceptableNoAlertDelay(ofSeconds(30))
    //            .acceptableExecutionTime(ofSeconds(30))
    //            .handler(this)
    //            .schedulingType(REGULAR)
    //            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
    //            .redistribute(true));
  }

  @Override
  public void handle(GithubRestraintInstance instance) {
    try {
      githubRestraintInstanceService.processRestraint(instance);
    } catch (Exception exception) {
      log.error("Failed to process github restraint {}", instance.getUuid(), exception);
    }
  }
}
