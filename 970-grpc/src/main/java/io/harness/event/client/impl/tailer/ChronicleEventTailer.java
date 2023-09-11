/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.client.impl.tailer;

import static com.google.common.base.Verify.verify;
import static java.util.Objects.requireNonNull;

import io.harness.event.EventPublisherGrpc.EventPublisherBlockingStub;
import io.harness.event.PublishMessage;
import io.harness.event.PublishRequest;
import io.harness.event.PublishResponse;
import io.harness.eventpublisherclient.EventPublisherClient;
import io.harness.flow.BackoffScheduler;
import io.harness.logging.LoggingListener;
import io.harness.util.EventServiceRestUtils;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.File;
import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.RollingChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import retrofit2.Call;

/**
 * Tails the chronicle-queue and publishes the events over rpc.
 * <p/>
 * Intentionally not thread-safe as CQ tailers are also not thread-safe. The methods in this class, and {@link
 * FileDeletionManager} are supposed to be executed by the same single thread.
 */
@Slf4j
@Singleton
public class ChronicleEventTailer extends AbstractScheduledService {
  private static final String READ_TAILER = "read-tailer";

  // Batching settings
  private static final int MAX_BATCH_COUNT = 500;
  private static final int MAX_BATCH_BYTES = 1024 * 256; // 256KB

  private final ExcerptTailer readTailer;

  private final FileDeletionManager fileDeletionManager;
  private final BackoffScheduler scheduler;
  private final Sampler sampler;

  private final EventPublisherBlockingStub blockingStub;

  private final RollingChronicleQueue queue;

  private final EventPublisherClient eventPublisherClient;

  private String accountId;

