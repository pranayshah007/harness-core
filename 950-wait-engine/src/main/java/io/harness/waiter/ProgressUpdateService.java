/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.waiter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ProgressData;
import io.harness.waiter.persistence.PersistenceWrapper;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ProgressUpdateService implements Runnable {
  @Inject private Injector injector;
  @Inject private PersistenceWrapper persistenceWrapper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private WaitInstanceService waitInstanceService;

  private final LoadingCache<String, String> busyCorrelationIds = CacheBuilder.newBuilder()
                                                                      .maximumSize(1000)
                                                                      .expireAfterWrite(1, TimeUnit.MINUTES)
                                                                      .build(new CacheLoader<String, String>() {
                                                                        @Override
                                                                        public String load(@NonNull String key)
                                                                            throws Exception {
                                                                          return key;
                                                                        }
                                                                      });

  @Override
  public void run() {
    ProgressUpdate progressUpdate = null;
    while (true) {
      try {
        final long now = System.currentTimeMillis();
        progressUpdate = waitInstanceService.fetchForProcessingProgressUpdate(busyCorrelationIds.asMap().keySet(), now);
        if (progressUpdate == null) {
          break;
        }

        if (progressUpdate.getExpireProcessing() > now) {
          busyCorrelationIds.put(progressUpdate.getCorrelationId(), progressUpdate.getCorrelationId());
          continue;
        }
        if (log.isDebugEnabled()) {
          log.debug("Starting to process progress response");
        }

        ProgressData progressData = progressUpdate.isUsingKryoWithoutReference()
            ? (ProgressData) referenceFalseKryoSerializer.asInflatedObject(progressUpdate.getProgressData())
            : (ProgressData) kryoSerializer.asInflatedObject(progressUpdate.getProgressData());

        List<WaitInstance> waitInstances = persistenceWrapper.fetchWaitInstances(progressUpdate.getCorrelationId());
        for (WaitInstance waitInstance : waitInstances) {
          ProgressCallback progressCallback = waitInstance.getProgressCallback();
          if (progressCallback != null) {
            injector.injectMembers(progressCallback);
            progressCallback.notify(progressUpdate.getCorrelationId(), progressData);
          } else {
            log.warn(String.format("Found null callback for correlationId: [%s]", progressUpdate.getCorrelationId()));
          }
        }
        if (log.isDebugEnabled()) {
          log.debug("Processed progress response for correlationId - " + progressUpdate.getCorrelationId()
              + " and waitInstanceIds - "
              + waitInstances.stream().map(WaitInstance::getUuid).collect(Collectors.toList()));
        }
      } catch (IllegalStateException e) {
        log.error("Caught Illegal Stage exception, probably mongo client reset", e);
        break;
      } catch (Exception e) {
        log.error("Exception occurred while running progress service", e);
      } finally {
        try {
          if (progressUpdate != null) {
            log.debug(String.format(
                "Deleting progressUpdate record for correlationId: [%s]", progressUpdate.getCorrelationId()));
            persistenceWrapper.delete(progressUpdate);
          }
        } catch (GeneralException e) {
          // do nothing as failure in delete because of 0 count can be ignored
        } catch (Exception e) {
          log.error("Exception occurred while deleting progressUpdate service record for uuid "
                  + progressUpdate.getUuid() + " and correlationId " + progressUpdate.getCorrelationId(),
              e);
        }
      }
    }
  }
}
