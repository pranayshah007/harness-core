/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.AbstractProducer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.EventHeader;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.WebhookEventAutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.entities.WebhookEvent.WebhookEventsKeys;
import io.harness.ng.webhook.services.api.WebhookEventProcessingService;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;
import io.harness.service.WebhookParserSCMService;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class WebhookEventProcessingServiceImpl
    implements WebhookEventProcessingService, MongoPersistenceIterator.Handler<WebhookEvent> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private WebhookParserSCMService webhookParserSCMService;
  @Inject private WebhookHelper webhookHelper;
  @Inject WebhookEventRepository webhookEventRepository;

  @Override
  public void registerIterators(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("WebhookEventProcessor")
            .poolSize(threadPoolSize)
            .interval(ofSeconds(5))
            .build(),
        WebhookEventProcessingService.class,
        MongoPersistenceIterator.<WebhookEvent, SpringFilterExpander>builder()
            .clazz(WebhookEvent.class)
            .fieldName(WebhookEventsKeys.nextIteration)
            .targetInterval(ofMinutes(5))
            .acceptableExecutionTime(ofMinutes(2))
            .acceptableNoAlertDelay(ofSeconds(30))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public void handle(WebhookEvent event) {
    try (AutoLogContext ignore1 = new AccountLogContext(event.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new WebhookEventAutoLogContext(event.getUuid(), OVERRIDE_ERROR)) {
      log.info("Processing the webhook event with uuid = [{}]", event.getUuid());
      ParseWebhookResponse parseWebhookResponse = null;
      SourceRepoType sourceRepoType = webhookHelper.getSourceRepoType(event);

      if (sourceRepoType != SourceRepoType.UNRECOGNIZED) {
        parseWebhookResponse = invokeScmService(event);
      }

      try {
        if (parseWebhookResponse == null) {
          log.info("Unsupported webhook event for id {} ", event.getUuid());
          return;
        }
        publishWebhookEvent(event, parseWebhookResponse, sourceRepoType);
      } catch (Exception e) {
        log.error(new StringBuilder(128)
                      .append("Error while publishing Webhook Event: ")
                      .append(event.getUuid())
                      .append(", Exception: ")
                      .append(e)
                      .toString());

      } finally {
        webhookEventRepository.delete(event);
      }
    }
  }

  public ParseWebhookResponse invokeScmService(WebhookEvent event) {
    try {
      Stopwatch stopwatch = Stopwatch.createStarted();
      ParseWebhookResponse parseWebhookResponse =
          webhookParserSCMService.parseWebhookUsingSCMAPI(event.getHeaders(), event.getPayload());
      log.info("Finished parsing webhook payload in {} ", stopwatch.elapsed(TimeUnit.SECONDS));
      return parseWebhookResponse;
    } catch (Exception exception) {
      logIfScmUnavailableException(event, exception);
    }

    // This failure could also mean, SCM could not parse payload. This may be some event SCM does not yet support.
    // We still need to continue, as someone might have configured Custom trigger on this.
    return null;
  }

  private void logIfScmUnavailableException(WebhookEvent event, Exception exception) {
    if (StatusRuntimeException.class.isAssignableFrom(exception.getClass())) {
      StatusRuntimeException e = (StatusRuntimeException) exception;

      if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
        // SCM service could not be accessed.
        log.error(new StringBuilder(128)
                      .append("SCM service unavailable for parsing webhook payload. EventId")
                      .append(event.getUuid())
                      .append(", Exception: ")
                      .append(e)
                      .toString());
      }
    }
  }

  public void publishWebhookEvent(
      WebhookEvent event, ParseWebhookResponse parseWebhookResponse, SourceRepoType sourceRepoType) {
    WebhookDTO webhookDTO = webhookHelper.generateWebhookDTO(event, parseWebhookResponse, sourceRepoType);

    // if publish fails for one of the producers, still continue for rest of the producers.
    webhookHelper.getProducerListForEvent(webhookDTO).forEach(producer -> {
      try {
        producer.send(Message.newBuilder().setData(webhookDTO.toByteString()).build());
      } catch (EventsFrameworkDownException e) {
        String topicName =
            producer instanceof AbstractProducer ? ((AbstractProducer) producer).getTopicName() : StringUtils.EMPTY;

        log.error(new StringBuilder(128)
                      .append("Error while publishing Webhook Event: ")
                      .append(webhookDTO.getEventId())
                      .append(" to Topic")
                      .append(topicName)
                      .append(", Exception: ")
                      .append(e)
                      .toString());
      }
    });
  }
}
