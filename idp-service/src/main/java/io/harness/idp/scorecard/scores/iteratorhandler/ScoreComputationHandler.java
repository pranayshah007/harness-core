/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.iteratorhandler;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

import java.util.Collections;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ScoreComputationHandler implements MongoPersistenceIterator.Handler<NamespaceEntity> {
  private PersistenceIteratorFactory persistenceIteratorFactory;
  private MongoTemplate mongoTemplate;
  private ScoreService scoreService;

  @Override
  public void handle(NamespaceEntity namespaceEntity) {
    log.info("Scorecard Score Computation for account - {} from iterator started at - {}",
        namespaceEntity.getAccountIdentifier(), System.currentTimeMillis());
    scoreService.computeScores(
        namespaceEntity.getAccountIdentifier(), Collections.emptyList(), Collections.emptyList());
    log.info("Scorecard Score Computation for account - {} from iterator is completed at - {}",
        namespaceEntity.getAccountIdentifier(), System.currentTimeMillis());
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    long INTERVAL = iteratorConfig.getTargetIntervalInSeconds() / 12;

    long ACCEPTABLE_NO_ALERT_DELAY = (iteratorConfig.getTargetIntervalInSeconds() * 3) / 2;

    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("IDPScoreCardScoreCalculator")
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(INTERVAL))
            .build(),
        ScoreComputationHandler.class,
        MongoPersistenceIterator.<NamespaceEntity, SpringFilterExpander>builder()
            .clazz(NamespaceEntity.class)
            .fieldName(NamespaceEntity.NamespaceKeys.nextIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(
                ofSeconds(60)) // This is the time we expect the processing to complete if it takes more we will log.
                               // Corrective action here is to see why things are slow and either increase the param
                               // time or correct business logic.
            .acceptableNoAlertDelay(
                ofSeconds(ACCEPTABLE_NO_ALERT_DELAY)) // This is only used to log delays. This log signifies that the
                                                      // records became eligible long time ago before the thread
                                                      // actually came here to process. If we are encountering this log
                                                      // it means our thread pool must be increased for the iterator.
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }
}
