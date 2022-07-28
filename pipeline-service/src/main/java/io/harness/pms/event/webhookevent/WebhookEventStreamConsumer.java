/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.webhookevent;

import static io.harness.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.ng.core.event.MessageListener;
import io.harness.pms.events.base.PmsAbstractRedisConsumer;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class WebhookEventStreamConsumer extends PmsAbstractRedisConsumer<WebhookEventStreamListener> {
  private final List<MessageListener> messageListenersList;
  private final QueueController queueController;
  private AtomicBoolean shouldStop = new AtomicBoolean(false);

  @Inject
  public WebhookEventStreamConsumer(@Named(WEBHOOK_EVENTS_STREAM) Consumer redisConsumer,
      WebhookEventStreamListener webhookEventListener, @Named("pmsEventsCache") Cache<String, Integer> eventsCache,
      QueueController queueController) {
    super(redisConsumer, webhookEventListener, eventsCache, queueController);
    messageListenersList = new ArrayList<>();
    messageListenersList.add(webhookEventListener);
    this.queueController = queueController;
  }

  @Override
  public void run() {
    log.info("Started the consumer for Webhook event stream {}", this.getClass().getSimpleName());
    String threadName = this.getClass().getSimpleName() + "-handler-" + generateUuid();
    log.debug("Setting thread name to {}", threadName);
    Thread.currentThread().setName(threadName);

    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
      while (!Thread.currentThread().isInterrupted() && !shouldStop.get()) {
        if (queueController.isNotPrimary()) {
          log.info(this.getClass().getSimpleName()
              + " is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(30);
          continue;
        }
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("Webhook event stream consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  public void shutDown() {
    shouldStop.set(true);
  }
}
