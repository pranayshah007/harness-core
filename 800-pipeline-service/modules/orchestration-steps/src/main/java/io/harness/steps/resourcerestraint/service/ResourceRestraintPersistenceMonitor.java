/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance.ResourceRestraintInstanceKeys;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(PIPELINE)
@Slf4j
public class ResourceRestraintPersistenceMonitor implements Handler<ResourceRestraintInstance> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Inject MongoTemplate mongoTemplate;

  public void registerIterators(IteratorConfig config) {
    PumpExecutorOptions executorOptions = PumpExecutorOptions.builder()
                                              .name("ResourceRestraintInstance-Monitor")
                                              .poolSize(config.getThreadPoolCount())
                                              .interval(ofSeconds(config.getTargetIntervalInSeconds()))
                                              .build();
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
        ResourceRestraintPersistenceMonitor.class,
        MongoPersistenceIterator.<ResourceRestraintInstance, SpringFilterExpander>builder()
            .clazz(ResourceRestraintInstance.class)
            .fieldName(ResourceRestraintInstanceKeys.nextIteration)
            .filterExpander(q -> q.addCriteria(where(ResourceRestraintInstanceKeys.state).in(ACTIVE, BLOCKED)))
            .targetInterval(ofSeconds(30))
            .acceptableNoAlertDelay(ofSeconds(30))
            .acceptableExecutionTime(ofSeconds(30))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public void handle(ResourceRestraintInstance instance) {
    try {
      resourceRestraintInstanceService.processRestraint(instance);
    } catch (Exception exception) {
      log.error("Failed to process restraint {}", instance.getUuid(), exception);
    }
  }
}
