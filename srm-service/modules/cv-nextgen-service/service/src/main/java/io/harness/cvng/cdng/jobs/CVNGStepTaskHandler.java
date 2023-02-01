/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.jobs;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;

import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.cdng.entities.CVNGStepTask.CVNGStepTaskKeys;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
@Singleton
public class CVNGStepTaskHandler implements MongoPersistenceIterator.Handler<CVNGStepTask> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<CVNGStepTask> persistenceProvider;
  @Inject private CVNGStepTaskService cvngStepTaskService;

  public void registerIterator() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("cvngStepTask")
            .poolSize(3)
            .interval(Duration.ofSeconds(10))
            .build(),
        CVNGStepTaskHandler.class,
        MongoPersistenceIterator.<CVNGStepTask, MorphiaFilterExpander<CVNGStepTask>>builder()
            .clazz(CVNGStepTask.class)
            .fieldName(CVNGStepTaskKeys.asyncTaskIteration)
            .targetInterval(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .handler(this)
            .filterExpander(query -> query.filter(CVNGStepTaskKeys.status, CVNGStepTask.Status.IN_PROGRESS))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }
  @Override
  public void handle(CVNGStepTask entity) {
    cvngStepTaskService.notifyCVNGStep(entity);
  }
}