  @Inject
  ChronicleEventTailer(@Nullable EventPublisherBlockingStub blockingStub, EventPublisherClient eventPublisherClient,
      @Named("tailer") RollingChronicleQueue chronicleQueue, FileDeletionManager fileDeletionManager,
      @Named("tailer") BackoffScheduler backoffScheduler) {
    this.blockingStub = blockingStub;
    this.eventPublisherClient = eventPublisherClient;
    this.queue = chronicleQueue;
    this.readTailer = chronicleQueue.createTailer(READ_TAILER);
    this.fileDeletionManager = fileDeletionManager;
    this.scheduler = backoffScheduler;
    this.sampler = new Sampler(Duration.ofMinutes(1));
    addListener(new LoggingListener(this), MoreExecutors.directExecutor());
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  @Override
  protected void startUp() {
    try {
      log.info("Starting up");
      if (fileDeletionManager.getSentIndex() == 0 && readTailer.index() != 0) {
        // Only for migration when readTailer is present, and sentTailer is not.
        log.info("Index of sent-tailer is 0. Setting it to read-tailer index");
        fileDeletionManager.setSentIndex(readTailer.index());
      }
      printStats();
      fileDeletionManager.deleteOlderFiles();
    } catch (Exception e) {
      log.error("Exception in startUp", e);
    }
  }

  @Override
  protected void shutDown() {
    try {
      log.info("Shutting down");
      printStats();
      fileDeletionManager.deleteOlderFiles();
    } catch (Exception e) {
      log.error("Exception in shutDown", e);
    } finally {
      this.queue.close();
      log.info("Successfully closed the queue.");
    }
  }

  private void printStats() {
    try {
      long readIndex = readTailer.index();
      long sentIndex = fileDeletionManager.getSentIndex();
      long endIndex = queue.createTailer().toEnd().index();
      long excerptCount = queue.countExcerpts(readIndex, endIndex);
      log.info("index.read-tailer={},  index.sent-tailer={}, index.end={}, excerptCount={}", readIndex, sentIndex,
          endIndex, excerptCount);
    } catch (OverlappingFileLockException exception) {
      log.error("======== OverlappingFileLockException has been thrown =======");
      deleteFilesAndStartTailerService();
    } catch (Exception e) {
      log.error("Exception in printStats", e);
    }
  }

  public void deleteFilesAndStartTailerService() {
    deleteQueueFiles();
    // And start the Tailer Service.
    log.info("EX::: Starting chronicle event trailer");
    this.startAsync().awaitRunning();
    log.info("EX--- Started chronicle event trailer");
  }

  private void deleteQueueFiles() {
    // Close the ChronicleQueue
    queue.close();

    File[] filesInDir = queue.file().listFiles();
    log.info("Total files in the Queue: {}", filesInDir.length);
    if (filesInDir == null) {
      log.info("No files to delete.");
      return;
    }
    boolean anyFilesDeleted = false;
    List<File> olderFiles = Arrays.stream(filesInDir).sorted().collect(Collectors.toList());
    if (!olderFiles.isEmpty()) {
      log.info("Deleting files {}", olderFiles);
      for (File file : olderFiles) {
        try {
          anyFilesDeleted = Files.deleteIfExists(file.toPath()) || anyFilesDeleted;
        } catch (Exception e) {
          log.error("Failed to delete {}", file.toPath(), e);
        }
      }
      log.info("File deletion completed. anyFilesDeleted: {}", anyFilesDeleted);
    }
  }

  @Override
  protected void runOneIteration() {
    // service will terminate if exception is not caught.
    try {
      sampler.updateTime();
      sampler.sampled(() -> log.info("Checking for messages to publish"));
      Batch batchToSend = new Batch(MAX_BATCH_BYTES, MAX_BATCH_COUNT);
      while (!batchToSend.isFull()) {
        long endIndex = queue.createTailer().toEnd().index();
        try (DocumentContext dc = readTailer.readingDocument()) {
          if (!dc.isPresent()) {
            sampler.sampled(() -> log.info("Reached end of queue"));
            long readIndex = readTailer.index();
            if (readIndex < endIndex) {
              readTailer.moveToIndex(endIndex);
              fileDeletionManager.setSentIndex(endIndex);
              log.warn(
                  "Observed readTailer not at end with no document context. Moved from {} to {}", readIndex, endIndex);
            }
            break;
          }
          try {
            verify(dc.wire() != null, "Null wire with document context present");
            byte[] bytes = requireNonNull(dc.wire()).read().bytes();
            if (bytes != null) {
              PublishMessage message = PublishMessage.parseFrom(bytes);
              batchToSend.add(message);
            } else {
              // could happen in case of an error during append with document context open.
              log.warn("Read NULL message. Skipping");
            }
          } catch (Exception e) {
            log.error("Exception while parsing message", e);
          }
        }
      }
      if (batchToSend.isFull()) {
        log.info("Batch is full");
      }
      if (!batchToSend.isEmpty()) {
        PublishRequest publishRequest = PublishRequest.newBuilder().addAllMessages(batchToSend.getMessages()).build();
        try {
          publishMessagesOverRest(publishRequest);
          fileDeletionManager.setSentIndex(readTailer.index());
          scheduler.recordSuccess();
          log.info("Published {} messages successfully over rest", batchToSend.size());
        } catch (IOException e) {
          log.error("Something wrong with publishing over rest", e);
          try {
            if (blockingStub == null) {
              log.info("::: blockingStub is not initialized :::: ");
              return;
            }
            log.info("Trying to publish over GRPC");
            blockingStub.withDeadlineAfter(30, TimeUnit.SECONDS).publish(publishRequest);
            log.info("Published {} messages successfully over grpc", batchToSend.size());
            fileDeletionManager.setSentIndex(readTailer.index());
            scheduler.recordSuccess();
          } catch (Exception err) {
            log.warn("Exception during message publish", err);
            QueueUtils.moveToIndex(readTailer, fileDeletionManager.getSentIndex());
            scheduler.recordFailure();
          }
        }
      } else {
        fileDeletionManager.setSentIndex(readTailer.index());
        sampler.sampled(() -> log.info("Skipping message publish as batch is empty"));
      }
    } catch (OverlappingFileLockException exception) {
      log.error("======== OverlappingFileLockException has been thrown =======");
      deleteFilesAndStartTailerService();
    } catch (Exception e) {
      log.error("Encountered exception", e);
    } finally {
      try {
        sampler.sampled(this::printStats);
        sampler.sampled(fileDeletionManager::deleteOlderFiles);
      } catch (Exception e) {
        log.error("Encountered exception in finally", e);
      }
    }
  }

  private void publishMessagesOverRest(PublishRequest publishRequest) throws IOException {
    try {
      Call<PublishResponse> call = eventPublisherClient.publish(accountId, publishRequest);
      EventServiceRestUtils.executeRestCall(call);
    } catch (Exception e) {
      log.error("Error while publishing messages over rest ", e);
      throw new IOException(e);
    }
  }

  @Override
  protected Scheduler scheduler() {
    return scheduler;
  }
}
