/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.githubrestraint.services;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NgIteratorConfig;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitopsprovider.entity.GithubRestraintInstance;
import io.harness.gitopsprovider.entity.GithubRestraintInstance.GithubRestraintInstanceKeys;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

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
    PersistenceIteratorFactory.PumpExecutorOptions executorOptions =
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("GithubRestraintInstance-Monitor")
            .poolSize(config.getThreadPoolSize())
            .interval(ofSeconds(config.getTargetIntervalInSeconds()))
            .build();
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
        GithubRestraintPersistenceMonitor.class,
        MongoPersistenceIterator.<GithubRestraintInstance, SpringFilterExpander>builder()
            .clazz(GithubRestraintInstance.class)
            .fieldName(GithubRestraintInstanceKeys.nextIteration)
            .filterExpander(q -> q.addCriteria(where(GithubRestraintInstanceKeys.state).in(ACTIVE, BLOCKED)))
            .targetInterval(ofSeconds(30))
            .acceptableNoAlertDelay(ofSeconds(30))
            .acceptableExecutionTime(ofSeconds(30))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
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
