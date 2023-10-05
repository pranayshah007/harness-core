/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.workers.background.critical.iterator;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.FunctorException;
import io.harness.exception.WingsException;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceRequiredProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.Account;
import software.wings.beans.Permit;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.delegatetasks.buildsource.ArtifactStreamLogContext;
import software.wings.service.impl.PermitServiceImpl;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ArtifactCollectionService;
import software.wings.service.intfc.PermitService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class ArtifactCollectionHandler extends IteratorPumpAndRedisModeHandler implements Handler<ArtifactStream> {
  public static final String GROUP = "ARTIFACT_STREAM_CRON_GROUP";
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofSeconds(30);

  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private PermitService permitService;
  @Inject @Named("AsyncArtifactCollectionService") private ArtifactCollectionService artifactCollectionServiceAsync;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private MorphiaPersistenceRequiredProvider<ArtifactStream> persistenceProvider;

  @Override
  public void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<ArtifactStream, MorphiaFilterExpander<ArtifactStream>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       ArtifactCollectionHandler.class,
                       MongoPersistenceIterator.<ArtifactStream, MorphiaFilterExpander<ArtifactStream>>builder()
                           .clazz(ArtifactStream.class)
                           .fieldName(ArtifactStreamKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .filterExpander(
                               query -> query.field(ArtifactStreamKeys.collectionEnabled).in(Arrays.asList(true, null)))
                           .redistribute(true));
  }

  @Override
  public void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<ArtifactStream, MorphiaFilterExpander<ArtifactStream>>)
            persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                ArtifactCollectionHandler.class,
                MongoPersistenceIterator.<ArtifactStream, MorphiaFilterExpander<ArtifactStream>>builder()
                    .clazz(ArtifactStream.class)
                    .fieldName(ArtifactStreamKeys.nextIteration)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                    .handler(this)
                    .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                    .persistenceProvider(persistenceProvider)
                    .filterExpander(
                        query -> query.field(ArtifactStreamKeys.collectionEnabled).in(Arrays.asList(true, null))));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "ArtifactCollection";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(ArtifactStream artifactStream) {
    try (AutoLogContext ignore2 = new ArtifactStreamLogContext(
             artifactStream.getUuid(), artifactStream.getArtifactStreamType(), OVERRIDE_ERROR)) {
      executeInternal(artifactStream);
    }
  }

  private void executeInternal(ArtifactStream artifactStream) {
    String artifactStreamId = artifactStream.getUuid();
    try {
      if (artifactCollectionUtils.skipArtifactStreamIteration(artifactStream, true)) {
        return;
      }

      int leaseDuration = (int) (TimeUnit.MINUTES.toMillis(2)
          * PermitServiceImpl.getBackoffMultiplier(artifactStream.getFailedCronAttempts()));
      String permitId = permitService.acquirePermit(Permit.builder()
                                                        .appId(artifactStream.fetchAppId())
                                                        .group(GROUP)
                                                        .key(artifactStreamId)
                                                        .expireAt(new Date(System.currentTimeMillis() + leaseDuration))
                                                        .leaseDuration(leaseDuration)
                                                        .accountId(artifactStream.getAccountId())
                                                        .build());
      if (isNotEmpty(permitId)) {
        if (artifactStream.getFailedCronAttempts() > 30) {
          log.info("Permit [{}] acquired for artifactStream [failedCount: {}] for [{}] minutes", permitId,
              artifactStream.getFailedCronAttempts(), TimeUnit.MILLISECONDS.toMinutes(leaseDuration));
        } else {
          log.debug("Permit [{}] acquired for artifactStream [failedCount: {}] for [{}] minutes", permitId,
              artifactStream.getFailedCronAttempts(), TimeUnit.MILLISECONDS.toMinutes(leaseDuration));
        }
        artifactCollectionServiceAsync.collectNewArtifactsAsync(artifactStream, permitId);
      }
    } catch (FunctorException exception) {
      log.warn("Failed to collect artifacts for artifact stream. Reason {}", exception.getMessage());
    } catch (WingsException exception) {
      log.warn("Failed to collect artifacts for artifact stream. Reason {}", exception.getMessage());
      if (artifactStream.getAccountId() != null) {
        exception.addContext(Account.class, artifactStream.getAccountId());
      }
      exception.addContext(ArtifactStream.class, artifactStreamId);
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception e) {
      log.warn("Failed to collect artifacts for artifactStream. Reason {}", e.getMessage());
    }
  }
}
