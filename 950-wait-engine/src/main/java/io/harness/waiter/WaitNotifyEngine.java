/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.waiter;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.waiter.NotifyEvent.Builder.aNotifyEvent;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ProgressData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitInstance.WaitInstanceBuilder;
import io.harness.waiter.persistence.PersistenceWrapper;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.DuplicateKeyException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * WaitNotifyEngine allows tasks to register in waitQueue and get notified via callback.
 * No entry in the waitQueue found for the correlationIds:
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class WaitNotifyEngine {
  public static final int MIN_WAIT_INSTANCE_TIMEOUT = 3;

  @Inject private PersistenceWrapper persistenceWrapper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private NotifyQueuePublisherRegister publisherRegister;

  public String waitForAllOn(String publisherName, NotifyCallback notifyCallback, String... correlationIds) {
    return waitForAllOn(publisherName, notifyCallback, null, correlationIds);
  }

  public String waitForAllOn(
      String publisherName, NotifyCallback callback, ProgressCallback progressCallback, String... correlationIds) {
    Preconditions.checkArgument(isNotEmpty(correlationIds), "correlationIds are null or empty");

    if (log.isDebugEnabled()) {
      log.debug("Received waitForAll on - correlationIds : {}", Arrays.toString(correlationIds));
    }
    final List<String> list;
    if (correlationIds.length == 1) {
      list = singletonList(correlationIds[0]);
    } else {
      // In case of multiple items, we have to make sure that all of them are unique
      Set<String> set = new HashSet<>();
      Collections.addAll(set, correlationIds);
      list = new ArrayList<>(set);
    }

    return waitForAllOn(publisherName, callback, progressCallback, list, Duration.ofSeconds(0));
  }

  public String waitForAllOnInList(String publisherName, OldNotifyCallback callback, List<String> list) {
    return waitForAllOn(publisherName, callback, null, list, Duration.ofSeconds(0));
  }

  public String waitForAllOnInList(
      String publisherName, OldNotifyCallback callback, List<String> list, Duration timeout) {
    return waitForAllOn(publisherName, callback, null, list, timeout);
  }

  public String waitForAllOn(String publisherName, NotifyCallback callback, ProgressCallback progressCallback,
      List<String> list, Duration timeout) {
    final WaitInstanceBuilder waitInstanceBuilder = WaitInstance.builder()
                                                        .uuid(generateUuid())
                                                        .callback(callback)
                                                        .progressCallback(progressCallback)
                                                        .publisher(publisherName)
                                                        .timeout(timeout);

    waitInstanceBuilder.correlationIds(list).waitingOnCorrelationIds(list);

    final String waitInstanceId = persistenceWrapper.saveWithTimeout(waitInstanceBuilder.build(), timeout);

    WaitInstance waitInstance;
    if ((waitInstance = persistenceWrapper.modifyAndFetchWaitInstanceForExistingResponse(waitInstanceId, list))
        != null) {
      if (isEmpty(waitInstance.getWaitingOnCorrelationIds())
          && waitInstance.getCallbackProcessingAt() < System.currentTimeMillis()) {
        sendNotification(waitInstance);
      }
    }

    return waitInstanceId;
  }

  public void progressOn(String correlationId, ProgressData progressData) {
    progressOn(correlationId, progressData, true);
  }

  public void progressOn(String correlationId, ProgressData progressData, boolean usingKryoWithoutReference) {
    Preconditions.checkArgument(isNotBlank(correlationId), "correlationId is null or empty");

    if (log.isDebugEnabled()) {
      log.debug("notify request received for the correlationId : {}", correlationId);
    }

    try {
      persistenceWrapper.save(
          ProgressUpdate.builder()
              .uuid(generateUuid())
              .correlationId(correlationId)
              .createdAt(currentTimeMillis())
              .usingKryoWithoutReference(usingKryoWithoutReference)
              .progressData(usingKryoWithoutReference ? referenceFalseKryoSerializer.asDeflatedBytes(progressData)
                                                      : kryoSerializer.asDeflatedBytes(progressData))
              .build());
    } catch (Exception exception) {
      log.error("Failed to notify for progress of type " + progressData.getClass().getSimpleName(), exception);
    }
  }

  public String doneWith(String correlationId, ResponseData response) {
    return doneWith(correlationId, response, response instanceof ErrorResponseData);
  }

  private String doneWith(String correlationId, ResponseData response, boolean error) {
    Preconditions.checkArgument(isNotBlank(correlationId), "correlationId is null or empty");

    if (log.isDebugEnabled()) {
      log.debug("done with notify request received for the correlationId : {}", correlationId);
    }

    try {
      final Stopwatch stopwatch = Stopwatch.createStarted();
      long doneWithStartTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
      persistenceWrapper.save(NotifyResponse.builder()
                                  .uuid(correlationId)
                                  .createdAt(currentTimeMillis())
                                  .usingKryoWithoutReference(true)
                                  .responseData(referenceFalseKryoSerializer.asDeflatedBytes(response))
                                  .error(error || response instanceof ErrorResponseData)
                                  .build());
      long queryEndTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);

      if (log.isDebugEnabled()) {
        log.debug("Process NotifyResponse mongo queryTime {}", queryEndTime - doneWithStartTime);
      }
      handleNotifyResponse(correlationId);
      return correlationId;
    } catch (DuplicateKeyException | org.springframework.dao.DuplicateKeyException exception) {
      log.warn("Unexpected rate of DuplicateKeyException per correlation", exception);
    } catch (Exception exception) {
      log.error("Failed to notify for response of type " + response.getClass().getSimpleName(), exception);
    }
    return null;
  }

  public void sendNotification(WaitInstance waitInstance) {
    try (WaitInstanceLogContext ignore = new WaitInstanceLogContext(waitInstance.getUuid(), OVERRIDE_NESTS)) {
      String publisher = waitInstance.getPublisher();

      final NotifyQueuePublisher notifyQueuePublisher = publisherRegister.obtain(waitInstance.getPublisher());

      if (notifyQueuePublisher == null) {
        // There is nothing smart that we can do.
        // If there is no publisher we should let people evaluate and handle the problem.
        log.error("Unknown publisher {}", publisher);
        return;
      }

      notifyQueuePublisher.send(aNotifyEvent().waitInstanceId(waitInstance.getUuid()).build());
    }
  }

  public void handleNotifyResponse(String uuid) {
    WaitInstance waitInstance;
    while ((waitInstance = persistenceWrapper.modifyAndFetchWaitInstance(uuid)) != null) {
      if (isEmpty(waitInstance.getWaitingOnCorrelationIds())) {
        sendNotification(waitInstance);
      }
    }
  }

  public boolean doneWithWithoutCallback(@NonNull String correlationId) {
    try {
      WaitInstance waitInstance;
      while ((waitInstance = modifyAndFetchWaitInstance(correlationId)) != null) {
        if (isEmpty(waitInstance.getWaitingOnCorrelationIds())) {
          persistenceWrapper.deleteWaitInstance(waitInstance);
        }
      }
      return true;
    } catch (Exception exception) {
      log.error("Failed to Noop notify for correlationId: {}", correlationId, exception);
      return false;
    }
  }

  private WaitInstance modifyAndFetchWaitInstance(String correlationId) {
    WaitInstance waitInstance = persistenceWrapper.modifyAndFetchWaitInstance(correlationId);
    if (waitInstance == null) {
      log.warn("Not found a WaitInstance with correlationId [{}]", correlationId);
    }
    return waitInstance;
  }
}
