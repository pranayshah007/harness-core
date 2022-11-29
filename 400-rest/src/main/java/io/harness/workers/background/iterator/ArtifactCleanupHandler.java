/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.workers.background.iterator;

import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;

import static java.time.Duration.ofMinutes;

import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceRequiredProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.Account;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ArtifactCleanupService;
import software.wings.service.intfc.SettingsService;
import software.wings.utils.DelegateArtifactCollectionUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArtifactCleanupHandler extends IteratorPumpModeHandler implements Handler<ArtifactStream> {
  public static final String GROUP = "ARTIFACT_STREAM_CRON_GROUP";

  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject @Named("AsyncArtifactCleanupService") private ArtifactCleanupService artifactCleanupServiceAsync;
  @Inject @Named("SyncArtifactCleanupService") private ArtifactCleanupService artifactCleanupServiceSync;
  @Inject ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private MorphiaPersistenceRequiredProvider<ArtifactStream> persistenceProvider;
  @Inject private SettingsService settingsService;

  @Override
  public void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<ArtifactStream, MorphiaFilterExpander<ArtifactStream>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       ArtifactCleanupHandler.class,
                       MongoPersistenceIterator.<ArtifactStream, MorphiaFilterExpander<ArtifactStream>>builder()
                           .clazz(ArtifactStream.class)
                           .fieldName(ArtifactStreamKeys.nextCleanupIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofMinutes(15))
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .filterExpander(query
                               -> query.field(ArtifactStreamKeys.artifactStreamType)
                                      .in(DelegateArtifactCollectionUtils.SUPPORTED_ARTIFACT_CLEANUP_LIST)
                                      .field(ArtifactStreamKeys.collectionEnabled)
                                      .in(Arrays.asList(true, null)))
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "ArtifactCleanup";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(ArtifactStream artifactStream) {
    log.info("Received the artifact cleanup for ArtifactStream");
    executeInternal(artifactStream);
  }

  public void handleManually(ArtifactStream artifactStream, String accountId) {
    log.info("Received the artifact cleanup for ArtifactStream manually");
    executeInternalInSync(artifactStream, accountId);
  }

  private void executeInternal(ArtifactStream artifactStream) {
    String artifactStreamId = artifactStream.getUuid();
    try {
      if (artifactCollectionUtils.skipArtifactStreamIteration(artifactStream, false)) {
        return;
      }
      artifactCleanupServiceAsync.cleanupArtifacts(artifactStream, null);
    } catch (WingsException exception) {
      log.warn("Failed to cleanup artifacts for artifact stream. Reason {}", exception.getMessage());
      exception.addContext(Account.class, artifactStream.getAccountId());
      exception.addContext(ArtifactStream.class, artifactStreamId);
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception e) {
      log.warn("Failed to cleanup artifacts for artifactStream. Reason {}", e.getMessage());
    }
  }

  private void executeInternalInSync(ArtifactStream artifactStream, String accountId) {
    if (artifactCollectionUtils.skipArtifactStreamIteration(artifactStream, false)) {
      return;
    }
    artifactCleanupServiceSync.cleanupArtifacts(artifactStream, accountId);
  }

  public String fetchAccountId(ArtifactStream artifactStream) {
    String artifactStreamType = artifactStream.getArtifactStreamType();
    String accountId;

    if (CUSTOM.name().equals(artifactStreamType)) {
      ArtifactStreamAttributes artifactStreamAttributes =
          artifactCollectionUtils.renderCustomArtifactScriptString((CustomArtifactStream) artifactStream);
      accountId = artifactStreamAttributes.getAccountId();
    } else {
      SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
      if (settingAttribute == null) {
        log.warn("Artifact Server {} was deleted", artifactStream.getSettingId());
        // TODO:: mark inactive maybe
        return null;
      }
      accountId = settingAttribute.getAccountId();
    }
    return accountId;
  }
}
