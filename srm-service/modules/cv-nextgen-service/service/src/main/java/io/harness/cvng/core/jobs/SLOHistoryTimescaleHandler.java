/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;

import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLOTimeScaleService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class SLOHistoryTimescaleHandler implements MongoPersistenceIterator.Handler<AbstractServiceLevelObjective> {
  @Inject SLOTimeScaleService sloTimeScaleService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<AbstractServiceLevelObjective> persistenceProvider;

  @Inject Clock clock;
  public void registerIterator() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("SLOHistoryTimescaleHandler")
            .poolSize(3)
            .interval(Duration.ofSeconds(10))
            .build(),
        AbstractServiceLevelObjective.class,
        MongoPersistenceIterator
            .<AbstractServiceLevelObjective, MorphiaFilterExpander<AbstractServiceLevelObjective>>builder()
            .clazz(AbstractServiceLevelObjective.class)
            .fieldName(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.sloHistoryTimescaleIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .handler(this)
            .schedulingType(REGULAR)
            .filterExpander(query
                -> query
                       .criteria(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.sloHistoryTimescaleIteration)
                       .greaterThan(clock.instant().minus(1, ChronoUnit.MINUTES).toEpochMilli()))
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(AbstractServiceLevelObjective serviceLevelObjective) {
    sloTimeScaleService.insertSLOHistory(serviceLevelObjective);
  }
}
