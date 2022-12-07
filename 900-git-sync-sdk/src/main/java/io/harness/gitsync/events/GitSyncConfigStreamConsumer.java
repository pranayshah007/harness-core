/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.events;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_CONFIG_STREAM;
import static io.harness.gitsync.AbstractGitSyncSdkModule.GIT_SYNC_SDK;

import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(DX)
public class GitSyncConfigStreamConsumer extends RedisTraceConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private final Consumer redisConsumer;
  private final List<MessageListener> messageListenersList;
  AuthorizationServiceHeader authorizationServiceHeader;
  private final QueueController queueController;

  @Inject
  public GitSyncConfigStreamConsumer(@Named(GIT_CONFIG_STREAM + GIT_SYNC_SDK) Consumer redisConsumer,
      @Named(GIT_CONFIG_STREAM + GIT_SYNC_SDK) MessageListener gitSyncConfigStreamCConsumer,
      @Named("git-msvc") AuthorizationServiceHeader authorizationServiceHeader, QueueController queueController) {
    this.redisConsumer = redisConsumer;
    this.queueController = queueController;
    messageListenersList = new ArrayList<>();
    messageListenersList.add(gitSyncConfigStreamCConsumer);
    this.authorizationServiceHeader = authorizationServiceHeader;
  }

  @Override
  public void run() {
    log.info("Started the consumer for git sync config stream");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(authorizationServiceHeader.getServiceId()));
      while (!Thread.currentThread().isInterrupted()) {
        if (queueController.isNotPrimary()) {
          log.info("Git sync config consumer is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(30);
          continue;
        }
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("git sync config stream consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for git sync config stream consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages;
    String messageId;
    boolean messageProcessed;
    messages = redisConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));
    for (Message message : messages) {
      messageId = message.getId();
      messageProcessed = handleMessage(message);
      if (messageProcessed) {
        redisConsumer.acknowledge(messageId);
      }
    }
  }

  protected boolean processMessage(Message message) {
    AtomicBoolean success = new AtomicBoolean(true);
    messageListenersList.forEach(messageListener -> {
      if (!messageListener.handleMessage(message)) {
        success.set(false);
      }
    });

    return success.get();
  }
}
